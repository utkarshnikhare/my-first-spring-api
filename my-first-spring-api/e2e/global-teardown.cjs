const fs = require('node:fs');
const path = require('node:path');

const localAuthFile = path.join(__dirname, 'test-results', 'local-auth-secrets.json');

async function globalTeardown() {
  if (fs.existsSync(localAuthFile)) fs.unlinkSync(localAuthFile);
}

module.exports = globalTeardown;
