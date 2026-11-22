const { test, expect, registerBuyer, registerSeller, loginSeller, logout, adminLogin } = require('./support.cjs');

async function registerAndApproveSeller(page) {
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
  return seller;
}

test.describe('Deep Mobile & Theme Audit', () => {

  test('1. Buyer Theme Toggle & Appearance Verification', async ({ page }) => {
    const consoleErrors = [];
    page.on('pageerror', err => consoleErrors.push(err.message));
    page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

    await page.goto('/index.html#/home');
    await page.waitForSelector('#bottomNav');

    // Initial Light Theme check
    const lightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const lightText = await page.evaluate(() => getComputedStyle(document.body).color);
    const initialTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light');
    console.log('Buyer Initial Theme:', initialTheme, 'Bg:', lightBg, 'Text:', lightText);

    // Click Theme Toggle
    const themeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    await themeBtn.click();

    // Check after Toggle
    const toggledTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    const toggledBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const toggledText = await page.evaluate(() => getComputedStyle(document.body).color);
    const storedTheme = await page.evaluate(() => localStorage.getItem('sociomart-theme'));

    console.log('Buyer Toggled Theme:', toggledTheme, 'Bg:', toggledBg, 'Text:', toggledText, 'Stored:', storedTheme);

    // Verify Theme actually changes visually (Background & Text MUST differ)
    expect(toggledTheme).toBe('dark');
    expect(storedTheme).toBe('dark');
    expect(toggledBg).not.toBe(lightBg);
    expect(toggledText).not.toBe(lightText);

    // Reload page and verify persistence
    await page.reload();
    const persistedTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    expect(persistedTheme).toBe('dark');

    // Click Toggle again to return to light
    await page.locator('[data-action="toggle-theme"]:visible').first().click();
    const restoredTheme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
    expect(restoredTheme).toBe('light');

    expect(consoleErrors).toHaveLength(0);
  });

  test('2. Seller Studio Theme Toggle & Header Verification', async ({ page }) => {
    const consoleErrors = [];
    page.on('pageerror', err => consoleErrors.push(err.message));

    // First test unauthenticated login screen theme toggle
    await page.goto('/seller.html');
    await page.waitForSelector('#sellerLoginForm');
    const authThemeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    expect(await authThemeBtn.count()).toBeGreaterThan(0);
    const lightAuthBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await authThemeBtn.click();
    const darkAuthBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(darkAuthBg).not.toBe(lightAuthBg);
    await authThemeBtn.click(); // revert

    // Now register and approve seller for authenticated dashboard verification
    const seller = await registerAndApproveSeller(page);

    await page.goto('/seller.html#/home');
    await page.waitForSelector('.sd-header [data-action="toggle-theme"]');
    
    // Check if Seller authenticated header has a theme toggle button
    const sellerThemeBtn = page.locator('.sd-header [data-action="toggle-theme"]').first();
    const exists = await sellerThemeBtn.count() > 0;
    console.log('Seller header theme toggle button exists:', exists);
    expect(exists).toBeTruthy();

    const lightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await sellerThemeBtn.click();
    const toggledBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    console.log('Seller Light Bg:', lightBg, 'Toggled Bg:', toggledBg);
    expect(toggledBg).not.toBe(lightBg);

    // Toggle back
    await sellerThemeBtn.click();
    expect(consoleErrors).toHaveLength(0);
  });

  test('3. Admin Theme Toggle & Enter Key Login', async ({ page }) => {
    const consoleErrors = [];
    page.on('pageerror', err => consoleErrors.push(err.message));

    await page.goto('/admin.html');
    await page.waitForSelector('#adminLoginForm');

    // Theme toggle on admin login screen
    const adminThemeBtn = page.locator('[data-action="toggle-theme"]:visible').first();
    const lightBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    await adminThemeBtn.click();
    const toggledBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    console.log('Admin Light Bg:', lightBg, 'Toggled Bg:', toggledBg);
    expect(toggledBg).not.toBe(lightBg);

    // Toggle back
    await adminThemeBtn.click();

    // Now log in as admin and verify topbar theme toggle
    await adminLogin(page);
    await page.waitForSelector('#adminTopbar:not(.hidden)');
    const topbarThemeBtn = page.locator('#adminTopbar [data-action="toggle-theme"]');
    expect(await topbarThemeBtn.count()).toBeGreaterThan(0);
    await topbarThemeBtn.click();
    const loggedInDarkBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(loggedInDarkBg).not.toBe(lightBg);
    await topbarThemeBtn.click(); // revert to light

    expect(consoleErrors).toHaveLength(0);
  });

  test('4. Mobile Viewport Fixed Nav Clearance (360px & 390px)', async ({ page }) => {
    await registerAndApproveSeller(page);

    for (const width of [360, 390]) {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/seller.html#/home');
      await page.waitForSelector('.sd-quick');

      // Verify Seller Bottom Navigation exists
      const bottomNav = page.locator('.bottom-nav');
      const bottomNavBox = await bottomNav.boundingBox();
      expect(bottomNavBox).not.toBeNull();
      expect(bottomNavBox.height).toBeGreaterThan(48);

      const quickCards = await page.locator('.sd-quick .sd-qa').all();
      expect(quickCards.length).toBe(5);

      // Scroll each shortcut into view and check visibility
      for (const card of quickCards) {
        await card.scrollIntoViewIfNeeded();
        const cardBox = await card.boundingBox();
        expect(cardBox).not.toBeNull();
        expect(cardBox.width).toBeGreaterThan(30);
        expect(cardBox.height).toBeGreaterThan(30);
      }

      // Check view padding-bottom clearance
      const paddingBottom = await page.evaluate(() => {
        const view = document.querySelector('.view') || document.querySelector('.sd-root');
        return parseInt(window.getComputedStyle(view).paddingBottom, 10);
      });
      console.log(`Viewport ${width}px view paddingBottom: ${paddingBottom}px`);
      expect(paddingBottom).toBeGreaterThanOrEqual(76);

      // When scrolled to the very bottom, verify the last content element clears the bottom nav
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      const lastElementBox = await page.evaluate(() => {
        const root = document.querySelector('.sd-root');
        const lastChild = root.lastElementChild;
        const rect = lastChild.getBoundingClientRect();
        return { bottom: rect.bottom, windowHeight: window.innerHeight };
      });
      console.log(`Viewport ${width}px last element bottom: ${lastElementBox.bottom}, windowHeight: ${lastElementBox.windowHeight}, navTop: ${bottomNavBox.y}`);
      expect(lastElementBox.bottom).toBeLessThanOrEqual(bottomNavBox.y + 10);
    }
  });

});
