const path = require('node:path');
const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { defineConfig, devices } = require('@playwright/test');

const backendDirectory = path.resolve(__dirname, '..');
const baseURL = 'http://localhost:18082';
const localPassword = role => `E2E${role}${createHash('sha256').update(`${backendDirectory}:${role}`).digest('hex')}`;
const adminPassword = localPassword('Admin');
const superAdminPassword = localPassword('SuperAdmin');
const localAuthFile = path.join(__dirname, 'test-results', 'local-auth-secrets.json');

fs.mkdirSync(path.dirname(localAuthFile), { recursive: true });
fs.writeFileSync(localAuthFile, JSON.stringify({ adminPassword, superAdminPassword }), 'utf8');

/**
 * Viewport definitions for responsive testing.
 * Each entry: [width, height, deviceName, isMobile]
 */
const VIEWPORTS = [
  [320, 568, 'iPhone SE 1st', true],
  [360, 640, 'Galaxy S-series', true],
  [375, 667, 'iPhone Mini', true],
  [390, 844, 'iPhone 12-15', true],
  [430, 932, 'iPhone Pro Max', true],
  [768, 1024, 'iPad Mini', true],
  [1024, 768, 'iPad Pro', true],
  [1366, 768, 'HD Laptop', false],
  [1920, 1080, 'Full HD', false],
];

/**
 * Browser projects to test: Chromium, Firefox, WebKit.
 * Each browser runs all viewport combinations.
 */
function createBrowserProjects(browserName, deviceBase) {
  return VIEWPORTS.map(([width, height, deviceName, isMobile]) => ({
    name: `${browserName}-${deviceName.replace(/\s+/g, '-')}`,
    testMatch: isMobile ? '**/*.mobile.spec.cjs' : '**/*.spec.cjs',
    testIgnore: isMobile ? undefined : '**/*.mobile.spec.cjs',
    use: {
      ...devices[deviceBase],
      viewport: { width, height },
      isMobile,
      hasTouch: isMobile,
    },
  }));
}

const isWindows = process.platform === 'win32';

// Note: Playwright's WebKit build on Windows drops session cookies over plain HTTP localhost.
// WebKit is enabled on Linux/macOS or when INCLUDE_WEBKIT=true is explicitly set.
const enableWebKit = !isWindows || process.env.INCLUDE_WEBKIT === 'true';

const projects = [
  ...createBrowserProjects('chromium', 'Desktop Chrome'),
  ...createBrowserProjects('firefox', 'Desktop Firefox'),
  ...(enableWebKit ? createBrowserProjects('webkit', 'Desktop Safari') : []),
];

module.exports = defineConfig({
  testDir: __dirname,
  testMatch: '**/*.spec.cjs',
  globalTeardown: require.resolve('./global-teardown.cjs'),
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  outputDir: path.join(__dirname, 'test-results'),
  reporter: [
    ['list'],
    ['html', { outputFolder: path.join(__dirname, 'playwright-report'), open: 'never' }],
    ['json', { outputFile: path.join(__dirname, 'playwright-results.json') }],
  ],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
  },
  projects,
  webServer: {
    command: process.platform === 'win32'
      ? 'mvnw.cmd -DskipTests -Dspring-boot.run.profiles=local spring-boot:run'
      : './mvnw -DskipTests -Dspring-boot.run.profiles=local spring-boot:run',
    cwd: backendDirectory,
    url: `${baseURL}/api/auth/config`,
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      SPRING_PROFILES_ACTIVE: 'local',
      PORT: '18082',
      SOCIOMART_RATELIMIT_ENABLED: 'false',
      SOCIOMART_DEMO_ADMIN_PASSWORD: adminPassword,
      SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD: superAdminPassword,
    },
  },
});