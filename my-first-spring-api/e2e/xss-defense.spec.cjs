const {
  test,
  expect,
  apiJson,
  logout,
  registerBuyer,
  loginBuyer,
  registerSeller,
  loginSeller,
  adminLogin
} = require('./support.cjs');

test('stored XSS in order customInstructions is rendered escaped and never executes on buyer or seller screens', async ({
  page,
  browserIssues
}) => {
  // 1. Setup approved seller with an active offering
  const seller = await registerSeller(page);
  await logout(page);

  const adminLoginResponse = await adminLogin(page);
  expect(adminLoginResponse.status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  const sellerRow = page.locator('.seller-row').filter({ hasText: seller.name });
  const approval = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await sellerRow.getByRole('button', { name: 'Approve' }).click();
  expect((await approval).status()).toBe(200);

  await logout(page);
  await loginSeller(page, seller);

  const offeringName = `XSS Shield Item ${seller.token}`;
  await page.locator('[data-action="go-add"]').click();
  await expect(page.getByRole('heading', { name: 'Add Offering' })).toBeVisible();
  await page.locator('[data-action="go-create"]').click();
  await expect(page.getByRole('heading', { name: 'Create Offering' })).toBeVisible();
  const offeringForm = page.locator('#createOfferingForm');
  await expect(offeringForm).toBeVisible();
  await offeringForm.locator('[name="name"]').fill(offeringName);
  await offeringForm.locator('[name="description"]').fill('Safe food item');
  await offeringForm.locator('[name="price"]').fill('50');
  await offeringForm.locator('[name="maxQuantity"]').fill('5');
  if (await offeringForm.locator('button[data-preset="lunch"]').count() > 0) {
    await offeringForm.locator('button[data-preset="lunch"]').click();
  } else {
    await offeringForm.locator('input[name="categories"][value="LUNCH"]').check();
    await offeringForm.locator('[name="orderWindowEnd"]').fill('23:59');
  }
  await offeringForm.getByRole('button', { name: 'Publish Offering' }).click();
  await expect(page.locator('#createOfferingForm')).toHaveCount(0);

  const sellerProducts = await apiJson(page, '/api/seller/products');
  expect(sellerProducts.status).toBe(200);
  const products = Array.isArray(sellerProducts.body) ? sellerProducts.body : sellerProducts.body.products;
  const offering = products.find(p => p.name === offeringName);
  expect(offering).toBeTruthy();

  await logout(page);

  // 2. Buyer places order with Stored XSS payload in customInstructions
  const buyer = await registerBuyer(page);
  await page.goto('/index.html#/food');
  const itemLink = page.getByRole('link', { name: new RegExp(offeringName) });
  await expect(itemLink).toBeVisible();
  await itemLink.click();
  await expect(page.getByRole('heading', { name: offeringName })).toBeVisible();
  const visitKitchen = page.getByRole('link', { name: new RegExp(`Visit ${seller.kitchenName}`) });
  await visitKitchen.click();
  await page.getByRole('button', { name: /^(ORDER|PRE-ORDER)$/ }).click();
  await expect(page.locator('#sheetRoot')).toBeVisible();
  await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();

  await page.goto('/index.html#/summary');
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();

  const xssPayload = '<img src=invalid-img onerror="window.__xss_attack_triggered=true"><span id="xss-canary-element">XSS</span>';
  await page.locator('#orderNote').fill(xssPayload);
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();

  // Verify confirm order screen renders escaped text without executing XSS
  const confirmXssExecuted = await page.evaluate(() => window.__xss_attack_triggered);
  expect(confirmXssExecuted).toBeUndefined();
  expect(await page.locator('#xss-canary-element').count()).toBe(0);

  await expect(page.getByRole('radio', { name: /Cash on Delivery/ })).toBeChecked();
  await page.locator('#placeOrderBtn').click();
  await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();

  // 3. Buyer order history screen verification
  await page.goto('/index.html#/orders');
  const buyerOrderCard = page.locator('.odc-order-card').filter({ hasText: offeringName });
  await expect(buyerOrderCard).toBeVisible();
  const buyerOrdersXssExecuted = await page.evaluate(() => window.__xss_attack_triggered);
  expect(buyerOrdersXssExecuted).toBeUndefined();
  expect(await page.locator('#xss-canary-element').count()).toBe(0);

  await logout(page);

  // 4. Seller dashboard / order details verification
  await loginSeller(page, seller);
  await page.goto(`/seller.html#/order-detail/${offering.id}`);
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();

  const remarkButton = page.getByRole('button', { name: 'View customer remark', exact: true });
  await expect(remarkButton).toBeVisible();
  await remarkButton.click();
  await expect(page.locator('#modalRoot')).toBeVisible();

  // Ensure modal text is escaped, canary element does not exist in DOM, and attack is not triggered
  const sellerXssExecuted = await page.evaluate(() => window.__xss_attack_triggered);
  expect(sellerXssExecuted).toBeUndefined();
  expect(await page.locator('#xss-canary-element').count()).toBe(0);
  await page.locator('#modalRoot').getByRole('button').first().click();

  expect(browserIssues).toEqual([]);
});
