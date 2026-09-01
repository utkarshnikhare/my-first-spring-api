'use strict';
var state = { user: null, draft: null, viewMode: 'items', catFilter: null, ordersTab: 'active', sellerTab: 'offerings', otpStep: 'mobile', otpMobile: '', pendingAuthNext: '#/checkout', productIndex: {}, sellerProducts: [], sellerKitchen: null, sellerOrders: [], modalQty: 1, modalProduct: null, ratedOrders: {} };
var routes = { '#/home': homeView, '#/search': searchView, '#/my-orders': ordersView, '#/checkout': checkoutView, '#/orders': myOrdersView, '#/sell': sellerView, '#/profile': profileView };
async function navigate(hash) { if (location.hash === hash) await render(); else location.hash = hash; }
async function render() { var hash = location.hash || '#/home'; var viewFn = routes[hash] || homeView; var view = viewEl(); view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>'; try { var html = await viewFn(hash); view.innerHTML = html || ''; updateNav(hash); } catch (err) { view.innerHTML = '<div class="view-enter empty"><span class="empty-icon">⚠️</span><div class="empty-title">Error</div><p class="muted small">' + esc(err.message) + '</p></div>'; } }
function updateNav(hash) { $all('.nav-item').forEach(function (el) { el.classList.remove('active'); }); var m = { '#/home': 'home', '#/my-orders': 'order', '#/orders': 'orders', '#/sell': 'seller' }; var el = document.querySelector('[data-nav="' + (m[hash] || '') + '"]'); if (el) el.classList.add('active'); }
async function profileView() { if (!state.user || !state.user.authenticated) { state.pendingAuthNext = '#/profile'; return '<div class="view-enter"><div class="page-head"><h1>Profile</h1></div>' + authPromptHtml('Verify your mobile number to view your profile') + '</div>'; } var u = state.user; var h = '<div class="view-enter"><div class="page-head"><h1>My Profile</h1></div>'; h += '<div class="card pad"><div class="profile-header"><div class="profile-avatar">' + esc((u.name || '?').charAt(0).toUpperCase()) + '</div><div class="profile-info"><h2>' + esc(u.name || 'User') + '</h2><p class="muted small">' + esc(u.mobileNumber || '') + '</p><span class="role-badge role-' + (u.role || 'buyer').toLowerCase() + '">' + (u.role || 'BUYER') + '</span></div></div></div>'; h += '<div class="card pad"><form data-form="profile"><div class="form-group"><label class="form-label">Name</label><input class="form-input" name="name" value="' + esc(u.name || '') + '" required></div><div class="form-group"><label class="form-label">Mobile</label><input class="form-input" value="' + esc(u.mobileNumber || '') + '" disabled></div><button class="btn btn-primary btn-block" type="submit">Save Changes</button></form></div>'; if (u.role !== 'SELLER') { h += '<div class="card pad"><h3>Become a Seller</h3><p class="muted small">Share your home cooking with neighbours.</p><button class="btn btn-secondary btn-block" data-action="become-seller" style="margin-top:8px">Start Selling</button></div>'; } h += '<div class="card pad"><h3>Appearance</h3><div class="toggle-row"><span class="toggle-label">Dark Mode</span><div class="toggle-switch ' + (document.documentElement.getAttribute('data-theme') === 'dark' ? 'on' : '') + '" data-action="toggle-theme" role="switch" tabindex="0"></div></div></div>'; h += '<div class="card pad"><button class="btn btn-danger-ghost btn-block" data-action="logout">Log Out</button></div></div>'; return h; }
document.addEventListener('click', async function (e) {
    var t = e.target.closest('[data-action]'); if (!t) return;
    var a = t.getAttribute('data-action');
    switch (a) {
        case 'toggle-theme': toggleTheme(); break;
        case 'open-profile': navigate('#/profile'); break;
        case 'become-seller': await becomeSeller(); break;
        case 'request-otp': await requestOtp(); break;
        case 'verify-otp': await verifyOtp(); break;
        case 'logout': await logout(); break;
        case 'add-to-cart': await addToCart(Number(t.dataset.pid), t.dataset.name, Number(t.dataset.price)); break;
        case 'remove-item': await removeFromCart(Number(t.dataset.pid)); break;
        case 'place-order': await placeOrder(); break;
        case 'rate-order': var r = prompt('Rate this order (1-5):'); if (r) await rateOrder(Number(t.dataset.oid), Number(r)); break;
        case 'update-order-status': await updateOrderStatus(Number(t.dataset.oid), t.dataset.next); break;
        case 'add-product': var n = prompt('Product name:'); var p = prompt('Price:'); if (n && p) await addProduct({ name: n, price: Number(p) }); break;
        case 'edit-product': var nn = prompt('New name:'); var pp = prompt('New price:'); if (nn && pp) await updateProduct(Number(t.dataset.pid), { name: nn, price: Number(pp) }); break;
        case 'delete-product': await deleteProduct(Number(t.dataset.pid)); break;
    }
});
window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', async function () { initTheme(); await loadUser(); await render(); });
async function requestOtp() {
    var mobile = $('#otpMobile') ? $('#otpMobile').value.trim() : '';
    if (!mobile || mobile.length < 10) { toast('Enter a valid 10-digit mobile number', 'error'); return; }
    var btn = document.getElementById('otpSendBtn') || document.querySelector('[data-action="request-otp"]');
    if (btn) { btn.disabled = true; btn.innerHTML = '<span class="btn-spinner"></span> Sending...'; }
    try { await api('/api/auth/otp/request', { method: 'POST', body: { mobileNumber: mobile } }); state.otpMobile = mobile; state.otpStep = 'otp'; toast('OTP sent! Use any 4-digit code for demo.', 'success'); render(); }
    catch (err) { toast('Failed: ' + err.message, 'error'); }
    finally { if (btn) { btn.disabled = false; btn.textContent = 'Send OTP'; } }
}
async function verifyOtp() { var otp = $('#otpCode') ? $('#otpCode').value.trim() : ''; if (!otp || otp.length < 4) { toast('Enter the 4-digit OTP', 'error'); return; } try { var result = await api('/api/auth/otp/verify', { method: 'POST', body: { mobileNumber: state.otpMobile, otp: otp } }); state.user = result.user || result; state.otpStep = 'mobile'; toast('Welcome back!', 'success'); navigate(state.pendingAuthNext || '#/home'); } catch (err) { toast('Failed: ' + err.message, 'error'); } }
async function loadUser() { try { var me = await api('/api/auth/me'); state.user = me; if (me.role === 'SELLER') { var n = document.getElementById('navSeller'); if (n) n.hidden = false; } } catch (err) { state.user = null; } }
async function logout() { try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) {} state.user = null; toast('Logged out', 'info'); navigate('#/home'); }