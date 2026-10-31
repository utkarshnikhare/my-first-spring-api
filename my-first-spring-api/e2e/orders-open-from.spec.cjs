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

test('seller schedules a specific-date offering and buyers cannot order before its IST opening time', async ({
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
  await page.goto('/seller.html');
  await page.getByRole('link', { name: '+ Create Offering' }).click();
  const form = page.locator('#createOfferingForm');
  await expect(form).toBeVisible();
  const offeringName = `Scheduled offering ${seller.token}`;
  await form.locator('[name="name"]').fill(offeringName);
  await form.locator('[name="description"]').fill('Orders open on a scheduled IST date and time.');
  await form.locator('[name="price"]').fill('65');
  await form.locator('[name="maxQuantity"]').fill('5');
  await form.locator('input[name="categories"][value="LUNCH"]').check();
  await form.locator('#offeringForGroup [data-val="choose"]').click();

  const dates = await page.evaluate(() => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(new Date()).map(part => [part.type, part.value]));
    const midnightUtc = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day));
    return {
      tomorrow: new Date(midnightUtc + 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
      offeringDate: new Date(midnightUtc + 2 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
    };
  });
  await form.locator('[name="chosenOfferingDate"]').fill(dates.offeringDate);
  await form.locator('[name="orderOpenDateMode"]').selectOption('tomorrow');
  await form.locator('[name="orderOpenTime"]').fill('09:00');
  await form.evaluate((element, offeringDate) => {
    for (const [name, value] of [
      ['orderWindowEnd', '17:00'],
      ['readyByTime', `${offeringDate}T18:00`]
    ]) {
      const input = element.querySelector(`[name="${name}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }, dates.offeringDate);

  const publish = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/seller/products'
      && response.request().method() === 'POST');
  await form.getByRole('button', { name: 'Publish Offering' }).click();
  const published = await publish;
  expect(published.status()).toBe(200);
  const product = await published.json();
  expect(product).toMatchObject({
    name: offeringName,
    availableDate: dates.offeringDate,
    ordersOpenFrom: `${dates.tomorrow}T09:00:00`,
    lifecycleState: 'SCHEDULED_TO_OPEN',
    ordersClosed: true
  });

  const kitchen = await apiJson(page, '/api/seller/kitchen');
  expect(kitchen.status).toBe(200);
  await logout(page);
  await registerBuyer(page);
  await page.goto(`/index.html#/kitchen/${kitchen.body.id}`);
  const card = page.locator('.offering-card').filter({ hasText: offeringName });
  await expect(card).toBeVisible();
  await expect(card).toContainText('ORDERS CLOSED');
  await expect(card.getByRole('button', { name: 'Orders closed' })).toBeDisabled();

  const csrfToken = await page.evaluate(() => {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  });
  const earlyDraft = await page.request.post(
    new URL(`/api/buyer/orders/draft?kitchenId=${kitchen.body.id}`, page.url()).toString(),
    {
      data: [{ productId: product.id, quantity: 1, scheduledDate: dates.offeringDate }],
      headers: { 'X-XSRF-TOKEN': csrfToken }
    }
  );
  expect(earlyDraft.status()).toBe(400);
  expect((await earlyDraft.json()).message).toContain('open on');
  expect(browserIssues).toEqual([]);
});
