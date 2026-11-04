import { test, expect, SYSTEM_DASHBOARD } from './helpers/fixtures';

test.describe('System dashboard', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(SYSTEM_DASHBOARD);
    // Wait for the JS to fire the first API call and populate the summary
    await page.waitForSelector('.th-card', { timeout: 15_000 });
  });

  // ---- page chrome ----

  test('renders the header with title', async ({ page }) => {
    await expect(page.locator('.th-header h1')).toContainText('TruffleHog');
  });

  test('renders breadcrumb showing System scope', async ({ page }) => {
    await expect(page.locator('.th-breadcrumbs')).toContainText('System');
  });

  test('Refresh button is present', async ({ page }) => {
    await expect(page.locator('#th-refresh')).toBeVisible();
  });

  test('Settings link navigates to admin page', async ({ page }) => {
    await page.locator('a[href*="/trufflehog/admin"]').first().click();
    await page.waitForURL(/\/trufflehog\/admin/, { timeout: 10_000 });
    expect(page.url()).toContain('/trufflehog/admin');
  });

  // ---- summary cards ----

  test('renders at least 5 KPI cards', async ({ page }) => {
    const cards = page.locator('.th-card');
    expect(await cards.count()).toBeGreaterThanOrEqual(5);
  });

  test('Open card shows a number', async ({ page }) => {
    const openCard = page.locator('.th-card--open .th-card-value');
    await expect(openCard).toBeVisible();
    const text = await openCard.textContent();
    expect(text).toMatch(/^\d+$/);
  });

  test('Verified open card is present', async ({ page }) => {
    await expect(page.locator('.th-card--verified')).toBeVisible();
    await expect(page.locator('.th-card--verified .th-card-label')).toContainText('Verified');
  });

  test('Fixed card is present', async ({ page }) => {
    await expect(page.locator('.th-card--fixed')).toBeVisible();
  });

  // ---- charts ----

  test('severity chart container is rendered', async ({ page }) => {
    await expect(page.locator('#th-chart-severity')).toBeVisible();
  });

  test('detector chart container is rendered', async ({ page }) => {
    await expect(page.locator('#th-chart-detector')).toBeVisible();
  });

  test('trend chart container is rendered', async ({ page }) => {
    await expect(page.locator('#th-chart-trend')).toBeVisible();
  });

  test('activity chart container is rendered', async ({ page }) => {
    await expect(page.locator('#th-chart-activity')).toBeVisible();
  });

  // ---- projects / repos panels ----

  test('projects panel is present on system dashboard', async ({ page }) => {
    await expect(page.locator('#th-projects')).toBeVisible();
  });

  test('top repos panel is present', async ({ page }) => {
    await expect(page.locator('#th-top-repos')).toBeVisible();
  });

  // ---- findings table structure ----

  test('findings table is rendered', async ({ page }) => {
    await expect(page.locator('table.th-table')).toBeVisible();
  });

  test('findings table has correct column headers', async ({ page }) => {
    const headers = page.locator('table.th-table thead th');
    const texts = await headers.allTextContents();
    const cleaned = texts.map(t => t.trim().replace(/[↑↓↕]/g, '').trim());
    expect(cleaned).toContain('Severity');
    expect(cleaned).toContain('Detector');
    expect(cleaned).toContain('Repository');
    expect(cleaned).toContain('Status');
  });

  test('sortable header columns have data-col attributes', async ({ page }) => {
    await expect(page.locator('th.th-sortable[data-col="severity"]')).toBeVisible();
    await expect(page.locator('th.th-sortable[data-col="lastSeenAt"]')).toBeVisible();
    await expect(page.locator('th.th-sortable[data-col="status"]')).toBeVisible();
  });

  // ---- filter bar ----

  test('filter bar is visible with search input', async ({ page }) => {
    await expect(page.locator('.th-filter-bar')).toBeVisible();
    await expect(page.locator('#th-filter-search')).toBeVisible();
  });

  test('status filter dropdown is visible', async ({ page }) => {
    await expect(page.locator('#th-filter-status')).toBeVisible();
  });

  test('verified-only checkbox is visible', async ({ page }) => {
    await expect(page.locator('#th-filter-verified')).toBeVisible();
  });

  // ---- pager ----

  test('pager prev/next buttons are rendered', async ({ page }) => {
    await expect(page.locator('#th-prev')).toBeVisible();
    await expect(page.locator('#th-next')).toBeVisible();
    await expect(page.locator('#th-pageinfo')).toBeVisible();
  });

  test('prev button is disabled on first page', async ({ page }) => {
    await page.waitForSelector('#th-findings tr', { timeout: 10_000 });
    await expect(page.locator('#th-prev')).toBeDisabled();
  });

  // ---- recent scans ----

  test('recent scans table is rendered', async ({ page }) => {
    await expect(page.locator('table.th-scans-table')).toBeVisible();
  });

  // ---- CSS loading ----

  test('dashboard CSS is applied (card has expected background)', async ({ page }) => {
    const bg = await page.locator('.th-card').first().evaluate(
      el => window.getComputedStyle(el).backgroundColor
    );
    // Should be white (rgb(255,255,255)) not transparent
    expect(bg).toMatch(/rgb\(255, 255, 255\)/);
  });

  test('no TRUFFLEHOG_CONTEXT errors in console', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await page.reload();
    await page.waitForSelector('.th-card', { timeout: 15_000 });
    const contextErrors = errors.filter(e => e.includes('TRUFFLEHOG') || e.includes('TypeError'));
    expect(contextErrors).toHaveLength(0);
  });
});
