const {
  test,
  expect,
  apiJson,
  readMe,
  logout,
  registerSeller,
  loginSeller,
  adminLogin
} = require('./support.cjs');

test('seller stays pending until Admin approval and neither role can cross its authorization boundary', async ({
  page,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  const pendingSellerApi = await apiJson(page, '/api/seller/products');
  expect(pendingSellerApi.status).toBe(403);

  await page.goto('/admin.html');
  await expect(page.getByRole('heading', { name: 'Not an Admin account' })).toBeVisible();
  expect((await apiJson(page, '/api/admin/sellers/pending')).status).toBe(403);

  await logout(page);
  await page.goto('/seller.html');
  await page.locator('#sellerLoginMobile').fill(seller.mobile);
  await page.locator('#sellerLoginPassword').fill('Incorrect-E2E-password!');
  const badSellerLogin = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
  await page.locator('#sellerLoginForm').getByRole('button', { name: 'Sign in' }).click();
  expect((await badSellerLogin).status()).toBe(401);
  expect((await readMe(page)).body.authenticated).toBe(false);

  const badAdminLogin = await adminLogin(page, 'Incorrect-E2E-admin-password!');
  expect((await badAdminLogin).status()).toBe(401);
  await expect(page.getByRole('heading', { name: 'Admin Sign In' })).toBeVisible();
  expect((await readMe(page)).body.authenticated).toBe(false);

  await logout(page);
  const goodAdminLogin = await adminLogin(page);
  expect(goodAdminLogin.status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  await expect(page.getByRole('heading', { name: 'Pending Approvals' })).toBeVisible();

  const row = page.locator('.seller-row').filter({ hasText: seller.name });
  await expect(row).toBeVisible();
  const approval = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await row.getByRole('button', { name: 'Approve' }).click();
  expect((await approval).status()).toBe(200);
  await expect(row).toHaveCount(0);
  expect((await apiJson(page, '/api/admin/sellers/pending')).body.some(
    item => item.mobileNumber === seller.mobile
  )).toBe(false);

  await logout(page);
  await loginSeller(page, seller);
  expect((await apiJson(page, '/api/seller/products')).status).toBe(200);
  expect((await apiJson(page, '/api/admin/sellers/pending')).status).toBe(403);
  await page.goto('/admin.html');
  await expect(page.getByRole('heading', { name: 'Not an Admin account' })).toBeVisible();
  expect((await readMe(page)).body.role).toBe('SELLER');
  expect(browserIssues).toEqual([]);
});
