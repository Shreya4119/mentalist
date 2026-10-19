import { expect, login, openChat, test } from './fixtures/mock-api';

const input = (page: import('@playwright/test').Page) => page.getByPlaceholder('Ask a question…');
async function ask(page: import('@playwright/test').Page, q: string) {
  await input(page).fill(q);
  await page.getByRole('button', { name: 'Send' }).click();
}

test.describe('Chat', () => {
  test('CHAT-01 chat names the chosen report in the banner and empty state', async ({ page, api }) => {
    await openChat(page, api, 'demo', 'Acme-FY26.pdf');
    await expect(page.locator('.scope-banner')).toContainText('Asking about Acme-FY26.pdf');
    await expect(page.getByText('Ask a question about Acme-FY26.pdf. Answers come only from this report and its analysis.')).toBeVisible();
  });

  test('CHAT-02 Send is disabled for empty or whitespace-only input', async ({ page, api }) => {
    await openChat(page, api);
    const send = page.getByRole('button', { name: 'Send' });
    await expect(send).toBeDisabled();
    await input(page).fill('    ');
    await expect(send).toBeDisabled();
    await input(page).press('Enter');
    expect(api.chatCalls).toHaveLength(0);
  });

  test('CHAT-03 asking shows the question, a Thinking state, then the answer', async ({ page, api }) => {
    api.chatDelayMs = 600;
    await openChat(page, api);
    await ask(page, 'What is the current ratio?');
    await expect(page.locator('.message.user')).toHaveText('What is the current ratio?');
    await expect(page.getByText('Thinking…')).toBeVisible();
    await expect(input(page)).toBeDisabled();
    await expect(page.locator('.message.assistant .content')).toHaveText('Mock answer to: What is the current ratio?');
    await expect(page.getByText('Thinking…')).toHaveCount(0);
    await expect(input(page)).toBeEnabled();
    await expect(input(page)).toHaveValue('');
  });

  test('CHAT-04 Enter key submits the question', async ({ page, api }) => {
    await openChat(page, api);
    await input(page).fill('Enter works?');
    await input(page).press('Enter');
    await expect(page.locator('.message.assistant')).toBeVisible();
    expect(api.chatCalls[0].question).toBe('Enter works?');
  });

  test('CHAT-05 citations are collapsed by default and expand to show source text', async ({ page, api }) => {
    await openChat(page, api);
    await ask(page, 'liquidity?');
    const citations = page.locator('.citations');
    await expect(citations.locator('summary')).toHaveText('1 citation(s)');
    await expect(page.getByText('Current assets exceed current liabilities 1.6 times.')).toBeHidden();
    await citations.locator('summary').click();
    await expect(page.getByText('northwind-fy26.pdf (chunk 0)')).toBeVisible();
    await expect(page.getByText('Current assets exceed current liabilities 1.6 times.')).toBeVisible();
  });

  test('CHAT-06 answers without citations show no citations block', async ({ page, api }) => {
    api.answer = () => ({ answer: "I don't know based on this report.", citations: [] });
    await openChat(page, api);
    await ask(page, 'What is the CEO salary?');
    await expect(page.locator('.message.assistant')).toContainText("I don't know");
    await expect(page.locator('.citations')).toHaveCount(0);
  });

  test('CHAT-07 follow-ups reuse the conversation; New conversation starts fresh', async ({ page, api }) => {
    await openChat(page, api);
    await ask(page, 'first');
    await expect(page.locator('.message.assistant')).toHaveCount(1);
    await ask(page, 'second');
    await expect(page.locator('.message.assistant')).toHaveCount(2);
    expect(api.chatCalls[0].conversationId).toBeNull();
    expect(api.chatCalls[1].conversationId).toBe('conv-1');

    await page.getByRole('button', { name: 'New conversation' }).click();
    await expect(page.locator('.message')).toHaveCount(0);
    await ask(page, 'third');
    await expect(page.locator('.message.assistant')).toHaveCount(1);
    expect(api.chatCalls[2].conversationId).toBeNull();
  });

  test('CHAT-08 thumbs-up records feedback once and locks both buttons', async ({ page, api }) => {
    await openChat(page, api);
    await ask(page, 'q');
    const fb = page.locator('.message.assistant .feedback button');
    await fb.first().click();
    await expect(fb.first()).toHaveClass(/active/);
    await expect(fb.first()).toBeDisabled();
    await expect(fb.nth(1)).toBeDisabled();
    expect(api.feedbackCalls).toEqual([{ messageId: expect.any(String), rating: 'UP' }]);
  });

  test('CHAT-09 failed feedback is reported to the user', async ({ page, api }) => {
    api.failNext.feedback = 500;
    await openChat(page, api);
    await ask(page, 'q');
    await page.locator('.message.assistant .feedback button').nth(1).click();
    await expect(page.locator('.error')).toBeVisible();
  });

  test('CHAT-10 server error shows a retryable message and keeps the question visible', async ({ page, api }) => {
    api.failNext.chat = 500;
    await openChat(page, api);
    await ask(page, 'will fail');
    await expect(page.getByText('Something went wrong asking that question. Please try again.')).toBeVisible();
    await expect(page.locator('.message.user')).toHaveText('will fail');
    await ask(page, 'retry');
    await expect(page.locator('.message.assistant')).toBeVisible();
    await expect(page.locator('.error')).toHaveCount(0);
  });

  test('CHAT-11 multi-line answers keep their line breaks', async ({ page, api }) => {
    api.answer = () => ({ answer: 'Line one\nLine two', citations: [] });
    await openChat(page, api);
    await ask(page, 'q');
    const ws = await page.locator('.message.assistant .content').evaluate((el) => getComputedStyle(el).whiteSpace);
    expect(ws).toBe('pre-wrap');
  });

  test('CHAT-12 the newest message is scrolled into view in a long conversation', async ({ page, api }) => {
    api.answer = (c) => ({ answer: `Answer ${c.question}\n` + 'filler line\n'.repeat(15), citations: [] });
    await openChat(page, api);
    for (let i = 1; i <= 6; i++) {
      await ask(page, `Q${i}`);
      await expect(page.locator('.message.assistant')).toHaveCount(i);
    }
    await expect(page.locator('.message.assistant').last()).toBeInViewport();
  });

  test('CHAT-13 user text is rendered as text, not HTML (XSS)', async ({ page, api }) => {
    api.answer = () => ({ answer: '<img src=x onerror="window.__xss=1">', citations: [] });
    await openChat(page, api);
    await ask(page, '<b>bold?</b>');
    await expect(page.locator('.message.user')).toHaveText('<b>bold?</b>');
    await expect(page.locator('.message.assistant .content')).toHaveText('<img src=x onerror="window.__xss=1">');
    expect(await page.evaluate(() => (window as any).__xss)).toBeUndefined();
  });

  test('CHAT-14 every question carries the chosen report id; back link returns to the analysis', async ({ page, api }) => {
    const r = await openChat(page, api, 'admin', 'acme-fy26.pdf');
    await ask(page, 'first');
    await expect(page.locator('.message.assistant')).toHaveCount(1);
    await ask(page, 'second');
    await expect(page.locator('.message.assistant')).toHaveCount(2);
    expect(api.chatCalls.map((c) => c.documentId)).toEqual([r.id, r.id]);
    await page.getByRole('link', { name: '← Back to the analysis' }).click();
    await expect(page).toHaveURL(new RegExp(`/reports/${r.id}$`));
  });

  test('CHAT-15 chat link for a missing report says so and blocks chatting', async ({ page, api }) => {
    await login(page, 'admin');
    await page.goto('/reports/00000000-0000-4000-8000-000000000999/chat');
    await expect(page.getByText('Could not load that report.')).toBeVisible();
    await expect(page.getByRole('link', { name: '← Financial reports' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Send' })).toBeDisabled();
    await expect(input(page)).toBeDisabled();
    expect(api.chatCalls).toHaveLength(0);
  });

  test('CHAT-16 chat link for a report still being analysed blocks chatting', async ({ page, api }) => {
    const r = api.addReport('acme-fy26.pdf');
    Object.assign(r, { analysisStatus: 'PROCESSING', analysis: null, fullTextIndexed: false });
    api.reportPollsUntilDone = 99;
    await login(page, 'admin');
    await page.goto(`/reports/${r.id}/chat`);
    await expect(page.getByText('acme-fy26.pdf is still being analysed')).toBeVisible();
    await expect(input(page)).toBeDisabled();
    expect(api.chatCalls).toHaveLength(0);
  });

  test('CHAT-17 switching reports starts a fresh conversation about the new one', async ({ page, api }) => {
    const b = api.addReport('b-report.pdf', 'fy25');
    const a = await openChat(page, api, 'admin', 'a-report.pdf');
    await ask(page, 'about A');
    await expect(page.locator('.message.assistant')).toHaveCount(1);
    await page.getByRole('link', { name: '← Back to the analysis' }).click();
    await page.getByRole('link', { name: '← All reports' }).click();
    await page.getByRole('link', { name: /b-report\.pdf/ }).click();
    await page.getByRole('link', { name: 'Open full screen' }).click();
    await expect(page.locator('.scope-banner')).toContainText('Asking about b-report.pdf');
    await expect(page.locator('.message')).toHaveCount(0);
    await ask(page, 'about B');
    await expect(page.locator('.message.assistant')).toHaveCount(1);
    expect(api.chatCalls[1]).toMatchObject({ documentId: b.id, conversationId: null });
    expect(api.chatCalls[0].documentId).toBe(a.id);
  });

  test('CHAT-18 report removed mid-chat: clear message, chat blocked, still logged in', async ({ page, api }) => {
    const r = await openChat(page, api);
    api.reports = api.reports.filter((x) => x.id !== r.id);
    await ask(page, 'still there?');
    await expect(page.getByText('This report is no longer available for questions')).toBeVisible();
    await expect(input(page)).toBeDisabled();
    expect(await page.evaluate(() => localStorage.getItem('mentalist_token'))).not.toBeNull();
  });

  test('CHAT-19 old /chat/:id links redirect to the report chat', async ({ page, api }) => {
    const r = api.addReport('acme-fy26.pdf');
    await login(page, 'demo');
    await page.goto(`/chat/${r.id}`);
    await expect(page).toHaveURL(new RegExp(`/reports/${r.id}/chat$`));
    await expect(page.locator('.scope-banner')).toContainText('Asking about acme-fy26.pdf');
  });
});
