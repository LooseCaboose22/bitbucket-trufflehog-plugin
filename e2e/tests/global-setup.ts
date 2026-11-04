import { test as setup, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, 'helpers/auth.json');
const BASE = process.env.BITBUCKET_URL ?? 'http://localhost:7990/bitbucket';
const USER = process.env.BITBUCKET_USER ?? 'admin';
const PASS = process.env.BITBUCKET_PASS ?? 'admin';

setup('authenticate', async ({ page }) => {
  await page.goto(`${BASE}/login`);
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});

  // If existing session cookies redirected us away from login, just save and exit
  if (!page.url().includes('/login')) {
    await page.context().storageState({ path: AUTH_FILE });
    console.log('Already authenticated — auth state saved to', AUTH_FILE);
    return;
  }

  await page.locator('#j_username').fill(USER);
  await page.locator('#j_password').fill(PASS);
  await page.locator('[name="submit"]').click();

  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });
  await expect(page).toHaveURL(/bitbucket/, { timeout: 15_000 });

  await page.context().storageState({ path: AUTH_FILE });
  console.log('Auth state saved to', AUTH_FILE);
});
