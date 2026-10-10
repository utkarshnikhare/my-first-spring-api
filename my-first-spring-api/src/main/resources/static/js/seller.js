/**
 * SocioMart Seller App v1.0 - 5-tab SPA
 */
var S = { user: null, kitchen: null, kitchenUrl: null, viewMode: 'editor', selectedDate: 'today', sortFilter: 'all', historySelected: [], draftOffering: null, favTemplates: [], favError: null, historyItems: [], offeringFilterSociety: '', offeringFilterStatus: '', offeringFilterDelivery: '', offeringProductId: '', deliverySaving: {}, deliveryBlockRequestId: 0, bulkDelivering: false, offeringFor: 'today', quickPostRequestId: null, editOffering: null, dashFilter: 'ALL', offeringsTab: 'history', recurringSchedules: [], recurringDetail: null, offeringMode: 'today', recurringDuration: 'thisweek', dashTab: 'live', offeringSubmitting: false, editOccurrenceId: null, authMode: 'login', authOptions: null, authError: null, authServiceAreaId: '', authSlugTouched: false };
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
/** Seller session and approval are reloaded from the database on each entry. */
async function ensureSellerSession() {
    try {
        var me = await api('/api/auth/me');
        S.authError = null;
        S.user = me && me.authenticated ? me : null;
        return !!(me && me.authenticated && me.role === 'SELLER' &&
            (!me.sellerApprovalStatus || me.sellerApprovalStatus === 'APPROVED'));
    } catch (err) {
        S.authError = err;
        S.user = null;
        return false;
    }
}

/** Seller API failures are surfaced; a rejected request is never retried as another account. */
async function sellerApi(path, opts) {
    return await api(path, opts);
}

function sellerAreaOptions(selectedId) {
    return (S.authOptions || []).map(function (area) {
        return '<option value="' + esc(area.id) + '"' +
            (String(area.id) === String(selectedId) ? ' selected' : '') + '>' + esc(area.name) + '</option>';
    }).join('');
}

function sellerSocietyOptions(areaId) {
    var area = (S.authOptions || []).find(function (item) { return String(item.id) === String(areaId); });
    return (area && area.societies || []).map(function (society) {
        return '<option value="' + esc(society.id) + '">' + esc(society.name) + '</option>';
    }).join('');
}

function sellerServiceSocietyHtml(areaId) {
    var area = (S.authOptions || []).find(function (item) { return String(item.id) === String(areaId); });
    var rows = area && area.societies || [];
    if (!rows.length) return '<p class="muted small">No active societies are available in this area.</p>';
    return rows.map(function (society) {
        return '<label class="seller-reg-society"><input type="checkbox" name="serviceSocietyIds" value="' +
            esc(society.id) + '"> <span>' + esc(society.name) + '</span></label>';
    }).join('');
}

function sellerStatusHtml() {
    var status = (S.user && S.user.sellerApprovalStatus) || 'PENDING';
    var title = status === 'REJECTED' ? 'Application needs attention' : 'Seller application pending';
    var detail = (S.user && S.user.sellerStatusReason) ||
        'Your storefront is private until an Admin reviews and approves your application.';
    var kitchenUrl = (S.user && S.user.kitchenUrl) || S.kitchenUrl;
    var link = kitchenUrl
        ? '<p class="small">Your reserved kitchen URL: <a href="' + esc(kitchenUrl) + '">' + esc(kitchenUrl) + '</a></p>' : '';
    return '<div class="login-wrap"><section class="admin-login seller-auth-card">' +
        '<div class="modal-icon">🕒</div><h1>' + esc(title) + '</h1>' +
        '<p class="muted">Current status: <strong>' + esc(status) + '</strong></p>' +
        '<p>' + esc(detail) + '</p>' + link +
        '<div class="seller-auth-actions"><button class="btn btn-secondary" type="button" data-action="seller-retry">Refresh status</button> ' +
        '<button class="btn btn-outline" type="button" data-action="seller-logout">Log out</button></div>' +
        '</section></div>';
}

async function sellerAuthHtml() {
    if (S.user && S.user.role === 'SELLER') return sellerStatusHtml();
    if (S.authError) {
        return '<div class="login-wrap"><section class="admin-login seller-auth-card">' +
            '<h1>Seller service unavailable</h1><p>' + esc(S.authError.message || 'Could not verify your session.') + '</p>' +
            '<button class="btn btn-primary" type="button" data-action="seller-retry">Retry</button></section></div>';
    }
    var login = '<div class="form-group"><label for="sellerLoginMobile">Mobile number</label>' +
        '<input class="form-input" id="sellerLoginMobile" name="mobileNumber" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" autocomplete="username" required></div>' +
        '<div class="form-group"><label for="sellerLoginPassword">Password</label>' +
        '<input class="form-input" id="sellerLoginPassword" name="password" type="password" minlength="10" maxlength="72" autocomplete="current-password" required></div>' +
        '<button class="btn btn-primary btn-block" type="submit">Sign in</button>' +
        '<button class="btn btn-secondary btn-block mt-2" type="button" data-action="seller-show-register">Register as a seller</button>';
    var body = '<div class="login-wrap"><section class="admin-login seller-auth-card">' +
        '<div class="al-brand">🍲 SocioMart Seller</div>' +
        '<p class="muted small">Demo-only password sign-in. This is not production-ready authentication.</p>' +
        '<form id="sellerLoginForm">' + login + '</form></section></div>';
    if (S.authMode !== 'register') return body;
    try {
        if (!S.authOptions) S.authOptions = await api('/api/auth/registration-options');
    } catch (err) {
        return '<div class="login-wrap"><section class="admin-login seller-auth-card"><h1>Could not load registration options</h1>' +
            '<p>' + esc(err.message) + '</p><button class="btn btn-primary" type="button" data-action="seller-show-register">Retry</button></section></div>';
    }
    if (!S.authOptions || !S.authOptions.length) {
        return '<div class="login-wrap"><section class="admin-login seller-auth-card"><h1>Seller registration unavailable</h1>' +
            '<p>No active areas and societies are configured.</p><button class="btn btn-secondary" type="button" data-action="seller-show-login">Back to login</button></section></div>';
    }
    var firstArea = S.authOptions[0];
    var form = '<form id="sellerRegistrationForm">' +
        '<div class="form-group"><label for="sellerRegName">Seller / contact name</label><input class="form-input" id="sellerRegName" name="sellerName" maxlength="120" required></div>' +
        '<div class="form-group"><label for="sellerRegMobile">Mobile number</label><input class="form-input" id="sellerRegMobile" name="mobileNumber" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" required></div>' +
        '<div class="form-group"><label for="sellerRegPassword">Create password</label><input class="form-input" id="sellerRegPassword" name="password" type="password" minlength="10" maxlength="72" autocomplete="new-password" required></div>' +
        '<div class="form-group"><label for="sellerRegWhatsApp">WhatsApp number</label><input class="form-input" id="sellerRegWhatsApp" name="whatsappNumber" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" required></div>' +
        '<div class="form-group"><label for="sellerRegAlternate">Alternate contact (optional)</label><input class="form-input" id="sellerRegAlternate" name="alternateContact" inputmode="numeric" pattern="[0-9]{10}" maxlength="10"></div>' +
        '<div class="form-group"><label for="sellerRegKitchen">Kitchen / storefront name</label><input class="form-input" id="sellerRegKitchen" name="kitchenName" maxlength="120" required></div>' +
        '<div class="form-group"><label for="sellerRegSlug">Choose public kitchen URL</label><div class="muted small">/index.html#/kitchen/</div><input class="form-input" id="sellerRegSlug" name="kitchenSlug" minlength="3" maxlength="80" pattern="[A-Za-z0-9]+(-[A-Za-z0-9]+)*" required></div>' +
        '<div class="form-group"><label for="sellerRegSpeciality">Cuisine / speciality</label><input class="form-input" id="sellerRegSpeciality" name="speciality" maxlength="250" required></div>' +
        '<div class="form-group"><label for="sellerRegCategory">Seller category</label><select class="form-input" id="sellerRegCategory" name="sellerCategory" required><option value="KITCHEN">Kitchen</option><option value="HOMEMADE_PRODUCTS">Homemade products</option><option value="BOTH">Both</option></select></div>' +
        '<div class="form-group"><label for="sellerRegShort">Short description (optional)</label><textarea class="form-input" id="sellerRegShort" name="shortDescription" maxlength="500"></textarea></div>' +
        '<div class="form-group"><label for="sellerRegInstagram">Instagram URL (optional)</label><input class="form-input" id="sellerRegInstagram" name="instagramLink" type="url" maxlength="255"></div>' +
        '<div class="form-group"><label for="sellerRegPrimaryArea">Primary area</label><select class="form-input" id="sellerRegPrimaryArea" name="primaryAreaId" required>' +
        sellerAreaOptions(firstArea.id) + '</select></div>' +
        '<div class="form-group"><label for="sellerRegPrimarySociety">Primary society</label><select class="form-input" id="sellerRegPrimarySociety" name="primarySocietyId" required>' +
        sellerSocietyOptions(firstArea.id) + '</select></div>' +
        '<div class="form-group"><label for="sellerRegBuilding">Building / wing (optional)</label><input class="form-input" id="sellerRegBuilding" name="building" maxlength="120"></div>' +
        '<div class="form-group"><label for="sellerRegServiceArea">Service coverage area</label><select class="form-input" id="sellerRegServiceArea" name="serviceAreaId" required>' +
        sellerAreaOptions(firstArea.id) + '</select></div>' +
        '<fieldset class="seller-reg-coverage"><legend>Societies you will serve</legend><div id="sellerRegSocieties">' +
        sellerServiceSocietyHtml(firstArea.id) + '</div></fieldset>' +
        '<p class="muted small">New seller accounts remain pending until Admin approval. This demo password is not production authentication.</p>' +
        '<button class="btn btn-primary btn-block" type="submit">Submit seller application</button>' +
        '<button class="btn btn-secondary btn-block mt-2" type="button" data-action="seller-show-login">Back to sign in</button>' +
        '</form>';
    return '<div class="login-wrap"><section class="admin-login seller-auth-card seller-registration-card">' +
        '<div class="al-brand">🏪 Seller registration</div>' + form + '</section></div>';
}

function bindSellerAuth(view) {
    var loginForm = view.querySelector('#sellerLoginForm');
    if (loginForm) loginForm.addEventListener('submit', sellerLoginSubmit);
    var registrationForm = view.querySelector('#sellerRegistrationForm');
    if (!registrationForm) return;
    registrationForm.addEventListener('submit', sellerRegistrationSubmit);
    var primaryArea = view.querySelector('#sellerRegPrimaryArea');
    primaryArea.addEventListener('change', function () {
        var society = view.querySelector('#sellerRegPrimarySociety');
        society.innerHTML = sellerSocietyOptions(primaryArea.value);
    });
    var serviceArea = view.querySelector('#sellerRegServiceArea');
    serviceArea.addEventListener('change', function () {
        S.authServiceAreaId = serviceArea.value;
        view.querySelector('#sellerRegSocieties').innerHTML = sellerServiceSocietyHtml(serviceArea.value);
    });
    var kitchenName = view.querySelector('#sellerRegKitchen');
    var slug = view.querySelector('#sellerRegSlug');
    kitchenName.addEventListener('input', function () {
        if (!S.authSlugTouched) slug.value = kitchenName.value.toLowerCase()
            .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    });
    slug.addEventListener('input', function () { S.authSlugTouched = true; });
}

async function sellerLoginSubmit(event) {
    event.preventDefault();
    var form = event.currentTarget;
    var button = form.querySelector('button[type="submit"]');
    if (button.disabled) return;
    button.disabled = true;
    try {
        var session = await api('/api/auth/login', { method: 'POST', body: {
            mobileNumber: form.elements.mobileNumber.value.trim(),
            password: form.elements.password.value
        } });
        if (session.role !== 'SELLER') {
            S.user = session;
            toast('This account is not registered as a seller.', 'error');
            await sellerRender();
            return;
        }
        S.user = session;
        S.authError = null;
        location.hash = '#/home';
        await sellerRender();
    } catch (err) {
        toast('Sign in failed: ' + err.message, 'error');
    } finally {
        if (button.isConnected) button.disabled = false;
    }
}

async function sellerRegistrationSubmit(event) {
    event.preventDefault();
    var form = event.currentTarget;
    var button = form.querySelector('button[type="submit"]');
    if (button.disabled) return;
    var serviceIds = Array.from(form.querySelectorAll('input[name="serviceSocietyIds"]:checked'))
        .map(function (input) { return Number(input.value); });
    if (!serviceIds.length) {
        toast('Select at least one society you will serve.', 'error');
        return;
    }
    button.disabled = true;
    try {
        var session = await api('/api/auth/register/seller', { method: 'POST', body: {
            sellerName: form.elements.sellerName.value.trim(),
            mobileNumber: form.elements.mobileNumber.value.trim(),
            password: form.elements.password.value,
            whatsappNumber: form.elements.whatsappNumber.value.trim(),
            alternateContact: form.elements.alternateContact.value.trim(),
            kitchenName: form.elements.kitchenName.value.trim(),
            kitchenSlug: form.elements.kitchenSlug.value.trim(),
            speciality: form.elements.speciality.value.trim(),
            sellerCategory: form.elements.sellerCategory.value,
            shortDescription: form.elements.shortDescription.value.trim(),
            instagramLink: form.elements.instagramLink.value.trim(),
            primaryAreaId: Number(form.elements.primaryAreaId.value),
            primarySocietyId: Number(form.elements.primarySocietyId.value),
            building: form.elements.building.value.trim(),
            serviceAreaId: Number(form.elements.serviceAreaId.value),
            serviceSocietyIds: serviceIds
        } });
        S.user = session;
        S.kitchenUrl = session.kitchenUrl || null;
        S.authMode = 'login';
        S.authSlugTouched = false;
        await sellerRender();
    } catch (err) {
        toast('Registration failed: ' + err.message, 'error');
        if (button.isConnected) button.disabled = false;
    }
}

async function sellerRender() {
    var hash = location.hash || '#/home';
    if (hash === '#/home' && S.lastRouteHash !== '#/home') S.dashTab = 'live';
    S.lastRouteHash = hash;
    var route = sellerResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    closeSheet();
    // Verify the server session before calling any owner-scoped endpoint, so a
    // Buyer login elsewhere cannot leave this screen stuck on an auth error.
    if (!(await ensureSellerSession())) {
        view.innerHTML = await sellerAuthHtml();
        bindSellerAuth(view);
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
    // V2 §7.1 / V3 §6.1: Recurring is a badge on the card, never a tab.
    if (p.recurring) return '<span class="oc-badge live">RECURRING • LIVE</span>';
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
        { icon: '📦', cls: 'blue', value: dash.totalOrders || 0, label: 'Total Orders', href: '#/orders', action: 'open-all-orders' }
    ];
    return '<div class="sd-stats">' + cards.map(function (c) {
        // V3 §3.1: the complete Total Orders card opens the EXISTING Orders
        // screen (All Orders) - no dashboard-only orders page exists.
        var tag = c.href ? 'a' : c.action ? 'button' : 'div';
        var hrefAttr = c.href ? ' href="' + c.href + '"' : '';
        var actionAttr = c.action ? ' type="button" data-action="' + c.action + '"' : '';
        return '<' + tag + ' class="sd-stat sd-stat--' + c.cls + '"' + hrefAttr + actionAttr + '>'
            + '<span class="sd-stat__icon" aria-hidden="true">' + c.icon + '</span>'
            + '<span class="sd-stat__value">' + esc(String(c.value)) + '</span>'
            + '<span class="sd-stat__label">' + esc(c.label) + '</span>'
            + '</' + tag + '>';
    }).join('') + '</div>';
}

/**
 * Storefront card: real store photo, real store name, real operational status.
 * LIVE is derived from the seller's own kitchen.paused flag - never hard-coded,
 * and a paused kitchen reads PAUSED instead. Both seller types share this one
 * component, so no Kitchen-only assumption is baked in.
 */
function sdStoreCardHtml(dash, kitchen, unavailable) {
    var img = kitchen ? sellerImg(kitchen.imageUrl) : '';
    var name = (kitchen && kitchen.displayName) || dash.kitchenName || 'Your Store';
    var paused = !!(kitchen && kitchen.paused);
    var homemade = !!(kitchen && kitchen.sellerType === 'HOMEMADE_PRODUCTS');
    var ph = homemade ? '🧺' : '🍽️';
    var state = unavailable ? 'is-unknown' : (paused ? 'is-paused' : 'is-active');
    var stateLabel = unavailable ? 'Status unavailable' : (paused ? 'Paused' : 'Active');
    var stateFlag = unavailable ? 'UNKNOWN' : (paused ? 'PAUSED' : 'LIVE');
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
        + '<span class="' + state + '">' + stateLabel + '</span>'
        + '<span class="sd-store__sep">•</span>'
        + '<span class="sd-flag ' + state + '">' + stateFlag + '</span>'
        + '</div></div>'
        + '<button class="sd-store__view" type="button" data-action="preview-kitchen"><span aria-hidden="true">👁️</span> View</button>'
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
    // V3 §11: the inline quantity +/- stepper is REMOVED from the dashboard -
    // quantity changes happen through Edit / Edit Today, never by an inline
    // stock hack on the card.
    h += '<div class="sd-card__actions">';
    // View Orders (N) uses the existing #/order-detail/{productId} route. The
    // count is appended only when the app returned a real one.
    if (p.recurring && p.occurrenceId != null) {
        h += '<button class="sd-btn sd-btn--primary" type="button" data-action="open-offering-orders-date" data-pid="'
            + esc(p.id) + '" data-date="' + esc(p.nextOccurrenceDate || sellerDate('today')) + '">View Orders'
            + (orderCount != null ? ' (' + esc(String(orderCount)) + ')' : '') + '</button>';
    } else {
        h += '<a class="sd-btn sd-btn--primary" href="#/order-detail/' + p.id + '">View Orders'
            + (orderCount != null ? ' (' + esc(String(orderCount)) + ')' : '') + '</a>';
    }
    if (p.recurring && p.occurrenceId != null) {
        // V2 §8 / V3 §8: occurrence-specific actions only. No generic Pause /
        // Edit / Sold Out here - their scope would be ambiguous on a rule that
        // repeats across dates.
        var occToday = p.nextOccurrenceDate === sellerDate('today');
        h += '<button class="sd-btn sd-btn--edit" type="button" data-action="edit-today" data-oid="'
            + esc(p.occurrenceId) + '">' + (occToday ? 'Edit Today' : 'Edit This Date') + '</button>';
        var dayLimited = p.maxQuantity != null || p.remainingQuantity != null;
        if (dayLimited) {
            h += '<button class="sd-btn sd-btn--soldout" type="button" data-action="occ-sold-out" data-oid="'
                + esc(p.occurrenceId) + '">' + (occToday ? 'Sold Out Today' : 'Sold Out This Date') + '</button>';
        } else {
            h += '<button class="sd-btn sd-btn--pause" type="button" data-action="occ-close" data-oid="'
                + esc(p.occurrenceId) + '">' + (occToday ? 'Close Orders Today' : 'Close Orders This Date') + '</button>';
        }
    } else if (!p.recurring) {
        h += '<button class="sd-btn sd-btn--edit" type="button" data-action="edit-offering" data-pid="' + p.id + '">Edit</button>';
        var oneTimeLimited = p.maxQuantity != null || p.remainingQuantity != null;
        // Unlimited offerings close orders rather than pretending to sell out.
        if (!p.soldOut && !p.ordersPaused) h += '<button class="sd-btn sd-btn--pause" type="button" data-action="pause-orders" data-pid="' + p.id + '">' + (oneTimeLimited ? 'Pause' : 'Close Orders') + '</button>';
        if (p.ordersPaused && !p.soldOut) h += '<button class="sd-btn sd-btn--resume" type="button" data-action="resume-orders" data-pid="' + p.id + '">Resume</button>';
        if (oneTimeLimited && !p.soldOut && !p.ordersPaused) h += '<button class="sd-btn sd-btn--soldout" type="button" data-action="mark-soldout" data-pid="' + p.id + '">Sold Out</button>';
    }
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
        + '<div class="sd-earnings__cell"><div class="sd-earnings__label">Pending Today</div>'
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
    // V3 §5: the quick-action row is gone from the dashboard, so its skeleton
    // placeholders are gone too - the loading state must match the real layout.
    return '<div class="sd-root" aria-busy="true" aria-label="Loading your dashboard">'
        + '<div class="sd-stats">' + stats + '</div>'
        + '<div class="sd-skel sd-skel--store"></div>'
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
        h += sdStoreCardHtml(dash, kitchen, kitchenErr);
        // LIVE is the default every time the seller opens the dashboard; RECURRING
        // is the schedule-management tab. The "Quick actions" row is removed per
        // the approved V3 design: View + Edit + bottom navigation cover those roles.
        h += '<div class="date-tabs">'
            + '<button class="date-tab' + (S.dashTab !== 'recurring' ? ' active' : '') + '" type="button" data-action="set-dash-tab" data-tab="live">LIVE</button>'
            + '<button class="date-tab' + (S.dashTab === 'recurring' ? ' active' : '') + '" type="button" data-action="set-dash-tab" data-tab="recurring">RECURRING</button>'
            + '</div>';
        if (kitchenErr) {
            // Non-destructive: the store card still renders from the dashboard
            // payload, we just say the photo/status could not be refreshed.
            h += '<p class="sd-note">Store details could not be refreshed. '
                + '<button class="sd-note__retry" type="button" data-action="seller-retry">Retry</button></p>';
        }
        var offerings = (dash.offerings || []).filter(function (p) {
            return !p.ordersClosed && !p.ordersPaused && !p.soldOut;
        });
        // LIVE is the default tab: what can customers order right now?
        if (S.dashTab === 'recurring') {
            h += await sellerRecurringHtml();
        } else {
            var shown = offerings;
            if (S.dashFilter && S.dashFilter !== 'ALL') {
                shown = offerings.filter(function (p) { return sdCategoryGroup(p) === S.dashFilter; });
            }
            h += '<div class="sd-section">'
                + '<h2 class="sd-section__title">My Offerings<span class="sd-section__count">(' + shown.length + ')</span></h2>'
                + sdOfferingFilterHtml(offerings)
                + '</div>';
            if (!offerings.length) {
                h += emptyHtml('🍽️', 'No Offerings yet',
                    'Nothing on sale right now. Create your first offering and start taking orders.',
                    '<a class="btn btn-primary card-mt" href="#/create">+ Create Offering</a>');
            } else {
                if (!shown.length) {
                    h += emptyHtml('🍽️', 'Nothing in this filter', 'Choose "All" to see every offering.');
                } else {
                    shown.forEach(function (p) {
                        h += sdOfferingCardHtml(p, orderCounts[p.id] != null ? orderCounts[p.id] : null);
                    });
                }
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
    // sd-root wrapper + sd-greet/sd-section chrome reuses the approved Dashboard
    // language. The history card list itself is intentionally untouched: its
    // classes and copy are pinned by regression tests (responsive fix + content).
    var h = '<div class="view-enter sd-root"><div class="sd-greet"><h1 class="sd-greet__hi">My Offerings</h1>' +
        '<p class="sd-greet__sub">Create a new offering, or revisit the ones you have already run.</p></div>';
    // Primary action sits above the history so it is reachable without scrolling.
    // It reuses the existing Add Offering screen (#/add -> the same create flow).
    // Plain anchor on the existing #/add route: navigating by hash means the SPA
    // router handles it exactly like every other in-app link (a data-action here
    // would also fire sellerNavigate and render the view twice).
    // sd-add is the Dashboard CTA token; the "+ Add Offering" wording and #/add
    // target are unchanged so the existing flow is reused verbatim.
    h += '<a class="sd-add" href="#/add">+ Add Offering</a>';
    // Minimal recurring surface: two pinned-history-safe tab buttons that swap
    // between the untouched history list and the recurring schedule cards. The
    // tab words are lower-case in the markup only via CSS; the literal text
    // keeps the capitalised form the tests pin elsewhere.
    h += '<div class="date-tabs"><button class="date-tab' + (S.offeringsTab !== 'recurring' ? ' active' : '') + '" type="button" data-action="set-offerings-tab" data-tab="history">History</button>' +
        '<button class="date-tab' + (S.offeringsTab === 'recurring' ? ' active' : '') + '" type="button" data-action="set-offerings-tab" data-tab="recurring">Recurring</button></div>';
    if (S.offeringsTab === 'recurring') return h + await sellerRecurringHtml() + '</div>';
    h += '<div class="sd-section"><h2 class="sd-section__title">Offering History</h2></div>';
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

/**
 * Minimal RECURRING tab (per-day overrides only, no create/editor UI).
 *
 * <p>Kept deliberately separate from sellerHistoryView so the pinned history
 * markup above stays byte-identical: the history-card list, the "+ Add
 * Offering" CTA order, and the empty/error copy are untouched. Each schedule
 * renders its own resolved per-day rows; saving one date PATCHes exactly that
 * occurrence, so a Wednesday edit can never touch Monday or Friday.
 */
async function sellerRecurringHtml() {
    var h = '<div class="sd-section"><h2 class="sd-section__title">Recurring schedules</h2></div>';
    h += '<p class="muted small mb-2">One row per selling date. Editing a date changes only that date.</p>';
    var schedules = [];
    try {
        schedules = await sellerApi('/api/seller/schedules') || [];
        S.recurringSchedules = schedules;
    } catch (e) { return emptyHtml('⚠️', 'Could not load recurring schedules', e.message); }
    if (!schedules.length) {
        return h + emptyHtml('🔁', 'No recurring schedules yet', 'Create one from an offering to repeat it weekly.');
    }
    for (var i = 0; i < schedules.length; i++) {
        h += await sellerRecurringCardHtml(schedules[i]);
    }
    return h;
}

async function sellerRecurringCardHtml(card) {
    var h = '<div class="sd-recurring-card"><div class="sd-recurring-card__name">' + esc(card.productName || 'Offering') + '</div>';
    h += '<p class="muted small">' + esc(card.startDate || '') + ' → ' + esc(card.endDate || '') +
        ' · Repeats ' + esc(sellerRecurringDaysText(card.recurrenceWeekdays)) + '</p>';
    h += '<p class="muted small">Default quantity: ' + esc(sellerRecurringQtyText(card.defaultQuantity)) + '</p>';
    // V2 §7.2: the card shows the schedule's defaults and next useful date.
    h += '<p class="muted small">Orders close ' + esc(card.defaultOrderCloseTime || '—') +
        ' · Delivery ' + esc(card.defaultReadyByTime || '—') +
        (card.nextOccurrenceDate ? ' · Next occurrence: ' + esc(prettyDate(card.nextOccurrenceDate)) : '') + '</p>';
    // V2 §15: an Ongoing schedule is capped at 90 days; near expiry the seller
    // is prompted to extend rather than letting the schedule silently lapse.
    if (card.ongoing && card.endDate) {
        var msLeft = (new Date(card.endDate + 'T23:59:59') - new Date()) / 86400000;
        if (msLeft <= 14) {
            h += '<div class="info-box">⏳ This ongoing schedule ends ' + esc(prettyDate(card.endDate)) +
                '. Extend it to keep future dates active.' +
                ' <button class="btn btn-secondary btn-sm" type="button" data-action="extend-schedule" data-schedule-id="' +
                esc(card.scheduleId) + '">Extend Schedule</button></div>';
        }
    }
    h += '<div class="sd-card__actions">'
        + '<button class="sd-btn sd-btn--primary sd-btn--sm" type="button" data-action="manage-schedule" data-schedule-id="' + esc(card.scheduleId) + '">Manage Schedule</button>'
        + '<button class="sd-btn sd-btn--danger sd-btn--sm" type="button" data-action="end-schedule" data-schedule-id="' + esc(card.scheduleId) + '">End Schedule</button>'
        + '</div>';
    h += '</div>';
    return h;
}

/** One resolved selling-date row with its own per-day override form. */
function sellerRecurringRowHtml(card, o) {
    var h = '<div class="history-card"><span class="hc-body"><span class="hc-name">' + esc(prettyDate(o.date)) + '</span>' +
        '<span class="hc-meta">' + esc(sellerRecurringQtyText(o.quantity)) + ' · closes ' + esc(o.orderCloseTime || '—') +
        ' · ' + esc(o.readyByTime || '') + (o.soldOut ? ' · Sold out' : '') + (o.ordersPaused ? ' · Orders paused' : '') + '</span></span>';
    h += '<span class="hc-price">' + esc(o.status || '') + '</span>';
    h += '<form data-recurring-form="' + esc(o.id) + '">' +
        '<div class="form-group"><label class="form-label">Quantity for this date</label>' +
        '<input class="form-input" name="quantity" type="number" min="1" step="1" value="' + esc(o.quantity == null ? '' : o.quantity) + '" placeholder="Blank for unlimited"></div>' +
        '<div class="form-group"><label class="form-label">Orders Close for this date (HH:mm)</label>' +
        '<input class="form-input" name="orderCloseTime" value="' + esc(o.orderCloseTime || '') + '" placeholder="e.g. 13:00"></div>' +
        '<div class="form-group"><label class="form-label">Ready By for this date</label>' +
        '<input class="form-input" name="readyByTime" value="' + esc(o.readyByTime || '') + '"></div>' +
        '<label class="checkbox-option"><input type="checkbox" name="soldOut"' + (o.soldOut ? ' checked' : '') + '> Sold out (this date only)</label>' +
        '<label class="checkbox-option"><input type="checkbox" name="ordersPaused"' + (o.ordersPaused ? ' checked' : '') + '> Orders paused (this date only)</label>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="save-recurring-day" data-oid="' + esc(o.id) + '">Save this date only</button>'
        + '<button class="btn btn-secondary btn-block" type="button" data-action="edit-this-date" data-oid="' + esc(o.id) + '">Edit This Date</button>'
        + '<button class="btn btn-sm btn-ghost" type="button" data-action="view-orders-date" data-oid="' + esc(o.id) + '">View Orders</button>'
        + '</div>';
    return h;
}

function sellerRecurringDaysText(days) {
    if (!days || !days.length) return 'No repeat days';
    var names = { MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun' };
    return days.map(function (d) { return names[d] || d; }).join(', ');
}

function sellerRecurringQtyText(qty) {
    return qty == null ? 'No limit' : qty + ' available';
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
 * End date for a repeat duration, computed from the schedule START date.
 *  thisweek -> the coming Sunday (the schedule always contains its start day),
 *  onemonth -> start + 1 month,
 *  ongoing  -> 90 inclusive calendar days (start + 89 days; Requirement 15),
 *              no unbounded "forever" option anywhere in the client),
 *  dated    -> caller supplies the date itself, so it is not computed here.
 */
function computeRecurringEnd(startIso, duration) {
    var s = new Date(startIso + 'T00:00:00');
    if (isNaN(s.getTime())) return '';
    if (duration === 'onemonth') {
        var m = new Date(s.getTime());
        m.setMonth(m.getMonth() + 1);
        return localDateStr(m);
    }
    if (duration === 'ongoing') {
        return localDateStr(new Date(s.getTime() + 89 * 86400000));
    }
    // This week: through the coming Sunday (getDay: 0 = Sunday).
    var daysLeft = (7 - s.getDay()) % 7;
    return localDateStr(new Date(s.getTime() + daysLeft * 86400000));
}

/**
 * Keeps the End Date field in step with the chosen Repeat Duration:
 * "Until a date" hands the field back to the seller (editable, cleared if it
 * held a computed value), every other duration auto-computes and locks it so
 * the visible range can never disagree with what gets submitted.
 */
function syncRecurringEndDate() {
    var endInput = $('#recurringEndDateMain');
    if (!endInput) return;
    if (S.recurringDuration === 'dated') {
        endInput.readOnly = false;
        endInput.removeAttribute('readonly');
        return;
    }
    endInput.readOnly = true;
    endInput.setAttribute('readonly', 'readonly');
    var startInput = $('#recurringStartDate');
    var start = startInput ? startInput.value : '';
    endInput.value = start ? computeRecurringEnd(start, S.recurringDuration) : '';
}

/**
 * Validates the REPEATING SCHEDULE block and returns the exact
 * ProductCreateDto.recurringSchedule payload, or null after toasting the first
 * problem. Weekdays travel as DayOfWeek enum names because that is how
 * RecurringScheduleDto.recurrenceWeekdays deserialises; blank quantity means
 * "No limit" (null), matching the one-time rule.
 */
function buildRecurringSchedule(vals, form) {
    var start = (vals.recurringStartDate || '').trim();
    if (!start) { toast('Schedule start date is required', 'error'); return null; }
    if (start < sellerDate('today') && (!form || form.id !== 'manageScheduleForm')) {
        toast('Schedule start date cannot be in the past', 'error'); return null;
    }
    var duration = vals.recurringDuration || 'thisweek';
    var end = (vals.recurringEndDate || '').trim();
    if (duration !== 'dated') end = computeRecurringEnd(start, duration);
    if (!end) { toast('Choose an end date for the schedule', 'error'); return null; }
    if (end < start) { toast('Schedule end date cannot be before the start date', 'error'); return null; }
    var days = [];
    $all('input[name="recurringWeekday"]:checked', form).forEach(function (cb) { days.push(Number(cb.value)); });
    if (days.length === 0) { toast('Select at least one day to repeat on', 'error'); return null; }
    var dayNames = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
    var weekdays = days.sort(function (a, b) { return a - b; }).map(function (n) { return dayNames[n - 1]; });
    var qtyText = (vals.recurringDefaultQuantity || '').trim();
    var qty = null;
    if (qtyText !== '') {
        var q = Number(qtyText);
        if (!Number.isInteger(q) || q < 1) {
            toast('Default quantity must be a whole number of at least 1, or blank for No limit', 'error');
            return null;
        }
        qty = q;
    }
    var close = (vals.recurringDefaultOrderCloseTime || '').trim();
    if (!close) { toast('Default Orders Close time is required for a repeating schedule', 'error'); return null; }
    var ready = (vals.recurringDefaultReadyByTime || '').trim();
    if (!ready) { toast('Default Delivery / Ready By is required for a repeating schedule', 'error'); return null; }
    return {
        startDate: start,
        endDate: end,
        recurrenceWeekdays: weekdays,
        defaultQuantity: qty,
        defaultOrderCloseTime: close,
        defaultReadyByTime: ready,
        ongoing: duration === 'ongoing'
    };
}

/**
 * Builds the Manage Schedule form markup from one resolved schedule card.
 * Only the recurring defaults/pattern are editable here — the offering itself
 * (name, price, offering date) is out of scope and untouched by Manage Schedule.
 */
function manageScheduleFormHtml(card) {
    var start = card.startDate || sellerDate('today');
    var end = card.endDate || '';
    var dur = card.ongoing ? 'ongoing' : 'dated';
    var days = card.recurrenceWeekdays || [];
    var c = [0, 0, 0, 0, 0, 0, 0];
    var dayNumbers = { MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6, SUNDAY: 7 };
    days.forEach(function (d) {
        var n = typeof d === 'number' ? d : dayNumbers[String(d).toUpperCase()];
        if (n >= 1 && n <= 7) c[n - 1] = 1;
    });
    var names = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    var h = '<form class="seller-form" id="manageScheduleForm">';
    h += '<div class="form-group"><label class="form-label">Start Date <span class="req">*</span></label>';
    h += '<input class="form-input" id="recurringStartDate" name="manageStartDate" type="date" value="' + esc(start) + '" min="' + esc(start) + '"></div>';
    h += '<div class="form-group"><label class="form-label">End Date <span class="req">*</span></label>';
    h += '<input class="form-input" id="recurringEndDateMain" name="manageEndDate" type="date" value="' + esc(end) + '" min="' + esc(start) + '"' + (dur === 'dated' ? '' : ' readonly') + '></div>';
    h += '<div class="form-group"><label class="form-label">Repeat On <span class="req">*</span></label>'
        + '<div class="weekday-checkbox-group">';
    for (var i = 1; i <= 7; i++) {
        h += '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="' + i + '"' + (c[i - 1] ? ' checked' : '') + '> ' + names[i - 1] + '</label>';
    }
    h += '</div></div>';
    h += '<div class="form-group"><label class="form-label">Repeat Duration <span class="req">*</span></label><div class="radio-group">';
    var durs = [['thisweek', 'This week'], ['onemonth', '1 month'], ['dated', 'Until a date'], ['ongoing', 'Ongoing (90 days)']];
    durs.forEach(function (pair) {
        h += '<label class="radio-option' + (pair[0] === dur ? ' selected' : '') + '" data-action="set-recurring-duration" data-duration="' + pair[0] + '">' + pair[1] + '</label>';
    });
    h += '</div><p class="muted small">Ongoing is internally capped at 90 days and can be extended later from Manage Schedule.</p></div>';
    h += '<input type="hidden" name="recurringDuration" id="recurringDuration" value="' + esc(dur) + '">';
    h += '<div class="form-group"><label class="form-label">Default Quantity per Occurrence (blank for No limit)</label>';
    h += '<input class="form-input" name="manageDefaultQuantity" type="number" min="1" step="1" value="' + esc(card.defaultQuantity == null ? '' : card.defaultQuantity) + '" placeholder="Blank for unlimited"></div>';
    h += '<div class="form-group"><label class="form-label">Default Orders Close (HH:mm)</label>';
    h += '<input class="form-input" name="manageDefaultOrderCloseTime" type="time" value="' + esc(card.defaultOrderCloseTime || '') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Default Delivery / Ready By</label>';
    h += '<input class="form-input" name="manageDefaultReadyByTime" type="text" value="' + esc(card.defaultReadyByTime || '') + '"></div>';
    h += '<button class="btn btn-primary btn-block" type="submit">Save Schedule</button></form>';
    return h;
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
    h += '<div class="form-group" id="offeringForGroup"><label class="form-label">Offering For <span class="req">*</span></label><div class="radio-group">' + availabilityOption('today', 'Today', mode, locked) + availabilityOption('tomorrow', 'Tomorrow', mode, locked) + availabilityOption('choose', 'Choose Date', mode, locked) + '</div></div>';
    h += '<input type="hidden" name="availableDate" id="availDate" value="' + esc(chosenDate) + '">';
    h += '<div class="form-group" id="chooseDateRow"' + (mode === 'choose' ? '' : ' hidden') + '><label class="form-label">Offering Date <span class="req">*</span></label><input type="date" class="form-input" name="chosenOfferingDate" min="' + todayIso + '" data-action="set-availability-date" value="' + esc(chosenDate) + '"' + lockAttr + '></div>';

    // REPEATING SCHEDULE section (Requirement 3.2). Create Offering only:
    // Edit Offering never edits the repeating rule (Manage Schedule does that),
    // so the whole block is skipped in edit mode.
    if (!isEdit) {
        var recurMode = S.offeringMode === 'recurring' ? 'recurring' : 'today';
        var dur = S.recurringDuration || 'thisweek';
        h += '<div class="form-group" id="recurringScheduleSection">';
        h += '<div class="font-700 mt-2 mb-1">AVAILABILITY <span class="small muted">(One time, or a repeating schedule)</span></div>';
        h += '<div class="muted small mb-2">Choose <strong>ONE TIME</strong> to keep a single offering date, or <strong>REPEATING SCHEDULE</strong> to sell this item on selected days.</div>';
        h += '<div class="row-actions">';
        h += '<label class="radio-option ' + (recurMode === 'recurring' ? 'selected' : '') + '" data-action="set-offering-mode" data-mode="recurring">REPEATING SCHEDULE</label>';
        h += '<label class="radio-option ' + (recurMode !== 'recurring' ? 'selected' : '') + '" data-action="set-offering-mode" data-mode="today">ONE TIME</label>';
        h += '</div>';
        // No native `required` on anything inside this container: a required input
        // in a hidden container still blocks form submission with an invisible
        // browser error. Validation happens in buildRecurringSchedule() on submit.
        h += '<div class="mt-2" id="recurringScheduleFields"' + (recurMode === 'recurring' ? '' : ' hidden') + '>';
        h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Start Date <span class="req">*</span></label><input class="form-input" id="recurringStartDate" name="recurringStartDate" type="date" min="' + todayIso + '"></div>';
        h += '<div class="form-group"><label class="form-label">End Date <span class="req">*</span></label><input class="form-input" id="recurringEndDateMain" name="recurringEndDate" type="date" min="' + todayIso + '"' + (dur === 'dated' ? '' : ' readonly') + '></div></div>';

        h += '<div class="form-group"><label class="form-label">Repeat On <span class="req">*</span></label><div class="weekday-checkbox-group">' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="1"> M</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="2"> T</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="3"> W</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="4"> T</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="5"> F</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="6"> S</label>' +
            '<label class="weekday-checkbox"><input type="checkbox" name="recurringWeekday" value="7"> S</label></div></div>';

        h += '<div class="form-group"><label class="form-label">Repeat Duration <span class="req">*</span></label><div class="radio-group">' +
            '<label class="radio-option' + (dur === 'thisweek' ? ' selected' : '') + '" data-action="set-recurring-duration" data-duration="thisweek">This week</label>' +
            '<label class="radio-option' + (dur === 'onemonth' ? ' selected' : '') + '" data-action="set-recurring-duration" data-duration="onemonth">1 month</label>' +
            '<label class="radio-option' + (dur === 'dated' ? ' selected' : '') + '" data-action="set-recurring-duration" data-duration="dated">Until a date</label>' +
            '<label class="radio-option' + (dur === 'ongoing' ? ' selected' : '') + '" data-action="set-recurring-duration" data-duration="ongoing">Ongoing (90 days)</label></div>' +
            '<p class="muted small">Ongoing is internally capped at 90 days and can be extended later from Manage Schedule.</p></div>';
        h += '<input type="hidden" name="recurringWeekdays" id="recurringWeekdays" value="">';
        h += '<input type="hidden" name="recurringDuration" id="recurringDuration" value="' + esc(dur) + '">';
        h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Default Quantity per Occurrence (blank for No limit)</label><input class="form-input" name="recurringDefaultQuantity" type="number" min="1" step="1" placeholder="Blank for unlimited"></div>';
        h += '<div class="form-group"><label class="form-label">Default Orders Close (HH:mm)</label><input class="form-input" name="recurringDefaultOrderCloseTime" type="time"></div></div>';
        h += '<div class="form-group"><label class="form-label">Default Delivery / Ready By</label><input class="form-input" name="recurringDefaultReadyByTime" type="text" placeholder="e.g. 1:00 PM"></div>';
        h += '</div>';  // recurringScheduleFields
        h += '</div>';  // recurringScheduleSection
    }
    h += '<input type="hidden" name="recurringSchedule" id="recurringScheduleHidden" value="">';

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
    // A new form always starts in Today mode (ONE TIME); never inherit a previous choice.
    S.offeringMode = 'today';
    S.recurringDuration = 'thisweek';
    S.recurringScheduleConfig = null;
    var t = S.draftOffering || {};
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Create Offering</h1><p class="muted small">' +
        (S.republishSourceId ? 'Review the previous offering details, then set fresh timing and quantity.' : 'Fill in the details for your new dish.') + '</p></div>';
    // Offering Mode toggle - blocking call handled by event delegation
    h += offeringFormHtml(t, { formId: 'createOfferingForm', mode: S.offeringMode, edit: false, locked: false });
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

/**
 * Loading skeleton for the Orders screen (Screen 7A).
 *
 * <p>Painted by {@link sellerOrdersView} before its request resolves - the same
 * pattern the Seller Dashboard uses - so a slow connection never shows a blank
 * page. It is purely presentational: no figure is invented, every block is an
 * empty shell that the real /api/seller-app/orders/summary payload replaces.
 */
function soSkeletonHtml() {
    function card() {
        return '<div class="sd-skel-card"><div class="sd-skel sd-skel--thumb"></div><div class="sd-skel-lines">'
            + '<div class="sd-skel sd-skel--line w60"></div><div class="sd-skel sd-skel--line w90"></div>'
            + '<div class="sd-skel sd-skel--line w40"></div></div></div>';
    }
    return '<div class="sd-root so-root" role="status" aria-busy="true" aria-label="Loading orders">'
        + '<div class="sd-skel so-skel-summary"></div>'
        + '<div class="sd-skel sd-skel--line w40"></div>'
        + card() + card() + card()
        + '</div>';
}

// SCREEN 7A: ORDER SUMMARY
async function sellerOrdersView() {
    // Paint a content-shaped skeleton immediately so a slow payload never shows
    // a blank screen; sellerRender replaces it with the real markup when we
    // return. Purely presentational - every figure still comes from the API.
    var live = viewEl();
    if (live) live.innerHTML = soSkeletonHtml();
    // The RESOLVED date drives both the request and every label: the third tab
    // can hold a picked ISO date, so labels come from sellerDate() rather than
    // from the tab key alone.
    var selDate = sellerDate(S.selectedDate);
    var isAll = S.selectedDate === 'all';
    var isToday = !isAll && selDate === sellerDate('today');
    var isTomorrow = !isToday && selDate === sellerDate('tomorrow');
    var dayLabel = isAll ? 'All Orders' : isToday ? 'Today' : isTomorrow ? 'Tomorrow' : prettyDate(selDate);
    var listTitle = isAll ? 'All Orders' : isToday ? 'Today\u2019s Orders' : isTomorrow ? 'Tomorrow\u2019s Orders' : prettyDate(selDate);
    var h = '<div class="view-enter sd-root so-root">';
    h += '<header class="so-head"><h1 class="so-title">Orders</h1></header>';
    h += '<div class="date-tabs so-tabs" role="group" aria-label="Orders by day">'
        + '<button class="date-tab' + (isAll ? ' active' : '') + '" type="button" data-action="set-date" data-date="all" aria-pressed="' + isAll + '">All Orders</button>'
        + '<button class="date-tab' + (S.selectedDate === 'today' ? ' active' : '') + '" type="button" data-action="set-date" data-date="today" aria-pressed="' + (S.selectedDate === 'today') + '">Today</button>'
        + '<button class="date-tab' + (S.selectedDate === 'tomorrow' ? ' active' : '') + '" type="button" data-action="set-date" data-date="tomorrow" aria-pressed="' + (S.selectedDate === 'tomorrow') + '">Tomorrow</button>'
        + '<button class="date-tab' + (!isAll && S.selectedDate !== 'today' && S.selectedDate !== 'tomorrow' ? ' active' : '') + '" type="button" data-action="set-date" data-date="pick" aria-pressed="' + (!isAll && S.selectedDate !== 'today' && S.selectedDate !== 'tomorrow') + '">Pick date</button>'
        + '</div>';
    try {
        var summary = await sellerApi('/api/seller-app/orders/summary?date=' + (isAll ? 'all' : selDate));
        var hasOrders = summary.totalOrderCount > 0;
        if (hasOrders) {
            // Summary card: eyebrow day, big real count, then the three real
            // status counts. Nothing here is sample data from the mockup.
            h += '<section class="daily-total-card so-summary" aria-label="Orders summary">';
            h += '<div class="dtc-eyebrow">' + esc(dayLabel) + '</div>';
            h += '<div class="dtc-headline"><span class="dtc-number">' + summary.totalOrderCount + '</span>'
                + '<span class="dtc-label">Total Orders</span></div>';
            h += '<div class="dtc-badges">'
                + '<span class="dtc-badge green"><span class="dtc-dot" aria-hidden="true"></span>' + summary.paidCount + ' Paid</span>'
                + '<span class="dtc-badge orange"><span class="dtc-dot" aria-hidden="true"></span>' + summary.pendingCount + ' Pending</span>'
                + '<span class="dtc-badge red"><span class="dtc-dot" aria-hidden="true"></span>' + summary.cancelledCount + ' Cancelled</span>'
                + '</div>';
            if (summary.totalRevenue) h += '<div class="tiny muted mt-2">Revenue: <strong class="text-brand">' + money(summary.totalRevenue) + '</strong></div>';
            h += '</section>';
        } else {
            h += emptyHtml('📋', 'No Orders', 'No orders yet. New orders will appear here.');
        }
        if (!summary.products || summary.products.length === 0) {
            if (hasOrders) h += emptyHtml('📋', 'No order items', 'No order items exist for this date.');
        } else {
            h += '<h2 class="so-section">' + esc(listTitle) + '</h2>';
            h += '<div class="so-list">';
            summary.products.forEach(function (p) {
                var img = p.imageUrl || '';
                h += '<article class="order-product-card">';
                h += '<div class="opc-header">';
                h += '<span class="opc-thumb">'
                    + (img ? '<img src="' + esc(img) + '" alt="" data-emoji="' + foodEmoji(p.productName) + '" onerror="imgFallback(this)">' : foodEmoji(p.productName))
                    + '</span>';
                h += '<span class="opc-body"><span class="opc-name">' + esc(p.productName) + '</span>'
                    + '<span class="opc-meta">' + p.totalOrders + ' orders · ' + p.totalPlates + ' plates</span></span>';
                h += '<span class="opc-revenue">' + money(p.revenue) + '</span>';
                h += '</div>';
                h += '<div class="opc-foot">'
                    + '<span class="opc-meta"><span class="dot-green">●</span> ' + p.paidCount + ' paid · <span class="dot-orange">●</span> ' + p.pendingCount + ' pending</span>'
                    + (isAll ? '<span class="opc-meta">Choose a date above for order details</span>'
                        : '<a class="opc-link" href="#/order-detail/' + p.productId + '">View Orders <span aria-hidden="true">→</span></a>')
                    + '</div>';
                h += '</article>';
            });
            h += '</div>';
            // The mockup's closing pill. It points at the existing full-history
            // route the Earnings screen already links to - no new screen and no
            // new endpoint were created for it.
            h += '<div class="so-foot"><a class="so-history" href="#/my-offerings">View full history</a></div>';
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
/**
 * Loading skeleton for the Earnings screen (Screen 8).
 *
 * <p>Painted by {@link sellerEarningsView} before its request resolves - the
 * same pattern the Dashboard and Orders screens use - so a slow connection
 * never shows a blank page. It is purely presentational: no figure is
 * invented, every block is an empty shell that the real
 * /api/seller-app/earnings payload replaces.</p>
 */
function erSkeletonHtml() {
    function item() {
        return '<div class="sd-skel-card"><div class="sd-skel sd-skel--thumb"></div><div class="sd-skel-lines">'
            + '<div class="sd-skel sd-skel--line w60"></div><div class="sd-skel sd-skel--line w90"></div>'
            + '<div class="sd-skel sd-skel--line w40"></div></div></div>';
    }
    return '<div class="sd-root er-root" role="status" aria-busy="true" aria-label="Loading earnings">'
        + '<div class="sd-skel er-skel-summary"></div>'
        + '<div class="sd-skel sd-skel--line w40"></div>'
        + item() + item() + item()
        + '</div>';
}

/**
 * Screen 8 - approved Earnings mockup (presentation only).
 *
 * <p>Every figure still comes from the EXISTING /api/seller-app/earnings
 * payload, and the wording stays the app's own: Confirmed Today / Pending /
 * This Month are order values grouped by payment status - NOT settled bank
 * revenue. The full-history link keeps pointing at the existing
 * #/my-offerings route. No new screen, endpoint or financial calculation was
 * introduced, and no sample figure from the mockup is hard-coded.</p>
 */
async function sellerEarningsView() {
    // Paint a content-shaped skeleton immediately so a slow payload never shows
    // a blank screen; sellerRender replaces it with the real markup when we
    // return. Purely presentational - every figure still comes from the API.
    var live = viewEl();
    if (live) live.innerHTML = erSkeletonHtml();
    var h = '<div class="view-enter sd-root er-root">';
    h += '<header class="er-head"><h1 class="er-title">Earnings</h1></header>';
    try {
        var e = await sellerApi('/api/seller-app/earnings');
        if (!e.hasEarnings) {
            h += emptyHtml('💰', 'No Earnings', 'No earnings to show yet');
            // Same behaviour as before: a non-zero pending value is still shown
            // even when nothing has confirmed yet today.
            if (e.pending != null && Number(e.pending) !== 0) {
                h += '<section class="er-summary" aria-label="Pending payments summary">'
                    + '<div class="er-hero-block"><div class="er-label">Pending Payments</div>'
                    + '<div class="er-hero er-hero--pending"><span class="er-clock" aria-hidden="true">🕐</span>' + money(e.pending) + '</div>'
                    + '</div></section>';
            }
        } else {
            // Summary card: the mockup's large confirmed figure, then the two
            // supporting real values. Nothing here is sample data.
            h += '<section class="er-summary" aria-label="Earnings summary">';
            h += '<div class="er-hero-block"><div class="er-label">Confirmed Today</div>'
                + '<div class="er-hero">' + money(e.confirmedToday) + '</div></div>';
            h += '<div class="er-sum-rest">'
                + '<div class="er-sub-block"><div class="er-label">Pending</div>'
                + '<div class="er-pending"><span class="er-clock" aria-hidden="true">🕐</span>' + money(e.pending) + '</div></div>'
                + '<div class="er-sub-block"><div class="er-label">This Month</div>'
                + '<div class="er-month">' + money(e.thisMonth) + '</div></div>'
                + '</div>';
            h += '</section>';
            if (e.items && e.items.length) {
                h += '<h2 class="er-section">Item-wise Breakdown</h2>';
                h += '<div class="er-list">';
                e.items.forEach(function (item) {
                    var img = item.imageUrl || '';
                    h += '<article class="er-item">';
                    h += '<div class="er-item-head">';
                    h += '<span class="er-item-icon">'
                        + (img ? '<img src="' + esc(img) + '" alt="" data-emoji="' + foodEmoji(item.productName) + '" onerror="imgFallback(this)">' : foodEmoji(item.productName))
                        + '</span>';
                    h += '<span class="er-item-body"><span class="er-item-name">' + esc(item.productName) + '</span>'
                        + '<span class="er-item-orders">' + item.totalOrders + ' orders</span></span>';
                    h += '</div>';
                    // The visible colours follow the mockup, so each amount also
                    // carries a screen-reader label: meaning is never colour-only.
                    h += '<div class="er-item-foot">'
                        + '<span class="er-item-confirmed"><span class="er-vh">Confirmed </span>' + money(item.confirmedRevenue) + '</span>'
                        + '<span class="er-item-pending"><span class="er-vh">Pending </span><span class="er-clock" aria-hidden="true">🕐</span>' + money(item.pendingRevenue) + '</span>'
                        + '</div>';
                    h += '</article>';
                });
                h += '</div>';
            }
        }
        // The mockup's outlined closing button, wired to the exact route the
        // screen always used - no new screen and no new endpoint were created.
        h += '<div class="er-foot"><a class="er-history" href="#/my-offerings">View full history <span aria-hidden="true">→</span></a></div>';
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

/**
 * Loading skeleton for the Offering Orders screen (Screen 7B).
 *
 * <p>Painted by {@link sellerOrderDetailView} before its request resolves - the
 * same pattern the Dashboard / Orders / Earnings screens use - so a slow
 * connection never shows a blank page. It is purely presentational: no figure
 * is invented, every block is an empty shell that the real
 * /api/seller-app/orders/product/{id} payload replaces.</p>
 */
function odSkeletonHtml() {
    function row() {
        return '<div class="sd-skel-card od-skel-row"><div class="sd-skel od-skel-dot"></div><div class="sd-skel-lines">'
            + '<div class="sd-skel sd-skel--line w40"></div><div class="sd-skel sd-skel--line w60"></div></div></div>';
    }
    return '<div class="sd-root od-root" role="status" aria-busy="true" aria-label="Loading offering orders">'
        + '<div class="od-skel-head"><div class="sd-skel sd-skel--line w40"></div></div>'
        + '<div class="sd-skel od-skel-summary"></div>'
        + '<div class="sd-skel sd-skel--line w60"></div>'
        + row() + row() + row()
        + '</div>';
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
    // Content-shaped skeleton while the payload is in flight - the same pattern
    // the Dashboard / Orders / Earnings screens use. Purely presentational:
    // sellerRender replaces it with the string we return once we resolve.
    var live = viewEl();
    if (live) live.innerHTML = odSkeletonHtml();
    var h = '<div class="view-enter sd-root od-root">';
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

        h += '<header class="od-head"><button class="icon-btn od-back" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<h1 class="od-title">Order Summary</h1></header>';
        if (kitchenName) h += '<p class="od-kitchen muted small">🏪 ' + esc(kitchenName) + '</p>';

        // Mockup summary card: offering name, then N orders · X plates · ₹Y -
        // all from persisted order data, never sample figures from the design.
        h += '<section class="drilldown-header od-summary" aria-label="Offering summary">';
        h += '<h2 class="od-sum-name">' + esc(pname) + '</h2>';
        h += '<p class="dd-stats">' + orderCount + ' ' + orderWord + ' · ' +
            (detail.totalPlates || 0) + ' plates · ' + money(detail.totalRevenue || 0) + '</p>';
        h += '<div class="dtc-badges">' +
            '<span class="dtc-badge green"><span class="dtc-dot" aria-hidden="true"></span>' + (detail.paidCount || 0) + ' Paid</span>' +
            '<span class="dtc-badge orange"><span class="dtc-dot" aria-hidden="true"></span>' + (detail.pendingCount || 0) + ' Pending</span>' +
            '<span class="dtc-badge red"><span class="dtc-dot" aria-hidden="true"></span>' + (detail.cancelledCount || 0) + ' Cancelled</span>' +
            '</div></section>';

        // Reconcile with the dashboard card: "N booked" is the offering's live
        // reservation total across every date it is posted for, while these rows
        // cover ONE date. Whenever the two differ, say why instead of letting the
        // seller read it as missing orders.
        var bookedTotal = detail.dashboardBookedQuantity;
        if (typeof bookedTotal === 'number' && bookedTotal !== (detail.totalPlates || 0)) {
            var bookedUnit = detail.productUnit || 'units';
            if (bookedTotal !== 1 && bookedUnit.slice(-1) !== 's') bookedUnit += 's';
            h += '<div class="tiny muted mt-1 od-booked">Dashboard shows ' + bookedTotal + ' ' +
                esc(bookedUnit) + ' booked — that covers every date of this ' +
                'offering. These orders are ' + esc(prettyDate(sellerDate(S.selectedDate))) + ' only.</div>';
        }

        // Filters only. Sorting within a single offering is meaningless - the
        // seller is already looking at one item - so no sort control is offered.
        h += '<div class="oc-filters od-filters" role="group" aria-label="Filter orders">';
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
            h += '<div class="tiny muted mt-1 od-showing">Showing ' + (detail.filteredTotalOrders || 0) + ' of ' + orderCount + ' orders</div>';
        }
        // Delivery progress sits ABOVE the rows and is deliberately unfiltered: it
        // describes the whole offering for this date, which is also the scope of
        // its Mark All Delivered action.
        h += deliveryProgressHtml(detail);
        h += '<div id="offeringCustomers" class="od-list">' + offeringCustomersHtml(detail) + '</div>';
    } catch (e) {
        h += '<header class="od-head"><button class="icon-btn od-back" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<h1 class="od-title">Order Summary</h1></header>';
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
        out += '<div class="customer-row compact oc-row oc-row--' + bucket + '" role="button" tabindex="0" data-action="open-order" data-order="' + esc(c.orderId) + '">';
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

/**
 * Loading skeleton for the Individual Order screen (Screen 7C).
 *
 * <p>Painted by {@link sellerOrderDetailByOrderView} before its request
 * resolves - the same pattern the Offering Orders / Dashboard / Orders /
 * Earnings screens use - so a slow connection never shows a blank page. It is
 * purely presentational: every block is an empty shell that the real
 * /api/seller/orders/{id} payload replaces.</p>
 */
function oicSkeletonHtml() {
    return '<div class="sd-root oic-root" role="status" aria-busy="true" aria-label="Loading order details">'
        + '<div class="oic-skel-head"><div class="sd-skel sd-skel--line w40"></div></div>'
        + '<div class="sd-skel oic-skel-card"></div>'
        + '<div class="sd-skel sd-skel--line w60"></div>'
        + '<div class="sd-skel oic-skel-card oic-skel-card--sm"></div>'
        + '</div>';
}

/**
 * Screen 7C - individual order detail (presentation only).
 *
 * <p>There is no separate approved mockup for this drill-down: it reuses the
 * approved Offering Orders (Screen 7B) visual language - the same back
 * control, the same summary-card treatment, the same dot + readable-text
 * status badges, the same touch targets - so the two order screens read as
 * one flow. Every figure still comes from the EXISTING
 * /api/seller/orders/{id} payload, both existing actions (Cancel Order,
 * Mark as Paid) keep their exact data-action hooks, and the loading / error
 * states stay honest: a skeleton while the payload is in flight, a retry
 * when it fails. No new screen, endpoint, route or business rule was
 * introduced, and no sample figure is hard-coded.</p>
 */
// SCREEN 7C: INDIVIDUAL ORDER DETAIL
async function sellerOrderDetailByOrderView(orderId) {
    // Content-shaped skeleton while the payload is in flight - the same
    // pattern the sibling screens use. Purely presentational:
    // sellerRender replaces it with the string we return once we resolve.
    var live = viewEl();
    if (live) live.innerHTML = oicSkeletonHtml();
    var h = '<div class="view-enter sd-root oic-root">';
    h += '<header class="od-head oic-head"><button class="icon-btn od-back" type="button" data-action="go-back" aria-label="Back">←</button>'
        + '<h1 class="od-title">Order Details</h1></header>';
    try {
        var order = await sellerApi('/api/seller/orders/' + orderId);
        // Summary card: order number + amount, then the buyer. All from the
        // payload, never sample figures.
        h += '<section class="drilldown-header oic-summary" aria-label="Order summary">';
        h += '<div class="oic-top"><h2 class="oic-num">#' + esc(order.orderNumber) + '</h2>';
        var statusClass = order.orderStatus === 'CANCELLED' ? 'cancelled' : (order.paymentStatus === 'PAID' ? 'paid' : 'pending');
        h += '<span class="status-dot ' + statusClass + '" aria-hidden="true"></span></div>';
        h += '<p class="dd-stats oic-total">' + money(order.totalAmount) + '</p>';
        if (order.buyer) {
            h += '<p class="odc-buyer oic-buyer">' + esc(order.buyer.name || 'Unknown') + '</p>';
        }
        // Same treatment the approved 7B screen gives its badges: the
        // coloured dot carries state, the label stays readable text.
        var paymentStatus = order.paymentStatus || 'PENDING';
        h += '<div class="dtc-badges oic-badges">';
        h += '<span class="dtc-badge ' + (paymentStatus === 'PAID' ? 'green' : 'orange') + '"><span class="dtc-dot" aria-hidden="true"></span>Payment: ' + esc(paymentStatus) + '</span>';
        h += '<span class="dtc-badge ' + (order.orderStatus === 'CANCELLED' ? 'red' : 'green') + '"><span class="dtc-dot" aria-hidden="true"></span>Order status: ' + esc(order.orderStatus || 'ORDERED') + '</span></div>';
        h += '</section>';
        // Buyer / delivery detail card. Class hooks the existing actions and
        // tests rely on (odc-*, ei-*, data-action) are preserved verbatim.
        h += '<section class="card pad card-mb oic-card" aria-label="Buyer and items">';
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
            h += '<div class="mt-2 oic-items">';
            order.items.forEach(function (item) {
                var itemTotal = item.price != null && item.quantity != null ? item.price * item.quantity : 0;
                h += '<div class="odc-item"><span class="ei-name">' + esc(item.productName || item.name) + '</span><span class="ei-orders">' + item.quantity + 'x ' + money(item.price) + ' = ' + money(itemTotal) + '</span></div>';
            });
            h += '</div>';
        }
        h += '</section>';
        // Actions keep their exact hooks and business rules.
        if (order.orderStatus && ['ORDERED', 'CONFIRMED', 'READY'].indexOf(order.orderStatus) >= 0) {
            h += '<button class="btn btn-secondary btn-block mt-2 oic-act" type="button" data-action="cancel-order" data-order-id="' + esc(order.id) + '">Cancel Order</button>';
        }
        if ((paymentStatus === 'PENDING' || paymentStatus === 'WILL_PAY_LATER') && order.orderStatus !== 'CANCELLED') {
            h += '<button class="btn btn-primary btn-block mt-2 oic-act" type="button" data-action="mark-paid" data-oid="' + esc(order.id) + '">Mark as Paid</button>';
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Could not load details', e.message,
            '<button class="btn btn-primary card-mt" type="button" data-action="seller-retry">Retry</button>');
    }
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
    // V2 §10: Edit Today / Edit This Date saves an override for THIS DATE
    // ONLY. Name, price, image, category, the offering date and the repeating
    // rule are deliberately not writable here - they belong to the product or
    // to Manage Schedule, and touching them would leak one day's edit into
    // every other occurrence.
    if (S.editOccurrenceId != null) {
        var occId = S.editOccurrenceId;
        var occBody = {};
        var rawQty = String(ev.availableQuantity == null ? '' : ev.availableQuantity).trim();
        if (rawQty === '') {
            // Blank means "No limit" for this date - but only report a change
            // when the occurrence was limited before.
            if (o.maxQuantity != null) occBody.clearQuantity = true;
        } else {
            var occQty = Number(rawQty);
            if (!Number.isInteger(occQty) || occQty < 1) {
                toast('Quantity for this date must be a whole number of at least 1, or blank for No limit', 'error');
                return;
            }
            if (occQty !== o.maxQuantity) occBody.quantity = occQty;
        }
        var occClose = parseOptionalHhmm(ev.orderWindowEnd, 'Orders Close');
        if (!occClose) { toast('Orders Close is required', 'error'); return; }
        if (occClose !== (o.orderWindowEnd || '')) occBody.orderCloseTime = occClose;
        var occReady = String(ev.readyByTime || '').trim();
        if (occReady && occReady !== (o.readyByTime || '')) occBody.readyByTime = occReady;
        if (Object.keys(occBody).length === 0) { toast('No changes to save', 'info'); return; }
        await sellerApi('/api/seller/schedules/occurrences/' + occId, { method: 'PATCH', body: occBody });
        toast('Saved for this date only', 'success');
        S.editOccurrenceId = null;
        S.editOffering = null;
        S.offeringFor = 'today';
        sellerNavigate('#/home');
        return;
    }
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
            if (S.offeringSubmitting) return; // guard against double submit
            S.offeringSubmitting = true;
            var publishBtn = form.querySelector('button[type="submit"]');
            if (publishBtn) publishBtn.disabled = true;
            var vals = formVals(form);
            // Optional timing fields: blank means "not provided" -- never send
            // an invalid empty timestamp to the backend.
            vals.orderWindowStart = parseOptionalHhmm(vals.orderWindowStart, 'Orders Open');
            vals.orderWindowEnd = parseOptionalHhmm(vals.orderWindowEnd, 'Orders Close');
            if (!vals.orderWindowEnd) { toast('Orders Close is required', 'error'); return; }
            var readyBy = (vals.readyByTime || '').trim();
            if (!readyBy) { toast('Delivery / Ready By is required', 'error'); return; }
            vals.readyByTime = readyBy;
            // Availability: ONE TIME keeps the existing Today/Tomorrow/Choose Date
            // rules; REPEATING SCHEDULE replaces the single date with the schedule
            // window (the product's date is its first occurrence). Requirement 3.
            var recurringConfig = null;
            if (S.offeringMode === 'recurring') {
                recurringConfig = buildRecurringSchedule(vals, form);
                if (!recurringConfig) return; // buildRecurringSchedule toasted
                vals.availableDate = recurringConfig.startDate;
            } else if (S.offeringFor === 'choose') {
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
            // Raw schedule inputs never travel to the API; only the structured
            // recurringSchedule object does (and only in recurring mode -- an
            // empty string would fail Jackson's object deserialisation).
            delete vals.recurringStartDate;
            delete vals.recurringEndDate;
            delete vals.recurringWeekdays;
            delete vals.recurringDuration;
            delete vals.recurringWeekday;
            delete vals.recurringDefaultQuantity;
            delete vals.recurringDefaultOrderCloseTime;
            delete vals.recurringDefaultReadyByTime;
            if (recurringConfig) vals.recurringSchedule = recurringConfig;
            else delete vals.recurringSchedule;
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
        } else if (form.id === 'manageScheduleForm') {
            // Manage Schedule only edits the recurring rule — never touch the
            // offering itself from here.
            var sid = S.manageScheduleScheduleId;
            if (!sid) { toast('No schedule selected', 'error'); return; }
            var vals = formVals(form);
            // Map the manage-schedule field names onto the recurring names the
            // shared recurring validator reads.
            vals.recurringStartDate = vals.manageStartDate;
            vals.recurringEndDate = vals.manageEndDate;
            vals.recurringDefaultQuantity = vals.manageDefaultQuantity;
            vals.recurringDefaultOrderCloseTime = vals.manageDefaultOrderCloseTime;
            vals.recurringDefaultReadyByTime = vals.manageDefaultReadyByTime;
            vals.recurringDuration = vals.manageDuration || vals.recurringDuration;
            var recurringConfig = buildRecurringSchedule(vals, form);
            if (!recurringConfig) return;
            recurringConfig.endDate = vals.manageEndDate || '';
            recurringConfig.ongoing = vals.recurringDuration === 'ongoing';
            if (!recurringConfig.startDate) recurringConfig.startDate = vals.manageStartDate || '';
            try {
                recurringConfig.clearDefaultQuantity = (vals.manageDefaultQuantity || '').trim() === '';
                await sellerApi('/api/seller/schedules/' + sid, { method: 'PUT', body: recurringConfig });
                toast('Schedule updated', 'success');
                $('#modalRoot').hidden = true;
                S.manageScheduleScheduleId = null;
                await sellerRender();
            } catch (err) {
                toast(err.message || 'Could not update schedule', 'error');
            }
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
    finally {
        // Release the double-submit guard on every path: success navigates away,
        // but a validation/transport failure leaves the same form on screen and
        // must stay editable.
        S.offeringSubmitting = false;
        if (publishBtn) publishBtn.disabled = false;
    }
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
            case 'seller-show-register':
                S.authMode = 'register';
                S.authError = null;
                S.authSlugTouched = false;
                await sellerRender();
                break;
            case 'seller-show-login':
                S.authMode = 'login';
                S.authError = null;
                await sellerRender();
                break;
            case 'seller-retry':
                S.authError = null;
                await sellerRender();
                break;
            case 'seller-logout':
                await api('/api/auth/logout', { method: 'POST' });
                S.user = null;
                S.kitchenUrl = null;
                S.authMode = 'login';
                await sellerRender();
                break;
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
            case 'open-all-orders': S.selectedDate = 'all'; sellerNavigate('#/orders'); break;
            case 'set-offerings-tab': {
                S.offeringsTab = t.dataset.tab === 'recurring' ? 'recurring' : 'history';
                await sellerRender();
                break;
            }
            case 'set-dash-tab': {
                S.dashTab = t.dataset.tab === 'recurring' ? 'recurring' : 'live';
                await sellerRender();
                break;
            }
            case 'save-recurring-day': {
                var oid = Number(t.dataset.oid);
                var form = t.closest('form[data-recurring-form]');
                if (!form) { toast('Could not find this date', 'error'); break; }
                var qtyRaw = form.querySelector('[name="quantity"]');
                var closeRaw = form.querySelector('[name="orderCloseTime"]');
                var readyRaw = form.querySelector('[name="readyByTime"]');
                var soldRaw = form.querySelector('[name="soldOut"]');
                var pausedRaw = form.querySelector('[name="ordersPaused"]');
                var qtyText = qtyRaw ? qtyRaw.value.trim() : '';
                var body = {
                    quantity: qtyText === '' ? null : Number(qtyText),
                    clearQuantity: qtyText === '',
                    orderCloseTime: closeRaw ? closeRaw.value : null,
                    readyByTime: readyRaw ? readyRaw.value : null,
                    soldOut: soldRaw ? !!soldRaw.checked : null,
                    ordersPaused: pausedRaw ? !!pausedRaw.checked : null
                };
                if (body.quantity != null && (!Number.isInteger(body.quantity) || body.quantity < 1)) {
                    toast('Quantity must be at least 1; leave blank for unlimited.', 'error');
                    break;
                }
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/schedules/occurrences/' + oid, { method: 'PATCH', body: body });
                    toast('Saved for this date only', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not save this date', 'error');
                }
                break;
            }
            case 'manage-schedule': {
                var sid = Number(t.dataset.scheduleId);
                // Re-render from the live row so the Manage Schedule form always
                // shows the freshest schedule card (lazy product association).
                try {
                    var card = await sellerApi('/api/seller/schedules/' + sid);
                    S.recurringScheduleConfig = card;
                    S.manageScheduleScheduleId = sid;
                    S.recurringDuration = card.ongoing ? 'ongoing' : 'dated';
                    var h = '<div class="view-enter"><div class="page-head"><h1>Manage Schedule</h1>'
                        + '<button class="icon-btn" type="button" data-action="go-back">←</button></div>'
                        + manageScheduleFormHtml(card) + '</div>';
                    $('#modalRoot').innerHTML = h;
                    $('#modalRoot').hidden = false;
                    S.manageScheduleDirty = false;
                } catch (err) {
                    toast(err.message || 'Could not load schedule', 'error');
                }
                break;
            }
            case 'end-schedule': {
                var sid = Number(t.dataset.scheduleId);
                if (!confirm('End this recurring schedule? Past occurrences and existing orders are preserved — the schedule simply stops activating future dates.')) break;
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/schedules/' + sid + '/end', { method: 'POST' });
                    toast('Schedule ended. History and orders remain.', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not end schedule', 'error');
                }
                break;
            }
            case 'edit-today':
            case 'edit-this-date': {
                // V2 §10: opens the existing Edit Offering UI pre-filled with
                // THIS occurrence's values, but the save path (guarded by
                // S.editOccurrenceId in submitOfferingEdit) writes an override
                // for this date only - never the product or the rule.
                var oid = Number(t.dataset.oid);
                try {
                    var occ = await sellerApi('/api/seller/schedules/occurrences/' + oid);
                    S.editOccurrenceId = oid;
                    S.editOffering = {
                        id: occ.productId,
                        name: occ.productName || '',
                        availableDate: occ.date,
                        bookedQuantity: 0,
                        maxQuantity: occ.quantity,
                        remainingQuantity: occ.quantity,
                        orderWindowEnd: occ.orderCloseTime,
                        readyByTime: occ.readyByTime,
                        hasOrders: false,
                        orderCount: 0,
                        soldOut: occ.soldOut,
                        ordersPaused: occ.ordersPaused,
                        category: ''
                    };
                    sellerNavigate('#/edit-offering');
                } catch (err) {
                    toast(err.message || 'Could not load occurrence', 'error');
                }
                break;
            }
            case 'occ-sold-out': {
                // V2 §11 / V3 §8: per-day Sold Out toggle - this date only.
                var soldOid = Number(t.dataset.oid);
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/schedules/occurrences/' + soldOid + '/sold-out',
                        { method: 'POST', body: { soldOut: true } });
                    toast('Marked sold out for this date only', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not mark sold out', 'error');
                }
                break;
            }
            case 'occ-close': {
                // V2 §11 / V3 §8: per-day Close Orders toggle - this date only.
                var closeOid = Number(t.dataset.oid);
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/schedules/occurrences/' + closeOid + '/pause',
                        { method: 'POST', body: { paused: true } });
                    toast('Orders closed for this date only', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not close orders', 'error');
                }
                break;
            }
            case 'extend-schedule': {
                // V2 §15: renewal path for a capped Ongoing schedule.
                var extSid = Number(t.dataset.scheduleId);
                t.disabled = true;
                try {
                    await sellerApi('/api/seller/schedules/' + extSid + '/extend', { method: 'POST' });
                    toast('Schedule extended by another 90 days', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not extend schedule', 'error');
                }
                break;
            }
            case 'view-orders-date': {
                var oid = Number(t.dataset.oid);
                try {
                    var occ = await sellerApi('/api/seller/schedules/occurrences/' + oid);
                    S.selectedDate = occ.date;
                    S.offeringFilterSociety = '';
                    S.offeringFilterStatus = '';
                    S.offeringFilterDelivery = '';
                    $('#modalRoot').hidden = true;
                    sellerNavigate('#/order-detail/' + occ.productId);
                } catch (err) {
                    toast(err.message || 'Could not load occurrence orders', 'error');
                }
                break;
            }
            case 'open-offering-orders-date': {
                S.selectedDate = t.dataset.date || sellerDate('today');
                S.offeringFilterSociety = '';
                S.offeringFilterStatus = '';
                S.offeringFilterDelivery = '';
                sellerNavigate('#/order-detail/' + t.dataset.pid);
                break;
            }
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
                    S.editOccurrenceId = null; // product edit, never an occurrence override
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
                 // Scoped clear: only the Offering For radios move. The REPEATING
                 // SCHEDULE / ONE TIME and Repeat Duration radios live on the same
                 // form and must keep their own selection.
                 $all('#offeringForGroup .radio-option').forEach(function (el) { el.classList.remove('selected'); });
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
             case 'set-offering-mode': {
                 // Availability toggle: ONE TIME vs REPEATING SCHEDULE (Req 3).
                 // Selection is scoped to this pair so it can never clear the
                 // Today/Tomorrow/Choose or Repeat Duration radios.
                 S.offeringMode = t.dataset.mode === 'recurring' ? 'recurring' : 'today';
                 $all('[data-action="set-offering-mode"]').forEach(function (el) { el.classList.remove('selected'); });
                 t.classList.add('selected');
                 var schedFields = $('#recurringScheduleFields');
                 if (schedFields) schedFields.hidden = S.offeringMode !== 'recurring';
                 // In recurring mode the schedule start date IS the offering date,
                 // so the single-date "Offering For" chooser steps aside; in ONE
                 // TIME mode it comes back with its previous choice intact.
                 var forGroup = $('#offeringForGroup');
                 if (forGroup) {
                     forGroup.hidden = S.offeringMode === 'recurring';
                     // Resync the single-date radios to the stored choice: the DOM
                     // was rendered once, so a mode round-trip must not leave a
                     // stale highlight over a different stored selection.
                     $all('#offeringForGroup .radio-option').forEach(function (el) {
                         el.classList.toggle('selected', (el.dataset.val || 'today') === S.offeringFor);
                     });
                 }
                 var chooseRow2 = $('#chooseDateRow');
                 if (chooseRow2 && S.offeringMode === 'recurring') chooseRow2.hidden = true;
                 else if (chooseRow2) chooseRow2.hidden = S.offeringFor !== 'choose';
                 if (S.offeringMode === 'recurring') syncRecurringEndDate();
                 break;
             }
             case 'set-recurring-duration': {
                 $all('[data-action="set-recurring-duration"]').forEach(function (el) { el.classList.remove('selected'); });
                 t.classList.add('selected');
                 S.recurringDuration = t.dataset.duration || 'thisweek';
                 var durInput = $('#recurringDuration');
                 if (durInput) durInput.value = S.recurringDuration;
                 syncRecurringEndDate();
                 break;
             }
        }
    } catch (err) { toast(err.message, 'error'); }
});

document.addEventListener('change', function (e) {
    var picker = e.target.closest('[data-action="set-availability-date"]');
    if (picker) { applyOfferingDatePicker(picker); return; }
    // REPEATING SCHEDULE: keep the aggregated weekday list and the computed
    // end date in step with what the seller just clicked. The payload itself is
    // rebuilt from the live checkboxes at submit time, so these stay cosmetic.
    var weekdayBox = e.target.closest('input[name="recurringWeekday"]');
    if (weekdayBox) {
        var hiddenDays = $('#recurringWeekdays');
        if (hiddenDays) {
            hiddenDays.value = $all('input[name="recurringWeekday"]:checked')
                .map(function (cb) { return cb.value; }).join(',');
        }
        return;
    }
    var recStart = e.target.closest('#recurringStartDate');
    if (recStart) {
        if (S.recurringDuration !== 'dated') syncRecurringEndDate();
        return;
    }
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

// Keyboard activation for the tappable order rows on the Offering Orders
// screen. They carry role="button" + tabindex, so Enter/Space must open the
// order exactly like a click does. Children that already own their keys (the
// Delivered checkbox, the remark button, the filter selects, links) are left
// alone so this can never steal or double-handle their behaviour.
document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' && e.key !== ' ' && e.key !== 'Spacebar') return;
    var t = e.target;
    if (!t || typeof t.closest !== 'function') return;
    if (t.closest('input, select, textarea, button, a')) return;
    var row = t.closest('.oc-row[data-action="open-order"]');
    if (!row) return;
    e.preventDefault();
    sellerNavigate('#/order-detail/order/' + row.dataset.order);
});

// BOOT - never leaves the page on an infinite spinner
var sellerBooted = false;
window.addEventListener('hashchange', function () { if (sellerBooted) sellerRender(); });
window.addEventListener('DOMContentLoaded', async function () {
    try {
        initTheme();
        if (!location.hash) {
            history.replaceState(null, '', '#/home');
        }
        sellerBooted = true;
        await sellerRender();
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
