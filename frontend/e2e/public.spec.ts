import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { MockApi, expect, login, test } from './fixtures/mock-api';

const axe = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).disableRules(['color-contrast']).analyze()).violations.map((v) => v.id);

async function signUp(page: Page, username = 'shreya', password = 'my portfolio 2026') {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  const form = page.getByRole('form', { name: 'Create account' });
  await form.getByLabel('Choose a username').fill(username);
  await form.getByLabel('Password', { exact: true }).fill(password);
  await form.getByLabel('Repeat password').fill(password);
  await form.getByRole('checkbox').check();
  await form.getByRole('button', { name: 'Create account' }).click();
}

function publicServer(api: MockApi) {
  api.publicMode = true;
  api.signupEnabled = true;
  api.limits = { analysesLeft: 3, analysesPerDay: 3, questionsLeft: 30, questionsPerDay: 30 };
}

test.describe('Public server', () => {
  test.beforeEach(({ api }) => publicServer(api));

  test('PUB-01 anyone can create an account and lands in their own (empty) library with the sample reports', async ({ page, api }) => {
    api.addReport('adyen-annual-report-2025.pdf'); // sample, uploaded by the admin
    api.addReport('someone-elses-report.pdf', 'fy26', 'ben'); // private to ben
    await signUp(page);
    await page.waitForURL('**/reports');
    expect(api.signedUp.get('shreya')).toBe('my portfolio 2026');
    await expect(page.locator('nav.topbar .user-name')).toHaveText('shreya (USER)');
    await expect(page.locator('.report-row')).toHaveCount(1); // only the sample, not ben's private report
    await expect(page.locator('.report-row')).toContainText('adyen-annual-report-2025.pdf');
    await expect(page.getByText('Sample', { exact: true })).toBeVisible();
    await expect(page.getByTestId('analyses-left')).toHaveText('3 report analyses left today (of 3).');
    await expect(page.getByText('admin / admin-pass')).toHaveCount(0);
  });

  test('PUB-02 sign-up explains what to fix', async ({ page, api }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Create account' }).click();
    const form = page.getByRole('form', { name: 'Create account' });
    await form.getByLabel('Choose a username').fill('shreya');
    await form.getByLabel('Password', { exact: true }).fill('short');
    await form.getByLabel('Repeat password').fill('short');
    await form.getByRole('button', { name: 'Create account' }).click();
    await expect(form.getByRole('alert')).toHaveText('Use a password of at least 10 characters.');
    await form.getByLabel('Password', { exact: true }).fill('shreya-2026-x');
    await form.getByLabel('Repeat password').fill('shreya-2026-y');
    await form.getByRole('button', { name: 'Create account' }).click();
    await expect(form.getByRole('alert')).toHaveText('The two passwords don’t match.');
    await form.getByLabel('Repeat password').fill('shreya-2026-x');
    await form.getByRole('button', { name: 'Create account' }).click();
    await expect(form.getByRole('alert')).toContainText('confirm');
    await form.getByRole('checkbox').check();
    await form.getByRole('button', { name: 'Create account' }).click();
    await expect(form.getByRole('alert')).toContainText('too easy to guess'); // server rule: password contains the username
    await form.getByLabel('Choose a username').fill('demo');
    await form.getByLabel('Password', { exact: true }).fill('a calm green river');
    await form.getByLabel('Repeat password').fill('a calm green river');
    await form.getByRole('button', { name: 'Create account' }).click();
    await expect(form.getByRole('alert')).toHaveText('That username is taken. Please choose another.');
    expect(api.signedUp.size).toBe(0);
  });

  test('PUB-03 a returning user signs in with the account they created', async ({ page, api }) => {
    api.signedUp.set('shreya', 'my portfolio 2026');
    await page.goto('/login');
    const form = page.getByRole('form', { name: 'Sign in' });
    await form.getByLabel('Username').fill('Shreya');
    await form.getByLabel('Password').fill('my portfolio 2026');
    await form.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/reports');
    await expect(page.locator('nav.topbar .user-name')).toHaveText('shreya (USER)');
  });

  test('PUB-04 when the daily analysis allowance is used up, uploads explain when it resets', async ({ page, api }) => {
    api.limits = { analysesLeft: 0, analysesPerDay: 3, questionsLeft: 30, questionsPerDay: 30 };
    await signUp(page);
    await page.waitForURL('**/reports');
    await expect(page.getByTestId('analyses-left')).toContainText('You’ve used today’s 3 report analyses');
    await page.getByLabel('Choose a financial report to upload').setInputFiles({ name: 'asml-2025.pdf', mimeType: 'application/pdf', buffer: Buffer.from('pdf') });
    await expect(page.getByRole('alert')).toContainText('resets at midnight');
    await expect(page).toHaveURL(/\/reports$/);
  });

  test('PUB-05 questions show what is left today and the limit message is plain', async ({ page, api }) => {
    const r = api.addReport('northwind-fy26.pdf');
    api.limits = { analysesLeft: 3, analysesPerDay: 3, questionsLeft: 4, questionsPerDay: 30 };
    await signUp(page);
    await page.waitForURL('**/reports');
    await page.goto(`/reports/${r.id}`);
    await page.getByRole('button', { name: 'Ask questions about this report' }).click();
    await expect(page.getByTestId('questions-left')).toHaveText('4 questions left today');
    api.nextChatError = { status: 429, message: "You've asked 30 questions today, your daily limit. It resets at midnight (Amsterdam time)." };
    await page.getByPlaceholder('e.g. Can the company pay its short-term debts?').fill('Is the debt high?');
    await page.keyboard.press('Enter');
    await expect(page.getByText('your daily limit')).toBeVisible();
  });

  test('PUB-06 someone else’s private report can’t be opened by its link', async ({ page, api }) => {
    const theirs = api.addReport('private.pdf', 'fy26', 'ben');
    await signUp(page);
    await page.waitForURL('**/reports');
    await page.goto(`/reports/${theirs.id}`);
    await expect(page.getByText(/not found|no longer exists|could not load/i)).toBeVisible();
  });

  test('PUB-07 people can delete their account and everything they uploaded', async ({ page, api }) => {
    await signUp(page);
    await page.waitForURL('**/reports');
    api.addReport('my-upload.pdf', 'fy26', 'shreya');
    await page.getByRole('contentinfo').getByRole('link', { name: 'Your account' }).click();
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByTestId('analyses-left')).toHaveText('3 report analyses left of 3');
    await page.getByRole('button', { name: 'Delete my account…' }).click();
    const confirm = page.getByRole('button', { name: 'Delete everything' });
    await expect(confirm).toBeDisabled();
    await page.getByLabel(/Type your username/).fill('shreya');
    await confirm.click();
    await expect(page).toHaveURL(/\/login\?deleted=1$/);
    await expect(page.getByText('Your account and all its data have been deleted.')).toBeVisible();
    expect(api.deletedAccounts).toEqual(['shreya']);
    expect(api.reports.some((r) => r.uploadedBy === 'shreya')).toBe(false);
    expect(await page.evaluate(() => localStorage.getItem('mentalist_token'))).toBeNull();
  });

  test('PUB-08 About & privacy is readable without signing in', async ({ page, api }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: 'About & privacy' }).click();
    await expect(page.getByRole('heading', { name: 'About & privacy' })).toBeVisible();
    await expect(page.getByText('Reports you upload are private', { exact: false })).toBeVisible();
    await expect(page.getByText('daily allowance', { exact: false })).toBeVisible();
    expect(await axe(page)).toEqual([]);
    await page.getByRole('link', { name: '← Sign-in page' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('PUB-09 login, sign-up and account pages pass the axe scan', async ({ page, api }) => {
    await page.goto('/login');
    expect(await axe(page)).toEqual([]);
    await page.getByRole('button', { name: 'Create account' }).click();
    expect(await axe(page)).toEqual([]);
    await signUp(page);
    await page.waitForURL('**/reports');
    await page.goto('/account');
    await expect(page.getByRole('heading', { name: 'Your account', exact: true })).toBeVisible();
    expect(await axe(page)).toEqual([]);
  });

  test('PUB-10 the owner signs in normally, has no daily limit and sees every report', async ({ page, api }) => {
    api.addReport('bens-report.pdf', 'fy26', 'ben');
    await page.goto('/login');
    const form = page.getByRole('form', { name: 'Sign in' });
    await form.getByLabel('Username').fill('admin');
    await form.getByLabel('Password').fill('admin-pass');
    await form.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/reports');
    await expect(page.getByTestId('analyses-left')).toHaveCount(0);
    await expect(page.locator('.report-row')).toContainText('bens-report.pdf');
    await page.goto('/account');
    await expect(page.getByRole('button', { name: 'Delete my account…' })).toHaveCount(0);
  });
});

test('PUB-11 locally nothing changes: no sign-up, demo hint shown, no allowance line', async ({ page, api }) => {
  await page.goto('/login');
  await expect(page.getByText('admin / admin-pass')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create account' })).toHaveCount(0);
  await login(page, 'demo');
  await expect(page.getByTestId('analyses-left')).toHaveCount(0);
  await expect(page.locator('nav.topbar .user-name')).toHaveText('demo (USER)');
});
