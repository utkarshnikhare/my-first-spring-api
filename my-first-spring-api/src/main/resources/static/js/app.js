// ==================== App shell: router, nav & global events ====================

var state = {
    user: null,
    viewMode: 'items',        // Screen 2 [By Items] / [By Kitchens]
    catMode: 'items',         // Screen 2A toggle
    kitchenTab: 'LIVE_NOW',   // Screen 3 tabs
    favTab: 'kitchens',       // Screen 8 favourites tabs
    ordersTab: 'orders',      // Screen 8 orders/enquiries tabs
    ordersFilter: 'all',      // Screen 8 order filter: all/active/completed/cancelled
    payMethod: 'upi',         // Screen 6 demo payment method: upi/card/cod
    placingOrder: false,      // idempotency guard for payment confirmation
    lastOrder: null,          // placed order for the confirmation screen
    authMobile: '',
    pendingAuthAction: null,
    pendingCheckout: null
};

var routes = {
    '#/home': homeView,
    '#/food': foodHubView,
    '#/kitchens': kitchensView,
    '#/summary': orderSummaryView,
    '#/confirm': confirmOrderView,
    '#/payment': paymentView,
    '#/payment-success': paymentSuccessView,
    '#/favourites': favouritesView,
    '#/orders': ordersView,
    '#/profile': profileView
};

function resolveRoute(hash) {
    if (routes[hash]) return { fn: routes[hash], arg: hash };
    if (hash.startsWith('#/category/')) return { fn: categoryView, arg: hash };
    if (hash.startsWith('#/kitchen/')) return { fn: kitchenPageView, arg: hash };
    if (hash.startsWith('#/order/')) return { fn: orderDetailView, arg: hash };
    if (hash.startsWith('#/search/')) return { fn: comparisonView, arg: hash };
    return { fn: homeView, arg: '#/home' };
}

async function render() {
    var hash = location.hash || '#/home';
    var route = resolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    closeSheet();
    try {
        var html = await route.fn(route.arg);
        view.innerHTML = html || '';
        updateNav(hash);
        updateCartBar();
        if (view.querySelector('.sticky-footer-bar')) view.classList.add('has-sticky-footer');
        else view.classList.remove('has-sticky-footer');
        window.scrollTo(0, 0);
    } catch (err) {
        view.innerHTML = '<div class="view-enter">' + emptyHtml('⚠️', 'Something went wrong', err.message) + '</div>';
    }
}

function updateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = null;
    if (hash === '#/home') key = 'home';
    else if (hash === '#/favourites') key = 'favourites';
    else if (hash === '#/orders') key = 'orders';
    else if (hash === '#/profile') key = 'profile';
    var el = document.querySelector('[data-nav="' + (key || '') + '"]');
    if (el) el.classList.add('active');
}

function navigate(hash) {
    if (location.hash === hash) render();
    else location.hash = hash;
}

// ==================== Global click delegation ====================

document.addEventListener('click', async function (e) {
    var t = e.target.closest('[data-action]');
    if (!t) return;
    var a = t.dataset.action;
    try {
        switch (a) {
            case 'go-back': history.back(); break;
            case 'noop': break;
            case 'toggle-notifs': {
                var panel = $('#notifPanel');
                if (panel) panel.hidden = !panel.hidden;
                break;
            }
            case 'toggle-theme': toggleTheme(); break;
            case 'set-mode': state.viewMode = t.dataset.mode; await render(); break;
            case 'set-cat-mode': state.catMode = t.dataset.mode; await render(); break;
            case 'switch-cat': navigate('#/category/' + t.dataset.cat); break;
            case 'set-kitchen-tab': state.kitchenTab = t.dataset.tab; await render(); break;
            case 'set-fav-tab': state.favTab = t.dataset.tab; await render(); break;
            case 'set-orders-tab': state.ordersTab = t.dataset.tab; await render(); break;
            case 'set-orders-filter': state.ordersFilter = t.dataset.filter; await render(); break;
            case 'open-login': openAuthModal(); break;
            case 'read-more': {
                var full = decodeURIComponent(t.dataset.full || '');
                var parent = t.closest('.about-text') || t.closest('.oc-desc');
                if (parent) parent.textContent = full;
                break;
            }
            case 'share-kitchen': {
                var url = location.href;
                if (navigator.share) { try { await navigator.share({ title: 'SocioMart Kitchen', url: url }); } catch (e2) {} }
                else { try { await navigator.clipboard.writeText(url); toast('Link copied', 'success'); } catch (e3) {} }
                break;
            }
            case 'open-order-sheet': openOrderSheet(t.dataset.product, t.dataset.kitchen); break;
            case 'sheet-qty': sheetQty(Number(t.dataset.dir)); break;
            case 'set-sheet-date': sheet.date = t.dataset.date; highlightSheetSelection(); break;
            case 'set-sheet-slot': sheet.slot = t.dataset.slot; highlightSheetSelection(); break;
            case 'sheet-add': sheetAdd(); break;
            case 'open-enquiry': openEnquirySheet(t.dataset.kid, t.dataset.kname); break;
            case 'cart-qty': await cartQty(Number(t.dataset.idx), Number(t.dataset.dir)); break;
            case 'cart-remove': await cartRemove(Number(t.dataset.idx)); break;
            case 'go-checkout': await goCheckout(); break;
            case 'go-payment': navigate('#/payment'); break;
            case 'select-pay-method': state.payMethod = t.dataset.method; await render(); break;
            case 'confirm-payment': await confirmPayment(t); break;
            case 'logout': {
                try { await api('/api/auth/logout', { method: 'POST' }); } catch (e5) {}
                state.user = null;
                toast('Logged out', 'info');
                navigate('#/home');
                if (location.hash === '#/home') render();
                break;
            }
            case 'toggle-fav-kitchen': await toggleFavourite('kitchen', Number(t.dataset.kid), t); break;
            case 'toggle-fav-product': await toggleFavourite('product', Number(t.dataset.pid), t); break;
        }
    } catch (err) {
        toast(err.message, 'error');
    }
});

// ==================== Favourites (identity-bound, Spec 1.1) ====================

async function toggleFavourite(type, id, btnEl) {
    var label = type === 'kitchen' ? 'kitchen' : 'dish';
    var doToggle = async function () {
        try {
            var resp = await api('/api/favourites/' + type + '/' + id + '/toggle', { method: 'POST' });
            if (btnEl) {
                btnEl.classList.toggle('faved', resp.favourited);
                btnEl.textContent = resp.favourited ? '❤️' : '🤍';
            }
            toast(resp.favourited ? ('Saved ' + label + ' to favourites') : ('Removed from favourites'), 'success');
        } catch (err) {
            if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
        }
    };
    // Deferred login: favourites require identity — open login gate when needed.
    requireAuth(doToggle);
}

// ==================== Form delegation ====================

document.addEventListener('submit', async function (e) {
    var form = e.target.closest('form[data-form]');
    if (!form) return;
    e.preventDefault();
    var kind = form.dataset.form;
    try {
        if (kind === 'search') {
            var q = form.querySelector('[name="q"]').value.trim();
            if (q) navigate('#/search/' + encodeURIComponent(q));
        } else if (kind === 'enquiry') {
            await submitEnquiry(form);
        } else if (kind === 'profile-login') {
            var mobile = form.querySelector('[name="mobileNumber"]').value.trim();
            if (!/^\d{10}$/.test(mobile)) { toast('Enter a valid 10-digit mobile number', 'error'); return; }
            openAuthModal();
            var mob = $('#authMobile');
            if (mob) mob.value = mobile;
        } else if (kind === 'profile-edit') {
            var vals = formVals(form);
            await api('/api/buyer/profile', { method: 'PUT', body: vals });
            state.user = Object.assign({}, state.user, vals);
            toast('Profile saved', 'success');
            await render();
        }
    } catch (err) {
        if (err instanceof ApiError && err.status === 401) requireAuth(function () { render(); });
        else toast(err.message, 'error');
    }
});

// ==================== Boot ====================

window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', async function () {
    initTheme();
    try {
        var me = await api('/api/auth/me');
        state.user = (me && me.authenticated) ? me : null;
    } catch (e) { state.user = null; }
    if (!location.hash) location.hash = '#/home';
    await render();
});
