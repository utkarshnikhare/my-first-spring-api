const { randomInt, randomUUID } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { test: base, expect } = require('@playwright/test');

const localAuthFile = path.join(__dirname, 'test-results', 'local-auth-secrets.json');

const test = base.extend({
  browserIssues: async ({ page }, use, testInfo) => {
    const issues = [];
    const expectedAccessDenials = [];
    const pendingErrorBodies = [];
    page.on('console', message => {
      if (message.type() !== 'error') return;
      const status = message.text().match(/status of (\d{3})/);
      if (status && [401, 403, 409].includes(Number(status[1]))) return;
      issues.push(`console.error: ${message.text()}`);
    });
    page.on('pageerror', error => issues.push(`pageerror: ${error.message}`));
    page.on('requestfailed', request => {
      if (new URL(request.url()).pathname.startsWith('/api/')) {
        issues.push(`API request failed: ${request.method()} ${new URL(request.url()).pathname}`);
      }
    });
    page.on('response', response => {
      const url = new URL(response.url());
      if (url.pathname.startsWith('/api/') && response.status() >= 400) {
        const detail = `API ${response.status()}: ${response.request().method()} ${url.pathname}`;
        const expectedDuplicateRegistration = response.status() === 409
          && response.request().method() === 'POST'
          && url.pathname === '/api/auth/register/buyer';
        if ([401, 403].includes(response.status()) || expectedDuplicateRegistration) {
          expectedAccessDenials.push(detail);
        }
        else if (response.status() === 409 && url.pathname === '/api/notifications') {
          pendingErrorBodies.push(response.text().then(body => issues.push(`${detail}: ${body}`)));
        } else issues.push(detail);
      }
    });

    await use(issues);
    await Promise.all(pendingErrorBodies);
    if (expectedAccessDenials.length) {
      await testInfo.attach('expected-auth-denials.txt', {
        body: Buffer.from(expectedAccessDenials.join('\n')),
        contentType: 'text/plain'
      });
    }
    if (issues.length) {
      await testInfo.attach('browser-and-api-errors.txt', {
        body: Buffer.from(issues.join('\n')),
        contentType: 'text/plain'
      });
      expect(issues, 'unexpected browser or server errors').toEqual([]);
    }
  }
});

function newIdentity(prefix) {
  const token = randomUUID().replaceAll('-', '').slice(0, 10);
  return {
    token,
    name: `${prefix} ${token}`,
    mobile: `6${randomInt(100_000_000, 1_000_000_000)}`,
    password: `E2E-${randomUUID().replaceAll('-', '').slice(0, 20)}!`
  };
}

async function apiJson(page, path, { method = 'GET', body } = {}) {
  return page.evaluate(async ({ path, method, body }) => {
    const init = { method, credentials: 'same-origin' };
    if (body !== undefined) {
      init.headers = { 'Content-Type': 'application/json' };
      init.body = JSON.stringify(body);
    }
    const response = await fetch(path, init);
    const text = await response.text();
    let result = text;
    try {
      result = text ? JSON.parse(text) : null;
    } catch (error) {
      result = text;
    }
    return { status: response.status, body: result };
  }, { path, method, body });
}

async function readMe(page) {
  return apiJson(page, '/api/auth/me');
}

async function logout(page) {
  const response = await apiJson(page, '/api/auth/logout', { method: 'POST' });
  expect(response.status).toBeLessThan(400);
  await page.reload();
}

async function openBuyerRegistration(page) {
  await page.goto('/index.html#/favourites');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  const modal = page.locator('#modalRoot');
  await modal.getByRole('button', { name: 'Create buyer account' }).click();
  await expect(page.locator('#buyerRegistrationForm')).toBeVisible();
}

async function fillBuyerRegistration(page, buyer) {
  await page.locator('#buyerRegName').fill(buyer.name);
  await page.locator('#buyerRegMobile').fill(buyer.mobile);
  await page.locator('#buyerRegPassword').fill(buyer.password);
  await page.locator('#buyerRegArea').selectOption({ index: 1 });
  await expect.poll(() => page.locator('#buyerRegSociety option').count())
    .toBeGreaterThan(1);
  await page.locator('#buyerRegSociety').selectOption({ index: 1 });
  await page.locator('#buyerRegBuilding').fill('E2E Tower');
  await page.locator('#buyerRegFlat').fill(`A-${buyer.token.slice(0, 4)}`);
}

async function registerBuyer(page) {
  const buyer = newIdentity('E2E Buyer');
  await openBuyerRegistration(page);
  await fillBuyerRegistration(page, buyer);
  await page.locator('#buyerRegistrationForm').getByRole('button', { name: 'Create account' }).click();
  await expect(page.locator('#modalRoot')).toBeHidden();
  await expect.poll(async () => (await readMe(page)).body).toMatchObject({
    authenticated: true,
    role: 'BUYER',
    mobileNumber: buyer.mobile
  });
  return buyer;
}

async function loginBuyer(page, buyer) {
  await page.goto('/index.html#/favourites');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await page.locator('#authMobile').fill(buyer.mobile);
  await page.locator('#authPassword').fill(buyer.password);
  const responsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('#authForm').getByRole('button', { name: 'Log in', exact: true }).click();
  expect((await responsePromise).status()).toBe(200);
  await expect.poll(async () => (await readMe(page)).body).toMatchObject({
    authenticated: true,
    role: 'BUYER',
    mobileNumber: buyer.mobile
  });
}

async function registerSeller(page) {
  const seller = newIdentity('E2E Seller');
  seller.kitchenName = `E2E Kitchen ${seller.token}`;
  seller.slug = `e2e-kitchen-${seller.token}`;
  seller.whatsapp = `8${randomInt(100_000_000, 1_000_000_000)}`;

  await page.goto('/seller.html');
  await page.getByRole('button', { name: 'Register as a seller' }).click();
  const form = page.locator('#sellerRegistrationForm');
  await expect(form).toBeVisible();
  await page.locator('#sellerRegName').fill(seller.name);
  await page.locator('#sellerRegMobile').fill(seller.mobile);
  await page.locator('#sellerRegPassword').fill(seller.password);
  await page.locator('#sellerRegWhatsApp').fill(seller.whatsapp);
  await page.locator('#sellerRegKitchen').fill(seller.kitchenName);
  await page.locator('#sellerRegSlug').fill(seller.slug);
  await page.locator('#sellerRegSpeciality').fill('E2E home-cooked meals');
  await page.locator('#sellerRegCategory').selectOption('KITCHEN');
  await page.locator('#sellerRegPrimaryArea').selectOption({ index: 0 });
  await page.locator('#sellerRegPrimarySociety').selectOption({ index: 0 });
  await page.locator('#sellerRegServiceArea').selectOption({ index: 0 });
  await page.locator('#sellerRegSocieties input[type="checkbox"]').first().check();
  await form.getByRole('button', { name: 'Submit seller application' }).click();
  await expect(page.getByRole('heading', { name: 'Seller application pending' })).toBeVisible();
  await expect(page.getByText('Current status: PENDING')).toBeVisible();
  await expect(page.locator('body')).toContainText(seller.slug);
  await expect.poll(async () => (await readMe(page)).body).toMatchObject({
    authenticated: true,
    role: 'SELLER',
    sellerApprovalStatus: 'PENDING',
    mobileNumber: seller.mobile
  });
  seller.id = (await readMe(page)).body.userId;
  expect(seller.id).toEqual(expect.any(Number));
  return seller;
}

async function loginSeller(page, seller, approvalStatus = 'APPROVED') {
  await page.goto('/seller.html');
  const form = page.locator('#sellerLoginForm');
  await expect(form).toBeVisible();
  await page.locator('#sellerLoginMobile').fill(seller.mobile);
  await page.locator('#sellerLoginPassword').fill(seller.password);
  const responsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Sign in' }).click();
  expect((await responsePromise).status()).toBe(200);
  await expect.poll(async () => (await readMe(page)).body).toMatchObject({
    authenticated: true,
    role: 'SELLER',
    sellerApprovalStatus: approvalStatus,
    mobileNumber: seller.mobile
  });
}

async function adminLogin(page, password = JSON.parse(fs.readFileSync(localAuthFile, 'utf8')).adminPassword) {
  if (!password) throw new Error('The local E2E Admin secret was not provided by the Playwright config.');
  await page.goto('/admin.html');
  await expect(page.locator('#alMobile')).toBeVisible();
  const config = await apiJson(page, '/api/auth/config');
  expect(config.body).toMatchObject({
    directAuthEnabled: true,
    adminLoginConfigured: true,
    superAdminLoginConfigured: true
  });
  await page.locator('#alMobile').fill('9000000002');
  await page.locator('#alPassword').fill(password);
  const responsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('[data-action="admin-login"]').click();
  return responsePromise;
}

module.exports = {
  test,
  expect,
  apiJson,
  readMe,
  logout,
  newIdentity,
  openBuyerRegistration,
  fillBuyerRegistration,
  registerBuyer,
  loginBuyer,
  registerSeller,
  loginSeller,
  adminLogin
};
