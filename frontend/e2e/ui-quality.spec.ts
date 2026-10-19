import AxeBuilder from '@axe-core/playwright';
import { expect, login, openChat, test } from './fixtures/mock-api';

test.describe('Themes', () => {
  test('THEME-01 picker switches theme, closes, and persists across reload', async ({ page, api }) => {
    await login(page, 'demo');
    await page.getByRole('button', { name: 'Change color theme' }).click();
    await expect(page.locator('.theme-option')).toHaveCount(4);
    await page.locator('.theme-option', { hasText: 'Ocean' }).click();
    await expect(page.locator('.theme-menu')).toHaveCount(0);
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'ocean');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'ocean');
    await page.getByRole('button', { name: 'Change color theme' }).click();
    await expect(page.locator('.theme-option.active')).toContainText('Ocean');
  });

  test('THEME-02 default Warm Coral theme has no data-theme attribute', async ({ page, api }) => {
    await login(page, 'demo');
    await expect(page.locator('html')).not.toHaveAttribute('data-theme', /.+/);
  });

  test('THEME-03 theme menu closes on Escape or outside click', async ({ page, api }) => {
    await login(page, 'demo');
    await page.getByRole('button', { name: 'Change color theme' }).click();
    await page.keyboard.press('Escape');
    await expect(page.locator('.theme-menu')).toHaveCount(0);
    await page.getByRole('button', { name: 'Change color theme' }).click();
    await page.locator('h1').click();
    await expect(page.locator('.theme-menu')).toHaveCount(0);
  });
});

test.describe('Accessibility (axe, WCAG 2.1 A/AA)', () => {
  // Colour contrast is tracked separately (A11Y-04) as a known palette defect,
  // so these scans gate every other WCAG A/AA rule.
  const scan = (page: import('@playwright/test').Page, rules: 'all-but-contrast' | 'contrast-only' = 'all-but-contrast') => {
    const builder = new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']);
    return (rules === 'contrast-only' ? builder.withRules(['color-contrast']) : builder.disableRules(['color-contrast'])).analyze();
  };

  const summarize = (r: Awaited<ReturnType<typeof scan>>) =>
    r.violations.map((v) => `${v.id} [${v.impact}] x${v.nodes.length}: ${v.help}`);

  test('A11Y-01 login page', async ({ page, api }) => {
    await page.goto('/login');
    expect(summarize(await scan(page))).toEqual([]);
  });

  test('A11Y-02 financial reports home page', async ({ page, api }) => {
    api.addReport('northwind-fy26.pdf');
    api.addReport('northwind-fy25.pdf', 'fy25');
    await login(page, 'admin');
    expect(summarize(await scan(page))).toEqual([]);
  });

  test('A11Y-03 chat page with an answer', async ({ page, api }) => {
    await openChat(page, api);
    await page.getByPlaceholder('Ask a question…').fill('q');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.locator('.message.assistant')).toBeVisible();
    expect(summarize(await scan(page))).toEqual([]);
  });
});

test.describe('Accessibility — colour contrast (known defect)', () => {
  test('A11Y-04 text meets WCAG AA contrast on all pages', async ({ page, api }) => {
    test.info().annotations.push({ type: 'issue', description: 'DEF-11: theme palettes fail WCAG AA contrast (primary on white 2.8:1, muted text 3.8:1, faint text 2.3:1)' });
    test.fail(true, 'Known defect DEF-11 — remove this line once the palette is fixed');
    const counts: number[] = [];
    await page.goto('/login');
    counts.push((await new AxeBuilder({ page }).withRules(['color-contrast']).analyze()).violations.length);
    await login(page, 'demo');
    counts.push((await new AxeBuilder({ page }).withRules(['color-contrast']).analyze()).violations.length);
    expect(counts).toEqual([0, 0]);
  });
});

test.describe('Responsive @responsive', () => {
  const noHorizontalScroll = async (page: import('@playwright/test').Page) =>
    page.evaluate((w) => document.documentElement.scrollWidth <= w + 1, page.viewportSize()!.width);

  test('RESP-01 login fits the viewport', async ({ page, api }) => {
    await page.goto('/login');
    expect(await noHorizontalScroll(page)).toBe(true);
  });

  test('RESP-02 reports home fits the viewport', async ({ page, api }) => {
    api.addReport('a-very-long-file-name-for-the-annual-report-and-accounts-2026-final-v3.pdf');
    await login(page, 'admin');
    expect(await noHorizontalScroll(page)).toBe(true);
    await expect(page.getByRole('button', { name: 'Log out' })).toBeInViewport({ ratio: 1 });
  });

  test('RESP-03 chat composer and nav fit the viewport', async ({ page, api }) => {
    await openChat(page, api);
    expect(await noHorizontalScroll(page)).toBe(true);
    await expect(page.getByRole('button', { name: 'Send' })).toBeInViewport({ ratio: 1 });
    await expect(page.getByRole('button', { name: 'New conversation' })).toBeInViewport({ ratio: 1 });
    await expect(page.getByRole('button', { name: 'Log out' })).toBeInViewport({ ratio: 1 });
  });
});

test.describe('Console hygiene', () => {
  test('HYG-01 no console errors across a full admin journey', async ({ page, api }) => {
    const errors: string[] = [];
    page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
    page.on('pageerror', (e) => errors.push(e.message));
    await openChat(page, api, 'admin');
    await page.getByPlaceholder('Ask a question…').fill('q');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.locator('.message.assistant')).toBeVisible();
    await page.getByRole('link', { name: '← Back to the analysis' }).click();
    const nav = page.locator('nav.topbar');
    await nav.getByRole('link', { name: 'Learn', exact: true }).click();
    await nav.getByRole('link', { name: 'Financial reports' }).click();
    await page.getByRole('button', { name: 'Log out' }).click();
    expect(errors).toEqual([]);
  });
});
