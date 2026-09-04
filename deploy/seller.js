/**
 * SocioMart Seller App v1.0 - Complete 5-tab SPA
 * Dashboard | Kitchen | Orders | History | Earnings
 */

var S = {
    user: null,
    kitchen: null,
    myKitchen: null,
    viewMode: 'editor',
    selectedDate: 'today',
    sortFilter: 'all',
    historySelected: [],
    draftOffering: null,
    favTemplates: [],
    offerings: [],
    dashboardMetrics: { viewsToday: 0, followers: 0, totalOrders: 0 },
    earnings: { confirmedToday: 0, pending: 0, thisMonth: 0, items: [] },
    ordersSummary: { total: 0, paid: 0, pending: 0, cancelled: 0, products: [] }
};

var sellerRoutes = {
    '#/home': sellerHomeView,
    '#/add': sellerAddView,
    '#/create': sellerCreateView,
    '#/quick-post': sellerQuickPostView,
    '#/history': sellerHistoryView,
    '#/kitchen': sellerKitchenView,
    '#/orders': sellerOrdersView,
    '#/order-detail': sellerOrderDetailView,
    '#/earnings': sellerEarningsView
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
    try {
        view.innerHTML = await route.fn(route.arg) || '';
        sellerUpdateNav(hash);
        window.scrollTo(0, 0);
    } catch (err) {
        view.innerHTML = '<div class="view-enter">' + emptyHtml('⚠️', 'Something went wrong', err.message) + '</div>';
    }
}

function sellerUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash === '#/home' ? 'home' :
              hash === '#/kitchen' ? 'kitchen' :
              (hash === '#/orders' || hash.startsWith('#/order-detail/')) ? 'orders' :
              hash === '#/history' ? 'history' :
              hash === '#/earnings' ? 'earnings' : null;
    var el = document.querySelector('[data-nav="' + (key || '') + '"]');
    if (el) el.classList.add('active');
}

function sellerNavigate(hash) {
    if (location.hash === hash) sellerRender();
    else location.hash = hash;
}

function greeting() {
    var h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

function offeringStatusBadge(p) {
    if (p.soldOut) return '<span class="oc-badge soldout">SOLD OUT</span>';
    if (p.closed) return '<span class="oc-badge closed">CLOSED</span>';
    return '<span class="oc-badge live">LIVE</span>';
}

function statusDot(paid, cancelled) {
    if (cancelled) return '<span class="status-dot red"></span>';
    if (paid) return '<span class="status-dot green"></span>';
    return '<span class="status-dot orange"></span>';
}

function localDateStr(d) {
    var y = d.getFullYear();
    var m = ('0' + (d.getMonth() + 1)).slice(-2);
    var day = ('0' + d.getDate()).slice(-2);
    return y + '-' + m + '-' + day;
}

function sellerDate(dateKey) {
    if (dateKey === 'tomorrow') return localDateStr(new Date(Date.now() + 864e5));
    return localDateStr(new Date());
}

// ==================== SCREEN 1: SELLER DASHBOARD / HOME ====================

async function sellerHomeView() {
    var h = '<div class="view-enter">';
    
    // Header with greeting
    var sellerName = (S.user && S.user.name) || 'Seller';
    h += '<div class="seller-dashboard-header">';
    h += '<div class="sdh-top-row">';
    h += '<div class="sdh-greeting">' + greeting() + ', ' + esc(sellerName) + ' 👋</div>';
    h += '<button class="icon-btn notif-bell" data-action="toggle-notifs" aria-label="Notifications">🔔<span class="bell-badge">3</span></button>';
    h += '</div>';
    h += '<h1 class="sdh-title">Your SocioMart Dashboard</h1>';
    h += '</div>';

    // Fetch dashboard data
    try {
        var dashboard = await api('/api/seller-app/dashboard');
        S.offerings = dashboard.offerings || [];
        S.dashboardMetrics = {
            viewsToday: dashboard.viewsToday || 37,
            followers: dashboard.followers || 18,
            totalOrders: dashboard.totalOrders || 162
        };
    } catch (e) {
        S.offerings = getDemoOfferings();
        S.dashboardMetrics = { viewsToday: 37, followers: 18, totalOrders: 162 };
    }

    // Summary Cards
    h += '<div class="metric-cards-row">';
    h += '<div class="metric-card"><div class="metric-value">' + S.dashboardMetrics.viewsToday + '</div><div class="metric-label">Views Today</div></div>';
    h += '<div class="metric-card"><div class="metric-value">' + S.dashboardMetrics.followers + '</div><div class="metric-label">Followers</div></div>';
    h += '<div class="metric-card"><div class="metric-value">' + S.dashboardMetrics.totalOrders + '</div><div class="metric-label">Total Orders</div></div>';
    h += '</div>';

    // My Offerings Section
    h += '<div class="section-head"><h2>My Offerings</h2><span class="section-count">' + S.offerings.length + ' items</span></div>';

    if (S.offerings.length === 0) {
        h += emptyHtml('🍽️', 'No offerings yet', 'Tap "Add Offering" to create your first food offering.');
    } else {
        S.offerings.forEach(function (p) {
            h += offeringCardHtml(p);
        });
    }

    // Add Offering Button
    h += '<button class="btn-add-offering" data-action="go-add">+ Add Offering</button>';

    h += '</div>';
    return h;
}

function offeringCardHtml(p) {
    var available = p.remainingQuantity !== null && p.remainingQuantity !== undefined ? p.remainingQuantity : 'No Limit';
    var booked = p.bookedQuantity || 0;
    var isLimited = p.maxQuantity !== null && p.maxQuantity !== undefined;
    var isSoldOut = p.soldOut || (isLimited && p.remainingQuantity <= 0);

    var html = '<div class="offering-card" data-action="view-offering" data-pid="' + p.id + '">';
    
    // Header with name and status
    html += '<div class="oc-header">';
    html += '<div class="oc-name">' + esc(p.name) + '</div>';
    html += offeringStatusBadge({ soldOut: isSoldOut, closed: p.closed });
    html += '</div>';

    // Stats row
    html += '<div class="oc-stats">';
    html += '<span class="oc-stat"><strong>' + booked + '</strong> booked</span>';
    html += '<span class="oc-stat">·</span>';
    html += '<span class="oc-stat"><strong>' + available + '</strong> available</span>';
    html += '</div>';


// ==================== SCREEN 2: ADD OFFERING ENTRY POINT ====================

async function sellerAddView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>What would you like to do?</h1><p class="muted small">Choose how you want to add your new offering to the market.</p></div>';

    try {
        S.favTemplates = await api('/api/seller-app/templates');
    } catch (e) {
        S.favTemplates = getDemoTemplates();
    }

    h += '<div class="pathway-card" data-action="go-use-favourite">';
    h += '<div class="pc-icon">⭐</div>';
    h += '<div class="pc-title">Create from Favourite</div>';
    h += '<div class="pc-desc">Quickly post from saved templates (max 3).</div>';
    if (S.favTemplates.length > 0) {
        h += '<div class="favourite-pills">';
        S.favTemplates.forEach(function (t) {
            h += '<span class="fav-pill" data-action="use-template" data-tid="' + t.id + '">' + esc(t.name) + '</span>';
        });
        h += '</div>';
    }
    if (S.favTemplates.length >= 3) {
        h += '<p class="fav-limit-note">Remove an existing favourite to save a new one.</p>';
    }
    h += '</div>';

    h += '<div class="pathway-card" data-action="go-create">';
    h += '<div class="pc-icon">✨</div>';
    h += '<div class="pc-title">Create New Offering</div>';
    h += '<div class="pc-desc">Fill in all details manually.</div>';
    h += '</div>';


// ==================== SCREEN 3: CREATE OFFERING FORM ====================

async function sellerCreateView() {
    var draft = S.draftOffering || {};
    var h = '<div class="view-enter">';
    h += '<form id="createForm" class="create-form">';
    h += '<div class="page-head"><h1>Create New Offering</h1><p class="muted small">Fill in the details for your new food offering.</p></div>';

    h += '<div class="form-group">';
    h += '<label class="form-label">Photos <span class="required">*</span></label>';
    h += '<div class="photo-upload-row">';
    h += '<div class="photo-tile" data-action="add-photo"><span class="pt-icon">📷</span><span class="pt-label">Add Photo</span></div>';
    h += '</div>';
    h += '<p class="form-hint">Add up to 5 photos of your dish.</p>';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="itemName">Item Name <span class="required">*</span></label>';
    h += '<input class="form-input" id="itemName" name="name" value="' + esc(draft.name || '') + '" placeholder="e.g., Poha, Idli, Dosa" required>';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="itemDesc">Short Description</label>';
    h += '<textarea class="form-input" id="itemDesc" name="description" rows="2" placeholder="Describe your dish...">' + esc(draft.description || '') + '</textarea>';
    h += '</div>';

    h += '<div class="form-row">';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="itemPrice">Price (₹) <span class="required">*</span></label>';
    h += '<input class="form-input" id="itemPrice" name="price" type="number" inputmode="numeric" value="' + (draft.price || '') + '" placeholder="50" required>';
    h += '</div>';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="itemUnit">Unit</label>';
    h += '<select class="form-input" id="itemUnit" name="priceUnit">';
    var units = ['per plate', 'per piece', 'per box', 'per kg', 'per bowl'];
    units.forEach(function (u) {
        h += '<option value="' + u + '" ' + (draft.priceUnit === u ? 'selected' : '') + '>' + u + '</option>';
    });
    h += '</select>';
    h += '</div>';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label">Availability</label>';
    h += '<div class="radio-group">';
    h += '<div class="radio-option selected" data-action="set-availability" data-val="today">Today</div>';
    h += '<div class="radio-option" data-action="set-availability" data-val="tomorrow">Tomorrow</div>';
    h += '<div class="radio-option" data-action="set-availability" data-val="custom">Specific Date</div>';
    h += '</div>';
    h += '<input type="date" class="form-input" id="availDate" name="availableDate" style="margin-top:8px; display:none">';
    h += '</div>';

    h += '<div class="form-row">';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="orderOpen">Orders Open</label>';
    h += '<input class="form-input" id="orderOpen" name="orderWindowStart" type="time" value="' + esc(draft.orderWindowStart || '06:00') + '">';
    h += '</div>';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="orderClose">Orders Close</label>';
    h += '<input class="form-input" id="orderClose" name="orderWindowEnd" type="time" value="' + esc(draft.orderWindowEnd || '09:00') + '">';
    h += '</div>';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="maxQty">Quantity Available</label>';
    h += '<input class="form-input" id="maxQty" name="maxQuantity" type="number" inputmode="numeric" value="' + (draft.maxQuantity || '') + '" placeholder="Leave blank for unlimited">';
    h += '<p class="form-hint">Leave blank for no limit.</p>';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="toggle-label">';
    h += '<input type="checkbox" id="favToggle" name="saveAsFavourite" ' + (draft.saveAsFavourite ? 'checked' : '') + '>';
    h += '<span class="toggle-switch"></span>';
    h += '<span class="toggle-text">Save as Favourite (max 3)</span>';

// ==================== SCREEN 4: QUICK POST / WHATSAPP PASTE ====================

async function sellerQuickPostView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Quick Create</h1><p class="muted small">Paste a WhatsApp-style message to auto-fill offering details.</p></div>';

    h += '<form id="quickPostForm" class="create-form">';
    h += '<div class="form-group">';
    h += '<label class="form-label" for="qpMessage">Paste your message</label>';
    h += '<textarea class="form-input qp-textarea" id="qpMessage" name="message" rows="6" placeholder="e.g., Tomorrow fresh homemade Poha available. ₹40 per plate. 30 plates available. Orders open till 9 PM. Delivery tomorrow 8 AM."></textarea>';
    h += '</div>';
    h += '<button type="button" class="btn btn-primary btn-block" data-action="parse-message">Parse Message</button>';
    h += '</form>';

    h += '<div id="parseResult"></div>';
    h += '</div>';
    return h;
}

// ==================== SCREEN 5: HISTORY & REPUBLISH ====================

async function sellerHistoryView() {
    S.historySelected = [];
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>History and Republish</h1><p class="muted small">Quickly republish offerings from your recent history.</p></div>';

    try {
        var items = await api('/api/seller-app/history');
        if (items.length === 0) {
            h += emptyHtml('📜', 'No recent items', 'Items from the last 2 days appear here.');
        } else {
            var grouped = {};
            items.forEach(function (item) {
                var dateKey = item.availableDate || 'Unknown';
                if (!grouped[dateKey]) grouped[dateKey] = [];
                grouped[dateKey].push(item);
            });

            Object.keys(grouped).sort().reverse().forEach(function (date) {
                h += '<div class="history-date-group">';
                h += '<div class="hdg-label">' + formatHistoryDate(date) + '</div>';
                grouped[date].forEach(function (item) {
                    h += '<label class="history-card">';
                    h += '<input type="checkbox" data-action="toggle-history" data-pid="' + item.id + '">';
                    h += '<div class="hc-body">';
                    h += '<div class="hc-name">' + esc(item.name) + '</div>';
                    h += '<div class="hc-meta">₹' + item.price + ' / ' + esc(item.priceUnit || 'plate') + ' · ' + (item.maxQuantity || '∞') + ' portions</div>';
                    if (item.cutoffTime) h += '<div class="hc-time">⏰ Cutoff: ' + esc(item.cutoffTime) + '</div>';
                    if (item.readyByTime) h += '<div class="hc-time">📦 Delivery: ' + esc(item.readyByTime) + '</div>';

// ==================== SCREEN 6: MANAGE KITCHEN ====================

async function sellerKitchenView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Manage Kitchen</h1><p class="muted small">Your public kitchen profile.</p></div>';

    try {
        S.myKitchen = await api('/api/seller/kitchen');
    } catch (e) {
        S.myKitchen = getDemoKitchen();
    }

    var k = S.myKitchen;

    h += '<form id="kitchenForm" class="create-form">';

    h += '<div class="kitchen-avatar-upload">';
    h += '<div class="kitchen-avatar" data-action="upload-avatar">' + (k.imageUrl ? '<img src="' + esc(k.imageUrl) + '" alt="Kitchen">' : '🏪') + '</div>';
    h += '</div>';

    h += '<div class="form-section-title">Kitchen Information</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="kitchenName">Kitchen Name</label>';
    h += '<input class="form-input" id="kitchenName" name="name" value="' + esc(k.name || "Aarti's Kitchen") + '" required>';
    h += '</div>';

    h += '<div class="form-row">';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="kitchenSociety">Society/Locality</label>';
    h += '<input class="form-input" id="kitchenSociety" name="society" value="' + esc(k.society || 'Sunshine Society') + '">';
    h += '</div>';
    h += '<div class="form-group flex-1">';
    h += '<label class="form-label" for="kitchenBuilding">Building/Tower</label>';
    h += '<input class="form-input" id="kitchenBuilding" name="building" value="' + esc(k.building || 'Building B') + '">';
    h += '</div>';
    h += '</div>';

    h += '<div class="form-section-title">About Your Kitchen</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="kitchenSpeciality">Speciality</label>';
    h += '<input class="form-input" id="kitchenSpeciality" name="speciality" value="' + esc(k.speciality || 'Fresh homemade breakfast and traditional Maharashtrian snacks') + '">';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="form-label" for="kitchenDesc">Full Description</label>';
    h += '<textarea class="form-input" id="kitchenDesc" name="description" rows="4" placeholder="Describe your kitchen...">' + esc(k.description || 'We prepare fresh, homemade Maharashtrian food with love.') + '</textarea>';
    h += '</div>';

    h += '<div class="form-section-title">Kitchen Gallery</div>';
    h += '<div class="gallery-grid">';
    h += '<div class="photo-tile" data-action="add-photo"><span class="pt-icon">📷</span><span class="pt-label">Add Photo</span></div>';
    h += '</div>';

    h += '<div class="form-section-title">Contact & Social</div>';

    h += '<div class="form-group">';
    h += '<label class="toggle-label">';

// ==================== SCREEN 7: ORDERS ====================

async function sellerOrdersView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Orders</h1></div>';

    h += '<div class="date-tabs">';
    h += '<button class="date-tab ' + (S.selectedDate === 'today' ? 'active' : '') + '" data-action="set-date" data-date="today">Today</button>';
    h += '<button class="date-tab ' + (S.selectedDate === 'tomorrow' ? 'active' : '') + '" data-action="set-date" data-date="tomorrow">Tomorrow</button>';
    h += '</div>';

    try {
        var summary = await api('/api/seller-app/orders/summary?date=' + S.selectedDate);
        S.ordersSummary = summary;

        h += '<div class="daily-total-card">';
        h += '<div class="dtc-number">' + summary.totalOrderCount + '</div>';
        h += '<div class="dtc-label">Total Orders</div>';
        h += '<div class="dtc-badges">';
        h += '<span class="dtc-badge green">✓ ' + summary.paidCount + ' Paid</span>';
        h += '<span class="dtc-badge orange">⏳ ' + summary.pendingCount + ' Pending</span>';
        h += '<span class="dtc-badge red">✕ ' + summary.cancelledCount + ' Cancelled</span>';
        h += '</div>';
        h += '</div>';

        if (summary.products && summary.products.length > 0) {
            summary.products.forEach(function (p) {
                h += '<div class="order-product-card" data-action="view-order-detail" data-pid="' + p.productId + '">';
                h += '<div class="opc-header">';
                h += '<div class="opc-name">' + esc(p.productName) + '</div>';
                h += '<div class="opc-revenue">₹' + p.totalRevenue + '</div>';
                h += '</div>';
                h += '<div class="opc-meta">' + p.totalQuantity + ' plates · ' + p.paidCount + ' paid, ' + p.pendingCount + ' pending</div>';
                h += '<button class="btn btn-secondary btn-sm" style="margin-top:8px">View Orders</button>';
                h += '</div>';
            });
        } else {
            h += emptyHtml('📋', 'No orders', 'No orders for ' + S.selectedDate + '.');
        }
    } catch (e) {
        h += emptyHtml('📋', 'No orders', 'No orders for ' + S.selectedDate + '.');
    }

    h += '</div>';
    return h;
}

// ==================== SCREEN 7A: ORDER DETAIL ====================

async function sellerOrderDetailView(productId) {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><button class="icon-btn" data-action="go-back">←</button><h1>Order Details</h1></div>';

    try {
        var detail = await api('/api/seller-app/orders/product/' + productId + '?date=' + S.selectedDate);

        h += '<div class="drilldown-header">';
        h += '<h3>' + esc(detail.productName) + '</h3>';
        h += '<div class="dd-stats">Revenue: <strong>₹' + detail.totalRevenue + '</strong> · Plates: <strong>' + detail.totalPlates + '</strong></div>';

// ==================== SCREEN 8: EARNINGS ====================

async function sellerEarningsView() {
    var h = '<div class="view-enter">';
    h += '<div class="page-head"><h1>Earnings</h1></div>';

    try {
        var e = await api('/api/seller-app/earnings');
        S.earnings = e;

        h += '<div class="earnings-header-card">';
        h += '<div class="ehc-label">CONFIRMED TODAY</div>';
        h += '<div class="ehc-main">₹' + e.confirmedToday + '</div>';
        h += '<div class="ehc-row">';
        h += '<div class="ehc-item"><div class="ehc-val orange">⏳ ₹' + e.pending + '</div><div class="ehc-sub">PENDING</div></div>';
        h += '<div class="ehc-item"><div class="ehc-val">₹' + e.thisMonth + '</div><div class="ehc-sub">THIS MONTH</div></div>';
        h += '</div>';
        h += '</div>';

        h += '<div class="section-head"><h2>Item-wise Breakdown</h2></div>';

        if (e.items && e.items.length > 0) {
            e.items.forEach(function (item) {
                h += '<div class="ep-row">';
                h += '<span class="ep-label">' + esc(item.name) + ' (' + item.orders + ' orders)</span>';
                h += '<span class="ep-value green">₹' + item.confirmed + '</span>';
                h += '</div>';
                if (item.pending > 0) {
                    h += '<div class="ep-row" style="padding-left:16px">';
                    h += '<span class="ep-label">↳ Pending</span>';
                    h += '<span class="ep-value orange">₹' + item.pending + '</span>';
                    h += '</div>';
                }
            });
        }

        h += '<button class="btn btn-secondary btn-block" style="margin-top:16px">View Full History</button>';
    } catch (e) {
        h += emptyHtml('💰', 'No earnings data', 'Earnings will appear here once you start receiving orders.');

// ==================== DEMO DATA FALLBACKS ====================

function getDemoOfferings() {
    return [
        { id: 1, name: 'Poha', description: 'Fresh homemade poha with onions and lemon', price: 40, priceUnit: 'per plate', maxQuantity: 30, remainingQuantity: 10, bookedQuantity: 20, cutoffTime: '9:00 AM', readyByTime: 'Today at 6:00 PM', soldOut: false },
        { id: 2, name: 'Idli', description: 'Soft steamed idli with sambar and chutney', price: 50, priceUnit: 'per plate', maxQuantity: 25, remainingQuantity: 15, bookedQuantity: 10, cutoffTime: '9:00 PM', readyByTime: 'Tomorrow at 7:00 AM', soldOut: false },
        { id: 3, name: 'Dosa', description: 'Crispy masala dosa with potato filling', price: 60, priceUnit: 'per piece', maxQuantity: 20, remainingQuantity: 0, bookedQuantity: 20, cutoffTime: '10:00 AM', readyByTime: 'Today at 1:00 PM', soldOut: true },
        { id: 4, name: 'Misal Pav', description: 'Spicy misal with pav and farsan', price: 70, priceUnit: 'per plate', maxQuantity: 15, remainingQuantity: 8, bookedQuantity: 7, cutoffTime: '11:00 AM', readyByTime: 'Today at 2:00 PM', soldOut: false },
        { id: 5, name: 'Puran Poli', description: 'Sweet puran poli with ghee', price: 35, priceUnit: 'per piece', maxQuantity: 40, remainingQuantity: 25, bookedQuantity: 15, cutoffTime: '10:00 PM', readyByTime: 'As per request', soldOut: false },
        { id: 6, name: 'Modak', description: 'Traditional steamed modak with coconut filling', price: 45, priceUnit: 'per piece', maxQuantity: 50, remainingQuantity: 30, bookedQuantity: 20, cutoffTime: '10:00 PM', readyByTime: 'As per request', soldOut: false },
        { id: 7, name: 'Thalipeeth', description: 'Multi-grain thalipeeth with butter', price: 55, priceUnit: 'per piece', maxQuantity: 20, remainingQuantity: 12, bookedQuantity: 8, cutoffTime: '8:00 AM', readyByTime: 'Today at 11:00 AM', soldOut: false },
        { id: 8, name: 'Sabudana Khichdi', description: 'Sabudana khichdi with peanuts and lemon', price: 50, priceUnit: 'per plate', maxQuantity: 25, remainingQuantity: 18, bookedQuantity: 7, cutoffTime: '9:00 AM', readyByTime: 'Today at 12:00 PM', soldOut: false },
        { id: 9, name: 'Vada Pav', description: 'Mumbai style vada pav with chutney', price: 25, priceUnit: 'per piece', maxQuantity: 60, remainingQuantity: 35, bookedQuantity: 25, cutoffTime: '5:00 PM', readyByTime: 'Today at 8:00 PM', soldOut: false },
        { id: 10, name: 'Samosa', description: 'Crispy potato samosa with chutney', price: 20, priceUnit: 'per piece', maxQuantity: 100, remainingQuantity: 60, bookedQuantity: 40, cutoffTime: '6:00 PM', readyByTime: 'Today at 7:00 PM', soldOut: false },
        { id: 11, name: 'Pav Bhaji', description: 'Mumbai style pav bhaji with butter', price: 80, priceUnit: 'per plate', maxQuantity: 30, remainingQuantity: 0, bookedQuantity: 30, cutoffTime: '7:00 PM', readyByTime: 'Today at 9:00 PM', soldOut: true },
        { id: 12, name: 'Aloo Paratha', description: 'Stuffed aloo paratha with curd and pickle', price: 60, priceUnit: 'per piece', maxQuantity: 20, remainingQuantity: 10, bookedQuantity: 10, cutoffTime: '9:00 AM', readyByTime: 'Today at 11:30 AM', soldOut: false }
    ];
}

function getDemoTemplates() {
    return [
        { id: 1, name: 'Poha', description: 'Fresh homemade poha', price: 40, priceUnit: 'per plate', maxQuantity: 30, cutoffTime: '9:00 AM', readyByTime: 'Today at 6:00 PM' },
        { id: 2, name: 'Idli', description: 'Soft steamed idli', price: 50, priceUnit: 'per plate', maxQuantity: 25, cutoffTime: '9:00 PM', readyByTime: 'Tomorrow at 7:00 AM' },
        { id: 3, name: 'Puran Poli', description: 'Sweet puran poli with ghee', price: 35, priceUnit: 'per piece', maxQuantity: 40, cutoffTime: '10:00 PM', readyByTime: 'As per request' }
    ];
}

function getDemoKitchen() {
    return {
        id: 1,
        name: "Aarti's Kitchen",
        society: 'Sunshine Society',
        building: 'Building B',
        speciality: 'Fresh homemade breakfast and traditional Maharashtrian snacks',

// ==================== EVENT HANDLERS ====================

document.addEventListener('click', async function (e) {
    var t = e.target.closest('[data-action]');
    if (!t) return;
    var a = t.dataset.action;
    try {
        switch (a) {
            case 'go-back': history.back(); break;
            case 'noop': break;
            case 'toggle-notifs': break;
            case 'go-add': sellerNavigate('#/add'); break;
            case 'go-create': S.draftOffering = null; sellerNavigate('#/create'); break;
            case 'go-quick-post': sellerNavigate('#/quick-post'); break;
            case 'go-use-favourite': sellerNavigate('#/add'); break;
            case 'view-offering': sellerNavigate('#/order-detail/' + t.dataset.pid); break;
            case 'view-order-detail': sellerNavigate('#/order-detail/' + t.dataset.pid); break;
            case 'use-template': {
                var tid = Number(t.dataset.tid);
                var template = S.favTemplates.find(function (t) { return t.id === tid; });
                if (template) {
                    S.draftOffering = template;
                    sellerNavigate('#/create');
                }
                break;
            }
            case 'inv-inc': {
                var pidInc = Number(t.dataset.pid);
                await api('/api/seller-app/products/' + pidInc + '/inventory', { method: 'PATCH', body: { delta: 1 } });
                var elInc = $('#inv-' + pidInc);
                if (elInc) elInc.textContent = parseInt(elInc.textContent) + 1;
                toast('Quantity updated', 'success');
                break;
            }
            case 'inv-dec': {
                var pidDec = Number(t.dataset.pid);
                var curVal = parseInt($('#inv-' + pidDec)?.textContent || '0');
                if (curVal <= 0) { toast('Cannot go below 0', 'error'); break; }
                await api('/api/seller-app/products/' + pidDec + '/inventory', { method: 'PATCH', body: { delta: -1 } });
                var elDec = $('#inv-' + pidDec);
                if (elDec) elDec.textContent = parseInt(elDec.textContent) - 1;
                toast('Quantity updated', 'success');
                break;
            }
            case 'mark-soldout': {
                var pidSO = Number(t.dataset.pid);
                if (confirm('Mark this item as Sold Out?')) {
                    await api('/api/seller-app/products/' + pidSO + '/sold-out', { method: 'POST' });
                    toast('Marked as Sold Out', 'success');
                    sellerRender();
                }
                break;
            }
            case 'set-date': S.selectedDate = t.dataset.date; await sellerRender(); break;
            case 'set-sort': S.sortFilter = t.value; break;
            case 'parse-message': {
                var msg = $('#qpMessage').value;
                if (!msg.trim()) { toast('Please paste a message first', 'error'); break; }
                var result = await api('/api/seller-app/parse-message', { method: 'POST', body: { message: msg } });
                var box = $('#parseResult');
                if (box) {
                    var html = '<div class="parse-result-box"><h4>Extracted Details (Review before publishing)</h4>';
                    html += '<div class="parse-field"><span class="pf-label">Name</span><span class="pf-value ' + (result.name ? '' : 'missing') + '">' + (result.name || 'Missing') + '</span></div>';
                    html += '<div class="parse-field"><span class="pf-label">Price</span><span class="pf-value ' + (result.price ? '' : 'missing') + '">' + (result.price ? ('₹' + result.price) : 'Missing') + '</span></div>';
                    html += '<button class="btn btn-primary btn-block" style="margin-top:10px" data-action="go-create">Review and Publish</button></div>';
                    box.innerHTML = html;
                }
                break;
            }

// Form submissions
document.addEventListener('submit', async function (e) {
    var form = e.target.closest('form');
    if (!form) return;
    e.preventDefault();

    try {
        if (form.id === 'createForm') {
            var vals = formVals(form);
            if (!vals.name || !vals.price) { toast('Please fill in Item Name and Price', 'error'); return; }

            // Save as favourite if checked
            if (vals.saveAsFavourite === 'on' || vals.saveAsFavourite === true) {
                if (S.favTemplates.length >= 3) {
                    toast('Maximum 3 favourites reached. Remove one first.', 'error');
                    return;
                }
                try {
                    var favBody = {
                        name: vals.name,
                        description: vals.description || '',
                        price: Number(vals.price),
                        priceUnit: vals.priceUnit,
                        maxQuantity: vals.maxQuantity ? Number(vals.maxQuantity) : null,
                        orderWindowStart: vals.orderWindowStart,
                        orderWindowEnd: vals.orderWindowEnd,
                        availableDate: vals.availableDate
                    };
                    await api('/api/seller-app/templates', { method: 'POST', body: favBody });
                } catch (favErr) { toast('Could not save favourite: ' + favErr.message, 'error'); }
            }

            // Create the offering
            var kid = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid) {
                var k = await api('/api/seller/kitchen');
                kid = k.id;
                S.myKitchen = k;
            }
            await api('/api/seller/products?kitchenId=' + kid, { method: 'POST', body: vals });
            toast('Offering published!', 'success');
            sellerNavigate('#/home');
        } else if (form.id === 'kitchenForm') {
            var kid2 = (S.myKitchen && S.myKitchen.id) || (S.kitchen && S.kitchen.id) || null;
            if (!kid2) {
                var k2 = await api('/api/seller/kitchen');
                kid2 = k2.id;
                S.myKitchen = k2;
            }
            await api('/api/seller/kitchen/' + kid2, { method: 'PUT', body: formVals(form) });
            toast('All changes saved', 'success');
        }
    } catch (err) {
        toast(err.message, 'error');
    }
});

// ==================== BOOT ====================

window.addEventListener('hashchange', sellerRender);
window.addEventListener('DOMContentLoaded', async function () {
    if (!location.hash) location.hash = '#/home';
    // Auto-login as demo seller (Aarti) without OTP for client demo
    try {
        var me = await api('/api/seller-app/demo-login', { method: 'POST' });
        S.user = me;
    } catch (e) {
        S.user = { name: 'Aarti', role: 'SELLER' };
    }
    await sellerRender();
});

            case 'batch-republish': {
                if (S.historySelected.length === 0) { toast('Select at least one item', 'error'); break; }
                await api('/api/seller-app/batch-republish', { method: 'POST', body: { productIds: S.historySelected, availableDate: sellerDate('today') } });
                toast('Republished ' + S.historySelected.length + ' items!', 'success');
                sellerNavigate('#/home');
                break;
            }
            case 'toggle-history': {
                var pid = Number(t.dataset.pid);
                if (t.checked) S.historySelected.push(pid);
                else S.historySelected = S.historySelected.filter(function (id) { return id !== pid; });
                break;
            }
            case 'toggle-favourite': {
                var tg = $('#favToggle');
                if (tg) tg.classList.toggle('on');
                break;
            }
            case 'preview-offering': {
                var form = $('#createForm');
                if (form) {
                    var vals = formVals(form);
                    alert('Preview:\n' + JSON.stringify(vals, null, 2));
                }
                break;
            }
            case 'preview-kitchen': toast('Opening kitchen preview...', 'info'); break;
            case 'add-photo': toast('Photo upload (demo)', 'info'); break;
            case 'upload-avatar': toast('Avatar upload (demo)', 'info'); break;
            case 'set-availability': {
                $all('.radio-option').forEach(function (el) { el.classList.remove('selected'); });
                t.classList.add('selected');
                var av = $('#availDate');
                if (av) {
                    if (t.dataset.val === 'custom') { av.style.display = 'block'; }
                    else { av.style.display = 'none'; av.value = sellerDate(t.dataset.val || 'today'); }
                }
                break;
            }
        }
    } catch (err) { toast(err.message, 'error'); }
});

        description: 'We prepare fresh, homemade Maharashtrian food with love. Our specialties include traditional breakfast items, snacks, and festival specialties made with authentic recipes passed down through generations.',
        imageUrl: null
    };
}

    }

    h += '</div>';
    return h;
}

        h += '<div class="dd-stats">Paid: <strong>' + detail.paidCount + '</strong> · Pending: <strong>' + detail.pendingCount + '</strong> · Cancelled: <strong>' + detail.cancelledCount + '</strong></div>';
        h += '</div>';

        h += '<select class="sort-select" data-action="set-sort">';
        h += '<option value="all">All Orders</option>';
        h += '<option value="paid">Paid Only</option>';
        h += '<option value="pending">Pending Only</option>';
        h += '<option value="cancelled">Cancelled Only</option>';
        h += '</select>';

        if (detail.orders && detail.orders.length > 0) {
            detail.orders.forEach(function (o) {
                var cancelled = o.status === 'CANCELLED';
                h += '<div class="customer-row ' + (cancelled ? 'cancelled' : '') + '">';
                h += statusDot(o.paid, cancelled);
                h += '<div class="cr-body">';
                h += '<div class="cr-name">' + esc(o.buyerName || 'Customer') + '</div>';
                h += '<div class="cr-loc">📍 ' + esc(o.society || '') + ', ' + esc(o.flat || '') + '</div>';
                h += '</div>';
                h += '<div class="cr-qty">' + o.quantity + ' plates</div>';
                h += '</div>';
            });
        } else {
            h += emptyHtml('📋', 'No orders', 'No orders for this item on ' + S.selectedDate + '.');
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Error', 'Could not load order details.');
    }

    h += '</div>';
    return h;
}

    h += '<input type="checkbox" name="whatsappEnabled" checked>';
    h += '<span class="toggle-switch"></span>';
    h += '<span class="toggle-text">WhatsApp</span>';
    h += '</label>';
    h += '<input class="form-input" name="whatsappNumber" value="9100000001" placeholder="WhatsApp number" style="margin-top:8px">';
    h += '</div>';

    h += '<div class="form-group">';
    h += '<label class="toggle-label">';
    h += '<input type="checkbox" name="instagramEnabled">';
    h += '<span class="toggle-switch"></span>';
    h += '<span class="toggle-text">Instagram</span>';
    h += '</label>';
    h += '<input class="form-input" name="instagramHandle" placeholder="@yourhandle" style="margin-top:8px">';
    h += '</div>';

    h += '<button type="submit" class="btn btn-primary btn-block">Save Changes</button>';

    h += '</form>';
    h += '</div>';
    return h;
}

                    h += '</div></label>';
                });
                h += '</div>';
            });

            h += '<button class="btn btn-primary btn-block" data-action="batch-republish" style="margin-top:16px">Publish Selected</button>';
        }
    } catch (e) {
        h += emptyHtml('📜', 'No recent items', 'Items from the last 2 days appear here.');
    }

    h += '</div>';
    return h;
}

function formatHistoryDate(dateStr) {
    var d = new Date(dateStr + 'T00:00:00');
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var diff = Math.round((d - today) / 86400000);
    if (diff === 0) return 'Today';
    if (diff === 1) return 'Tomorrow';
    if (diff === -1) return 'Yesterday';
    return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

    h += '</label>';
    h += '</div>';

    h += '<div class="form-actions">';
    h += '<button type="button" class="btn btn-secondary" data-action="preview-offering">Preview</button>';
    h += '<button type="submit" class="btn btn-primary">Publish Offering</button>';
    h += '</div>';

    h += '</form>';
    h += '</div>';
    return h;
}

    h += '<div class="pathway-card" data-action="go-quick-post">';
    h += '<div class="pc-icon">💬</div>';
    h += '<div class="pc-title">Quick Create / WhatsApp Paste</div>';
    h += '<div class="pc-desc">Paste a WhatsApp message to auto-fill details.</div>';
    h += '</div>';

    h += '</div>';
    return h;
}

    // Time window
    html += '<div class="oc-time-row">';
    if (p.cutoffTime) {
        html += '<span>⏰ Orders close: <span class="time-label">' + esc(p.cutoffTime) + '</span></span>';
    }
    html += '</div>';
    if (p.readyByTime) {
        html += '<div class="oc-time-row">';
        html += '<span>📦 Delivery: <span class="time-label">' + esc(p.readyByTime) + '</span></span>';
        html += '</div>';
    }

    // Price
    html += '<div class="oc-price">₹' + p.price + ' <span class="oc-unit">/' + esc(p.priceUnit || 'plate') + '</span></div>';

    // Quantity controls (only for limited items)
    if (isLimited && !isSoldOut) {
        html += '<div class="oc-controls">';
        html += '<div class="stepper">';
        html += '<button type="button" data-action="inv-dec" data-pid="' + p.id + '">−</button>';
        html += '<span class="stepper-value" id="inv-' + p.id + '">' + p.remainingQuantity + '</span>';
        html += '<button type="button" data-action="inv-inc" data-pid="' + p.id + '">+</button>';
        html += '</div>';
        html += '<button class="btn-soldout" data-action="mark-soldout" data-pid="' + p.id + '">Mark Sold Out</button>';
        html += '</div>';
    } else if (!isSoldOut) {
        html += '<div class="oc-controls">';
        html += '<span class="no-limit-badge">No Limit</span>';
        html += '<button class="btn-soldout" data-action="mark-soldout" data-pid="' + p.id + '">Mark Sold Out</button>';
        html += '</div>';
    }

    html += '</div>';
    return html;
}


