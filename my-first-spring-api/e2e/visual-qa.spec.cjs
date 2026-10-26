const fs = require('node:fs');
const path = require('node:path');
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

const VIEWPORTS = [
  { width: 320, height: 568, name: '320x568-mobile-small' },
  { width: 360, height: 800, name: '360x800-mobile-android' },
  { width: 390, height: 844, name: '390x844-mobile-iphone' },
  { width: 430, height: 932, name: '430x932-mobile-pro-max' },
  { width: 768, height: 1024, name: '768x1024-tablet-portrait' },
  { width: 1024, height: 768, name: '1024x768-tablet-landscape' },
  { width: 1280, height: 800, name: '1280x800-laptop' },
  { width: 1440, height: 900, name: '1440x900-desktop-std' },
  { width: 1920, height: 1080, name: '1920x1080-desktop-fhd' }
];

const screenshotDir = path.join(__dirname, 'test-results', 'screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(() => {
    const scrollW = document.documentElement.scrollWidth;
    const clientW = document.documentElement.clientWidth;
    const innerW = window.innerWidth;
    // Allow up to 1px tolerance for subpixel font/CSS rendering
    if (scrollW > Math.max(clientW, innerW) + 1) {
      const all = Array.from(document.querySelectorAll('*'));
      const culprits = all.filter(el => {
        const rect = el.getBoundingClientRect();
        return rect.right > innerW + 1;
      }).map(el => ({
        tag: el.tagName,
        id: el.id,
        className: el.className,
        right: el.getBoundingClientRect().right,
        width: el.getBoundingClientRect().width
      })).slice(0, 5);
      return { scrollW, innerW, culprits };
    }
    return null;
  });
  expect(overflow, `Horizontal overflow detected at ${label}: ${JSON.stringify(overflow)}`).toBeNull();
}

async function takeVisualSnapshot(page, name) {
  const filePath = path.join(screenshotDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage: false });
}

test.describe('Visual QA & Responsive Viewport Audits', () => {

  test('Buyer screens visual and overflow audit across all 9 viewports', async ({ page, browserIssues }) => {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      // 1. Buyer unauthenticated home / marketplace
      await page.goto('/index.html');
      await expect(page.locator('body')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Buyer Home [${vp.name}]`);
      if (vp.width === 360 || vp.width === 1440) {
        await takeVisualSnapshot(page, `buyer-home-${vp.name}`);
      }

      // 2. Buyer theme toggle check
      await page.locator('[data-action="toggle-theme"]').first().click();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
      await assertNoHorizontalOverflow(page, `Buyer Home Dark [${vp.name}]`);
      // Toggle back to light
      await page.locator('[data-action="toggle-theme"]').first().click();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

      // 3. Favourites empty state
      await page.goto('/index.html#/favourites');
      await expect(page.locator('#view')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Buyer Favourites [${vp.name}]`);

      // 4. Cart / Summary empty or initial state
      await page.goto('/index.html#/summary');
      await expect(page.locator('#view')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Buyer Summary [${vp.name}]`);
    }
    expect(browserIssues).toEqual([]);
  });

  test('Seller screens visual and overflow audit across all 9 viewports', async ({ page, browserIssues }) => {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      // 1. Seller login & registration page
      await page.goto('/seller.html');
      await expect(page.locator('#sellerLoginForm')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Seller Login [${vp.name}]`);

      // 2. Seller Registration form
      await page.getByRole('button', { name: 'Register as a seller' }).click();
      await expect(page.locator('#sellerRegistrationForm')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Seller Reg Form [${vp.name}]`);
      if (vp.width === 360 || vp.width === 1440) {
        await takeVisualSnapshot(page, `seller-reg-${vp.name}`);
      }

      // Return to login
      await page.getByRole('button', { name: 'Back to sign in' }).click();
      await expect(page.locator('#sellerLoginForm')).toBeVisible();
    }
    expect(browserIssues).toEqual([]);
  });

  test('Admin screens visual and overflow audit across all 9 viewports', async ({ page, browserIssues }) => {
    const adminLoginResponse = await adminLogin(page);
    expect(adminLoginResponse.status()).toBe(200);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });

      // 1. Admin Approvals
      await page.locator('a[data-nav="approvals"]').first().click();
      await expect(page.locator('#view')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Admin Approvals [${vp.name}]`);
      if (vp.width === 360 || vp.width === 1440) {
        await takeVisualSnapshot(page, `admin-approvals-${vp.name}`);
      }

      // 2. Admin Dark theme toggle
      await page.locator('[data-action="toggle-theme"]').first().click();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
      await assertNoHorizontalOverflow(page, `Admin Dark Approvals [${vp.name}]`);
      // Toggle back to light
      await page.locator('[data-action="toggle-theme"]').first().click();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

      // 3. Admin Orders / Reporting
      await page.locator('a[data-nav="orders"]').first().click();
      await expect(page.locator('#view')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Admin Orders [${vp.name}]`);

      // 4. Admin Dashboard
      await page.locator('a[data-nav="dashboard"]').first().click();
      await expect(page.locator('#view')).toBeVisible();
      await assertNoHorizontalOverflow(page, `Admin Dashboard [${vp.name}]`);
    }

    await logout(page);
    expect(browserIssues).toEqual([]);
  });

  test('Complete End-to-End Visual Journey with offering creation, order, cancellation & inventory verification', async ({
    page,
    browserIssues
  }) => {
    // Test on 390x844 (standard mobile)
    await page.setViewportSize({ width: 390, height: 844 });

    // Step 1: Register Seller
    const seller = await registerSeller(page);
    await assertNoHorizontalOverflow(page, 'Seller Pending Screen 390x844');
    await takeVisualSnapshot(page, 'seller-pending-390x844');
    await logout(page);

    // Step 2: Admin approves seller
    await adminLogin(page);
    await page.locator('a[data-nav="approvals"]').first().click();
    const sellerRow = page.locator('.seller-row').filter({ hasText: seller.name });
    await expect(sellerRow).toBeVisible();
    const approvalPromise = page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
    await sellerRow.getByRole('button', { name: 'Approve' }).click();
    expect((await approvalPromise).status()).toBe(200);
    await logout(page);

    // Step 3: Seller logs in, checks home/dashboard, creates offering
    await loginSeller(page, seller);
    await page.locator('a[data-nav="home"]').first().click();
    await assertNoHorizontalOverflow(page, 'Seller Home 390x844');
    await takeVisualSnapshot(page, 'seller-home-390x844');

    const offeringName = `Visual QA Meal ${seller.token}`;
    await page.locator('[data-action="go-add"]').click();
    await page.locator('[data-action="go-create"]').click();
    const offeringForm = page.locator('#createOfferingForm');
    await expect(offeringForm).toBeVisible();
    await assertNoHorizontalOverflow(page, 'Seller Create Offering 390x844');
    await takeVisualSnapshot(page, 'seller-create-offering-390x844');

    await offeringForm.locator('[name="name"]').fill(offeringName);
    await offeringForm.locator('[name="description"]').fill('Audited meal for responsive visual QA');
    await offeringForm.locator('[name="price"]').fill('120');
    await offeringForm.locator('[name="maxQuantity"]').fill('5');
    if (await offeringForm.locator('button[data-preset="lunch"]').count() > 0) {
      await offeringForm.locator('button[data-preset="lunch"]').click();
    } else {
      await offeringForm.locator('input[name="categories"][value="LUNCH"]').check();
      await offeringForm.locator('[name="orderWindowEnd"]').fill('23:59');
    }
    await offeringForm.getByRole('button', { name: 'Publish Offering' }).click();
    await expect(page.locator('#createOfferingForm')).toHaveCount(0);

    // Check offerings list
    await page.locator('a[data-nav="history"]').first().click();
    await assertNoHorizontalOverflow(page, 'Seller Offerings List 390x844');
    await takeVisualSnapshot(page, 'seller-offerings-list-390x844');

    await logout(page);

    // Step 4: Buyer registers and places order
    const buyer = await registerBuyer(page);
    await page.goto('/index.html#/food');
    await assertNoHorizontalOverflow(page, 'Buyer Marketplace 390x844');

    const itemLink = page.getByRole('link', { name: new RegExp(offeringName) });
    await expect(itemLink).toBeVisible();
    await itemLink.click();
    await assertNoHorizontalOverflow(page, 'Buyer Item Detail 390x844');
    await takeVisualSnapshot(page, 'buyer-item-detail-390x844');

    const visitKitchen = page.getByRole('link', { name: new RegExp(`Visit ${seller.kitchenName}`) });
    await visitKitchen.click();
    await expect(page.locator('body')).toContainText(seller.kitchenName);
    await assertNoHorizontalOverflow(page, 'Buyer Kitchen Storefront 390x844');
    await takeVisualSnapshot(page, 'buyer-kitchen-storefront-390x844');

    await page.getByRole('button', { name: /^(ORDER|PRE-ORDER)$/ }).click();
    await expect(page.locator('#sheetRoot')).toBeVisible();
    await assertNoHorizontalOverflow(page, 'Buyer Quantity Sheet 390x844');
    await takeVisualSnapshot(page, 'buyer-quantity-sheet-390x844');

    await page.locator('#sheetRoot').getByRole('button', { name: 'Add to Order' }).click();
    await page.goto('/index.html#/summary');
    await assertNoHorizontalOverflow(page, 'Buyer Summary 390x844');

    await page.locator('#orderNote').fill('Please deliver to flat with care');
    await page.locator('[data-action="go-checkout"]').click();
    await expect(page.getByRole('heading', { name: 'Confirm Order' })).toBeVisible();
    await assertNoHorizontalOverflow(page, 'Buyer Confirm Order 390x844');
    await takeVisualSnapshot(page, 'buyer-confirm-order-390x844');

    // Immediate payment choice
    await page.locator('[data-action="select-pay-status"][data-status="PENDING"]').click();
    await page.locator('#placeOrderBtn').click();
    await expect(page.getByRole('heading', { name: 'Order Confirmed' })).toBeVisible();
    await assertNoHorizontalOverflow(page, 'Buyer Order Confirmed 390x844');
    await takeVisualSnapshot(page, 'buyer-order-confirmed-390x844');

    // Step 5: Verify buyer order history
    await page.goto('/index.html#/orders');
    await assertNoHorizontalOverflow(page, 'Buyer Orders History 390x844');
    await takeVisualSnapshot(page, 'buyer-orders-history-390x844');

    await logout(page);

    // Step 6: Seller order management & cancellation drill
    await loginSeller(page, seller);
    const sellerProducts = await apiJson(page, '/api/seller/products');
    const products = Array.isArray(sellerProducts.body) ? sellerProducts.body : sellerProducts.body.products;
    const offering = products.find(p => p.name === offeringName);
    expect(offering).toBeTruthy();
    expect(offering.remainingQuantity).toBe(4); // 5 - 1 = 4

    await page.goto(`/seller.html#/order-detail/${offering.id}`);
    await assertNoHorizontalOverflow(page, 'Seller Order Detail 390x844');
    await takeVisualSnapshot(page, 'seller-order-detail-390x844');

    // Cancel order to test stock restoration drill
    const ordersRes = await apiJson(page, '/api/seller/orders');
    expect(ordersRes.status).toBe(200);
    const sellerOrders = Array.isArray(ordersRes.body) ? ordersRes.body : [];
    expect(sellerOrders.length).toBeGreaterThan(0);
    const orderToCancel = sellerOrders[0];

    const cancelRes = await apiJson(page, `/api/seller/orders/${orderToCancel.id}/status`, {
      method: 'PATCH',
      body: { orderStatus: 'CANCELLED', reason: 'Visual QA cancellation and restocking drill' }
    });
    expect(cancelRes.status).toBe(200);

    // Verify stock restored back to 5
    const restockedProducts = await apiJson(page, '/api/seller/products');
    const pList = Array.isArray(restockedProducts.body) ? restockedProducts.body : restockedProducts.body.products;
    const restockedOffering = pList.find(p => p.name === offeringName);
    expect(restockedOffering.remainingQuantity).toBe(5);

    await logout(page);
    expect(browserIssues).toEqual([]);
  });

  test('Seller Lifecycle States (PENDING, CHANGES_REQUESTED, REJECTED) and publication gate', async ({
    page,
    browserIssues
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });

    // 1. Pending Seller cannot publish
    const seller = await registerSeller(page);
    const publishAttempt = await apiJson(page, '/api/seller/products', {
      method: 'POST',
      body: {
        name: 'Unauthorized Meal',
        description: 'Should never publish',
        price: 100,
        maxQuantity: 10,
        categories: ['LUNCH'],
        orderWindowEnd: '23:59'
      }
    });
    expect([401, 403]).toContain(publishAttempt.status);

    await logout(page);

    // 2. Admin requests changes
    await adminLogin(page);
    const reqChanges = await apiJson(page, `/api/admin/sellers/${seller.id}/request-changes`, {
      method: 'POST',
      body: { reason: 'Please update your kitchen description with specific items offered.' }
    });
    expect(reqChanges.status).toBe(200);
    await logout(page);

    // 3. Seller in CHANGES_REQUESTED state logs in (HTTP 200, renders changes-requested view)
    await page.goto('/seller.html');
    await page.locator('#sellerLoginMobile').fill(seller.mobile);
    await page.locator('#sellerLoginPassword').fill(seller.password);
    const crLogin = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
    await page.locator('#sellerLoginForm').getByRole('button', { name: 'Sign in' }).click();
    expect((await crLogin).status()).toBe(200);
    await expect(page.locator('body')).toContainText('CHANGES_REQUESTED');
    await expect(page.locator('body')).toContainText('Changes requested on your application');
    await takeVisualSnapshot(page, 'seller-changes-requested-390x844');

    // CHANGES_REQUESTED seller also cannot publish
    const crPublish = await apiJson(page, '/api/seller/products', {
      method: 'POST',
      body: { name: 'CR Meal', description: 'desc', price: 100, maxQuantity: 5, categories: ['LUNCH'], orderWindowEnd: '23:59' }
    });
    expect([401, 403]).toContain(crPublish.status);

    await logout(page);

    // 4. Admin rejects seller
    await adminLogin(page);
    const rejectRes = await apiJson(page, `/api/admin/sellers/${seller.id}/reject`, {
      method: 'POST',
      body: { reason: 'Application does not meet society hygiene criteria.' }
    });
    expect(rejectRes.status).toBe(200);
    await logout(page);

    // 5. Rejected seller logs in (HTTP 200, renders rejected state)
    await page.goto('/seller.html');
    await page.locator('#sellerLoginMobile').fill(seller.mobile);
    await page.locator('#sellerLoginPassword').fill(seller.password);
    const rejLogin = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
    await page.locator('#sellerLoginForm').getByRole('button', { name: 'Sign in' }).click();
    expect((await rejLogin).status()).toBe(200);
    await expect(page.locator('body')).toContainText('REJECTED');
    await takeVisualSnapshot(page, 'seller-rejected-390x844');

    // Rejected seller cannot publish
    const rejPublish = await apiJson(page, '/api/seller/products', {
      method: 'POST',
      body: { name: 'Rej Meal', description: 'desc', price: 100, maxQuantity: 5, categories: ['LUNCH'], orderWindowEnd: '23:59' }
    });
    expect([401, 403]).toContain(rejPublish.status);

    await logout(page);
    expect(browserIssues).toEqual([]);
  });

  test('Cross-Role Boundary Defense & Authorization Protection', async ({
    page,
    browserIssues
  }) => {
    // Register Buyer
    const buyer = await registerBuyer(page);
    const me = await readMe(page);
    expect(me.body.authenticated).toBe(true);
    expect(me.body.role).toBe('BUYER');

    // Buyer attempts to access seller endpoints -> must be blocked with 403
    const sellerAccess = await apiJson(page, '/api/seller/products');
    expect(sellerAccess.status).toBe(403);

    // Buyer attempts to access admin endpoints -> must be blocked with 403
    const adminAccess = await apiJson(page, '/api/admin/sellers');
    expect(adminAccess.status).toBe(403);

    await logout(page);
    expect(browserIssues).toEqual([]);
  });

  test('Accessibility, Form Labels, and Touch Target Audit on mobile viewport', async ({
    page,
    browserIssues
  }) => {
    await page.setViewportSize({ width: 360, height: 800 });

    // Check Buyer Home accessibility
    await page.goto('/index.html');
    const homeH1 = await page.locator('h1').count();
    expect(homeH1).toBeGreaterThanOrEqual(1);

    // Check Seller Login form inputs have labels or placeholders
    await page.goto('/seller.html');
    const inputs = await page.evaluate(() => {
      const allInputs = Array.from(document.querySelectorAll('input:not([type="hidden"])'));
      return allInputs.map(input => ({
        id: input.id,
        name: input.name,
        type: input.type,
        hasLabel: !!document.querySelector(`label[for="${input.id}"]`),
        hasAriaLabel: !!input.getAttribute('aria-label') || !!input.getAttribute('aria-labelledby'),
        hasPlaceholder: !!input.getAttribute('placeholder')
      }));
    });
    for (const inp of inputs) {
      const isAccessible = inp.hasLabel || inp.hasAriaLabel || inp.hasPlaceholder;
      expect(isAccessible, `Input #${inp.id || inp.name} should have accessible label or placeholder`).toBe(true);
    }

    // Check touch target heights of navigation items (min 40px) on buyer storefront where bottom nav is active
    await page.goto('/index.html');
    const navItems = await page.evaluate(() => {
      const items = Array.from(document.querySelectorAll('.bottom-nav .nav-item')).filter(el => {
        const rect = el.getBoundingClientRect();
        return rect.height > 0 || rect.width > 0;
      });
      return items.map(el => {
        const rect = el.getBoundingClientRect();
        return { height: rect.height, width: rect.width };
      });
    });
    expect(navItems.length).toBeGreaterThan(0);
    for (const item of navItems) {
      expect(item.height, 'Navigation touch target height should be >= 40px').toBeGreaterThanOrEqual(40);
    }

    expect(browserIssues).toEqual([]);
  });
});
