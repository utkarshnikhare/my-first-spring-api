/**
 * Exercises the REAL static/js/config.js under a mocked browser environment and
 * reports what API base URL it resolves to, plus the URL common.js would build.
 *
 * This deliberately does not re-implement the logic: it loads the shipped file
 * into a vm context with a synthetic `window`/`location`, so the assertions in
 * SellerApiBaseConfigTest exercise the code that actually ships.
 *
 * Usage: node config-base-probe.js <path-to-config.js> <path-to-json-cases-file>
 * Each case: { label, hostname, port, hasOverride: bool, override?: value }
 *
 * Cases are passed in a FILE rather than on argv: Windows argument handling
 * strips the double quotes out of a JSON blob, which corrupts it.
 *
 * Emits one TAB-separated line per case, so the test needs no JSON parser:
 *   label \t ok|error \t base \t joinedUrl \t errName \t errMessage
 */
const fs = require('fs');
const vm = require('vm');

const configPath = process.argv[2];
const cases = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));

const cell = s => String(s == null ? '' : s).replace(/[\t\r\n]/g, ' ').trim();

for (const c of cases) {
  const windowMock = { location: { hostname: c.hostname, port: String(c.port) } };
  if (c.hasOverride) windowMock.SOCIO_API_BASE_URL = c.override;

  const ctx = { window: windowMock, URL: URL, TypeError: TypeError, SyntaxError: SyntaxError };
  ctx.globalThis = ctx;
  vm.createContext(ctx);

  let status = 'ok', base = '', joined = '', errName = '', errMsg = '';
  try {
    vm.runInContext(fs.readFileSync(configPath, 'utf8'), ctx, { filename: 'config.js' });
    // Read the module-level const exactly as common.js sees it.
    base = vm.runInContext('CONFIG.API_BASE_URL', ctx);
    // Reproduce exactly how common.js builds an API URL.
    joined = base + '/api/seller-app/history';
  } catch (e) {
    status = 'error';
    errName = e.name;
    errMsg = e.message;
  }
  process.stdout.write([c.label, status, base, joined, errName, errMsg].map(cell).join('\t') + '\n');
}
