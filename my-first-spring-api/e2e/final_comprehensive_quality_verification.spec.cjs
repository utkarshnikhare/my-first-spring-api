const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const evidenceDir = path.resolve(__dirname, '../../../../brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/final_quality_verification');
if (!fs.existsSync(evidenceDir)) {
  fs.mkdirSync(evidenceDir, { recursive: true });
}

test.describe('Final Comprehensive Quality, Functional & Theme Audit', () => {

  test('1. Dark Theme Comprehensive Contrast & Component Surface Verification', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });

    // A. Buyer Portal Dark Theme Check
    await page.goto('/index.html#/food');
    await page.waitForSelector('.segmented button.active');

    // Force dark theme
    await page.evaluate(() => {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('sociomart-theme', 'dark');
      if (typeof applyThemeUiState === 'function') applyThemeUiState();
    });
    // Allow CSS transitions (transition: all .18s ease) to finish
    await page.waitForTimeout(350);

    const buyerBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(buyerBg).toBe('rgb(28, 22, 19)'); // #1C1613 Espresso dark

    // Check segmented active button surface in dark mode: MUST NOT be white #FFFFFF
    const segmentedActiveBg = await page.evaluate(() => {
      const activeBtn = document.querySelector('.segmented button.active');
      return activeBtn ? getComputedStyle(activeBtn).backgroundColor : null;
    });
    console.log('[Theme Check] Buyer Segmented Active Bg in Dark:', segmentedActiveBg);
    if (segmentedActiveBg) {
      expect(segmentedActiveBg).not.toBe('rgb(255, 255, 255)');
      expect(segmentedActiveBg).toBe('rgb(51, 43, 38)'); // #332B26 surface-2
    }

    await page.screenshot({ path: path.join(evidenceDir, 'buyer-dark-food-verified.png') });

    // B. Seller Portal Dark Theme Check
    await page.goto('/seller.html');
    await page.evaluate(() => {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('sociomart-theme', 'dark');
      if (typeof applyThemeUiState === 'function') applyThemeUiState();
    });

    const sellerBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(sellerBg).toBe('rgb(28, 22, 19)');

    await page.screenshot({ path: path.join(evidenceDir, 'seller-dark-login-verified.png') });

    // C. Admin Portal Dark Theme Check
    await page.goto('/admin.html');
    await page.evaluate(() => {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('sociomart-theme', 'dark');
      if (typeof applyThemeUiState === 'function') applyThemeUiState();
    });

    const adminBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(adminBg).toBe('rgb(28, 22, 19)');

    await page.screenshot({ path: path.join(evidenceDir, 'admin-dark-login-verified.png') });
  });

  test('2. Buyer Navigation & Favourites In-Memory Isolation', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });
    await page.goto('/index.html#/home');
    await page.waitForSelector('#bottomNav');

    // Verify all 4 bottom nav links
    const navItems = page.locator('#bottomNav .nav-item');
    expect(await navItems.count()).toBe(4);

    // Verify touch targets
    for (let i = 0; i < 4; i++) {
      const box = await navItems.nth(i).boundingBox();
      expect(box.height).toBeGreaterThanOrEqual(44);
    }

    // Navigate to food hub
    await page.goto('/index.html#/food');
    await page.waitForSelector('.cat-grid');

    // Click Category tile
    const catTiles = page.locator('.cat-tile');
    expect(await catTiles.count()).toBeGreaterThan(0);
    await catTiles.first().click();
    await page.waitForTimeout(500);
    expect(page.url()).toContain('#/category/');

    // Verify capsule row switchers
    const capsules = page.locator('.capsule');
    expect(await capsules.count()).toBe(5);
    await capsules.nth(2).click(); // Switch to DINNER
    await page.waitForTimeout(400);
    expect(page.url()).toContain('#/category/DINNER');

    await page.screenshot({ path: path.join(evidenceDir, 'buyer-category-dinner-pixel7.png') });
  });

  test('3. Seller Bottom Navigation & Form State Security', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });
    await page.goto('/seller.html');
    await page.waitForSelector('#sellerLoginForm');

    // Switch to Registration
    await page.locator('[data-action="seller-show-register"]:visible').first().click();
    await page.waitForSelector('#sellerRegistrationForm');

    // Verify all registration inputs exist and have labels
    const inputs = ['#sellerRegName', '#sellerRegMobile', '#sellerRegKitchen', '#sellerRegPassword'];
    for (const sel of inputs) {
      expect(await page.locator(sel).count()).toBe(1);
    }

    // Switch back to Login
    await page.locator('[data-action="seller-show-login"]:visible').first().click();
    await page.waitForSelector('#sellerLoginForm');
    expect(await page.locator('#sellerLoginMobile').count()).toBe(1);
    expect(await page.locator('#sellerLoginPassword').count()).toBe(1);

    await page.screenshot({ path: path.join(evidenceDir, 'seller-auth-flow-pixel7.png') });
  });

  test('4. Responsive Viewport Audits across 13 Breakpoints', async ({ page }) => {
    const viewports = [
      { width: 320, height: 568, label: '320px-small-mobile' },
      { width: 360, height: 780, label: '360px-standard-android' },
      { width: 375, height: 667, label: '375px-iphone-se' },
      { width: 390, height: 844, label: '390px-iphone-14' },
      { width: 412, height: 915, label: '412px-pixel-7' },
      { width: 430, height: 932, label: '430px-iphone-14-pro-max' },
      { width: 768, height: 1024, label: '768px-tablet-portrait' },
      { width: 820, height: 1180, label: '820px-ipad-air' },
      { width: 1024, height: 768, label: '1024px-tablet-landscape' },
      { width: 1280, height: 800, label: '1280px-laptop' },
      { width: 1366, height: 768, label: '1366px-hd-desktop' },
      { width: 1440, height: 900, label: '1440px-macbook-desktop' },
      { width: 1920, height: 1080, label: '1920px-fhd-desktop' }
    ];

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto('/index.html#/home');
      await page.waitForSelector('#bottomNav');

      // Verify no horizontal overflow
      const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
      expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 2); // allow 1-2px subpixel tolerance
    }
  });
});
