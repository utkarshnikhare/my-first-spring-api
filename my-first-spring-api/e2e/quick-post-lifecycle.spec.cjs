const {
  test,
  expect,
  apiJson,
  logout,
  registerBuyer,
  registerSeller,
  loginSeller,
  adminLogin
} = require('./support.cjs');

test('seller publishes a Quick Post that buyers can order through the existing checkout', async ({
  page,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  await logout(page);
  expect((await adminLogin(page)).status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  const sellerRow = page.locator('.seller-row').filter({ hasText: seller.name });
  const approval = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await sellerRow.getByRole('button', { name: 'Approve' }).click();
  expect((await approval).status()).toBe(200);

  await logout(page);
  await loginSeller(page, seller);
  await page.getByRole('button', { name: 'Add Offering' }).click();
  await page.locator('[data-action="go-create"]').click();
  const standardForm = page.locator('#createOfferingForm');
  await expect(standardForm.locator('[name="priceUnit"]')).toHaveValue('Per Plate');
  await standardForm.locator('[name="name"]').fill('Custom seller item');
  await standardForm.locator('[name="price"]').fill('75');
  await standardForm.locator('[data-action="apply-preset"][data-preset="lunch"]').click();
  await standardForm.locator('[data-action="clear-preset"]').click();
  await expect(standardForm.locator('[name="name"]')).toHaveValue('Custom seller item');
  await expect(standardForm.locator('[name="price"]')).toHaveValue('75');
  await expect(standardForm.locator('.btn-meal-preset.active')).toHaveCount(0);

  await page.goto('/seller.html#/add');
  await page.locator('[data-action="go-quick-post"]').click();
  const form = page.locator('#quickPostForm');
  await expect(form).toBeVisible();
  await expect(form.locator('[name="name"]')).toHaveCount(0);
  await expect(form.locator('[name="orderWindowEnd"]')).toHaveCount(0);
  await expect(form.locator('[name="maxQuantity"]')).toHaveCount(0);
  await form.locator('[name="message"]').fill('Fresh idli, four pieces per plate.\nReady from 6 PM.');
  await form.locator('[name="price"]').fill('50');

  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Publish Quick Post' }).click();
  const publishResponse = await publish;
  expect(publishResponse.status()).toBe(200);
  const product = await publishResponse.json();
  expect(product).toMatchObject({
    quickPost: true,
    description: 'Fresh idli, four pieces per plate.\nReady from 6 PM.',
    price: 50,
    priceUnit: 'unit',
    maxQuantity: null
  });
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  expect(kitchen.status).toBe(200);

  await page.goto('/seller.html#/kitchen');
  const previewTabPromise = page.waitForEvent('popup');
  await page.getByRole('button', { name: 'Preview Kitchen Page' }).click();
  const previewTab = await previewTabPromise;
  await expect(previewTab).toHaveURL(new RegExp(`/index\\.html#/kitchen/${kitchen.body.id}$`));
  await expect(page).toHaveURL(/seller\.html#\/kitchen$/);
  await expect(page.getByRole('button', { name: 'Preview Kitchen Page' })).toBeVisible();
  await previewTab.getByRole('link', { name: /Explore Other Kitchens/ }).first().click();
  await expect(previewTab).toHaveURL(/index\.html#\/kitchens$/);
  await expect(previewTab.getByRole('heading', { name: 'All Kitchens' }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Preview Kitchen Page' })).toBeVisible();
  await previewTab.close();

  await page.goto('/seller.html#/home');
  await page.getByRole('button', { name: 'Log out of seller account' }).click();
  await expect(page.locator('#sellerLoginForm')).toBeVisible();
  await page.goto('/seller.html#/kitchen');
  await expect(page.locator('#sellerLoginForm')).toBeVisible();
  await page.goBack();
  await expect(page.locator('#sellerLoginForm')).toBeVisible();

  await registerBuyer(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  const card = page.locator('.offering-card').filter({
    hasText: 'Fresh idli, four pieces per plate.'
  });
  await expect(card).toBeVisible();
  await expect(card).toContainText('Quick Post');
  await expect(card).toContainText('Ready from 6 PM.');
  await expect(card.getByRole('button', { name: 'ORDER' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await card.getByRole('button', { name: 'ORDER' }).click();
  await expect(page.locator('#sheetRoot')).toBeVisible();
  await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
  await page.goto('/index.html#/summary');
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();
  await expect(page.locator('body')).toContainText('Fresh idli, four pieces per plate.');
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
  await page.locator('[data-action="select-pay-status"][data-status="PENDING"]').click();
  await page.locator('#placeOrderBtn').click();
  await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();

  const orders = await apiJson(page, '/api/buyer/orders/my');
  expect(orders.status).toBe(200);
  expect(orders.body.active.some(order =>
    order.items.some(item => item.productName === product.name))).toBe(true);

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  await expect(page.locator('.offering-card').filter({
    hasText: 'Fresh idli, four pieces per plate.'
  })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(browserIssues).toEqual([]);
});
