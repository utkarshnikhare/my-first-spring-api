const {
  test,
  expect,
  apiJson,
  readMe,
  logout,
  registerBuyer,
  loginBuyer,
  registerSeller,
  loginSeller,
  adminLogin
} = require('./support.cjs');

test('seller approval -> live offering -> buyer order -> inventory -> seller delivery -> buyer history', async ({
  page,
  browser,
  browserIssues
}) => {
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
  const offeringName = `E2E Lunch ${seller.token}`;
  await page.locator('[data-action="go-add"]').click();
  await expect(page.getByRole('heading', { name: 'Add Offering' })).toBeVisible();
  await page.locator('[data-action="go-create"]').click();
  await expect(page.getByRole('heading', { name: 'Create Offering' })).toBeVisible();
  const offeringForm = page.locator('#createOfferingForm');
  await expect(offeringForm).toBeVisible();
  await offeringForm.locator('[name="name"]').fill(offeringName);
  await offeringForm.locator('[name="description"]').fill('A browser-tested demo meal');
  await offeringForm.locator('[name="price"]').fill('75');
  await offeringForm.locator('[name="maxQuantity"]').fill('1');
  await offeringForm.locator('input[name="categories"][value="LUNCH"]').check();
  await offeringForm.locator('[name="orderWindowEnd"]').fill('23:59');
  const readyBy = new Date();
  readyBy.setDate(readyBy.getDate() + 1);
  readyBy.setHours(12, 0, 0, 0);
  const readyByValue = [
    readyBy.getFullYear(),
    String(readyBy.getMonth() + 1).padStart(2, '0'),
    String(readyBy.getDate()).padStart(2, '0')
  ].join('-') + `T${String(readyBy.getHours()).padStart(2, '0')}:${String(readyBy.getMinutes()).padStart(2, '0')}`;
  await offeringForm.locator('[name="readyByTime"]').fill(readyByValue);
  await offeringForm.getByRole('button', { name: 'Publish Offering' }).click();
  await expect(page.locator('#createOfferingForm')).toHaveCount(0);

  const sellerProducts = await apiJson(page, '/api/seller/products');
  expect(sellerProducts.status).toBe(200);
  const products = Array.isArray(sellerProducts.body) ? sellerProducts.body : sellerProducts.body.products;
  const offering = products.find(product => product.name === offeringName);
  expect(offering, 'the offering should be persisted for its seller').toBeTruthy();
  expect(offering.remainingQuantity).toBe(1);

  await logout(page);
  const buyer = await registerBuyer(page);
  await page.goto('/index.html#/food');
  const itemLink = page.getByRole('link', { name: new RegExp(offeringName) });
  await expect(itemLink).toBeVisible();
  await itemLink.click();
  await expect(page.getByRole('heading', { name: offeringName })).toBeVisible();
  const visitKitchen = page.getByRole('link', { name: new RegExp(`Visit ${seller.kitchenName}`) });
  await visitKitchen.click();
  await expect(page.locator('body')).toContainText(seller.kitchenName);
  await page.getByRole('button', { name: 'ORDER', exact: true }).click();
  await expect(page.locator('#sheetRoot')).toBeVisible();
  await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
  await page.goto('/index.html#/summary');
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();
  await page.locator('#orderNote').fill('Less spicy, please call on arrival.');
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
  await expect(page.locator('body')).toContainText('Less spicy, please call on arrival.');
  await page.locator('[data-action="select-pay-status"][data-status="PENDING"]').click();

  let placeRequests = 0;
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/buyer/orders/place' &&
        request.method() === 'POST') placeRequests++;
  });
  await page.locator('#placeOrderBtn').evaluate(button => {
    button.click();
    button.click();
  });
  await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();
  expect(placeRequests).toBe(1);
  await expect(page.locator('body')).toContainText(offeringName);

  const productState = await apiJson(page, `/api/products/${offering.id}`);
  expect(productState.status).toBe(200);
  expect(productState.body).toMatchObject({ remainingQuantity: 0, soldOut: true });
  const publicKitchen = await apiJson(page, `/api/kitchens/${encodeURIComponent(seller.slug)}`);
  expect(publicKitchen.status).toBe(200);
  expect(publicKitchen.body.products.some(product => product.name === offeringName)).toBe(false);

  const orders = await apiJson(page, '/api/buyer/orders/my');
  expect(orders.status).toBe(200);
  expect(orders.body.active).toEqual(expect.any(Array));
  const placedOrder = orders.body.active.find(order =>
    order.items.some(item => item.productName === offeringName));
  expect(placedOrder, 'the order should persist and belong to the buyer').toBeTruthy();
  expect(placedOrder.customInstructions).toBe('Less spicy, please call on arrival.');

  await logout(page);
  await loginSeller(page, seller);
  const freshProducts = await apiJson(page, '/api/seller/products');
  const freshProductList = Array.isArray(freshProducts.body) ? freshProducts.body : freshProducts.body.products;
  const persistedOffering = freshProductList.find(product => product.name === offeringName);
  expect(persistedOffering).toBeTruthy();
  expect(persistedOffering).toMatchObject({ remainingQuantity: 0, soldOut: true });
  await page.goto(`/seller.html#/order-detail/${persistedOffering.id}`);
  await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();
  const incomingOrder = await apiJson(page, `/api/seller/orders/${placedOrder.id}`);
  expect(incomingOrder.status).toBe(200);
  expect(incomingOrder.body.buyer.name).toBe(buyer.name);
  expect(incomingOrder.body.customInstructions).toBe('Less spicy, please call on arrival.');
  await expect(page.locator('.od-list')).toContainText('1 Per Piece');
  await expect(page.locator('#deliveryBlock')).toContainText('0 of 1 delivered');
  const remarkButton = page.getByRole('button', { name: 'View customer remark', exact: true });
  await expect(remarkButton).toBeVisible();
  await remarkButton.click();
  await expect(page.locator('#modalRoot')).toContainText('Less spicy, please call on arrival.');
  await page.locator('#modalRoot').getByRole('button').first().click();
  const delivered = page.getByRole('checkbox', { name: 'Mark this order delivered' });
  await delivered.check();
  await expect(delivered).toBeChecked();
  await expect(page.locator('#deliveryBlock')).toContainText('1 of 1 delivered');

  const deliveredOrder = await apiJson(page, `/api/seller/orders/${placedOrder.id}`);
  expect(deliveredOrder.status).toBe(200);
  expect(deliveredOrder.body.deliveryStatus).toBe('DELIVERED');
  await logout(page);
  await loginBuyer(page, buyer);
  await page.goto('/index.html#/orders');
  const buyerOrderCard = page.locator('.odc-order-card').filter({ hasText: offeringName });
  await expect(buyerOrderCard).toBeVisible();
  await expect(buyerOrderCard).toContainText('✅ Delivered');
  await expect(buyerOrderCard).toContainText('Less spicy, please call on arrival.');

  const anonymousContext = await browser.newContext({ baseURL: 'http://127.0.0.1:18082' });
  const anonymousPage = await anonymousContext.newPage();
  await anonymousPage.goto(`/index.html#/kitchen/${encodeURIComponent(seller.slug)}`);
  await expect(anonymousPage.locator('body')).toContainText(seller.kitchenName);
  await expect(anonymousPage.locator('.offering-card').filter({ hasText: offeringName })).toHaveCount(0);
  const anonymousProductState = await anonymousPage.evaluate(async id => {
    const response = await fetch(`/api/products/${id}`);
    return { status: response.status, body: await response.json() };
  }, offering.id);
  expect(anonymousProductState.status).toBe(200);
  expect(anonymousProductState.body).toMatchObject({ remainingQuantity: 0, soldOut: true });
  await anonymousContext.close();
  expect(browserIssues).toEqual([]);
});
