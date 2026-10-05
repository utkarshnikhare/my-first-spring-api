/**
 * SocioMart Seller App v1.0 - 5-tab SPA
 */
var S = { user: null, kitchen: null, viewMode: 'editor', selectedDate: 'today', sortFilter: 'all', historySelected: [], draftOffering: null, favTemplates: [], favError: null, historyItems: [], offeringFilterSociety: '', offeringFilterStatus: '', offeringFilterDelivery: '', offeringProductId: '', deliverySaving: {}, deliveryBlockRequestId: 0, bulkDelivering: false, offeringFor: 'today', quickPostRequestId: null, editOffering: null, dashFilter: 'ALL' };
var sellerRoutes = {
    '#/home': sellerHomeView, '#/add': sellerAddView, '#/create': sellerCreateView,
    '#/edit-offering': sellerEditOfferingView,
    '#/quick-post': sellerQuickPostView,
    // "My Offerings". '#/history' stays registered as an alias so existing
    // bookmarks, deep links and the dashboard's history link keep working.
    '#/my-offerings': sellerHistoryView, '#/history': sellerHistoryView,
    '#/kitchen': sellerKitchenView, '#/orders': sellerOrdersView,
    '#/order-detail': sellerOrderDetailView, '#/earnings': sellerEarningsView,
    '#/enquiries': sellerEnquiriesView
};
function sellerResolveRoute(hash) {
    if (sellerRoutes[hash]) return { fn: sellerRoutes[hash], arg: hash };
    if (hash.startsWith('#/order-detail/order/')) return { fn: sellerOrderDetailByOrderView, arg: hash.split('/')[3] };
    if (hash.startsWith('#/order-detail/')) return { fn: sellerOrderDetailView, arg: hash.split('/')[2] };
    return { fn: sellerHomeView, arg: '#/home' };
}
/**
 * Seller session guard.
 *
 * The Buyer and Seller apps run on the same origin and therefore share ONE
 * browser session. Logging in as a Buyer overwrites the server-side identity the
 * Seller app depends on, so the Seller app used to show a raw
 * "Only sellers can perform this action" until the page was reloaded: it
 * authenticated once at boot and then trusted that cached state.
 *
 * The backend stays authoritative here. We ask the server who the current
 * session is, and only when it is not our seller session do we re-establish it
 * through the app's existing /api/seller-app/demo-login. The expected role is
 * read from the server's own response - nothing here is hardcoded and no
 * authorization is bypassed: a Buyer calling the seller APIs directly still
 * gets 403, because requireSeller() re-checks the persisted role server-side.
 */
async function ensureSellerSession() {
    try {
        var me = await api('/api/auth/me');
        if (me && me.authenticated && me.role === 'SELLER') return true;
    } catch (e) {
        // Unknown session state - fall through and re-authenticate below.
    }
    // Stale, missing, or logged in as a non-seller: restore the seller session.
    return restoreSellerSession();
}

/**
 * Forces a fresh seller login and reports whether the server really handed back
 * a seller. Used when a request has already failed with an auth error: at that
 * point the session is known NOT to be a seller, so re-probing /api/auth/me
 * would be a wasted round trip that can race the same way.
 *
 * The expected role is read from the server's own response - nothing is
 * hardcoded and no authorization is bypassed: requireSeller() still re-checks
 * the persisted role server-side on every request.
 */
async function restoreSellerSession() {
    try {
        var s = await api('/api/seller-app/demo-login', { method: 'POST' });
        return !!(s && s.authenticated && s.role === 'SELLER');
    } catch (e) {
        return false;
    }
}

/** True for the two statuses that mean "this session is not the seller". */
function isSellerAuthError(err) {
    return !!err && (err.status === 401 || err.status === 403);
}

/**
 * Seller-scoped request with bounded self-heal.
 *
 * ensureSellerSession() runs before the route, but that guard and the route's
 * own request are two separate HTTP calls. The Buyer and Seller apps share ONE
 * browser session, so a Buyer login landing in that gap makes an otherwise
 * valid seller request fail with 401/403 - and the view then rendered that raw
 * error permanently, leaving the Seller screen stuck on "Could not load
 * dashboard / Only sellers can perform this action" with no way back except a
 * manual reload.
 *
 * So on an auth failure: re-establish the seller session and retry ONCE. The
 * retry is strictly bounded (no loop, no backoff). Any non-auth error, and any
 * second auth failure, propagates untouched, so genuine server, network and
 * permission problems are still reported honestly rather than hidden.
 */
async function sellerApi(path, opts) {
    try {
        return await api(path, opts);
    } catch (err) {
        if (!isSellerAuthError(err)) throw err;
        if (!(await restoreSellerSession())) throw err;
        return await api(path, opts);
    }
}

function sellerAuthErrorHtml() {
    return '<div class="view-enter">' +
        emptyHtml('🔒', 'Seller session required',
            'We could not verify your seller session. Please retry.',
            '<button class="btn btn-primary btn-mt-md" type="button" data-action="seller-retry">Retry</button>') +
        '</div>';
}

async function sellerRender() {
    var hash = location.hash || '#/home';
    var route = sellerResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    closeSheet();
    // Verify the server session before calling any owner-scoped endpoint, so a
    // Buyer login elsewhere cannot leave this screen stuck on an auth error.
    if (!(await ensureSellerSession())) {
        view.innerHTML = sellerAuthErrorHtml();
        return;
    }
    try { view.innerHTML = await route.fn(route.arg) || ''; sellerUpdateNav(hash); await loadUnreadNotifications(); if (typeof applyThemeUiState === 'function') applyThemeUiState(); window.scrollTo(0, 0); var saInput = $('#coverageSocietyIdsInput'); if (saInput) renderCoverageSocieties(); }
    catch (err) { view.innerHTML = '<div class="view-enter">' + emptyHtml('⚠️', 'Something went wrong', err.message) + '</div>'; }
}
function sellerUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash === '#/home' ? 'home' : hash === '#/kitchen' ? 'kitchen' : (hash === '#/orders' || hash.startsWith('#/order-detail/')) ? 'orders' : hash === '#/enquiries' ? 'enquiries' : (hash === '#/history' || hash === '#/my-offerings') ? 'history' : hash === '#/earnings' ? 'earnings' : null;
    var el = document.querySelector('[data-nav="' + (key || '') + '"]');
    if (el) el.classList.add('active');
}

/**
 * Renders the society checkboxes for the currently selected Area.
 *
 * <p>Choosing an Area preselects every active society inside it (the "initially
 * selected" rule); the seller then unticks individual ones. A society the Admin
 * adds later is only picked up the next time the seller visits this screen and
 * re-submits - it is never silently adopted into existing coverage.</p>
 */
function renderCoverageSocieties() {
    var list = $('#coverageSocietyList');
    if (!list) return;
    var areaId = S.coverageAreaId || '';
    var area = null;
    (S.coverageOptions || []).forEach(function (a) { if (String(a.id) === String(areaId)) area = a; });
    list.innerHTML = '';
    if (!areaId || !area) {
        list.innerHTML = '<div class="muted small">Select an area to choose its societies.</div>';
        return;
    }
    var societies = area.societies || [];
    if (!societies.length) {
        list.innerHTML = '<div class="muted small">This area has no active societies yet.</div>';
        return;
    }
    // Selecting a different Area starts from that area's full active set.
    if (String(S.coverageLoadedAreaId || '') !== String(areaId)) {
        S.coverageIds = societies.map(function (s) { return Number(s.id); });
        S.coverageLoadedAreaId = String(areaId);
    } else {
        S.coverageIds = (S.coverageIds || []).filter(function (id) {
            return societies.some(function (s) { return Number(s.id) === Number(id); });
        });
    }
    var html = '';
    societies.forEach(function (s) {
        var on = S.coverageIds.indexOf(Number(s.id)) !== -1;
        html += '<label class="sa-pill" style="display:inline-flex;align-items:center;gap:6px;margin:0 6px 6px 0;cursor:pointer">'
            + '<input type="checkbox" data-action="toggle-coverage-society" data-id="' + esc(s.id) + '"' + (on ? ' checked' : '') + '>'
            + '<span>' + esc(s.name) + '</span></label>';
    });
    list.innerHTML = html;
    updateCoverageIdsInput();
}

function updateCoverageIdsInput() {
    var input = $('#coverageSocietyIdsInput');
    if (input) input.value = (S.coverageIds || []).join(',');
}

function parseServiceAreas(val) {
    if (!val) return [];
    return val.split(',').map(function (s) { return s.trim(); }).filter(function (s) { return s.length > 0; });
}
function sellerNavigate(hash) { if (location.hash === hash) sellerRender(); else location.hash = hash; }
function greeting() { var h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
function offeringStatusBadge(p) {
    if (p.soldOut) return '<span class="oc-badge soldout">SOLD OUT</span>';
    if (p.ordersPaused) return '<span class="oc-badge paused">PAUSED</span>';
    if (p.ordersClosed || p.lifecycleState === 'ORDERS_CLOSED') return '<span class="oc-badge closed">ORDERS CLOSED</span>';
    if (p.isPreorder || p.lifecycleState === 'PRE_ORDER') return '<span class="oc-badge live">PRE-ORDER • LIVE</span>';
    return '<span class="oc-badge live">LIVE</span>';
}
/** "Orders close" value for a dashboard offering card: pretty time, plus the
 *  offering day/date for future (pre-order) offerings so the seller knows
 *  which date the cutoff belongs to. Uses the existing prettyTime/prettyDate. */
function sellerOrdersCloseLabel(p) {
    if (!p.cutoffTime) return '--';
    var time = prettyTime(p.cutoffTime);
    var day = p.availableDate ? prettyDate(p.availableDate) : '';
    if (day && day !== 'Today' && day !== 'Invalid Date') return day + ', ' + time;
    return time;
}
/** "Delivery" value for a dashboard offering card: the persisted ready-by text.
 *  Pure 24-hour times are prettified; free-text values that already carry their
 *  own day context (e.g. "1:30 PM Monday", "1:00 PM today") are shown as-is. */
function sellerDeliveryLabel(p) {
    var v = (p.readyByTime || '').trim();
    if (!v) return '--';
    return /^([01]?\d|2[0-3]):[0-5]\d$/.test(v) ? prettyTime(v) : v;
}
function statusDot(paid, cancelled) { return cancelled ? '<span class="status-dot red"></span>' : paid ? '<span class="status-dot green"></span>' : '<span class="status-dot orange"></span>'; }
function localDateStr(d) { var y = d.getFullYear(), m = ('0' + (d.getMonth() + 1)).slice(-2), day = ('0' + d.getDate()).slice(-2); return y + '-' + m + '-' + day; }
function applyOfferingDatePicker(picker) {
    if (!picker) return;
    if (picker.value && picker.value < sellerDate('today')) {
        toast('Offering date cannot be in the past', 'error');
        picker.value = sellerDate('today');
    }
    var hidden = $('#availDate');
    if (hidden && picker.value) hidden.value = picker.value;
}
function sellerDate(dateKey) {
    if (dateKey === 'tomorrow') return localDateStr(new Date(Date.now() + 864e5));
    if (dateKey && dateKey !== 'today' && dateKey !== 'pick') return dateKey;
    return localDateStr(new Date());
}
function prettyDateTime(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    return prettyDate(d.toISOString().split('T')[0]) + ' ' + prettyTime(d.toTimeString().slice(0,5));
}
function sellerImg(url) {
    if (!url) return '';
    var u = String(url).trim();
    if (!u) return '';
    // Legacy seed data sometimes carried a bare unit word ("piece"/"plate")
    // in the image column. Those are NOT fetchable URLs - rendering them as
    // <img src> triggers a same-origin request like /piece that 404s and
    // logs a console error. Only pass through real image references; the
    // caller falls back to the emoji placeholder otherwise.
    var low = u.toLowerCase();
    if (low === 'piece' || low === 'plate' || low === 'per piece' || low === 'per plate' || low === 'per box' || low === 'box' || low === 'cup' || low === 'bowl') return '';
    if (u.slice(0, 8) === 'https://' || u.slice(0, 7) === 'http://') return u;
    if (u.charAt(0) === '/' || u.slice(0, 5) === 'data:') return u;
    if (/\.(png|jpe?g|gif|webp|svg|avif)(\?.*)?$/i.test(u)) return u;
    return '';
}
function foodEmoji(name) {
    var n = (name || '').toLowerCase();
    if (n.indexOf('poha') >= 0 || n.indexOf('misal') >= 0) return '🍲';
    if (n.indexOf('dosa') >= 0 || n.indexOf('idli') >= 0 || n.indexOf('sambar') >= 0) return '🍚';
    if (n.indexOf('paratha') >= 0 || n.indexOf('roti') >= 0 || n.indexOf('naan') >= 0) return '🫓';
    if (n.indexOf('biryani') >= 0) return '🍛';
    if (n.indexOf('sweet') >= 0 || n.indexOf('kheer') >= 0 || n.indexOf('gulab') >= 0) return '🍮';
    if (n.indexOf('samosa') >= 0 || n.indexOf('pak') >= 0) return '🥟';
    if (n.indexOf('lassi') >= 0) return '🥗';
    if (n.indexOf('noodle') >= 0) return '🍜';
    return '🍽️';
}

// SCREEN 2: ADD OFFERING ENTRY POINT
async function sellerAddView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Add Offering</h1><p class="muted small">Favourites are manual reusable templates (maximum 3). History is automatic published-offering history.</p></div>';
    h += '<p class="muted small mb-2"><a class="text-brand" href="#/history">View automatic History →</a></p>';
    // A failed read is NOT the same as "you have no favourites": keep the error
    // separate from the legitimately empty list so real data is never hidden by it.
    S.favTemplates = []; S.favError = null;
    try { S.favTemplates = (await sellerApi('/api/seller-app/templates')) || []; } catch (e) { S.favError = e.message || 'please try again'; }
    h += '<div class="pathway-card" data-action="go-use-favourite"><div class="pc-icon">⭐</div><div class="pc-title">Create from Favourite</div><div class="pc-desc">Quickly post from saved templates (max 3).</div>';
    if (S.favError) { h += '<div class="pc-desc">Could not load your saved favourites - ' + esc(S.favError) + ' <button class="btn btn-secondary btn-sm" type="button" data-action="retry-favourites">Retry</button></div>'; } else if (S.favTemplates.length > 0) { h += '<div class="favourite-pills">'; S.favTemplates.forEach(function (t) { h += '<span class="fav-pill" data-action="use-template" data-tid="' + t.id + '">⭐ ' + esc(t.name) + '</span>'; }); h += '</div>'; }
    else { h += '<div class="pc-desc">No saved favourites yet - turn on Save as template (max 3) while creating an offering.</div>'; }
    h += '</div>';
    h += '<div class="pathway-card" data-action="go-create"><div class="pc-icon">✨</div><div class="pc-title">Create New Offering</div><div class="pc-desc">Fill in all details manually.</div></div>';
    h += '<div class="pathway-card" data-action="go-quick-post"><div class="pc-icon">📋</div><div class="pc-title">Quick Post</div><div class="pc-desc">Paste a WhatsApp message and publish a simple Today announcement.</div></div>';
    h += '</div>';
    return h;
}

// SCREEN 1: SELLER DASHBOARD (HOME)
/* ============================================================
   SELLER DASHBOARD - Stitch redesign (presentation only)
   ------------------------------------------------------------
   Every class below is prefixed `sd-` so the new visual language
   cannot leak into the other seller screens (Kitchen, Orders, My
   Offerings, Earnings, Quick Post) or into the Buyer/Admin apps.
   All data still comes from the EXISTING endpoints:
     GET /api/seller-app/dashboard  (metrics + offerings + earnings)
     GET /api/seller/kitchen        (store image / name / paused / sellerType)
     GET /api/seller-app/orders/summary?date=today (per-offering counts)
   No new API, no new route, no backend change.
   ============================================================ */

/** Seller first name for the greeting. Falls back to the session user name. */
function sellerFirstName(dash) {
    var raw = (dash && dash.sellerName) || (S.user && S.user.name) || '';
    raw = String(raw).trim();
    if (!raw) return 'Seller';
    return raw.split(/\s+/)[0];
}

/** Initial letter for the header avatar. */
function sellerInitial() {
    var raw = (S.user && S.user.name) || '';
    raw = String(raw).trim();
    return raw ? raw.charAt(0).toUpperCase() : 'S';
}

/**
 * Top summary cards. Values come straight from the dashboard payload:
 * Views Today / Followers are real analytics counts and Total Orders is the
 * seller's real order count. Nothing here is invented.
 */
function sdStatCardsHtml(dash) {
    var cards = [
        { icon: '👁️', cls: 'peach', value: dash.viewsToday || 0, label: 'Views Today' },
        { icon: '💗', cls: 'pink', value: dash.followers || 0, label: 'Followers' },
        { icon: '📦', cls: 'blue', value: dash.totalOrders || 0, label: 'Total Orders' }
    ];
    return '<div class="sd-stats">' + cards.map(function (c) {
        return '<div class="sd-stat sd-stat--' + c.cls + '">'
            + '<span class="sd-stat__icon" aria-hidden="true">' + c.icon + '</span>'
            + '<span class="sd-stat__value">' + esc(String(c.value)) + '</span>'
            + '<span class="sd-stat__label">' + esc(c.label) + '</span>'
            + '</div>';
    }).join('') + '</div>';
}

/**
 * Storefront card: real store photo, real store name, real operational status.
 * LIVE is derived from the seller's own kitchen.paused flag - never hard-coded,
 * and a paused kitchen reads PAUSED instead. Both seller types share this one
 * component, so no Kitchen-only assumption is baked in.
 */
function sdStoreCardHtml(dash, kitchen) {
    var img = kitchen ? sellerImg(kitchen.imageUrl) : '';
    var name = (kitchen && kitchen.displayName) || dash.kitchenName || 'Your Store';
    var paused = !!(kitchen && kitchen.paused);
    var homemade = !!(kitchen && kitchen.sellerType === 'HOMEMADE_PRODUCTS');
    var ph = homemade ? '🧺' : '🍽️';
    var state = paused ? 'is-paused' : 'is-active';
    return '<div class="sd-store">'
        + '<div class="sd-store__photo" data-emoji="' + ph + '">'
        + (img ? '<img src="' + esc(img) + '" alt="' + esc(name) + '" onerror="imgFallback(this)">' : ph)
        + '</div>'
        + '<div class="sd-store__main">'
        + '<div class="sd-store__nameline">'
        + '<a class="sd-store__name" href="#/kitchen">' + esc(name) + '</a>'
        + '<a class="sd-store__chev" href="#/kitchen" aria-label="Manage ' + esc(name) + '">›</a>'
        + '</div>'
        + '<div class="sd-store__status">'
        + '<span class="sd-dot ' + state + '" aria-hidden="true"></span>'
        + '<span class="' + state + '">' + (paused ? 'Paused' : 'Active') + '</span>'
        + '<span class="sd-store__sep">•</span>'
        + '<span class="sd-flag ' + state + '">' + (paused ? 'PAUSED' : 'LIVE') + '</span>'
        + '</div></div>'
        + '<a class="sd-store__edit" href="#/kitchen"><span aria-hidden="true">✏️</span> Edit</a>'
        + '</div>';
}

/**
 * Quick actions. Each one is a real, already-working destination:
 *   View Store     -> the existing preview-kitchen action, which opens the
 *                     buyer kitchen page at /index.html#/kitchen/{id}
 *   Manage Profile -> #/kitchen (store profile + Service Areas + gallery)
 *   Today's Menu   -> #/my-offerings
 *   Gallery        -> #/kitchen (gallery images are managed there)
 *   More           -> #/quick-post
 * No decorative no-op controls are introduced.
 */
function sdQuickActionsHtml() {
    var acts = [
        { icon: '🏪', label: 'View Store', act: 'preview-kitchen' },
        { icon: '👤', label: 'Manage Profile', route: '#/kitchen' },
        { icon: '📋', label: 'Today\u2019s Menu', route: '#/my-offerings' },
        { icon: '🖼️', label: 'Gallery', route: '#/kitchen' },
        { icon: '⋯', label: 'More', route: '#/quick-post' }
    ];
    return '<div class="sd-quick">' + acts.map(function (a) {
        var icon = '<span class="sd-qa__icon" aria-hidden="true">' + a.icon + '</span>';
        var label = '<span class="sd-qa__label">' + esc(a.label) + '</span>';
        // The preview action must run through the existing click handler, so it
        // is a button; the rest are plain links to existing routes.
        if (a.act) return '<button class="sd-qa" type="button" data-action="' + a.act + '">' + icon + label + '</button>';
        return '<a class="sd-qa" href="' + a.route + '">' + icon + label + '</a>';
    }).join('') + '</div>';
}

/** Coarse grouping used only by the dashboard filter dropdown. */
function sdCategoryGroup(p) {
    var c = String((p && p.category) || '').toLowerCase();
    if (!c) return 'KITCHEN';
    return /homemade|ghee|achar|papad|cake|sweet|jar|pack/.test(c) ? 'HOMEMADE' : 'KITCHEN';
}
function sdCategoryLabel(k) { return k === 'HOMEMADE' ? 'Homemade' : 'Kitchen'; }

/**
 * Simple dashboard filter. Full filtering/history lives on My Offerings, so this
 * stays deliberately minimal: "All", plus Kitchen/Homemade only when the seller
 * actually has offerings in more than one category.
 */
function sdOfferingFilterHtml(offerings) {
    var groups = {};
    offerings.forEach(function (p) {
        var g = sdCategoryGroup(p);
        groups[g] = (groups[g] || 0) + 1;
    });
    var keys = Object.keys(groups);
    var current = S.dashFilter || 'ALL';
    if (current !== 'ALL' && keys.indexOf(current) === -1) current = 'ALL';
    var opts = [{ k: 'ALL', n: offerings.length, label: 'All' }];
    if (keys.length > 1) keys.forEach(function (k) { opts.push({ k: k, n: groups[k], label: sdCategoryLabel(k) }); });
    return '<label class="sd-filter"><span class="visually-hidden">Filter offerings</span>'
        + '<select data-action="set-dash-filter" aria-label="Filter offerings by category">'
        + opts.map(function (o) {
            return '<option value="' + esc(o.k) + '"' + (o.k === current ? ' selected' : '') + '>'
                + esc(o.label + ' (' + o.n + ')') + '</option>';
        }).join('') + '</select></label>';
}

/**
 * One offering card. Structure follows the approved reference while keeping
 * every operational control the seller relies on: image, name, status badge,
 * booked/available quantities, order deadline, delivery time, price, and the
 * View Orders / Edit / Pause-Resume / Sold Out actions.
 *
 * @param p          the ProductDto from the existing dashboard payload
 * @param orderCount real order count for this offering today, or null when the
 *                   app has no figure to show (never a made-up number)
 */
function sdOfferingCardHtml(p, orderCount) {
    var img = sellerImg(p.imageUrl);
    var remaining = p.remainingQuantity;
    var maxQty = p.maxQuantity;
    var h = '<div class="sd-card">';
    h += '<div class="sd-card__photo" data-emoji="' + foodEmoji(p.name) + '">'
        + (img ? '<img src="' + esc(img) + '" alt="' + esc(p.name) + '" onerror="imgFallback(this)">' : foodEmoji(p.name))
        + '</div>';
    h += '<div class="sd-card__body">';
    h += '<div class="sd-card__top"><span class="sd-card__name">' + esc(p.name) + '</span>' + offeringStatusBadge(p) + '</div>';
    h += '<div class="sd-card__qty"><strong>' + (p.bookedQuantity || 0) + ' booked</strong> · '
        + (remaining != null ? '<strong>' + remaining + ' available</strong>' : 'No limit') + '</div>';
    h += '<div class="sd-card__times">'
        + '<span class="sd-card__time">🕐 Orders close <span class="sd-tv">' + esc(sellerOrdersCloseLabel(p)) + '</span></span>'
        + '<span class="sd-card__tdiv">|</span>'
        + '<span class="sd-card__time">🛵 Delivery <span class="sd-tv">' + esc(sellerDeliveryLabel(p)) + '</span></span>'
        + '</div>';
    // Price is real product data (price + unit), never a sample figure.
    if (p.price != null) {
        h += '<div class="sd-card__price">' + money(p.price)
            + (p.priceUnit ? ' <span class="sd-card__unit">/ ' + esc(p.priceUnit) + '</span>' : '') + '</div>';
    }
    // Inline stock adjust stays available for limited-quantity offerings.
    if (maxQty != null && remaining != null && remaining >= 0 && !p.soldOut && !p.ordersPaused) {
        h += '<div class="stepper oc-stepper sd-card__stepper">'
            + '<button type="button" data-action="inv-dec" data-pid="' + p.id + '" aria-label="Decrease available quantity for ' + esc(p.name) + '">−</button>'
            + '<span class="stepper-value" id="inv-' + p.id + '">' + remaining + '</span>'
            + '<button type="button" data-action="inv-inc" data-pid="' + p.id + '" aria-label="Increase available quantity for ' + esc(p.name) + '">+</button></div>';
    }
    h += '<div class="sd-card__actions">';
    // View Orders (N) uses the existing #/order-detail/{productId} route. The
    // count is appended only when the app returned a real one.
    h += '<a class="sd-btn sd-btn--primary" href="#/order-detail/' + p.id + '">View Orders'
        + (orderCount != null ? ' (' + esc(String(orderCount)) + ')' : '') + '</a>';
    h += '<button class="sd-btn sd-btn--edit" type="button" data-action="edit-offering" data-pid="' + p.id + '">Edit</button>';
    // Pause stays reversible and is never conflated with Sold Out.
    if (!p.soldOut && !p.ordersPaused) h += '<button class="sd-btn sd-btn--pause" type="button" data-action="pause-orders" data-pid="' + p.id + '">Pause</button>';
    if (p.ordersPaused && !p.soldOut) h += '<button class="sd-btn sd-btn--resume" type="button" data-action="resume-orders" data-pid="' + p.id + '">Resume</button>';
    if (!p.soldOut && !p.ordersPaused) h += '<button class="sd-btn sd-btn--soldout" type="button" data-action="mark-soldout" data-pid="' + p.id + '">Sold Out</button>';
    h += '</div></div></div>';
    return h;
}

/**
 * Earnings summary. Uses the app's OWN financial definitions and wording
 * (order value by payment status). It deliberately does NOT claim verified
 * bank revenue, because the app records order values rather than settling
 * money itself. "View Details" opens the existing Earnings screen.
 */
function sdEarningsHtml(dash) {
    return '<div class="sd-earnings">'
        + '<div class="sd-earnings__head">'
        + '<div class="sd-earnings__title"><span aria-hidden="true">💰</span> Earnings Summary</div>'
        + '<a class="sd-earnings__more" href="#/earnings">View Details <span aria-hidden="true">→</span></a>'
        + '</div>'
        + '<div class="sd-earnings__grid">'
        + '<div class="sd-earnings__cell"><div class="sd-earnings__label">Confirmed Today</div>'
        + '<div class="sd-earnings__val is-green">' + money(dash.confirmedToday) + '</div></div>'
        + '<div class="sd-earnings__cell"><div class="sd-earnings__label">Pending</div>'
        + '<div class="sd-earnings__val is-amber">' + money(dash.pending) + '</div></div>'
        + '<div class="sd-earnings__cell"><div class="sd-earnings__label">This Month</div>'
        + '<div class="sd-earnings__val">' + money(dash.thisMonth) + '</div></div>'
        + '</div></div>';
}

/** Lightweight skeleton shown while the dashboard payload is in flight. */
function sdSkeletonHtml() {
    function card() {
        return '<div class="sd-skel-card"><div class="sd-skel sd-skel--thumb"></div><div class="sd-skel-lines">'
            + '<div class="sd-skel sd-skel--line w60"></div><div class="sd-skel sd-skel--line w90"></div>'
            + '<div class="sd-skel sd-skel--line w40"></div></div></div>';
    }
    var stats = '';
    for (var i = 0; i < 3; i++) stats += '<div class="sd-skel sd-skel--stat"></div>';
    var quick = '';
    for (var j = 0; j < 5; j++) quick += '<div class="sd-skel sd-skel--qa"></div>';
    return '<div class="sd-root" aria-busy="true" aria-label="Loading your dashboard">'
        + '<div class="sd-stats">' + stats + '</div>'
        + '<div class="sd-skel sd-skel--store"></div>'
        + '<div class="sd-quick">' + quick + '</div>'
        + '<div class="sd-skel sd-skel--line w30 sd-skel--gap"></div>'
        + card() + card()
        + '</div>';
}

/**
 * Real per-offering order counts for today, taken from the EXISTING order
 * summary endpoint the Orders screen already uses.
 *
 * Products with no orders today are simply absent from that payload, so a
 * missing key means "no figure available" rather than zero. The card then shows
 * a plain "View Orders" instead of asserting a misleading "(0)".
 */
async function sellerOfferingOrderCounts() {
    try {
        var summary = await sellerApi('/api/seller-app/orders/summary?date=' + sellerDate('today'));
        var map = {};
        ((summary && summary.products) || []).forEach(function (row) {
            if (row && row.productId != null) map[row.productId] = row.totalOrders || 0;
        });
        return map;
    } catch (e) {
        // Never block the dashboard on this optional count: degrade to no badge.
        return {};
    }
}

async function sellerHomeView() {
    // Paint the skeleton immediately so a slow dashboard never shows a blank
    // screen; sellerRender replaces it with the real markup when we return.
    var live = viewEl();
    if (live) live.innerHTML = sdSkeletonHtml();
    var h = '<div class="view-enter sd-root">';
    // Header: brand on the left, real notification bell + seller avatar right.
    var sellerNameForAvatar = String((S.user && S.user.name) || 'Seller').trim();
    var avatarLetter = sellerNameForAvatar ? sellerNameForAvatar.charAt(0).toUpperCase() : 'S';
    h += '<header class="sd-header">'
        + '<div class="sd-header__brand">'
        + '<span class="sd-header__logo" aria-hidden="true">' + esc(avatarLetter) + '</span>'
        + '<span class="sd-header__word">SocioMart</span>'
        + '</div>'
        + '<div class="sd-header__right">'
        + '<span class="notif-bell">' + notificationBadgeHtml() + notificationPanelHtml('sellerNotifPanel') + '</span>'
        + '<button class="sd-header__avatar" type="button" data-action="toggle-theme"'
        + ' aria-label="Switch between light and dark theme" title="Switch theme">'
        + esc(avatarLetter) + '</button>'
        + '</div></header>';
    try {
        // Both requests hit endpoints that already exist and are already used
        // elsewhere in the Seller app, so no new contract is introduced.
        var kitchen = null;
        var kitchenErr = false;
        try { kitchen = await sellerApi('/api/seller/kitchen'); S.myKitchen = kitchen; }
        catch (e) { kitchenErr = true; }
        var dash = await sellerApi('/api/seller-app/dashboard');
        var orderCounts = await sellerOfferingOrderCounts();
        S.kitchen = { id: dash.kitchenId, name: dash.kitchenName };

        // Greeting: the seller's real first name and the real time of day.
        h += '<div class="sd-greet">'
            + '<h1 class="sd-greet__hi">' + greeting() + ', ' + esc(sellerFirstName(dash)) + ' <span aria-hidden="true">👋</span></h1>'
            + '<p class="sd-greet__sub">Your SocioMart Dashboard</p></div>';

        h += sdStatCardsHtml(dash);
        h += sdStoreCardHtml(dash, kitchen);
        h += sdQuickActionsHtml();
        if (kitchenErr) {
            // Non-destructive: the store card still renders from the dashboard
            // payload, we just say the photo/status could not be refreshed.
            h += '<p class="sd-note">Store details could not be refreshed. '
                + '<button class="sd-note__retry" type="button" data-action="seller-retry">Retry</button></p>';
        }
        var offerings = dash.offerings || [];
        h += '<div class="sd-section">'
            + '<h2 class="sd-section__title">My Offerings<span class="sd-section__count">(' + offerings.length + ')</span></h2>'
            + sdOfferingFilterHtml(offerings) + '</div>';
        if (!offerings.length) {
            // Empty-state copy is the approved Requirement 17/18 wording, kept
            // verbatim: the redesign changes how this looks, not what it says.
            h += emptyHtml('🍽️', 'No Offerings yet',
                'Nothing on sale right now. Create your first offering and start taking orders.',
                '<a class="btn btn-primary card-mt" href="#/create">+ Create Offering</a>');
        } else {
            var filter = S.dashFilter || 'ALL';
            var shown = offerings.filter(function (p) {
                return filter === 'ALL' || sdCategoryGroup(p) === filter;
            });
            if (!shown.length) {
                h += emptyHtml('🍽️', 'Nothing in this filter', 'Choose "All" to see every offering.');
            } else {
                shown.forEach(function (p) {
                    h += sdOfferingCardHtml(p, orderCounts[p.id] != null ? orderCounts[p.id] : null);
                });
            }
        }
        // Large orange CTA -> the EXISTING create-offering entry point.
        h += '<button class="sd-add" type="button" data-action="go-add"><span aria-hidden="true">＋</span> Add Offering</button>';
        h += sdEarningsHtml(dash);
    } catch (e) {
        // Honest failure state: say what broke and offer a retry. Never render
        // a partial success as if the action had worked.
        h += emptyHtml('⚠️', 'Could not load dashboard', e.message || 'Please try again.',
            '<button class="btn btn-primary card-mt" type="button" data-action="seller-retry">Retry</button>');
    }
    h += '</div>';
    return h;
}

// SCREEN 5: MY OFFERINGS (formerly "History")
async function sellerHistoryView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>My Offerings</h1>' +
        '<p class="muted small">Create a new offering, or revisit the ones you have already run.</p></div>';
    // Primary action sits above the history so it is reachable without scrolling.
    // It reuses the existing Add Offering screen (#/add -> the same create flow).
    // Plain anchor on the existing #/add route: navigating by hash means the SPA
    // router handles it exactly like every other in-app link (a data-action here
    // would also fire sellerNavigate and render the view twice).
    h += '<a class="btn btn-primary btn-block btn-mt-sm" href="#/add">+ Add Offering</a>';
    h += '<div class="section-head"><h2>Offering History</h2></div>';
    h += '<p class="muted small mb-2">Previous offerings listed here...</p>';
    try {
        var items = await sellerApi('/api/seller-app/history');
        S.historyItems = items || [];
        // Render the normalised list rather than the raw response: a null body would
        // otherwise throw here and be reported as "could not load history".
        if (S.historyItems.length === 0) {
            h += emptyHtml('🕘', 'No previous items', 'Expired offerings for your kitchen will appear here.');
        } else {
            S.historyItems.forEach(function (p) {
                var offeringDate = p.availableDate ? prettyDate(p.availableDate) : 'Previous offering';
                h += '<div class="history-card"><span class="hc-body"><span class="hc-name">' + esc(p.name) + '</span>' +
                    '<span class="hc-meta">' + esc(offeringDate) + ' · ' + esc(p.category || 'Uncategorised') + '</span></span>' +
                    '<span class="hc-price">' + money(p.price) + '</span>' +
                    '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="republish-history" data-pid="' + esc(p.id) + '">Republish</button></div>';
            });
        }
    } catch (e) { h += emptyHtml('⚠️', 'Could not load history', e.message); }
    h += '</div>';
    return h;
}

// SCREEN 4: QUICK POST — Today only

/**
 * Maximum Quick Post text length.
 *
 * <p>Mirrors the authoritative server-side check in
 * {@code SellerAppService.createQuickPost} ("Quick Post message is too long"),
 * so the browser never advertises a different maximum than the API accepts.
 * The existing offering content columns are untyped TEXT, so there is no other
 * content limit to inherit and this field's own limit is reused rather than
 * adding a second, competing number.
 */
var QUICK_POST_MAX = 5000;

/**
 * Live character-count feedback for the Quick Post textarea.
 *
 * <p>{@code maxlength} is deliberately NOT set on the textarea: it would
 * silently discard the tail of a pasted message, and losing a seller's text
 * without telling them is exactly the failure this screen must not have. The
 * counter turns red, names the overflow in characters, and blocks submission
 * instead, so the seller decides what to cut.</p>
 */
function updateQuickPostCounter() {
    var ta = document.getElementById('qpMessage');
    var out = document.getElementById('qpCounter');
    if (!ta || !out) return;
    var len = ta.value.length;
    var over = len - QUICK_POST_MAX;
    out.textContent = over > 0
        ? over + ' character' + (over === 1 ? '' : 's') + ' over the ' + QUICK_POST_MAX + ' character limit'
        : (QUICK_POST_MAX - len) + ' of ' + QUICK_POST_MAX + ' characters left';
    out.classList.toggle('over', over > 0);
    ta.setAttribute('aria-invalid', over > 0 ? 'true' : 'false');
    var btn = document.getElementById('qpSubmit');
    if (btn) btn.disabled = over > 0;
}

async function sellerQuickPostView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Quick Post</h1><p class="muted small">Paste your WhatsApp message. Quick Posts are always published for Today.</p></div>';
    h += '<div class="card pad card-purple card-mb"><div class="font-700">This becomes a real offering</div>' +
        '<p class="small mt-1">Your post is published as an offering buyers can order from, using the same ' +
        'price, timing and order window as Create Offering.</p></div>';
    h += '<form class="seller-form" id="quickPostForm">';
    h += '<div class="form-group"><label class="form-label" for="qpMessage">Paste WhatsApp message <span class="req">*</span></label>' +
        '<textarea class="form-textarea" id="qpMessage" name="message" rows="6" maxlength="' + (QUICK_POST_MAX + 1) + '" ' +
        'aria-describedby="qpCounter" placeholder="Paste your WhatsApp message here..." required></textarea>' +
        '<div class="qp-counter-row"><span id="qpCounter" class="qp-counter" role="status" aria-live="polite">' +
        QUICK_POST_MAX + ' of ' + QUICK_POST_MAX + ' characters left</span></div></div>';
    // Minimum extra fields required by the existing Product creation architecture.
    // These reuse the exact Create Offering controls, names and help text so the
    // two screens stay consistent instead of inventing a parallel form.
    h += '<div class="form-group"><label class="form-label">Item Name <span class="req">*</span></label>' +
        '<input class="form-input" name="name" placeholder="e.g. Paneer Butter Masala" required></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Price (Rs) <span class="req">*</span></label>' +
        '<input class="form-input" name="price" type="number" min="0.01" step="0.01" placeholder="100" required></div>' +
        '<div class="form-group"><label class="form-label">Unit <span class="req">*</span></label>' +
        '<select class="form-select" name="priceUnit"><option value="Per Piece">Per Piece</option>' +
        '<option value="Per Plate">Per Plate</option><option value="Per Box">Per Box</option></select></div></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Orders Open — Optional</label>' +
        '<input class="form-input" name="orderWindowStart" type="time">' +
        '<p class="muted small">Leave blank to start accepting orders immediately.</p></div>' +
        '<div class="form-group"><label class="form-label">Orders Close <span class="req">*</span></label>' +
        '<input class="form-input" name="orderWindowEnd" type="time" required>' +
        '<p class="muted small">Last date/time customers can place an order.</p></div></div>';
    h += '<div class="form-group"><label class="form-label">Delivery / Ready By <span class="req">*</span></label>' +
        '<input class="form-input" name="readyByTime" type="datetime-local" required>' +
        '<p class="muted small">Date/time by which the order will be ready/delivered.</p></div>';
    h += '<div class="form-group"><label class="form-label">Quantity Available — Optional</label>' +
        '<input class="form-input" name="maxQuantity" type="number" min="1" step="1" placeholder="Blank for unlimited">' +
        '<p class="muted small">Leave blank for unlimited quantity.</p></div>';
    h += '<div class="form-group"><label class="form-label">To be listed in <span class="req">*</span></label>' +
        '<div class="checkbox-group">' + offeringCategoryBox('BREAKFAST', 'Breakfast', []) +
        offeringCategoryBox('LUNCH', 'Lunch', []) + offeringCategoryBox('DINNER', 'Dinner', []) +
        offeringCategoryBox('SNACKS', 'Snacks', []) + '</div>' +
        '<p class="muted small">Select at least one category.</p></div>';
    h += '<button class="btn btn-primary btn-block" type="submit" id="qpSubmit">Publish Offering</button>';
    h += '</form>';
    // Legacy announcements already stored in quick_posts remain listed so nothing
    // that exists today disappears. NEW Quick Posts are ordinary offerings and
    // no longer create announcement rows.
    try {
        var posts = await sellerApi('/api/seller-app/quick-posts');
        if (posts && posts.length) {
            h += '<h3 class="section-gap mb-2">Earlier announcements</h3>';
            posts.forEach(function (p) { h += '<div class="card pad card-mb"><div class="font-700">' + esc(p.message) + '</div>' + (p.imageData ? '<img class="mt-2" style="max-width:100%;border-radius:8px" src="' + esc(p.imageData) + '" alt="Quick Post image">' : '') + '</div>'; });
        }
    } catch (e) { /* form remains available if list refresh fails */ }
    h += '</div>';
    return h;
}

// ==================== Offering form (shared by Create + Edit) ====================

/** One "Offering For" choice; a locked offering renders it as non-interactive text. */
function availabilityOption(val, label, mode, locked) {
    return '<label class="radio-option' + (mode === val ? ' selected' : '') + '"' +
        (locked ? ' aria-disabled="true"' : ' data-action="set-availability" data-val="' + val + '"') + '>' + label + '</label>';
}
/** One category checkbox, pre-checked from the stored category list. */
function offeringCategoryBox(value, label, selected) {
    return '<label class="checkbox-option"><input type="checkbox" name="categories" value="' + value + '"' +
        (selected.indexOf(value) >= 0 ? ' checked' : '') + '> ' + label + '</label>';
}
/** Cross-field timing rules shared by Create and Edit; returns an error text or null. */
function offeringTimingError(availableDate, openHhmm, closeHhmm, readyBy) {
    if (openHhmm && closeHhmm && openHhmm >= closeHhmm) return 'Orders Open must be before Orders Close';
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(readyBy)) return 'Delivery / Ready By must be a valid date and time';
    var readyDate = readyBy.slice(0, 10);
    if (readyDate < availableDate) return 'Delivery / Ready By cannot be before the offering date';
    var orderingDate = availableDate;
    if (availableDate > sellerDate('today')) {
        var orderDate = new Date(availableDate + 'T00:00:00');
        orderDate.setDate(orderDate.getDate() - 1);
        orderingDate = localDateStr(orderDate);
    }
    if (orderingDate + 'T' + closeHhmm + ':00' > readyBy) return 'Orders Close must be before Delivery / Ready By';
    return null;
}
/** Which "Offering For" mode matches a stored offering date (Edit screen). */
function offeringForMode(isoDate) {
    if (!isoDate || isoDate === sellerDate('today')) return 'today';
    if (isoDate === sellerDate('tomorrow')) return 'tomorrow';
    return 'choose';
}

/**
 * The offering form markup, shared by Create Offering (opts.edit false) and Edit
 * Offering (Requirement 5). With `opts.locked` the fields customers already
 * agreed to stay visible but can no longer be changed.
 */
function offeringFormHtml(t, opts) {
    opts = opts || {};
    t = t || {};
    var isEdit = !!opts.edit;
    var locked = !!opts.locked;
    var mode = opts.mode || 'today';
    var lockAttr = locked ? ' disabled' : '';
    var todayIso = sellerDate('today');
    var chosenDate = isEdit ? (t.availableDate || todayIso) : todayIso;
    var selectedUnit = t.priceUnit || 'Per Piece';
    var categoryValues = String(t.category || '').split(',').map(function (c) { return c.trim().toUpperCase(); }).filter(Boolean);
    var h = '<form class="seller-form" id="' + (opts.formId || 'createOfferingForm') + '">';
    h += '<div class="form-group"><label class="form-label">Photos <span class="req">*</span></label><div class="photo-upload-row"><div class="photo-tile" data-action="add-photo">' + (isEdit && sellerImg(t.imageUrl) ? '<img src="' + esc(sellerImg(t.imageUrl)) + '" alt="' + esc(t.name) + '" onerror="imgFallback(this)">' : '+') + '</div></div></div>';
    h += '<div class="form-group"><label class="form-label">Item Name <span class="req">*</span></label><input class="form-input" name="name" value="' + esc(t.name || '') + '" placeholder="e.g. POHA" required' + lockAttr + '></div>';
    h += '<div class="form-group"><label class="form-label">Short Description</label><textarea class="form-textarea" name="description">' + esc(t.description || '') + '</textarea></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Price (Rs) <span class="req">*</span></label><input class="form-input" name="price" type="number" value="' + (t.price || '') + '" placeholder="100" required' + lockAttr + '></div>';
    h += '<div class="form-group"><label class="form-label">Unit <span class="req">*</span></label><select class="form-select" name="priceUnit"' + lockAttr + '><option value="Per Piece"' + (selectedUnit === 'Per Piece' ? ' selected' : '') + '>Per Piece</option><option value="Per Plate"' + (selectedUnit === 'Per Plate' ? ' selected' : '') + '>Per Plate</option><option value="Per Box"' + (selectedUnit === 'Per Box' ? ' selected' : '') + '>Per Box</option></select></div></div>';
    h += '<input type="hidden" name="imageUrl" value="' + esc(t.imageUrl || '') + '">';
    h += '<div class="form-group"><label class="form-label">Offering For <span class="req">*</span></label><div class="radio-group">' + availabilityOption('today', 'Today', mode, locked) + availabilityOption('tomorrow', 'Tomorrow', mode, locked) + availabilityOption('choose', 'Choose Date', mode, locked) + '</div></div>';
    h += '<input type="hidden" name="availableDate" id="availDate" value="' + esc(chosenDate) + '">';
    h += '<div class="form-group" id="chooseDateRow"' + (mode === 'choose' ? '' : ' hidden') + '><label class="form-label">Offering Date <span class="req">*</span></label><input type="date" class="form-input" name="chosenOfferingDate" min="' + todayIso + '" data-action="set-availability-date" value="' + esc(chosenDate) + '"' + lockAttr + '></div>';

    var openValue = isEdit ? (t.orderWindowStart || '') : '';
    var closeValue = isEdit ? (t.orderWindowEnd || '') : '';
    var closeNote = locked
        ? 'Has orders — can only be extended (currently ' + prettyTime(t.orderWindowEnd) + ').'
        : 'Last date/time customers can place an order.';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Orders Open — Optional</label><input class="form-input" name="orderWindowStart" type="time" value="' + esc(openValue) + '"' + lockAttr + '><p class="muted small">Leave blank to start accepting orders immediately.</p></div>';
    h += '<div class="form-group"><label class="form-label">Orders Close <span class="req">*</span></label><input class="form-input" name="orderWindowEnd" type="time" required value="' + esc(closeValue) + '"><p class="muted small">' + esc(closeNote) + '</p></div></div>';
    var readyIso = !!(t.readyByTime && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(t.readyByTime));
    var readyNote = 'Date/time by which the order will be ready/delivered.';
    if (locked) {
        var readyDisplay = t.readyByTime
            ? (readyIso ? prettyDate(t.readyByTime.slice(0, 10)) + ', ' + prettyTime(t.readyByTime.slice(11, 16)) : t.readyByTime)
            : '--';
        h += '<div class="form-group"><label class="form-label">Delivery / Ready By <span class="req">*</span></label><input class="form-input" name="readyByTimeLocked" type="text" value="' + esc(readyDisplay) + '" disabled><p class="muted small">' + esc(readyNote) + '</p></div>';
    } else {
        if (isEdit && t.readyByTime && !readyIso) readyNote = 'Saved value: ' + t.readyByTime + '. Pick a new date/time to replace it.';
        h += '<div class="form-group"><label class="form-label">Delivery / Ready By <span class="req">*</span></label><input class="form-input" name="readyByTime" type="datetime-local"' + (isEdit ? '' : ' required') + ' value="' + esc(readyIso ? t.readyByTime : '') + '"><p class="muted small">' + esc(readyNote) + '</p></div>';
    }
    var qtyValue = '', qtyAttr = '', qtyNote = 'Leave blank for unlimited quantity.';
    if (isEdit) {
        if (t.maxQuantity == null) {
            qtyAttr = ' disabled';
            qtyNote = 'This offering has no quantity limit.';
        } else {
            qtyValue = String(t.remainingQuantity == null ? 0 : t.remainingQuantity);
            qtyNote = 'Plates buyers can still order (booked so far: ' + (t.bookedQuantity || 0) + ').';
        }
    } else if (t.maxQuantity != null) {
        qtyValue = String(t.maxQuantity);
    }
    h += '<div class="form-group"><label class="form-label">' + (isEdit ? 'Quantity Available Now' : 'Quantity Available — Optional') + '</label><input class="form-input" name="' + (isEdit ? 'availableQuantity' : 'maxQuantity') + '" type="number" min="' + (isEdit ? '0' : '1') + '" step="1" value="' + esc(qtyValue) + '" placeholder="' + (isEdit ? 'Unlimited' : 'Blank for unlimited') + '"' + qtyAttr + '><p class="muted small">' + esc(qtyNote) + '</p></div>';
    h += '<div class="form-group"><label class="form-label">To be listed in <span class="req">*</span></label><div class="checkbox-group">' + offeringCategoryBox('BREAKFAST', 'Breakfast', categoryValues) + offeringCategoryBox('LUNCH', 'Lunch', categoryValues) + offeringCategoryBox('DINNER', 'Dinner', categoryValues) + offeringCategoryBox('SNACKS', 'Snacks', categoryValues) + '</div><p class="muted small">Select at least one category.</p></div>';
    if (!isEdit) {
        h += '<div class="toggle-row"><div><div class="toggle-text">Mark as Favourite</div><div class="toggle-note">Save as template (max 3).</div></div><div class="toggle-switch" id="favToggle" data-action="toggle-favourite"></div></div>';
    }
    h += '<button class="btn btn-primary btn-block" type="submit">' + (isEdit ? 'Save Changes' : 'Publish Offering') + '</button></form>';
    return h;
}

// SCREEN 3: CREATE OFFERING (MANUAL FORM)
async function sellerCreateView() {
    // A new form always starts in Today mode; never inherit a previous choice.
    S.offeringFor = 'today';
    var t = S.draftOffering || {};
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Create Offering</h1><p class="muted small">' +
        (S.republishSourceId ? 'Review the previous offering details, then set fresh timing and quantity.' : 'Fill in the details for your new dish.') + '</p></div>';
    h += offeringFormHtml(t, { formId: 'createOfferingForm', mode: 'today' });
    h += '</div>';
    return h;
}

// SCREEN 3B: EDIT OFFERING (Requirement 5 — edit rules for a live offering)
async function sellerEditOfferingView() {
    var o = S.editOffering;
    if (!o || !o.id) {
        return '<div class="view-enter">' + emptyHtml('🍽️', 'Offering not found', 'Open an offering from your dashboard and tap Edit again.') + '</div>';
    }
    var locked = !!o.hasOrders;
    // Pre-select the Offering For choice that matches the stored date.
    S.offeringFor = offeringForMode(o.availableDate);
    var h = '<div class="view-enter">';
    h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button><h2 class="flex-1">Edit Offering</h2></div>';
    if (locked) {
        h += '<div class="info-box">⚠️ This offering already has orders. Some details cannot be changed.</div>';
        h += '<p class="muted small">Name, price, unit, offering date, Orders Open and Delivery / Ready By are frozen so existing customers keep what they agreed to. Orders Close can only be extended.';
        if (o.orderCount) {
            h += ' ' + o.orderCount + ' order' + (o.orderCount === 1 ? '' : 's') + ' so far';
            if (o.bookedQuantity) h += ' · ' + o.bookedQuantity + ' plate' + (o.bookedQuantity === 1 ? '' : 's') + ' booked';
            h += '.';
        }
        h += '</p>';
    } else {
        h += '<p class="muted small">No orders yet — every detail stays editable until the first order arrives.</p>';
    }
    if (o.soldOut) h += '<div class="info-box">🔴 Sold out. Raise the quantity available to restock it.</div>';
    h += offeringFormHtml(o, { formId: 'editOfferingForm', edit: true, locked: locked, mode: S.offeringFor });
    h += '</div>';
    return h;
}

// SCREEN 7A: ORDER SUMMARY
async function sellerOrdersView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Orders</h1></div>';
    h += '<div class="date-tabs"><button class="date-tab' + (S.selectedDate === 'today' ? ' active' : '') + '" data-action="set-date" data-date="today">Today</button><button class="date-tab' + (S.selectedDate === 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="tomorrow">Tomorrow</button><button class="date-tab' + (S.selectedDate !== 'today' && S.selectedDate !== 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="pick">Pick date</button></div>';
    try {
        var summary = await sellerApi('/api/seller-app/orders/summary?date=' + sellerDate(S.selectedDate));
        var hasOrders = summary.totalOrderCount > 0;
        if (hasOrders) {
            h += '<div class="daily-total-card"><div class="dtc-number">' + summary.totalOrderCount + '</div><div class="dtc-label">Total Orders</div>';
            h += '<div class="dtc-badges"><span class="dtc-badge green">✓ ' + summary.paidCount + ' Paid</span><span class="dtc-badge orange">⏳ ' + summary.pendingCount + ' Pending</span><span class="dtc-badge red">✕ ' + summary.cancelledCount + ' Cancelled</span></div>';
            if (summary.totalRevenue) h += '<div class="tiny muted mt-2">Revenue: <strong class="text-brand">' + money(summary.totalRevenue) + '</strong></div>';
            h += '</div>';
        } else {
            h += emptyHtml('📋', 'No Orders', 'No orders yet. New orders will appear here.');
        }
        if (!summary.products || summary.products.length === 0) {
            if (hasOrders) h += emptyHtml('📋', 'No order items', 'No order items exist for this date.');
        } else {
            summary.products.forEach(function (p) {
                h += '<div class="order-product-card"><div class="opc-header"><span class="opc-name">' + esc(p.productName) + '</span><span class="opc-revenue">' + money(p.revenue) + '</span></div>';
                h += '<div class="opc-meta">' + p.totalOrders + ' orders · ' + p.totalPlates + ' plates</div>';
                h += '<div class="opc-meta"><span class="dot-green">●</span> ' + p.paidCount + ' paid · <span class="dot-orange">●</span> ' + p.pendingCount + ' pending</div>';
                 h += '<a class="btn btn-secondary btn-sm btn-block btn-mt-sm" href="#/order-detail/' + p.productId + '">View Orders</a></div>';
            });
        }
    } catch (e) { h += emptyHtml('⚠️', 'Could not load orders', e.message); }
    h += '</div>';
    return h;
}

// SCREEN 6: MANAGE KITCHEN
async function sellerKitchenView() {
    var kitchen = null;
    try { kitchen = await sellerApi('/api/seller/kitchen'); S.myKitchen = kitchen; S.kitchen = kitchen; } catch (e) { }
    // Service-area coverage is chosen from the Admin-owned Area/Society master and
    // submitted as Society IDs. Only active records are offered, and the previous
    // coverage is restored from the kitchen's authoritative ID set - never re-derived
    // from the display string.
    var coverageOptions = [];
    var coverageError = false;
    try { coverageOptions = await sellerApi('/api/seller/coverage-options') || []; } catch (e) { coverageError = true; }
    var coveredIds = (kitchen && kitchen.servedSocietyIds) ? kitchen.servedSocietyIds.map(Number) : [];
    // Preselect the Area that owns the current coverage so an edit does not silently
    // move the kitchen to a different area.
    var preselectedAreaId = '';
    coverageOptions.forEach(function (a) {
        (a.societies || []).forEach(function (s) {
            if (coveredIds.indexOf(Number(s.id)) !== -1 && !preselectedAreaId) preselectedAreaId = String(a.id);
        });
    });
    S.coverageOptions = coverageOptions;
    S.coverageAreaId = preselectedAreaId;
    S.coverageIds = coveredIds;
    // The restored Area is also the "already loaded" Area, so the first render takes
    // the restore branch and keeps the persisted IDs. Without this the initial render
    // looks like an Area change and would preselect every active Society - silently
    // covering one the Admin added after this kitchen was saved. Preselect-all is
    // reserved for a real Area change (see the change handler, which clears this).
    S.coverageLoadedAreaId = preselectedAreaId;
    var areaOptions = coverageOptions.map(function (a) {
        return '<option value="' + esc(a.id) + '"' + (String(a.id) === preselectedAreaId ? ' selected' : '') + '>' + esc(a.name) + '</option>';
    }).join('');
    var paused = !!(kitchen && kitchen.paused);
    // Kitchen Name remains the primary heading; the owner name is shown beneath it,
    // sourced from the seller's own profile and omitted when absent.
    var kitchenSellerLine = (kitchen && kitchen.sellerName && String(kitchen.sellerName).trim())
        ? '<p class="ki-seller muted small">Seller: ' + esc(String(kitchen.sellerName).trim()) + '</p>' : '';
    var h = '<div class="view-enter"><div class="page-head"><h1>Manage Kitchen</h1>' + kitchenSellerLine + '</div>';
    h += '<div class="kitchen-status-badge' + (paused ? ' paused' : '') + '">' + (paused ? 'Kitchen PAUSED' : 'Kitchen Published') + '</div>';
    h += paused
        ? '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="resume-kitchen">Resume Kitchen</button>'
        : '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="pause-kitchen">Pause Kitchen</button>';
    h += '<button class="btn btn-secondary btn-sm btn-block btn-mt-sm" type="button" data-action="preview-kitchen">Preview Kitchen Page</button>';
    h += '<form class="seller-form" id="kitchenForm">';
    h += '<div class="kitchen-avatar-upload"><div class="kitchen-avatar" data-action="upload-avatar" role="button" tabindex="0" aria-label="Upload kitchen photo">' + (kitchen && sellerImg(kitchen.imageUrl) ? '<img src="' + esc(sellerImg(kitchen.imageUrl)) + '" class="avatar-img" alt="Kitchen photo" onerror="imgFallback(this)">' : '📷') + '</div></div>';
    h += '<div class="form-group"><label class="form-label">Kitchen Name</label><input class="form-input" name="displayName" value="' + esc(kitchen && kitchen.displayName ? kitchen.displayName : 'Aarti Kitchen') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Who can order from me? (Service Areas)</label>';
    h += '<div class="muted small" style="margin-bottom:6px">Select the area you deliver to, then tick the societies inside it. Buyers outside your selected societies cannot discover or order from your kitchen.</div>';
    if (coverageError) {
        h += '<div class="muted small" style="margin-bottom:6px">Could not load the area list. Please retry.</div>';
    } else if (!coverageOptions.length) {
        h += '<div class="muted small" style="margin-bottom:6px">No areas are available yet. Ask an Admin to add an area and its societies.</div>';
    }
    h += '<div class="form-row-2" style="margin-top:8px"><select class="form-input" id="coverageAreaSelect" data-action="select-coverage-area" aria-label="Service area"' + (coverageOptions.length ? '' : ' disabled') + '><option value="">Select area</option>' + areaOptions + '</select></div>';
    h += '<div id="coverageSocietyList" style="margin-top:8px"></div>';
    h += '<input type="hidden" name="areaId" id="coverageAreaIdInput" value="' + esc(preselectedAreaId) + '">';
    h += '<input type="hidden" name="societyIds" id="coverageSocietyIdsInput" value="' + esc(coveredIds.join(',')) + '">';
    h += '</div>';
    h += '<div class="form-group"><label class="form-label">Speciality</label><input class="form-input" name="shortDescription" value="' + esc(kitchen && kitchen.shortDescription ? kitchen.shortDescription : 'Homemade Maharashtrian Food') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Full Description</label><textarea class="form-textarea" name="description">' + esc(kitchen && kitchen.description ? kitchen.description : 'Fresh homemade breakfast and traditional snacks') + '</textarea></div>';
    h += '<div class="form-group"><label class="form-label">Gallery Images (URLs, comma-separated)</label><input class="form-input" name="galleryImages" value="' + esc(kitchen && kitchen.galleryImages ? kitchen.galleryImages : '') + '"></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">WhatsApp</label><input class="form-input" name="whatsappLink" value="' + esc(kitchen && kitchen.whatsappLink ? kitchen.whatsappLink : '+91 9100000001') + '"></div><div class="form-group"><label class="form-label">Instagram</label><input class="form-input" name="instagramLink" value="' + esc(kitchen && kitchen.instagramLink ? kitchen.instagramLink : '@aartiskitchen') + '"></div></div>';
    h += '<div class="form-group"><label class="form-label">UPI ID</label><input class="form-input" name="upiId" value="' + esc(kitchen && kitchen.upiId ? kitchen.upiId : 'aarti@okhdfc') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Store Type</label><div class="radio-group"><label class="radio-option' + (kitchen && kitchen.sellerType === 'HOMEMADE_PRODUCTS' ? '' : ' selected') + '" data-action="set-seller-type" data-val="KITCHEN">🍽️ Kitchen / Food Seller</label><label class="radio-option' + (kitchen && kitchen.sellerType === 'HOMEMADE_PRODUCTS' ? ' selected' : '') + '" data-action="set-seller-type" data-val="HOMEMADE_PRODUCTS">🍰 Homemade Products</label></div><input type="hidden" name="sellerType" id="sellerTypeInput" value="' + esc(kitchen && kitchen.sellerType ? kitchen.sellerType : 'KITCHEN') + '"></div>';
    h += '<div class="info-box">Your menu loads automatically from live offerings.</div>';
    h += '<button class="btn btn-primary btn-block" type="submit">SAVE CHANGES</button></form></div>';
    return h;
}

// SCREEN 8: EARNINGS
async function sellerEarningsView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Earnings</h1></div>';
    try {
        var e = await sellerApi('/api/seller-app/earnings');
        if (!e.hasEarnings) {
            h += emptyHtml('💰', 'No Earnings', 'No earnings to show yet');
            if (e.pending != null && Number(e.pending) !== 0) {
                h += '<div class="earnings-header-card"><div class="ehc-label">PENDING PAYMENTS</div><div class="ehc-main">' + money(e.pending) + '</div></div>';
            }
        } else {
            h += '<div class="earnings-header-card"><div class="ehc-label">CONFIRMED TODAY</div><div class="ehc-main">' + money(e.confirmedToday) + '</div>';
            h += '<div class="ehc-row"><div class="ehc-item"><div class="ehc-val orange">' + money(e.pending) + '</div><div class="ehc-sub">PENDING</div></div><div class="ehc-item"><div class="ehc-val">' + money(e.thisMonth) + '</div><div class="ehc-sub">THIS MONTH</div></div></div></div>';
            if (e.items && e.items.length) e.items.forEach(function (item) { h += '<div class="earning-item"><span class="ei-icon">🍽️</span><span class="ei-body"><span class="ei-name">' + esc(item.productName) + '</span><span class="ei-orders">' + item.totalOrders + ' orders</span></span><span class="ei-revenue"><span class="ei-confirmed">' + money(item.confirmedRevenue) + '</span><br><span class="ei-pending">' + money(item.pendingRevenue) + '</span></span></div>'; });
        }
        h += '<a class="btn btn-secondary btn-block" href="#/my-offerings">VIEW FULL HISTORY</a>';
    } catch (err) { h += emptyHtml('⚠️', 'Could not load earnings', err.message); }
    h += '</div>';
    return h;
}

// ==================== Screen: Seller Enquiries ====================

async function sellerEnquiriesView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Enquiries</h1></div>';
    try {
        var enquiries = await api('/api/enquiries/seller/my');
        if (!enquiries || !enquiries.length) {
            h += emptyHtml('✉️', 'No enquiries yet', 'When buyers send enquiries, they will appear here.');
        } else {
            enquiries.forEach(function (enq) {
                var statusClass = enq.status === 'NEW' ? 'pill-amber' : enq.status === 'CONTACTED' ? 'pill-green' : 'pill';
                var ackBtn = enq.acknowledgedAt ? '' : '<button class="btn btn-primary btn-sm" data-action="ack-enquiry" data-id="' + enq.id + '">Acknowledge</button>';
                var closeBtn = enq.status !== 'CLOSED' ? '<button class="btn btn-secondary btn-sm" data-action="close-enquiry" data-id="' + enq.id + '">Close</button>' : '';
                h += '<div class="card pad card-mb">' +
                    '<div class="top-row mb-2"><div class="flex-1"><div class="font-700">' + esc(enq.kitchenName) + '</div>' +
                    '<div class="muted small">' + esc(enq.message) + '</div>' +
                    (enq.preferredDate ? '<div class="muted tiny">Preferred: ' + esc(enq.preferredDate) + '</div>' : '') +
                    (enq.quantity ? '<div class="muted tiny">Quantity: ' + esc(enq.quantity) + '</div>' : '') +
                    '</div>' +
                    '<span class="pill ' + statusClass + '">' + enq.status + '</span></div>' +
                    '<div class="tiny muted">' + prettyDate(enq.createdAt) + (enq.acknowledgedAt ? ' · Acknowledged' : ' · Unacknowledged') + '</div>' +
                    '<div class="mt-2">' + ackBtn + ' ' + closeBtn + '</div></div>';
            });
        }
    } catch (err) { h += emptyHtml('⚠️', 'Could not load enquiries', err.message); }
    h += '</div>';
    return h;
}

// SCREEN 7B: OFFERING ORDERS (summary-first with filters)
//
// This view returns an HTML STRING. sellerRender() only assigns that string to
// view.innerHTML after this function resolves, so nothing here may touch the
// DOM: an earlier version did (it set #offeringName and called
// renderOfferingCustomers on an element that did not exist yet), which left the
// customer list permanently empty. Everything is now built into the string.

/**
 * Drill-down URL for ONE offering: date plus the three independent filters
 * (society, payment, delivery).
 *
 * <p>Kept in one place so the initial render and the delivery block's later
 * refresh read exactly the same subset - otherwise the progress line could
 * describe a different view than the rows under it.</p>
 */
function offeringDetailUrl(productId) {
    return '/api/seller-app/orders/product/' + productId +
        '?date=' + sellerDate(S.selectedDate) +
        (S.offeringFilterSociety ? '&society=' + encodeURIComponent(S.offeringFilterSociety) : '') +
        (S.offeringFilterStatus ? '&status=' + encodeURIComponent(S.offeringFilterStatus) : '') +
        (S.offeringFilterDelivery ? '&delivery=' + encodeURIComponent(S.offeringFilterDelivery) : '');
}

async function sellerOrderDetailView(productId) {
    S.offeringFilterSociety = S.offeringFilterSociety || '';
    S.offeringFilterStatus = S.offeringFilterStatus || '';
    S.offeringFilterDelivery = S.offeringFilterDelivery || '';
    // Remembered so the delivery block can repaint ITSELF from the server after a
    // checkbox toggle without re-rendering (and possibly disturbing) the rows.
    S.offeringProductId = productId;
    var h = '<div class="view-enter">';
    try {
        var detail = await sellerApi(offeringDetailUrl(productId));
        // Options come from the UNFILTERED society list for this offering/date, so
        // the dropdown can never collapse to the currently selected society and
        // switching between societies works repeatedly (also for status = non-All).
        var societies = [];
        var societyPool = detail.availableSocieties || [];
        if (societyPool.length) {
            societyPool.forEach(function (s) { if (s && societies.indexOf(s) === -1) societies.push(s); });
        } else {
            (detail.customers || []).forEach(function (c) { if (c.society && societies.indexOf(c.society) === -1) societies.push(c.society); });
        }
        if (S.offeringFilterSociety && societies.indexOf(S.offeringFilterSociety) === -1) {
            // The selected society has no orders for this offering on this date
            // any more (e.g. the date changed): clear it and reload once so the
            // rows, the "Showing N of M" line and the control stay in agreement.
            S.offeringFilterSociety = '';
            return sellerOrderDetailView(productId);
        }
        var pname = detail.productName || 'Offering';
        var kitchenName = S.kitchen && S.kitchen.name ? S.kitchen.name : '';
        var orderCount = detail.totalOrders || 0;
        var orderWord = orderCount === 1 ? 'order' : 'orders';

        h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<h2 class="flex-1">Order Summary</h2></div>';
        if (kitchenName) h += '<p class="muted small text-sm card-mb">🏪 ' + esc(kitchenName) + '</p>';

        // Headline: N orders • X plates • ₹Y - all from persisted order data.
        h += '<div class="drilldown-header"><h3>' + esc(pname) + '</h3>';
        h += '<div class="dd-stats">' + orderCount + ' ' + orderWord + ' • ' +
            (detail.totalPlates || 0) + ' plates • ' + money(detail.totalRevenue || 0) + '</div>';
        h += '<div class="dtc-badges">' +
            '<span class="dtc-badge green">' + (detail.paidCount || 0) + ' Paid</span>' +
            '<span class="dtc-badge orange">' + (detail.pendingCount || 0) + ' Pending</span>' +
            '<span class="dtc-badge red">' + (detail.cancelledCount || 0) + ' Cancelled</span>' +
            '</div></div>';

        // Reconcile with the dashboard card: "N booked" is the offering's live
        // reservation total across every date it is posted for, while these rows
        // cover ONE date. Whenever the two differ, say why instead of letting the
        // seller read it as missing orders.
        var bookedTotal = detail.dashboardBookedQuantity;
        if (typeof bookedTotal === 'number' && bookedTotal !== (detail.totalPlates || 0)) {
            var bookedUnit = detail.productUnit || 'units';
            if (bookedTotal !== 1 && bookedUnit.slice(-1) !== 's') bookedUnit += 's';
            h += '<div class="tiny muted mt-1">Dashboard shows ' + bookedTotal + ' ' +
                esc(bookedUnit) + ' booked — that covers every date of this ' +
                'offering. These orders are ' + esc(prettyDate(sellerDate(S.selectedDate))) + ' only.</div>';
        }

        // Filters only. Sorting within a single offering is meaningless - the
        // seller is already looking at one item - so no sort control is offered.
        h += '<div class="oc-filters">';
        h += '<select class="oc-filter-select" data-action="set-offering-society" aria-label="Filter by society"><option value="">All Societies</option>';
        societies.forEach(function (s) { h += '<option value="' + esc(s) + '"' + (S.offeringFilterSociety === s ? ' selected' : '') + '>' + esc(s) + '</option>'; });
        h += '</select>';
        h += '<select class="oc-filter-select" data-action="set-offering-status" aria-label="Filter by status"><option value="">All Status</option>';
        [['paid', 'Paid'], ['pending', 'Pending'], ['cancelled', 'Cancelled']].forEach(function (pair) {
            h += '<option value="' + pair[0] + '"' + (S.offeringFilterStatus === pair[0] ? ' selected' : '') + '>' + pair[1] + '</option>';
        });
        h += '</select>';
        // Third, INDEPENDENT filter: delivery. Handled by the 'change' listener
        // alone, exactly like the other two selects - a 'click' would report the
        // previously selected option and re-render over the new choice.
        h += '<select class="oc-filter-select" data-action="set-offering-delivery" aria-label="Filter by delivery"><option value="">All Delivery</option>';
        [['delivered', 'Delivered'], ['not_delivered', 'Not delivered']].forEach(function (pair) {
            h += '<option value="' + pair[0] + '"' + (S.offeringFilterDelivery === pair[0] ? ' selected' : '') + '>' + pair[1] + '</option>';
        });
        h += '</select></div>';

        if (S.offeringFilterSociety || S.offeringFilterStatus || S.offeringFilterDelivery) {
            h += '<div class="tiny muted mt-1">Showing ' + (detail.filteredTotalOrders || 0) + ' of ' + orderCount + ' orders</div>';
        }
        // Delivery progress sits ABOVE the rows and is deliberately unfiltered: it
        // describes the whole offering for this date, which is also the scope of
        // its Mark All Delivered action.
        h += deliveryProgressHtml(detail);
        h += '<div id="offeringCustomers">' + offeringCustomersHtml(detail) + '</div>';
    } catch (e) {
        h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<h2 class="flex-1">Order Summary</h2></div>';
        h += emptyHtml('⚠️', 'Could not load orders', e.message,
            '<button class="btn btn-primary card-mt" type="button" data-action="seller-retry">Retry</button>');
    }
    h += '</div>';
    return h;
}

/** Compact customer-order rows as a string (never written to the live DOM). */
function offeringCustomersHtml(detail) {
    var customers = (detail && detail.customers) || [];
    if (customers.length === 0) {
        return emptyHtml('📋', 'No customer orders', 'No orders match the selected filters.');
    }
    var out = '';
    customers.forEach(function (c) {
        // Order lifecycle decides the colour bucket; payment status decides
        // Paid vs Pending. A cancelled order is never shown as paid/pending.
        var bucket = c.cancelled ? 'cancelled' : (c.paid ? 'paid' : 'pending');
        var label = c.cancelled ? 'CANCELLED' : (c.paid ? 'PAID' : 'PENDING');
        var addr = [];
        if (c.society) addr.push(c.society);
        if (c.building) addr.push(c.building);
        if (c.buyerFlat) addr.push(c.buyerFlat);
        var qtyLabel = c.quantity + ' ' + esc(c.unit || 'plate') + (c.quantity !== 1 ? 's' : '');
        var remark = c.remark
            ? '<button type="button" class="remark-icon" data-action="show-remark" data-remark="' + esc(c.remark) + '" aria-label="View customer remark">💬</button>'
            : '';
        // Delivered control. The checkbox is rendered ONLY for rows delivery
        // tracking applies to (the server marks cancelled/draft rows
        // non-editable), and it carries the last SERVER-confirmed value in
        // data-delivered so a failed save can be rolled back to it.
        // Both the label and the input carry the action so a tap anywhere on the
        // control is routed to 'change' and never to the row's navigation.
        var delivered = !!c.delivered;
        var deliveryCtrl = '';
        if (c.deliveryEditable) {
            deliveryCtrl = '<label class="oc-delivered' + (delivered ? ' on' : '') + '" data-action="set-delivered">' +
                '<input type="checkbox" data-action="set-delivered" data-oid="' + esc(c.orderId) + '"' +
                ' data-delivered="' + (delivered ? 'true' : 'false') + '"' + (delivered ? ' checked' : '') +
                ' aria-label="Mark this order delivered">' +
                '<span class="oc-delivered-text">Delivered</span></label>';
        } else if (delivered) {
            deliveryCtrl = '<span class="oc-delivered-static">Delivered</span>';
        }
        out += '<div class="customer-row compact oc-row" role="button" tabindex="0" data-action="open-order" data-order="' + esc(c.orderId) + '">';
        out += '<span class="status-dot ' + bucket + '" aria-hidden="true"></span>';
        out += '<div class="oc-row-main">';
        out += '<div class="oc-row-top"><span class="cr-qty">' + qtyLabel + '</span>' +
            '<span class="cr-status ' + bucket + '">' + label + '</span></div>';
        if (addr.length) out += '<div class="cr-loc">' + esc(addr.join(' • ')) + '</div>';
        if (deliveryCtrl) out += '<div class="oc-row-delivery">' + deliveryCtrl + '</div>';
        out += '</div>';
        out += remark;
        out += '<span class="oc-row-chevron" aria-hidden="true">›</span>';
        out += '</div>';
    });
    return out;
}

/**
 * Delivery progress block for ONE offering on ONE date.
 *
 * <p>Every figure is the SERVER's: "x of y delivered", the number on the bulk
 * button and the number its confirmation quotes all come from the same
 * unfiltered calculation, so a filtered row list can never make the action look
 * like it covers only the visible subset. The block carries the offering id so
 * it can repaint itself from the server after a toggle.</p>
 */
function deliveryProgressHtml(detail) {
    var p = detail.deliveryProgress || null;
    var active = p ? (p.activeOrderCount || 0) : 0;
    var done = p ? (p.deliveredCount || 0) : 0;
    var remaining = p ? (p.remainingCount || 0) : 0;
    var scope = p ? (p.bulkScopeOrderCount || 0) : 0;
    var pct = active > 0 ? Math.round((done / active) * 100) : 0;
    var h = '<div class="del-progress" id="deliveryBlock">';
    h += '<div class="del-progress-top"><span class="del-progress-title">🚚 Deliveries</span>' +
        '<span class="del-progress-count">' + done + ' of ' + active + ' delivered' + (remaining > 0 ? ' · ' + remaining + ' remaining' : '') + '</span></div>';
    h += '<div class="del-bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="' + pct + '">' +
        '<span class="del-bar-fill" style="width:' + pct + '%"></span></div>';
    if (active === 0) {
        h += '<div class="tiny muted mt-1">No deliverable orders for this offering on this date.</div>';
    } else if (remaining === 0) {
        h += '<div class="del-done-note">✓ All deliveries complete for this date</div>';
    } else {
        h += '<button class="btn btn-primary btn-sm del-mark-all" type="button" data-action="mark-all-delivered">' +
            'Mark All Delivered (' + scope + ')</button>';
        h += '<div class="tiny muted mt-1">Applies to every active order of this offering on ' +
            esc(prettyDate(sellerDate(S.selectedDate))) + ' — not just the rows below.</div>';
    }
    h += '</div>';
    return h;
}

/**
 * Repaints ONLY the delivery block from the server.
 *
 * <p>After an individual toggle the checkbox itself is trusted from the PATCH
 * response, but the "x of y delivered" line and the bulk button's count must
 * stay the server's numbers, so they are re-read instead of adjusted in the
 * browser. Overlapping toggles issue overlapping reads, so only the newest
 * response may paint: an older reply describes an earlier state and would
 * visibly roll the numbers back.</p>
 */
async function refreshDeliveryBlock() {
    var productId = S.offeringProductId;
    if (!productId || !$('#deliveryBlock')) return;
    var seq = ++S.deliveryBlockRequestId;
    try {
        var detail = await sellerApi(offeringDetailUrl(productId));
        if (seq !== S.deliveryBlockRequestId) return;
        var current = $('#deliveryBlock');
        if (current) current.outerHTML = deliveryProgressHtml(detail);
    } catch (e) {
        // Keep the last confirmed numbers: a failed refresh must not turn a save
        // that already succeeded into an error message.
    }
}

/**
 * Auto-saves ONE order's Delivered flag from a row checkbox.
 *
 * <p>Owns its own errors because it is called from the 'change' listener
 * without being awaited: a failed save must roll the checkbox back to the last
 * SERVER-confirmed value and say why, never leave a row looking saved.</p>
 */
async function saveDeliveryToggle(input) {
    var oid = input.dataset.oid;
    if (!oid) return;
    var wasDelivered = input.dataset.delivered === 'true';
    var wantDelivered = !!input.checked;
    if (wantDelivered === wasDelivered) return;
    // Rapid-click guard: a second change for the same row while its request is
    // still in flight would send a duplicate write. The endpoint is idempotent,
    // but the UI must not queue overlapping PATCHes for one row.
    S.deliverySaving = S.deliverySaving || {};
    if (S.deliverySaving[oid]) { input.checked = wasDelivered; return; }
    S.deliverySaving[oid] = true;
    input.disabled = true;
    try {
        var updated = await sellerApi('/api/seller-app/orders/' + encodeURIComponent(oid) + '/delivery-status', {
            method: 'PATCH',
            body: { deliveryStatus: wantDelivered ? 'DELIVERED' : 'NOT_DELIVERED' }
        });
        // The SERVER's answer wins: another tab may have changed this order since
        // this screen was rendered.
        var serverDelivered = updated && updated.deliveryStatus === 'DELIVERED';
        input.checked = serverDelivered;
        input.dataset.delivered = serverDelivered ? 'true' : 'false';
        var chip = input.closest('.oc-delivered');
        if (chip) chip.classList.toggle('on', serverDelivered);
        toast(serverDelivered ? 'Order marked delivered' : 'Delivery unmarked', 'success');
    } catch (err) {
        input.checked = wasDelivered;
        input.dataset.delivered = wasDelivered ? 'true' : 'false';
        toast(err.message, 'error');
    } finally {
        input.disabled = false;
        delete S.deliverySaving[oid];
    }
    // The numbers on the screen belong to the server, so they are re-read even
    // after a failure (where the reload simply confirms nothing changed).
    refreshDeliveryBlock();
}

// SCREEN 7C: INDIVIDUAL ORDER DETAIL
// SCREEN 7C: INDIVIDUAL ORDER DETAIL
async function sellerOrderDetailByOrderView(orderId) {
    var h = '<div class="view-enter"><div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button><h2 class="flex-1">Order Details</h2></div>';
    try {
        var order = await sellerApi('/api/seller/orders/' + orderId);
        h += '<div class="card pad card-mb">';
        h += '<div class="top-row"><div class="font-700">#' + esc(order.orderNumber) + '</div>';
        var statusClass = order.orderStatus === 'CANCELLED' ? 'cancelled' : (order.paymentStatus === 'PAID' ? 'paid' : 'pending');
        h += '<span class="status-dot ' + statusClass + '"></span></div>';
        if (order.buyer) {
            var addrParts = [];
            if (order.buyer.society) addrParts.push(order.buyer.society);
            if (order.buyer.building) addrParts.push(order.buyer.building);
            if (order.buyer.flatHouseNumber) addrParts.push(order.buyer.flatHouseNumber);
            h += '<div class="odc-buyer-row"><span class="odc-buyer">' + esc(order.buyer.name || 'Unknown') + '</span>';
            h += '<span class="odc-qty">' + money(order.totalAmount) + '</span></div>';
            if (addrParts.length > 0) { h += '<div class="odc-address">📍 ' + esc(addrParts.join(', ')) + '</div>'; }
            if (order.buyer.mobileNumber) { h += '<div class="odc-address">📱 ' + esc(order.buyer.mobileNumber) + '</div>'; }
        }
        if (order.orderTime) { h += '<div class="tiny muted mt-1">Ordered: ' + prettyDateTime(order.orderTime) + '</div>'; }
        if (order.customInstructions) { h += '<div class="odc-remark">"' + esc(order.customInstructions) + '"</div>'; }
        if (order.items && order.items.length > 0) {
            h += '<div class="mt-2">';
            order.items.forEach(function (item) {
                var itemTotal = item.price != null && item.quantity != null ? item.price * item.quantity : 0;
                h += '<div class="odc-item"><span class="ei-name">' + esc(item.name) + '</span><span class="ei-orders">' + item.quantity + 'x ' + money(item.price) + ' = ' + money(itemTotal) + '</span></div>';
            });
            h += '</div>';
        }
        var paymentStatus = order.paymentStatus || 'PENDING';
        h += '<div class="dtc-badges mt-2">';
        h += '<span class="dtc-badge ' + (paymentStatus === 'PAID' ? 'green' : 'orange') + '">Payment: ' + esc(paymentStatus) + '</span>';
        h += '<span class="dtc-badge ' + (order.orderStatus === 'CANCELLED' ? 'red' : 'green') + '">Order status: ' + esc(order.orderStatus || 'ORDERED') + '</span></div>';
        if (order.orderStatus && ['ORDERED', 'CONFIRMED', 'READY'].indexOf(order.orderStatus) >= 0) {
            h += '<button class="btn btn-secondary btn-block mt-2" type="button" data-action="cancel-order" data-order-id="' + esc(order.id) + '">Cancel Order</button>';
        }
        if ((paymentStatus === 'PENDING' || paymentStatus === 'WILL_PAY_LATER') && order.orderStatus !== 'CANCELLED') {
            h += '<button class="btn btn-primary btn-block mt-2" type="button" data-action="mark-paid" data-oid="' + esc(order.id) + '">Mark as Paid</button>';
        }
        h += '</div>';
    } catch (e) { h += emptyHtml('⚠️', 'Could not load details', e.message); }
    h += '</div>';
    return h;
}

/**
 * Parses an optional HH:mm time input value for the Create Offering form.
 * Returns null for blank input (optional field) and throws for malformed
 * values so inconsistent timing data never reaches the backend.
 */
function parseOptionalHhmm(value, label) {
    var v = (value || '').trim();
    if (!v) return null;
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(v)) {
        throw new Error(label + ' must be a valid time in 24-hour HH:mm format.');
    }
    return v;
}

/**
 * Requirement 5 — saves the Edit Offering form.
 *
 * Only fields the seller may still change are sent, so frozen values are never
 * round-tripped. Timing/price edits use the existing partial update; the
 * availability change uses the existing atomic inventory endpoint so a
 * concurrent buyer order is never overwritten and the max/zero guards apply.
 */
async function submitOfferingEdit(form) {
    var o = S.editOffering;
    if (!o || !o.id) { toast('This offering is no longer available', 'error'); return; }
    var locked = !!o.hasOrders;
    var ev = formVals(form);
    var payload = {};
    var booked = o.bookedQuantity || 0;
    var remainingNow = o.remainingQuantity == null ? 0 : o.remainingQuantity;
    var delta = 0;

    // Description and photo stay editable on every offering.
    var description = ev.description == null ? '' : ev.description;
    if (description !== (o.description || '')) payload.description = description;
    if ((ev.imageUrl || '') !== (o.imageUrl || '')) payload.imageUrl = ev.imageUrl || '';

    var categories = [];
    $all('input[name="categories"]:checked', form).forEach(function (cb) { categories.push(cb.value); });
    if (categories.length === 0) { toast('Select at least one category', 'error'); return; }
    if (categories.join(',') !== (o.category || '')) payload.categories = categories;

    var closeVal = parseOptionalHhmm(ev.orderWindowEnd, 'Orders Close');
    if (!closeVal) { toast('Orders Close is required', 'error'); return; }
    if (locked && closeVal < o.orderWindowEnd) {
        toast('This offering already has orders, so Orders Close can only be extended (currently ' + prettyTime(o.orderWindowEnd) + ')', 'error');
        return;
    }
    if (closeVal !== (o.orderWindowEnd || '')) payload.orderWindowEnd = closeVal;

    if (!locked) {
        // Nothing has been ordered yet, so every timing and price field is free.
        var openVal = parseOptionalHhmm(ev.orderWindowStart, 'Orders Open');
        if ((openVal || '') !== (o.orderWindowStart || '')) payload.orderWindowStart = openVal || '';
        var offeringDate = S.offeringFor === 'choose' ? (ev.chosenOfferingDate || '') : sellerDate(S.offeringFor);
        if (!offeringDate) { toast('Choose an offering date', 'error'); return; }
        if (offeringDate !== (o.availableDate || '')) payload.availableDate = offeringDate;
        var readyInput = (ev.readyByTime || '').trim();
        if (readyInput !== (o.readyByTime || '')) {
            if (!readyInput) { toast('Delivery / Ready By is required', 'error'); return; }
            payload.readyByTime = readyInput;
        }
        if ((ev.name || '') !== (o.name || '')) {
            if (!ev.name) { toast('Item Name is required', 'error'); return; }
            payload.name = ev.name;
        }
        if ((ev.price || '') !== (o.price == null ? '' : String(o.price))) {
            if (!ev.price) { toast('Price is required', 'error'); return; }
            payload.price = Number(ev.price);
        }
        if ((ev.priceUnit || '') !== (o.priceUnit || '')) payload.priceUnit = ev.priceUnit;
        var effectiveOpen = payload.orderWindowStart != null ? payload.orderWindowStart : o.orderWindowStart;
        if (effectiveOpen && effectiveOpen >= closeVal) { toast('Orders Open must be before Orders Close', 'error'); return; }
        // A stored legacy ready-by text is left untouched unless the seller types
        // a new value; only newly typed values are pre-checked here (the server
        // re-validates authoritatively).
        if (payload.readyByTime) {
            var timingProblem = offeringTimingError(payload.availableDate || o.availableDate,
                effectiveOpen, closeVal, payload.readyByTime);
            if (timingProblem) { toast(timingProblem, 'error'); return; }
        }
    }

    // Quantity: raise the limit first when the seller offers more than before,
    // then apply the availability change atomically.
    if (o.maxQuantity != null) {
        var availableVal = Number(ev.availableQuantity);
        if (!Number.isInteger(availableVal) || availableVal < 0) {
            toast('Quantity Available must be a whole number of 0 or more', 'error');
            return;
        }
        if (availableVal !== remainingNow) {
            if (booked + availableVal < 1) {
                toast('Quantity Available must be at least 1, or use Mark Sold Out to stop orders', 'error');
                return;
            }
            if (booked + availableVal > o.maxQuantity) payload.maxQuantity = booked + availableVal;
            delta = availableVal - remainingNow;
        }
    }

    if (Object.keys(payload).length === 0 && delta === 0) { toast('No changes to save', 'info'); return; }
    if (Object.keys(payload).length > 0) {
        await sellerApi('/api/seller/products/' + o.id, { method: 'PUT', body: payload });
    }
    if (delta !== 0) {
        await sellerApi('/api/seller-app/products/' + o.id + '/inventory', { method: 'PATCH', body: { delta: delta } });
    }
    toast('Offering updated', 'success');
    S.editOffering = null;
    S.offeringFor = 'today';
    sellerNavigate('#/home');
}

// Form submission
document.addEventListener('submit', async function (e) {
    var form = e.target.closest('form');
    if (!form) return;
    e.preventDefault();
    try {
        if (form.id === 'createOfferingForm') {
            var vals = formVals(form);
            // Optional timing fields: blank means "not provided" -- never send
            // an invalid empty timestamp to the backend.
            vals.orderWindowStart = parseOptionalHhmm(vals.orderWindowStart, 'Orders Open');
            vals.orderWindowEnd = parseOptionalHhmm(vals.orderWindowEnd, 'Orders Close');
            if (!vals.orderWindowEnd) { toast('Orders Close is required', 'error'); return; }
            var readyBy = (vals.readyByTime || '').trim();
            if (!readyBy) { toast('Delivery / Ready By is required', 'error'); return; }
            vals.readyByTime = readyBy;
            if (S.offeringFor === 'choose') {
                vals.availableDate = vals.chosenOfferingDate || '';
                if (!vals.availableDate) { toast('Choose an offering date', 'error'); return; }
            } else {
                vals.availableDate = sellerDate(S.offeringFor);
            }
            delete vals.chosenOfferingDate;
            if (vals.maxQuantity !== '' && vals.maxQuantity !== null && vals.maxQuantity !== undefined) {
                var mq = Number(vals.maxQuantity);
                if (!Number.isInteger(mq) || mq < 1) {
                    toast('Quantity Available must be a whole number of at least 1, or blank for unlimited', 'error');
                    return;
                }
                vals.maxQuantity = mq;
            } else {
                vals.maxQuantity = null; // blank = unlimited
            }
            // Immediate UX checks; the backend repeats these rules authoritatively.
            var timingError = offeringTimingError(vals.availableDate, vals.orderWindowStart, vals.orderWindowEnd, readyBy);
            if (timingError) {
                toast(timingError, 'error');
                return;
            }
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await sellerApi('/api/seller/kitchen');
                kid = k.id;
                S.myKitchen = k;
            }
            var categories = [];
            $all('input[name="categories"]:checked', form).forEach(function (cb) { categories.push(cb.value); });
            if (categories.length === 0) {
                toast('Select at least one category', 'error');
                return;
            }
            vals.categories = categories;
            var saveFav = $('#favToggle') && $('#favToggle').classList.contains('on');
            if (saveFav) {
                try {
                    var favBody = { name: vals.name, description: vals.description || '', price: Number(vals.price), priceUnit: vals.priceUnit, maxQuantity: vals.maxQuantity, orderWindowStart: vals.orderWindowStart, orderWindowEnd: vals.orderWindowEnd, cutoffTime: vals.cutoffTime, readyByTime: vals.readyByTime, availableDate: vals.availableDate, category: (categories && categories[0]) || '' };
                    await sellerApi('/api/seller-app/templates', { method: 'POST', body: favBody });
                } catch (favErr) { toast('Could not save favourite: ' + favErr.message, 'error'); }
            }
            await sellerApi('/api/seller/products?kitchenId=' + kid, { method: 'POST', body: vals });
            toast('Offering published!', 'success');
            S.draftOffering = null;
            S.republishSourceId = null;
            S.offeringFor = 'today';
            sellerNavigate('#/home');
        } else if (form.id === 'editOfferingForm') {
            await submitOfferingEdit(form);
        } else if (form.id === 'quickPostForm') {
            var message = (form.querySelector('[name="message"]').value || '').trim();
            if (!message) { toast('Paste a WhatsApp message first', 'error'); return; }
            // Frontend guard matches the server limit exactly. The server stays
            // authoritative - this only avoids a pointless round trip and gives
            // the seller immediate feedback while typing.
            if (message.length > QUICK_POST_MAX) {
                toast('Quick Post is ' + (message.length - QUICK_POST_MAX) + ' character'
                    + (message.length - QUICK_POST_MAX === 1 ? '' : 's')
                    + ' over the ' + QUICK_POST_MAX + ' character limit', 'error');
                return;
            }
            var submitButton = form.querySelector('#qpSubmit');
            if (submitButton) submitButton.disabled = true;

            // A Quick Post is now an ordinary Product. Rather than duplicating
            // Product/Offerings/Order logic, the form is submitted to the SAME
            // endpoint Create Offering already uses, so every existing rule
            // (price, timing, categories, ownership, authorization) applies
            // unchanged, and the result lands in Offerings + buyer discovery
            // with the normal Order button.
            var vals = formVals(form);
            // The seller's post text is the offering's primary content.
            vals.description = message;
            delete vals.message;

            vals.orderWindowStart = parseOptionalHhmm(vals.orderWindowStart, 'Orders Open');
            vals.orderWindowEnd = parseOptionalHhmm(vals.orderWindowEnd, 'Orders Close');
            if (!vals.orderWindowEnd) { toast('Orders Close is required', 'error'); if (submitButton) submitButton.disabled = false; return; }
            var qpReadyBy = (vals.readyByTime || '').trim();
            if (!qpReadyBy) { toast('Delivery / Ready By is required', 'error'); if (submitButton) submitButton.disabled = false; return; }
            vals.readyByTime = qpReadyBy;

            // Quick Posts stay a Today-only offering, exactly as before.
            vals.availableDate = sellerDate('today');
            vals.isPreorder = false;

            var qpPrice = Number(vals.price);
            if (!isFinite(qpPrice) || qpPrice <= 0) {
                toast('Price must be greater than 0', 'error');
                if (submitButton) submitButton.disabled = false;
                return;
            }
            vals.price = qpPrice;

            if (vals.maxQuantity !== '' && vals.maxQuantity !== null && vals.maxQuantity !== undefined) {
                var qpQty = Number(vals.maxQuantity);
                if (!Number.isInteger(qpQty) || qpQty < 1) {
                    toast('Quantity Available must be a whole number of at least 1, or blank for unlimited', 'error');
                    if (submitButton) submitButton.disabled = false;
                    return;
                }
                vals.maxQuantity = qpQty;
            } else {
                vals.maxQuantity = null; // blank = unlimited
            }

            // Same cross-field timing rule the Create Offering form uses.
            var qpTimingError = offeringTimingError(vals.availableDate, vals.orderWindowStart, vals.orderWindowEnd, qpReadyBy);
            if (qpTimingError) {
                toast(qpTimingError, 'error');
                if (submitButton) submitButton.disabled = false;
                return;
            }

            var qpCategories = [];
            $all('input[name="categories"]:checked', form).forEach(function (cb) { qpCategories.push(cb.value); });
            if (qpCategories.length === 0) {
                toast('Select at least one category', 'error');
                if (submitButton) submitButton.disabled = false;
                return;
            }
            vals.categories = qpCategories;

            // Product has no "Offering For" chooser on this screen: it is always
            // Today, matching the original Quick Post rule.
            vals.imageUrl = '';
            try {
                var qpKid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
                if (!qpKid) {
                    var qpK = await sellerApi('/api/seller/kitchen');
                    qpKid = qpK.id;
                    S.myKitchen = qpK;
                }
                await sellerApi('/api/seller/products?kitchenId=' + qpKid, { method: 'POST', body: vals });
                toast('Quick Post published as an offering!', 'success');
                S.quickPostRequestId = null;
                S.offeringFor = 'today';
                sellerNavigate('#/home');
            } finally {
                if (submitButton) submitButton.disabled = false;
            }
        } else if (form.id === 'kitchenForm') {
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await sellerApi('/api/seller/kitchen');
                kid = k.id;
                S.myKitchen = k;
            }
            var kitchenPayload = formVals(form);
            // Coverage is submitted as Society IDs, never as free-text names.
            // When no Area is chosen the fields are omitted entirely so an
            // unrelated profile edit cannot silently wipe existing coverage.
            delete kitchenPayload.serviceAreas;
            if (S.coverageAreaId) {
                kitchenPayload.areaId = Number(S.coverageAreaId);
                kitchenPayload.societyIds = (S.coverageIds || []).map(Number);
            } else {
                delete kitchenPayload.areaId;
                delete kitchenPayload.societyIds;
            }
            await sellerApi('/api/seller/kitchen/' + kid, { method: 'PUT', body: kitchenPayload });
            toast('All changes saved', 'success');
        }
    } catch (err) { toast(err.message, 'error'); }
});

// EVENT DELEGATION
document.addEventListener('click', async function (e) {
    var t = e.target.closest('[data-action]');
    if (!t) return;
    var a = t.dataset.action;
    // The Delivered checkbox lives INSIDE a clickable order row. A click on it
    // (or on its label) must toggle delivery through the 'change' listener and
    // must NOT also navigate to the order detail screen, so it is deliberately
    // ignored here - the native label/checkbox behaviour still fires 'change'.
    if (a === 'set-delivered') return;
    try {
        switch (a) {
            case 'go-back': history.back(); break;
            case 'noop': break;
            case 'toggle-notifs': await toggleNotifications(t.dataset.panelId || 'sellerNotifPanel'); break;
            case 'read-notification': await readNotification(t.dataset.notificationId); break;
            case 'toggle-theme': toggleTheme(); break;
            case 'go-add': sellerNavigate('#/add'); break;
            case 'retry-favourites': await sellerRender(); break;
            // The saved favourites are already listed as pills inside this card, so a
            // click on the card body must tell the seller what to do next instead of
            // silently doing nothing - this action had no handler at all before.
            case 'go-use-favourite': {
                if (S.favError) { await sellerRender(); break; }
                if (S.favTemplates.length === 0) { toast('No saved favourites yet - turn on Save as template (max 3) while creating an offering.', 'info'); break; }
                toast('Tap one of your saved favourites to use it', 'info');
                break;
            }
            case 'go-create': sellerNavigate('#/create'); break;
            case 'go-quick-post': sellerNavigate('#/quick-post'); break;
            case 'go-kitchen': sellerNavigate('#/kitchen'); break;
            case 'go-orders': sellerNavigate('#/orders'); break;
            case 'go-history': sellerNavigate('#/history'); break;
            case 'go-earnings': sellerNavigate('#/earnings'); break;
            case 'go-home': sellerNavigate('#/home'); break;
            case 'use-template': {
                var tid = Number(t.dataset.tid);
                var template = S.favTemplates.find(function (x) { return x.id === tid; });
                if (template) { S.draftOffering = template; sellerNavigate('#/create'); }
                break;
            }
            case 'inv-inc': {
                var pid = Number(t.dataset.pid);
                await sellerApi('/api/seller-app/products/' + pid + '/inventory', { method: 'PATCH', body: { delta: 1 } });
                var el = $('#inv-' + pid); if (el) el.textContent = parseInt(el.textContent) + 1;
                toast('Quantity updated', 'success'); break;
            }
            case 'inv-dec': {
                var pid = Number(t.dataset.pid);
                await sellerApi('/api/seller-app/products/' + pid + '/inventory', { method: 'PATCH', body: { delta: -1 } });
                var el = $('#inv-' + pid); if (el) el.textContent = parseInt(el.textContent) - 1;
                toast('Quantity updated', 'success'); break;
            }
            case 'mark-soldout': {
                var pid = Number(t.dataset.pid);
                confirmModal({
                    icon: '🔴',
                    title: 'Mark as Sold Out?',
                    message: 'This will mark the offering as sold out and hide it from buyers.',
                    okLabel: 'Yes, Sold Out',
                    onOk: async function () {
                        await sellerApi('/api/seller-app/products/' + pid + '/sold-out', { method: 'POST' });
                        toast('Marked as Sold Out', 'success');
                        sellerRender();
                    }
                });
                break;
            }
            case 'pause-orders': {
                var pausePid = Number(t.dataset.pid);
                await sellerApi('/api/seller-app/products/' + pausePid + '/pause', { method: 'POST' });
                toast('Orders paused', 'success');
                await sellerRender();
                break;
            }
            case 'resume-orders': {
                var resumePid = Number(t.dataset.pid);
                await sellerApi('/api/seller-app/products/' + resumePid + '/resume', { method: 'POST' });
                toast('Orders resumed', 'success');
                await sellerRender();
                break;
            }
            case 'pause-kitchen': {
                if (!S.myKitchen) { toast('Kitchen is not available', 'error'); break; }
                confirmModal({
                    icon: '⏸️',
                    title: 'Pause your kitchen?',
                    message: 'Customers will no longer be able to discover your kitchen or place new orders. Existing confirmed orders will not be affected.',
                    okLabel: 'Yes, Pause Kitchen',
                    onOk: async function () {
                        await sellerApi('/api/seller/kitchen/' + S.myKitchen.id + '/pause', { method: 'POST' });
                        toast('Kitchen paused', 'success');
                        await sellerRender();
                    }
                });
                break;
            }
            case 'resume-kitchen': {
                if (!S.myKitchen) { toast('Kitchen is not available', 'error'); break; }
                await sellerApi('/api/seller/kitchen/' + S.myKitchen.id + '/resume', { method: 'POST' });
                toast('Kitchen resumed', 'success');
                await sellerRender();
                break;
            }
            case 'set-view-mode': S.viewMode = t.dataset.mode; await sellerRender(); break;
            case 'set-date': S.selectedDate = t.dataset.date; await sellerRender(); break;
            // set-date-calendar / set-offering-society / set-offering-status are
            // <input>/<select> controls: they are handled ONLY by the 'change'
            // listener below. Reading their value from 'click' yields the OLD
            // selection and re-renders the view over the freshly filtered one.
            case 'set-sort': S.sortFilter = t.value; break;
            case 'set-detail-sort': S.selectedSort = t.value; await sellerRender(); break;
            case 'open-order': sellerNavigate('#/order-detail/order/' + t.dataset.order); break;
            case 'mark-all-delivered': {
                if (S.bulkDelivering) break;
                var bulkProductId = S.offeringProductId;
                if (!bulkProductId) { toast('Open an offering to mark its deliveries', 'error'); break; }
                // The scope is the offering + date and is quoted from the SERVER, so
                // the dialog states the count the backend will really change even
                // when the visible rows are filtered or this tab is stale.
                var scopeCount = null;
                try {
                    var fresh = await sellerApi(offeringDetailUrl(bulkProductId));
                    scopeCount = (fresh.deliveryProgress && fresh.deliveryProgress.bulkScopeOrderCount) || 0;
                } catch (err) { toast(err.message, 'error'); break; }
                if (scopeCount === 0) {
                    toast('Every active order for this offering is already delivered', 'success');
                    refreshDeliveryBlock();
                    break;
                }
                confirmModal({
                    icon: '🚚',
                    title: 'Mark all delivered?',
                    message: scopeCount + (scopeCount === 1 ? ' order' : ' orders') +
                        ' for this offering on ' + prettyDate(sellerDate(S.selectedDate)) +
                        ' will be marked delivered. Cancelled orders are not included.',
                    okLabel: 'Mark All Delivered',
                    onOk: async function () {
                        S.bulkDelivering = true;
                        try {
                            var res = await sellerApi('/api/seller-app/orders/product/' + bulkProductId +
                                '/mark-all-delivered?date=' + sellerDate(S.selectedDate), { method: 'POST' });
                            // The returned count is what the backend ACTUALLY changed,
                            // which is not always the count the dialog quoted (another
                            // tab may have delivered some rows in between).
                            var applied = (res && res.appliedCount) || 0;
                            toast(applied === 1 ? '1 order marked delivered' : applied + ' orders marked delivered', 'success');
                        } catch (err) {
                            toast(err.message, 'error');
                        } finally {
                            S.bulkDelivering = false;
                        }
                        // Reload from server truth: every row's checkbox, the progress
                        // line and the bulk count must agree after a batch - and a
                        // failed batch (atomic rollback) shows the unchanged state.
                        await sellerRender();
                    }
                });
                break;
            }
            case 'show-remark':
                // Reuse the EXISTING modal helpers (openModal/closeModal) rather
                // than a raw alert(). No new component and no chat system invented.
                // The remark button is the closest [data-action] ancestor, so this
                // does NOT also trigger the row's open-order navigation.
                if (typeof openModal === 'function') {
                    openModal('<div class="modal-icon">💬</div>' +
                        '<h3>Customer remark</h3>' +
                        '<p class="muted small">' + esc(t.dataset.remark || '') + '</p>' +
                        '<div class="modal-actions">' +
                        '<button class="btn btn-primary" type="button" onclick="closeModal()">Close</button>' +
                        '</div>');
                }
                break;
            case 'parse-message': {
                var msg = $('#qpMessage').value;
                if (!msg.trim()) { toast('Please paste a message first', 'error'); return; }
                var result = await sellerApi('/api/seller-app/parse-message', { method: 'POST', body: { message: msg } });
                var box = $('#parseResult');
                if (box) {
                    var html = '<div class="parse-result-box"><h4>Extracted Details (Review before publishing)</h4>';
                    html += '<div class="parse-field"><span class="pf-label">Name</span><span class="pf-value ' + (result.name ? '' : 'missing') + '">' + (result.name || 'Missing') + '</span></div>';
                    html += '<div class="parse-field"><span class="pf-label">Price</span><span class="pf-value ' + (result.price ? '' : 'missing') + '">' + (result.price ? ('Rs ' + result.price) : 'Missing') + '</span></div>';
                    html += '<button class="btn btn-primary btn-block" data-action="go-create">Review and Publish</button></div>';
                    box.innerHTML = html;
                }
                break;
            }
            case 'cancel-order': {
                if (!confirm('Cancel this order?')) return;
                if (t.disabled) return;
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/orders/' + encodeURIComponent(t.dataset.orderId) + '/status', { method: 'PATCH', body: { orderStatus: 'CANCELLED' } });
                    toast('Order cancelled', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not cancel order', 'error');
                }
                break;
            }
            case 'edit-offering': {
                if (t.disabled) return;
                var editPid = Number(t.dataset.pid);
                t.disabled = true;
                try {
                    // Authoritative state (incl. whether orders already freeze fields).
                    S.editOffering = await sellerApi('/api/seller/products/' + editPid);
                    sellerNavigate('#/edit-offering');
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not load this offering', 'error');
                }
                break;
            }
            case 'republish-history': {
                if (t.disabled) return;
                var historyId = Number(t.dataset.pid);
                var historyItem = (S.historyItems || []).find(function (item) { return item.id === historyId; });
                if (!historyItem) { toast('This history item is no longer available', 'error'); return; }
                t.disabled = true;
                S.draftOffering = historyItem;
                S.republishSourceId = historyItem.id;
                S.offeringFor = 'today';
                sellerNavigate('#/create');
                break;
            }
            case 'batch-republish': {
                if (S.historySelected.length === 0) { toast('Select at least one item', 'error'); return; }
                await sellerApi('/api/seller-app/batch-republish', { method: 'POST', body: { productIds: S.historySelected, availableDate: sellerDate('today') } });
                toast('Republished ' + S.historySelected.length + ' items!', 'success'); sellerNavigate('#/home');
                break;
            }
            case 'toggle-history': {
                var pid = Number(t.dataset.pid);
                if (t.checked) S.historySelected.push(pid);
                else S.historySelected = S.historySelected.filter(function (id) { return id !== pid; });
                break;
            }
            case 'toggle-favourite': { var tg = $('#favToggle'); if (tg) tg.classList.toggle('on'); break; }
            case 'mark-paid': {
                var oid = Number(t.dataset.oid);
                if (!confirm('Mark this order as PAID?')) return;
                if (t.disabled) return;
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/orders/' + oid + '/payment-status', { method: 'PATCH' });
                    toast('Order marked as paid', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not update payment status', 'error');
                }
                break;
            }
            case 'add-service-area': {
                // Replaced by the Area + society-checkbox picker; the old
                // name-string pill editor no longer writes coverage.
                break;
            }
            case 'remove-service-area': {
                break;
            }
            case 'preview-offering': toast('Preview mode', 'info'); break;
            case 'preview-kitchen': {
                // Opens the EXISTING buyer-facing kitchen page. seller.html and
                // index.html are separate documents, so the buyer SPA is reached
                // by a full page load using its own hash route "#/kitchen/{id}",
                // which buyer.js resolves through MarketplaceController's
                // GET /api/kitchens/id/{id}. This is the same mechanism the Admin
                // console already uses for open-buyer (admin.js location.href).
                var previewKitchen = S.myKitchen || S.kitchen;
                var previewId = previewKitchen && previewKitchen.id;
                if (!previewId) {
                    // The Kitchen screen always loads the seller's OWN kitchen
                    // first, but a missing/stale cache must never fall back to
                    // some other kitchen or silently preview nothing.
                    try {
                        previewKitchen = await sellerApi('/api/seller/kitchen');
                        S.myKitchen = previewKitchen;
                        S.kitchen = previewKitchen;
                        previewId = previewKitchen && previewKitchen.id;
                    } catch (previewErr) {
                        toast('Could not load your kitchen. Please retry.', 'error');
                        break;
                    }
                }
                if (!previewId) {
                    toast('No kitchen found to preview. Publish your kitchen first.', 'error');
                    break;
                }
                toast('Opening kitchen preview...', 'info');
                location.href = '/index.html#/kitchen/' + encodeURIComponent(previewId);
                break;
            }
            case 'add-photo': toast('Photo upload (demo)', 'info'); break;
            case 'upload-avatar': toast('Kitchen photo upload (demo)', 'info'); break;
            case 'seller-retry': location.reload(); break;
            case 'ack-enquiry': {
                var eid = Number(t.dataset.id);
                await api('/api/enquiries/' + eid + '/acknowledge', { method: 'POST' });
                toast('Enquiry acknowledged', 'success');
                await sellerRender();
                break;
            }
            case 'close-enquiry': {
                var eid2 = Number(t.dataset.id);
                if (!confirm('Close this enquiry?')) return;
                await api('/api/enquiries/' + eid2 + '/status', { method: 'PATCH', body: { status: 'CLOSED' } });
                toast('Enquiry closed', 'success');
                await sellerRender();
                break;
            }
            case 'set-seller-type': {
                $all('.radio-option').forEach(function (el) { el.classList.remove('selected'); });
                t.classList.add('selected');
                var val = t.dataset.val || 'KITCHEN';
                var input = $('#sellerTypeInput');
                if (input) input.value = val;
                break;
            }
             case 'set-availability': {
                 $all('.radio-option').forEach(function (el) { el.classList.remove('selected'); });
                 t.classList.add('selected');
                 S.offeringFor = t.dataset.val || 'today';
                 var cdr = $('#chooseDateRow');
                 if (cdr) cdr.hidden = S.offeringFor !== 'choose';
                 var picker = $('#chooseDateRow input[type="date"]');
                 var av = $('#availDate');
                 if (av) av.value = S.offeringFor === 'choose'
                     ? (picker && picker.value || sellerDate('today'))
                     : sellerDate(S.offeringFor);
                 break;
             }
             case 'set-availability-date': {
                 applyOfferingDatePicker(t);
                 break;
             }
        }
    } catch (err) { toast(err.message, 'error'); }
});

document.addEventListener('change', function (e) {
    var picker = e.target.closest('[data-action="set-availability-date"]');
    if (picker) { applyOfferingDatePicker(picker); return; }
    // A <select> reports the chosen option through 'change', not 'click' -
    // clicking the dropdown only fires 'click' with the OLD value still set.
    // Without these the offering-orders society/status filters never applied.
    var societyFilter = e.target.closest('[data-action="set-offering-society"]');
    if (societyFilter) { S.offeringFilterSociety = societyFilter.value; sellerRender(); return; }
    // Seller Dashboard's own lightweight category filter (All / Kitchen /
    // Homemade). Read from 'change' for the same reason as the others above:
    // a click on a <select> still reports the OLD value.
    var dashFilter = e.target.closest('[data-action="set-dash-filter"]');
    if (dashFilter) { S.dashFilter = dashFilter.value; sellerRender(); return; }
    var statusFilter = e.target.closest('[data-action="set-offering-status"]');
    if (statusFilter) { S.offeringFilterStatus = statusFilter.value; sellerRender(); return; }
    // Third, independent filter. It combines with society + payment instead of
    // replacing them, and it is read from 'change' for the same reason.
    var deliveryFilter = e.target.closest('[data-action="set-offering-delivery"]');
    if (deliveryFilter) { S.offeringFilterDelivery = deliveryFilter.value; sellerRender(); return; }
    // The row's Delivered checkbox. 'change' (not 'click') is what carries the
    // NEW state, so the auto-save is driven from here; the click listener
    // deliberately ignores the control so a tap cannot also open the order.
    var deliveredToggle = e.target.closest('[data-action="set-delivered"]');
    if (deliveredToggle && deliveredToggle.type === 'checkbox') { saveDeliveryToggle(deliveredToggle); return; }
    var dateInput = e.target.closest('[data-action="set-date-calendar"]');
    if (dateInput) { S.selectedDate = dateInput.value; sellerRender(); return; }
    // The service-area <select> also reports through 'change'. Choosing an Area
    // resets the selection to that area's full active set, which is what makes a
    // newly added society appear - and what stops one from being adopted silently.
    var coverageArea = e.target.closest('[data-action="select-coverage-area"]');
    if (coverageArea) {
        S.coverageAreaId = coverageArea.value;
        S.coverageLoadedAreaId = '';
        var areaInput = $('#coverageAreaIdInput');
        if (areaInput) areaInput.value = coverageArea.value;
        renderCoverageSocieties();
        return;
    }
    var coverageSociety = e.target.closest('[data-action="toggle-coverage-society"]');
    if (coverageSociety) {
        var sid = Number(coverageSociety.dataset.id);
        var idx = (S.coverageIds || []).indexOf(sid);
        if (coverageSociety.checked && idx === -1) S.coverageIds = (S.coverageIds || []).concat([sid]);
        if (!coverageSociety.checked && idx !== -1) S.coverageIds = S.coverageIds.filter(function (id) { return id !== sid; });
        updateCoverageIdsInput();
        return;
    }
});

// Live Quick Post character feedback. Delegated on document so it keeps working
// across the SPA re-renders that replace #view on every navigation.
document.addEventListener('input', function (e) {
    if (e.target && e.target.id === 'qpMessage') updateQuickPostCounter();
});

// BOOT - never leaves the page on an infinite spinner
var sellerBooted = false;
window.addEventListener('hashchange', function () { if (sellerBooted) sellerRender(); });
window.addEventListener('DOMContentLoaded', async function () {
    try {
        initTheme();
        // Establish the seller session up front. If this genuinely fails we show
        // a retry state instead of silently continuing into an auth error.
        if (!(await ensureSellerSession())) {
            var v0 = viewEl();
            if (v0) v0.innerHTML = sellerAuthErrorHtml();
            return;
        }
        if (!location.hash) {
            sellerBooted = true;
            location.hash = '#/home';
        } else {
            sellerBooted = true;
            await sellerRender();
        }
    } catch (bootErr) {
        var view = viewEl();
        if (view) view.innerHTML = '<div class="view-enter">' +
            emptyHtml('⚠️', 'Seller app failed to load', (bootErr && bootErr.message) || 'Unknown error',
                '<button class="btn btn-primary btn-mt-md" type="button" data-action="seller-retry">Retry</button>') +
            '</div>';
    }
});
setTimeout(async function () {
    var view = viewEl();
    if (view && view.querySelector('.page-loading')) {
        view.innerHTML = '<div class="view-enter">' +
            emptyHtml('⏳', 'Still loading...', 'The server is not responding. Check your connection and retry.',
                '<button class="btn btn-primary btn-mt-md" type="button" data-action="seller-retry">Retry</button>') +
            '</div>';
    }
}, 20000);
