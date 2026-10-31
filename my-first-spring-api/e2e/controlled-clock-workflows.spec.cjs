const {
  test,
  expect,
  apiJson,
  logout,
  registerBuyer,
  loginBuyer,
  registerSeller,
  loginSeller,
  adminLogin,
  openE2eClock
} = require('./support.cjs');

async function istToday(page) {
  return page.evaluate(() => new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date()));
}

function addDays(date, days) {
  const result = new Date(`${date}T00:00:00Z`);
  result.setUTCDate(result.getUTCDate() + days);
  return result.toISOString().slice(0, 10);
}

async function approveAndLoginSeller(page, seller) {
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
}

async function createOfferingThroughSellerUi(page, {
  name,
  availableDate,
  openingDateMode = 'today',
  openingTime = '',
  orderWindowStart = null
}) {
  await page.goto('/seller.html');
  await page.getByRole('link', { name: '+ Create Offering' }).click();
  const form = page.locator('#createOfferingForm');
  await expect(form).toBeVisible();
  await form.locator('[name="name"]').fill(name);
  await form.locator('[name="description"]').fill('A deterministic browser timing check.');
  await form.locator('[name="price"]').fill('65');
  await form.locator('[name="maxQuantity"]').fill('5');
  await form.locator('input[name="categories"][value="LUNCH"]').check();
  await form.locator('#offeringForGroup [data-val="choose"]').click();
  await form.locator('[name="chosenOfferingDate"]').fill(availableDate);
  await form.locator('[name="orderOpenDateMode"]').selectOption(openingDateMode);
  if (openingTime) await form.locator('[name="orderOpenTime"]').fill(openingTime);
  await form.evaluate((element, values) => {
    for (const [name, value] of Object.entries(values)) {
      const input = element.querySelector(`[name="${name}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }, {
    orderWindowEnd: '17:00',
    readyByTime: `${availableDate}T18:00`,
    ...(orderWindowStart ? { orderWindowStart } : {})
  });
  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Publish Offering' }).click();
  const response = await publish;
  expect(response.status()).toBe(200);
  return response.json();
}

async function createDraft(page, kitchenId, productId, scheduledDate) {
  const csrfToken = await page.evaluate(() => {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  });
  const response = await page.request.post(
    new URL(`/api/buyer/orders/draft?kitchenId=${kitchenId}`, page.url()).toString(),
    {
      data: [{ productId, quantity: 1, scheduledDate }],
      headers: { 'X-XSRF-TOKEN': csrfToken }
    }
  );
  return { status: response.status(), body: await response.json() };
}

async function openKitchenCard(page, kitchenId, name) {
  await page.goto(`/index.html#/kitchen/${kitchenId}`);
  return page.locator('.offering-card').filter({ hasText: name });
}

test('DEF-09 cutoff remains open across IST midnight through 17:00 and rejects at 17:01', async ({
  page,
  browser,
  browserIssues
}) => {
  const today = await istToday(page);
  const deliveryDate = addDays(today, 1);
  const seller = await registerSeller(page);
  await approveAndLoginSeller(page, seller);
  const product = await createOfferingThroughSellerUi(page, {
    name: `DEF-09 cutoff ${seller.token}`,
    availableDate: deliveryDate
  });
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  expect(kitchen.status).toBe(200);
  await logout(page);
  await registerBuyer(page);
  const clock = await openE2eClock(browser);
  try {
    const card = await openKitchenCard(page, kitchen.body.id, product.name);
    await clock.set(`${today}T23:59:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card.getByRole('button')).toBeEnabled();
    expect((await createDraft(page, kitchen.body.id, product.id, deliveryDate)).status).toBe(200);

    await clock.set(`${deliveryDate}T00:00:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card.getByRole('button')).toBeEnabled();
    expect((await createDraft(page, kitchen.body.id, product.id, deliveryDate)).status).toBe(200);

    await clock.set(`${deliveryDate}T17:00:00`);
    await page.reload();
    await expect(card.getByRole('button')).toBeEnabled();
    expect((await createDraft(page, kitchen.body.id, product.id, deliveryDate)).status).toBe(200);

    await clock.set(`${deliveryDate}T17:01:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card).toContainText('ORDERS CLOSED');
    await expect(card.getByRole('button')).toBeDisabled();
    const rejected = await createDraft(page, kitchen.body.id, product.id, deliveryDate);
    expect(rejected.status).toBe(400);
    expect(rejected.body.message).toMatch(/cutoff|closed/i);
  } finally {
    await clock.close();
  }
  expect(browserIssues).toEqual([]);
});

test('Orders Open From activates in the buyer browser at its scheduled IST minute', async ({
  page,
  browser,
  browserIssues
}) => {
  const today = await istToday(page);
  const openingDate = addDays(today, 1);
  const deliveryDate = addDays(today, 2);
  const seller = await registerSeller(page);
  await approveAndLoginSeller(page, seller);
  const product = await createOfferingThroughSellerUi(page, {
    name: `Open-from activation ${seller.token}`,
    availableDate: deliveryDate,
    openingDateMode: 'tomorrow',
    openingTime: '09:00'
  });
  expect(product.ordersOpenFrom).toBe(`${openingDate}T09:00:00`);
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  await logout(page);
  await registerBuyer(page);
  const clock = await openE2eClock(browser);
  try {
    const card = await openKitchenCard(page, kitchen.body.id, product.name);
    await clock.set(`${openingDate}T08:59:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card).toContainText('ORDERS CLOSED');
    await expect(card.getByRole('button')).toBeDisabled();
    expect((await createDraft(page, kitchen.body.id, product.id, deliveryDate)).status).toBe(400);

    await clock.set(`${openingDate}T09:00:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card.getByRole('button')).toBeEnabled();
    expect((await createDraft(page, kitchen.body.id, product.id, deliveryDate)).status).toBe(200);
  } finally {
    await clock.close();
  }
  expect(browserIssues).toEqual([]);
});

test('legacy orderWindowStart records still activate without an Orders Open From timestamp', async ({
  page,
  browser,
  browserIssues
}) => {
  const today = await istToday(page);
  const deliveryDate = addDays(today, 1);
  const seller = await registerSeller(page);
  await approveAndLoginSeller(page, seller);
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  const create = await apiJson(page, '/api/seller/products', {
    method: 'POST',
    body: {
      name: `Legacy opening time ${seller.token}`,
      description: 'Seeded through the original daily opening-time field.',
      price: 65,
      priceUnit: 'Per Plate',
      categories: ['LUNCH'],
      availableDate: deliveryDate,
      availableToday: true,
      isPreorder: true,
      orderWindowStart: '09:00',
      orderWindowEnd: '17:00',
      readyByTime: `${deliveryDate}T18:00`,
      maxQuantity: 5
    }
  });
  expect(create.status).toBe(200);
  expect(create.body).toMatchObject({
    orderWindowStart: '09:00',
    ordersOpenFrom: null
  });
  await logout(page);
  await registerBuyer(page);
  const clock = await openE2eClock(browser);
  try {
    const card = await openKitchenCard(page, kitchen.body.id, create.body.name);
    await clock.set(`${deliveryDate}T08:59:00`);
    await page.reload();
    await expect(card).toBeVisible();
    await expect(card).toContainText('ORDERS CLOSED');
    await expect(card.getByRole('button')).toBeDisabled();

    await clock.set(`${deliveryDate}T09:00:00`);
    await page.reload();
    await expect(card.getByRole('button')).toBeEnabled();
    expect((await createDraft(page, kitchen.body.id, create.body.id, deliveryDate)).status).toBe(200);
  } finally {
    await clock.close();
  }
  expect(browserIssues).toEqual([]);
});

test('Quick Post expires at IST midnight while accepted orders remain fulfillable', async ({
  page,
  browser,
  browserIssues
}) => {
  const seller = await registerSeller(page);
  await approveAndLoginSeller(page, seller);
  await page.getByRole('button', { name: 'Add Offering' }).click();
  await page.locator('[data-action="go-create"]').click();
  await page.goto('/seller.html#/add');
  await page.locator('[data-action="go-quick-post"]').click();
  const quickPostForm = page.locator('#quickPostForm');
  await expect(quickPostForm).toBeVisible();
  const message = `Midnight Quick Post ${seller.token}\nFresh today only.`;
  await quickPostForm.locator('[name="message"]').fill(message);
  await quickPostForm.locator('[name="price"]').fill('50');
  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await quickPostForm.getByRole('button', { name: 'Publish Quick Post' }).click();
  const published = await publish;
  expect(published.status()).toBe(200);
  const product = await published.json();
  const kitchen = await apiJson(page, '/api/seller/kitchen');
  await logout(page);
  const buyer = await registerBuyer(page);
  const clock = await openE2eClock(browser);
  try {
    await clock.set(`${product.availableDate}T23:59:00`);
    const card = await openKitchenCard(page, kitchen.body.id, product.name);
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: 'ORDER' }).click();
    await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
    await page.goto('/index.html#/summary');
    await expect(page.getByRole('heading', { name: 'Order Summary' })).toBeVisible();
    await page.locator('[data-action="go-checkout"]').click();
    await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
    await page.goto('/index.html#/payment');
    await expect(page.getByRole('heading', { name: 'Payment', exact: true })).toBeVisible();
    await expect(page.locator('#payNowBtn')).toBeVisible();
    const place = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/buyer/orders/place'
        && response.request().method() === 'POST');
    await page.locator('#payNowBtn').click();
    const placed = await place;
    expect(placed.status()).toBe(200);
    const order = await placed.json();

    const nextDate = addDays(product.availableDate, 1);
    await clock.set(`${nextDate}T00:01:00`);
    const expiredCard = await openKitchenCard(page, kitchen.body.id, product.name);
    await expect(expiredCard).toHaveCount(0);
    const expiredDraft = await createDraft(page, kitchen.body.id, product.id, product.availableDate);
    expect(expiredDraft.status).toBe(400);
    expect(expiredDraft.body.message).toMatch(/past|expired|closed|no longer/i);

    await logout(page);
    await loginSeller(page, seller);
    const delivered = await apiJson(page, `/api/seller-app/orders/${order.id}/delivery-status`, {
      method: 'PATCH',
      body: { deliveryStatus: 'DELIVERED' }
    });
    expect(delivered.status).toBe(200);
    expect(delivered.body).toMatchObject({
      id: order.id,
      orderStatus: 'ORDERED',
      deliveryStatus: 'DELIVERED'
    });

    await logout(page);
    await loginBuyer(page, buyer);
    const history = await apiJson(page, '/api/buyer/orders/my');
    expect(history.status).toBe(200);
    expect(history.body.active.find(entry => entry.id === order.id)).toMatchObject({
      orderStatus: 'ORDERED',
      deliveryStatus: 'DELIVERED'
    });
    expect(history.body.active.find(entry => entry.id === order.id).items
      .some(item => item.productName === product.name)).toBe(true);
  } finally {
    await clock.close();
  }
  expect(browserIssues).toEqual([]);
});
