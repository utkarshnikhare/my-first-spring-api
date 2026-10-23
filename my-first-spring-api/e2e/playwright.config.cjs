const path = require('node:path');
const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { defineConfig, devices } = require('@playwright/test');

const backendDirectory = path.resolve(__dirname, '..');
const baseURL = 'http://127.0.0.1:18082';
const localPassword = role => `E2E${role}${createHash('sha256').update(`${backendDirectory}:${role}`).digest('hex')}`;
const adminPassword = localPassword('Admin');
const superAdminPassword = localPassword('SuperAdmin');
const localAuthFile = path.join(__dirname, 'test-results', 'local-auth-secrets.json');

fs.mkdirSync(path.dirname(localAuthFile), { recursive: true });
fs.writeFileSync(localAuthFile, JSON.stringify({ adminPassword, superAdminPassword }), 'utf8');

module.exports = defineConfig({
  testDir: __dirname,
  testMatch: '**/*.spec.cjs',
  globalTeardown: require.resolve('./global-teardown.cjs'),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  outputDir: path.join(__dirname, 'test-results'),
  reporter: [
    ['list'],
    ['html', { outputFolder: path.join(__dirname, 'playwright-report'), open: 'never' }]
  ],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  projects: [
    {
      name: 'desktop',
      testIgnore: '**/*.mobile.spec.cjs',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 1000 }
      }
    },
    {
      name: 'mobile',
      testMatch: '**/*.mobile.spec.cjs',
      use: { ...devices['Pixel 7'] }
    }
  ],
  webServer: {
    command: process.platform === 'win32'
      ? 'mvnw.cmd -DskipTests -Dspring-boot.run.profiles=demo spring-boot:run'
      : './mvnw -DskipTests -Dspring-boot.run.profiles=demo spring-boot:run',
    cwd: backendDirectory,
    url: `${baseURL}/api/auth/config`,
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      SPRING_PROFILES_ACTIVE: 'demo',
      PORT: '18082',
      SOCIOMART_DEMO_ADMIN_PASSWORD: adminPassword,
      SOCIOMART_DEMO_SUPER_ADMIN_PASSWORD: superAdminPassword
    }
  }
});
