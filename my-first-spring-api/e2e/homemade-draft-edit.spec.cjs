const { test, expect, registerSeller, registerBuyer, logout, adminLogin, loginSeller, apiJson, openE2eClock } = require('./support.cjs');

test('typed homemade drafts retain their dates and stock modes through edits', async ({ page, browser, browserIssues }) => {
  const seller = await registerSeller(page, { sellerType: 'HOMEMADE_PRODUCTS' });
  await logout(page);
  expect((await adminLogin(page)).status()).toBe(200);
  await page.locator('[data-hash="#/approvals"]').first().click();
  const row = page.locator('.seller-row').filter({ hasText: seller.name });
  const approved = page.waitForResponse(r => new URL(r.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
  await row.getByRole('button', { name: 'Approve' }).click();
  expect((await approved).status()).toBe(200);
  await logout(page);
  await loginSeller(page, seller);
  await expect(page.getByRole('heading', { name: /Good / })).toBeVisible();
  await page.locator('[data-action="go-add"]').first().click();
  const create = page.locator('#createOfferingForm');
  await expect(create).toBeVisible();
  await create.locator('[name="name"]').fill('Draft browser test');
  await create.locator('[name="price"]').fill('50');
  await create.locator('[data-action="set-hm-avail"][data-avail="SPECIFIC_DATE"]').click();
  const date = await create.locator('[name="chosenOfferingDate"]').inputValue();
  const later = new Date(`${date}T12:00:00Z`);
  later.setUTCDate(later.getUTCDate() + 1);
  const readyDate = later.toISOString().slice(0, 10);
  await create.locator('[name="hmReadyDate"]').fill(readyDate);
  await create.locator('[name="readyByTime"]').fill('22:00');
  await create.locator('[data-action="set-hm-stock"][data-stock="limited"]').click();
  await create.locator('[name="maxQuantity"]').fill('5');
  const created = page.waitForResponse(r => new URL(r.url()).pathname === '/api/seller/products' && r.request().method() === 'POST');
  await create.locator('[data-action="save-product-draft"]').click();
  const result = await created;
  expect(result.status()).toBe(200);
  const draft = await result.json();
  expect(draft.lifecycleState).toBe('DRAFT');
  const normal = await apiJson(page, '/api/seller/products', {
    method: 'POST',
    body: {
      name: 'Previous browser offering',
      description: 'History copy control',
      price: 40,
      priceUnit: 'Per Piece',
      categories: ['HOMEMADE'],
      availableDate: date,
      availableToday: true,
      isPreorder: false,
      readyByTime: 'Usually ready in 1 day',
      maxQuantity: 3
    }
  });
  expect(normal.status).toBe(200);
  const second = await apiJson(page, '/api/seller/products', {
    method: 'POST',
    body: {
      name: 'Second browser draft',
      description: 'Independent mode control',
      price: 30,
      priceUnit: 'Per Piece',
      categories: ['HOMEMADE'],
      availableDate: date,
      availableToday: false,
      isPreorder: false,
      readyByTime: 'Usually ready in 1 day',
      saveAsDraft: true,
      availType: 'ALWAYS_AVAILABLE'
    }
  });
  expect(second.status).toBe(200);
  expect(second.body.lifecycleState).toBe('DRAFT');

  await page.locator('a[href="#/home"]').first().click();
  await page.locator(`[data-action="edit-offering"][data-pid="${draft.id}"]`).first().click();
  let edit = page.locator('#editOfferingForm');
  await expect(edit.locator('[name="hmAvailType"]')).toHaveValue('SPECIFIC_DATE');
  await expect(edit.locator('[name="hmReadyDate"]')).toHaveValue(readyDate);
  await expect(edit.locator('[name="hmStockMode"]')).toHaveValue('limited');
  await edit.locator('[data-action="set-hm-avail"][data-avail="SPECIFIC_DATE"]').click();
  await edit.locator('[data-action="set-hm-stock"][data-stock="limited"]').click();
  await edit.locator('[name="description"]').fill('Saved without changing the date');
  let updated = page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/products/${draft.id}` && r.request().method() === 'PUT');
  await edit.getByRole('button', { name: 'Save Changes' }).click();
  expect((await updated).status()).toBe(200);
  let stored = await apiJson(page, `/api/seller/products/${draft.id}`);
  expect(stored.body).toMatchObject({ lifecycleState: 'DRAFT', description: 'Saved without changing the date', availType: 'SPECIFIC_DATE', readyByTime: `${readyDate}T22:00`, maxQuantity: 5 });

  await page.locator('a[href="#/home"]').first().click();
  await page.locator(`[data-action="edit-offering"][data-pid="${second.body.id}"]`).first().click();
  edit = page.locator('#editOfferingForm');
  await expect(edit.locator('[name="hmAvailType"]')).toHaveValue('ALWAYS_AVAILABLE');
  await expect(edit.locator('[name="hmStockMode"]')).toHaveValue('unlimited');
  await expect(edit.locator('[name="chosenOfferingDate"]')).toBeDisabled();
  expect((await apiJson(page, `/api/seller/products/${second.body.id}`)).body.lifecycleState).toBe('DRAFT');

  await page.locator('a[href="#/home"]').first().click();
  await page.locator(`[data-action="edit-offering"][data-pid="${draft.id}"]`).first().click();
  edit = page.locator('#editOfferingForm');
  await expect(edit.locator('[name="hmAvailType"]')).toHaveValue('SPECIFIC_DATE');
  await expect(edit.locator('[name="hmStockMode"]')).toHaveValue('limited');
  await edit.locator('[data-action="set-hm-avail"][data-avail="MADE_ON_REQUEST"]').click();
  await edit.locator('[name="hmRequestPrepTime"]').selectOption('2 Days');
  await edit.locator('[data-action="set-hm-stock"][data-stock="unlimited"]').click();
  updated = page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/products/${draft.id}` && r.request().method() === 'PUT');
  await edit.getByRole('button', { name: 'Save Changes' }).click();
  expect((await updated).status()).toBe(200);
  stored = await apiJson(page, `/api/seller/products/${draft.id}`);
  expect(stored.body).toMatchObject({ lifecycleState: 'DRAFT', availType: 'MADE_ON_REQUEST', maxQuantity: null, remainingQuantity: null });
  expect(stored.body.readyByTime).toContain('2 Days');

  await page.goto('/seller.html#/home');
  await page.locator(`[data-action="edit-offering"][data-pid="${draft.id}"]`).first().click();
  edit = page.locator('#editOfferingForm');
  await expect(edit.locator('[name="hmAvailType"]')).toHaveValue('MADE_ON_REQUEST');
  await expect(edit.locator('[name="hmRequestPrepTime"]')).toHaveValue('2 Days');
  await expect(edit.locator('[name="chosenOfferingDate"]')).toBeDisabled();
  await expect(edit.locator('[name="hmReadyDate"]')).toBeDisabled();
  await edit.locator('[data-action="set-hm-avail"][data-avail="ALWAYS_AVAILABLE"]').click();
  await edit.locator('[data-action="set-hm-stock"][data-stock="limited"]').click();
  await edit.locator('[name="maxQuantity"]').fill('7');
  updated = page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/products/${draft.id}` && r.request().method() === 'PUT');
  await edit.getByRole('button', { name: 'Save Changes' }).click();
  expect((await updated).status()).toBe(200);
  stored = await apiJson(page, `/api/seller/products/${draft.id}`);
  expect(stored.body).toMatchObject({ lifecycleState: 'DRAFT', availType: 'ALWAYS_AVAILABLE', maxQuantity: 7, remainingQuantity: 7 });

  await page.goto('/seller.html#/home');
  await page.locator(`[data-action="edit-offering"][data-pid="${draft.id}"]`).first().click();
  edit = page.locator('#editOfferingForm');
  await expect(edit.locator('[name="hmAvailType"]')).toHaveValue('ALWAYS_AVAILABLE');
  await expect(edit.locator('[name="hmStockMode"]')).toHaveValue('limited');
  await expect(edit.locator('[name="maxQuantity"]')).toHaveValue('7');

  const buyerContext = await browser.newContext({ baseURL: 'http://127.0.0.1:18082' });
  try {
    const buyerPage = await buyerContext.newPage();
    await registerBuyer(buyerPage);
    expect((await apiJson(buyerPage, `/api/products/${draft.id}`)).status).toBe(404);
    const search = await apiJson(buyerPage, '/api/search?q=Draft%20browser%20test');
    expect(search.status).toBe(200);
    expect(search.body.products.some(product => product.id === draft.id)).toBe(false);
  } finally {
    await buyerContext.close();
  }

  const clock = await openE2eClock(browser);
  try {
    await clock.set(`${readyDate}T12:00:00`);
    await page.goto('/seller.html#/my-offerings');
    const card = page.locator('.history-card').filter({ hasText: 'Draft browser test' });
    await expect(card).toBeVisible();
    await expect(card.getByRole('button', { name: 'Edit Draft' })).toBeVisible();
    await expect(card.locator('[data-action="republish-history"]')).toHaveCount(0);
    await card.getByRole('button', { name: 'Edit Draft' }).click();
    await expect(page.locator('#editOfferingForm [name="hmAvailType"]')).toHaveValue('ALWAYS_AVAILABLE');
    expect((await apiJson(page, `/api/seller/products/${draft.id}`)).body.lifecycleState).toBe('DRAFT');

    // Simulate a stale history response; the action must re-read the real product.
    const historyUrl = '**/api/seller-app/history';
    await page.route(historyUrl, async route => {
      const response = await route.fetch();
      const items = await response.json();
      const stale = items.map(item => item.id === draft.id ? { ...item, lifecycleState: 'EXPIRED' } : item);
      await route.fulfill({ response, json: stale });
    });
    await page.goto('/seller.html#/my-offerings');
    const staleCard = page.locator('.history-card').filter({ hasText: 'Draft browser test' });
    const refetch = page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/products/${draft.id}` && r.request().method() === 'GET');
    await staleCard.locator('[data-action="republish-history"]').click();
    expect((await refetch).status()).toBe(200);
    await expect(page.getByText('Edit this draft; publishing drafts is not available yet.')).toBeVisible();
    await expect(page.locator('#createOfferingForm')).toHaveCount(0);
    await page.unroute(historyUrl);

    const previousCard = page.locator('.history-card').filter({ hasText: 'Previous browser offering' });
    const normalRefetch = page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/products/${normal.body.id}` && r.request().method() === 'GET');
    await previousCard.locator('[data-action="republish-history"]').click();
    expect((await normalRefetch).status()).toBe(200);
    await expect(page.locator('#createOfferingForm [name="name"]')).toHaveValue('Previous browser offering');
  } finally {
    await clock.close();
  }
  expect(browserIssues).toEqual([]);
});
