const { test, expect } = require('@playwright/test');

test('Deliberate failure to verify CI pipeline blocks broken code', async () => {
  expect(1 + 1, 'Deliberate test failure for CI pipeline verification').toBe(42);
});
