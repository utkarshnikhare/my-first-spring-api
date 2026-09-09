/**
 * SocioMart Admin App v1.0 — Complete admin console
 * Screens: Dashboard, Buyers, Sellers, Kitchens, Offerings, Orders, Enquiries, Pending Approvals
 */
var A = { me: null, role: null, loginMobile: null, trafficPeriod: 'today' };
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
/** Readable date/time for admin rows, e.g. "7 Sep 2026, 10:42 AM". */
function adminDate(iso) {
    if (!iso) return '—';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return esc(String(iso));
    var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    var h12 = d.getHours() % 12 || 12;
    var ampm = d.getHours() < 12 ? 'AM' : 'PM';
    return d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear() +
        ', ' + h12 + ':' + String(d.getMinutes()).padStart(2, '0') + ' ' + ampm;
}

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
            case 'admin-order-filter': {
                A.orderFilter = t.dataset.filter || 'all';
                await adminRender();
                break;
            }
            case 'admin-order-search': {
                var input = $('#adminOrderSearch');
                if (input) A.orderSearch = input.value;
                await adminRender();
                break;
            }
            case 'admin-order-detail': {
                var oid = t.dataset.id;
                var detail = await api('/api/admin/orders/' + oid);
                viewEl().innerHTML = await adminOrderDetailView(detail);
                break;
            }
            case 'admin-back-orders': {
                A.orderDetailId = null;
                location.hash = '#/orders';
                break;
            }
            case 'admin-traffic-period': {
                A.trafficPeriod = t.dataset.period || 'today';
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
    h += '<div class="section-head admin-section-head"><div><h1>' + greeting() + ', ' + esc(A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin') + '</h1><p class="muted small">Marketplace overview — live shared-database figures</p></div></div>';

    // Operational stat grid — every figure comes from /api/admin/dashboard (no hardcoding)
    h += '<div class="dash-grid">';
    function dashCard(icon, num, label, sub, hash) {
        var open = hash ? '<a class="dash-card" href="' + hash + '" data-action="go-tab" data-hash="' + hash + '">'
                        : '<div class="dash-card">';
        var close = hash ? '</a>' : '</div>';
        return open +
            '<div class="dc-top"><span class="dc-icon">' + icon + '</span><span class="dc-num">' + num + '</span></div>' +
            '<div class="dc-label">' + label + '</div>' +
            (sub ? '<div class="dc-sub">' + sub + '</div>' : '') +
            close;
    }
    h += dashCard('🛒', data.totalBuyers || 0, 'Buyers', 'registered accounts', '#/buyers');
    h += dashCard('👥', data.totalSellers || 0, 'Sellers', (data.approvedSellers || 0) + ' approved · ' + (data.pendingSellers || 0) + ' pending', '#/sellers');
    h += dashCard('⏳', data.pendingSellers || 0, 'Pending Approvals', 'awaiting review', '#/pending');
    h += dashCard('🏪', data.totalKitchens || 0, 'Kitchens', (data.liveKitchens || 0) + ' live · ' + (data.kitchensWithZeroLiveOfferings || 0) + ' with no live items', '#/kitchens');
    h += dashCard('🍽️', data.totalOfferings || 0, 'Offerings', (data.liveOfferings || 0) + ' live · ' + (data.preorderOfferings || 0) + ' pre-order · ' + (data.soldOutOfferings || 0) + ' sold out', '#/offerings');
    h += dashCard('📦', data.totalOrders || 0, 'Orders', 'today: ' + (data.ordersToday || 0) + ' · this month: ' + (data.ordersThisMonth || 0), '#/orders');
    h += dashCard('✉️', data.totalEnquiries || 0, 'Enquiries', (data.openEnquiries || 0) + ' awaiting response · ' + (data.resolvedEnquiries || 0) + ' responded', '#/enquiries');
    h += dashCard('❤️', data.totalFavourites || 0, 'Favourites', 'kitchens saved by buyers', '');
    h += '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Order Value</h3>' +
        '<p class="muted tiny" style="margin:0 0 8px">Total value of orders placed on the marketplace (not platform revenue).</p>';
    h += '<div class="flex gap-2 wrap"><div class="flex-1 min-140"><div class="muted small">Total</div><div class="font-700 font-size-2">' + money(data.totalOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1 min-140"><div class="muted small">Today</div><div class="font-700 font-size-2">' + money(data.todayOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1 min-140"><div class="muted small">This Month</div><div class="font-700 font-size-2">' + money(data.monthOrderValue || 0) + '</div></div></div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Payment Status</h3>' +
        '<div class="flex gap-2 wrap">' +
        '<div class="flex-1 min-140"><span class="pill pill-green">● Paid</span><div class="font-700 green mt-1">' + (data.paidCount || 0) + ' orders</div><div class="muted small">' + money(data.paidValue || 0) + '</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-amber">● Will Pay Later</span><div class="font-700 orange mt-1">' + (data.willPayLaterCount || 0) + ' orders</div><div class="muted small">' + money(data.willPayLaterValue || 0) + '</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-grey">● Pending</span><div class="font-700 mt-1">' + (data.pendingPaymentCount || 0) + ' orders</div><div class="muted small">' + money(data.pendingPaymentValue || 0) + '</div></div>' +
        '</div></div>';

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
        var st = s.sellerApprovalStatus || '';
        var needsAction = st === 'PENDING' || st === 'REJECTED';
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(s.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(s.name || 'Unknown') + ' <span class="pill pill-' + (st === 'APPROVED' ? 'green' : st === 'PENDING' ? 'amber' : 'grey') + '">' + esc(st || '') + '</span></div>' +
            '<div class="sr-meta">📱 ' + esc(s.mobileNumber || '—') + ' · ' + esc(s.kitchenName || 'No kitchen') + ' · ' + esc(s.area || '') + '</div>' +
            '<div class="sr-meta">Live: ' + (s.liveOfferings || 0) + '/' + (s.totalOfferings || 0) + ' offerings · Registered ' + adminDate(s.createdAt) + '</div>' +
            '</div>' +
            (needsAction
                ? '<div class="sr-actions">' +
                  '<button class="btn btn-success btn-sm" type="button" data-action="approve-seller" data-id="' + s.id + '">Approve</button>' +
                  '<button class="btn btn-outline btn-sm" type="button" data-action="open-reject" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Reject</button>' +
                  '</div>'
                : '') +
            '</div>';
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
    var filter = A.orderFilter || 'all';
    var search = A.orderSearch || '';
    var qs = '?filter=' + encodeURIComponent(filter) + '&search=' + encodeURIComponent(search);
    var list = await api('/api/admin/orders' + qs);
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Orders</h1><p class="muted small">' + (list ? list.length : 0) + ' orders</p></div></div>';
    h += '<div class="admin-filters">';
    h += '<input type="text" class="form-input form-input-sm" placeholder="Search orders..." value="' + esc(search) + '" id="adminOrderSearch">';
    h += '<button class="btn btn-sm ' + (filter === 'all' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-order-filter" data-filter="all">All</button>';
    h += '<button class="btn btn-sm ' + (filter === 'last3days' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-order-filter" data-filter="last3days">Last 3 Days</button>';
    h += '</div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">No orders yet.</div></div>';
    }
    list.forEach(function (o) {
        var os = o.orderStatus || '';
        var ps = o.paymentStatus || '';
        var osPill = os === 'ORDERED' ? '<span class="pill pill-amber">🟠 ' + esc(os) + '</span>'
            : os === 'CONFIRMED' ? '<span class="pill pill-green">🟢 ' + esc(os) + '</span>'
            : os === 'READY' ? '<span class="pill pill-blue">🔵 ' + esc(os) + '</span>'
            : os === 'DELIVERED' ? '<span class="pill pill-grey">✓ ' + esc(os) + '</span>'
            : os === 'COMPLETED' ? '<span class="pill pill-green">✓ ' + esc(os) + '</span>'
            : os === 'CANCELLED' ? '<span class="pill pill-red">🔴 ' + esc(os) + '</span>'
            : '<span class="pill pill-grey">' + esc(os) + '</span>';
        var psPill = ps === 'PAID' ? '<span class="pill pill-green">● Paid</span>'
            : ps === 'WILL_PAY_LATER' ? '<span class="pill pill-amber">● Will Pay Later</span>'
            : ps === 'PENDING' ? '<span class="pill pill-grey">● Pending</span>'
            : (ps ? '<span class="pill pill-grey">' + esc(ps) + '</span>' : '');
        h += '<div class="seller-row" data-action="admin-order-detail" data-id="' + o.id + '">' +
            '<div class="sr-avatar">📦</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">#' + esc(o.orderNumber || String(o.id)) + ' · ' + esc(o.kitchenName || '') + '</div>' +
            '<div class="sr-meta">Buyer: ' + esc(o.buyerName || '—') + ' · ' + esc(o.buyerMobile || '') + '</div>' +
            '<div class="sr-meta os-badges">' + osPill + ' ' + psPill + ' <strong>' + money(o.totalAmount || 0) + '</strong></div>' +
            '<div class="sr-meta">' + adminDate(o.createdAt) + (o.society ? ' · ' + esc(o.society) : '') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOrderDetailView(id) {
    var o = await api('/api/admin/orders/' + id);
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Order #' + esc(o.orderNumber || String(o.id)) + '</h1><p class="muted small">' + adminDate(o.createdAt) + '</p></div></div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Customer</h3>';
    h += '<p><strong>' + esc(o.buyerName || '—') + '</strong> · ' + esc(o.buyerMobile || '') + '</p>';
    h += '<p class="muted small">' + esc(o.buyerSociety || '') + (o.buyerBuilding ? ', ' + esc(o.buyerBuilding) : '') + (o.buyerFlat ? ', Flat ' + esc(o.buyerFlat) : '') + '</p>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Kitchen & Seller</h3>';
    h += '<p><strong>Kitchen:</strong> ' + esc(o.kitchenName || '—') + ' (ID: ' + o.kitchenId + ')</p>';
    h += '<p><strong>Seller:</strong> ' + esc(o.sellerName || '—') + ' (ID: ' + o.sellerId + ')</p>';
    h += '<p class="muted small">' + esc(o.kitchenSociety || '') + (o.kitchenBuilding ? ', ' + esc(o.kitchenBuilding) : '') + '</p>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Items</h3>';
    (o.items || []).forEach(function (it) {
        h += '<div class="flex items-center gap-2" style="padding:8px 0;border-bottom:1px solid #eee;">';
        h += '<div class="flex-1"><strong>' + esc(it.productName || 'Item') + '</strong></div>';
        h += '<div class="muted small">Qty: ' + it.quantity + '</div>';
        h += '<div class="muted small">Unit Price: ' + money(it.price || 0) + '</div>';
        h += '<div class="muted small">Line Total: ' + money(it.total || 0) + '</div>';
        h += '</div>';
    });
    h += '<div class="total-row" style="margin-top:12px;"><span>Order Total</span><span>' + money(o.totalAmount || 0) + '</span></div>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Status</h3>';
    h += '<p><strong>Payment:</strong> ' + esc(o.paymentStatus || '—') + '</p>';
    h += '<p><strong>Order:</strong> ' + esc(o.orderStatus || '—') + '</p>';
    if (o.customInstructions) {
        h += '<p class="muted small"><strong>Note:</strong> ' + esc(o.customInstructions) + '</p>';
    }
    h += '</div>';
    h += '<button class="btn btn-secondary btn-block" data-action="admin-back-orders">← Back to Orders</button>';
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
        var es = e.status || '';
        var esPill = es === 'WAITING_FOR_RESPONSE' ? '<span class="pill pill-amber">⏳ Awaiting response</span>'
            : es === 'SELLER_RESPONDED' ? '<span class="pill pill-green">✓ Seller responded</span>'
            : (es ? '<span class="pill pill-grey">' + esc(es) + '</span>' : '');
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">✉️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(e.kitchenName || 'Kitchen') + ' ' + esPill + '</div>' +
            '<div class="sr-meta">From: ' + esc(e.userName || 'Buyer') + ' · ' + adminDate(e.createdAt) + '</div>' +
            '<div class="sr-meta eq-message">“' + esc(e.message || '') + '”</div>' +
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

// adminSellersView is the real registry backed by GET /api/admin/sellers —
// it was previously shadowed by a placeholder, dead-ending the Sellers tab
// despite a fully working backend. The real view is defined above.
var adminAnalyticsView = adminTrafficView;
var adminConsoleView = adminPlaceholderView('Platform Console', 'Super Admin: accounts, features, grants, settings', '⚙️');

function adminTrafficView() {
    return async function () {
        var period = A.trafficPeriod || 'today';
        var view = viewEl();
        view.innerHTML = '<div class="view-enter"><div class="section-head admin-section-head"><div><h1>Traffic Analytics</h1><p class="muted small">Active Buyers and Sellers based on real order activity</p></div></div>' +
            '<div class="admin-filters">' +
            '<button class="btn btn-sm ' + (period === 'today' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="today">Today</button>' +
            '<button class="btn btn-sm ' + (period === 'week' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="week">This Week</button>' +
            '<button class="btn btn-sm ' + (period === 'month' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="month">This Month</button>' +
            '</div>' +
            '<div id="trafficContent"><div class="page-loading"><div class="spinner"></div></div></div>';
        try {
            var data = await api('/api/admin/traffic?period=' + encodeURIComponent(period));
            renderTrafficContent(data, period);
        } catch (err) {
            document.getElementById('trafficContent').innerHTML = '<div class="admin-empty">Failed to load traffic analytics: ' + esc(err.message) + '</div>';
        }
    };
}

function renderTrafficContent(data, period) {
    var container = document.getElementById('trafficContent');
    if (!container) return;
    if (!data || !data.series || !data.series.length) {
        container.innerHTML = '<div class="admin-empty">No buyer or seller activity found for this period.</div>';
        return;
    }
    var buyers = data.activeBuyers || 0;
    var sellers = data.activeSellers || 0;
    var h = '<div class="dash-grid card-mt">' +
        '<div class="dash-card"><div class="dc-top"><span class="dc-icon">🛒</span><span class="dc-num">' + buyers + '</span></div><div class="dc-label">Active Buyers</div><div class="dc-sub">' + periodLabel(period) + '</div></div>' +
        '<div class="dash-card"><div class="dc-top"><span class="dc-icon">👨‍🍳</span><span class="dc-num">' + sellers + '</span></div><div class="dc-label">Active Sellers</div><div class="dc-sub">' + periodLabel(period) + '</div></div>' +
        '</div>';
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Traffic Trend</h3><p class="muted tiny" style="margin:0 0 12px">Distinct active Buyers and Sellers over the selected period.</p>';
    h += '<div class="traffic-chart-wrap">' + trafficSvgChart(data.series, period) + '</div>';
    h += '<div class="flex gap-2 wrap" style="margin-top:14px">' +
        '<div class="flex-1 min-140"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#4F46E5;margin-right:6px;vertical-align:middle"></span> Buyers</div>' +
        '<div class="flex-1 min-140"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#16A34A;margin-right:6px;vertical-align:middle"></span> Sellers</div>' +
        '</div></div>';
    container.innerHTML = h;
}

function periodLabel(period) {
    if (period === 'today') return 'Today';
    if (period === 'week') return 'This Week';
    if (period === 'month') return 'This Month';
    return period;
}

function trafficSvgChart(series, period) {
    var width = 800;
    var height = 280;
    var pad = { top: 20, right: 20, bottom: 40, left: 45 };
    var chartW = width - pad.left - pad.right;
    var chartH = height - pad.top - pad.bottom;

    var maxVal = 0;
    series.forEach(function (pt) {
        maxVal = Math.max(maxVal, pt.buyers || 0, pt.sellers || 0);
    });
    if (maxVal === 0) maxVal = 1;

    var xStep = chartW / Math.max(series.length - 1, 1);
    var pointsBuyers = series.map(function (pt, i) {
        return [pad.left + i * xStep, pad.top + chartH - ((pt.buyers || 0) / maxVal) * chartH];
    });
    var pointsSellers = series.map(function (pt, i) {
        return [pad.left + i * xStep, pad.top + chartH - ((pt.sellers || 0) / maxVal) * chartH];
    });

    function polyline(pts) {
        return pts.map(function (p) { return p[0] + ',' + p[1]; }).join(' ');
    }

    function dots(pts, color) {
        return pts.map(function (p, i) {
            return '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="4" fill="' + color + '" stroke="#fff" stroke-width="2" />' +
                '<title>' + (series[i].label || '') + ': Buyers ' + (series[i].buyers || 0) + ', Sellers ' + (series[i].sellers || 0) + '</title>';
        }).join('');
    }

    var xLabels = series.map(function (pt, i) {
        var x = pad.left + i * xStep;
        var show = series.length <= 12 || i % Math.ceil(series.length / 12) === 0 || i === series.length - 1;
        if (!show) return '';
        return '<text x="' + x + '" y="' + (height - 8) + '" text-anchor="middle" font-size="11" fill="var(--muted)">' + esc(pt.label) + '</text>';
    }).join('');

    var yTicks = 5;
    var yLabels = '';
    for (var i = 0; i <= yTicks; i++) {
        var val = Math.round((maxVal / yTicks) * i);
        var y = pad.top + chartH - (i / yTicks) * chartH;
        yLabels += '<text x="' + (pad.left - 8) + '" y="' + (y + 4) + '" text-anchor="end" font-size="11" fill="var(--muted)">' + val + '</text>';
        yLabels += '<line x1="' + pad.left + '" y1="' + y + '" x2="' + (width - pad.right) + '" y2="' + y + '" stroke="var(--border)" stroke-width="1" opacity="0.5" />';
    }

    return '<svg viewBox="0 0 ' + width + ' ' + height + '" style="width:100%;height:auto;max-height:280px" preserveAspectRatio="xMidYMid meet">' +
        '<rect x="' + pad.left + '" y="' + pad.top + '" width="' + chartW + '" height="' + chartH + '" fill="var(--surface)" rx="8" />' +
        yLabels +
        xLabels +
        '<polyline fill="none" stroke="#4F46E5" stroke-width="3" points="' + polyline(pointsBuyers) + '" />' +
        '<polyline fill="none" stroke="#16A34A" stroke-width="3" points="' + polyline(pointsSellers) + '" />' +
        dots(pointsBuyers, '#4F46E5') +
        dots(pointsSellers, '#16A34A') +
        '</svg>';
}

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
document.addEventListener('keyup', function (ev) {
    if (ev.target.id === 'adminOrderSearch') {
        adminAction('admin-order-search', ev.target);
    }
});
