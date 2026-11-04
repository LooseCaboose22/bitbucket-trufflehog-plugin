import { test as setup, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, 'tests/helpers/auth.json');
const BASE = process.env.BITBUCKET_URL ?? 'http://localhost:7990/bitbucket';
const USER = process.env.BITBUCKET_USER ?? 'admin';
const PASS = process.env.BITBUCKET_PASS ?? 'admin';

setup('authenticate', async ({ page }) => {
  await page.goto(`${BASE}/login`);

  await page.locator('#j_username').fill(USER);
  await page.locator('#j_password').fill(PASS);
  await page.locator('[name="submit"]').click();

  // Wait until we land on the dashboard (not still on login)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
  await expect(page).toHaveURL(/bitbucket/, { timeout: 15_000 });

  await page.context().storageState({ path: AUTH_FILE });
  console.log('Auth state saved to', AUTH_FILE);
});
