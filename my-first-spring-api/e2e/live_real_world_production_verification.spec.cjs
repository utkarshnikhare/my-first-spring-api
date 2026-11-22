const { test, expect, devices } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const LIVE_BASE_URL = 'https://sociomart-frontend.netlify.app';
const evidenceDir = path.resolve('C:/Users/Admin/.gemini/antigravity/brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/live_production');

test.describe('Real-World Live Production Verification (Commit 7ff18db)', () => {

  test('1. Buyer Theme Switcher & Persistence (Live Production)', async ({ page }) => {
    await page.goto(`${LIVE_BASE_URL}/index.html#/home`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#bottomNav');

    // 1. Initial Light Theme
    const initialTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
    const lightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const lightText = await page.evaluate(() => getComputedStyle(document.body).color);
    expect(initialTheme).toBe('light');
    expect(lightBg).toBe('rgb(245, 239, 230)'); // Authentic Warm Editorial Light

    // 2. Toggle to Dark Theme
    const themeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    await themeBtn.click();

    const darkTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    const darkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const darkText = await page.evaluate(() => getComputedStyle(document.body).color);
    const storedTheme = await page.evaluate(() => localStorage.getItem('sociomart-theme'));

    console.log(`[Buyer Live] Dark Theme: ${darkTheme}, Bg: ${darkBg}, Stored: ${storedTheme}`);
    expect(darkTheme).toBe('dark');
    expect(storedTheme).toBe('dark');
    expect(darkBg).toBe('rgb(28, 22, 19)'); // Authentic Warm Editorial Dark (#1C1613)
    expect(darkText).toBe('rgb(245, 239, 230)'); // Light typography (#F5EFE6)

    // 3. Reload Page and verify persistence
    await page.reload({ waitUntil: 'networkidle' });
    const persistedTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    const persistedBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(persistedTheme).toBe('dark');
    expect(persistedBg).toBe('rgb(28, 22, 19)');

    // 4. Toggle back to Light Theme
    await page.locator('[data-action="toggle-theme"]:visible').first().click();
    const restoredTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    const restoredBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(restoredTheme).toBe('light');
    expect(restoredBg).toBe('rgb(245, 239, 230)');
  });

  test('2. Seller & Admin Theme Switcher & Persistence (Live Production)', async ({ page }) => {
    // A. Seller Login Screen Theme Toggle
    await page.goto(`${LIVE_BASE_URL}/seller.html`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#sellerLoginForm');

    const sellerThemeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    expect(await sellerThemeBtn.count()).toBeGreaterThan(0);

    const sellerLightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await sellerThemeBtn.click();
    const sellerDarkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    console.log(`[Seller Live] Light: ${sellerLightBg}, Dark: ${sellerDarkBg}`);
    expect(sellerDarkBg).toBe('rgb(28, 22, 19)');

    // Reload and verify persistence
    await page.reload({ waitUntil: 'networkidle' });
    expect(await page.evaluate(() => document.documentElement.getAttribute('data-theme'))).toBe('dark');
    await page.locator('[data-action="toggle-theme"]:visible').first().click(); // revert

    // B. Admin Login Screen Theme Toggle
    await page.goto(`${LIVE_BASE_URL}/admin.html`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#adminLoginForm');

    const adminThemeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    expect(await adminThemeBtn.count()).toBeGreaterThan(0);

    const adminLightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await adminThemeBtn.click();
    const adminDarkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    console.log(`[Admin Live] Light: ${adminLightBg}, Dark: ${adminDarkBg}`);
    expect(adminDarkBg).toBe('rgb(28, 22, 19)');

    // Reload and verify persistence
    await page.reload({ waitUntil: 'networkidle' });
    expect(await page.evaluate(() => document.documentElement.getAttribute('data-theme'))).toBe('dark');
    await page.locator('[data-action="toggle-theme"]:visible').first().click(); // revert
  });

  test('3. Major Workflows Against Live Production APIs (Render & Netlify)', async ({ page }) => {
    // A. Buyer Discovery & Navigation against live Render API
    await page.goto(`${LIVE_BASE_URL}/index.html#/home`, { waitUntil: 'networkidle' });

    // Verify Tiles loaded from live home view
    const tiles = page.locator('.tiles-grid .tile');
    await expect(tiles.first()).toBeVisible({ timeout: 10000 });
    const tileCount = await tiles.count();
    console.log(`[Buyer Live] Loaded ${tileCount} module tiles on Home`);
    expect(tileCount).toBeGreaterThanOrEqual(2);

    // Click "Food & Kitchens" tile to enter discovery hub
    await tiles.first().click();
    await page.waitForSelector('.cat-tile');
    const catTiles = page.locator('.cat-tile');
    expect(await catTiles.count()).toBeGreaterThanOrEqual(1);

    // Test Navigation tabs: Favourites
    await page.locator('[data-nav="favourites"]').click();
    await page.waitForSelector('.empty, .fav-list');
    expect(page.url()).toContain('#/favourites');

    // Test Navigation tabs: Orders
    await page.locator('[data-nav="orders"]').click();
    await page.waitForSelector('.empty, .order-list');
    expect(page.url()).toContain('#/orders');

    // Return to Home
    await page.locator('[data-nav="home"]').click();
    await page.waitForSelector('.tiles-grid');

    // B. Seller Registration & Login Form controls
    await page.goto(`${LIVE_BASE_URL}/seller.html`, { waitUntil: 'networkidle' });
    const regToggleBtn = page.getByRole('button', { name: 'Register as a seller' });
    await regToggleBtn.click();
    await expect(page.locator('#sellerRegistrationForm')).toBeVisible();

    // Test back to login toggle
    const backToLoginBtn = page.locator('[data-action="seller-show-login"]');
    await backToLoginBtn.click();
    await expect(page.locator('#sellerLoginForm')).toBeVisible();

    // Test password toggle button
    const pwdInput = page.locator('#sellerLoginPassword');
    const pwdToggleBtn = page.locator('#sellerLoginForm .password-toggle-btn');
    expect(await pwdInput.getAttribute('type')).toBe('password');
    await pwdToggleBtn.click();
    expect(await pwdInput.getAttribute('type')).toBe('text');
    await pwdToggleBtn.click();
    expect(await pwdInput.getAttribute('type')).toBe('password');
  });

  test('4. Service Worker v3 Automatic Cache Upgrade Verification', async ({ page }) => {
    await page.goto(`${LIVE_BASE_URL}/index.html`, { waitUntil: 'networkidle' });

    // Inspect Service Worker registration and active Cache Storage
    const swInfo = await page.evaluate(async () => {
      if (!('serviceWorker' in navigator)) return { supported: false };
      const reg = await navigator.serviceWorker.getRegistration();
      const keys = await caches.keys();
      return {
        supported: true,
        hasRegistration: !!reg,
        scope: reg ? reg.scope : null,
        cacheKeys: keys
      };
    });

    console.log('[PWA Live] Service Worker registration & caches:', JSON.stringify(swInfo));
    expect(swInfo.supported).toBeTruthy();
    // Verify sociomart-shell-v3 is present or active, and no stale v1/v2 caches remain
    if (swInfo.cacheKeys.length > 0) {
      expect(swInfo.cacheKeys).toContain('sociomart-shell-v3');
      expect(swInfo.cacheKeys).not.toContain('sociomart-shell-v2');
      expect(swInfo.cacheKeys).not.toContain('sociomart-shell-v1');
    }
  });

  test('5. Android Mobile Emulation (Pixel 7 Viewport 412x915 with Touch)', async ({ browser }) => {
    // Emulate real Android device: Pixel 7
    const context = await browser.newContext({
      ...devices['Pixel 7'],
      locale: 'en-IN',
      timezoneId: 'Asia/Kolkata'
    });
    const page = await context.newPage();

    // Check Buyer Home on Android Mobile
    await page.goto(`${LIVE_BASE_URL}/index.html#/home`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#bottomNav');

    // Verify zero horizontal scroll overflow
    const overflow = await page.evaluate(() => {
      return document.documentElement.scrollWidth > window.innerWidth;
    });
    expect(overflow).toBeFalsy();

    // Verify Fixed Bottom Nav clearance
    const navBox = await page.locator('#bottomNav').boundingBox();
    expect(navBox).not.toBeNull();
    expect(navBox.height).toBeGreaterThanOrEqual(48);

    // Verify view padding bottom
    const viewPaddingBottom = await page.evaluate(() => {
      const el = document.querySelector('.view');
      return el ? parseInt(getComputedStyle(el).paddingBottom, 10) : 0;
    });
    console.log(`[Android Pixel 7] View padding bottom: ${viewPaddingBottom}px, Nav Height: ${navBox.height}px`);
    expect(viewPaddingBottom).toBeGreaterThanOrEqual(76);

    // Capture screenshot on Android emulation
    await page.screenshot({ path: path.join(evidenceDir, 'live-buyer-android-pixel7.png') });

    // Check Seller Studio on Android Mobile
    await page.goto(`${LIVE_BASE_URL}/seller.html`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#sellerLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-seller-android-pixel7.png') });

    // Check Admin Portal on Android Mobile
    await page.goto(`${LIVE_BASE_URL}/admin.html`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#adminLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-admin-android-pixel7.png') });

    await context.close();
  });

});
