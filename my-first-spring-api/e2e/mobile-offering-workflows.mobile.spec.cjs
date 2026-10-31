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

async function approveSeller(page, seller) {
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
}

test('mobile Quick Post seller publishing and buyer checkout work end to end', async ({
  page,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  await approveSeller(page, seller);

  await page.getByRole('button', { name: 'Add Offering' }).click();
  await expect(page.locator('[data-action="go-quick-post"]')).toBeVisible();
  await page.locator('[data-action="go-quick-post"]').click();
  const form = page.locator('#quickPostForm');
  await expect(form).toBeVisible();
  await form.locator('[name="message"]').fill('Fresh poha, one serving per box.\nReady from 8 AM.');
  await form.locator('[name="price"]').fill('55');
  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Publish Quick Post' }).click();
  const publishResponse = await publish;
  expect(publishResponse.status()).toBe(200);
  const product = await publishResponse.json();
  expect(product).toMatchObject({
    quickPost: true,
    description: 'Fresh poha, one serving per box.\nReady from 8 AM.',
    price: 55,
    maxQuantity: null
  });
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  expect(kitchen.status).toBe(200);

  await logout(page);
  await registerBuyer(page);
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  const card = page.locator('.offering-card').filter({ hasText: 'Fresh poha, one serving per box.' });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: 'ORDER' }).click();
  await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
  await page.goto('/index.html#/summary');
  await page.locator('[data-action="go-checkout"]').click();
  await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
  await page.goto('/index.html#/payment');
  await expect(page.locator('.pay-method[data-method="cod"]')).toBeVisible();
  await expect(page.locator('#payNowBtn')).toContainText('PLACE ORDER (PAY ON DELIVERY)');
  const placing = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/buyer/orders/place'
      && response.request().method() === 'POST');
  await page.locator('#payNowBtn').click();
  const response = await placing;
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({
    paymentMethod: 'cod',
    paymentStatus: 'UNPAID',
    paymentConfigurationSnapshot: 'CASH_ONLY',
    items: [expect.objectContaining({ productId: product.id, quantity: 1 })]
  });
  await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(browserIssues).toEqual([]);
});

test('mobile buyers see a scheduled offering disabled and checkout is rejected before opening', async ({
  page,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  await approveSeller(page, seller);

  await page.goto('/seller.html');
  await page.getByRole('link', { name: '+ Create Offering' }).click();
  const form = page.locator('#createOfferingForm');
  await expect(form).toBeVisible();
  const name = `Mobile scheduled ${seller.token}`;
  await form.locator('[name="name"]').fill(name);
  await form.locator('[name="description"]').fill('Scheduled opening mobile acceptance.');
  await form.locator('[name="price"]').fill('65');
  await form.locator('[name="maxQuantity"]').fill('4');
  await form.locator('input[name="categories"][value="LUNCH"]').check();
  await form.locator('#offeringForGroup [data-val="choose"]').click();

  const dates = await page.evaluate(() => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(new Date()).map(part => [part.type, part.value]));
    const todayMidnightUtc = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day));
    return {
      tomorrow: new Date(todayMidnightUtc + 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
      delivery: new Date(todayMidnightUtc + 2 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
    };
  });
  await form.locator('[name="chosenOfferingDate"]').fill(dates.delivery);
  await form.locator('[name="orderOpenDateMode"]').selectOption('tomorrow');
  await form.locator('[name="orderOpenTime"]').fill('09:00');
  await form.evaluate((element, deliveryDate) => {
    for (const [field, value] of [
      ['orderWindowEnd', '17:00'],
      ['readyByTime', `${deliveryDate}T18:00`]
    ]) {
      const input = element.querySelector(`[name="${field}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }, dates.delivery);

  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Publish Offering' }).click();
  const published = await publish;
  expect(published.status()).toBe(200);
  const product = await published.json();
  expect(product.lifecycleState).toBe('SCHEDULED_TO_OPEN');
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  expect(kitchen.status).toBe(200);

  await logout(page);
  await registerBuyer(page);
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  const card = page.locator('.offering-card').filter({ hasText: name });
  await expect(card).toBeVisible();
  await expect(card).toContainText('ORDERS CLOSED');
  await expect(card.getByRole('button', { name: 'Orders closed' })).toBeDisabled();
  const csrf = await page.evaluate(() => {
    const value = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return value ? decodeURIComponent(value[1]) : '';
  });
  const attempt = await page.request.post(
    new URL(`/api/buyer/orders/draft?kitchenId=${kitchen.body.id}`, page.url()).toString(),
    {
      data: [{ productId: product.id, quantity: 1, scheduledDate: dates.delivery }],
      headers: { 'X-XSRF-TOKEN': csrf }
    }
  );
  expect(attempt.status()).toBe(400);
  expect((await attempt.json()).message).toContain('open on');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(browserIssues).toEqual([]);
});
