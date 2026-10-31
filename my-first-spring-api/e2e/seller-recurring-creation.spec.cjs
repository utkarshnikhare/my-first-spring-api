const {
  test,
  expect,
  logout,
  registerSeller,
  adminLogin,
  loginSeller
} = require('./support.cjs');

test('new recurring schedules are unavailable while the seller history page remains accessible', async ({
  page,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  await logout(page);
  expect((await adminLogin(page)).status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  const row = page.locator('.seller-row').filter({ hasText: seller.name });
  const approval = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await row.getByRole('button', { name: 'Approve' }).click();
  expect((await approval).status()).toBe(200);

  await logout(page);
  await loginSeller(page, seller);
  await page.goto('/seller.html#/create');
  const form = page.locator('#createOfferingForm');
  await expect(form).toBeVisible();
  await expect(form).not.toContainText(/repeat|recurring/i);
  await expect(form.locator('[name="recurringSchedule"]')).toHaveCount(0);
  await expect(form.locator('[name="recurringWeekday"]')).toHaveCount(0);

  await page.goto('/seller.html#/recurring');
  await expect(page.locator('body')).toContainText('No recurring schedules yet');
  await expect(page.locator('body')).toContainText(
    'New recurring schedules can no longer be created.'
  );
  expect(browserIssues).toEqual([]);
});
