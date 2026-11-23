const {
  test,
  expect,
  logout,
  registerSeller,
  adminLogin,
  loginSeller,
  apiJson
} = require('./support.cjs');

test('seller can load existing favourite, see active toggle, unfavourite to free slot, and add replacement favourite', async ({
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

  // 3. Pre-create 3 favourites via API
  const t1 = await apiJson(page, '/api/seller-app/templates', {
    method: 'POST',
    body: {
      name: 'Poha Special',
      description: 'Hot breakfast poha',
      price: 60,
      priceUnit: 'portion',
      orderWindowEnd: '09:00',
      category: 'BREAKFAST'
    }
  });
  expect(t1.status).toBe(200);

  const t2 = await apiJson(page, '/api/seller-app/templates', {
    method: 'POST',
    body: {
      name: 'Lunch Thali',
      description: 'Complete home thali',
      price: 150,
      priceUnit: 'portion',
      orderWindowEnd: '12:30',
      category: 'LUNCH'
    }
  });
  expect(t2.status).toBe(200);

  const t3 = await apiJson(page, '/api/seller-app/templates', {
    method: 'POST',
    body: {
      name: 'Evening Samosa',
      description: 'Crispy samosa pair',
      price: 40,
      priceUnit: 'portion',
      orderWindowEnd: '17:00',
      category: 'SNACKS'
    }
  });
  expect(t3.status).toBe(200);

  // Verify exactly 3 favourites exist
  const initialTemplates = await apiJson(page, '/api/seller-app/templates');
  expect(initialTemplates.status).toBe(200);
  expect(initialTemplates.body.length).toBe(3);

  // 4. Navigate to Add Offering
  await page.goto('/seller.html#/add');
  await expect(page.getByRole('heading', { name: 'Add Offering' })).toBeVisible();

  // Verify all 3 favourite pills are visible
  await expect(page.locator('.fav-pill').filter({ hasText: 'Poha Special' })).toBeVisible();
  await expect(page.locator('.fav-pill').filter({ hasText: 'Lunch Thali' })).toBeVisible();
  await expect(page.locator('.fav-pill').filter({ hasText: 'Evening Samosa' })).toBeVisible();

  // 5. Test creating from scratch when 3 slots are full
  await page.locator('[data-action="go-create"]').click();
  await expect(page.getByRole('heading', { name: 'Create Offering' })).toBeVisible();

  // The toggle must be DISABLED with cap warning
  const favToggle = page.locator('#favToggle');
  await expect(favToggle).toBeVisible();
  await expect(favToggle).toHaveClass(/disabled/);
  await expect(page.locator('#favToggleLabel')).toHaveText('☆ Add to Favourites');
  await expect(page.locator('#favToggleNote')).toContainText('You can save up to 3 favourite offerings. Remove an existing favourite to add another.');

  // 6. Go back to Add Offering and load an existing favourite ("Lunch Thali")
  await page.goto('/seller.html#/add');
  await page.locator('.fav-pill').filter({ hasText: 'Lunch Thali' }).click();

  // Form opens populated with Lunch Thali
  await expect(page.getByRole('heading', { name: 'Create Offering' })).toBeVisible();
  await expect(page.locator('input[name="name"]')).toHaveValue('Lunch Thali');

  // Verify Favourite control is ACTIVE / ON
  await expect(favToggle).toHaveClass(/on/);
  await expect(favToggle).not.toHaveClass(/disabled/);
  await expect(page.locator('#favToggleLabel')).toHaveText('★ Remove from Favourites');
  await expect(page.locator('#favToggleNote')).toContainText('This offering is saved in your favourites (tap switch to remove).');

  // 7. Click the active toggle to UNFAVOURITE
  const deletePromise = page.waitForResponse(response =>
    response.url().includes(`/api/seller-app/templates/${t2.body.id}`) && response.request().method() === 'DELETE');
  await favToggle.click();
  const deleteResponse = await deletePromise;
  expect(deleteResponse.status()).toBe(200);

  // Verify toggle updates immediately to unselected state
  await expect(favToggle).not.toHaveClass(/on/);
  await expect(page.locator('#favToggleLabel')).toHaveText('☆ Add to Favourites');
  await expect(page.locator('#favToggleNote')).toHaveText('Save as template (max 3).');

  // 8. Verify backend count is now 2
  const updatedTemplates = await apiJson(page, '/api/seller-app/templates');
  expect(updatedTemplates.status).toBe(200);
  expect(updatedTemplates.body.length).toBe(2);
  const remainingNames = updatedTemplates.body.map(x => x.name);
  expect(remainingNames).not.toContain('Lunch Thali');
  expect(remainingNames).toEqual(expect.arrayContaining(['Poha Special', 'Evening Samosa']));

  // 9. Go back to Add Offering: only 2 pills remain
  await page.goto('/seller.html#/add');
  await expect(page.locator('.fav-pill').filter({ hasText: 'Poha Special' })).toBeVisible();
  await expect(page.locator('.fav-pill').filter({ hasText: 'Evening Samosa' })).toBeVisible();
  await expect(page.locator('.fav-pill').filter({ hasText: 'Lunch Thali' })).toHaveCount(0);

  // 10. Now create a replacement favourite and verify slot is available
  await page.locator('[data-action="go-create"]').click();
  await expect(favToggle).not.toHaveClass(/disabled/);
  await expect(page.locator('#favToggleLabel')).toHaveText('☆ Add to Favourites');
  await expect(page.locator('#favToggleNote')).toHaveText('Save as template (max 3).');

  // Toggle it ON for the new item
  await favToggle.click();
  await expect(favToggle).toHaveClass(/on/);
  await expect(page.locator('#favToggleLabel')).toHaveText('★ Remove from Favourites');

  // Fill and publish replacement offering using lunch preset
  await page.locator('button[data-preset="lunch"]').click();
  await page.locator('input[name="name"]').fill('Paneer Bhurji');
  await page.locator('input[name="price"]').fill('120');

  const publishResponsePromise = page.waitForResponse(response =>
    response.url().includes('/api/seller/products') && response.request().method() === 'POST');
  const templateSavePromise = page.waitForResponse(response =>
    response.url().includes('/api/seller-app/templates') && response.request().method() === 'POST');

  await page.locator('button[type="submit"]').click();
  expect((await publishResponsePromise).status()).toBe(200);
  expect((await templateSavePromise).status()).toBe(200);

  // 11. Verify count is back to 3 in backend
  const finalTemplates = await apiJson(page, '/api/seller-app/templates');
  expect(finalTemplates.status).toBe(200);
  expect(finalTemplates.body.length).toBe(3);
  expect(finalTemplates.body.map(x => x.name)).toEqual(
    expect.arrayContaining(['Poha Special', 'Evening Samosa', 'Paneer Bhurji'])
  );

  // 11b. Test direct unfavourite from pill remove button on #/add
  await page.goto('/seller.html#/add');
  const paneerPill = page.locator('.fav-pill').filter({ hasText: 'Paneer Bhurji' });
  await expect(paneerPill).toBeVisible();
  const pillDeleteBtn = paneerPill.locator('.fav-pill-remove');
  await expect(pillDeleteBtn).toBeVisible();

  const directDeletePromise = page.waitForResponse(response =>
    response.url().includes('/api/seller-app/templates/') && response.request().method() === 'DELETE');
  await pillDeleteBtn.click();
  expect((await directDeletePromise).status()).toBe(200);

  // Verify pill is immediately removed
  await expect(page.locator('.fav-pill').filter({ hasText: 'Paneer Bhurji' })).toHaveCount(0);
  const afterDirectDelete = await apiJson(page, '/api/seller-app/templates');
  expect(afterDirectDelete.body.length).toBe(2);

  // Re-add to test cap enforcement
  const readded = await apiJson(page, '/api/seller-app/templates', {
    method: 'POST',
    body: {
      name: 'Paneer Bhurji',
      price: 120,
      orderWindowEnd: '14:00',
      category: 'LUNCH'
    }
  });
  expect(readded.status).toBe(200);

  // 12. Verify 4th addition is blocked by backend (cap of 3 enforced)
  const blockedFourth = await apiJson(page, '/api/seller-app/templates', {
    method: 'POST',
    body: {
      name: 'Fourth Dish',
      price: 50,
      orderWindowEnd: '18:00',
      category: 'DINNER'
    }
  });
  expect(blockedFourth.status).toBeGreaterThanOrEqual(400);

  // Clear expected 409 rejection from browser issues list
  const idx = browserIssues.indexOf('API 409: POST /api/seller-app/templates');
  if (idx !== -1) browserIssues.splice(idx, 1);

  expect(browserIssues).toEqual([]);
});
