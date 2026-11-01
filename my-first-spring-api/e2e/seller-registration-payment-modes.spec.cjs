const {
  test,
  expect,
  registerSeller
} = require('./support.cjs');

async function openRegistration(page) {
  await page.goto('/seller.html');
  await page.getByRole('button', { name: 'Register as a seller' }).click();
  await expect(page.locator('#sellerRegistrationForm')).toBeVisible();
}

test('seller registration shows all payment configurations and requires only their own identifiers', async ({
  page,
  browserIssues
}) => {
  await openRegistration(page);
  const mode = page.locator('#sellerRegPaymentMode');
  const upiGroup = page.locator('#sellerRegUpiGroup');
  const gpayGroup = page.locator('#sellerRegGpayGroup');

  await mode.selectOption('GPAY');
  await expect(gpayGroup).toBeVisible();
  await expect(upiGroup).toBeHidden();
  await expect(page.locator('#sellerRegGpayType')).toHaveAttribute('required', '');
  await expect(page.locator('#sellerRegGpayIdentifier')).toHaveAttribute('required', '');
  await expect(page.locator('#sellerRegUpiId')).toBeDisabled();
  await expect(page.locator('#sellerRegGpayType option')).toHaveText([
    'Choose a type',
    'GPay-linked mobile number',
    'GPay UPI ID'
  ]);
  await expect(page.locator('#sellerRegGpayGroup')).toContainText(
    'a phone number is not assumed to be a UPI ID'
  );

  await mode.selectOption('UPI');
  await expect(upiGroup).toBeVisible();
  await expect(gpayGroup).toBeHidden();
  await expect(page.locator('#sellerRegUpiId')).toHaveAttribute('required', '');
  await expect(page.locator('#sellerRegGpayIdentifier')).toBeDisabled();

  await mode.selectOption('GPAY_AND_UPI');
  await expect(upiGroup).toBeVisible();
  await expect(gpayGroup).toBeVisible();
  await expect(page.locator('#sellerRegUpiId')).toHaveAttribute('required', '');
  await expect(page.locator('#sellerRegGpayIdentifier')).toHaveAttribute('required', '');

  await mode.selectOption('CASH_ONLY');
  await expect(upiGroup).toBeHidden();
  await expect(gpayGroup).toBeHidden();
  await expect(page.locator('#sellerRegUpiId')).toBeDisabled();
  await expect(page.locator('#sellerRegGpayType')).toBeDisabled();
  await expect(page.locator('#sellerRegGpayIdentifier')).toBeDisabled();

  let cashRegistration;
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/auth/register/seller'
        && request.method() === 'POST') {
      cashRegistration = request.postDataJSON();
    }
  });
  const seller = await registerSeller(page, { paymentMode: 'CASH_ONLY' });
  expect(seller.mobile).toMatch(/^\d{10}$/);
  expect(cashRegistration).toMatchObject({
    acceptedPaymentMode: 'CASH_ONLY',
    upiId: null,
    gpayIdentifierType: null,
    gpayIdentifier: null
  });
  expect(browserIssues).toEqual([]);
});
