import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.BITBUCKET_URL ?? 'http://localhost:7990/bitbucket';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    storageState: 'tests/helpers/auth.json',
  },

  projects: [
    {
      name: 'setup',
      testDir: './tests',
      testMatch: '**/global-setup.ts',
      use: { storageState: undefined },
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },
  ],

  timeout: 30_000,
  expect: { timeout: 8_000 },
});
