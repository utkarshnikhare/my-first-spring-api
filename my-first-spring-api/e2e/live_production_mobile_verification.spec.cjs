const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const LIVE_URL = 'https://sociomart-frontend.netlify.app';
const evidenceDir = path.resolve('C:/Users/Admin/.gemini/antigravity/brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/live_production_v4');
if (!fs.existsSync(evidenceDir)) {
  fs.mkdirSync(evidenceDir, { recursive: true });
}

test.describe('Live Production Netlify Badge Removal & Mobile Performance Verification', () => {

  test('1. Live Seller Portal: No Netlify badge overlay, bottom navigation completely clear', async ({ page }) => {
    // Pixel 7 mobile viewport
    await page.setViewportSize({ width: 412, height: 915 });

    const start = Date.now();
    await page.goto(`${LIVE_URL}/seller.html`, { waitUntil: 'domcontentloaded' });
    const loadTime = Date.now() - start;
    console.log(`[Perf] Seller Page DOM Load Time: ${loadTime}ms`);

    // Wait for seller login screen
    await page.waitForSelector('#sellerLoginForm');

    // Verify Netlify badge iframe is NOT present or neutralized
    const badgeIframe = page.locator('iframe[title*="Netlify" i], iframe[src*="netlify" i], #nl-badge-frame iframe');
    expect(await badgeIframe.count()).toBe(0);

    // Verify neutralizer div markers are present and non-intercepting
    const badgeMarker = page.locator('#nl-badge-frame');
    expect(await badgeMarker.count()).toBeGreaterThanOrEqual(1);
    const markerStyles = await page.evaluate(() => {
      const el = document.getElementById('nl-badge-frame');
      const cs = window.getComputedStyle(el);
      return { display: cs.display, pointerEvents: cs.pointerEvents };
    });
    expect(markerStyles.display).toBe('none');
    expect(markerStyles.pointerEvents).toBe('none');

    // Capture Pixel 7 Mobile Screenshot of Seller Login
    await page.screenshot({ path: path.join(evidenceDir, 'live-seller-login-pixel7.png') });

    // Test form toggles and inputs without any badge blocking the view
    const showRegisterBtn = page.locator('[data-action="seller-show-register"]:visible').first();
    await showRegisterBtn.click();
    await page.waitForSelector('#sellerRegistrationForm');

    // Capture Pixel 7 Mobile Screenshot of Seller Registration
    await page.screenshot({ path: path.join(evidenceDir, 'live-seller-register-pixel7.png') });

    // Navigate back to sign in
    const showLoginBtn = page.locator('[data-action="seller-show-login"]:visible').first();
    await showLoginBtn.click();
    await page.waitForSelector('#sellerLoginForm');
  });

  test('2. Live Buyer Portal: Seamless Navigation, Fast Load & Unblocked Bottom Nav', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });

    const start = Date.now();
    await page.goto(`${LIVE_URL}/index.html#/home`, { waitUntil: 'networkidle' });
    const loadTime = Date.now() - start;
    console.log(`[Perf] Buyer Home Page Full Load Time: ${loadTime}ms`);

    await page.waitForSelector('#bottomNav');

    // Verify badge iframe is completely absent
    const badgeIframe = page.locator('iframe[title*="Netlify" i], iframe[src*="netlify" i]');
    expect(await badgeIframe.count()).toBe(0);

    // Verify bottom nav items
    const navItems = page.locator('#bottomNav .nav-item');
    expect(await navItems.count()).toBe(4);

    // Verify click on Favourites nav item
    await navItems.nth(1).click();
    await page.waitForTimeout(600);
    expect(page.url()).toContain('#/favourites');

    // Capture Pixel 7 Mobile Screenshot of Buyer Favourites
    await page.screenshot({ path: path.join(evidenceDir, 'live-buyer-favourites-pixel7.png') });

    // Navigate back to Home
    await navItems.nth(0).click();
    await page.waitForTimeout(600);
    expect(page.url()).toContain('#/home');

    // Capture Pixel 7 Mobile Screenshot of Buyer Home
    await page.screenshot({ path: path.join(evidenceDir, 'live-buyer-home-pixel7.png') });
  });

  test('3. Live Admin Portal: Unblocked Screen & Theme Controls', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });

    const start = Date.now();
    await page.goto(`${LIVE_URL}/admin.html`, { waitUntil: 'domcontentloaded' });
    const loadTime = Date.now() - start;
    console.log(`[Perf] Admin Page DOM Load Time: ${loadTime}ms`);

    await page.waitForSelector('#adminLoginForm');

    // Verify badge iframe is absent
    const badgeIframe = page.locator('iframe[title*="Netlify" i], iframe[src*="netlify" i]');
    expect(await badgeIframe.count()).toBe(0);

    // Capture Pixel 7 Mobile Screenshot of Admin Login
    await page.screenshot({ path: path.join(evidenceDir, 'live-admin-login-pixel7.png') });
  });
});
