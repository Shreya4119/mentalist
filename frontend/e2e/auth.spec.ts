import { expect, login, openChat, test } from './fixtures/mock-api';

test.describe('Authentication & access control', () => {
  test('AUTH-01 login page shows Mentalist branding and demo hint', async ({ page, api }) => {
    await page.goto('/login');
    await expect(page).toHaveTitle(/Mentalist/);
    await expect(page.getByRole('heading', { name: 'Mentalist' })).toBeVisible();
    await expect(page.getByText('admin / admin-pass')).toBeVisible();
    await expect(page.getByText(/company reports/)).toBeVisible();
    await expect(page.getByText(/HR|payroll/)).toHaveCount(0);
    await expect(page.getByLabel('Username')).toBeFocused();
  });

  test('AUTH-02 wrong password shows an error and stores no token', async ({ page, api }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('nope');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Invalid username or password.')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate(() => localStorage.getItem('mentalist_token'))).toBeNull();
  });

  test('AUTH-03 empty form is not submitted to the server', async ({ page, api }) => {
    let loginCalls = 0;
    page.on('request', (r) => r.url().includes('/api/auth/login') && loginCalls++);
    await page.goto('/login');
    const signIn = page.getByRole('button', { name: 'Sign in' });
    await expect(signIn).toBeDisabled();
    await page.getByLabel('Username').fill('admin');
    await expect(signIn).toBeDisabled();
    await page.getByLabel('Password').press('Enter');
    await page.waitForTimeout(300);
    expect(loginCalls, 'empty credentials should be blocked client-side').toBe(0);
    await page.getByLabel('Password').fill('x');
    await expect(signIn).toBeEnabled();
  });

  test('AUTH-04 admin lands on Financial reports; there is no HR documents area', async ({ page, api }) => {
    await login(page, 'admin');
    await expect(page.getByRole('heading', { name: 'Financial reports' })).toBeVisible();
    const nav = page.locator('nav.topbar');
    await expect(nav.getByRole('link')).toHaveText(['Financial reports', 'My plan', 'Learn']);
    await expect(page.getByText('admin (ADMIN)')).toBeVisible();
    await expect(page.locator('.brand')).toHaveText('Mentalist');
  });

  test('AUTH-05 demo user signs in as a regular user', async ({ page, api }) => {
    await login(page, 'demo');
    await expect(page.getByText('demo (USER)')).toBeVisible();
    await expect(page.getByLabel('Choose a financial report to upload')).toBeAttached();
  });

  test('AUTH-06 old HR links and unknown paths land on Financial reports', async ({ page, api }) => {
    await login(page, 'demo');
    for (const path of ['/documents', '/chat', '/does-not-exist']) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/reports$/);
    }
  });

  test('AUTH-06b a session saved by the old HR version is cleared, so the user signs in again', async ({ page, api }) => {
    await page.goto('/login');
    await page.evaluate(() => {
      localStorage.setItem('hr_rag_token', 'mock-HR_ADMIN');
      localStorage.setItem('hr_rag_role', 'HR_ADMIN');
    });
    await page.goto('/reports');
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate(() => localStorage.getItem('hr_rag_token'))).toBeNull();
  });

  test('AUTH-07 signed-out user is sent to login from any protected route', async ({ page, api }) => {
    for (const path of ['/reports', '/reports/learn', '/', '/does-not-exist']) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/login$/);
    }
  });

  test('AUTH-08 logout clears the session and protects routes again', async ({ page, api }) => {
    await login(page, 'admin');
    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate(() => localStorage.getItem('mentalist_token'))).toBeNull();
    await page.goBack();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('AUTH-09 session survives a page reload', async ({ page, api }) => {
    await login(page, 'admin');
    await page.reload();
    await expect(page).toHaveURL(/\/reports$/);
    await expect(page.getByText('admin (ADMIN)')).toBeVisible();
  });

  test('AUTH-10 a 401 from any API call (expired token) logs the user out', async ({ page, api }) => {
    await openChat(page, api);
    api.failNext.chat = 401;
    await page.getByPlaceholder('Ask a question…').fill('hello');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page).toHaveURL(/\/login\?session=ended$/);
    await expect(page.getByText('Your session ended. Please sign in again.')).toBeVisible();
  });

  for (const status of [403, 404, 500]) {
    test(`AUTH-11 a ${status} from an API call does NOT log the user out`, async ({ page, api }) => {
      await openChat(page, api);
      api.failNext.chat = status;
      await page.getByPlaceholder('Ask a question…').fill('hello');
      await page.getByRole('button', { name: 'Send' }).click();
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page).toHaveURL(/\/reports\/[\w-]+\/chat$/);
      expect(await page.evaluate(() => localStorage.getItem('mentalist_token'))).not.toBeNull();
    });
  }
});
