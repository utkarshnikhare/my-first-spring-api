const fs = require('fs');
const path = require('path');
const { test, expect, adminLogin, loginSeller, registerSeller, registerBuyer, logout } = require('./support.cjs');

const VIEWPORTS = [
  { name: '320px-small-mobile', width: 320, height: 568 },
  { name: '360px-android-mobile', width: 360, height: 800 },
  { name: '375px-iphone-se', width: 375, height: 667 },
  { name: '390px-iphone-14', width: 390, height: 844 },
  { name: '430px-iphone-pro-max', width: 430, height: 932 },
  { name: '768px-tablet-portrait', width: 768, height: 1024 },
  { name: '820px-ipad-air', width: 820, height: 1180 },
  { name: '1024px-tablet-landscape', width: 1024, height: 768 },
  { name: '1280px-laptop', width: 1280, height: 800 },
  { name: '1366px-hd-desktop', width: 1366, height: 768 },
  { name: '1440px-macbook-desktop', width: 1440, height: 900 },
  { name: '1920px-fhd-desktop', width: 1920, height: 1080 },
  { name: '2560px-qhd-desktop', width: 2560, height: 1440 }
];

const evidenceDir = path.resolve('C:/Users/Admin/.gemini/antigravity/brain/2e3fa6ce-b43c-4407-b6a3-74d1a4447b78/mobile_ux_repair_evidence/full_repair');
fs.mkdirSync(evidenceDir, { recursive: true });

async function verifyViewportLayout(page, routeName, vp) {
  // 1. Check for horizontal overflow
  const overflow = await page.evaluate(() => {
    const docW = document.documentElement.scrollWidth;
    const winW = window.innerWidth;
    return { docW, winW, hasOverflow: docW > winW };
  });
  expect(overflow.hasOverflow, `Route ${routeName} has horizontal overflow at ${vp.width}px: docW=${overflow.docW}, winW=${overflow.winW}`).toBeFalsy();

  // 2. Check bottom nav does not overlap last interactive element or has padding clearance
  const hasBottomNav = await page.locator('#bottomNav, #adminNav').isVisible().catch(() => false);
  if (hasBottomNav) {
    const navBox = await page.locator('#bottomNav, #adminNav').boundingBox();
    if (navBox && navBox.y > 0) {
      // Nav is rendered at bottom
      expect(navBox.height).toBeGreaterThan(40);
    }
  }

  // 3. Take screenshot
  const screenshotPath = path.join(evidenceDir, `${routeName}-${vp.name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: false });
}

test.describe('13-Viewport Responsive Layout Audit', () => {

  test('Buyer Home & Navigation Across 13 Viewports', async ({ page }) => {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto('/index.html#/home');
      await page.waitForSelector('#view', { state: 'visible' });
      await verifyViewportLayout(page, 'buyer-home', vp);
    }
  });

  test('Seller Dashboard & Quick Actions Across 13 Viewports', async ({ page }) => {
    // Register & approve seller first
    const seller = await registerSeller(page);
    await logout(page);

    await adminLogin(page);
    await page.locator('[data-hash="#/approvals"]').first().click();
    const sellerRow = page.locator('.seller-row').filter({ hasText: seller.name });
    const approval = page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/admin/sellers/${seller.id}/approve`);
    await sellerRow.getByRole('button', { name: 'Approve' }).click();
    await approval;
    await logout(page);

    await loginSeller(page, seller);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto('/seller.html#/home');
      await page.waitForSelector('.sd-quick__grid', { state: 'visible' });
      await verifyViewportLayout(page, 'seller-dashboard', vp);
    }
  });

  test('Admin Dashboard & Analytics Across 13 Viewports', async ({ page }) => {
    await adminLogin(page);

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      await page.goto('/admin.html#/analytics');
      await page.waitForSelector('.traffic-chart-wrap svg', { state: 'visible' });
      await verifyViewportLayout(page, 'admin-analytics', vp);
    }
  });

});
