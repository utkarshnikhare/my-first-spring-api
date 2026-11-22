const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const LIVE_FRONTEND_URL = 'https://sociomart-frontend.netlify.app';
const evidenceDir = path.resolve('C:/Users/Admin/.gemini/antigravity/brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/live_production');

test.describe('Live Netlify Production Audit', () => {

  test('1. Live Buyer Portal & Theme Toggle Verification', async ({ page }) => {
    // Desktop Viewport
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`${LIVE_FRONTEND_URL}/index.html#/home`);
    await page.waitForSelector('#bottomNav');

    // Screenshot Desktop
    await page.screenshot({ path: path.join(evidenceDir, 'live-buyer-home-desktop.png') });

    // Verify Theme Switcher works live
    const themeBtn = page.locator('[data-action="toggle-theme"]').first();
    const lightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await themeBtn.click();
    const darkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(darkBg).not.toBe(lightBg);
    console.log('Live Buyer Light Bg:', lightBg, 'Dark Bg:', darkBg);
    await themeBtn.click(); // Revert to light

    // Mobile Viewport (iPhone 14 - 390px)
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${LIVE_FRONTEND_URL}/index.html#/home`);
    await page.waitForSelector('#bottomNav');
    await page.screenshot({ path: path.join(evidenceDir, 'live-buyer-home-mobile.png') });
  });

  test('2. Live Seller Portal Verification', async ({ page }) => {
    // Desktop Viewport
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`${LIVE_FRONTEND_URL}/seller.html`);
    await page.waitForSelector('#sellerLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-seller-dashboard-desktop.png') });

    // Mobile Viewport
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${LIVE_FRONTEND_URL}/seller.html`);
    await page.waitForSelector('#sellerLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-seller-dashboard-mobile.png') });
  });

  test('3. Live Admin Portal Verification', async ({ page }) => {
    // Desktop Viewport
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto(`${LIVE_FRONTEND_URL}/admin.html`);
    await page.waitForSelector('#adminLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-admin-login-desktop.png') });

    // Mobile Viewport
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${LIVE_FRONTEND_URL}/admin.html`);
    await page.waitForSelector('#adminLoginForm');
    await page.screenshot({ path: path.join(evidenceDir, 'live-admin-login-mobile.png') });
  });

});
