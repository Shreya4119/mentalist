import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { expect, login, test } from './fixtures/mock-api';

const money = (page: Page, label: string | RegExp) => page.getByLabel(label);

async function openPlan(page: Page) {
  await login(page, 'demo');
  await page.locator('nav.topbar').getByRole('link', { name: 'My plan' }).click();
  await expect(page.getByRole('heading', { name: 'My plan', level: 1 })).toBeVisible();
}

async function fillDutchFamily(page: Page) {
  await page.getByLabel('Country you live in').selectOption({ label: 'Netherlands' });
  await page.getByLabel(/My household/).check();
  await page.getByLabel('Two (couple)').check();
  await page.getByLabel('Dependants').fill('1');
  await money(page, /^Net monthly income/).fill('5200');
  await money(page, /^Gross monthly income/).fill('7600');
  await money(page, /^Essential monthly spending/).fill('2600');
  await money(page, /^Total monthly spending/).fill('4300');
  await money(page, /^Rent or mortgage payment/).fill('1500');
  await money(page, /^Cash savings/).fill('11000');
  await money(page, /^Monthly loan and credit repayments/).fill('150');
  await money(page, /^Monthly retirement saving/).fill('600');
  await page.getByRole('group', { name: /employer add to your pension/ }).getByLabel('Yes').check();
  await page.getByRole('group', { name: /full amount/ }).getByLabel('No', { exact: true }).check();
  await money(page, /^Investments in total/).fill('42000');
  await page.getByLabel(/aansprakelijkheidsverzekering/).check();
  await page.getByRole('button', { name: '+ Add a goal' }).click();
  await page.getByLabel('Goal name').fill('Home deposit');
  await page.getByLabel('Target amount').fill('40000');
  await page.getByLabel('By year').fill('2030');
  await page.getByLabel('Saved so far').fill('15000');
  await page.getByLabel('Saving a month').fill('300');
}

test.describe('My plan — family financial health check', () => {
  test('PLAN-01 first visit explains the check, privacy and leads to the form', async ({ page, api }) => {
    await openPlan(page);
    await expect(page.getByRole('heading', { name: /Check your financial health/ })).toBeVisible();
    await expect(page.getByText(/Only you can see your numbers/)).toBeVisible();
    await page.getByRole('link', { name: 'Start my health check' }).click();
    await expect(page).toHaveURL(/\/plan\/check$/);
    await expect(page.getByRole('heading', { name: 'Your personal health check' })).toBeVisible();
  });

  test('PLAN-02 missing required numbers are explained and nothing is sent', async ({ page, api }) => {
    await openPlan(page);
    await page.goto('/plan/check');
    await page.getByRole('button', { name: 'See my health check' }).click();
    const errors = page.locator('#form-errors');
    await expect(errors).toBeFocused();
    await expect(errors).toContainText('Choose your country.');
    await expect(errors).toContainText('Net monthly income must be more than 0.');
    await expect(errors).toContainText('Essential monthly spending must be more than 0.');
    expect(api.planRuns).toHaveLength(0);
  });

  test('PLAN-03 a Dutch family gets a score, next step, nine checks, goals and local rules', async ({ page, api }) => {
    await openPlan(page);
    await page.getByRole('link', { name: 'Start my health check' }).click();
    await fillDutchFamily(page);
    await page.getByRole('button', { name: 'See my health check' }).click();
    await expect(page).toHaveURL(/\/plan\/[\w-]+$/);

    const sent = api.planRuns[0];
    expect(sent).toMatchObject({ individual: false, country: 'NL', adults: 2, dependants: 1, netMonthlyIncome: 5200, essentialMonthlySpending: 2600,
      employerMatchAvailable: true, employerMatchFullyUsed: false, investmentsTotal: 42000 });
    expect(sent.insurance).toMatchObject({ liability: true, health: true, disability: false, termLife: false });
    expect(sent.goals).toEqual([{ name: 'Home deposit', targetAmount: 40000, targetYear: 2030, currentSaved: 15000, monthlyContribution: 300 }]);

    await expect(page.getByRole('heading', { name: /Health check of/ })).toBeVisible();
    await expect(page.locator('.score-band .big')).toHaveText('55');
    await expect(page.locator('.score-band .rating')).toHaveText('Stable');
    await expect(page.getByRole('heading', { name: /^Household health/ })).toBeVisible();
    await expect(page.locator('.subtitle')).toContainText('Two adults · 1 dependant');
    await expect(page.locator('.step.next')).toContainText("Take your employer's full pension contribution");
    await expect(page.locator('.step.next .badge')).toHaveText('Next');
    await expect(page.locator('.ratio-row')).toHaveCount(9);
    await expect(page.locator('.ratio-row', { hasText: 'Emergency fund' })).toContainText('4.2 months');
    await expect(page.locator('.ratio-row', { hasText: 'Emergency fund' })).toContainText('including the health-insurance own risk');
    await expect(page.locator('.goal')).toHaveCount(2);
    await expect(page.locator('.goal', { hasText: 'Home deposit' }).locator('.goal-status')).toContainText('Behind');
    await expect(page.getByRole('heading', { name: 'Rules for Netherlands' })).toBeVisible();
    await expect(page.getByText(/Box 3 tax-free amount/)).toBeVisible();
    await expect(page.getByRole('note')).toContainText('not financial, tax or legal advice');
  });

  test('PLAN-04 insurance names follow the chosen country', async ({ page, api }) => {
    await openPlan(page);
    await page.goto('/plan/check');
    await expect(page.getByLabel(/Privathaftpflicht/)).toBeVisible();
    await page.getByLabel('Country you live in').selectOption({ label: 'Netherlands' });
    await expect(page.getByLabel(/aansprakelijkheidsverzekering/)).toBeVisible();
    await expect(page.getByLabel(/Privathaftpflicht/)).toHaveCount(0);
  });

  test('PLAN-05 the ⓘ on a check explains the term and what the user’s own number means', async ({ page, api }) => {
    const s = api.addSnapshot('nl');
    await login(page, 'demo');
    await page.goto(`/plan/${s.id}`);
    await page.getByRole('button', { name: 'What is Emergency fund?' }).click();
    const tip = page.getByRole('dialog', { name: 'Emergency fund' });
    await expect(tip).toContainText('Your numbers');
    await expect(tip).toContainText('4.2 months of essential costs');
    await expect(tip).toContainText('spare tyre');
    await tip.getByRole('link', { name: 'More in Learn the basics →' }).click();
    await expect(page).toHaveURL(/\/reports\/learn#emergencyFund$/);
    await expect(page.getByRole('heading', { name: 'Your household' })).toBeVisible();
  });

  test('PLAN-06 My plan shows the latest check and progress over time', async ({ page, api }) => {
    const older = api.addSnapshot('nl', 'demo', '2025-10-01T09:00:00Z');
    api.addSnapshot('de', 'demo', '2026-10-01T09:00:00Z');
    await openPlan(page);
    await expect(page.locator('.score-band .big')).toHaveText('100');
    await expect(page.getByText('Your score is up 45 points since 1 Oct 2025.')).toBeVisible();
    await expect(page.locator('.history li')).toHaveCount(2);
    await page.locator('.history a', { hasText: '2025' }).click();
    await expect(page).toHaveURL(new RegExp(`/plan/${older.id}$`));
    await expect(page.locator('.score-band .big')).toHaveText('55');
  });

  test('PLAN-07 "Update my numbers" starts from the last answers', async ({ page, api }) => {
    api.addSnapshot('nl', 'demo', '2026-10-01T09:00:00Z', {
      country: 'NL', adults: 2, dependants: 1, netMonthlyIncome: 5200, essentialMonthlySpending: 2600, cashSavings: 11000,
      insurance: { liability: true, health: true, disability: false, termLife: false },
      goals: [{ name: 'Home deposit', targetAmount: 40000, targetYear: 2030, currentSaved: 15000, monthlyContribution: 300 }],
    });
    await openPlan(page);
    await page.getByRole('link', { name: 'Update my numbers' }).click();
    await expect(page.getByRole('heading', { name: 'Update your numbers' })).toBeVisible();
    await expect(page.getByLabel('Country you live in')).toHaveValue(/NL/);
    await expect(money(page, /^Net monthly income/)).toHaveValue('5200');
    await expect(page.getByLabel('Goal name')).toHaveValue('Home deposit');
    await expect(page.getByLabel(/aansprakelijkheidsverzekering/)).toBeChecked();
  });

  test('PLAN-08 deleting a check asks first, then removes it', async ({ page, api }) => {
    const s = api.addSnapshot('nl');
    await login(page, 'demo');
    await page.goto(`/plan/${s.id}`);
    await page.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByRole('alertdialog')).toContainText("This can't be undone.");
    await page.getByRole('button', { name: 'Yes, delete' }).click();
    await expect(page).toHaveURL(/\/plan$/);
    expect(api.planDeletes).toEqual([s.id]);
    await expect(page.getByRole('link', { name: 'Start my health check' })).toBeVisible();
  });

  test('PLAN-09 server-side validation messages are shown in the form', async ({ page, api }) => {
    await openPlan(page);
    await page.goto('/plan/check');
    await page.getByLabel('Country you live in').selectOption({ label: 'Germany' });
    await money(page, /^Net monthly income/).fill('3000');
    await money(page, /^Essential monthly spending/).fill('2000');
    await money(page, /^Total monthly spending/).fill('1500');
    await page.getByRole('button', { name: 'See my health check' }).click();
    await expect(page.locator('#form-errors')).toContainText("Total spending can't be lower than essential spending.");
    await expect(page).toHaveURL(/\/plan\/check$/);
  });

  test('PLAN-10 nobody can open another user’s health check', async ({ page, api }) => {
    const theirs = api.addSnapshot('nl', 'admin');
    await login(page, 'demo');
    await page.goto(`/plan/${theirs.id}`);
    await expect(page.getByText('This health check could not be found.')).toBeVisible();
    await expect(page.locator('.score-band')).toHaveCount(0);
  });

  test('PLAN-12 "Just me" is the default: no household questions, a check for one person', async ({ page, api }) => {
    await openPlan(page);
    await page.getByRole('link', { name: 'Start my health check' }).click();
    await expect(page.getByLabel(/Just me/)).toBeChecked();
    await expect(page.getByText('Adults sharing finances')).toHaveCount(0);
    await expect(page.getByLabel('Dependants')).toHaveCount(0);
    await expect(page.getByText('Your take-home pay after tax.')).toBeVisible();
    await page.getByLabel('Country you live in').selectOption({ label: 'Germany' });
    await money(page, /^Net monthly income/).fill('3100');
    await money(page, /^Essential monthly spending/).fill('1500');
    await page.getByRole('button', { name: 'See my health check' }).click();
    await expect(page).toHaveURL(/\/plan\/[\w-]+$/);
    expect(api.planRuns[0]).toMatchObject({ individual: true, adults: 1, dependants: 0, country: 'DE' });
    await expect(page.getByRole('heading', { name: /^Your financial health/ })).toBeVisible();
    await expect(page.locator('.subtitle')).toContainText('Just me');
  });

  test('PLAN-13 switching to "My household" asks about adults and dependants; back to "Just me" resets them', async ({ page, api }) => {
    await openPlan(page);
    await page.goto('/plan/check');
    await page.getByLabel(/My household/).check();
    await expect(page.getByRole('heading', { name: 'Your family health check' })).toBeVisible();
    await page.getByLabel('Two (couple)').check();
    await page.getByLabel('Dependants').fill('2');
    await page.getByLabel(/Just me/).check();
    await expect(page.getByLabel('Dependants')).toHaveCount(0);
    await page.getByLabel('Country you live in').selectOption({ label: 'Netherlands' });
    await money(page, /^Net monthly income/).fill('3000');
    await money(page, /^Essential monthly spending/).fill('1500');
    await page.getByRole('button', { name: 'See my health check' }).click();
    await expect(page).toHaveURL(/\/plan\/[\w-]+$/);
    expect(api.planRuns[0]).toMatchObject({ individual: true, adults: 1, dependants: 0 });
  });

  test('PLAN-11 form and results pass the axe WCAG 2.1 A/AA scan (contrast tracked separately)', async ({ page, api }) => {
    const s = api.addSnapshot('nl');
    await login(page, 'demo');
    const scan = async () => (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .disableRules(['color-contrast']).analyze()).violations.map((v) => `${v.id}: ${v.help}`);
    await page.goto('/plan/check');
    await page.getByRole('button', { name: '+ Add a goal' }).click();
    expect(await scan()).toEqual([]);
    await page.goto(`/plan/${s.id}`);
    await expect(page.locator('.score-band')).toBeVisible();
    expect(await scan()).toEqual([]);
  });
});

test.describe('My plan — phones @responsive', () => {
  test('PLAN-RESP-01 form and results fit a phone screen', async ({ page, api }) => {
    const s = api.addSnapshot('nl');
    await login(page, 'demo');
    const fits = () => page.evaluate((w) => document.documentElement.scrollWidth <= w + 1, page.viewportSize()!.width);
    await page.goto('/plan/check');
    expect(await fits()).toBe(true);
    await page.goto(`/plan/${s.id}`);
    await expect(page.locator('.score-band')).toBeVisible();
    expect(await fits()).toBe(true);
    await expect(page.locator('.step.next')).toBeVisible();
  });
});
