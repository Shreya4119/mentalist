import { Page, Route, test as base } from '@playwright/test';
import { ANALYSIS_FY25, ANALYSIS_FY26, COMPARISON } from './report-fixtures';
import { PLAN_DE, PLAN_NL } from './plan-fixtures';

export type Role = 'ADMIN' | 'USER';
export type Username = 'admin' | 'demo';

export interface MockReport {
  id: string;
  filename: string;
  uploadedAt: string;
  uploadedBy: string;
  analysisStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  provider: string;
  analysis: any | null;
  errorMessage: string | null;
  /** Full report text embedded for retrieval (can lag well behind the analysis for long reports). */
  fullTextIndexed: boolean;
  /** Text extraction failed (scan/photos): chat falls back to the analysis. */
  textFailed?: boolean;
}

export interface ChatCall {
  question: string;
  conversationId: string | null;
  documentId: string | null;
}

const USERS: Record<Username, { password: string; role: Role }> = {
  admin: { password: 'admin-pass', role: 'ADMIN' },
  demo: { password: 'demo-pass', role: 'USER' },
};

let seq = 0;
const uuid = () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`;

/**
 * In-memory stand-in for the Spring Boot API. Tests mutate `reports`, set
 * `failNext` to inject an error status, and read `chatCalls` /
 * `feedbackCalls` / `reportUploads` to assert what the UI sent.
 */
export class MockApi {
  chatCalls: ChatCall[] = [];
  feedbackCalls: { messageId: string; rating: string }[] = [];
  /** Next response for the matching endpoint key returns this status. */
  failNext: Partial<Record<'login' | 'chat' | 'feedback' | 'plan', number>> = {};

  // ---- family health check (My plan)
  snapshots: { id: string; username: Username; createdAt: string; input: any; result: any }[] = [];
  planRuns: any[] = [];
  planDeletes: string[] = [];

  /** Seeds a saved health check (nl = Dutch family, score 55; de = German single, score 100). */
  addSnapshot(which: 'nl' | 'de' = 'nl', username: Username = 'demo', createdAt = '2026-10-01T09:00:00Z', input: any = null) {
    const result = which === 'nl' ? PLAN_NL : PLAN_DE;
    const snap = { id: uuid(), username, createdAt, input: input ?? { country: result.country, adults: which === 'nl' ? 2 : 1, dependants: which === 'nl' ? 1 : 0, goals: [], insurance: null }, result };
    this.snapshots.push(snap);
    return snap;
  }

  private async handlePlanning(route: Route, username: Username, path: string, method: string) {
    const id = path.split('/')[4];
    const mine = this.snapshots.filter((s) => s.username === username).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    if (!id && method === 'GET') {
      return this.json(route, 200, mine.map((s) => ({ id: s.id, createdAt: s.createdAt, country: s.result.country, score: s.result.health.score, rating: s.result.health.rating })));
    }
    if (!id && method === 'POST') {
      const f = this.take('plan');
      if (f) return this.json(route, f, { message: 'error' });
      const input = route.request().postDataJSON();
      this.planRuns.push(input);
      const errors: string[] = [];
      if (!input.country) errors.push('Choose your country.');
      if (!(input.netMonthlyIncome > 0)) errors.push('Net monthly income must be more than 0.');
      if (input.totalMonthlySpending != null && input.essentialMonthlySpending != null && input.totalMonthlySpending < input.essentialMonthlySpending) {
        errors.push("Total spending can't be lower than essential spending.");
      }
      if (errors.length) return this.json(route, 400, { message: 'Please check the form.', errors });
      const snap = { id: uuid(), username, createdAt: new Date().toISOString(), input, result: input.country === 'NL' ? PLAN_NL : PLAN_DE };
      this.snapshots.push(snap);
      return this.json(route, 201, { id: snap.id, createdAt: snap.createdAt, input: snap.input, result: snap.result });
    }
    const snap = mine.find((s) => s.id === id);
    if (!snap) return this.json(route, 404, { message: 'Health check not found' });
    if (method === 'GET') return this.json(route, 200, { id: snap.id, createdAt: snap.createdAt, input: snap.input, result: snap.result });
    if (method === 'DELETE') {
      this.snapshots = this.snapshots.filter((s) => s.id !== id);
      this.planDeletes.push(id);
      return route.fulfill({ status: 204 });
    }
    return this.json(route, 404, { message: `No mock for ${method} ${path}` });
  }
  /** Optional artificial latency for /api/chat (ms). */
  chatDelayMs = 0;

  // ---- financial reports
  reports: MockReport[] = [];
  reportUploads: { filename: string; provider: string | null }[] = [];
  /** File names of every part in each upload request (several page photos → one report). */
  reportUploadParts: string[][] = [];
  /** When set, the next upload is answered with this status and message (e.g. 413 from a proxy). */
  nextUploadError: { status: number; message: string } | null = null;
  private uploadHashes = new Map<string, string>();
  reportReanalyses: { id: string; provider: string | null }[] = [];
  reportDeletes: string[] = [];
  /** Report polls a fresh upload stays in flight for before completing. */
  reportPollsUntilDone = 2;
  /** When set, the next analysis to finish fails with this message. */
  nextAnalysisError: string | null = null;
  providerList = [
    { id: 'gemini', label: 'Google Gemini', model: 'gemini-3.8-flash', configured: true, isDefault: true },
    { id: 'claude', label: 'Anthropic Claude', model: 'claude-sonnet-5-5', configured: true, isDefault: false },
  ];
  private reportPolls = new Map<string, number>();

  /** Seeds an analysed report (fy26 = strong FY 2025-26 analysis, fy25 = prior year). */
  addReport(filename: string, which: 'fy26' | 'fy25' = 'fy26', uploadedBy: Username = 'admin'): MockReport {
    const r: MockReport = {
      id: uuid(), filename, uploadedAt: which === 'fy26' ? '2026-09-30T09:00:00Z' : '2025-09-30T09:00:00Z',
      uploadedBy, analysisStatus: 'COMPLETED', provider: 'gemini',
      analysis: which === 'fy26' ? ANALYSIS_FY26 : ANALYSIS_FY25, errorMessage: null, fullTextIndexed: true,
    };
    this.reports.push(r);
    return r;
  }

  private reportSummary(r: MockReport) {
    const a = r.analysis;
    return {
      id: r.id, filename: r.filename, uploadedAt: r.uploadedAt, uploadedBy: r.uploadedBy,
      documentStatus: r.textFailed ? 'FAILED' : r.fullTextIndexed ? 'INDEXED' : 'PROCESSING',
      chatReady: r.fullTextIndexed || r.analysisStatus === 'COMPLETED',
      analysisStatus: r.analysisStatus, provider: r.provider, model: r.provider === 'claude' ? 'claude-sonnet-5-5' : 'gemini-3.8-flash',
      companyName: a?.extraction.companyName ?? null, reportingPeriod: a?.extraction.reportingPeriod ?? null,
      healthScore: a?.health.score ?? null, healthRating: a?.health.rating ?? null,
      errorMessage: r.errorMessage, analysisRequestedAt: new Date(Date.now() - 5000).toISOString(),
      analysisCompletedAt: r.analysisStatus === 'COMPLETED' ? new Date().toISOString() : null,
    };
  }

  /** Advances an in-flight report one poll towards COMPLETED (or FAILED). */
  private tick(r: MockReport) {
    if (r.analysisStatus !== 'PENDING' && r.analysisStatus !== 'PROCESSING') return;
    const n = (this.reportPolls.get(r.id) ?? 0) + 1;
    this.reportPolls.set(r.id, n);
    if (n < this.reportPollsUntilDone) {
      r.analysisStatus = 'PROCESSING';
      return;
    }
    this.reportPolls.delete(r.id);
    if (this.nextAnalysisError) {
      r.analysisStatus = 'FAILED';
      r.errorMessage = this.nextAnalysisError;
      this.nextAnalysisError = null;
      return;
    }
    r.analysisStatus = 'COMPLETED';
    r.analysis = ANALYSIS_FY26;
    r.fullTextIndexed = true;
  }

  private async handleReports(route: Route, role: Role, username: string, path: string, method: string, url: URL) {
    const admin = role === 'ADMIN';
    const parts = path.split('/'); // ['', 'api', 'reports', id?, 'analysis'?]
    const id = parts[3];
    if (path === '/api/reports/providers') return this.json(route, 200, this.providerList);
    if (path === '/api/reports' && method === 'GET') {
      this.reports.forEach((r) => this.tick(r));
      return this.json(route, 200, [...this.reports].sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt)).map((r) => this.reportSummary(r)));
    }
    if (path === '/api/reports' && method === 'POST') {
      const body = route.request().postDataBuffer()?.toString('latin1') ?? '';
      const parts = [...body.matchAll(/name="file"; filename="([^"]+)"/g)].map((m) => m[1]);
      const filename = parts[0] ?? 'report.pdf';
      const provider = /name="provider"\r\n\r\n([^\r]+)/.exec(body)?.[1] ?? null;
      this.reportUploads.push({ filename, provider });
      this.reportUploadParts.push(parts);
      if (this.nextUploadError) {
        const e = this.nextUploadError;
        this.nextUploadError = null;
        return this.json(route, e.status, { message: e.message });
      }
      if (!parts.every((f) => /\.(pdf|png|jpe?g|webp|tiff?|gif|bmp|xlsx|xls|csv|docx|txt)$/i.test(f))) {
        return this.json(route, 415, { message: 'Unsupported file type. Upload a PDF, image (PNG/JPG), spreadsheet (XLSX/XLS/CSV), Word file or text file.' });
      }
      // Same bytes as an earlier upload → 409 pointing at the existing report (server uses SHA-256).
      const fileBodies = body.split(/name="file"; filename="[^"]+"/).slice(1).map((b) => b.slice(b.indexOf('\r\n\r\n') + 4).split('\r\n--')[0]);
      const key = fileBodies.join('|');
      const existing = this.uploadHashes.get(key);
      if (existing && this.reports.some((x) => x.id === existing)) {
        return this.json(route, 409, { message: 'This exact file was already uploaded.', existingId: existing });
      }
      const name = parts.length > 1 ? filename.replace(/\.[^.]+$/, '') + ` (+${parts.length - 1} pages).pdf` : filename;
      const r: MockReport = { id: uuid(), filename: name, uploadedAt: new Date().toISOString(), uploadedBy: username,
        analysisStatus: 'PENDING', provider: provider ?? 'gemini', analysis: null, errorMessage: null, fullTextIndexed: false };
      this.reports.push(r);
      this.uploadHashes.set(key, r.id);
      return this.json(route, 202, this.reportSummary(r));
    }
    if (id === 'compare') {
      const a = this.reports.find((r) => r.id === url.searchParams.get('a'));
      const b = this.reports.find((r) => r.id === url.searchParams.get('b'));
      if (!a || !b) return this.json(route, 404, { message: 'Report not found' });
      if (a.analysisStatus !== 'COMPLETED' || b.analysisStatus !== 'COMPLETED') return this.json(route, 409, { message: 'not finished analysis yet' });
      const c = JSON.parse(JSON.stringify(COMPARISON));
      c.a.reportId = a.id; c.a.filename = a.filename; c.b.reportId = b.id; c.b.filename = b.filename;
      return this.json(route, 200, c);
    }
    const r = this.reports.find((x) => x.id === id);
    if (!r) return this.json(route, 404, { message: 'Report not found' });
    const canManage = admin || r.uploadedBy === username;
    if (parts.length === 4 && method === 'GET') {
      this.tick(r);
      return this.json(route, 200, { report: this.reportSummary(r), analysis: r.analysisStatus === 'COMPLETED' ? r.analysis : null, canManage });
    }
    if (parts[4] === 'analysis' && method === 'POST') {
      if (!canManage) return this.json(route, 403, { message: 'Only the uploader or an admin can change this report' });
      this.reportReanalyses.push({ id: r.id, provider: url.searchParams.get('provider') });
      r.analysisStatus = 'PENDING';
      r.errorMessage = null;
      if (url.searchParams.get('provider')) r.provider = url.searchParams.get('provider')!;
      return this.json(route, 202, this.reportSummary(r));
    }
    if (parts.length === 4 && method === 'DELETE') {
      if (!canManage) return this.json(route, 403, { message: 'Only the uploader or an admin can change this report' });
      this.reports = this.reports.filter((x) => x.id !== r.id);
      this.reportDeletes.push(r.id);
      return route.fulfill({ status: 204 });
    }
    return this.json(route, 404, { message: `No mock for ${method} ${path}` });
  }
  answer: (q: ChatCall) => { answer: string; citations: any[] } = (q) => ({
    answer: `Mock answer to: ${q.question}`,
    citations: [
      { documentId: 'cite-doc', filename: 'northwind-fy26.pdf', chunkIndex: 0, chunkText: 'Current assets exceed current liabilities 1.6 times.' },
    ],
  });

  private conversation = 0;

  async install(page: Page) {
    // Keep tests offline-safe: the web font is cosmetic.
    await page.route(/fonts\.(googleapis|gstatic)\.com/, (r) => r.fulfill({ status: 200, body: '' }));
    await page.route('**/api/**', (route) => this.handle(route));
  }

  private take(key: keyof MockApi['failNext']): number | undefined {
    const s = this.failNext[key];
    delete this.failNext[key];
    return s;
  }

  private json(route: Route, status: number, body?: unknown) {
    return route.fulfill({
      status,
      contentType: 'application/json',
      body: body === undefined ? '' : JSON.stringify(body),
    });
  }

  private role(route: Route): Role | null {
    const auth = route.request().headers()['authorization'] ?? '';
    const m = /^Bearer mock-(ADMIN|USER)$/.exec(auth);
    return (m?.[1] as Role) ?? null;
  }

  private async handle(route: Route) {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const method = req.method();

    if (path === '/api/auth/login' && method === 'POST') {
      const forced = this.take('login');
      if (forced) return this.json(route, forced, { message: 'error' });
      const { username, password } = req.postDataJSON() ?? {};
      const user = USERS[username as Username];
      if (!user || user.password !== password) return this.json(route, 401, { message: 'Invalid username or password' });
      return this.json(route, 200, { token: `mock-${user.role}`, role: user.role });
    }

    const role = this.role(route);
    if (!role) return this.json(route, 401);
    if (path.startsWith('/api/planning/health-check')) {
      return this.handlePlanning(route, role === 'ADMIN' ? 'admin' : 'demo', path, method);
    }
    if (path.startsWith('/api/reports')) {
      return this.handleReports(route, role, role === 'ADMIN' ? 'admin' : 'demo', path, method, url);
    }

    if (path === '/api/chat' && method === 'POST') {
      const body = req.postDataJSON();
      const call: ChatCall = {
        question: body.question,
        conversationId: body.conversationId ?? null,
        documentId: body.documentId ?? null,
      };
      this.chatCalls.push(call);
      if (!call.documentId) return this.json(route, 400, { message: 'documentId is required' });
      const target = this.reports.find((x) => x.id === call.documentId);
      if (!target) return this.json(route, 404, { message: 'Report not found' });
      if (!target.fullTextIndexed && target.analysisStatus !== 'COMPLETED') return this.json(route, 409, { message: 'Report is not ready for questions yet' });
      if (this.chatDelayMs) await new Promise((r) => setTimeout(r, this.chatDelayMs));
      const f = this.take('chat');
      if (f) return this.json(route, f, { message: 'error' });
      const conversationId = call.conversationId ?? `conv-${++this.conversation}`;
      const { answer, citations } = this.answer(call);
      return this.json(route, 200, { conversationId, messageId: uuid(), answer, citations });
    }

    const fb = /^\/api\/chat\/([^/]+)\/feedback$/.exec(path);
    if (fb && method === 'POST') {
      const f = this.take('feedback');
      if (f) return this.json(route, f);
      this.feedbackCalls.push({ messageId: fb[1], rating: req.postDataJSON().rating });
      return route.fulfill({ status: 200 });
    }

    return this.json(route, 404, { message: `No mock for ${method} ${path}` });
  }
}

export async function login(page: Page, username: Username) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(USERS[username].password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL('**/reports');
}

/** Seeds one analysed report, logs in, and opens its full-screen chat from the analysis page. */
export async function openChat(page: Page, api: MockApi, username: Username = 'demo', filename = 'northwind-fy26.pdf') {
  const r = api.addReport(filename);
  await login(page, username);
  await page.goto(`/reports/${r.id}`);
  await page.getByRole('link', { name: 'Open full screen' }).click();
  await page.waitForURL(`**/reports/${r.id}/chat`);
  await page.getByText(`Asking about ${filename}`).waitFor();
  return r;
}

export const test = base.extend<{ api: MockApi }>({
  api: async ({ page }, use) => {
    const api = new MockApi();
    await api.install(page);
    await use(api);
  },
});

export { expect } from '@playwright/test';
