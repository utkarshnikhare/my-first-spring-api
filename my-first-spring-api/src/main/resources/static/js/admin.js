/**
 * SocioMart Admin App v1.0 — Shell + OTP role gate (Increment A).
 * Screens B (Pending Approvals(, C (Seller Registry(, D (Analytics(, E (Super Console(
 * arrive in subsequent increments; placeholders render until then.
 */
var A = { me: null, role: null, otpMobile: null };
var adminRoutes = {
    '#/home': adminHomeView,
    '#/pending': adminPendingView,
    '#/sellers': adminSellersView,
    '#/analytics': adminAnalyticsView,
    '#/console': adminConsoleView
};
function adminResolveRoute(hash) {
    if (adminRoutes[hash]) return { fn: adminRoutes[hash], arg: hash };
    return { fn: adminHomeView, arg: '#/home' };
}
async function adminRender() {
    if (!A.role) { await adminGate(); return; }
    var hash = location.hash || '#/home';
    var route = adminResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    try {
        view.innerHTML = await route.fn(route.arg) || '';
        adminUpdateNav(hash);
        window.scrollTo(0, 0);
    } catch (err) {
        view.innerHTML = '<div class="view-enter"><div class="section-head admin-section-head"><div><h1>Error</h1><p class="muted small">' + esc(err.message) + '</p></div></div></div>';
    }
}
function adminUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash.replace(/^#\//, '').split('/')[0];
    var el = document.querySelector('[data-nav="' + key + '"]');
    if (el) el.classList.add('active');
    var consoleTab = document.querySelector('[data-nav="console"]');
    if (consoleTab) consoleTab.style.display = (A.role === 'SUPER_ADMIN') ? '' : 'none';
}
function adminNavigate(hash) { if (location.hash === hash) adminRender(); else location.hash = hash; }
function greeting() { var h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
// ==================== AUTH GATE ====================
async function adminGate() {
    var me = null;
    try { me = await api('/api/auth/me'); } catch (e) { me = null; }
    if (me && me.authenticated && (me.role === 'ADMIN' || me.role === 'SUPER_ADMIN')) {
        A.me = me; A.role = me.role;
        showAdminApp();
        await adminRender();
    } else if (me && me.authenticated) {
        renderBlockedScreen(me.role || 'UNKNOWN');
    } else {
        renderLoginScreen();
    }
}
function showAdminApp() {
    var top = $('#adminTopbar'), nav = $('#adminNav');
    if (top) top.classList.remove('hidden');
    if (nav) nav.classList.remove('hidden');
    document.body.classList.add('admin-authed');
    adminUpdateNav(location.hash || '#/home');
    var badge = $('#adminRoleBadge'), nm = $('#adminName');
    if (badge) { badge.textContent = A.role; badge.classList.toggle('super', A.role === 'SUPER_ADMIN'); }
    if (nm) nm.textContent = A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin';
}
function renderLoginScreen() {
    viewEl().innerHTML =
        '<div class="login-wrap"><div class="admin-login">' +
        '<div class="al-brand">🛡️ SocioMart Admin</div>' +
        '<h2>Admin Sign In</h2>' +
        '<p class="muted small">Enter your mobile to receive a one-time password. This console accepts ADMIN &amp; SUPER_ADMIN accounts.</p>' +
        '<div class="form-group"><label for="alMobile">Mobile number</label><input id="alMobile" inputmode="numeric" maxlength="10" placeholder="10-digit mobile" autocomplete="tel"></div>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="otp-request">Get OTP</button>' +
        '<a class="al-back" href="/index.html">← Back to buyer app</a>' +
        '</div></div>';
}
function renderBlockedScreen(role) {
    viewEl().innerHTML =
        '<div class="blocked-wrap"><div class="admin-blocked">' +
        '<div class="ab-icon">🚫</div>' +
        '<h2>Not an Admin account</h2>' +
        '<p>You are signed in as <strong>' + esc(role) + '</strong>. The Admin console is restricted to ADMIN and SUPER_ADMIN accounts.</p>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="open-buyer">Open Buyer App</button>' +
        '<button class="btn btn-outline btn-block" type="button" data-action="logout">Logout</button>' +
        '</div></div>';
}
// ==================== ACTIONS ====================
async function adminAction(action, t) {
    try {
        switch (action) {
            case 'otp-request':
            case 'otp-resend': {
                var mobile = A.otpMobile || ($('#alMobile') ? $('#alMobile').value : '');
                if (!mobile || !/^[6-9]\d{9}$/.test(mobile)) throw new Error('Enter a valid 10-digit mobile number');
                A.otpMobile = mobile;
                var res = await api('/api/auth/otp/request', { method: 'POST', body: { mobileNumber: mobile } });
                toast('OTP sent to ' + mobile, 'success');
                viewEl().innerHTML =
                    '<div class="login-wrap"><div class="admin-login">' +
                    '<div class="al-brand">🛡️ SocioMart Admin</div>' +
                    '<h2>Enter OTP</h2>' +
                    '<div class="al-info">OTP sent to <strong>' + esc(mobile) + '</strong>.' + (res && res.otp ? ' Dev preview: <strong>' + esc(res.otp) + '</strong>' : '') + '</div>' +
                    '<div class="form-group"><label for="alOtp">4-digit OTP</label><input id="alOtp" inputmode="numeric" maxlength="4" placeholder="••••" autocomplete="one-time-code"></div>' +
                    '<button class="btn btn-primary btn-block" type="button" data-action="otp-verify">Verify &amp; Enter</button>' +
                    '<button class="btn btn-outline btn-block" type="button" data-action="otp-resend">Resend OTP</button>' +
                    '<a class="al-back" href="javascript:void(0)" data-action="otp-back">← Change mobile</a>' +
                    '</div></div>';
                break;
            }
            case 'otp-back': renderLoginScreen(); break;
            case 'otp-verify': {
                var otp = $('#alOtp') ? $('#alOtp').value : '';
                if (!/^\d{4}$/.test(otp)) throw new Error('Enter the 4-digit OTP');
                var resp = await api('/api/auth/otp/verify', { method: 'POST', body: { mobileNumber: A.otpMobile, otpCode: otp } });
                if (resp && resp.authenticated && (resp.role === 'ADMIN' || resp.role === 'SUPER_ADMIN')) {
                    A.me = resp; A.role = resp.role;
                    showAdminApp();
                    toast('Welcome, ' + (resp.name || 'Admin') + '!', 'success');
                    await adminRender();
                } else {
                    renderBlockedScreen(resp ? resp.role : 'UNKNOWN');
                }
                break;
            }
            case 'logout':
                await api('/api/auth/logout', { method: 'POST' });
                A.me = null; A.role = null; A.otpMobile = null;
                document.body.classList.remove('admin-authed');
                location.href = '/index.html';
                break;
            case 'open-buyer': location.href = '/index.html'; break;
            case 'go-tab': adminNavigate(t.dataset.hash); break;
        }
    } catch (err) { toast(err.message, 'error'); }
}
// ==================== VIEWS ====================
async function adminHomeView() {
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + greeting() + ', ' + esc(A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin') + '</h1><p class="muted small">SocioMart Admin Console</p></div></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/pending"><div class="ac-icon">⏳</div><div class="ac-title">Pending Approvals</div><div class="ac-desc">Review and approve or reject new seller registrations.</div><span class="ac-chip next">Screen B — next increment</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/sellers"><div class="ac-icon">👥</div><div class="ac-title">Seller Registry</div><div class="ac-desc">Browse all sellers, filter by approval status, suspend accounts.</div><span class="ac-chip next">Screen C — next increment</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/analytics"><div class="ac-icon">📊</div><div class="ac-title">Platform Analytics</div><div class="ac-desc">Users, orders, products, traffic &amp; growth metrics.</div><span class="ac-chip next">Screen D — next increment</span></div>';
    if (A.role === 'SUPER_ADMIN') {
        h += '<div class="admin-card" data-action="go-tab" data-hash="#/console"><div class="ac-icon">⚙️</div><div class="ac-title">Platform Console</div><div class="ac-desc">Admin accounts, feature flags, seller grants, platform settings.</div><span class="ac-chip next">Screen E — next increment</span></div>';
    }
    h += '<div class="placeholder-note">Shell + OTP role gate is live (Increment A(. The screens above arrive in the next increments.</div>';
    return h;
}
function adminPlaceholderView(title, copy, icon) {
    return async function () {
        return '<div class="view-enter">' +
            '<div class="section-head admin-section-head"><div><h1>' + title + '</h1><p class="muted small">' + copy + '</p></div></div>' +
            '<div class="placeholder-note"><div style="font-size:2rem;margin-bottom:6px">' + icon + '</div><strong>' + title + '</strong> — arrives in a later increment.</div></div>';
    };
}
var adminPendingView = adminPlaceholderView('Pending Approvals', 'Approve or reject new seller registrations', '⏳');
var adminSellersView = adminPlaceholderView('Seller Registry', 'Browse and manage seller accounts by status', '👥');
var adminAnalyticsView = adminPlaceholderView('Platform Analytics', 'Platform usage, growth and traffic metrics', '📊');
var adminConsoleView = adminPlaceholderView('Platform Console', 'Super Admin: accounts, features, grants, settings', '⚙️');
// ==================== DELEGATED CLICKS ====================
document.addEventListener('click', async function (ev) {
    var t = ev.target.closest('[data-action]');
    if (!t) return;
    if (t.tagName === 'A' && t.getAttribute('href')) { ev.preventDefault(); }
    await adminAction(t.dataset.action, t);
});
// ==================== BOOT ====================
window.addEventListener('hashchange', function () { if (A.role) adminRender(); });
window.addEventListener('DOMContentLoaded', function () { initTheme(); adminGate(); });