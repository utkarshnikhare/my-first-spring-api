const {
  test,
  expect,
  registerBuyer,
  logout,
  registerSeller,
  loginSeller,
  readMe
} = require('./support.cjs');

test('mobile registration dialogs and long seller onboarding remain usable and authenticated', async ({
  page,
  browserIssues
}) => {
  const buyer = await registerBuyer(page);
  const buyerAuth = await readMe(page);
  expect(buyerAuth.body.mobileNumber).toBe(buyer.mobile);
  const buyerOverflow = await page.evaluate(() =>
    document.documentElement.scrollWidth > window.innerWidth);
  expect(buyerOverflow).toBe(false);

  await logout(page);
  const seller = await registerSeller(page);
  const sellerOverflow = await page.evaluate(() =>
    document.documentElement.scrollWidth > window.innerWidth);
  expect(sellerOverflow).toBe(false);
  await expect(page.locator('body')).toContainText('Current status: PENDING');

  await logout(page);
  await page.goto('/seller.html');
  await page.locator('#sellerLoginMobile').fill(seller.mobile);
  await page.locator('#sellerLoginPassword').fill('Incorrect-E2E-password!');
  const wrongLogin = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('#sellerLoginForm').getByRole('button', { name: 'Sign in' }).click();
  expect((await wrongLogin).status()).toBe(401);
  expect((await readMe(page)).body.authenticated).toBe(false);
  await loginSeller(page, seller, 'PENDING');
  await expect(page.locator('body')).toContainText('Current status: PENDING');
  expect(browserIssues).toEqual([]);
});
