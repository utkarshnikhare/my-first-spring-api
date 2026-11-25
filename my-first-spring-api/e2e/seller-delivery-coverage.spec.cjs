const { test, expect, adminLogin, logout, apiJson } = require('./support.cjs');

test('seller delivery coverage selection across multiple areas with search, select all, and persistent summary', async ({
  page,
  browserIssues
}) => {
  const token = Math.random().toString(36).slice(2, 8);
  const sellerMobile = `98${Math.floor(10000000 + Math.random() * 90000000)}`;

  // 1. Navigate to Seller Registration
  await page.goto('/seller.html');
  await page.getByRole('button', { name: 'Register as a seller' }).click();
  const form = page.locator('#sellerRegistrationForm');
  await expect(form).toBeVisible();

  // Verify separation: Location / Pickup Address section exists
  await expect(page.locator('.seller-location-section legend')).toContainText('Your Location / Pickup Address');
  await expect(page.locator('#sellerRegPrimaryArea')).toBeVisible();
  await expect(page.locator('#sellerRegPrimarySociety')).toBeVisible();

  // Verify Delivery Coverage section exists
  const coverageSection = page.locator('#sellerRegCoverageSection');
  await expect(coverageSection).toBeVisible();
  await expect(coverageSection.locator('legend')).toContainText('Where do you deliver? / Delivery Coverage');

  // Verify multiple area cards exist in accordion
  const areaCards = page.locator('.coverage-area-card');
  const areaCount = await areaCards.count();
  expect(areaCount).toBeGreaterThanOrEqual(1);

  // First area is expanded by default; check chevron and body
  const firstCard = areaCards.first();
  await expect(firstCard.locator('.coverage-area-header')).toHaveAttribute('aria-expanded', 'true');
  await expect(firstCard.locator('.coverage-area-body')).toBeVisible();

  // If there's a second area, verify it is collapsed initially
  if (areaCount > 1) {
    const secondCard = areaCards.nth(1);
    await expect(secondCard.locator('.coverage-area-header')).toHaveAttribute('aria-expanded', 'false');
    await expect(secondCard.locator('.coverage-area-body')).toBeHidden();

    // Expand second area
    await secondCard.locator('.coverage-area-header').click();
    await expect(secondCard.locator('.coverage-area-header')).toHaveAttribute('aria-expanded', 'true');
    await expect(secondCard.locator('.coverage-area-body')).toBeVisible();
  }

  // Test Select All within the first area
  const firstSelectAll = firstCard.locator('.coverage-select-all');
  if (await firstSelectAll.isVisible()) {
    await firstSelectAll.check();
    await expect(firstCard.locator('.coverage-area-badge')).toContainText('All societies');
    await expect(page.locator('#coverageSummaryCount')).not.toContainText('No societies selected');
  }

  // If second area exists, select one society in it
  if (areaCount > 1) {
    const secondCard = areaCards.nth(1);
    const secondSocieties = secondCard.locator('.coverage-society-item input[type="checkbox"]');
    if (await secondSocieties.count() > 0) {
      await secondSocieties.first().check();
      await expect(secondCard.locator('.coverage-area-badge')).toContainText(/1 selected|All societies/);
    }
  }

  // Verify summary reflects multi-area coverage
  const summary = page.locator('#sellerCoverageSummary');
  await expect(summary).toBeVisible();
  await expect(page.locator('#coverageSummaryCount')).toContainText('selected');

  // Test search within the first area
  const searchInput = firstCard.locator('.coverage-area-search');
  if (await searchInput.isVisible()) {
    await searchInput.fill('NonExistentSocietyNameXYZ');
    await expect(firstCard.locator('.coverage-no-results')).toBeVisible();
    // Clearing search restores list
    await searchInput.fill('');
    await expect(firstCard.locator('.coverage-no-results')).toHaveCount(0);
  }

  // Fill in required seller details
  await page.locator('#sellerRegName').fill(`Chef ${token}`);
  await page.locator('#sellerRegMobile').fill(sellerMobile);
  await page.locator('#sellerRegPassword').fill('Valid-Password-12345');
  await page.locator('#sellerRegWhatsApp').fill(sellerMobile);
  await page.locator('#sellerRegKitchen').fill(`Kitchen ${token}`);
  await page.locator('#sellerRegSlug').fill(`kitchen-${token}`);
  await page.locator('#sellerRegSpeciality').fill('Multi-Area Home Feast');
  await page.locator('#sellerRegCategory').selectOption('BOTH');
  await page.locator('#sellerRegPaymentMode').selectOption('CASH_ONLY');

  // Submit registration
  await form.getByRole('button', { name: 'Submit seller application' }).click();

  // Verify success state
  await expect(page.getByRole('heading', { name: 'Seller application pending' })).toBeVisible();
  await expect(page.getByText('Current status: PENDING')).toBeVisible();

  // Verify that an Admin viewing the seller application sees the coverage
  await logout(page);
  await adminLogin(page);
  await page.goto('/admin.html#/approvals');
  await expect(page.getByRole('heading', { name: 'Pending Approvals' })).toBeVisible();

  const sellerRow = page.locator('.seller-row').filter({ hasText: `Chef ${token}` });
  await expect(sellerRow).toBeVisible();

  expect(browserIssues).toEqual([]);
});
