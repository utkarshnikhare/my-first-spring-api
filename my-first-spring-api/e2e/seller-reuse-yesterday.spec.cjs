const {
  test,
  expect,
  logout,
  registerSeller,
  adminLogin,
  loginSeller,
} = require('./support.cjs');

test('seller Add Offering displays Option 4 Reuse Yesterdays Menu and opens selection view', async ({
  page,
  browserIssues
}) => {
  // 1. Register and approve seller
  const seller = await registerSeller(page);
  await logout(page);
  expect((await adminLogin(page)).status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  const row = page.locator('.seller-row').filter({ hasText: seller.name });
  const approval = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await row.getByRole('button', { name: 'Approve' }).click();
  expect((await approval).status()).toBe(200);

  // 2. Login as approved seller
  await logout(page);
  await loginSeller(page, seller);

  // 3. Navigate to Add Offering (#/add)
  await page.goto('/seller.html#/add');
  await page.waitForSelector('.pathway-card');

  const cards = page.locator('.pathway-card');
  await expect(cards).toHaveCount(4);

  // Verify exact order and text
  await expect(cards.nth(0)).toContainText('Create Offering');
  await expect(cards.nth(1)).toContainText('Create from Favourites');
  await expect(cards.nth(2)).toContainText('Quick Post');
  await expect(cards.nth(3)).toContainText('Reuse Yesterday’s Menu');
  await expect(cards.nth(3).locator('.badge-new')).toHaveText('New');

  // 4. Click Option 4: Reuse Yesterday's Menu
  await cards.nth(3).click();
  await expect(page).toHaveURL(/#\/reuse-yesterday/);

  // 5. Verify screen title and context
  await expect(page.locator('h2')).toContainText('Reuse Yesterday’s Menu');
  await expect(page.locator('.reuse-date-badge')).toBeVisible();

  // Fresh seller has no yesterday offerings -> verify empty state
  await expect(page.locator('.empty')).toBeVisible();
  await expect(page.locator('.empty h3')).toContainText('No Offerings Found from Yesterday');
  await expect(page.locator('.empty a[href="#/create"]')).toBeVisible();
  await expect(page.locator('.empty a[href="#/add"]')).toBeVisible();

  // 6. Test Back button returns to Add Offering
  await page.locator('.empty a[href="#/add"]').click();
  await expect(page).toHaveURL(/#\/add/);
  await expect(page.locator('.pathway-card')).toHaveCount(4);
});
