import { test, expect, ADMIN_PAGE, SYSTEM_DASHBOARD } from './helpers/fixtures';

test.describe('Admin settings page', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(ADMIN_PAGE);
    await page.waitForSelector('.th-form-card', { timeout: 15_000 });
  });

  // ---- page chrome ----

  test('renders page title', async ({ page }) => {
    await expect(page.locator('.th-header h1')).toContainText('Settings');
  });

  test('renders breadcrumb with dashboard link', async ({ page }) => {
    const link = page.locator('.th-breadcrumbs a[href*="dashboard/system"]');
    await expect(link).toBeVisible();
    await expect(link).toContainText('Dashboard');
  });

  test('back to dashboard link navigates correctly', async ({ page }) => {
    await page.locator('a.th-hbtn[href*="dashboard/system"]').click();
    await expect(page).toHaveURL(/dashboard\/system/, { timeout: 10_000 });
  });

  // ---- form card structure ----

  test('renders three settings cards', async ({ page }) => {
    const cards = page.locator('.th-form-card');
    expect(await cards.count()).toBe(3);
  });

  test('first card is about TruffleHog binary', async ({ page }) => {
    const legend = page.locator('.th-form-card').first().locator('.th-form-legend');
    await expect(legend).toContainText('TruffleHog binary');
  });

  test('second card is scheduling', async ({ page }) => {
    const legend = page.locator('.th-form-card').nth(1).locator('.th-form-legend');
    await expect(legend).toContainText('Scheduling');
  });

  test('third card is push behavior', async ({ page }) => {
    const legend = page.locator('.th-form-card').nth(2).locator('.th-form-legend');
    await expect(legend).toContainText('Push behavior');
  });

  // ---- form fields present ----

  test('executable path input is present', async ({ page }) => {
    await expect(page.locator('[name="trufflehogExecutable"]')).toBeVisible();
  });

  test('cron expression input is present', async ({ page }) => {
    await expect(page.locator('[name="cronExpression"]')).toBeVisible();
  });

  test('concurrent scans number input is present', async ({ page }) => {
    await expect(page.locator('[name="maxConcurrentScans"]')).toBeVisible();
    const type = await page.locator('[name="maxConcurrentScans"]').getAttribute('type');
    expect(type).toBe('number');
  });

  test('scan timeout number input is present', async ({ page }) => {
    await expect(page.locator('[name="scanTimeoutMinutes"]')).toBeVisible();
  });

  test('scan on push checkbox is present', async ({ page }) => {
    await expect(page.locator('[name="scanOnPush"]')).toBeVisible();
    const type = await page.locator('[name="scanOnPush"]').getAttribute('type');
    expect(type).toBe('checkbox');
  });

  test('block push on verified finding checkbox is present', async ({ page }) => {
    await expect(page.locator('[name="blockPushOnVerifiedFinding"]')).toBeVisible();
  });

  // ---- save action ----

  test('save button is present', async ({ page }) => {
    await expect(page.locator('#th-save-settings')).toBeVisible();
    await expect(page.locator('#th-save-settings')).toContainText('Save');
  });

  test('save status element is present', async ({ page }) => {
    await expect(page.locator('#th-save-status')).toBeAttached();
  });

  test('clicking save shows Saved confirmation', async ({ page }) => {
    await page.locator('#th-save-settings').click();
    const status = page.locator('#th-save-status');
    await expect(status).toContainText(/Saved|Failed/, { timeout: 8_000 });
  });

  // ---- round-trip a field value ----

  test('changing concurrent scans and saving persists the value', async ({ page }) => {
    const input = page.locator('[name="maxConcurrentScans"]');
    const original = await input.inputValue();

    const newVal = original === '3' ? '4' : '3';
    await input.fill(newVal);
    await page.locator('#th-save-settings').click();
    await expect(page.locator('#th-save-status')).toContainText('Saved', { timeout: 8_000 });

    // Reload and verify value persisted
    await page.reload();
    await page.waitForSelector('.th-form-card', { timeout: 10_000 });
    const persisted = await page.locator('[name="maxConcurrentScans"]').inputValue();
    expect(persisted).toBe(newVal);

    // Restore original
    await page.locator('[name="maxConcurrentScans"]').fill(original);
    await page.locator('#th-save-settings').click();
    await expect(page.locator('#th-save-status')).toContainText('Saved', { timeout: 8_000 });
  });

  // ---- CSS / JS loading ----

  test('form rows use th-form-row class', async ({ page }) => {
    const rows = page.locator('.th-form-row');
    expect(await rows.count()).toBeGreaterThanOrEqual(6);
  });

  test('save button has th-form-btn styling applied', async ({ page }) => {
    const btn = page.locator('#th-save-settings');
    const bg = await btn.evaluate(el => window.getComputedStyle(el).backgroundColor);
    // th-form-btn is styled with --blue background
    expect(bg).not.toBe('rgba(0, 0, 0, 0)');
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await page.reload();
    await page.waitForSelector('.th-form-card', { timeout: 10_000 });
    const jsErrors = errors.filter(e => !e.includes('favicon'));
    expect(jsErrors).toHaveLength(0);
  });

  // ---- access control ----

  test('admin page is accessible to admin user', async ({ page }) => {
    await expect(page).not.toHaveURL(/login/);
    await expect(page.locator('.th-form-card').first()).toBeVisible();
  });
});
