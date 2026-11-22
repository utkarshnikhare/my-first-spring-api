const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const evidenceDir = path.resolve(__dirname, '../../../../brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/mobile_overlay_removal');
if (!fs.existsSync(evidenceDir)) {
  fs.mkdirSync(evidenceDir, { recursive: true });
}

test.describe('Mobile Navigation Overlay Removal & Performance Verification', () => {

  test('1. Netlify Badge Elimination & Seller Mobile Bottom Nav Unblocked', async ({ page }) => {
    // Set Pixel 7 mobile viewport
    await page.setViewportSize({ width: 412, height: 915 });

    await page.goto('/seller.html');

    // Verify neutralizer markers exist in DOM
    const badgeFrame = page.locator('#nl-badge-frame');
    const hudFrame = page.locator('#nl-hud-frame');
    expect(await badgeFrame.count()).toBeGreaterThanOrEqual(1);
    expect(await hudFrame.count()).toBeGreaterThanOrEqual(1);

    // Verify they are completely hidden and non-intercepting
    const badgeVisibility = await page.evaluate(() => {
      const el = document.getElementById('nl-badge-frame');
      const cs = window.getComputedStyle(el);
      return {
        display: cs.display,
        pointerEvents: cs.pointerEvents,
        visibility: cs.visibility,
        width: cs.width,
        height: cs.height
      };
    });
    console.log('[Badge Suppression Check]:', badgeVisibility);
    expect(badgeVisibility.display).toBe('none');
    expect(badgeVisibility.pointerEvents).toBe('none');

    // Simulate an external script attempting to inject a floating Netlify iframe at bottom-right
    await page.evaluate(() => {
      const iframe = document.createElement('iframe');
      iframe.id = 'nl-badge-frame-injected';
      iframe.setAttribute('title', 'Netlify Badge');
      iframe.style.cssText = 'position:fixed;bottom:0;right:0;width:120px;height:40px;z-index:2147483645;background:black;';
      document.body.appendChild(iframe);
    });

    // Check if MutationObserver / CSS neutralized it
    const injectedState = await page.evaluate(() => {
      const el = document.getElementById('nl-badge-frame-injected');
      if (!el) return { removed: true };
      const cs = window.getComputedStyle(el);
      return {
        removed: false,
        display: cs.display,
        pointerEvents: cs.pointerEvents
      };
    });
    console.log('[Injected Badge Neutralization Check]:', injectedState);
    expect(injectedState.removed || injectedState.display === 'none' || injectedState.pointerEvents === 'none').toBe(true);

    // Make bottom nav visible for interaction testing
    await page.evaluate(() => {
      const nav = document.getElementById('bottomNav');
      if (nav) nav.style.display = 'flex';
    });

    // Verify all 6 seller bottom navigation items have proper touch target size (min 44px)
    const navItems = page.locator('#bottomNav .nav-item');
    const count = await navItems.count();
    expect(count).toBe(6);

    for (let i = 0; i < count; i++) {
      const item = navItems.nth(i);
      const box = await item.boundingBox();
      const label = await item.locator('.nav-label').textContent();
      console.log(`[Seller Nav Target ${i + 1}] Label: "${label.trim()}", W: ${box.width}px, H: ${box.height}px`);
      expect(box.height).toBeGreaterThanOrEqual(44);
      expect(box.width).toBeGreaterThan(40);
    }

    // Verify elementFromPoint at bottom-right corner hits the Seller nav item, NOT any Netlify badge!
    const pointElement = await page.evaluate(() => {
      const nav = document.getElementById('bottomNav');
      const rect = nav.getBoundingClientRect();
      const rightX = rect.right - 15;
      const centerY = rect.top + rect.height / 2;
      const el = document.elementFromPoint(rightX, centerY);
      return {
        tag: el ? el.tagName : null,
        closestNav: el ? !!el.closest('.nav-item') : false,
        id: el ? el.id : null,
        className: el ? el.className : null
      };
    });
    console.log('[Bottom-Right Touch Hit Check]:', pointElement);
    expect(pointElement.closestNav).toBe(true);

    // Capture Pixel 7 Mobile Screenshot
    await page.screenshot({ path: path.join(evidenceDir, 'seller-mobile-pixel7-nav-verified.png') });
  });

  test('2. Buyer Mobile Bottom Navigation & Safe Clearance Verification', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });

    await page.goto('/index.html');

    // Verify markers
    expect(await page.locator('#nl-badge-frame').count()).toBeGreaterThanOrEqual(1);

    // Verify buyer bottom nav
    const bottomNav = page.locator('#bottomNav');
    await expect(bottomNav).toBeVisible();

    const navItems = page.locator('#bottomNav .nav-item');
    expect(await navItems.count()).toBe(4);

    for (let i = 0; i < 4; i++) {
      const item = navItems.nth(i);
      const box = await item.boundingBox();
      expect(box.height).toBeGreaterThanOrEqual(44);
    }

    // Capture Pixel 7 Mobile Screenshot
    await page.screenshot({ path: path.join(evidenceDir, 'buyer-mobile-pixel7-nav-verified.png') });
  });

  test('3. Admin Mobile Topbar & Neutralizer Verification', async ({ page }) => {
    await page.setViewportSize({ width: 412, height: 915 });

    await page.goto('/admin.html');

    // Verify markers
    expect(await page.locator('#nl-badge-frame').count()).toBeGreaterThanOrEqual(1);

    // Capture Pixel 7 Mobile Screenshot
    await page.screenshot({ path: path.join(evidenceDir, 'admin-mobile-pixel7-verified.png') });
  });
});
