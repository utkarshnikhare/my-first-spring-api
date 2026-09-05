/**
 * SocioMart Seller App v1.0 - 5-tab SPA
 */
var S = { user: null, kitchen: null, viewMode: 'editor', selectedDate: 'today', sortFilter: 'all', historySelected: [], draftOffering: null, favTemplates: [] };
var sellerRoutes = {
    '#/home': sellerHomeView, '#/add': sellerAddView, '#/create': sellerCreateView,
    '#/quick-post': sellerQuickPostView, '#/history': sellerHistoryView,
    '#/kitchen': sellerKitchenView, '#/orders': sellerOrdersView,
    '#/order-detail': sellerOrderDetailView, '#/earnings': sellerEarningsView
};
function sellerResolveRoute(hash) {
    if (sellerRoutes[hash]) return { fn: sellerRoutes[hash], arg: hash };
    if (hash.startsWith('#/order-detail/')) return { fn: sellerOrderDetailView, arg: hash.split('/')[2] };
    return { fn: sellerHomeView, arg: '#/home' };
}
async function sellerRender() {
    var hash = location.hash || '#/home';
    var route = sellerResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    closeSheet();
    try { view.innerHTML = await route.fn(route.arg) || ''; sellerUpdateNav(hash); window.scrollTo(0, 0); }
    catch (err) { view.innerHTML = '<div class="view-enter">' + emptyHtml('Warn', 'Error', err.message) + '</div>'; }
}
function sellerUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash === '#/home' ? 'home' : hash === '#/kitchen' ? 'kitchen' : (hash === '#/orders' || hash.startsWith('#/order-detail/')) ? 'orders' : hash === '#/history' ? 'history' : hash === '#/earnings' ? 'earnings' : null;
    var el = document.querySelector('[data-nav="' + (key || '') + '"]');
    if (el) el.classList.add('active');
}
function sellerNavigate(hash) { if (location.hash === hash) sellerRender(); else location.hash = hash; }
function greeting() { var h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
function offeringStatusBadge(p) { return p.soldOut ? '<span class="oc-badge soldout">SOLD OUT</span>' : '<span class="oc-badge live">LIVE</span>'; }
function statusDot(paid, cancelled) { return cancelled ? '<span class="status-dot red"></span>' : paid ? '<span class="status-dot green"></span>' : '<span class="status-dot orange"></span>'; }
function localDateStr(d) { var y = d.getFullYear(), m = ('0' + (d.getMonth() + 1)).slice(-2), day = ('0' + d.getDate()).slice(-2); return y + '-' + m + '-' + day; }
function sellerDate(dateKey) {
    if (dateKey === 'tomorrow') return localDateStr(new Date(Date.now() + 864e5));
    if (dateKey && dateKey !== 'today' && dateKey !== 'pick') return dateKey;
    return localDateStr(new Date());
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
    h += '<div class="page-head"><h1>Add Offering</h1><p class="muted small">Choose how you want to add your new offering.</p></div>';
    try { S.favTemplates = await api('/api/seller-app/templates'); } catch (e) { S.favTemplates = []; }
    h += '<div class="pathway-card" data-action="go-use-favourite"><div class="pc-icon">⭐</div><div class="pc-title">Create from Favourite</div><div class="pc-desc">Quickly post from saved templates (max 3).</div>';
    if (S.favTemplates.length > 0) { h += '<div class="favourite-pills">'; S.favTemplates.forEach(function (t) { h += '<span class="fav-pill" data-action="use-template" data-tid="' + t.id + '">⭐ ' + esc(t.name) + '</span>'; }); h += '</div>'; }
    h += '</div>';
    h += '<div class="pathway-card" data-action="go-create"><div class="pc-icon">✨</div><div class="pc-title">Create New Offering</div><div class="pc-desc">Fill in all details manually.</div></div>';
    h += '<div class="pathway-card" data-action="go-quick-post"><div class="pc-icon">📋</div><div class="pc-title">Quick Create</div><div class="pc-desc">Paste a WhatsApp message — we auto-fill details.</div></div>';
    h += '</div>';
    return h;
}

// SCREEN 1: SELLER DASHBOARD (HOME)
async function sellerHomeView() {
    var h = '<div class="view-enter">';
    h += '<div class="seller-header"><div class="sdh-text"><p class="sdh-greeting">' + greeting() + ', Aarti</p><h1 class="sdh-title">Your Dashboard</h1></div><span class="notif-bell"><button class="icon-btn" type="button" data-action="noop" aria-label="Notifications">🔔<span class="bell-badge">3</span></button><button class="icon-btn" type="button" data-action="toggle-theme" aria-label="Toggle theme">🌓</button></span></div>';
    try {
        var dash = await api('/api/seller-app/dashboard');
        S.kitchen = { id: dash.kitchenId, name: dash.kitchenName };
        h += '<div class="metric-cards-row">';
        h += '<div class="metric-card"><div class="metric-value">' + dash.viewsToday + '</div><div class="metric-label">Views Today</div></div>';
        h += '<div class="metric-card"><div class="metric-value">' + dash.followers + '</div><div class="metric-label">Followers</div></div>';
        h += '<div class="metric-card"><div class="metric-value">' + dash.totalOrders + '</div><div class="metric-label">Total Orders</div></div></div>';
        h += '<div class="section-head"><h2>My Offerings</h2></div>';
        if (!dash.offerings || dash.offerings.length === 0) { h += emptyHtml('Dish', 'No offerings yet', 'Tap "+ Add Offering" to publish your first dish.'); }
        else {
            dash.offerings.forEach(function (p) {
                h += '<div class="offering-card">';
                h += '<div class="oc-photo"></div>';
                h += '<div class="oc-body">';
                h += '<div class="oc-header"><span class="oc-name">' + esc(p.name) + '</span>' + offeringStatusBadge(p) + '</div>';
                var booked = p.bookedQuantity || 0, remaining = p.remainingQuantity, maxQty = p.maxQuantity;
                h += '<div class="oc-stats"><strong>' + booked + '</strong> booked · ' + (remaining != null ? remaining + ' available' : 'No limit') + '</div>';
                h += '<div class="oc-time-row"><span>Cutoff: <span class="time-label">' + esc(p.cutoffTime || '--') + '</span></span><span>Delivery: <span class="time-label">' + esc(p.readyByTime || '--') + '</span></span></div>';
                if (maxQty != null && remaining != null && remaining >= 0 && !p.soldOut) { h += '<div class="stepper"><button type="button" data-action="inv-dec" data-pid="' + p.id + '" aria-label="Decrease">-</button><span class="stepper-value" id="inv-' + p.id + '">' + remaining + '</span><button type="button" data-action="inv-inc" data-pid="' + p.id + '" aria-label="Increase">+</button></div>'; }
                if (!p.soldOut) { h += '<button class="btn-soldout" type="button" data-action="mark-soldout" data-pid="' + p.id + '">Mark Sold Out</button>'; }
                h += '<a class="btn btn-secondary btn-sm btn-block btn-mt-sm" href="#/order-detail/' + p.id + '">View Orders</a>';
                h += '</div></div>';
            });
        }
        h += '<button class="btn-add-offering" type="button" data-action="go-add">+ Add Offering</button>';
        h += '<div class="earnings-preview"><h3>Earnings Summary</h3>';
        h += '<div class="ep-row"><span class="ep-label">Confirmed Today</span><span class="ep-value green">' + money(dash.confirmedToday) + '</span></div>';
        h += '<div class="ep-row"><span class="ep-label">Pending</span><span class="ep-value orange">' + money(dash.pending) + '</span></div>';
        h += '<div class="ep-row"><span class="ep-label">This Month</span><span class="ep-value">' + money(dash.thisMonth) + '</span></div></div>';
    } catch (e) { h += emptyHtml('Warn', 'Could not load dashboard', e.message); }
    h += '</div>';
    return h;
}

// SCREEN 5: HISTORY
async function sellerHistoryView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>History</h1><p class="muted small">Repost items from the last 2 days.</p></div>';
    S.historySelected = [];
    try {
        var items = await api('/api/seller-app/history');
        if (items.length === 0) { h += emptyHtml('Hist', 'No recent items', 'Items from the last 2 days appear here.'); }
        else {
            h += '<div class="history-date-header">YESTERDAY AND TODAY</div>';
            items.forEach(function (p) { h += '<label class="history-card"><input type="checkbox" data-action="toggle-history" data-pid="' + p.id + '"><span class="hc-body"><span class="hc-name">' + esc(p.name) + '</span><span class="hc-meta">' + esc(p.cutoffTime || '') + '</span></span><span class="hc-price">' + money(p.price) + '</span></label>'; });
            h += '<button class="sticky-footer-btn" type="button" data-action="batch-republish">Publish Selected</button>';
        }
    } catch (e) { h += emptyHtml('Warn', 'Could not load history', e.message); }
    h += '</div>';
    return h;
}

// SCREEN 4: QUICK POST
async function sellerQuickPostView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Quick Create</h1><p class="muted small">Paste your WhatsApp promotional message.</p></div>';
    h += '<div class="segmented"><button type="button" class="active" data-action="set-view-mode" data-mode="editor">Editor View</button><button type="button" data-action="set-view-mode" data-mode="buyer">Buyer View</button></div>';
    h += '<textarea class="qp-textarea" id="qpMessage" placeholder="Paste your WhatsApp message here..."></textarea>';
    h += '<button class="btn btn-primary btn-block" type="button" data-action="parse-message">Parse Message</button><div id="parseResult"></div></div>';
    return h;
}

// SCREEN 3: CREATE OFFERING (MANUAL FORM)
async function sellerCreateView() {
    var t = S.draftOffering || {};
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Create Offering</h1><p class="muted small">Fill in the details for your new dish.</p></div>';
    h += '<form class="seller-form" id="createOfferingForm">';
    h += '<div class="form-group"><label class="form-label">Photos <span class="req">*</span></label><div class="photo-upload-row"><div class="photo-tile" data-action="add-photo">+</div></div></div>';
    h += '<div class="form-group"><label class="form-label">Item Name <span class="req">*</span></label><input class="form-input" name="name" value="' + esc(t.name || '') + '" placeholder="e.g. POHA" required></div>';
    h += '<div class="form-group"><label class="form-label">Short Description</label><textarea class="form-textarea" name="description">' + esc(t.description || '') + '</textarea></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Price (Rs) <span class="req">*</span></label><input class="form-input" name="price" type="number" value="' + (t.price || '') + '" placeholder="100" required></div>';
    h += '<div class="form-group"><label class="form-label">Unit <span class="req">*</span></label><select class="form-select" name="priceUnit"><option value="Per Piece">Per Piece</option><option value="Per Plate">Per Plate</option><option value="Per Box">Per Box</option></select></div></div>';
    h += '<div class="form-group"><label class="form-label">Availability <span class="req">*</span></label><div class="radio-group"><label class="radio-option selected" data-action="set-availability" data-val="today">Today</label><label class="radio-option" data-action="set-availability" data-val="tomorrow">Tomorrow</label></div></div>';
    h += '<input type="hidden" name="availableDate" id="availDate" value="' + sellerDate('today') + '">';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Orders Open <span class="req">*</span></label><input class="form-input" name="orderWindowStart" type="time" value="08:00"></div>';
    h += '<div class="form-group"><label class="form-label">Orders Close <span class="req">*</span></label><input class="form-input" name="orderWindowEnd" type="time" value="10:00"></div></div>';
    h += '<div class="form-group"><label class="form-label">Quantity Available <span class="req">*</span></label><input class="form-input" name="maxQuantity" type="number" placeholder="Blank for unlimited"></div>';
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
        h += '<div class="daily-total-card"><div class="dtc-number">' + summary.totalOrderCount + '</div><div class="dtc-label">Total Orders</div>';
        h += '<div class="dtc-badges"><span class="dtc-badge green">✓ ' + summary.paidCount + ' Paid</span><span class="dtc-badge orange">⏳ ' + summary.pendingCount + ' Pending</span><span class="dtc-badge red">✕ ' + summary.cancelledCount + ' Cancelled</span></div>';
        if (summary.totalRevenue) { h += '<div class="tiny muted mt-2">Revenue: <strong class="text-brand">' + money(summary.totalRevenue) + '</strong></div>'; }
        h += '</div>';
        if (!summary.products || summary.products.length === 0) { h += emptyHtml('📋', 'No orders', 'Orders for this date will appear here.'); }
        else {
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
    var h = '<div class="view-enter"><div class="page-head"><h1>Manage Kitchen</h1></div>';
    h += '<div class="kitchen-status-badge">Kitchen Published</div>';
    h += '<button class="btn btn-secondary btn-sm btn-block" type="button" data-action="preview-kitchen">Preview Kitchen Page</button>';
    var kitchen = null;
    try { kitchen = await api('/api/seller/kitchen'); S.myKitchen = kitchen; S.kitchen = kitchen; } catch (e) { }
    h += '<form class="seller-form" id="kitchenForm">';
    h += '<div class="kitchen-avatar-upload"><div class="kitchen-avatar" data-action="upload-avatar">' + (kitchen && kitchen.imageUrl ? '<img src="' + esc(kitchen.imageUrl) + '" class="avatar-img">' : '') + '</div></div>';
    h += '<div class="form-group"><label class="form-label">Kitchen Name</label><input class="form-input" name="displayName" value="' + esc(kitchen && kitchen.displayName ? kitchen.displayName : 'Aarti Kitchen') + '"></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">Society</label><input class="form-input" name="society" value="' + esc(kitchen && kitchen.society ? kitchen.society : 'Sunshine Society') + '"></div><div class="form-group"><label class="form-label">Building</label><input class="form-input" name="building" value="' + esc(kitchen && kitchen.building ? kitchen.building : 'Building B') + '"></div></div>';
    h += '<div class="form-group"><label class="form-label">Speciality</label><input class="form-input" name="shortDescription" value="' + esc(kitchen && kitchen.shortDescription ? kitchen.shortDescription : 'Homemade Maharashtrian Food') + '"></div>';
    h += '<div class="form-group"><label class="form-label">Full Description</label><textarea class="form-textarea" name="description">' + esc(kitchen && kitchen.description ? kitchen.description : 'Fresh homemade breakfast and traditional snacks') + '</textarea></div>';
    h += '<div class="form-row-2"><div class="form-group"><label class="form-label">WhatsApp</label><input class="form-input" name="whatsappLink" value="' + esc(kitchen && kitchen.whatsappLink ? kitchen.whatsappLink : '+91 9100000001') + '"></div><div class="form-group"><label class="form-label">Instagram</label><input class="form-input" name="instagramLink" value="' + esc(kitchen && kitchen.instagramLink ? kitchen.instagramLink : '@aartiskitchen') + '"></div></div>';
    h += '<div class="form-group"><label class="form-label">UPI ID</label><input class="form-input" name="upiId" value="' + esc(kitchen && kitchen.upiId ? kitchen.upiId : 'aarti@okhdfc') + '"></div>';
    h += '<div class="info-box">Your menu loads automatically from live offerings.</div>';
    h += '<button class="btn btn-primary btn-block" type="submit">SAVE CHANGES</button></form></div>';
    return h;
}

// SCREEN 8: EARNINGS
async function sellerEarningsView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Earnings</h1></div>';
    try {
        var e = await api('/api/seller-app/earnings');
        h += '<div class="earnings-header-card"><div class="ehc-label">CONFIRMED TODAY</div><div class="ehc-main">' + money(e.confirmedToday) + '</div>';
        h += '<div class="ehc-row"><div class="ehc-item"><div class="ehc-val orange">' + money(e.pending) + '</div><div class="ehc-sub">PENDING</div></div><div class="ehc-item"><div class="ehc-val">' + money(e.thisMonth) + '</div><div class="ehc-sub">THIS MONTH</div></div></div></div>';
        if (!e.items || e.items.length === 0) { h += emptyHtml('Earn', 'No earnings yet', 'Your earnings breakdown appears here.'); }
        else { e.items.forEach(function (item) { h += '<div class="earning-item"><span class="ei-icon">🍽️</span><span class="ei-body"><span class="ei-name">' + esc(item.productName) + '</span><span class="ei-orders">' + item.totalOrders + ' orders</span></span><span class="ei-revenue"><span class="ei-confirmed">' + money(item.confirmedRevenue) + '</span><br><span class="ei-pending">' + money(item.pendingRevenue) + '</span></span></div>'; }); }
        h += '<a class="btn btn-secondary btn-block" href="#/history">VIEW FULL HISTORY</a>';
    } catch (err) { h += emptyHtml('Warn', 'Could not load earnings', err.message); }
    h += '</div>';
    return h;
}

// SCREEN 7B: ORDER DRILL-DOWN
async function sellerOrderDetailView(productId) {
    S.selectedSort = S.selectedSort || 'all';
    var h = '<div class="view-enter">';
    h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button><h2 class="flex-1">Order Details</h2></div>';
    h += '<div class="date-tabs"><button class="date-tab' + (S.selectedDate === 'today' ? ' active' : '') + '" data-action="set-date" data-date="today">Today</button><button class="date-tab' + (S.selectedDate === 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="tomorrow">Tomorrow</button><button class="date-tab' + (S.selectedDate !== 'today' && S.selectedDate !== 'tomorrow' ? ' active' : '') + '" data-action="set-date" data-date="pick">Pick date</button></div>';
    if (S.selectedDate === 'pick') { h += '<div class="section-gap"><input type="date" class="sort-select w-full" data-action="set-date-calendar" value="' + sellerDate(S.selectedDate) + '"></div>'; }
    try {
        var detail = await api('/api/seller-app/orders/product/' + productId + '?date=' + sellerDate(S.selectedDate));
        h += '<div class="drilldown-header"><h3>' + esc(detail.productName) + '</h3>';
        h += '<div class="dd-stats">Revenue: <strong>' + money(detail.totalRevenue) + '</strong> · Plates: <strong>' + detail.totalPlates + '</strong></div>';
        h += '<div class="dtc-badges"><span class="dtc-badge green">Paid: ' + detail.paidCount + '</span> <span class="dtc-badge orange">Pending: ' + detail.pendingCount + '</span> <span class="dtc-badge red">Cancelled: ' + detail.cancelledCount + '</span></div></div>';
        h += '<select class="sort-select" data-action="set-detail-sort"><option value="all"' + (S.selectedSort === 'all' ? ' selected' : '') + '>All Customers</option><option value="paid"' + (S.selectedSort === 'paid' ? ' selected' : '') + '>Paid Only</option><option value="pending"' + (S.selectedSort === 'pending' ? ' selected' : '') + '>Pending Only</option><option value="cancelled"' + (S.selectedSort === 'cancelled' ? ' selected' : '') + '>Cancelled Only</option></select>';
        var customers = (detail.customers || []).slice();
        if (S.selectedSort === 'paid') customers = customers.filter(function (c) { return c.paid; });
        else if (S.selectedSort === 'pending') customers = customers.filter(function (c) { return !c.paid && !c.cancelled; });
        else if (S.selectedSort === 'cancelled') customers = customers.filter(function (c) { return c.cancelled; });
        if (customers.length === 0) { h += emptyHtml('📋', 'No customer orders', 'No orders match this filter.'); }
        else {
            h += '<div class="customer-count">' + customers.length + ' customer' + (customers.length !== 1 ? 's' : '') + '</div>';
            customers.forEach(function (c) {
                var statusClass = c.cancelled ? 'cancelled' : (c.paid ? 'paid' : 'pending');
                var statusLabel = c.cancelled ? 'Cancelled' : (c.paid ? 'Paid' : 'Pending');
                h += '<div class="order-detail-card ' + (c.cancelled ? 'cancelled' : '') + '">';
                h += '<div class="odc-top"><span class="odc-order-id">#' + esc(c.orderNumber || ('SM-' + c.orderId)) + '</span><span class="odc-status-pill ' + statusClass + '">' + statusLabel + '</span></div>';
                h += '<div class="odc-buyer-row"><span class="odc-buyer">' + esc(c.buyerName || 'Unknown') + '</span><span class="odc-qty">×' + c.quantity + ' ' + esc(c.unit || 'plate') + (c.quantity > 1 ? 's' : '') + '</span></div>';
                if (c.buyerMobile) { h += '<div class="odc-mobile">📱 ' + esc(c.buyerMobile) + '</div>'; }
                h += '<div class="odc-items"><div class="odc-item-name">' + esc(detail.productName) + '</div>';
                if (c.pricePerUnit) { h += '<div class="odc-item-meta">' + money(c.pricePerUnit) + ' × ' + c.quantity + '</div>'; }
                if (c.totalAmount) { h += '<div class="odc-item-total">Total: ' + money(c.totalAmount) + '</div>'; }
                h += '</div>';
                var addrParts = [];
                if (c.society) addrParts.push(c.society);
                if (c.building) addrParts.push(c.building);
                if (c.buyerFlat) addrParts.push(c.buyerFlat);
                if (addrParts.length > 0) { h += '<div class="odc-address">📍 ' + esc(addrParts.join(', ')) + '</div>'; }
                if (c.orderStatus) { h += '<div class="odc-address"><strong>Order Status:</strong> ' + esc(c.orderStatus) + '</div>'; }
                if (c.remark) { h += '<div class="odc-remark">"' + esc(c.remark) + '"</div>'; }
                h += '</div>';
            });
        }
    } catch (e) { h += emptyHtml('Warn', 'Could not load details', e.message); }
    h += '</div>';
    return h;
}

// Form submission
document.addEventListener('submit', async function (e) {
    var form = e.target.closest('form');
    if (!form) return;
    e.preventDefault();
    try {
        if (form.id === 'createOfferingForm') {
            var vals = formVals(form);
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await api('/api/seller/kitchen');
                kid = k.id;
                S.myKitchen = k;
            }
            var saveFav = $('#favToggle') && $('#favToggle').classList.contains('on');
            if (saveFav) {
                try {
                    var favBody = { name: vals.name, description: vals.description || '', price: Number(vals.price), priceUnit: vals.priceUnit, maxQuantity: vals.maxQuantity ? Number(vals.maxQuantity) : null, orderWindowStart: vals.orderWindowStart, orderWindowEnd: vals.orderWindowEnd, availableDate: vals.availableDate };
                    await api('/api/seller-app/templates', { method: 'POST', body: favBody });
                } catch (favErr) { toast('Could not save favourite: ' + favErr.message, 'error'); }
            }
            await api('/api/seller/products?kitchenId=' + kid, { method: 'POST', body: vals });
            toast('Offering published!', 'success'); sellerNavigate('#/home');
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
            case 'set-view-mode': S.viewMode = t.dataset.mode; await sellerRender(); break;
            case 'set-date': S.selectedDate = t.dataset.date; await sellerRender(); break;
            case 'set-date-calendar': S.selectedDate = t.value; await sellerRender(); break;
            case 'set-sort': S.sortFilter = t.value; break;
            case 'set-detail-sort': S.selectedSort = t.value; await sellerRender(); break;
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
            case 'preview-offering': toast('Preview mode', 'info'); break;
            case 'preview-kitchen': toast('Opening kitchen preview...', 'info'); break;
            case 'add-photo': toast('Photo upload (demo)', 'info'); break;
            case 'upload-avatar': toast('Avatar upload (demo)', 'info'); break;
            case 'seller-retry': location.reload(); break;
            case 'set-availability': $all('.radio-option').forEach(function (el) { el.classList.remove('selected'); }); t.classList.add('selected'); var av = $('#availDate'); if (av) av.value = sellerDate(t.dataset.val || 'today'); break;
        }
    } catch (err) { toast(err.message, 'error'); }
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
