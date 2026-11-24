import AxeBuilder from '@axe-core/playwright';
import { expect, login, test } from './fixtures/mock-api';
import { NORTHWIND_LEI } from './fixtures/company-fixtures';

test.describe('Companies (official filings)', () => {
  test('COMP-01 typing a company name and pressing Analyse opens its analysis, no upload needed', async ({ page, api }) => {
    await login(page, 'demo');
    await page.locator('nav.topbar').getByRole('link', { name: 'Companies' }).click();
    await expect(page.getByRole('heading', { name: 'Companies', level: 1 })).toBeVisible();
    await expect(page.getByText('No companies yet.')).toBeVisible();
    await page.getByLabel('Company name').fill('northwind industries');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
    expect(api.companyImports).toEqual([{ lei: NORTHWIND_LEI, refresh: false }]);
    await expect(page.getByRole('heading', { name: 'Northwind Industries NV', level: 1 })).toBeVisible();
    await expect(page.getByText('4 years of figures from official filings')).toBeVisible();
  });

  test('COMP-01b legal forms are ignored; similar names ask which company you meant', async ({ page, api }) => {
    await login(page, 'demo');
    await page.goto('/companies');
    await page.getByLabel('Company name').fill('Northwind');
    await page.getByRole('button', { name: 'Analyse', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Which company did you mean?' })).toBeVisible();
    expect(api.companyImports).toEqual([]);
    await page.getByRole('button', { name: 'Analyse Northwind Industries NV' }).click();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
    // Already analysed: typing "Northwind Industries N.V." opens it without reading the filings again.
    await page.goto('/companies');
    await page.getByLabel('Company name').fill('Northwind Industries N.V.');
    await page.getByRole('button', { name: 'Analyse', exact: true }).click();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
    expect(api.companyImports).toHaveLength(1);
  });

  test('COMP-02 detail page shows trends, the three scores, findings and sources', async ({ page, api }) => {
    api.addCompany();
    await login(page, 'demo');
    await page.goto(`/companies/${NORTHWIND_LEI}`);
    await expect(page.getByTestId('revenue-cagr')).toHaveText('14.5%');
    await expect(page.getByTestId('piotroski-score')).toHaveText('8');
    await expect(page.getByTestId('altman-z')).toHaveText('3.96');
    await expect(page.getByText('Safe zone', { exact: true })).toBeVisible();
    await expect(page.getByTestId('beneish-m')).toHaveText('-2.49');
    await expect(page.getByText('No unusual patterns')).toBeVisible();
    await expect(page.getByText('Customers are paying more slowly')).toBeVisible();
    await expect(page.getByText('Steady sales growth')).toBeVisible();
    const table = page.getByRole('table').filter({ hasText: 'Operating cash flow' }).last();
    await expect(table.getByRole('row', { name: /^Revenue/ })).toContainText('1.2 bn');
    const link = page.getByRole('link', { name: 'annual report 2024' });
    await expect(link).toHaveAttribute('href', /filings\.xbrl\.org/);
    await expect(link).toHaveAttribute('rel', 'noopener');
    await expect(page.getByText('(comparative figures)')).toBeVisible();
    await expect(page.getByText(/not investment advice/)).toBeVisible();
    // Regular users can't force a re-import.
    await expect(page.getByRole('button', { name: 'Refresh from filings' })).toHaveCount(0);
  });

  test('COMP-03 imported companies are listed; admin can refresh from filings', async ({ page, api }) => {
    api.addCompany();
    await login(page, 'admin');
    await page.goto('/companies');
    await page.getByRole('link', { name: /Northwind Industries NV/ }).click();
    await page.getByRole('button', { name: 'Refresh from filings' }).click();
    await expect(page.getByRole('button', { name: 'Refresh from filings' })).toBeEnabled();
    expect(api.companyImports).toEqual([{ lei: NORTHWIND_LEI, refresh: true }]);
  });

  test('COMP-04 problems are explained, not hidden', async ({ page, api }) => {
    await login(page, 'demo');
    await page.goto('/companies');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await expect(page.getByRole('alert')).toHaveText('Type at least 2 letters of the company name.');
    await page.getByLabel('Company name').fill('riverside');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await expect(page.getByRole('alert')).toHaveText('No annual filings were found for this company.');
    await page.getByLabel('Company name').fill('zzzz');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await expect(page.getByText(/No companies found/)).toBeVisible();
    api.nextImportError = { status: 429, message: "You've used your 5 company imports for today." };
    await page.getByLabel('Company name').fill('northwind industries');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await expect(page.getByRole('alert')).toHaveText("You've used your 5 company imports for today.");
    await expect(page).toHaveURL(/\/companies$/);
  });

  test('COMP-05 shows a progress notice while filings are read', async ({ page, api }) => {
    api.importDelayMs = 800;
    await login(page, 'demo');
    await page.goto('/companies');
    await page.getByLabel('Company name').fill('northwind industries');
    await page.getByRole('button', { name: 'Analyse' }).click();
    await expect(page.getByRole('status')).toContainText('Reading the official filings');
    await expect(page.getByRole('button', { name: 'Analysing…' })).toBeDisabled();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
  });

  test('COMP-07 browse every company of a country, filter by name and analyse one', async ({ page, api }) => {
    await login(page, 'demo');
    await page.goto('/companies');
    await page.getByLabel('Country', { exact: true }).selectOption({ label: 'Netherlands' });
    await expect(page.getByText('71 companies in Netherlands')).toBeVisible();
    const list = page.getByRole('list', { name: 'Companies in Netherlands' });
    await expect(list.getByRole('listitem')).toHaveCount(50);
    await page.getByRole('button', { name: 'Show more (21 left)' }).click();
    await expect(list.getByRole('listitem')).toHaveCount(71);
    await expect(page.getByRole('button', { name: /Show more/ })).toHaveCount(0);
    await page.getByLabel('Filter by name').fill('polder');
    await expect(page.getByText('1 company match in Netherlands')).toBeVisible();
    await expect(list.getByRole('listitem')).toHaveText([/Polder Bank NV\s*latest report 2025/]);
    await page.getByLabel('Filter by name').fill('northwind');
    await page.getByRole('button', { name: 'Analyse Northwind Industries NV' }).click();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
    expect(api.companyImports).toEqual([{ lei: NORTHWIND_LEI, refresh: false }]);
    // Back on the list the company now opens without reading the filings again.
    await page.goto('/companies');
    await page.getByLabel('Country', { exact: true }).selectOption({ label: 'Netherlands' });
    await page.getByLabel('Filter by name').fill('northwind');
    await page.getByRole('button', { name: 'Open Northwind Industries NV' }).click();
    await page.waitForURL(`**/companies/${NORTHWIND_LEI}`);
    expect(api.companyImports).toHaveLength(1);
  });

  test('COMP-08 a country list that cannot be loaded says so', async ({ page, api }) => {
    await login(page, 'demo');
    await page.goto('/companies');
    await page.getByLabel('Country', { exact: true }).selectOption({ label: 'Germany' });
    await expect(page.getByRole('alert')).toContainText("couldn't be reached");
    await page.getByLabel('Country', { exact: true }).selectOption({ label: 'Netherlands' });
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.getByText('71 companies in Netherlands')).toBeVisible();
    expect(api.directoryCalls).toEqual(['DE', 'NL']);
  });

  test('COMP-06 company pages pass an automated accessibility check', async ({ page, api }) => {
    api.addCompany();
    await login(page, 'demo');
    await page.goto(`/companies/${NORTHWIND_LEI}`);
    await expect(page.getByTestId('piotroski-score')).toBeVisible();
    const detail = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    expect(detail.violations.flatMap((v) => v.nodes.map((n) => v.id + ' ' + n.target.join(' ') + ' ' + (n.any[0]?.message ?? '')))).toEqual([]);
    await page.goto('/companies');
    await expect(page.getByRole('link', { name: /Northwind/ })).toBeVisible();
    await page.getByLabel('Country', { exact: true }).selectOption({ label: 'Netherlands' });
    await expect(page.getByText('71 companies in Netherlands')).toBeVisible();
    const list = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    expect(list.violations.map((v) => v.id)).toEqual([]);
  });
});
