import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { mkdtempSync, truncateSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expect, login, test } from './fixtures/mock-api';

const pdf = (name: string, bytes = 'fake pdf bytes') => ({ name, mimeType: 'application/pdf', buffer: Buffer.from(bytes) });

async function openReports(page: Page, user: 'admin' | 'demo' = 'admin') {
  await login(page, user);
  await page.getByRole('link', { name: 'Financial reports' }).click();
  await expect(page.getByRole('heading', { name: 'Financial reports' })).toBeVisible();
}

test.describe('Financial reports', () => {
  test('REP-01 empty library shows the upload zone, accepted formats and both providers', async ({ page, api }) => {
    await openReports(page);
    await expect(page.getByText('No reports yet')).toBeVisible();
    await expect(page.getByText('PDF, images (PNG, JPG, WEBP, TIFF), XLSX/XLS/CSV, DOCX or TXT · up to 250 MB')).toBeVisible();
    await expect(page.getByLabel('AI provider')).toHaveValue('gemini');
    await expect(page.getByLabel('AI provider').locator('option')).toHaveText(['Google Gemini', 'Anthropic Claude']);
  });

  test('REP-02 uploading a PDF opens its page, shows progress, then the analysis', async ({ page, api }) => {
    await openReports(page);
    await page.getByLabel('AI provider').selectOption('claude');
    await page.getByLabel('Choose a financial report to upload').setInputFiles(pdf('northwind-fy26.pdf'));
    await expect(page).toHaveURL(/\/reports\/[\w-]+$/);
    await expect(page.getByText(/Queued for analysis|Reading the report/).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Northwind Industries Ltd' })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.score-band .big')).toHaveText('96');
    expect(api.reportUploads).toEqual([{ filename: 'northwind-fy26.pdf', provider: 'claude' }]);
  });

  test('REP-03 analysis page shows verdict, story, charts, ratios with formulas, figures and disclaimer', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    await openReports(page);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    await expect(page).toHaveURL(new RegExp(`/reports/${r.id}$`));

    const subtitle = page.locator('.detail-header .subtitle');
    for (const part of ['FY 2025-26', 'Consolidated', 'Figures in INR crore', 'Industrial machinery']) {
      await expect(subtitle).toContainText(part);
    }
    await expect(page.locator('.score-band .rating')).toHaveText('Strong');
    await expect(page.locator('.dim-row')).toHaveCount(6);
    await expect(page.getByText('Northwind grew sales 13%')).toBeVisible();
    await expect(page.locator('.strengths .finding')).toHaveCount(2);
    await expect(page.locator('.red-flags .finding')).toHaveCount(2);

    // Charts: two grouped-bar charts (current vs prior) and the composition bar.
    await expect(page.locator('app-metric-bars')).toHaveCount(2);
    await expect(page.locator('app-metric-bars').first().locator('.hbar')).toHaveCount(8);
    await expect(page.locator('app-metric-bars').first().locator('.legend')).toHaveText(/FY 2025-26\s*FY 2024-25/);
    await expect(page.getByText("What it owns vs. how it's funded")).toBeVisible();

    // Ratios grouped by category; expanding one shows the formula with the report's own figures.
    await expect(page.locator('.group-title')).toHaveText([/^Profitability/, /^Liquidity/, /^Leverage & solvency/, /^Cash flow/, /^Efficiency/, /^Growth/]);
    const current = page.locator('.ratio-row').filter({ has: page.locator('.ratio-name', { hasText: /^Current ratio/ }) });
    await expect(current.locator('.ratio-value')).toHaveText('1.94x');
    await expect(current.locator('app-assessment-chip')).toHaveText(/Healthy/);
    await expect(current.locator('.ratio-meaning')).toHaveText(/has 1\.94 INR of short-term assets for every 1 INR of bills due within a year/);
    await current.getByText("How it's calculated").click();
    await expect(current.getByText('Formula: Current assets (9,900 INR crore) ÷ Current liabilities (5,100 INR crore)')).toBeVisible();

    await expect(page.locator('table.figures tbody tr')).toHaveCount(15);
    await expect(page.getByText('Operating income taken as profit before tax plus finance costs.')).toBeVisible();
    await expect(page.locator('.disclaimer')).toContainText('not investment advice');
    await page.getByText('How this score works').click();
    await expect(page.getByText(/Profitability 25%, Liquidity 20%/)).toBeVisible();
  });

  test('REP-04 unsupported file type is rejected with a clear message', async ({ page, api }) => {
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles({ name: 'tool.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('x') });
    await expect(page.getByRole('alert')).toContainText(/unsupported file type/i);
    await expect(page).toHaveURL(/\/reports$/);
  });

  test('REP-05 files over 250 MB are stopped before upload, with advice', async ({ page, api }) => {
    await openReports(page);
    // Playwright caps in-memory files at 50 MB, so write the oversized (sparse) file to disk.
    const big = join(mkdtempSync(join(tmpdir(), 'mentalist-')), 'huge-annual-report.pdf');
    writeFileSync(big, '');
    truncateSync(big, 250 * 1024 * 1024 + 1);
    await page.getByLabel('Choose a financial report to upload').setInputFiles(big);
    await expect(page.getByRole('alert')).toContainText('huge-annual-report.pdf is 251 MB; the limit is 250 MB.');
    await expect(page.getByRole('alert')).toContainText('compress the PDF');
    expect(api.reportUploads).toHaveLength(0);
  });

  test('REP-06 a failed analysis explains why and can be retried with another provider', async ({ page, api }) => {
    api.nextAnalysisError = 'Gemini: Rate limit or quota reached. Wait a minute and try again, or switch provider.';
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles(pdf('annual-report.pdf'));
    await expect(page.getByText("The analysis didn't finish")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Rate limit or quota reached')).toBeVisible();
    await page.getByLabel('AI provider for re-analysis').selectOption('claude');
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.locator('.score-band .big')).toHaveText('96', { timeout: 15_000 });
    expect(api.reportReanalyses).toEqual([{ id: expect.any(String), provider: 'claude' }]);
  });

  test('REP-07 the report page has an Ask panel: header button jumps to it, suggestions ask, answers cite sources', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    await openReports(page);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();

    const panel = page.locator('#ask');
    await expect(panel.getByRole('heading', { name: 'Ask about this report' })).toBeVisible();
    await page.getByRole('button', { name: 'Ask questions about this report' }).click();
    await expect(panel.getByLabel('Your question about this report')).toBeFocused();
    await expect(panel).toBeInViewport();

    await panel.getByRole('button', { name: 'Is the company’s debt a concern?' }).click();
    await expect(panel.locator('.qa-msg.user')).toHaveText('Is the company’s debt a concern?');
    await expect(panel.locator('.qa-msg.assistant .qa-text')).toHaveText('Mock answer to: Is the company’s debt a concern?');
    await expect(panel.locator('.suggestions')).toHaveCount(0);
    await panel.locator('.qa-cites summary').click();
    await expect(panel.getByText('Current assets exceed current liabilities 1.6 times.')).toBeVisible();

    // Follow-up keeps the conversation; New conversation starts over.
    await panel.getByLabel('Your question about this report').fill('And compared with last year?');
    await panel.getByRole('button', { name: 'Ask', exact: true }).click();
    await expect(panel.locator('.qa-msg.assistant')).toHaveCount(2);
    expect(api.chatCalls.map((c) => c.documentId)).toEqual([r.id, r.id]);
    expect(api.chatCalls[1].conversationId).toBe('conv-1');
    await panel.getByRole('button', { name: 'New conversation' }).click();
    await expect(panel.locator('.qa-msg')).toHaveCount(0);
    await expect(panel.locator('.suggestion')).toHaveCount(5);
  });

  test('REP-07b full-screen chat from the panel has a way back to the analysis', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    await openReports(page);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    await page.getByRole('link', { name: 'Open full screen' }).click();
    await expect(page).toHaveURL(new RegExp(`/reports/${r.id}/chat$`));
    await expect(page.locator('.scope-banner')).toContainText('Asking about northwind-fy26.pdf');
    await page.getByPlaceholder('Ask a question…').fill('What is the debt to equity ratio?');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.locator('.message.assistant')).toBeVisible();
    await page.getByRole('link', { name: '← Back to the analysis' }).click();
    await expect(page).toHaveURL(new RegExp(`/reports/${r.id}$`));
  });

  test('REP-07c questions work as soon as the analysis is done, even while the full text is still indexing', async ({ page, api }) => {
    const r = api.addReport('asml-2025-annual-report.pdf');
    r.fullTextIndexed = false;
    await openReports(page);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    const panel = page.locator('#ask');
    await expect(panel.getByText('The full report text is still being indexed')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ask questions about this report' })).toBeEnabled();
    await panel.getByLabel('Your question about this report').fill('Summarise the balance sheet');
    await panel.getByRole('button', { name: 'Ask', exact: true }).click();
    await expect(panel.locator('.qa-msg.assistant')).toBeVisible();
  });

  test('REP-07d the Ask link in the reports list opens the report at the question box', async ({ page, api }) => {
    api.addReport('northwind-fy26.pdf');
    await openReports(page);
    await page.getByRole('link', { name: 'Ask questions about Northwind Industries Ltd' }).click();
    await expect(page).toHaveURL(/#ask$/);
    await expect(page.locator('#ask').getByLabel('Your question about this report')).toBeFocused();
    await expect(page.locator('#ask')).toBeInViewport();
  });

  test('REP-07e no Ask panel or link while a report is still being analysed', async ({ page, api }) => {
    api.reportPollsUntilDone = 99;
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles(pdf('pending.pdf'));
    await expect(page.getByText(/Queued for analysis|Reading the report/).first()).toBeVisible();
    await expect(page.locator('#ask')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Ask questions about this report' })).toBeDisabled();
    await page.getByRole('link', { name: '← All reports' }).click();
    await expect(page.getByRole('link', { name: /Ask questions about/ })).toHaveCount(0);
  });

  test('REP-08 two analysed reports can be compared side by side', async ({ page, api }) => {
    const older = api.addReport('northwind-fy25.pdf', 'fy25');
    const newer = api.addReport('northwind-fy26.pdf', 'fy26');
    await openReports(page);
    const compareButton = page.locator('.compare-button');
    await expect(compareButton).toBeDisabled();
    await page.getByLabel(/Select Northwind Industries Limited to compare/).check();
    await page.getByLabel(/Select Northwind Industries Ltd to compare/).check();
    await expect(compareButton).toHaveText('Compare selected (2)');
    await compareButton.click();

    await expect(page).toHaveURL(new RegExp(`a=${older.id}.*b=${newer.id}`));
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/FY 2024-25 vs FY 2025-26/);
    await expect(page.locator('.warning-note')).toHaveCount(0);
    await expect(page.locator('app-score-band')).toHaveCount(2);
    await expect(page.locator('app-metric-bars')).toHaveCount(1);
    await expect(page.getByRole('row', { name: /Current ratio/ })).toBeVisible();
    await page.getByRole('button', { name: 'Swap A and B' }).click();
    await expect(page).toHaveURL(new RegExp(`a=${newer.id}.*b=${older.id}`));
  });

  test('REP-08b a report opened from a comparison goes back to that comparison', async ({ page, api }) => {
    const older = api.addReport('northwind-fy25.pdf', 'fy25');
    const newer = api.addReport('northwind-fy26.pdf', 'fy26');
    await login(page, 'admin');
    await page.goto(`/reports/compare?a=${older.id}&b=${newer.id}`);
    await page.getByRole('link', { name: 'FY 2025-26' }).click();
    await expect(page.getByRole('heading', { name: 'Northwind Industries Ltd' })).toBeVisible();
    await page.getByRole('link', { name: '← Back to comparison' }).click();
    await expect(page).toHaveURL(new RegExp(`/reports/compare\\?a=${older.id}&b=${newer.id}`));
    await page.getByRole('link', { name: '← All reports' }).click();
    await expect(page).toHaveURL(/\/reports$/);
  });

  test('REP-09 regular users can read any report but only manage their own', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf', 'fy26', 'admin');
    await openReports(page, 'demo');
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    await expect(page.locator('.score-band .big')).toHaveText('96');
    await expect(page.getByRole('button', { name: 'Re-analyze' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Delete report' })).toHaveCount(0);
    expect(r.id).toBeTruthy();
  });

  test('REP-10 deleting a report asks for confirmation and removes it', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    await openReports(page);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    await page.getByRole('button', { name: 'Delete report' }).click();
    await expect(page.getByText('Delete this report and its analysis?')).toBeVisible();
    await page.getByRole('button', { name: 'Yes, delete' }).click();
    await expect(page).toHaveURL(/\/reports$/);
    await expect(page.getByText('No reports yet')).toBeVisible();
    expect(api.reportDeletes).toEqual([r.id]);
  });

  test('REP-14 an iPhone HEIC photo gets a specific export tip and is not sent', async ({ page, api }) => {
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles({ name: 'IMG_0042.HEIC', mimeType: 'image/heic', buffer: Buffer.from('x') });
    await expect(page.getByRole('alert')).toContainText('iPhone HEIC photo');
    await expect(page.getByRole('alert')).toContainText('Most Compatible');
    expect(api.reportUploads).toHaveLength(0);
  });

  test('REP-15 several page photos are sent together and become one report', async ({ page, api }) => {
    await openReports(page);
    const photo = (n: number) => ({ name: `page-${n}.jpg`, mimeType: 'image/jpeg', buffer: Buffer.from(`jpeg ${n}`) });
    await page.getByLabel('Choose a financial report to upload').setInputFiles([photo(1), photo(2), photo(3)]);
    await expect(page).toHaveURL(/\/reports\/[0-9a-f-]+$/);
    expect(api.reportUploadParts).toEqual([['page-1.jpg', 'page-2.jpg', 'page-3.jpg']]);
  });

  test('REP-16 mixing a PDF with other files is explained instead of uploaded', async ({ page, api }) => {
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles([
      { name: 'page-1.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('a') },
      pdf('annual-report.pdf'),
    ]);
    await expect(page.getByRole('alert')).toContainText('annual-report.pdf isn\'t; upload PDFs and spreadsheets one at a time');
    expect(api.reportUploads).toHaveLength(0);
  });

  test('REP-17 an empty file is stopped before upload', async ({ page, api }) => {
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles({ name: 'empty.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(0) });
    await expect(page.getByRole('alert')).toHaveText('empty.pdf is empty.');
    expect(api.reportUploads).toHaveLength(0);
  });

  test('REP-18 uploading the same file twice opens the existing report with a note', async ({ page, api }) => {
    await openReports(page);
    const input = page.getByLabel('Choose a financial report to upload');
    await input.setInputFiles(pdf('adyen-2025.pdf', 'same bytes'));
    await expect(page).toHaveURL(/\/reports\/[0-9a-f-]+$/);
    const firstUrl = page.url();
    await page.locator('.back-link').click();
    await input.setInputFiles(pdf('adyen-2025-copy.pdf', 'same bytes'));
    await expect(page.getByTestId('duplicate-banner')).toBeVisible();
    expect(page.url().split('?')[0]).toBe(firstUrl);
    expect(api.reports).toHaveLength(1);
  });

  test('REP-19 a 413 from the server or proxy shows the size advice', async ({ page, api }) => {
    api.nextUploadError = { status: 413, message: 'This file is larger than the 250 MB upload limit. Upload just the financial statements pages, or compress the PDF.' };
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles(pdf('big.pdf'));
    await expect(page.getByRole('alert')).toContainText('larger than the 250 MB upload limit');
    await expect(page).toHaveURL(/\/reports$/);
  });

  test('REP-20 a renamed file rejected by the server explains the real type', async ({ page, api }) => {
    api.nextUploadError = { status: 415, message: 'statement.pdf looks like a ZIP/Office file, not a PDF. Rename it with the right extension or export it again.' };
    await openReports(page);
    await page.getByLabel('Choose a financial report to upload').setInputFiles(pdf('statement.pdf'));
    await expect(page.getByRole('alert')).toContainText('not a PDF');
  });

  test('REP-21 a scanned report says chat answers come from the analysis only', async ({ page, api }) => {
    const r = api.addReport('scan.pdf');
    r.fullTextIndexed = false;
    r.textFailed = true;
    await login(page, 'admin');
    await page.goto(`/reports/${r.id}`);
    await expect(page.getByText(/pages are images \(a scan or photos\).*answers come from the analysis only/)).toBeVisible();
  });

  test('REP-11 Financial reports is the home screen after sign-in', async ({ page, api }) => {
    api.addReport('northwind-fy26.pdf');
    await login(page, 'admin');
    await expect(page).toHaveURL(/\/reports$/);
    await expect(page.getByRole('link', { name: /^Northwind Industries Ltd/ })).toBeVisible();
  });

  test('REP-12 with no AI provider configured, upload is disabled and the fix is explained', async ({ page, api }) => {
    api.providerList = api.providerList.map((p) => ({ ...p, configured: false }));
    await openReports(page);
    await expect(page.getByRole('alert')).toContainText('No AI provider is configured');
    await expect(page.getByLabel('Choose a financial report to upload')).toBeDisabled();
  });

  test('REP-13 report pages pass the axe WCAG 2.1 A/AA scan (contrast tracked separately)', async ({ page, api }) => {
    api.addReport('northwind-fy25.pdf', 'fy25');
    api.addReport('northwind-fy26.pdf', 'fy26');
    await openReports(page);
    const scan = async () =>
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).disableRules(['color-contrast']).analyze())
        .violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`);
    expect(await scan()).toEqual([]);
    await page.getByRole('link', { name: /^Northwind Industries Ltd/ }).click();
    await expect(page.locator('.score-band .big')).toHaveText('96');
    expect(await scan()).toEqual([]);
  });
});

test.describe('Financial reports — responsive @responsive', () => {
  const fitsViewport = (page: Page) => page.evaluate((w) => document.documentElement.scrollWidth <= w + 1, page.viewportSize()!.width);
  const fullyVisible = async (page: Page, name: string) => {
    const box = await page.getByRole('button', { name }).boundingBox();
    const width = page.viewportSize()!.width;
    return !!box && box.x >= 0 && box.x + box.width <= width;
  };

  test('REP-RESP-01 analysis page and top bar fit a phone screen', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    await login(page, 'admin');
    await page.goto(`/reports/${r.id}`);
    await expect(page.locator('.score-band .big')).toHaveText('96');
    expect(await fitsViewport(page)).toBe(true);
    expect(await fullyVisible(page, 'Log out')).toBe(true);
    await expect(page.getByRole('link', { name: 'Financial reports' })).toBeVisible();
  });

  test('REP-RESP-02 reports list fits a phone screen', async ({ page, api }) => {
    api.addReport('a-very-long-annual-report-file-name-for-northwind-industries-fy-2025-26-final.pdf');
    await login(page, 'admin');
    await page.goto('/reports');
    expect(await fitsViewport(page)).toBe(true);
  });
});
