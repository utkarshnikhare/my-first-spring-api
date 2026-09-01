/**
 * SocioMart - Buyer Module
 * Handles: Home view, Search, Catalog browsing, Cart, Checkout, Orders
 */

// ==================== Home View ====================
async function homeView() {
    var data = await api('/api/marketplace');
    if (!data) return emptyBlock('📭', 'No kitchens available', 'Check back later for fresh home-cooked meals.');

    var h = '<div class="view-enter"><div class="page-head"><h1>Discover Food</h1>';
    h += '<p class="muted small">Fresh home-cooked meals from your neighborhood</p></div>';
    h += categoryBarHtml();
    h += '<div class="item-grid">';

    var items = data.featuredItems || data.items || [];
    items.forEach(function (item) {
        h += foodCardHtml(item);
    });
    h += '</div></div>';
    return h;
}

function categoryBarHtml() {
    var cats = ['all', 'breakfast', 'lunch', 'dinner', 'snacks'];
    var h = '<div class="category-bar">';
    cats.forEach(function (c) {
        h += '<button class="category-pill" data-cat="' + c + '">' + c.charAt(0).toUpperCase() + c.slice(1) + '</button>';
    });
    h += '</div>';
    return h;
}

function foodCardHtml(item) {
    var inCart = state.draft && state.draft.items && state.draft.items.find(function (i) { return i.productId === item.id; });
    var qty = inCart ? inCart.quantity : 0;
    return '<div class="food-card" data-pid="' + item.id + '">' +
        '<div class="food-emoji">' + emojiFor(item.name) + '</div>' +
        '<div class="food-name">' + esc(item.name) + '</div>' +
        '<div class="food-price">' + money(item.price) + '</div>' +
        '<button class="btn btn-sm ' + (inCart ? 'btn-success' : 'btn-primary') + '" data-action="add-to-cart" data-pid="' + item.id + '" data-name="' + esc(item.name) + '" data-price="' + item.price + '">' +
        (inCart ? '✓ ' + qty + ' in cart' : 'Add to Order') + '</button>' +
        '</div>';
}

function emptyBlock(icon, title, desc, action) {
    return '<div class="view-enter empty">' +
        '<span class="empty-icon">' + icon + '</span>' +
        '<div class="empty-title">' + esc(title) + '</div>' +
        '<p class="muted small">' + esc(desc) + '</p>' +
        (action || '') + '</div>';
}

// ==================== Search ====================
async function searchView(query) {
    var data = await api('/api/search?q=' + encodeURIComponent(query));
    var h = '<div class="view-enter"><div class="page-head"><h1>Search Results</h1>';
    h += '<p class="muted small">Results for "' + esc(query) + '"</p></div>';

    var items = data.items || data.products || [];
    if (items.length === 0) {
        h += emptyBlock('🔍', 'No results found', 'Try a different search term');
    } else {
        h += '<div class="item-grid">';
        items.forEach(function (item) { h += foodCardHtml(item); });
        h += '</div>';
    }
    h += '</div>';
    return h;
}

// ==================== Cart / Orders View ====================
async function ordersView() {
    var draft = await api('/api/buyer/orders/draft');
    state.draft = draft;

    var h = '<div class="view-enter"><div class="page-head"><h1>My Order</h1></div>';
    var items = draft.items || [];

    if (items.length === 0) {
        h += emptyBlock('🧺', 'Your order is empty', 'Browse kitchens and add items to your order');
    } else {
        h += '<div class="order-list">';
        items.forEach(function (item) {
            h += '<div class="order-item">' +
                '<span class="oi-emoji">' + emojiFor(item.productName) + '</span>' +
                '<div class="oi-details"><div class="oi-name">' + esc(item.productName) + '</div>' +
                '<div class="oi-meta">Qty: ' + item.quantity + ' × ' + money(item.price) + '</div></div>' +
                '<button class="btn btn-danger-ghost btn-sm" data-action="remove-item" data-pid="' + item.productId + '">✕</button>' +
                '</div>';
        });
        h += '</div>';
        h += '<div class="card pad">' +
            '<div class="total-row"><span>Total</span><strong>' + money(draft.totalAmount) + '</strong></div>' +
            '<a class="btn btn-primary btn-block" href="#/checkout">Proceed to Checkout</a></div>';
    }
    h += '</div>';
    return h;
}
// ==================== Checkout View ====================
async function checkoutView() {
    if (!state.user || !state.user.authenticated) {
        state.pendingAuthNext = '#/checkout';
        return '<div class="view-enter"><div class="page-head"><h1>Checkout</h1></div>' +
            authPromptHtml('Verify your mobile number to place your order') + '</div>';
    }

    var draft = await api('/api/buyer/orders/draft');
    if (!draft || !draft.items || draft.items.length === 0) {
        return emptyBlock('🧺', 'Nothing to checkout', 'Add items to your order first', '<a class="btn btn-primary" href="#/home">Browse Food</a>');
    }

    var h = '<div class="view-enter"><div class="page-head"><h1>Checkout</h1></div>';
    h += '<div class="card pad"><h3>Order Summary</h3>';
    draft.items.forEach(function (item) {
        h += '<div class="summary-row"><span>' + esc(item.productName) + ' × ' + item.quantity + '</span><span>' + money(item.price * item.quantity) + '</span></div>';
    });
    h += '<div class="total-row"><span>Total</span><strong>' + money(draft.totalAmount) + '</strong></div></div>';
    h += '<button class="btn btn-primary btn-block" data-action="place-order">Place Order</button>';
    h += '</div>';
    return h;
}

// ==================== My Orders View ====================
async function myOrdersView() {
    if (!state.user || !state.user.authenticated) {
        state.pendingAuthNext = '#/orders';
        return '<div class="view-enter"><div class="page-head"><h1>My Orders</h1></div>' +
            authPromptHtml('Verify your mobile number to view your orders') + '</div>';
    }

    var orders = await api('/api/buyer/orders/my');
    var h = '<div class="view-enter"><div class="page-head"><h1>My Orders</h1></div>';

    if (!orders || orders.length === 0) {
        h += emptyBlock('📋', 'No orders yet', 'Your order history will appear here');
    } else {
        h += '<div class="order-list">';
        orders.forEach(function (order) {
            h += orderCardHtml(order);
        });
        h += '</div>';
    }
    h += '</div>';
    return h;
}

function orderCardHtml(order) {
    var statusClass = order.status ? order.status.toLowerCase() : 'ordered';
    return '<div class="order-card">' +
        '<div class="order-header"><span class="order-id">#' + order.id + '</span>' +
        '<span class="order-status status-' + statusClass + '">' + (STATUS_LABEL[order.status] || order.status) + '</span></div>' +
        '<div class="order-items">' + (order.items || []).map(function (i) { return esc(i.productName) + ' × ' + i.quantity; }).join(', ') + '</div>' +
        '<div class="order-footer"><span class="order-total">' + money(order.totalAmount) + '</span>' +
        (order.status === 'DELIVERED' ? '<button class="btn btn-sm btn-primary" data-action="rate-order" data-oid="' + order.id + '">Rate</button>' : '') +
        '</div></div>';
}

// ==================== Auth Prompt ====================
function authPromptHtml(message) {
    return '<div class="card pad auth-prompt">' +
        '<h3>Verify Your Mobile</h3>' +
        '<p class="muted small">' + esc(message || 'Enter your mobile number to continue') + '</p>' +
        '<div class="form-group"><input class="form-input" id="otpMobile" type="tel" placeholder="Mobile number" maxlength="10"></div>' +
        '<button class="btn btn-primary btn-block" data-action="request-otp">Send OTP</button></div>';
}

// ==================== Buyer Actions ====================
async function addToCart(productId, name, price) {
    var btn = document.querySelector('[data-action="add-to-cart"][data-pid="' + productId + '"]');
    if (btn) { btn.disabled = true; btn.textContent = 'Adding...'; }
    try {
        var draft = state.draft;
        var items = draft && draft.items ? draft.items.slice() : [];
        var existing = items.find(function (i) { return i.productId === productId; });
        if (existing) { existing.quantity = (existing.quantity || 1) + 1; }
        else { items.push({ productId: productId, quantity: 1, productName: name, price: price }); }
        await api('/api/buyer/orders/draft?kitchenId=1', { method: 'POST', body: items });
        state.draft = { items: items };
        toast('Added to order', 'success');
        navigate('#/my-orders');
    } catch (err) {
        toast('Failed to add item: ' + err.message, 'error');
    } finally {
        if (btn) { btn.disabled = false; }
    }
}

async function removeFromCart(productId) {
    if (!confirm('Remove this item from your order?')) return;
    var btn = document.querySelector('[data-action="remove-item"][data-pid="' + productId + '"]');
    if (btn) { btn.disabled = true; btn.textContent = '...'; }
    try {
        var draft = state.draft;
        if (!draft) return;
        var items = (draft.items || []).filter(function (i) { return i.productId !== productId; });
        await api('/api/buyer/orders/draft?kitchenId=1', { method: 'POST', body: items });
        state.draft = { items: items };
        toast('Item removed', 'success');
        navigate('#/my-orders');
    } catch (err) {
        toast('Failed to remove item: ' + err.message, 'error');
    } finally {
        if (btn) { btn.disabled = false; }
    }
}

async function placeOrder() {
    try {
        await api('/api/buyer/orders/place', { method: 'POST', body: {} });
        toast('Order placed successfully!', 'success');
        navigate('#/orders');
    } catch (err) {
        toast('Failed to place order: ' + err.message, 'error');
    }
}

async function rateOrder(orderId, rating) {
    try {
        await api('/api/buyer/orders/' + orderId + '/rate?rating=' + rating, { method: 'POST' });
        toast('Thank you for rating!', 'success');
        navigate('#/orders');
    } catch (err) {
        toast('Failed to rate: ' + err.message, 'error');
    }
}