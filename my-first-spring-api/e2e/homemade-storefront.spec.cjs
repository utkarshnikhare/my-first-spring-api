const { test, expect } = require('@playwright/test');

test.describe('Homemade Storefront & Public Deep Link Routing', () => {

  test('unauthenticated buyer can access /homemade and view homemade storefront via clean slug', async ({ page }) => {
    // 1. Directly visit /homemade unauthenticated
    const hmResponse = await page.goto('/homemade', { waitUntil: 'domcontentloaded' });
    expect(hmResponse.status()).toBe(200);

    // Verify SPA router loads the page
    await expect(page.locator('#view')).toBeVisible();

    // 2. Directly visit clean public storefront URL /homemade-store/north-zone-kitchen
    const storeResponse = await page.goto('/homemade-store/north-zone-kitchen', { waitUntil: 'domcontentloaded' });
    expect(storeResponse.status()).toBe(200);

    // Check that page renders without 401 Unauthorized or 400 Bad Request
    await expect(page.locator('#view')).toBeVisible();
    await page.waitForTimeout(1000);

    // Verify that page content is rendered (either kitchen hero or not-found empty state, but no server crash)
    const content = await page.locator('#view').innerHTML();
    expect(content).not.toContain('401 Unauthorized');
    expect(content).not.toContain('Invalid value for parameter: id');
  });

});
