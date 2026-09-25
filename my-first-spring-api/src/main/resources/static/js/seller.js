/**
 * SocioMart Seller App v1.0 - 5-tab SPA
 */
var S = { user: null, kitchen: null, viewMode: 'editor', selectedDate: 'today', sortFilter: 'all', historySelected: [], draftOffering: null, favTemplates: [], offeringFilterSociety: '', offeringFilterStatus: '', offeringFor: 'today', quickPostRequestId: null };
var sellerRoutes = {
    '#/home': sellerHomeView, '#/add': sellerAddView, '#/create': sellerCreateView,
    '#/quick-post': sellerQuickPostView, '#/history': sellerHistoryView,
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
async function sellerRender() {
    var hash = location.hash || '#/home';
    var route = sellerResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    closeSheet();
    try { view.innerHTML = await route.fn(route.arg) || ''; sellerUpdateNav(hash); await loadUnreadNotifications(); if (typeof applyThemeUiState === 'function') applyThemeUiState(); window.scrollTo(0, 0); var saInput = $('#serviceAreasInput'); if (saInput) renderServiceAreas(saInput.value); }
    catch (err) { view.innerHTML = '<div class="view-enter">' + emptyHtml('⚠️', 'Something went wrong', err.message) + '</div>'; }
}
function sellerUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash === '#/home' ? 'home' : hash === '#/kitchen' ? 'kitchen' : (hash === '#/orders' || hash.startsWith('#/order-detail/')) ? 'orders' : hash === '#/enquiries' ? 'enquiries' : hash === '#/history' ? 'history' : hash === '#/earnings' ? 'earnings' : null;
    var el = document.querySelector('[data-nav="' + (key || '') + '"]');
    if (el) el.classList.add('active');
}

function renderServiceAreas(serviceAreas) {
    var list = $('#serviceAreaList');
    if (!list) return;
    list.innerHTML = '';
    if (!serviceAreas) return;
    serviceAreas.split(',').forEach(function (area) {
        area = area.trim();
        if (!area) return;
        var pill = document.createElement('span');
        pill.className = 'sa-pill';
        pill.dataset.name = area;
        pill.innerHTML = esc(area) + ' <button type="button" data-action="remove-service-area" data-name="' + esc(area) + '" aria-label="Remove">×</button>';
        list.appendChild(pill);
    });
}

function updateServiceAreasInput() {
    var list = $('#serviceAreaList');
    var input = $('#serviceAreasInput');
    if (!list || !input) return;
    var pills = list.querySelectorAll('.sa-pill');
    var areas = [];
    pills.forEach(function (pill) {
        var name = pill.dataset.name;
        if (name) areas.push(name);
    });
    input.value = areas.join(',');
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
    try { S.favTemplates = await api('/api/seller-app/templates'); } catch (e) { S.favTemplates = []; }
    h += '<div class="pathway-card" data-action="go-use-favourite"><div class="pc-icon">⭐</div><div class="pc-title">Create from Favourite</div><div class="pc-desc">Quickly post from saved templates (max 3).</div>';
    if (S.favTemplates.length > 0) { h += '<div class="favourite-pills">'; S.favTemplates.forEach(function (t) { h += '<span class="fav-pill" data-action="use-template" data-tid="' + t.id + '">⭐ ' + esc(t.name) + '</span>'; }); h += '</div>'; }
    h += '</div>';
    h += '<div class="pathway-card" data-action="go-create"><div class="pc-icon">✨</div><div class="pc-title">Create New Offering</div><div class="pc-desc">Fill in all details manually.</div></div>';
    h += '<div class="pathway-card" data-action="go-quick-post"><div class="pc-icon">📋</div><div class="pc-title">Quick Post</div><div class="pc-desc">Paste a WhatsApp message and publish a simple Today announcement.</div></div>';
    h += '</div>';
    return h;
}

// SCREEN 1: SELLER DASHBOARD (HOME)
async function sellerHomeView() {
    var h = '<div class="view-enter">';
    h += '<div class="seller-header"><div class="sdh-text"><p class="sdh-greeting">' + greeting() + ', ' + esc(S.user && S.user.name ? S.user.name : 'Seller') + '</p><h1 class="sdh-title">Your Dashboard</h1></div><span class="notif-bell">' + notificationBadgeHtml() + '<button class="icon-btn" type="button" data-action="toggle-theme" aria-label="Toggle theme">🌓</button>' + notificationPanelHtml('sellerNotifPanel') + '</span></div>';
    try {
        var dash = await api('/api/seller-app/dashboard');
        S.kitchen = { id: dash.kitchenId, name: dash.kitchenName };
        var hasActivity = (!dash.totalOrders || dash.totalOrders === 0)
            && (!dash.hasEarnings || dash.hasEarnings === false)
            && (!dash.pending || dash.pending === 0)
            && (!dash.followers || dash.followers === 0)
            && (!dash.viewsToday || dash.viewsToday === 0);
        if (!hasActivity) {
            h += '<div class="metric-cards-row">' +
                '<div class="metric-card"><div class="metric-value">' + dash.viewsToday + '</div><div class="metric-label">Views Today</div></div>' +
                '<div class="metric-card"><div class="metric-value">' + dash.followers + '</div><div class="metric-label">Followers</div></div>' +
                '<div class="metric-card"><div class="metric-value">' + dash.totalOrders + '</div><div class="metric-label">Total Orders</div></div></div>';
        }
        h += '<div class="section-head"><h2>My Offerings</h2></div>';
        if (!dash.offerings || dash.offerings.length === 0) {
            h += emptyHtml('🍽️', 'No Offerings', 'Nothing on sale right now. Create your first offering and start taking orders.',
                '<a class="btn btn-primary card-mt" href="#/create">+ Create Offering</a>');
        } else {
            dash.offerings.forEach(function (p) {
                h += '<div class="offering-card">';
                h += '<div class="oc-photo" data-emoji="' + foodEmoji(p.name) + '">' + (p.imageUrl ? '<img src="' + esc(p.imageUrl) + '" alt="' + esc(p.name) + '" onerror="imgFallback(this)">' : foodEmoji(p.name)) + '</div>';
                h += '<div class="oc-body">';
                h += '<div class="oc-header"><span class="oc-name">' + esc(p.name) + '</span>' + offeringStatusBadge(p) + '</div>';
                var booked = p.bookedQuantity || 0, remaining = p.remainingQuantity, maxQty = p.maxQuantity;
                h += '<div class="oc-stats"><strong>' + booked + ' booked</strong> • ' + (remaining != null ? '<strong>' + remaining + ' available</strong>' : 'No limit') + '</div>';
                h += '<div class="oc-time-row"><span>Orders close: <span class="time-label">' + esc(sellerOrdersCloseLabel(p)) + '</span></span><span>Delivery: <span class="time-label">' + esc(sellerDeliveryLabel(p)) + '</span></span></div>';
                if (maxQty != null && remaining != null && remaining >= 0 && !p.soldOut && !p.ordersPaused) { h += '<div class="stepper"><button type="button" data-action="inv-dec" data-pid="' + p.id + '" aria-label="Decrease">-</button><span class="stepper-value" id="inv-' + p.id + '">' + remaining + '</span><button type="button" data-action="inv-inc" data-pid="' + p.id + '" aria-label="Increase">+</button></div>'; }
                if (!p.soldOut && !p.ordersPaused) { h += '<button class="btn-soldout" type="button" data-action="mark-soldout" data-pid="' + p.id + '">Mark Sold Out</button>'; }
                if (!p.soldOut && !p.ordersPaused) { h += '<button class="btn btn-secondary btn-sm btn-block btn-mt-sm" type="button" data-action="pause-orders" data-pid="' + p.id + '">Pause Orders</button>'; }
                if (p.ordersPaused && !p.soldOut) { h += '<button class="btn btn-secondary btn-sm btn-block btn-mt-sm" type="button" data-action="resume-orders" data-pid="' + p.id + '">Resume Orders</button>'; }
                h += '<a class="btn btn-secondary btn-sm btn-block btn-mt-sm" href="#/order-detail/' + p.id + '">View Orders</a>';
                h += '</div></div>';
            });
        }
        h += '<button class="btn-add-offering" type="button" data-action="go-add">+ Add Offering</button>';
        var hasPending = dash.pending != null && Number(dash.pending) !== 0;
        if (dash.hasEarnings) {
            h += '<div class="earnings-preview"><h3>Earnings Summary</h3>';
            h += '<div class="ep-row"><span class="ep-label">Confirmed Today</span><span class="ep-value green">' + money(dash.confirmedToday) + '</span></div>';
            if (hasPending) h += '<div class="ep-row"><span class="ep-label">Pending</span><span class="ep-value orange">' + money(dash.pending) + '</span></div>';
            h += '<div class="ep-row"><span class="ep-label">This Month</span><span class="ep-value">' + money(dash.thisMonth) + '</span></div></div>';
        } else {
            h += emptyHtml('💰', 'No Earnings', 'No earnings to show yet');
            if (hasPending) h += '<div class="earnings-preview"><h3>Pending Payments</h3><div class="ep-row"><span class="ep-label">Pending</span><span class="ep-value orange">' + money(dash.pending) + '</span></div></div>';
        }
    } catch (e) { h += emptyHtml('⚠️', 'Could not load dashboard', e.message); }
    h += '</div>';
    return h;
}

// SCREEN 5: HISTORY
async function sellerHistoryView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>History</h1><p class="muted small">Previous offerings from your kitchen.</p></div>';
    try {
        var items = await api('/api/seller-app/history');
        S.historyItems = items || [];
        if (!items.length) {
            h += emptyHtml('🕘', 'No previous items', 'Expired offerings for your kitchen will appear here.');
        } else {
            items.forEach(function (p) {
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
async function sellerQuickPostView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Quick Post</h1><p class="muted small">Paste your WhatsApp message. Quick Posts are always published for Today.</p></div>';
    h += '<form class="seller-form" id="quickPostForm">';
    h += '<div class="form-group"><label class="form-label">Paste WhatsApp message <span class="req">*</span></label><textarea class="form-textarea" id="qpMessage" name="message" rows="6" placeholder="Paste your WhatsApp message here..." required></textarea></div>';
    h += '<div class="form-group"><label class="form-label">Add image <span class="muted small">(optional)</span></label><input class="form-input" id="qpImage" type="file" accept="image/png,image/jpeg,image/gif,image/webp"><p class="muted small">PNG, JPEG, GIF, or WebP up to 2 MB.</p></div>';
    h += '<button class="btn btn-primary btn-block" type="submit" id="qpSubmit">Post</button>';
    h += '</form>';
    try {
        var posts = await api('/api/seller-app/quick-posts');
        if (posts && posts.length) {
            h += '<h3 class="section-gap mb-2">Today\'s Quick Posts</h3>';
            posts.forEach(function (p) { h += '<div class="card pad card-mb"><div class="font-700">' + esc(p.message) + '</div>' + (p.imageData ? '<img class="mt-2" style="max-width:100%;border-radius:8px" src="' + esc(p.imageData) + '" alt="Quick Post image">' : '') + '</div>'; });
        }
    } catch (e) { /* form remains available if list refresh fails */ }
    h += '</div>';
    return h;
}

// SCREEN 3: CREATE OFFERING (MANUAL FORM)
async function sellerCreateView() {
    // A new form always starts in Today mode; never inherit a previous choice.
    S.offeringFor = 'today';
    var t = S.draftOffering || {};
    var selectedUnit = t.priceUnit || 'Per Piece';
    var categoryValues = String(t.category || '').split(',').map(function (c) { return c.trim().toUpperCase(); }).filter(Boolean);
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Create Offering</h1><p class="muted small">' +
        (S.republishSourceId ? 'Review the previous offering details, then set fresh timing and quantity.' : 'Fill in the details for your new dish.') + '</p></div>';
    h += '<form class="seller-form" id="createOfferingForm">';
    h += '<div class="form-group"><label class="form-label">Photos <span class="req">*</span></label><div class="photo-upload-row"><div class="photo-tile" data-action="add-photo">+</div></div></div>';
    h += '<div class="form-group"><label class="form-label">Item Name <span class="req">*</span></label><input class="form-input" name="name" value="' + esc(t.name || '') + '" placeholder="e.g. POHA" required></div>';
    h += '<div class="form-group"><label class="form-label">Short Description</label><textarea class="form-textarea" name="description">' + esc(t.description || '') + '</textarea></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Price (Rs) <span class="req">*</span></label><input class="form-input" name="price" type="number" value="' + (t.price || '') + '" placeholder="100" required></div>';
    h += '<div class="form-group"><label class="form-label">Unit <span class="req">*</span></label><select class="form-select" name="priceUnit"><option value="Per Piece"' + (selectedUnit === 'Per Piece' ? ' selected' : '') + '>Per Piece</option><option value="Per Plate"' + (selectedUnit === 'Per Plate' ? ' selected' : '') + '>Per Plate</option><option value="Per Box"' + (selectedUnit === 'Per Box' ? ' selected' : '') + '>Per Box</option></select></div></div>';
    h += '<input type="hidden" name="imageUrl" value="' + esc(t.imageUrl || '') + '">';
    h += '<div class="form-group"><label class="form-label">Offering For <span class="req">*</span></label><div class="radio-group"><label class="radio-option selected" data-action="set-availability" data-val="today">Today</label><label class="radio-option" data-action="set-availability" data-val="tomorrow">Tomorrow</label><label class="radio-option" data-action="set-availability" data-val="choose">Choose Date</label></div></div>';
    h += '<input type="hidden" name="availableDate" id="availDate" value="' + sellerDate('today') + '">';
    h += '<div class="form-group" id="chooseDateRow" hidden><label class="form-label">Offering Date <span class="req">*</span></label><input type="date" class="form-input" name="chosenOfferingDate" min="' + sellerDate('today') + '" data-action="set-availability-date" value="' + sellerDate('today') + '"></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Orders Open — Optional</label><input class="form-input" name="orderWindowStart" type="time"><p class="muted small">Leave blank to start accepting orders immediately.</p></div>';
    h += '<div class="form-group"><label class="form-label">Orders Close <span class="req">*</span></label><input class="form-input" name="orderWindowEnd" type="time" required><p class="muted small">Last date/time customers can place an order.</p></div></div>';
    h += '<div class="form-group"><label class="form-label">Delivery / Ready By <span class="req">*</span></label><input class="form-input" name="readyByTime" type="datetime-local" required><p class="muted small">Date/time by which the order will be ready/delivered.</p></div>';
    h += '<div class="form-group"><label class="form-label">Quantity Available — Optional</label><input class="form-input" name="maxQuantity" type="number" min="1" step="1" value="' + (t.maxQuantity != null ? esc(String(t.maxQuantity)) : '') + '" placeholder="Blank for unlimited"><p class="muted small">Leave blank for unlimited quantity.</p></div>';
    h += '<div class="form-group"><label class="form-label">To be listed in <span class="req">*</span></label><div class="checkbox-group"><label class="checkbox-option"><input type="checkbox" name="categories" value="BREAKFAST"' + (categoryValues.indexOf('BREAKFAST') >= 0 ? ' checked' : '') + '> Breakfast</label><label class="checkbox-option"><input type="checkbox" name="categories" value="LUNCH"' + (categoryValues.indexOf('LUNCH') >= 0 ? ' checked' : '') + '> Lunch</label><label class="checkbox-option"><input type="checkbox" name="categories" value="DINNER"' + (categoryValues.indexOf('DINNER') >= 0 ? ' checked' : '') + '> Dinner</label><label class="checkbox-option"><input type="checkbox" name="categories" value="SNACKS"' + (categoryValues.indexOf('SNACKS') >= 0 ? ' checked' : '') + '> Snacks</label></div><p class="muted small">Select at least one category.</p></div>';
    h += '<div class="toggle-row"><div><div class="toggle-text">Mark as Favourite</div><div class="toggle-note">Save as template (max 3).</div></div><div class="toggle-switch" id="favToggle" data-action="toggle-favourite"></div></div>';
    h += '<button class="btn btn-primary btn-block" type="submit">Publish Offering</button></form></div>';
    return h;
}

// SCREEN 7A: ORDER SUMMARY
async function sellerOrdersView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Orders</h1></div>';
    h += '<div class="date-tabs"><button class="date-tab' + (S.selectedDate === 'today' ? ' active' : '') + '" data-action="set-date" data-date="today">Today</button><button class="date-tab' + (S.selectedDate === 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="tomorrow">Tomorrow</button><button class="date-tab' + (S.selectedDate !== 'today' && S.selectedDate !== 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="pick">Pick date</button></div>';
    try {
        var summary = await api('/api/seller-app/orders/summary?date=' + sellerDate(S.selectedDate));
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
    try { kitchen = await api('/api/seller/kitchen'); S.myKitchen = kitchen; S.kitchen = kitchen; } catch (e) { }
    var societies = [];
    try { societies = await api('/api/seller/societies') || []; } catch (e) { }
    var societyOptions = societies.map(function (s) { return '<option value="' + esc(s) + '">' + esc(s) + '</option>'; }).join('');
    var paused = !!(kitchen && kitchen.paused);
    var h = '<div class="view-enter"><div class="page-head"><h1>Manage Kitchen</h1></div>';
    h += '<div class="kitchen-status-badge' + (paused ? ' paused' : '') + '">' + (paused ? 'Kitchen PAUSED' : 'Kitchen Published') + '</div>';
    h += paused
        ? '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="resume-kitchen">Resume Kitchen</button>'
        : '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="pause-kitchen">Pause Kitchen</button>';
    h += '<button class="btn btn-secondary btn-sm btn-block btn-mt-sm" type="button" data-action="preview-kitchen">Preview Kitchen Page</button>';
    h += '<form class="seller-form" id="kitchenForm">';
    h += '<div class="kitchen-avatar-upload"><div class="kitchen-avatar" data-action="upload-avatar" role="button" tabindex="0" aria-label="Upload kitchen photo">' + (kitchen && kitchen.imageUrl ? '<img src="' + esc(kitchen.imageUrl) + '" class="avatar-img" alt="Kitchen photo" onerror="imgFallback(this)">' : '📷') + '</div></div>';
    h += '<div class="form-group"><label class="form-label">Kitchen Name</label><input class="form-input" name="displayName" value="' + esc(kitchen && kitchen.displayName ? kitchen.displayName : 'Aarti Kitchen') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Who can order from me? (Service Areas)</label>';
    h += '<div class="muted small" style="margin-bottom:6px">Select the societies you deliver to. Buyers outside these societies cannot discover or order from your kitchen.</div>';
    h += '<div id="serviceAreaList"></div>';
    h += '<div class="form-row-2" style="margin-top:8px"><select class="form-input" id="newServiceArea"><option value="">Select society</option>' + societyOptions + '</select><button class="btn btn-secondary btn-sm" type="button" data-action="add-service-area">Add</button></div>';
    h += '<input type="hidden" name="serviceAreas" id="serviceAreasInput" value="' + esc(kitchen && kitchen.serviceAreas ? kitchen.serviceAreas : '') + '">';
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
        var e = await api('/api/seller-app/earnings');
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
        h += '<a class="btn btn-secondary btn-block" href="#/history">VIEW FULL HISTORY</a>';
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
async function sellerOrderDetailView(productId) {
    S.offeringFilterSociety = S.offeringFilterSociety || '';
    S.offeringFilterStatus = S.offeringFilterStatus || '';
    var h = '<div class="view-enter"><div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button><h2 class="flex-1" id="offeringName"></h2></div>';
    try {
        var detail = await api('/api/seller-app/orders/product/' + productId + '?date=' + sellerDate(S.selectedDate) + (S.offeringFilterSociety ? '&society=' + encodeURIComponent(S.offeringFilterSociety) : '') + (S.offeringFilterStatus ? '&status=' + encodeURIComponent(S.offeringFilterStatus) : ''));
        var pname = detail.productName || 'Offering';
        var el = document.getElementById('offeringName');
        if (el) el.textContent = pname;
        h += '<div class="drilldown-header"><h3>' + esc(pname) + '</h3>';
        h += '<div class="dd-stats">' + (detail.totalPlates || 0) + ' plates · ' + money(detail.totalRevenue || 0) + '</div>';
        h += '<div class="dtc-badges"><span class="dtc-badge green">' + (detail.paidCount || 0) + ' Paid</span><span class="dtc-badge orange">' + (detail.pendingCount || 0) + ' Pending</span><span class="dtc-badge red">' + (detail.cancelledCount || 0) + ' Cancelled</span></div></div>';
        h += '<div class="form-row-2" style="margin-top:10px">';
        var societies = [];
        var statusOpts = ['All Status', 'Paid', 'Pending', 'Cancelled'];
        (detail.customers || []).forEach(function (c) { if (c.society && societies.indexOf(c.society) === -1) societies.push(c.society); });
        h += '<select class="sort-select" data-action="set-offering-society"><option value="">All Societies</option>';
        societies.forEach(function (s) { h += '<option value="' + esc(s) + '"' + (S.offeringFilterSociety === s ? ' selected' : '') + '>' + esc(s) + '</option>'; });
        h += '</select>';
        h += '<select class="sort-select" data-action="set-offering-status"><option value="">All Status</option>';
        statusOpts.forEach(function (s) { var val = s === 'All Status' ? '' : s.toLowerCase(); h += '<option value="' + val + '"' + (S.offeringFilterStatus === val ? ' selected' : '') + '>' + esc(s) + '</option>'; });
        h += '</select></div>';
        if (S.offeringFilterSociety || S.offeringFilterStatus) {
            h += '<div class="tiny muted mt-1">Showing filtered results</div>';
        }
        h += '<div id="offeringCustomers"></div>';
        h += '</div>';
        renderOfferingCustomers(detail);
    } catch (e) { h += emptyHtml('⚠️', 'Could not load details', e.message); h += '</div>'; }
    return h;
}

function renderOfferingCustomers(detail) {
    var container = document.getElementById('offeringCustomers');
    if (!container) return;
    container.innerHTML = '';
    var customers = detail.customers || [];
    if (customers.length === 0) { container.innerHTML = emptyHtml('📋', 'No customer orders', 'No orders match the selected filters.'); return; }
    customers.forEach(function (c) {
        var statusClass = c.cancelled ? 'cancelled' : (c.paid ? 'paid' : 'pending');
        var statusLabel = c.cancelled ? 'CANCELLED' : (c.paid ? 'PAID' : 'PENDING');
        var addrParts = [];
        if (c.society) addrParts.push(c.society);
        if (c.building) addrParts.push(c.building);
        if (c.buyerFlat) addrParts.push(c.buyerFlat);
        var remarkHtml = c.remark ? '<span class="remark-icon" data-action="show-remark" data-remark="' + esc(c.remark) + '" title="Has remark">💬</span>' : '';
        var html = '<div class="customer-row compact" data-action="open-order" data-order="' + c.orderId + '">';
        html += '<div class="cr-top"><span class="cr-qty">' + c.quantity + ' ' + esc(c.unit || 'plate') + (c.quantity !== 1 ? 's' : '') + '</span><span class="status-dot ' + statusClass + '"></span><span class="cr-status ' + statusClass + '">' + statusLabel + '</span></div>';
        html += '<div class="cr-loc">' + esc(addrParts.join(' • ')) + '</div>';
        html += remarkHtml;
        html += '</div>';
        container.innerHTML += html;
    });
}

// SCREEN 7C: INDIVIDUAL ORDER DETAIL
async function sellerOrderDetailByOrderView(orderId) {
    var h = '<div class="view-enter"><div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button><h2 class="flex-1">Order Details</h2></div>';
    try {
        var order = await api('/api/seller/orders/' + orderId);
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
            var open = vals.orderWindowStart, close = vals.orderWindowEnd;
            if (open && close && open >= close) {
                toast('Orders Open must be before Orders Close', 'error');
                return;
            }
            var readyDate = readyBy.slice(0, 10);
            if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(readyBy)) {
                toast('Delivery / Ready By must be a valid date and time', 'error'); return;
            }
            if (readyDate < vals.availableDate) {
                toast('Delivery / Ready By cannot be before the offering date', 'error'); return;
            }
            var orderingDate = vals.availableDate;
            if (vals.availableDate > sellerDate('today')) {
                var orderDate = new Date(vals.availableDate + 'T00:00:00');
                orderDate.setDate(orderDate.getDate() - 1);
                orderingDate = localDateStr(orderDate);
            }
            var closeDateTime = orderingDate + 'T' + close + ':00';
            if (closeDateTime > readyBy) {
                toast('Orders Close must be before Delivery / Ready By', 'error'); return;
            }
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await api('/api/seller/kitchen');
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
                    await api('/api/seller-app/templates', { method: 'POST', body: favBody });
                } catch (favErr) { toast('Could not save favourite: ' + favErr.message, 'error'); }
            }
            await api('/api/seller/products?kitchenId=' + kid, { method: 'POST', body: vals });
            toast('Offering published!', 'success');
            S.draftOffering = null;
            S.republishSourceId = null;
            S.offeringFor = 'today';
            sellerNavigate('#/home');
        } else if (form.id === 'quickPostForm') {
            var message = (form.querySelector('[name="message"]').value || '').trim();
            if (!message) { toast('Paste a WhatsApp message first', 'error'); return; }
            var fileInput = form.querySelector('#qpImage');
            var file = fileInput && fileInput.files && fileInput.files[0];
            var imageData = null;
            if (file) {
                if (file.size > 2 * 1024 * 1024) { toast('Quick Post image must be 2 MB or smaller', 'error'); return; }
                imageData = await new Promise(function (resolve, reject) {
                    var reader = new FileReader();
                    reader.onload = function () { resolve(reader.result); };
                    reader.onerror = reject;
                    reader.readAsDataURL(file);
                });
            }
            var submitButton = form.querySelector('#qpSubmit');
            if (!S.quickPostRequestId) S.quickPostRequestId = 'ui-' + Date.now() + '-' + Math.random().toString(36).slice(2);
            if (submitButton) submitButton.disabled = true;
            try {
                await api('/api/seller-app/quick-posts', { method: 'POST', body: {
                    message: message, imageData: imageData, requestId: S.quickPostRequestId
                }});
                S.quickPostRequestId = null;
                toast('Quick Post published for Today', 'success');
                await sellerRender();
            } finally {
                if (submitButton) submitButton.disabled = false;
            }
        } else if (form.id === 'kitchenForm') {
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await api('/api/seller/kitchen');
                kid = k.id;
                S.myKitchen = k;
            }
            await api('/api/seller/kitchen/' + kid, { method: 'PUT', body: formVals(form) });
            toast('All changes saved', 'success');
        }
    } catch (err) { toast(err.message, 'error'); }
});

// EVENT DELEGATION
document.addEventListener('click', async function (e) {
    var t = e.target.closest('[data-action]');
    if (!t) return;
    var a = t.dataset.action;
    try {
        switch (a) {
            case 'go-back': history.back(); break;
            case 'noop': break;
            case 'toggle-notifs': await toggleNotifications(t.dataset.panelId || 'sellerNotifPanel'); break;
            case 'read-notification': await readNotification(t.dataset.notificationId); break;
            case 'toggle-theme': toggleTheme(); break;
            case 'go-add': sellerNavigate('#/add'); break;
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
                await api('/api/seller-app/products/' + pid + '/inventory', { method: 'PATCH', body: { delta: 1 } });
                var el = $('#inv-' + pid); if (el) el.textContent = parseInt(el.textContent) + 1;
                toast('Quantity updated', 'success'); break;
            }
            case 'inv-dec': {
                var pid = Number(t.dataset.pid);
                await api('/api/seller-app/products/' + pid + '/inventory', { method: 'PATCH', body: { delta: -1 } });
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
                        await api('/api/seller-app/products/' + pid + '/sold-out', { method: 'POST' });
                        toast('Marked as Sold Out', 'success');
                        sellerRender();
                    }
                });
                break;
            }
            case 'pause-orders': {
                var pausePid = Number(t.dataset.pid);
                await api('/api/seller-app/products/' + pausePid + '/pause', { method: 'POST' });
                toast('Orders paused', 'success');
                await sellerRender();
                break;
            }
            case 'resume-orders': {
                var resumePid = Number(t.dataset.pid);
                await api('/api/seller-app/products/' + resumePid + '/resume', { method: 'POST' });
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
                        await api('/api/seller/kitchen/' + S.myKitchen.id + '/pause', { method: 'POST' });
                        toast('Kitchen paused', 'success');
                        await sellerRender();
                    }
                });
                break;
            }
            case 'resume-kitchen': {
                if (!S.myKitchen) { toast('Kitchen is not available', 'error'); break; }
                await api('/api/seller/kitchen/' + S.myKitchen.id + '/resume', { method: 'POST' });
                toast('Kitchen resumed', 'success');
                await sellerRender();
                break;
            }
            case 'set-view-mode': S.viewMode = t.dataset.mode; await sellerRender(); break;
            case 'set-date': S.selectedDate = t.dataset.date; await sellerRender(); break;
            case 'set-date-calendar': S.selectedDate = t.value; await sellerRender(); break;
            case 'set-sort': S.sortFilter = t.value; break;
            case 'set-detail-sort': S.selectedSort = t.value; await sellerRender(); break;
            case 'set-offering-society': S.offeringFilterSociety = t.value; await sellerRender(); break;
            case 'set-offering-status': S.offeringFilterStatus = t.value; await sellerRender(); break;
            case 'open-order': sellerNavigate('#/order-detail/order/' + t.dataset.order); break;
            case 'show-remark': alert(t.dataset.remark); break;
            case 'parse-message': {
                var msg = $('#qpMessage').value;
                if (!msg.trim()) { toast('Please paste a message first', 'error'); return; }
                var result = await api('/api/seller-app/parse-message', { method: 'POST', body: { message: msg } });
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
                    await api('/api/seller/orders/' + encodeURIComponent(t.dataset.orderId) + '/status', { method: 'PATCH', body: { orderStatus: 'CANCELLED' } });
                    toast('Order cancelled', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not cancel order', 'error');
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
                await api('/api/seller-app/batch-republish', { method: 'POST', body: { productIds: S.historySelected, availableDate: sellerDate('today') } });
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
                    await api('/api/seller/orders/' + oid + '/payment-status', { method: 'PATCH' });
                    toast('Order marked as paid', 'success');
                    await sellerRender();
                } catch (err) {
                    t.disabled = false;
                    toast(err.message || 'Could not update payment status', 'error');
                }
                break;
            }
            case 'add-service-area': {
                var input = $('#newServiceArea');
                var val = input && input.value ? input.value.trim() : '';
                if (!val) return;
                var list = $('#serviceAreaList');
                var existing = list ? list.querySelectorAll('.sa-pill') : [];
                var found = false;
                existing.forEach(function (el) { if (el.dataset.name && el.dataset.name.toLowerCase() === val.toLowerCase()) found = true; });
                if (found) { toast('Society already added', 'error'); return; }
                if (!list) break;
                var pill = document.createElement('span');
                pill.className = 'sa-pill';
                pill.dataset.name = val;
                pill.innerHTML = esc(val) + ' <button type="button" data-action="remove-service-area" data-name="' + esc(val) + '" aria-label="Remove">×</button>';
                list.appendChild(pill);
                input.value = '';
                updateServiceAreasInput();
                break;
            }
            case 'remove-service-area': {
                var name = t.dataset.name;
                var pill = t.closest('.sa-pill');
                if (pill) pill.remove();
                updateServiceAreasInput();
                break;
            }
            case 'preview-offering': toast('Preview mode', 'info'); break;
            case 'preview-kitchen': toast('Opening kitchen preview...', 'info'); break;
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
    if (picker) applyOfferingDatePicker(picker);
});

// BOOT - never leaves the page on an infinite spinner
var sellerBooted = false;
window.addEventListener('hashchange', function () { if (sellerBooted) sellerRender(); });
window.addEventListener('DOMContentLoaded', async function () {
    try {
        initTheme();
        try {
            await api('/api/seller-app/demo-login', { method: 'POST' });
        } catch (e) {
            console.warn('demo-login failed:', e && e.message);
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
