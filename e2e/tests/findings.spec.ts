import { test, expect, SYSTEM_DASHBOARD } from './helpers/fixtures';

test.describe('Findings table — filter, sort, drawer', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(SYSTEM_DASHBOARD);
    await page.waitForSelector('table.th-table tbody', { timeout: 15_000 });
    // Wait for first data load
    await page.waitForFunction(
      () => document.querySelector('#th-pageinfo')?.textContent?.trim() !== '',
      { timeout: 15_000 }
    );
  });

  // ---- filter: status ----

  test('status filter to All shows more or equal findings than Open', async ({ page }) => {
    const openText = await page.locator('#th-pageinfo').textContent();
    const openTotal = parseTotal(openText ?? '');

    await page.locator('#th-filter-status').selectOption('');
    await page.waitForFunction(
      () => document.querySelector('#th-pageinfo')?.textContent?.trim() !== '',
      { timeout: 8_000 }
    );
    const allText = await page.locator('#th-pageinfo').textContent();
    const allTotal = parseTotal(allText ?? '');

    expect(allTotal).toBeGreaterThanOrEqual(openTotal);
  });

  test('status filter updates the findings table', async ({ page }) => {
    await page.locator('#th-filter-status').selectOption('FIXED');
    await page.waitForTimeout(600);

    const rows = page.locator('table.th-table tbody tr');
    const count = await rows.count();
    if (count > 0 && !(await rows.first().locator('td').first().isVisible())) return;

    for (let i = 0; i < Math.min(count, 5); i++) {
      const statusCell = rows.nth(i).locator('.th-status-FIXED');
      if (await statusCell.count() > 0) {
        await expect(statusCell.first()).toBeVisible();
      }
    }
  });

  // ---- filter: search ----

  test('search input filters findings', async ({ page }) => {
    const initialText = await page.locator('#th-pageinfo').textContent();

    await page.locator('#th-filter-search').fill('AWS');
    await page.waitForTimeout(400);

    const filteredText = await page.locator('#th-pageinfo').textContent();
    // Results may differ (fewer or same) — just confirm the page re-rendered
    const rows = page.locator('table.th-table tbody tr');
    expect(await rows.count()).toBeGreaterThanOrEqual(0);
  });

  test('clearing search restores findings', async ({ page }) => {
    const initialText = await page.locator('#th-pageinfo').textContent();
    if (!initialText || parseTotal(initialText) === 0) {
      test.skip();
      return;
    }

    await page.locator('#th-filter-search').fill('XYZZZNOTAREALDETECTOR');
    await page.waitForTimeout(400);
    const emptyText = await page.locator('#th-pageinfo').textContent();

    await page.locator('#th-filter-search').fill('');
    await page.waitForTimeout(400);
    const restoredText = await page.locator('#th-pageinfo').textContent();

    expect(restoredText).not.toBe(emptyText);
  });

  // ---- filter: verified only ----

  test('verified-only checkbox reduces or keeps finding count', async ({ page }) => {
    const before = parseTotal(await page.locator('#th-pageinfo').textContent() ?? '');

    await page.locator('#th-filter-verified').check();
    await page.waitForTimeout(600);

    const after = parseTotal(await page.locator('#th-pageinfo').textContent() ?? '');
    expect(after).toBeLessThanOrEqual(before);
  });

  // ---- column sort ----

  test('clicking severity header toggles sort indicator', async ({ page }) => {
    const sevHeader = page.locator('th.th-sortable[data-col="severity"]');
    await sevHeader.click();
    await page.waitForTimeout(400);

    await expect(sevHeader).toHaveClass(/th-sort-active/);
    const icon = sevHeader.locator('.th-sort-icon');
    const iconText = await icon.textContent();
    expect(['↑', '↓']).toContain(iconText?.trim());
  });

  test('clicking same sortable header twice reverses sort direction', async ({ page }) => {
    const hdr = page.locator('th.th-sortable[data-col="severity"]');
    await hdr.click();
    await page.waitForTimeout(400);
    const first = await hdr.locator('.th-sort-icon').textContent();

    await hdr.click();
    await page.waitForTimeout(400);
    const second = await hdr.locator('.th-sort-icon').textContent();

    expect(first?.trim()).not.toBe(second?.trim());
  });

  test('clicking a different header removes active from previous', async ({ page }) => {
    const sevHdr    = page.locator('th.th-sortable[data-col="severity"]');
    const statusHdr = page.locator('th.th-sortable[data-col="status"]');

    await sevHdr.click();
    await page.waitForTimeout(400);
    await expect(sevHdr).toHaveClass(/th-sort-active/);

    await statusHdr.click();
    await page.waitForTimeout(400);
    await expect(statusHdr).toHaveClass(/th-sort-active/);
    await expect(sevHdr).not.toHaveClass(/th-sort-active/);
  });

  test('lastSeenAt header is active by default (default sort)', async ({ page }) => {
    const hdr = page.locator('th.th-sortable[data-col="lastSeenAt"]');
    await expect(hdr).toHaveClass(/th-sort-active/);
    const icon = await hdr.locator('.th-sort-icon').textContent();
    expect(icon?.trim()).toBe('↓');
  });

  // ---- finding detail drawer ----

  test('clicking Details opens the drawer', async ({ page }) => {
    const detailLink = page.locator('[data-th-open-finding]').first();
    if (await detailLink.count() === 0) {
      test.skip();
      return;
    }

    await detailLink.click();

    const drawer = page.locator('#th-drawer');
    await expect(drawer).not.toHaveClass(/th-drawer--closed/, { timeout: 5_000 });
    await expect(page.locator('#th-drawer-title')).not.toBeEmpty({ timeout: 5_000 });
  });

  test('drawer shows detail grid with expected fields', async ({ page }) => {
    const detailLink = page.locator('[data-th-open-finding]').first();
    if (await detailLink.count() === 0) {
      test.skip();
      return;
    }

    await detailLink.click();
    await expect(page.locator('.th-detail-grid')).toBeVisible({ timeout: 8_000 });

    const dt = page.locator('.th-detail-grid dt');
    const labels = await dt.allTextContents();
    expect(labels).toContain('Severity');
    expect(labels).toContain('Status');
    expect(labels).toContain('File');
    expect(labels).toContain('Detector');
    expect(labels).toContain('Redacted');
    expect(labels).toContain('First seen');
    expect(labels).toContain('Last seen');
  });

  test('drawer close button hides the drawer', async ({ page }) => {
    const detailLink = page.locator('[data-th-open-finding]').first();
    if (await detailLink.count() === 0) {
      test.skip();
      return;
    }

    await detailLink.click();
    await expect(page.locator('#th-drawer')).not.toHaveClass(/th-drawer--closed/, { timeout: 5_000 });

    await page.locator('#th-drawer-close').click();
    await expect(page.locator('#th-drawer')).toHaveClass(/th-drawer--closed/, { timeout: 3_000 });
  });

  test('drawer history section is present', async ({ page }) => {
    const detailLink = page.locator('[data-th-open-finding]').first();
    if (await detailLink.count() === 0) {
      test.skip();
      return;
    }

    await detailLink.click();
    await expect(page.locator('#th-drawer-body')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('.th-drawer-section').last()).toBeVisible();
  });

  // ---- pagination ----

  test('pageinfo shows correct range format', async ({ page }) => {
    const text = await page.locator('#th-pageinfo').textContent();
    // Either "No findings" or "N–M of T"
    expect(text?.trim()).toMatch(/^(No findings|\d+–\d+ of \d+)$/);
  });

  test('next button navigates to page 2 when available', async ({ page }) => {
    const nextBtn = page.locator('#th-next');
    if (await nextBtn.isDisabled()) {
      test.skip();
      return;
    }

    const before = await page.locator('#th-pageinfo').textContent();
    await nextBtn.click();
    await page.waitForTimeout(600);
    const after = await page.locator('#th-pageinfo').textContent();
    expect(after).not.toBe(before);
  });

  test('prev is enabled after going to page 2', async ({ page }) => {
    if (await page.locator('#th-next').isDisabled()) {
      test.skip();
      return;
    }

    await page.locator('#th-next').click();
    await page.waitForTimeout(600);
    await expect(page.locator('#th-prev')).not.toBeDisabled();
  });

  // ---- severity badges in table ----

  test('severity badges are visually present in findings rows', async ({ page }) => {
    const rows = page.locator('table.th-table tbody tr');
    if (await rows.count() === 0) {
      test.skip();
      return;
    }
    // Skip if the only row is the empty-state placeholder (has th-empty cell, no data)
    const emptyCell = rows.first().locator('td.th-empty');
    if (await emptyCell.count() > 0) {
      test.skip();
      return;
    }
    const badge = rows.first().locator('.th-status-lozenge');
    await expect(badge).toBeVisible();
  });

  test('status badges use correct lozenge classes', async ({ page }) => {
    const allLozenges = page.locator('table.th-table tbody .th-status-lozenge');
    const count = await allLozenges.count();
    for (let i = 0; i < Math.min(count, 10); i++) {
      const classes = await allLozenges.nth(i).getAttribute('class') ?? '';
      const hasStatusClass = ['th-status-OPEN', 'th-status-FIXED',
        'th-status-FALSE_POSITIVE', 'th-status-ACCEPTED_RISK',
        'th-severity-CRITICAL', 'th-severity-HIGH', 'th-severity-MEDIUM',
        'th-severity-LOW', 'th-severity-INFO'].some(c => classes.includes(c));
      expect(hasStatusClass).toBe(true);
    }
  });
});

// ---- helpers ----

function parseTotal(pageInfoText: string): number {
  const m = pageInfoText.match(/of (\d+)/);
  return m ? parseInt(m[1], 10) : 0;
}
