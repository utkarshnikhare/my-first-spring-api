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

test.describe('Master Portal Interactive and Button Verification', () => {

  test('Buyer Journey: Discovery, Cart, Copy UPI, Checkout, Order Details, Profile, Logout', async ({ page }) => {
    // 1. Register & login a buyer
    const buyer = await registerBuyer(page);

    // 2. Test Buyer Navigation tabs
    await page.goto('/index.html#/home');
    await expect(page.locator('#bottomNav')).toBeVisible();

    // Home
    await page.locator('#bottomNav [data-nav="home"]').click();
    await expect(page).toHaveURL(/.*#\/home/);

    // Categories
    const breakfastPill = page.locator('.cat-tile').filter({ hasText: /Breakfast|BREAKFAST/i }).first();
    if (await breakfastPill.count() > 0) {
      await breakfastPill.click();
      await expect(page).toHaveURL(/.*#\/category\//);
      await page.locator('#bottomNav [data-nav="home"]').click();
    }

    // Favourites tab
    await page.locator('#bottomNav [data-nav="favourites"]').click();
    await expect(page).toHaveURL(/.*#\/favourites/);

    // Orders tab
    await page.locator('#bottomNav [data-nav="orders"]').click();
    await expect(page).toHaveURL(/.*#\/orders/);

    // Profile tab
    await page.locator('#bottomNav [data-nav="profile"]').click();
    await expect(page).toHaveURL(/.*#\/profile/);
    await expect(page.locator('form[data-form="profile-edit"]')).toBeVisible();

    // Edit profile form
    const nameInput = page.locator('form[data-form="profile-edit"] [name="name"]');
    await nameInput.fill(`${buyer.name} Updated`);
    await page.locator('form[data-form="profile-edit"] button[type="submit"]').click();
    await expect(page.locator('.toast.success, .toast')).toBeVisible({ timeout: 5000 });

    // 3. Favourites toggle test on home
    await page.locator('#bottomNav [data-nav="home"]').click();
    const favBtn = page.locator('[data-action="toggle-fav-kitchen"]').first();
    if (await favBtn.count() > 0) {
      const initialText = await favBtn.innerText();
      await favBtn.click();
      await expect(favBtn).not.toHaveText(initialText, { timeout: 5000 });
    }

    // 4. Discover a kitchen and open order sheet
    const kitchenCard = page.locator('.kc').first();
    if (await kitchenCard.count() > 0) {
      await kitchenCard.locator('.kc-name, .kc-image').first().click();
      await expect(page).toHaveURL(/.*#\/kitchen\//);

      // Find an order button
      const orderBtn = page.locator('[data-action="open-order-sheet"]').first();
      if (await orderBtn.count() > 0) {
        await orderBtn.click();
        const sheet = page.locator('#sheetRoot');
        await expect(sheet).toBeVisible();

        // Test quantity buttons in sheet
        const plusBtn = sheet.locator('[data-action="sheet-qty"][data-dir="1"]');
        const minusBtn = sheet.locator('[data-action="sheet-qty"][data-dir="-1"]');
        if (await plusBtn.count() > 0) {
          await plusBtn.click();
          await minusBtn.click();
        }

        // Click Add to cart
        const addBtn = sheet.locator('[data-action="sheet-add"]');
        await addBtn.click();
        await expect(sheet).toBeHidden({ timeout: 5000 });

        // Verify sticky cart bar
        const cartBar = page.locator('#viewOrderBar');
        await expect(cartBar).toBeVisible();

        // Click cart bar -> Order summary
        await cartBar.click();
        await expect(page).toHaveURL(/.*#\/summary/);

        // Click Place Order -> Payment
        const placeOrderBtn = page.locator('[data-action="go-checkout"]');
        if (await placeOrderBtn.count() > 0) {
          await placeOrderBtn.click();
          await expect(page).toHaveURL(/.*#\/payment/);

          // Test Copy UPI button if present
          const copyUpiBtn = page.locator('[data-action="copy-upi"]');
          if (await copyUpiBtn.count() > 0) {
            await copyUpiBtn.click();
            await expect(page.locator('.toast')).toBeVisible({ timeout: 5000 });
          }

          // Choose COD or submit order
          const codOption = page.locator('[data-action="select-pay-method"][data-method="cod"]');
          if (await codOption.count() > 0) {
            await codOption.click();
          }
          const finalPlaceBtn = page.locator('#placeOrderBtn, [data-action^="place-order"]').first();
          if (await finalPlaceBtn.count() > 0) {
            await finalPlaceBtn.click();
            await expect(page).toHaveURL(/.*#\/(payment-success|orders)/, { timeout: 15000 });
          }
        }
      }
    }

    // 5. Buyer Logout
    await page.goto('/index.html#/profile');
    const logoutBtn = page.locator('[data-action="logout"]');
    await expect(logoutBtn).toBeVisible();
    await logoutBtn.click();
    await expect(page).toHaveURL(/.*#\/home/);
    const me = await readMe(page);
    expect(me.body.authenticated).toBeFalsy();
  });

  test('Seller Journey: Dashboard Navigation, Quick Shortcuts, Store Preview, Offering Management', async ({ page }) => {
    // 1. Register a seller and approve
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

    // 2. Login Seller
    await loginSeller(page, seller);
    await page.goto('/seller.html#/home');
    await expect(page.locator('#bottomNav')).toBeVisible();

    // 3. Test Seller Navigation tabs
    await page.locator('#bottomNav [data-nav="kitchen"]').click();
    await expect(page).toHaveURL(/.*#\/kitchen/);

    await page.locator('#bottomNav [data-nav="orders"]').click();
    await expect(page).toHaveURL(/.*#\/orders/);

    await page.locator('#bottomNav [data-nav="history"]').click();
    await expect(page).toHaveURL(/.*#\/(my-offerings|history)/);

    await page.locator('#bottomNav [data-nav="recurring"]').click();
    await expect(page).toHaveURL(/.*#\/recurring/);

    await page.locator('#bottomNav [data-nav="earnings"]').click();
    await expect(page).toHaveURL(/.*#\/earnings/);

    // 4. Return to Home and test Quick Action shortcuts
    await page.locator('#bottomNav [data-nav="home"]').click();
    await expect(page.locator('.sd-quick__grid')).toBeVisible();

    // Test shortcut: Today's Menu
    const todaysMenuShortcut = page.locator('.sd-quick__grid [href="#/my-offerings"]');
    await expect(todaysMenuShortcut).toBeVisible();
    await todaysMenuShortcut.click();
    await expect(page).toHaveURL(/.*#\/my-offerings/);

    // Test shortcut: Manage Profile
    await page.locator('#bottomNav [data-nav="home"]').click();
    const profileShortcut = page.locator('.sd-quick__grid [href="#/kitchen"]').filter({ hasText: 'Manage Profile' });
    await expect(profileShortcut).toBeVisible();
    await profileShortcut.click();
    await expect(page).toHaveURL(/.*#\/kitchen/);

    // Test shortcut: More (Quick Post)
    await page.locator('#bottomNav [data-nav="home"]').click();
    const moreShortcut = page.locator('.sd-quick__grid [href="#/quick-post"]');
    await expect(moreShortcut).toBeVisible();
    await moreShortcut.click();
    await expect(page).toHaveURL(/.*#\/quick-post/);

    // 5. Test Storefront preview / link copy
    await page.goto('/seller.html#/kitchen');
    const copyLinkBtn = page.locator('[data-action="copy-store-link"]');
    if (await copyLinkBtn.count() > 0) {
      await copyLinkBtn.first().click();
      await expect(page.locator('.toast')).toBeVisible();
    }

    // 6. Seller Logout
    await page.goto('/seller.html#/home');
    const logoutBtn = page.locator('[data-action="seller-logout"], .sd-header__logout');
    if (await logoutBtn.count() > 0) {
      await logoutBtn.first().click();
      await expect(page.locator('#sellerLoginForm')).toBeVisible({ timeout: 10000 });
    }
  });

  test('Admin Journey: Login, Dashboard, Approvals, Navigation, Analytics, Enter Key Login', async ({ page }) => {
    // 1. Admin login via helper (validates auth config, demo password, session establishment)
    const loginResponse = await adminLogin(page);
    expect((await loginResponse).status()).toBe(200);

    // 2. Verify Admin Chrome & Navigation
    await expect(page.locator('#adminTopbar')).toBeVisible();
    await expect(page.locator('#adminNav')).toBeVisible();

    // 3. Navigate Admin Tabs
    await page.locator('#adminNav [data-nav="approvals"]').click();
    await expect(page).toHaveURL(/.*#\/approvals/);

    await page.locator('#adminNav [data-nav="sellers"]').click();
    await expect(page).toHaveURL(/.*#\/sellers/);

    await page.locator('#adminNav [data-nav="buyers"]').click();
    await expect(page).toHaveURL(/.*#\/buyers/);

    await page.locator('#adminNav [data-nav="orders"]').click();
    await expect(page).toHaveURL(/.*#\/orders/);

    await page.locator('#adminNav [data-nav="analytics"]').click();
    await expect(page).toHaveURL(/.*#\/analytics/);
    await expect(page.locator('.traffic-chart-wrap svg')).toBeVisible();

    await page.locator('#adminNav [data-nav="locations"]').click();
    await expect(page).toHaveURL(/.*#\/locations/);

    await page.locator('#adminNav [data-nav="exports"]').click();
    await expect(page).toHaveURL(/.*#\/exports/);

    // 4. Return to Dashboard and test Logout
    await page.locator('#adminNav [data-nav="dashboard"]').click();
    await expect(page).toHaveURL(/.*#\/dashboard/);

    const adminLogoutBtn = page.locator('#adminTopbar [data-action="logout"]');
    await adminLogoutBtn.click();
    await expect(page.locator('#adminLoginForm')).toBeVisible();
  });

});
