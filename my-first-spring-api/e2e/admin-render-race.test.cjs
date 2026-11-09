const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/admin.js'), 'utf8');

function deferred() {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}

function setup() {
  const view = { innerHTML: '' };
  const context = vm.createContext({
    document: { addEventListener() {} },
    window: { addEventListener() {}, scrollTo() {} },
    location: { hash: '#/dashboard' },
    history: { replaceState() {} },
    setTimeout() {},
  });
  vm.runInContext(script, context);
  context.A.role = 'ADMIN';
  context.api = async () => ({ authenticated: true, role: 'ADMIN' });
  context.viewEl = () => view;
  context.showAdminApp = () => {};
  context.esc = String;
  context.adminUpdateNav = () => {};
  context.adminRestoreFocus = () => {};
  context.renderLoginScreen = () => { view.innerHTML = 'LOGIN'; };
  return { context, view };
}

test('slow dashboard cannot replace the newer approvals view', async () => {
  const { context, view } = setup();
  const slow = deferred(), started = deferred();
  context.adminRoutes['#/dashboard'] = () => { started.resolve(); return slow.promise; };
  context.adminRoutes['#/approvals'] = async () => 'APPROVALS';
  const oldRender = context.adminRender();
  await started.promise;
  context.location.hash = '#/approvals';
  await context.adminRender();
  assert.equal(view.innerHTML, 'APPROVALS');
  slow.resolve('DASHBOARD');
  await oldRender;
  assert.equal(view.innerHTML, 'APPROVALS');
});

test('older same-route result and error cannot replace a newer result', async () => {
  for (const shouldReject of [false, true]) {
    const { context, view } = setup();
    const slow = deferred(), started = deferred();
    let calls = 0;
    context.adminRoutes['#/dashboard'] = () => {
      if (++calls === 1) { started.resolve(); return slow.promise; }
      return Promise.resolve('LATEST');
    };
    const oldRender = context.adminRender();
    await started.promise;
    await context.adminRender();
    if (shouldReject) slow.reject(new Error('old failure'));
    else slow.resolve('OLD');
    await oldRender;
    assert.equal(view.innerHTML, 'LATEST');
  }
});

test('stale identity failure cannot replace a newer authenticated view', async () => {
  const { context, view } = setup();
  const identity = deferred();
  let calls = 0;
  context.api = () => ++calls === 1 ? identity.promise : Promise.resolve({ authenticated: true, role: 'ADMIN' });
  context.adminRoutes['#/approvals'] = async () => 'APPROVALS';
  const oldRender = context.adminRender();
  context.location.hash = '#/approvals';
  await context.adminRender();
  identity.reject(new Error('old identity error'));
  await oldRender;
  assert.equal(view.innerHTML, 'APPROVALS');
  assert.equal(context.A.role, 'ADMIN');
});

test('navigation invalidates pending render even before hashchange is processed', async () => {
  const { context, view } = setup();
  const slow = deferred(), started = deferred();
  context.adminRoutes['#/dashboard'] = () => { started.resolve(); return slow.promise; };
  const oldRender = context.adminRender();
  await started.promise;
  context.location.hash = '#/approvals';
  slow.resolve('STALE');
  await oldRender;
  assert.notEqual(view.innerHTML, 'STALE');
});

test('current render still reports failures', async () => {
  const { context, view } = setup();
  context.esc = String;
  context.adminRoutes['#/dashboard'] = async () => { throw new Error('latest failure'); };
  await context.adminRender();
  assert.match(view.innerHTML, /latest failure/);
});
