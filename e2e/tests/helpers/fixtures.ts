import { test as base, request as baseRequest, APIRequestContext } from '@playwright/test';

const BASE = process.env.BITBUCKET_URL ?? 'http://localhost:7990/bitbucket';
const USER = process.env.BITBUCKET_USER ?? 'admin';
const PASS = process.env.BITBUCKET_PASS ?? 'admin';

export const REST = `${BASE}/rest/th-scanner/1.0`;

/** Pre-authenticated API request context (Basic auth, no cookie needed). */
export async function apiContext(): Promise<APIRequestContext> {
  return baseRequest.newContext({
    baseURL: BASE,
    extraHTTPHeaders: {
      Authorization: `Basic ${Buffer.from(`${USER}:${PASS}`).toString('base64')}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Atlassian-Token': 'no-check',
    },
  });
}

// Full URLs so page.goto() doesn't strip the /bitbucket context path
export const SYSTEM_DASHBOARD  = `${BASE}/plugins/servlet/trufflehog/dashboard/system`;
export const ADMIN_PAGE        = `${BASE}/plugins/servlet/trufflehog/admin`;

/** Extend base test with a typed API context fixture. */
export const test = base.extend<{ api: APIRequestContext }>({
  api: async ({}, use) => {
    const ctx = await apiContext();
    await use(ctx);
    await ctx.dispose();
  },
});

export { expect } from '@playwright/test';
