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

  await page.getByRole('link', { name: 'Store', exact: true }).click();
  const kitchenForm = page.locator('#kitchenForm');
  await expect(kitchenForm).toBeVisible();
  await kitchenForm.locator('[name="acceptedPaymentMode"]').selectOption('GPAY_AND_UPI');
  await kitchenForm.locator('[name="gpayIdentifierType"]').selectOption('PHONE_NUMBER');
  await kitchenForm.locator('[name="gpayIdentifier"]').fill('9876543210');
  await kitchenForm.locator('[name="upiId"]').fill('sociomart-test@okaxis');
  const paymentSettingsSave = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/seller/kitchen/${kitchen.body.id}`
      && response.request().method() === 'PUT');
  await kitchenForm.getByRole('button', { name: 'SAVE CHANGES' }).click();
  const settingsResponse = await paymentSettingsSave;
  expect(settingsResponse.status()).toBe(200);
  expect(await settingsResponse.json()).toMatchObject({
    acceptedPaymentMode: 'GPAY_AND_UPI',
    gpayIdentifierType: 'PHONE_NUMBER',
    gpayIdentifier: '9876543210',
    upiId: 'sociomart-test@okaxis'
  });

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
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
  await page.goto('/index.html#/payment');
  await expect(page.getByRole('heading', { name: 'Payment', exact: true })).toBeVisible();
  await expect(page.locator('.pay-method')).toHaveCount(2);
  await page.locator('[data-action="select-pay-method"][data-method="gpay"]').click();
  await expect(page.locator('body')).toContainText('9876543210');
  const placeButton = page.locator('#payNowBtn');
  await page.route('**/api/buyer/orders/place', route => route.abort('internetdisconnected'));
  await placeButton.click();
  await expect(page.locator('#placeOrderStatus')).toContainText('Order was not placed');
  await expect(placeButton).toBeEnabled();
  await expect(placeButton).not.toHaveAttribute('aria-busy');
  const expectedNetworkFailure = browserIssues.filter(issue =>
    issue === 'API request failed: POST /api/buyer/orders/place'
      || issue.startsWith('console.error: Failed to load resource:'));
  expect(expectedNetworkFailure).toHaveLength(2);
  expectedNetworkFailure.forEach(issue => {
    browserIssues.splice(browserIssues.indexOf(issue), 1);
  });
  await page.unroute('**/api/buyer/orders/place');

  let placeRequestCount = 0;
  await page.route('**/api/buyer/orders/place', async route => {
    placeRequestCount++;
    await new Promise(resolve => setTimeout(resolve, 300));
    await route.continue();
  });
  const placePaymentOrder = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/buyer/orders/place'
      && response.request().method() === 'POST');
  await placeButton.click();
  await expect(placeButton).toBeDisabled();
  await expect(placeButton).toContainText('Placing your order…');
  await placeButton.dispatchEvent('click');
  const paymentOrderResponse = await placePaymentOrder;
  await page.unroute('**/api/buyer/orders/place');
  expect(paymentOrderResponse.status()).toBe(200);
  expect(placeRequestCount).toBe(1);
  expect(await paymentOrderResponse.json()).toMatchObject({ paymentMethod: 'gpay' });
  await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();

  const orders = await apiJson(page, '/api/buyer/orders/my');
  expect(orders.status).toBe(200);
  expect(orders.body.active.some(order =>
    order.items.some(item => item.productName === product.name))).toBe(true);

  await logout(page);
  await loginSeller(page, seller);
  await page.getByRole('link', { name: 'Store', exact: true }).click();
  await expect(kitchenForm).toBeVisible();
  await kitchenForm.locator('[name="acceptedPaymentMode"]').selectOption('CASH_ONLY');
  const cashSettingsSave = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/seller/kitchen/${kitchen.body.id}`
      && response.request().method() === 'PUT');
  await kitchenForm.getByRole('button', { name: 'SAVE CHANGES' }).click();
  const cashSettingsResponse = await cashSettingsSave;
  expect(cashSettingsResponse.status()).toBe(200);
  expect((await cashSettingsResponse.json()).acceptedPaymentMode).toBe('CASH_ONLY');

  await logout(page);
  await registerBuyer(page);
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  const cashBuyerCard = page.locator('.offering-card').filter({
    hasText: 'Fresh idli, four pieces per plate.'
  });
  await expect(cashBuyerCard).toBeVisible();
  await cashBuyerCard.getByRole('button', { name: 'ORDER' }).click();
  await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
  await page.goto('/index.html#/summary');
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
  await page.goto('/index.html#/payment');
  await expect(page.locator('.pay-method')).toHaveCount(1);
  await expect(page.locator('.pay-method[data-method="cod"]')).toHaveClass(/selected/);
  await expect(page.locator('#payNowBtn')).toContainText('PLACE ORDER (PAY ON DELIVERY)');
  const placeCashOrder = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/buyer/orders/place'
      && response.request().method() === 'POST');
  await page.locator('#payNowBtn').click();
  const cashOrderResponse = await placeCashOrder;
  expect(cashOrderResponse.status()).toBe(200);
  const cashOrder = await cashOrderResponse.json();
  expect(cashOrder.paymentMethod).toBe('cod');

  await logout(page);
  await loginSeller(page, seller);
  const closePost = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/seller-app/products/${product.id}/pause`
      && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Close Post', exact: true }).click();
  expect((await closePost).status()).toBe(200);
  const preservedOrder = await apiJson(page, `/api/seller/orders/${cashOrder.id}`);
  expect(preservedOrder.status).toBe(200);
  expect(preservedOrder.body.orderStatus).not.toBe('CANCELLED');

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  await expect(page.locator('.offering-card').filter({
    hasText: 'Fresh idli, four pieces per plate.'
  })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(browserIssues).toEqual([]);
});
