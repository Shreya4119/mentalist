import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { GLOSSARY_LIST } from '../src/app/reports/learn/glossary';
import { LESSONS } from '../src/app/reports/learn/lessons';
import { expect, login, test } from './fixtures/mock-api';

async function openReport(page: Page, api: any) {
  const r = api.addReport('northwind-fy26.pdf');
  await login(page, 'admin');
  await page.goto(`/reports/${r.id}`);
  await expect(page.locator('.score-band .big')).toHaveText('96');
  return r;
}

const ratioRow = (page: Page, name: string) =>
  page.locator('.ratio-row').filter({ has: page.locator('.ratio-name', { hasText: new RegExp('^' + name) }) });

test.describe('Learning aids for non-finance users', () => {
  test('LEARN-01 every ratio, health area and figure has an ⓘ explanation', async ({ page, api }) => {
    await openReport(page, api);
    await expect(page.locator('.ratio-row app-info-tip button')).toHaveCount(16);
    await expect(page.locator('.dim-row app-info-tip button')).toHaveCount(6);
    await expect(page.locator('table.figures tbody app-info-tip button')).toHaveCount(15);
    await expect(page.getByRole('button', { name: 'What is Financial health score?' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'What is Consolidated vs standalone?' })).toBeVisible();
    await expect(page.getByRole('button', { name: "What is What it owns vs how it’s funded?" })).toBeVisible();
  });

  test('LEARN-02 a ratio tip explains the term, this company’s number and its effect on the health score', async ({ page, api }) => {
    await openReport(page, api);
    const btn = ratioRow(page, 'Debt-to-equity').getByRole('button', { name: 'What is Debt-to-equity?' });
    await btn.click();
    await expect(btn).toHaveAttribute('aria-expanded', 'true');
    const panel = page.getByRole('dialog', { name: 'Debt-to-equity' });
    await expect(panel).toBeVisible();
    await expect(panel.locator('.info-company')).toContainText('Northwind Industries Ltd has borrowed 0.14 INR for every 1 INR its shareholders have in the business. That is in the healthy range.');
    await expect(panel).toContainText('In everyday terms');
    await expect(panel).toContainText('Counts towards Leverage & solvency, which is 20% of the overall health score');
    await expect(panel).toContainText('0.5x or below is healthy');
    await expect(panel).toBeInViewport();

    // Escape closes and returns focus to the icon.
    await page.keyboard.press('Escape');
    await expect(panel).toHaveCount(0);
    await expect(btn).toBeFocused();
  });

  test('LEARN-03 only one tip is open at a time and clicking elsewhere closes it', async ({ page, api }) => {
    await openReport(page, api);
    await page.getByRole('button', { name: 'What is Financial health score?' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(1);
    await ratioRow(page, 'Current ratio').getByRole('button', { name: 'What is Current ratio?' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(1);
    await expect(page.getByRole('dialog', { name: 'Current ratio' })).toBeVisible();
    await page.locator('h1').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  test('LEARN-04 every ratio shows a plain-English line about this company, with the formula one tap away', async ({ page, api }) => {
    await openReport(page, api);
    const meanings = await page.locator('.ratio-meaning').allTextContents();
    expect(meanings).toHaveLength(16);
    expect(meanings.every((m) => m.trim().length > 20)).toBe(true);
    await expect(ratioRow(page, 'Net profit margin').locator('.ratio-meaning')).toHaveText(/keeps about 0\.14 INR of profit from every 1 INR of sales \(14\.0%\)/);
    await expect(ratioRow(page, 'Revenue growth').locator('.ratio-meaning')).toHaveText(/Sales grew 13\.0% compared with last year/);
    const calc = ratioRow(page, 'Interest coverage');
    await expect(calc.getByText(/^Formula:/)).toBeHidden();
    await calc.getByText("How it's calculated").click();
    await expect(calc.getByText(/Operating income \(2,380 INR crore\) ÷ Interest expense \(96 INR crore\)/)).toBeVisible();
  });

  test('LEARN-05 first-time guide can be dismissed and stays hidden after reload', async ({ page, api }) => {
    await openReport(page, api);
    const guide = page.getByRole('region', { name: 'New to investing? How to read this page' });
    await expect(guide).toBeVisible();
    await guide.getByRole('button', { name: 'Got it, hide this' }).click();
    await expect(guide).toHaveCount(0);
    await page.reload();
    await expect(page.locator('.score-band .big')).toHaveText('96');
    await expect(page.getByRole('region', { name: 'New to investing? How to read this page' })).toHaveCount(0);
  });

  test('LEARN-06 "More in Learn the basics" opens the glossary at that term, with a way back', async ({ page, api }) => {
    await openReport(page, api);
    await ratioRow(page, 'Quick ratio').getByRole('button', { name: 'What is Quick ratio?' }).click();
    await page.getByRole('link', { name: 'More in Learn the basics →' }).click();
    await expect(page).toHaveURL(/\/reports\/learn#quickRatio$/);
    await expect(page.locator('#quickRatio')).toBeInViewport();
    await expect(page.locator('#quickRatio h3')).toHaveText('Quick ratio');
    await page.getByRole('link', { name: '← Financial reports' }).click();
    await expect(page).toHaveURL(/\/reports$/);
  });

  test('LEARN-07 "All terms" is the searchable glossary and links related terms', async ({ page, api }) => {
    await login(page, 'admin');
    await page.goto('/reports');
    await page.getByRole('link', { name: 'Learn the basics' }).click();
    await expect(page.getByRole('heading', { name: 'Learn the basics' })).toBeVisible();
    await page.getByRole('link', { name: 'All terms' }).click();
    await expect(page).toHaveURL(/view=terms/);
    const all = await page.locator('article.term').count();
    expect(all).toBeGreaterThanOrEqual(45);
    await page.getByLabel('Search the glossary').fill('debt');
    const filtered = await page.locator('article.term').count();
    expect(filtered).toBeGreaterThan(2);
    expect(filtered).toBeLessThan(all);
    await page.getByLabel('Search the glossary').fill('zzzz');
    await expect(page.getByText('No terms match “zzzz”.')).toBeVisible();
    await page.getByLabel('Search the glossary').fill('');
    await page.locator('#roe').getByRole('link', { name: 'Debt-to-equity' }).click();
    await expect(page).toHaveURL(/view=terms#debtToEquity$/);
    await page.locator('#debtToEquity').getByRole('link', { name: 'See it illustrated →' }).click();
    await expect(page).toHaveURL(/lesson=debt&slide=1/);
    await expect(page.locator('article.slide h3')).toHaveText('Debt-to-equity');
    await expect(page.locator('article.slide svg[data-visual="debt-equity"]')).toBeVisible();
  });

  test('LEARN-11 lessons are illustrated slides you can step through with buttons, dots and arrow keys', async ({ page, api }) => {
    await login(page, 'admin');
    await page.goto('/reports/learn');
    await expect(page.locator('.lesson-card')).toHaveCount(11);
    await page.getByRole('link', { name: 'Start lesson 1: Start here' }).click();
    await expect(page).toHaveURL(/lesson=start&slide=1/);
    await expect(page.getByText('Slide 1 of 5')).toBeVisible();
    await expect(page.locator('article.slide h3')).toHaveText('Fundamental analysis');
    await expect(page.locator('article.slide svg[role="img"]')).toHaveAttribute('aria-label', /business, its reports, ratios/);
    await expect(page.getByRole('button', { name: '← Previous' })).toBeDisabled();
    await page.getByRole('button', { name: 'Next →' }).click();
    await expect(page.getByText('Slide 2 of 5')).toBeVisible();
    await page.keyboard.press('ArrowRight');
    await expect(page.locator('article.slide h3')).toHaveText('The one equation behind every balance sheet');
    await page.keyboard.press('ArrowLeft');
    await expect(page.getByText('Slide 2 of 5')).toBeVisible();
    await page.getByRole('button', { name: 'Slide 4: Reading a report in four steps' }).click();
    await expect(page.locator('article.slide ol li')).toHaveCount(4);
    await page.getByRole('button', { name: 'Slide 5: A healthy business is not always a good buy' }).click();
    await page.getByRole('link', { name: 'Next lesson: The health score →' }).click();
    await expect(page).toHaveURL(/lesson=score&slide=1/);
    // progress is remembered on the lesson list
    await page.getByRole('link', { name: '← All lessons' }).click();
    await expect(page.getByRole('link', { name: 'Review lesson 1: Start here' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Continue lesson 2: The health score' })).toBeVisible();
  });

  test('LEARN-12 every glossary term has its own slide, so every ⓘ tip leads somewhere', async ({ page, api }) => {
    const slideIds = LESSONS.flatMap((l) => l.slides.map((s) => s.id));
    expect(GLOSSARY_LIST.map((e) => e.key).filter((k) => !slideIds.includes(k))).toEqual([]);
    expect(new Set(slideIds).size).toBe(slideIds.length);
    await login(page, 'admin');
    for (const key of ['fundamentalAnalysis', 'interestCoverage', 'auditorOpinion', 'feeDrag', 'priorityLadder']) {
      await page.goto(`/reports/learn#${key}`);
      await expect(page.locator(`article.slide#${key}`)).toBeVisible();
      await expect(page.locator(`article.slide#${key} svg[role="img"]`)).toBeVisible();
    }
  });

  test('LEARN-08 the comparison page has the same ⓘ explanations', async ({ page, api }) => {
    const a = api.addReport('northwind-fy25.pdf', 'fy25');
    const b = api.addReport('northwind-fy26.pdf', 'fy26');
    await login(page, 'admin');
    await page.goto(`/reports/compare?a=${a.id}&b=${b.id}`);
    await page.getByRole('row', { name: /Current ratio/ }).getByRole('button', { name: 'What is Current ratio?' }).click();
    await expect(page.getByRole('dialog', { name: 'Current ratio' })).toContainText('Counts towards Liquidity');
  });

  test('LEARN-10 "Learn" is in the top navigation on every page and has its own active tab', async ({ page, api }) => {
    await login(page, 'demo');
    const nav = page.locator('nav.topbar');
    await nav.getByRole('link', { name: 'Learn', exact: true }).click();
    await expect(page).toHaveURL(/\/reports\/learn/);
    await expect(nav.getByRole('link', { name: 'Learn', exact: true })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'Financial reports' })).not.toHaveClass(/active/);
    await nav.getByRole('link', { name: 'Financial reports' }).click();
    await expect(nav.getByRole('link', { name: 'Financial reports' })).toHaveClass(/active/);
    await expect(nav.getByRole('link', { name: 'Learn', exact: true })).not.toHaveClass(/active/);
  });

  test('LEARN-09 learning pages pass the axe scan, including an open tip', async ({ page, api }) => {
    await openReport(page, api);
    await page.getByRole('button', { name: 'What is Financial health score?' }).click();
    const scan = async () =>
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).disableRules(['color-contrast']).analyze())
        .violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`);
    expect(await scan()).toEqual([]);
    await page.goto('/reports/learn');
    await expect(page.getByRole('heading', { name: 'Learn the basics' })).toBeVisible();
    expect(await scan()).toEqual([]);
    await page.goto('/reports/learn?lesson=household&slide=4');
    await expect(page.locator('article.slide h3')).toBeVisible();
    expect(await scan()).toEqual([]);
    await page.goto('/reports/learn?view=terms');
    await expect(page.getByLabel('Search the glossary')).toBeVisible();
    expect(await scan()).toEqual([]);
  });
});

test.describe('Learning aids — phones @responsive', () => {
  test('LEARN-RESP-01 a tip opens as a bottom sheet that fits the screen', async ({ page, api }) => {
    await openReport(page, api);
    await ratioRow(page, 'Current ratio').getByRole('button', { name: 'What is Current ratio?' }).click();
    const panel = page.getByRole('dialog', { name: 'Current ratio' });
    await expect(panel).toBeVisible();
    const box = (await panel.boundingBox())!;
    const vp = page.viewportSize()!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(vp.width + 1);
    expect(box.y + box.height).toBeLessThanOrEqual(vp.height + 1);
    expect(await page.evaluate((w) => document.documentElement.scrollWidth <= w + 1, vp.width)).toBe(true);
  });

  test('LEARN-RESP-02 a tip opened near the bottom of the screen is still fully visible', async ({ page, api }) => {
    await openReport(page, api);
    const btn = ratioRow(page, 'Debt-to-equity').getByRole('button', { name: 'What is Debt-to-equity?' });
    await btn.scrollIntoViewIfNeeded();
    // Put the icon near the bottom edge of the window before opening.
    await page.evaluate(() => {
      const el = [...document.querySelectorAll('.ratio-name')].find((n) => n.textContent?.startsWith('Debt-to-equity'))!;
      window.scrollBy(0, el.getBoundingClientRect().top - window.innerHeight + 60);
    });
    await btn.click();
    const panel = page.getByRole('dialog', { name: 'Debt-to-equity' });
    const box = (await panel.boundingBox())!;
    const vp = page.viewportSize()!;
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.y + box.height).toBeLessThanOrEqual(vp.height + 1);
    expect(box.x + box.width).toBeLessThanOrEqual(vp.width + 1);
  });

  test('LEARN-RESP-03 a slide fits a phone, shows the picture first and can be swiped', async ({ page, api }) => {
    await login(page, 'admin');
    await page.goto('/reports/learn?lesson=profit&slide=1');
    const slide = page.locator('article.slide');
    const vp = page.viewportSize()!;
    const box = (await slide.boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(vp.width + 1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (vp.width < 860) {
      const fig = (await page.locator('.slide-visual').boundingBox())!;
      const h3 = (await slide.locator('h3').boundingBox())!;
      expect(fig.y).toBeLessThan(h3.y);
    }
    await slide.dispatchEvent('touchstart', { changedTouches: [{ identifier: 1, clientX: 300, clientY: 400 }] });
    await slide.dispatchEvent('touchend', { changedTouches: [{ identifier: 1, clientX: 100, clientY: 400 }] });
    await expect(page.getByText('Slide 2 of 4')).toBeVisible();
  });
});
