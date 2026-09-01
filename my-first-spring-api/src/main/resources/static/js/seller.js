/**
 * SocioMart - Seller Module
 * Handles: Kitchen dashboard, Menu management, Order management
 */

// ==================== Seller Dashboard View ====================
async function sellerView() {
    if (!state.user || !state.user.authenticated) {
        state.pendingAuthNext = '#/sell';
        return '<div class="view-enter"><div class="page-head"><h1>Seller Dashboard</h1></div>' +
            authPromptHtml('Verify your mobile number to access the seller dashboard') + '</div>';
    }
    if (state.user.role !== 'SELLER') {
        return '<div class="view-enter">' + emptyBlock('🏡', 'You are not a seller yet',
            'Set up your kitchen in under a minute and start selling to neighbours',
            '<button class="btn btn-primary" data-action="become-seller" style="margin-top:12px">Create My Kitchen</button>') + '</div>';
    }

    var p = await api('/api/seller/products');
    var k = await api('/api/seller/kitchen');
    var orders = await api('/api/seller/orders');
    state.sellerProducts = p || [];
    state.sellerKitchen = k || null;
    state.sellerOrders = orders || [];

    var h = '<div class="view-enter"><div class="page-head"><h1>Seller Dashboard</h1>';
    h += '<p class="muted small">Manage your kitchen, menu, and orders</p></div>';

    if (state.user.sellerApprovalStatus && state.user.sellerApprovalStatus !== 'APPROVED') {
        var statusText = state.user.sellerApprovalStatus === 'PENDING' ? 'is pending admin approval'
            : state.user.sellerApprovalStatus === 'SUSPENDED' ? 'has been suspended by the platform' : state.user.sellerApprovalStatus;
        h += '<div class="banner banner-warn"><span>⚠️ Your kitchen ' + esc(statusText) + '</span></div>';
    }

    if (k) {
        h += '<div class="card kitchen-card">' +
            '<div class="kitchen-banner"><h2>' + esc(k.name || 'My Kitchen') + '</h2>' +
            '<p class="muted small">' + esc(k.description || 'No description') + '</p></div></div>';
    }

    h += '<div class="tab-bar">' +
        '<button class="tab-btn active" data-tab="offerings">Menu</button>' +
        '<button class="tab-btn" data-tab="orders">Orders</button>' +
        '</div>';

    h += '<div id="tab-offerings" class="tab-content">';
    h += '<div class="card pad"><div class="flex-between"><h3>Menu Items</h3>' +
        '<button class="btn btn-primary btn-sm" data-action="add-product">+ Add Item</button></div>';
    if (state.sellerProducts.length === 0) {
        h += '<p class="muted small">No items yet. Add your first menu item!</p>';
    } else {
        h += '<div class="product-list">';
        state.sellerProducts.forEach(function (item) {
            h += sellerProductRowHtml(item);
        });
        h += '</div>';
    }
    h += '</div></div>';

    h += '<div id="tab-orders" class="tab-content" style="display:none">';
    h += '<div class="card pad"><h3>Incoming Orders</h3>';
    if (state.sellerOrders.length === 0) {
        h += '<p class="muted small">No orders yet</p>';
    } else {
        h += '<div class="order-list">';
        state.sellerOrders.forEach(function (order) {
            h += sellerOrderRowHtml(order);
        });
        h += '</div>';
    }
    h += '</div></div>';
    h += '</div>';
    return h;
}

function sellerProductRowHtml(item) {
    return '<div class="product-row">' +
        '<span class="product-emoji">' + emojiFor(item.name) + '</span>' +
        '<div class="product-info"><div class="product-name">' + esc(item.name) + '</div>' +
        '<div class="product-price">' + money(item.price) + '</div></div>' +
        '<div class="product-actions">' +
        '<button class="btn btn-sm btn-secondary" data-action="edit-product" data-pid="' + item.id + '">Edit</button> ' +
        '<button class="btn btn-sm btn-danger-ghost" data-action="delete-product" data-pid="' + item.id + '">Delete</button>' +
        '</div></div>';
}

function sellerOrderRowHtml(order) {
    var statusClass = order.status ? order.status.toLowerCase() : 'ordered';
    var nextStatus = NEXT_STATUS[order.status];
    var nextLabel = NEXT_LABEL[nextStatus] || 'Update';
    return '<div class="order-card">' +
        '<div class="order-header"><span class="order-id">#' + order.id + '</span>' +
        '<span class="order-status status-' + statusClass + '">' + (STATUS_LABEL[order.status] || order.status) + '</span></div>' +
        '<div class="order-items">' + (order.items || []).map(function (i) { return esc(i.productName) + ' × ' + i.quantity; }).join(', ') + '</div>' +
        '<div class="order-footer"><span class="order-total">' + money(order.totalAmount) + '</span>' +
        (nextStatus ? '<button class="btn btn-sm btn-primary" data-action="update-order-status" data-oid="' + order.id + '" data-next="' + nextStatus + '">' + nextLabel + '</button>' : '') +
        '</div></div>';
}
// ==================== Seller Actions ====================
async function becomeSeller() {
    try {
        await api('/api/auth/become-seller', { method: 'POST' });
        toast('You are now a seller!', 'success');
        await loadUser();
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}

async function createKitchen(formData) {
    try {
        await api('/api/seller/kitchen', { method: 'POST', body: formData });
        toast('Kitchen created!', 'success');
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}

async function addProduct(formData) {
    try {
        var kitchenId = state.sellerKitchen ? state.sellerKitchen.id : 1;
        await api('/api/seller/products?kitchenId=' + kitchenId, { method: 'POST', body: formData });
        toast('Product added!', 'success');
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}

async function updateProduct(productId, formData) {
    try {
        await api('/api/seller/products/' + productId, { method: 'PUT', body: formData });
        toast('Product updated!', 'success');
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}

async function deleteProduct(productId) {
    if (!confirm('Delete this product?')) return;
    try {
        await api('/api/seller/products/' + productId, { method: 'DELETE' });
        toast('Product deleted', 'success');
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}

async function updateOrderStatus(orderId, nextStatus) {
    try {
        await api('/api/seller/orders/' + orderId + '/status', { method: 'PATCH', body: { status: nextStatus } });
        toast('Order updated!', 'success');
        navigate('#/sell');
    } catch (err) {
        toast('Failed: ' + err.message, 'error');
    }
}