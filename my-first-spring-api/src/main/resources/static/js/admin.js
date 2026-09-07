/**
 * SocioMart Admin App v1.0 — Complete admin console
 * Screens: Dashboard, Buyers, Sellers, Kitchens, Offerings, Orders, Enquiries, Pending Approvals
 */
var A = { me: null, role: null, loginMobile: null };
var adminRoutes = {
    '#/home': adminHomeView,
    '#/pending': adminPendingView,
    '#/sellers': adminSellersView,
    '#/buyers': adminBuyersView,
    '#/kitchens': adminKitchensView,
    '#/offerings': adminOfferingsView,
    '#/orders': adminOrdersView,
    '#/enquiries': adminEnquiriesView,
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
        '<div class="al-brand-row"><div class="al-brand">🛡️ SocioMart Admin</div>' +
        '<button class="icon-btn" type="button" data-action="toggle-theme" aria-label="Toggle theme">🌓</button></div>' +
        '<h2>Admin Sign In</h2>' +
        '<p class="muted small">Enter your mobile number to sign in. This console accepts ADMIN &amp; SUPER_ADMIN accounts.</p>' +
        '<div class="form-group"><label for="alMobile">Mobile number</label><input id="alMobile" inputmode="numeric" maxlength="10" placeholder="10-digit mobile" autocomplete="tel"></div>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="admin-login">Sign In</button>' +
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
            case 'admin-login': {
                var mobile = A.loginMobile || ($('#alMobile') ? $('#alMobile').value : '');
                if (!mobile || !/^[6-9]\d{9}$/.test(mobile)) throw new Error('Enter a valid 10-digit mobile number');
                A.loginMobile = mobile;
                var resp = await api('/api/auth/demo-login', { method: 'POST', body: { mobileNumber: mobile } });
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
            case 'login-back': renderLoginScreen(); break;
            case 'toggle-theme': if (typeof toggleTheme === 'function') toggleTheme(); break;
            case 'logout':
                await api('/api/auth/logout', { method: 'POST' });
                A.me = null; A.role = null; A.loginMobile = null;
                document.body.classList.remove('admin-authed');
                location.href = '/index.html';
                break;
            case 'open-buyer': location.href = '/index.html'; break;
            case 'go-tab': adminNavigate(t.dataset.hash); break;
            case 'approve-seller': {
                var id = Number(t.dataset.id);
                await api('/api/admin/sellers/' + id + '/approve', { method: 'POST' });
                toast('Seller approved', 'success');
                await adminRender();
                break;
            }
            case 'open-reject': {
                openModal(
                    '<h3>Reject ' + esc(t.dataset.name) + '?</h3>' +
                     '<p class="muted small">Add a reason (shown to the seller) — optional.</p>' +
                     '<div class="form-group"><textarea id="rejectReason" class="admin-textarea" rows="2" maxlength="200" placeholder="Reason (optional)"></textarea></div>' +
                    '<div class="modal-actions">' +
                    '<button class="btn btn-outline" type="button" data-action="cancel-reject">Cancel</button>' +
                    '<button class="btn btn-danger" type="button" data-action="confirm-reject" data-id="' + t.dataset.id + '">Reject</button>' +
                    '</div>');
                break;
            }
            case 'cancel-reject': closeModal(); break;
            case 'confirm-reject': {
                var id = Number(t.dataset.id);
                var reason = $('#rejectReason') ? $('#rejectReason').value.trim() : '';
                closeModal();
                await api('/api/admin/sellers/' + id + '/reject', { method: 'POST', body: { reason: reason || null } });
                toast('Seller rejected', 'success');
                await adminRender();
                break;
            }
        }
    } catch (err) { toast(err.message, 'error'); }
}

// ==================== VIEWS ====================
async function adminHomeView() {
    var data = await api('/api/admin/dashboard');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + greeting() + ', ' + esc(A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin') + '</h1><p class="muted small">SocioMart Admin Console</p></div></div>';

    h += '<div class="admin-card" data-action="go-tab" data-hash="#/pending"><div class="ac-icon">⏳</div><div class="ac-title">Pending Approvals</div><div class="ac-desc">' + (data.pendingSellers || 0) + ' sellers awaiting review</div><span class="ac-chip live">Live</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/sellers"><div class="ac-icon">👥</div><div class="ac-title">Sellers</div><div class="ac-desc">Total: ' + (data.totalSellers || 0) + ' | Approved: ' + (data.approvedSellers || 0) + '</div><span class="ac-chip next">Screen</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/kitchens"><div class="ac-icon">🏪</div><div class="ac-title">Kitchens</div><div class="ac-desc">Total: ' + (data.totalKitchens || 0) + ' | Live: ' + (data.liveKitchens || 0) + '</div><span class="ac-chip next">Screen</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/orders"><div class="ac-icon">📦</div><div class="ac-title">Orders</div><div class="ac-desc">Total: ' + (data.totalOrders || 0) + ' | Today: ' + (data.ordersToday || 0) + '</div><span class="ac-chip next">Screen</span></div>';
    h += '<div class="admin-card" data-action="go-tab" data-hash="#/analytics"><div class="ac-icon">📊</div><div class="ac-title">Platform Analytics</div><div class="ac-desc">GMV: ' + money(data.totalOrderValue || 0) + ' | Paid: ' + money(data.paidValue || 0) + '</div><span class="ac-chip next">Screen</span></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Order Value</h3>';
    h += '<div class="flex gap-2"><div class="flex-1"><div class="muted small">Total</div><div class="font-700">' + money(data.totalOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1"><div class="muted small">Today</div><div class="font-700">' + money(data.todayOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1"><div class="muted small">This Month</div><div class="font-700">' + money(data.monthOrderValue || 0) + '</div></div></div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Payments</h3>';
    h += '<div class="flex gap-2"><div class="flex-1"><div class="muted small">Paid</div><div class="font-700 green">' + (data.paidCount || 0) + ' · ' + money(data.paidValue || 0) + '</div></div>';
    h += '<div class="flex-1"><div class="muted small">Will Pay Later</div><div class="font-700 orange">' + (data.willPayLaterCount || 0) + ' · ' + money(data.willPayLaterValue || 0) + '</div></div>';
    h += '<div class="flex-1"><div class="muted small">Pending</div><div class="font-700">' + (data.pendingPaymentCount || 0) + ' · ' + money(data.pendingPaymentValue || 0) + '</div></div></div></div>';

    h += '</div>';
    return h;
}

async function adminPendingView() {
    var list = await api('/api/admin/sellers/pending');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Pending Approvals</h1><p class="muted small">' + (list ? list.length : 0) + ' seller(s) awaiting your decision</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">🎉 All caught up — no pending seller approvals.</div>';
    }
    list.forEach(function (s) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(s.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(s.name || 'Unknown') + '</div>' +
            '<div class="sr-meta">📱 ' + esc(s.mobileNumber || '—') + ' · Registered ' + adminDate(s.registeredAt) + '</div>' +
            '</div>' +
            '<div class="sr-actions">' +
            '<button class="btn btn-success btn-sm" type="button" data-action="approve-seller" data-id="' + s.id + '">Approve</button>' +
            '<button class="btn btn-outline btn-sm" type="button" data-action="open-reject" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Reject</button>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminBuyersView() {
    var list = await api('/api/admin/buyers');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Buyers</h1><p class="muted small">' + (list ? list.length : 0) + ' registered buyers</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No buyers yet.</div></div>';
    }
    list.forEach(function (b) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(b.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(b.name || 'Unknown') + '</div>' +
            '<div class="sr-meta">📱 ' + esc(b.mobileNumber || '—') + ' · ' + esc(b.society || '') + (b.building ? ', ' + esc(b.building) : '') + '</div>' +
            '<div class="sr-meta">Orders: ' + (b.orderCount || 0) + ' · Value: ' + money(b.totalOrderValue || 0) + ' · Favourites: ' + (b.favouriteKitchens || 0) + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminSellersView() {
    var list = await api('/api/admin/sellers');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Sellers</h1><p class="muted small">' + (list ? list.length : 0) + ' registered sellers</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No sellers yet.</div></div>';
    }
    list.forEach(function (s) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(s.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(s.name || 'Unknown') + ' <span class="pill pill-' + (s.sellerApprovalStatus === 'APPROVED' ? 'green' : s.sellerApprovalStatus === 'PENDING' ? 'amber' : 'grey') + '">' + esc(s.sellerApprovalStatus || '') + '</span></div>' +
            '<div class="sr-meta">📱 ' + esc(s.mobileNumber || '—') + ' · ' + esc(s.kitchenName || 'No kitchen') + ' · ' + esc(s.area || '') + '</div>' +
            '<div class="sr-meta">Live: ' + (s.liveOfferings || 0) + '/' + (s.totalOfferings || 0) + ' offerings · Registered ' + adminDate(s.createdAt) + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminKitchensView() {
    var list = await api('/api/admin/kitchens');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Kitchens</h1><p class="muted small">' + (list ? list.length : 0) + ' kitchens</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No kitchens yet.</div></div>';
    }
    list.forEach(function (k) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🏪</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(k.displayName || k.name) + ' <span class="pill pill-' + (k.hasLiveOfferings ? 'green' : 'grey') + '">' + (k.hasLiveOfferings ? 'Live' : 'No live items') + '</span></div>' +
            '<div class="sr-meta">Seller: ' + esc(k.sellerName || '—') + ' · ' + esc(k.area || '') + '</div>' +
            '<div class="sr-meta">Offerings: ' + (k.liveOfferings || 0) + ' live / ' + (k.totalOfferings || 0) + ' total</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOfferingsView() {
    var list = await api('/api/admin/offerings');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Offerings</h1><p class="muted small">' + (list ? list.length : 0) + ' offerings</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No offerings yet.</div></div>';
    }
    list.forEach(function (p) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🍽️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(p.name || '') + ' <span class="pill pill-' + (p.status === 'LIVE' ? 'green' : p.status === 'PRE_ORDER' ? 'blue' : p.status === 'SOLD_OUT' ? 'grey' : 'grey') + '">' + esc(p.status || '') + '</span></div>' +
            '<div class="sr-meta">' + esc(p.kitchenName || '') + ' · ' + money(p.price || 0) + (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + '</div>' +
            '<div class="sr-meta">' + (p.availableDate ? '📅 ' + esc(p.availableDate) + ' ' : '') + (p.cutoffTime ? '⏰ ' + esc(p.cutoffTime) + ' ' : '') + 'Qty: ' + (p.remainingQuantity != null ? p.remainingQuantity : '∞') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOrdersView() {
    var list = await api('/api/admin/orders');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Orders</h1><p class="muted small">' + (list ? list.length : 0) + ' orders</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No orders yet.</div></div>';
    }
    list.forEach(function (o) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">📦</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">#' + esc(o.orderNumber || String(o.id)) + ' · ' + esc(o.kitchenName || '') + '</div>' +
            '<div class="sr-meta">Buyer: ' + esc(o.buyerName || '—') + ' · ' + esc(o.buyerMobile || '') + '</div>' +
            '<div class="sr-meta">' + money(o.totalAmount || 0) + ' · ' + esc(o.orderStatus || '') + ' · ' + esc(o.paymentStatus || '') + '</div>' +
            '<div class="sr-meta">' + adminDate(o.createdAt) + (o.society ? ' · ' + esc(o.society) : '') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminEnquiriesView() {
    var list = await api('/api/admin/enquiries');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Enquiries</h1><p class="muted small">' + (list ? list.length : 0) + ' enquiries</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No enquiries yet.</div></div>';
    }
    list.forEach(function (e) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">✉️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(e.kitchenName || 'Kitchen') + '</div>' +
            '<div class="sr-meta">From: ' + esc(e.userName || 'Buyer') + ' · ' + adminDate(e.createdAt) + '</div>' +
            '<div class="sr-meta">' + esc(e.message || '') + '</div>' +
            '<div class="sr-meta">Status: ' + esc(e.status || '') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

function adminPlaceholderView(title, copy, icon) {
    return async function () {
        return '<div class="view-enter">' +
            '<div class="section-head admin-section-head"><div><h1>' + title + '</h1><p class="muted small">' + copy + '</p></div></div>' +
            '<div class="placeholder-note"><div class="font-size-2 mb-2">' + icon + '</div><strong>' + title + '</strong> — arrives in a later increment.</div></div>';
    };
}

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
