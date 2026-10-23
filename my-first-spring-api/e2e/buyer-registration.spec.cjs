const {
  test,
  expect,
  apiJson,
  readMe,
  openBuyerRegistration,
  fillBuyerRegistration,
  registerBuyer,
  loginBuyer,
  logout,
  newIdentity
} = require('./support.cjs');

test('buyer registers without OTP, duplicate registration is refused, and password login persists', async ({
  page,
  browserIssues
}) => {
  const buyer = await registerBuyer(page);
  await expect(page.locator('#buyerRegistrationForm')).toBeHidden();

  await page.goto('/index.html#/profile');
  await expect(page.locator('body')).toContainText(buyer.name);
  await expect(page.locator('body')).toContainText(buyer.mobile);

  await logout(page);
  const duplicateBuyer = { ...buyer };
  await openBuyerRegistration(page);
  await fillBuyerRegistration(page, duplicateBuyer);
  const duplicateResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/register/buyer');
  await expect(page.locator('#modalRoot')).toContainText('No OTP or SMS is required');
  await page.locator('#buyerRegistrationForm').getByRole('button', { name: 'Create account' }).click();
  expect((await duplicateResponse).status()).toBe(409);
  await expect(page.locator('#buyerRegistrationForm')).toBeVisible();

  await page.locator('#modalRoot').getByRole('button', { name: 'Back to login' }).click();
  await page.locator('#authMobile').fill(buyer.mobile);
  await page.locator('#authPassword').fill('Incorrect-E2E-password!');
  const rejectedLogin = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('#authForm').getByRole('button', { name: 'Log in', exact: true }).click();
  expect((await rejectedLogin).status()).toBe(401);
  await expect(page.locator('#modalRoot')).toBeVisible();
  expect((await readMe(page)).body.authenticated).toBe(false);

  await page.locator('#authPassword').fill(buyer.password);
  const successfulLogin = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('#authForm').getByRole('button', { name: 'Log in', exact: true }).click();
  expect((await successfulLogin).status()).toBe(200);
  await expect(page.locator('#modalRoot')).toBeHidden();
  await expect.poll(async () => (await readMe(page)).body).toMatchObject({
    authenticated: true,
    role: 'BUYER',
    mobileNumber: buyer.mobile
  });
  expect(browserIssues).toEqual([]);
});
