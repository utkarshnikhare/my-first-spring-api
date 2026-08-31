'use strict';

/* =====================================================================
   SocioMart SPA â€” hash router + views + actions + bootstrap
   ===================================================================== */

var state = {
  user: null,
  draft: null,
  viewMode: 'items',
  catFilter: null,
  ordersTab: 'active',
  sellerTab: 'offerings',
  otpStep: 'mobile',
  otpMobile: '',
  pendingAuthNext: '#/checkout',
  productIndex: {},
  sellerProducts: [],
  sellerKitchen: null,
  sellerOrders: [],
  modalQty: 1,
  modalProduct: null,
  ratedOrders: {}
};

var STATUS_LABEL = { PLACED: 'Placed', CONFIRMED: 'Confirmed', READY: 'Ready', DELIVERED: 'Delivered', COMPLETED: 'Completed', CANCELLED: 'Cancelled' };
var NEXT_STATUS = { PLACED: 'CONFIRMED', CONFIRMED: 'READY', READY: 'DELIVERED', DELIVERED: 'COMPLETED' };
var NEXT_LABEL = { CONFIRMED: 'Confirm Order', READY: 'Mark Ready', DELIVERED: 'Mark Delivered', COMPLETED: 'Mark Completed' };

var CAT_KEYWORDS = {
  breakfast: ['dosa', 'idli', 'poha', 'upma', 'paratha', 'omelette', 'tea', 'coffee', 'poori', 'puri', 'sandwich'],
  lunch: ['thali', 'biryani', 'rice', 'dal', 'roti', 'sabzi', 'curry', 'rajma', 'chole', 'sambar', 'paneer'],
  dinner: ['thali', 'roti', 'curry', 'dal', 'paneer', 'biryani', 'paratha', 'khichdi', 'masala'],
  snacks: ['samosa', 'pakora', 'pakoda', 'vada', 'sandwich', 'fritter', 'jamun', 'sweet', 'kachori', 'bhel']
};

/* ------------------------- helpers ------------------------- */
function $(sel, root) { return (root || document).querySelector(sel); }
function $all(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
function viewEl() { return $('#view'); }

function esc(v) {
  return String(v == null ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function money(n) {
  var num = Number(n == null ? 0 : n);
  return '\u20B9' + (Number.isInteger(num) ? String(num) : num.toFixed(2));
}

function emojiFor(name) {
  var n = (name || '').toLowerCase();
  if (n.indexOf('dosa') >= 0 || n.indexOf('idli') >= 0 || n.indexOf('rice') >= 0 || n.indexOf('bir') >= 0) return '\uD83C\uDF5A';
  if (n.indexOf('paneer') >= 0 || n.indexOf('dal') >= 0 || n.indexOf('masala') >= 0 || n.indexOf('curry') >= 0) return '\uD83C\uDF5B';
  if (n.indexOf('jamun') >= 0 || n.indexOf('sweet') >= 0 || n.indexOf('cake') >= 0) return '\uD83C\uDF6E';
  if (n.indexOf('coffee') >= 0 || n.indexOf('tea') >= 0) return '\u2615';
  if (n.indexOf('thali') >= 0 || n.indexOf('meal') >= 0) return '\uD83C\uDF71';
  if (n.indexOf('samosa') >= 0 || n.indexOf('pak') >= 0 || n.indexOf('vada') >= 0) return '\uD83E\uDD5F';
  return '\uD83C\uDF72';
}

function formVals(form) {
  var out = {};
  $all('input, textarea, select', form).forEach(function (el) {
    if (!el.name) return;
    if (el.type === 'checkbox') out[el.name] = el.checked;
    else out[el.name] = el.value.trim();
  });
  return out;
}
/* ------------------------- api ------------------------- */
async function api(path, opts) {
  opts = opts || {};
  path = path.replace(/\/+$/, '');
  if (!path) path = '/';
  var init = { method: opts.method || 'GET', credentials: 'same-origin', headers: {} };
  if (opts.body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(opts.body);
  }
  var res, text, data = null;
  try { res = await fetch(path, init); }
  catch (e) { throw new Error('Cannot reach the server. Please check your connection.'); }
  text = await res.text();
  if (text) { try { data = JSON.parse(text); } catch (x) { /* non-JSON */ } }
  if (!res.ok) {
    var msg = 'Request failed (' + res.status + ')';
    if (data && typeof data === 'object') {
      if (data.message) msg = data.message;
      else if (data.error) msg = data.error;
      else {
        var parts = Object.keys(data).map(function (k) { return k + ': ' + data[k]; });
        if (parts.length) msg = parts.join(' \u2022 ');
      }
    }
    var err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

/* ------------------------- toast / modal ------------------------- */
function toast(msg, type) {
  var root = $('#toastRoot');
  var el = document.createElement('div');
  el.className = 'toast ' + (type || 'info');
  el.textContent = msg;
  root.appendChild(el);
  setTimeout(function () {
    el.style.opacity = '0';
    el.style.transition = 'opacity .25s';
    setTimeout(function () { el.remove(); }, 260);
  }, 3200);
}

function openModal(html) {
  var r = $('#modalRoot');
  r.innerHTML = '<div class="modal">' + html + '</div>';
  r.hidden = false;
}
function closeModal() {
  var r = $('#modalRoot');
  r.innerHTML = '';
  r.hidden = true;
}

/* ------------------------- theme / chrome ------------------------- */
function initTheme() {
  var s = localStorage.getItem('sociomart-theme');
  if (s) document.documentElement.setAttribute('data-theme', s);
}
function toggleTheme() {
  var cur = document.documentElement.getAttribute('data-theme');
  var next = cur === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  localStorage.setItem('sociomart-theme', next);
}

function setActiveNav(route) {
  $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
  var nav = $('.nav-item[data-nav="' + route + '"]');
  if (nav) nav.classList.add('active');
}

function updateCartBadge() {
  var b = $('#cartBadge');
  var bar = $('#viewOrderBar');
  var barCount = $('#vobCount');
  var barTotal = $('#vobTotal');
  var count = 0, total = 0;
  if (state.draft && state.draft.items) {
    count = state.draft.items.reduce(function (s, i) { return s + (i.quantity || 0); }, 0);
    total = state.draft.items.reduce(function (s, i) { return s + (i.quantity || 0) * (i.price || 0); }, 0);
  }
  if (b) {
    if (count > 0) { b.textContent = count; b.hidden = false; }
    else b.hidden = true;
  }
  if (bar) {
    var route = parseHash().name;
    var hide = count <= 0 || route === 'order' || route === 'my-orders' ||
      route === 'checkout' || route === 'payment';
    bar.hidden = hide;
    if (!hide) {
      if (barCount) barCount.textContent = String(count);
      if (barTotal) barTotal.textContent = money(total);
    }
  }
}

function applyUserChrome() {
  var sn = $('#navSeller');
  var pi = $('#profileInitial');
  var u = state.user;
  if (u && u.authenticated) {
    if (pi) pi.textContent = (u.name || '?').charAt(0).toUpperCase();
    if (sn) sn.hidden = !(u.role === 'SELLER');
  } else {
    if (pi) pi.textContent = '?';
    if (sn) sn.hidden = true;
  }
}

async function refreshDraft() {
  try {
    var d = await api('/api/buyer/orders/draft');
    state.draft = d || null;
  } catch (e) { state.draft = null; }
  updateCartBadge();
}

async function applyAuth(resp) {
  state.user = resp;
  applyUserChrome();
  await refreshDraft();
}
/* ------------------------- router ------------------------- */
function parseHash() {
  var h = (location.hash || '#/home').replace(/^#\/?/, '');
  var parts = h.split('/');
  var name = parts.shift() || 'home';
  var arg = '';
  if (parts.length) {
    try { arg = decodeURIComponent(parts.join('/')); }
    catch (e) { arg = parts.join('/'); }
  }
  return { name: name, arg: arg };
}

var ROUTES = {
  home: function () { return homeView(); },
  search: function (q) { return searchView(q || ''); },
  kitchen: function (name) { return kitchenView(name); },
  order: function () { return cartView(); },
  'my-orders': function () { return cartView(); },
  checkout: function () { return checkoutView(); },
  payment: function (id) { return paymentView(id); },
  orders: function () { return ordersView(); },
  'order-detail': function (id) { return orderDetailView(id); },
  seller: function () { return sellerView(); },
  sell: function () { return sellerView(); },
  profile: function () { return profileView(); }
};

var renderToken = 0;

async function render() {
  var token = ++renderToken;
  var route = parseHash();
  var fn = ROUTES[route.name] || ROUTES.home;
  setActiveNav(route.name === 'order-detail' ? 'orders'
    : route.name === 'search' || route.name === 'kitchen' ? 'home'
    : route.name === 'my-orders' || route.name === 'checkout' || route.name === 'payment' ? 'order'
    : route.name === 'sell' ? 'seller'
    : route.name);
  viewEl().innerHTML = '<div class="page-loading"><div class="spinner" role="status" aria-label="Loading"></div></div>';
  window.scrollTo(0, 0);
  var html;
  try { html = await fn(route.arg); }
  catch (err) {
    if (token !== renderToken) return;
    html = '<div class="view-enter"><div class="empty card pad"><span class="empty-icon">\u26A0\uFE0F</span>' +
      '<h3 class="empty-title">Something went wrong</h3><p class="muted">' + esc(err.message) + '</p>' +
      '<button class="btn btn-primary" data-action="retry-render" style="margin-top:12px">Try Again</button></div></div>';
  }
  if (token !== renderToken) return;
  viewEl().innerHTML = html;
  updateCartBadge();
}

/* ------------------------- shared cards ------------------------- */
function productCard(p) {
  if (p && p.id != null) state.productIndex[p.id] = p;
  var stock = (p.remainingQuantity != null && p.remainingQuantity < 999)
    ? '<span class="badge-stock">' + p.remainingQuantity + ' left</span>' : '';
  return '<div class="food-card" data-action="pick-product" data-product-id="' + p.id + '">' +
    '<span class="food-icon">' + emojiFor(p.imageUrl || p.name) + '</span>' +
    '<h4 class="food-name">' + esc(p.name) + '</h4>' +
    '<div class="food-meta"><span>' + esc(p.kitchenName || '') + '</span><span class="price">' + money(p.price) + '</span></div>' +
    stock + '</div>';
}

function kitchenCard(k) {
  var open = k.availableToday !== false;
  return '<a class="kitchen-card" href="#/kitchen/' + encodeURIComponent(k.name || '') + '">' +
    '<div class="kitchen-card-img">' + emojiFor(k.imageUrl || k.displayName) + '</div>' +
    '<div class="kitchen-card-body"><h4>' + esc(k.displayName || k.name) + '</h4>' +
    '<p class="muted small">' + esc(k.shortDescription || k.society || 'Homemade goodness') + '</p>' +
    '<div class="kc-meta"><span class="kc-status">\u2B50 ' + (k.rating != null ? k.rating : 'New') + '</span>' +
    '<span class="badge ' + (open ? 'badge-live' : 'closed') + '">' + (open ? 'Open' : 'Closed') + '</span></div>' +
    '</div></a>';
}

function emptyBlock(icon, title, sub, ctaHtml) {
  return '<div class="empty"><span class="empty-icon">' + icon + '</span>' +
    '<h3 class="empty-title">' + esc(title) + '</h3>' +
    (sub ? '<p class="muted">' + esc(sub) + '</p>' : '') + (ctaHtml || '') + '</div>';
}

function orderCard(o) {
  var items = (o.items || []).map(function (i) { return esc(i.productName) + ' \u00D7 ' + i.quantity; }).join(', ');
  return '<div class="card pad" data-action="go-order-detail" data-order-id="' + o.id + '" style="cursor:pointer">' +
    '<div style="display:flex;justify-content:space-between;align-items:center;gap:8px">' +
    '<strong>' + esc(o.orderNumber || ('Order #' + o.id)) + '</strong>' +
    '<span class="badge badge-status-' + o.orderStatus + '">' + (STATUS_LABEL[o.orderStatus] || o.orderStatus) + '</span></div>' +
    '<div class="muted small" style="margin-top:4px">' + items + '</div>' +
    '<div style="display:flex;justify-content:space-between;margin-top:8px;align-items:center">' +
    '<span class="muted small">' + (o.paymentStatus === 'PAID' ? '\u2705 Paid' : '\u23F3 Payment pending') + '</span>' +
    '<strong class="price">' + money(o.totalAmount) + '</strong></div></div>';
}

function qrPlaceholder(text, size) {
  var cells = 21, seed = 7;
  var t = String(text || 'sociomart');
  for (var i = 0; i < t.length; i++) seed = (seed * 31 + t.charCodeAt(i)) >>> 0;
  var r = seed || 1;
  function rnd() { r = (r * 1103515245 + 12345) >>> 0; return (r >>> 16) / 65536; }
  var s = size / cells, body = '';
  for (var y = 0; y < cells; y++) {
    for (var x = 0; x < cells; x++) {
      var inFinder = (x < 8 && y < 8) || (x >= cells - 8 && y < 8) || (x < 8 && y >= cells - 8);
      if (!inFinder && rnd() > 0.52) body += '<rect x="' + (x * s) + '" y="' + (y * s) + '" width="' + s + '" height="' + s + '"/>';
    }
  }
  function finder(cx, cy) {
    return '<rect x="' + (cx * s) + '" y="' + (cy * s) + '" width="' + (7 * s) + '" height="' + (7 * s) + '" fill="currentColor"/>' +
      '<rect x="' + ((cx + 1) * s) + '" y="' + ((cy + 1) * s) + '" width="' + (5 * s) + '" height="' + (5 * s) + '" fill="var(--card, #fff)"/>' +
      '<rect x="' + ((cx + 2) * s) + '" y="' + ((cy + 2) * s) + '" width="' + (3 * s) + '" height="' + (3 * s) + '" fill="currentColor"/>';
  }
  return '<svg class="payment-qr" viewBox="0 0 ' + size + ' ' + size + '" width="' + size + '" height="' + size + '" fill="currentColor" role="img" aria-label="Payment QR placeholder">' + body + finder(0, 0) + finder(cells - 7, 0) + finder(0, cells - 7) + '</svg>';
}
/* ------------------------- buyer views ------------------------- */
async function homeView() {
  var data = await api('/api/marketplace');
  var h = '<div class="view-enter">';
  h += '<div class="hero"><h1>Homemade food from your neighbours</h1>' +
    '<p>Order fresh, home-cooked meals from trusted kitchens in your community.</p></div>';
  h += '<form class="search-bar" data-form="search">' +
    '<span class="search-icon">\uD83D\uDD0D</span>' +
    '<input type="text" name="q" placeholder="Search dishes or kitchens..." aria-label="Search"></form>';
  h += '<div class="location-row"><span class="loc-icon">\uD83D\uDCCD</span><span>Pride World City</span></div>';
  h += '<div class="category-grid">';
  ['breakfast', 'lunch', 'dinner', 'snacks'].forEach(function (c) {
    var icons = { breakfast: '\uD83C\uDF5E', lunch: '\uD83C\uDF5B', dinner: '\uD83C\uDF7D', snacks: '\uD83C\uDF6A' };
    h += '<div class="category-card ' + c + (state.catFilter === c ? ' active' : '') +
      '" data-action="filter-cat" data-cat="' + c + '"><span class="cat-icon">' + icons[c] +
      '</span>' + c.charAt(0).toUpperCase() + c.slice(1) + '</div>';
  });
  h += '</div>';
  h += '<div class="toggle-row">' +
    '<span class="toggle-label ' + (state.viewMode === 'items' ? 'active' : '') + '">By Items</span>' +
    '<div class="toggle-switch ' + (state.viewMode === 'kitchens' ? 'on' : '') +
    '" data-action="toggle-view" role="switch" aria-checked="' + (state.viewMode === 'kitchens') + '" tabindex="0"></div>' +
    '<span class="toggle-label ' + (state.viewMode === 'kitchens' ? 'active' : '') + '">By Kitchens</span></div>';
  if (state.viewMode === 'kitchens') {
    h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Kitchens near you</h2></div>';
    h += (data.kitchens && data.kitchens.length)
      ? '<div class="item-grid">' + data.kitchens.map(kitchenCard).join('') + '</div>'
      : emptyBlock('\uD83C\uDFE0', 'No kitchens yet', 'Be the first to sell in your community');
    h += '</div>';
  } else if (state.catFilter) {
    var all = [].concat(data.availableToday || [], data.popularProducts || [], data.newProducts || []);
    var words = CAT_KEYWORDS[state.catFilter] || [];
    var seen = {}, filtered = [];
    all.forEach(function (p) {
      var nm = (p.name || '').toLowerCase() + ' ' + (p.description || '').toLowerCase();
      var hit = words.some(function (w) { return nm.indexOf(w) >= 0; });
      if (hit && !seen[p.id]) { seen[p.id] = 1; filtered.push(p); }
    });
    h += '<div class="home-section"><div class="section-row"><h2 class="section-title">' +
      state.catFilter.charAt(0).toUpperCase() + state.catFilter.slice(1) + ' picks</h2>' +
      '<button class="btn btn-ghost btn-sm" data-action="clear-cat">Clear filter</button></div>';
    h += filtered.length ? '<div class="item-grid">' + filtered.map(productCard).join('') + '</div>'
      : emptyBlock('\uD83C\uDF5F', 'Nothing here yet', 'Try another category or browse all items');
    h += '</div>';
  } else {
    h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Available today</h2></div>';
    h += (data.availableToday && data.availableToday.length)
      ? '<div class="item-grid">' + data.availableToday.map(productCard).join('') + '</div>'
      : emptyBlock('\uD83C\uDF72', 'Nothing cooking right now', 'Check back soon or explore kitchens');
    h += '</div>';
    if (data.popularProducts && data.popularProducts.length) {
      h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Popular in your community</h2></div>' +
        '<div class="item-grid">' + data.popularProducts.map(productCard).join('') + '</div></div>';
    }
    if (data.newProducts && data.newProducts.length) {
      h += '<div class="home-section"><div class="section-row"><h2 class="section-title">New offerings</h2></div>' +
        '<div class="item-grid">' + data.newProducts.map(productCard).join('') + '</div></div>';
    }
  }
  h += '</div>';
  return h;
}

async function searchView(q) {
  var h = '<div class="view-enter"><div class="page-head"><h1>Search</h1>' +
    '<p class="muted">Find dishes and kitchens in your community</p></div>';
  h += '<form class="search-bar" data-form="search"><span class="search-icon">\uD83D\uDD0D</span>' +
    '<input type="text" name="q" value="' + esc(q) + '" placeholder="Search dishes or kitchens..." aria-label="Search"></form>';
  if (!q) { h += '<p class="muted center" style="margin-top:24px">Type something to start searching.</p></div>'; return h; }
  var res = await api('/api/search?q=' + encodeURIComponent(q));
  var hasProducts = res.products && res.products.length;
  var hasKitchens = res.kitchens && res.kitchens.length;
  if (!hasProducts && !hasKitchens) {
    h += emptyBlock('\uD83D\uDD0E', 'No results for "' + q + '"', 'Try a different dish or kitchen name');
  } else {
    if (hasKitchens) {
      h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Kitchens</h2></div>' +
        '<div class="item-grid">' + res.kitchens.map(kitchenCard).join('') + '</div></div>';
    }
    if (hasProducts) {
      h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Dishes</h2></div>' +
        '<div class="item-grid">' + res.products.map(productCard).join('') + '</div></div>';
    }
  }
  h += '</div>';
  return h;
}
async function kitchenView(name) {
  var data = await api('/api/kitchens/' + encodeURIComponent(name));
  var k = data.kitchen || {};
  var open = k.availableToday !== false;
  var h = '<div class="view-enter">';
  h += '<div class="kitchen-banner"><div class="kitchen-banner-avatar">' + emojiFor(k.imageUrl || k.displayName) + '</div>' +
    '<div class="kitchen-banner-info"><h1>' + esc(k.displayName || k.name) + '</h1>' +
    '<p class="muted small">' + esc([k.society, k.building].filter(Boolean).join(' \u00B7 ') || 'Your neighbourhood kitchen') + '</p>' +
    '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:6px">' +
    '<span class="kitchen-status-pill ' + (open ? '' : 'closed') + '">' +
    (open ? 'Orders Open' + (k.orderDeadline ? ' until ' + esc(k.orderDeadline) : '') : 'Orders Closed') + '</span>' +
    '<span class="muted small">\u2B50 ' + (k.rating != null ? k.rating : 'New') + '</span></div>' +
    '<div class="kitchen-social">' +
    (k.whatsappLink ? '<a class="btn btn-ghost btn-sm" href="' + esc(k.whatsappLink) + '" target="_blank" rel="noopener">WhatsApp</a>' : '') +
    (k.instagramLink ? '<a class="btn btn-ghost btn-sm" href="' + esc(k.instagramLink) + '" target="_blank" rel="noopener">Instagram</a>' : '') +
    '</div></div></div>';
  if (k.description) h += '<div class="card pad"><p class="muted">' + esc(k.description) + '</p></div>';
  h += '<div class="gallery">' + [1, 2, 3, 4].map(function () {
    return '<div class="gallery-item">' + emojiFor(k.imageUrl || k.displayName) + '</div>';
  }).join('') + '</div>';
  h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Menu</h2></div><div class="stack">';
  if (data.products && data.products.length) {
    data.products.forEach(function (p) { state.productIndex[p.id] = p; });
    h += data.products.map(function (p) {
      return '<div class="menu-item"><div class="menu-item-info"><h4>' + emojiFor(p.imageUrl || p.name) + ' ' + esc(p.name) + '</h4>' +
        '<p class="muted small">' + esc(p.description || '') + '</p>' +
        '<div style="display:flex;gap:8px;margin-top:4px;align-items:center;flex-wrap:wrap">' +
        '<span class="menu-item-price">' + money(p.price) + (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + '</span>' +
        (p.remainingQuantity != null ? '<span class="badge-stock">' + p.remainingQuantity + ' left</span>' : '') +
        '</div></div>' +
        '<div class="menu-item-actions"><button class="btn btn-primary btn-sm" data-action="pick-product" data-product-id="' + p.id + '">Order</button></div>' +
        '</div>';
    }).join('');
  } else h += emptyBlock('\uD83C\uDF72', 'No items available right now', 'Check pre-orders below or come back later');
  h += '</div></div>';
  if (data.preorderProducts && data.preorderProducts.length) {
    data.preorderProducts.forEach(function (p) { state.productIndex[p.id] = p; });
    h += '<div class="home-section"><div class="section-row"><h2 class="section-title">Coming up \u2014 Pre-orders</h2></div><div class="stack">';
    h += data.preorderProducts.map(function (p) {
      return '<div class="menu-item"><div class="menu-item-info"><h4>' + emojiFor(p.imageUrl || p.name) + ' ' + esc(p.name) + '</h4>' +
        '<p class="muted small">' + esc(p.description || '') + (p.availableDate ? ' \u00B7 Available on ' + esc(p.availableDate) : '') + '</p>' +
        '<span class="menu-item-price">' + money(p.price) + (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + '</span></div>' +
        '<div class="menu-item-actions"><button class="btn btn-secondary btn-sm" data-action="pick-product" data-product-id="' + p.id + '">Pre-order</button></div></div>';
    }).join('');
    h += '</div></div>';
  }
  h += '</div>';
  return h;
}
async function cartView() {
  var draft = state.draft;
  var h = '<div class="view-enter"><div class="page-head"><h1>My Order</h1>' +
    '<p class="muted">Review items before checkout</p></div>';
  if (!draft || !draft.items || !draft.items.length) {
    h += emptyBlock('\uD83E\uDD6D', 'Your order is empty', 'Add something delicious to get started',
      '<a class="btn btn-primary" href="#/home" style="margin-top:12px">Browse Food</a>');
    h += '</div>';
    return h;
  }
  var lines = draft.items.map(function (i) {
    if (!state.productIndex[i.productId]) {
      state.productIndex[i.productId] = { id: i.productId, name: i.productName, price: i.price };
    }
    return '<div class="order-item-row"><div><strong>' + esc(i.productName) + '</strong>' +
      '<div class="muted small">' + money(i.price) + ' each</div></div>' +
      '<div style="display:flex;align-items:center;gap:8px">' +
      '<div class="qty-counter">' +
      '<button class="btn btn-ghost btn-sm" data-action="draft-dec" data-product-id="' + i.productId + '" aria-label="Decrease">\u2212</button>' +
      '<strong>' + i.quantity + '</strong>' +
      '<button class="btn btn-ghost btn-sm" data-action="draft-inc" data-product-id="' + i.productId + '" aria-label="Increase">+</button>' +
      '</div><strong class="price">' + money(i.price * i.quantity) + '</strong></div></div>';
  }).join('');
  h += '<div class="card pad"><p class="muted small">From</p><h3>' +
    esc(draft.kitchen ? draft.kitchen.displayName : 'Kitchen') + '</h3><div class="stack">' + lines + '</div></div>';
  var subtotal = draft.items.reduce(function (s, i) { return s + i.price * i.quantity; }, 0);
  h += '<div class="card pad order-summary"><h3>Summary</h3>' +
    '<div class="sumline"><span>Items</span><span>' + draft.items.reduce(function (s, i) { return s + i.quantity; }, 0) + '</span></div>' +
    '<div class="sumline"><span>Subtotal</span><span>' + money(subtotal) + '</span></div>' +
    '<div class="sumline total"><span>Total</span><span>' + money(subtotal) + '</span></div>' +
    '<button class="btn btn-primary btn-block" data-action="go-checkout" style="margin-top:12px">Proceed to Checkout</button>' +
    '<button class="btn btn-danger-ghost btn-block" data-action="clear-draft" style="margin-top:8px">Clear Order</button>' +
    '</div></div>';
  return h;
}

function authPromptHtml(why) {
  return '<div class="view-enter"><div class="page-head"><h1>Verify your number</h1>' +
    '<p class="muted">' + esc(why || 'Verify your mobile number to continue') + '</p></div>' +
    '<div class="card pad"><form data-form="otp-request">' +
    '<div class="form-group"><label class="form-label" for="otpMobile">Mobile Number</label>' +
    '<input class="form-input" id="otpMobile" name="mobileNumber" inputmode="numeric" pattern="[6-9][0-9]{9}" ' +
    'placeholder="10-digit mobile number" maxlength="10" required></div>' +
    '<button class="btn btn-primary btn-block" type="submit">Send OTP</button></form></div></div>';
}

async function checkoutView() {
  var draft = state.draft;
  if (!draft || !draft.items || !draft.items.length) {
    return '<div class="view-enter">' + emptyBlock('\uD83E\uDD6D', 'Nothing to check out',
      'Add items to your order first',
      '<a class="btn btn-primary" href="#/home" style="margin-top:12px">Browse Food</a>') + '</div>';
  }
  if (!state.user || !state.user.authenticated) {
    state.pendingAuthNext = '#/checkout';
    return authPromptHtml('Verify your mobile number to place this order');
  }
  var u = state.user;
  var subtotal = draft.items.reduce(function (s, i) { return s + i.price * i.quantity; }, 0);
  var summary = draft.items.map(function (i) {
    return '<div class="sumline"><span>' + esc(i.productName) + ' \u00D7 ' + i.quantity + '</span><span>' +
      money(i.price * i.quantity) + '</span></div>';
  }).join('');
  var h = '<div class="view-enter"><div class="page-head"><h1>Checkout</h1>' +
    '<p class="muted">Confirm your details and place the order</p></div>';
  h += '<div class="card pad"><h3>Order Summary</h3>' + summary +
    '<div class="sumline total"><span>Total</span><span>' + money(subtotal) + '</span></div></div>';
  h += '<div class="card pad"><h3>Delivery Details</h3><form data-form="place-order">' +
    '<div class="form-group"><label class="form-label" for="poName">Your Name</label>' +
    '<input class="form-input" id="poName" name="name" value="' + esc(u.name || '') + '" placeholder="Full name" required></div>' +
    '<div class="form-group"><label class="form-label" for="poMobile">Mobile Number</label>' +
    '<input class="form-input" id="poMobile" name="mobileNumber" inputmode="numeric" value="' + esc(u.mobileNumber || '') + '" ' +
    'pattern="[6-9][0-9]{9}" maxlength="10" placeholder="10-digit mobile number" required></div>' +
    '<div class="form-group"><label class="form-label" for="poSociety">Society</label>' +
    '<input class="form-input" id="poSociety" name="society" value="Pride World City" placeholder="Society name"></div>' +
    '<div class="form-group"><label class="form-label" for="poBuilding">Building / Tower</label>' +
    '<input class="form-input" id="poBuilding" name="building" placeholder="e.g. Tower A"></div>' +
    '<div class="form-group"><label class="form-label" for="poFlat">Flat / House No.</label>' +
    '<input class="form-input" id="poFlat" name="flatHouseNumber" value="' + esc(u.flatHouseNumber || '') + '" placeholder="e.g. A-1204" required></div>' +
    '<div class="form-group"><label class="form-label" for="poNotes">Custom Instructions (optional)</label>' +
    '<textarea class="form-input" id="poNotes" name="customInstructions" rows="3" placeholder="Less spicy, no onion, deliver after 6 PM..."></textarea></div>' +
    '<button class="btn btn-primary btn-block" type="submit">Place Order \u00B7 ' + money(subtotal) + '</button>' +
    '</form></div></div>';
  return h;
}
async function paymentView(orderId) {
  var o;
  try { o = await api('/api/buyer/orders/' + orderId); }
  catch (e) {
    return '<div class="view-enter">' + emptyBlock('\uD83D\uDD0E', 'Order not found', e.message,
      '<a class="btn btn-primary" href="#/orders" style="margin-top:12px">My Orders</a>') + '</div>';
  }
  if (o.paymentStatus === 'PAID') return paymentSuccessHtml(o);
  var upiId = '';
  var kitchenName = o.kitchen ? o.kitchen.displayName : 'the kitchen';
  try {
    var kd = await api('/api/kitchens/' + encodeURIComponent(o.kitchen ? o.kitchen.name : ''));
    upiId = (kd.kitchen && kd.kitchen.upiId) || '';
  } catch (e) { upiId = ''; }
  var upiLink = upiId ? ('upi://pay?pa=' + encodeURIComponent(upiId) +
    '&pn=' + encodeURIComponent(kitchenName) + '&am=' + Number(o.totalAmount) + '&cu=INR') : '#';
  var h = '<div class="view-enter"><div class="page-head"><h1>Confirm Payment</h1>' +
    '<p class="muted">Scan the QR or pay via UPI to complete your order</p></div>';
  h += '<div class="card pad order-summary"><h3>' + esc(o.orderNumber || ('Order #' + o.id)) + '</h3>' +
    '<div class="sumline"><span>Kitchen</span><span>' + esc(kitchenName) + '</span></div>' +
    '<div class="sumline"><span>Items</span><span>' + (o.items || []).reduce(function (s, i) { return s + i.quantity; }, 0) + '</span></div>' +
    '<div class="sumline total"><span>Amount Payable</span><span>' + money(o.totalAmount) + '</span></div></div>';
  h += '<div class="card pad center"><h3>Scan to Pay</h3>' +
    '<div style="display:flex;justify-content:center;padding:12px 0;color:var(--ink,#1A252C)">' +
    qrPlaceholder(upiId || ('order-' + o.id), 168) + '</div>' +
    '<p class="muted small">Open any UPI app \u2192 Scan \u2192 Pay ' + money(o.totalAmount) + '</p>' +
    (upiId ? '<div class="upi-copy"><code id="upiText">' + esc(upiId) + '</code>' +
      '<button class="btn btn-secondary btn-sm" data-action="copy-upi" data-upi="' + esc(upiId) + '">Copy UPI ID</button></div>' +
      '<a class="btn btn-ghost btn-block" style="margin-top:10px" href="' + esc(upiLink) + '">Open UPI App</a>' : '') +
    '<button class="btn btn-success btn-block" data-action="mark-paid" data-order-id="' + o.id + '" style="margin-top:14px">I HAVE PAID</button>' +
    '</div></div>';
  return h;
}

function paymentSuccessHtml(o) {
  return '<div class="view-enter"><div class="card pad center" style="margin-top:24px">' +
    '<div style="font-size:56px">\uD83C\uDF89</div>' +
    '<h1 style="margin:8px 0">Payment Confirmed</h1>' +
    '<p class="muted">Your order <strong>' + esc(o.orderNumber || ('#' + o.id)) + '</strong> has been placed with ' +
    esc(o.kitchen ? o.kitchen.displayName : 'the kitchen') + '.</p>' +
    '<div class="sumline total" style="margin:12px 0"><span>Total Paid</span><span>' + money(o.totalAmount) + '</span></div>' +
    '<a class="btn btn-primary btn-block" href="#/orders">Track My Order</a>' +
    '<a class="btn btn-ghost btn-block" style="margin-top:8px" href="#/home">Back to Home</a>' +
    '</div></div>';
}

async function ordersView() {
  if (!state.user || !state.user.authenticated) {
    state.pendingAuthNext = '#/orders';
    return '<div class="view-enter"><div class="page-head"><h1>My Orders</h1></div>' +
      authPromptHtml('Verify your mobile number to see your orders') + '</div>';
  }
  var resp = await api('/api/buyer/orders/my');
  var all = [];
  Object.keys(resp || {}).forEach(function (k) {
    if (Array.isArray(resp[k])) all = all.concat(resp[k]);
  });
  var active = all.filter(function (o) { return o.orderStatus !== 'COMPLETED' && o.orderStatus !== 'CANCELLED'; });
  var done = all.filter(function (o) { return o.orderStatus === 'COMPLETED' || o.orderStatus === 'CANCELLED'; });
  var tab = state.ordersTab;
  var list = tab === 'active' ? active : done;
  var h = '<div class="view-enter"><div class="page-head"><h1>My Orders</h1></div>';
  h += '<div class="toggle-row">' +
    '<span class="toggle-label ' + (tab === 'active' ? 'active' : '') + '" data-action="switch-orders-tab" data-tab="active">Active (' + active.length + ')</span>' +
    '<span class="toggle-label ' + (tab === 'done' ? 'active' : '') + '" data-action="switch-orders-tab" data-tab="done">History (' + done.length + ')</span></div>';
  if (!list.length) {
    h += emptyBlock('\uD83D\uDCCB', tab === 'active' ? 'No active orders' : 'No past orders',
      tab === 'active' ? 'Hungry? Explore kitchens near you' : 'Your completed orders will appear here',
      '<a class="btn btn-primary" href="#/home" style="margin-top:12px">Browse Food</a>');
  } else {
    h += '<div class="stack-lg">' + list.map(orderCard).join('') + '</div>';
  }
  h += '</div>';
  return h;
}
async function orderDetailView(id) {
  var o = await api('/api/buyer/orders/' + id);
  var h = '<div class="view-enter"><div class="page-head"><a href="#/orders" class="muted small">&larr; My Orders</a>' +
    '<h1 style="margin-top:4px">' + esc(o.orderNumber || ('Order #' + o.id)) + '</h1></div>';
  h += '<div class="card pad order-summary"><div style="display:flex;justify-content:space-between;align-items:center">' +
    '<span class="badge badge-status-' + o.orderStatus + '">' + (STATUS_LABEL[o.orderStatus] || o.orderStatus) + '</span>' +
    '<span class="badge ' + (o.paymentStatus === 'PAID' ? 'badge-live' : 'badge-paused') + '">' +
    (o.paymentStatus === 'PAID' ? 'Paid' : 'Payment Pending') + '</span></div>' +
    '<div class="stack" style="margin-top:12px">' +
    (o.items || []).map(function (i) {
      return '<div class="order-item-row"><div><strong>' + esc(i.productName) + '</strong>' +
        '<div class="muted small">' + money(i.price) + ' \u00D7 ' + i.quantity + '</div></div>' +
        '<strong class="price">' + money(i.price * i.quantity) + '</strong></div>';
    }).join('') + '</div>' +
    '<div class="sumline total"><span>Total</span><span>' + money(o.totalAmount) + '</span></div></div>';
  h += '<div class="card pad"><h3>Delivery</h3>' +
    '<div class="sumline"><span>Name</span><span>' + esc(o.buyer ? o.buyer.name : '-') + '</span></div>' +
    '<div class="sumline"><span>Mobile</span><span>' + esc(o.buyer ? o.buyer.mobileNumber : '-') + '</span></div>' +
    '<div class="sumline"><span>Flat / House No.</span><span>' + esc(o.buyer && o.buyer.flatHouseNumber ? o.buyer.flatHouseNumber : '-') + '</span></div>' +
    (o.customInstructions ? '<div class="sumline"><span>Notes</span><span style="text-align:right;max-width:60%">' + esc(o.customInstructions) + '</span></div>' : '') +
    '</div>';
  if (o.paymentStatus !== 'PAID' && o.orderStatus !== 'CANCELLED') {
    h += '<button class="btn btn-success btn-block" data-action="go-pay" data-order-id="' + o.id + '" style="margin-top:12px">Complete Payment</button>';
  }
  if (o.orderStatus === 'DELIVERED' && o.paymentStatus === 'PAID' && !state.ratedOrders[o.id]) {
    h += '<button class="btn btn-secondary btn-block" data-action="rate-order" data-order-id="' + o.id + '" style="margin-top:8px">\u2B50 Rate this order</button>';
  }
  if (o.orderStatus === 'CANCELLED') {
    h += '<button class="btn btn-primary btn-block" data-action="reorder" data-order-id="' + o.id + '" style="margin-top:12px">Reorder</button>';
  }
  h += '</div>';
  return h;
}

async function profileView() {
  if (!state.user || !state.user.authenticated) {
    state.pendingAuthNext = '#/profile';
    return '<div class="view-enter"><div class="page-head"><h1>Profile</h1></div>' +
      authPromptHtml('Verify your mobile number to manage your profile') + '</div>';
  }
  var u = state.user;
  try { u = await api('/api/buyer/profile'); } catch (e) { /* fall back to auth payload */ }
  var h = '<div class="view-enter"><div class="page-head"><h1>Profile</h1>' +
    '<p class="muted">Manage your details and preferences</p></div>';
  h += '<div class="card pad"><form data-form="save-profile">' +
    '<div class="form-group"><label class="form-label" for="pfName">Name</label>' +
    '<input class="form-input" id="pfName" name="name" value="' + esc(u.name || '') + '" placeholder="Your name" required></div>' +
    '<div class="form-group"><label class="form-label" for="pfMobile">Mobile</label>' +
    '<input class="form-input" id="pfMobile" value="' + esc(u.mobileNumber || '') + '" disabled></div>' +
    '<div class="form-group"><label class="form-label" for="pfSociety">Society</label>' +
    '<input class="form-input" id="pfSociety" name="society" value="' + esc(u.society || 'Pride World City') + '" placeholder="Society name"></div>' +
    '<div class="form-group"><label class="form-label" for="pfBuilding">Building / Tower</label>' +
    '<input class="form-input" id="pfBuilding" name="building" value="' + esc(u.building || '') + '" placeholder="e.g. Tower A"></div>' +
    '<div class="form-group"><label class="form-label" for="pfFlat">Flat / House No.</label>' +
    '<input class="form-input" id="pfFlat" name="flatHouseNumber" value="' + esc(u.flatHouseNumber || '') + '" placeholder="e.g. A-1204"></div>' +
    '<button class="btn btn-primary btn-block" type="submit">Save Changes</button></form></div>';
  if (u.role !== 'SELLER') {
    h += '<div class="card pad"><h3>Become a Seller</h3>' +
      '<p class="muted small">Share your home cooking with neighbours and earn from your kitchen.</p>' +
      '<button class="btn btn-secondary btn-block" data-action="become-seller" style="margin-top:8px">Start Selling</button></div>';
  } else {
    h += '<div class="card pad"><h3>Seller Mode</h3><p class="muted small">You are a verified seller.</p>' +
      '<a class="btn btn-primary btn-block" href="#/sell">Open Seller Dashboard</a></div>';
  }
  h += '<div class="card pad"><h3>Appearance</h3>' +
    '<div class="toggle-row"><span class="toggle-label">Dark Mode</span>' +
    '<div class="toggle-switch ' + (document.documentElement.getAttribute('data-theme') === 'dark' ? 'on' : '') +
    '" data-action="toggle-theme" role="switch" tabindex="0"></div></div></div>';
  h += '<div class="card pad"><button class="btn btn-danger-ghost btn-block" data-action="logout">Log Out</button></div>';
  h += '</div>';
  return h;
}
/* ------------------------- seller views ------------------------- */
async function sellerView() {
  if (!state.user || !state.user.authenticated) {
    state.pendingAuthNext = '#/sell';
    return '<div class="view-enter"><div class="page-head"><h1>Seller Dashboard</h1></div>' +
      authPromptHtml('Verify your mobile number to access the seller dashboard') + '</div>';
  }
  if (state.user.role !== 'SELLER') {
    return '<div class="view-enter">' + emptyBlock('\uD83C\uDFE1', 'You are not a seller yet',
      'Set up your kitchen in under a minute and start selling to neighbours',
      '<button class="btn btn-primary" data-action="become-seller" style="margin-top:12px">Create My Kitchen</button>') + '</div>';
  }
  var p = await api('/api/seller/products');
  var k = await api('/api/seller/kitchen');
  var orders = await api('/api/seller/orders');
  state.sellerProducts = p || [];
  state.sellerKitchen = k || null;
  state.sellerOrders = orders || [];
  state.sellerProducts.forEach(function (x) { if (x && x.id != null) state.productIndex[x.id] = x; });

  var live = state.sellerProducts.filter(function (x) { return x.availableToday !== false; }).length;
  var paid = state.sellerOrders.filter(function (o) { return o.paymentStatus === 'PAID'; });
  var pending = state.sellerOrders.filter(function (o) {
    return o.orderStatus !== 'COMPLETED' && o.orderStatus !== 'CANCELLED' && o.paymentStatus !== 'PAID';
  });
  var earnings = paid.reduce(function (s, o) { return s + Number(o.totalAmount || 0); }, 0);

  var h = '<div class="view-enter"><div class="page-head"><h1>' +
    esc((state.sellerKitchen && (state.sellerKitchen.displayName || state.sellerKitchen.name)) || 'My Kitchen') + '</h1>' +
    '<p class="muted">Seller Dashboard</p></div>';
  h += '<div class="metric-grid">' +
    '<div class="metric-card"><span class="metric-value">' + state.sellerOrders.length + '</span><span class="metric-label">Orders</span></div>' +
    '<div class="metric-card"><span class="metric-value">' + money(earnings) + '</span><span class="metric-label">Earnings</span></div>' +
    '<div class="metric-card"><span class="metric-value">' + live + '</span><span class="metric-label">Live Items</span></div>' +
    '<div class="metric-card"><span class="metric-value">' +
    ((state.sellerKitchen && state.sellerKitchen.rating != null) ? state.sellerKitchen.rating : 'New') +
    '</span><span class="metric-label">Rating</span></div></div>';
  h += '<div class="toggle-row">' +
    '<span class="toggle-label ' + (state.sellerTab === 'offerings' ? 'active' : '') + '" data-action="switch-seller-tab" data-tab="offerings">Offerings</span>' +
    '<span class="toggle-label ' + (state.sellerTab === 'orders' ? 'active' : '') + '" data-action="switch-seller-tab" data-tab="orders">Orders (' + pending.length + ' pending)</span>' +
    '<span class="toggle-label ' + (state.sellerTab === 'settings' ? 'active' : '') + '" data-action="switch-seller-tab" data-tab="settings">Kitchen</span></div>';

  if (state.sellerTab === 'offerings') {
    h += '<button class="btn btn-primary btn-block" data-action="new-product" style="margin-bottom:12px">+ Add Offering</button>';
    if (!state.sellerProducts.length) {
      h += emptyBlock('\uD83C\uDF73', 'No offerings yet', 'Post your first dish and neighbours can order it');
    } else {
      h += '<div class="stack-lg">' + state.sellerProducts.map(sellerOfferingCard).join('') + '</div>';
    }
  } else if (state.sellerTab === 'orders') {
    var cards = state.sellerOrders.map(sellerOrderCard).join('');
    h += cards ? '<div class="stack-lg">' + cards + '</div>'
      : emptyBlock('\uD83D\uDCE6', 'No orders yet', 'Orders from neighbours will show up here');
  } else {
    h += sellerKitchenFormHtml(state.sellerKitchen);
  }
  h += '</div>';
  return h;
}

function sellerOfferingCard(x) {
  var isLive = x.availableToday !== false;
  return '<div class="offering-card"><div class="offering-card-top">' +
    '<div><strong>' + emojiFor(x.imageUrl || x.name) + ' ' + esc(x.name) + '</strong>' +
    '<div class="muted small">' + money(x.price) + (x.priceUnit ? ' / ' + esc(x.priceUnit) : '') +
    (x.remainingQuantity != null ? ' \u00B7 ' + x.remainingQuantity + ' left' : '') + '</div></div>' +
    '<span class="badge ' + (isLive ? 'badge-live' : 'badge-paused') + '">' + (isLive ? 'Live' : 'Paused') + '</span></div>' +
    '<div class="offering-card-actions">' +
    '<button class="btn btn-ghost btn-sm" data-action="preview-product" data-product-id="' + x.id + '">Preview</button>' +
    '<button class="btn btn-secondary btn-sm" data-action="edit-product" data-product-id="' + x.id + '">Modify</button>' +
    '<button class="btn btn-ghost btn-sm" data-action="toggle-product-live" data-product-id="' + x.id + '">' + (isLive ? 'Pause' : 'Go Live') + '</button>' +
    '<button class="btn btn-danger-ghost btn-sm" data-action="delete-product" data-product-id="' + x.id + '">Delete</button>' +
    '</div></div>';
}
function sellerOrderCard(o) {
  var b = o.buyer || {};
  var next = NEXT_STATUS[o.orderStatus];
  return '<div class="offering-card"><div class="offering-card-top">' +
    '<div><strong>' + esc(o.orderNumber || ('Order #' + o.id)) + '</strong>' +
    '<div class="muted small">' + esc(b.name || 'Buyer') + ' \u00B7 ' + esc(b.mobileNumber || '') +
    (b.flatHouseNumber ? '<br>Flat ' + esc(b.flatHouseNumber) : '') + '</div></div>' +
    '<span class="badge badge-status-' + o.orderStatus + '">' + (STATUS_LABEL[o.orderStatus] || o.orderStatus) + '</span></div>' +
    '<div class="muted small" style="margin:8px 0">' +
    (o.items || []).map(function (i) { return esc(i.productName) + ' \u00D7 ' + i.quantity; }).join(' \u2022 ') + '</div>' +
    '<div style="display:flex;justify-content:space-between;align-items:center">' +
    '<span class="badge ' + (o.paymentStatus === 'PAID' ? 'badge-live' : 'badge-paused') + '">' +
    (o.paymentStatus === 'PAID' ? 'Paid' : 'Payment Pending') + '</span>' +
    '<strong class="price">' + money(o.totalAmount) + '</strong></div>' +
    '<div class="offering-card-actions">' +
    (next ? '<button class="btn btn-primary btn-sm" data-action="advance-status" data-order-id="' + o.id + '" data-next="' + next + '">' + NEXT_LABEL[next] + '</button>' : '') +
    ((o.orderStatus === 'PLACED' || o.orderStatus === 'CONFIRMED') ?
      '<button class="btn btn-danger-ghost btn-sm" data-action="cancel-order" data-order-id="' + o.id + '">Cancel</button>' : '') +
    '</div></div>';
}

function sellerKitchenFormHtml(k) {
  k = k || {};
  return '<div class="card pad"><form data-form="save-kitchen">' +
    '<input type="hidden" name="name" value="' + esc(k.name || '') + '">' +
    '<div class="form-group"><label class="form-label" for="kDisplayName">Kitchen Name</label>' +
    '<input class="form-input" id="kDisplayName" name="displayName" value="' + esc(k.displayName || '') + '" required></div>' +
    '<div class="form-group"><label class="form-label" for="kSociety">Society</label>' +
    '<input class="form-input" id="kSociety" name="society" value="' + esc(k.society || '') + '"></div>' +
    '<div class="form-group"><label class="form-label" for="kBuilding">Building / Tower</label>' +
    '<input class="form-input" id="kBuilding" name="building" value="' + esc(k.building || '') + '"></div>' +
    '<div class="form-group"><label class="form-label" for="kShort">One-line Specialty</label>' +
    '<input class="form-input" id="kShort" name="shortDescription" value="' + esc(k.shortDescription || '') + '" placeholder="North Indian home meals"></div>' +
    '<div class="form-group"><label class="form-label" for="kDesc">Full Bio</label>' +
    '<textarea class="form-input" id="kDesc" name="description" rows="3">' + esc(k.description || '') + '</textarea></div>' +
    '<div class="form-group"><label class="form-label" for="kEmoji">Kitchen Emoji</label>' +
    '<input class="form-input" id="kEmoji" name="imageUrl" value="' + esc(k.imageUrl || '') + '" placeholder="An emoji like a pot or plate"></div>' +
    '<div class="form-group"><label class="form-label" for="kWhats">WhatsApp Link</label>' +
    '<input class="form-input" id="kWhats" name="whatsappLink" value="' + esc(k.whatsappLink || '') + '" placeholder="https://wa.me/..."></div>' +
    '<div class="form-group"><label class="form-label" for="kInsta">Instagram Link</label>' +
    '<input class="form-input" id="kInsta" name="instagramLink" value="' + esc(k.instagramLink || '') + '" placeholder="https://instagram.com/..."></div>' +
    '<div class="form-group"><label class="form-label" for="kUpi">UPI ID (for payments)</label>' +
    '<input class="form-input" id="kUpi" name="upiId" value="' + esc(k.upiId || '') + '" placeholder="name@upi"></div>' +
    '<div class="toggle-row"><span class="toggle-label">Available Today</span>' +
    '<div class="toggle-switch ' + (k.availableToday !== false ? 'on' : '') + '" data-action="flip-kitchen-open" role="switch" tabindex="0"></div></div>' +
    '<input type="hidden" name="availableToday" id="kAvail" value="' + (k.availableToday !== false ? 'true' : 'false') + '">' +
    '<button class="btn btn-primary btn-block" type="submit" style="margin-top:12px">Save Changes</button>' +
    '</form></div>';
}
/* ------------------------- modals ------------------------- */
function productModalHtml(p) {
  p = p || {};
  var isEdit = !!p.id;
  return '<div class="modal-title">' + (isEdit ? 'Modify Offering' : 'New Offering') + '</div>' +
    '<form data-form="save-product">' +
    '<input type="hidden" name="id" value="' + (p.id || '') + '">' +
    '<div class="form-group"><label class="form-label" for="pmEmoji">Food Emoji</label>' +
    '<input class="form-input" id="pmEmoji" name="imageUrl" value="' + esc(p.imageUrl || '') + '" placeholder="An emoji, e.g. rice bowl"></div>' +
    '<div class="form-group"><label class="form-label" for="pmName">Item Name</label>' +
    '<input class="form-input" id="pmName" name="name" value="' + esc(p.name || '') + '" placeholder="e.g. Paneer Butter Masala" required></div>' +
    '<div class="form-group"><label class="form-label" for="pmDesc">Short Description</label>' +
    '<input class="form-input" id="pmDesc" name="description" value="' + esc(p.description || '') + '" placeholder="Creamy tomato gravy with soft paneer"></div>' +
    '<div style="display:flex;gap:10px"><div class="form-group" style="flex:1">' +
    '<label class="form-label" for="pmPrice">Price</label>' +
    '<input class="form-input" id="pmPrice" name="price" type="number" min="1" step="1" value="' + (p.price != null ? Number(p.price) : '') + '" required></div>' +
    '<div class="form-group" style="flex:1">' +
    '<label class="form-label" for="pmUnit">Unit</label>' +
    '<input class="form-input" id="pmUnit" name="priceUnit" value="' + esc(p.priceUnit || 'plate') + '" placeholder="plate / kg / dozen"></div></div>' +
    '<div class="form-group"><label class="form-label" for="pmMax">Max Quantity Limit</label>' +
    '<input class="form-input" id="pmMax" name="maxQuantity" type="number" min="1" step="1" value="' + (p.maxQuantity != null ? p.maxQuantity : 20) + '"></div>' +
    '<div class="form-group"><label class="form-label" for="pmDate">Available Date</label>' +
    '<input class="form-input" id="pmDate" name="availableDate" type="date" value="' + esc(p.availableDate || '') + '"></div>' +
    '<div style="display:flex;gap:10px"><div class="form-group" style="flex:1">' +
    '<label class="form-label" for="pmStart">Order Window Start</label>' +
    '<input class="form-input" id="pmStart" name="orderWindowStart" type="time" value="' + esc(p.orderWindowStart || '') + '"></div>' +
    '<div class="form-group" style="flex:1">' +
    '<label class="form-label" for="pmEnd">Order Window End</label>' +
    '<input class="form-input" id="pmEnd" name="orderWindowEnd" type="time" value="' + esc(p.orderWindowEnd || '') + '"></div></div>' +
    '<div class="toggle-row"><span class="toggle-label">Available Today (Live)</span>' +
    '<div class="toggle-switch ' + (p.availableToday !== false ? 'on' : '') + '" data-action="flip-product-live" role="switch" tabindex="0"></div></div>' +
    '<input type="hidden" name="availableToday" id="pmAvail" value="' + (p.availableToday !== false ? 'true' : 'false') + '">' +
    '<div class="toggle-row"><span class="toggle-label">Is Pre-order</span>' +
    '<div class="toggle-switch ' + (p.isPreorder ? 'on' : '') + '" data-action="flip-product-preorder" role="switch" tabindex="0"></div></div>' +
    '<input type="hidden" name="isPreorder" id="pmPre" value="' + (p.isPreorder ? 'true' : 'false') + '">' +
    '<div style="display:flex;gap:8px;margin-top:12px">' +
    '<button class="btn btn-primary" type="submit" style="flex:1">' + (isEdit ? 'Save Changes' : 'Publish Offering') + '</button>' +
    '<button class="btn btn-ghost" type="button" data-action="close-modal">Cancel</button></div>' +
    '</form>';
}
function quickPostModalHtml() {
  return '<div class="modal-title">Quick Post</div>' +
    '<p class="muted small">Like a status update \u2014 just type the dish and price, we fill in the rest.</p>' +
    '<form data-form="quick-post">' +
    '<div class="form-group"><label class="form-label" for="qpText">What are you making?</label>' +
    '<textarea class="form-input" id="qpText" name="text" rows="3" placeholder="Hot gulab jamun - 10rs each, ready by 5pm" required></textarea></div>' +
    '<div class="form-group"><label class="form-label" for="qpQty">How many can you make?</label>' +
    '<input class="form-input" id="qpQty" name="maxQuantity" type="number" min="1" step="1" value="10"></div>' +
    '<div style="display:flex;gap:8px;margin-top:8px">' +
    '<button class="btn btn-primary" type="submit" style="flex:1">Post Now</button>' +
    '<button class="btn btn-ghost" type="button" data-action="close-modal">Cancel</button></div></form>';
}

function kitchenOnboardModalHtml() {
  return '<div class="modal-title">Create Your Kitchen</div>' +
    '<p class="muted small">Tell neighbours about your kitchen. You can edit everything later.</p>' +
    '<form data-form="onboard-kitchen">' +
    '<div class="form-group"><label class="form-label" for="koName">Kitchen Name</label>' +
    '<input class="form-input" id="koName" name="displayName" placeholder="e.g. Rahul\'s Rasoi" required></div>' +
    '<div class="form-group"><label class="form-label" for="koShort">One-line Specialty</label>' +
    '<input class="form-input" id="koShort" name="shortDescription" placeholder="North Indian home meals"></div>' +
    '<div class="form-group"><label class="form-label" for="koSociety">Society</label>' +
    '<input class="form-input" id="koSociety" name="society" value="Pride World City"></div>' +
    '<div class="form-group"><label class="form-label" for="koBuilding">Building / Tower</label>' +
    '<input class="form-input" id="koBuilding" name="building" placeholder="e.g. Tower A"></div>' +
    '<div class="form-group"><label class="form-label" for="koEmoji">Kitchen Emoji</label>' +
    '<input class="form-input" id="koEmoji" name="imageUrl" placeholder="An emoji for your kitchen"></div>' +
    '<div class="form-group"><label class="form-label" for="koUpi">UPI ID (for payments)</label>' +
    '<input class="form-input" id="koUpi" name="upiId" placeholder="name@upi"></div>' +
    '<div style="display:flex;gap:8px;margin-top:8px">' +
    '<button class="btn btn-primary" type="submit" style="flex:1">Create Kitchen</button>' +
    '<button class="btn btn-ghost" type="button" data-action="close-modal">Cancel</button></div></form>';
}

function itemPickerModalHtml(candidates, kitchenName) {
  return '<div class="modal-title">Add to Order</div>' +
    '<p class="muted small">Items from ' + esc(kitchenName || 'your selected kitchen') + '</p>' +
    '<div class="stack">' + candidates.map(function (p) {
      return '<div class="order-item-row"><div><strong>' + emojiFor(p.imageUrl || p.name) + ' ' + esc(p.name) + '</strong>' +
        '<div class="muted small">' + money(p.price) + (p.remainingQuantity != null ? ' \u00B7 ' + p.remainingQuantity + ' left' : '') + '</div></div>' +
        '<button class="btn btn-primary btn-sm" data-action="pick-product" data-product-id="' + p.id + '">Add</button></div>';
    }).join('') + '</div>';
}

function openProductModal(id) {
  var p = state.productIndex[id];
  if (!p) { toast('Item unavailable', 'error'); return; }
  state.modalQty = 1;
  state.modalProduct = p;
  var remaining = p.remainingQuantity != null ? p.remainingQuantity : null;
  openModal('<div class="modal-title">' + emojiFor(p.imageUrl || p.name) + ' ' + esc(p.name) + '</div>' +
    (p.kitchenName ? '<p class="muted small">from ' + esc(p.kitchenName) + '</p>' : '') +
    (p.description ? '<p class="muted">' + esc(p.description) + '</p>' : '') +
    '<div class="sumline"><span>Price</span><span class="price">' + money(p.price) +
    (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + '</span></div>' +
    (remaining != null ? '<div class="sumline"><span>Available</span><span class="badge-stock">' + remaining + ' left</span></div>' : '') +
    '<div class="qty-counter" style="margin:14px auto;justify-content:center">' +
    '<button class="btn btn-ghost btn-sm" data-action="modal-qty-dec" aria-label="Decrease">\u2212</button>' +
    '<strong id="modalQtyVal">1</strong>' +
    '<button class="btn btn-ghost btn-sm" data-action="modal-qty-inc" aria-label="Increase">+</button>' +
    '</div>' +
    '<button class="btn btn-primary btn-block" data-action="modal-add" data-product-id="' + p.id + '">Add to Order \u00B7 <span id="modalLineTotal">' +
    money(p.price) + '</span></button>');
}
/* ------------------------- actions ------------------------- */
async function ensureDraftFor(product) {
  var d = state.draft;
  if (product.kitchenId == null) return true;
  if (!d || !d.items || !d.items.length || !d.kitchen || d.kitchen.id === product.kitchenId) return true;
  var ok = await new Promise(function (resolve) {
    openModal('<div class="modal-title">Start a new order?</div>' +
      '<p class="muted">Your current order is from <strong>' + esc(d.kitchen.displayName || 'another kitchen') +
      '</strong>. Start a fresh order from <strong>' + esc(product.kitchenName || 'this kitchen') + '</strong>?</p>' +
      '<div style="display:flex;gap:8px;margin-top:12px">' +
      '<button class="btn btn-primary" id="mYes" style="flex:1">Yes, start new</button>' +
      '<button class="btn btn-ghost" id="mNo" style="flex:1">Keep current</button></div>');
    $('#mYes').onclick = function () { closeModal(); resolve(true); };
    $('#mNo').onclick = function () { closeModal(); resolve(false); };
  });
  if (!ok) return false;
  await api('/api/buyer/orders/draft', { method: 'DELETE' });
  state.draft = null;
  return true;
}

async function addToDraft(product, delta) {
  var ok = await ensureDraftFor(product);
  if (!ok) return;
  var items = [];
  if (state.draft && state.draft.items) {
    items = state.draft.items.map(function (i) { return { productId: i.productId, quantity: i.quantity }; });
  }
  var found = items.find(function (i) { return i.productId === product.id; });
  if (found) found.quantity += delta;
  else items.push({ productId: product.id, quantity: delta });
  items = items.filter(function (i) { return i.quantity > 0; });
  if (!items.length) {
    await api('/api/buyer/orders/draft', { method: 'DELETE' });
    state.draft = null;
    updateCartBadge();
    return;
  }
  var kitchenId = product.kitchenId != null ? product.kitchenId
    : (state.draft && state.draft.kitchen ? state.draft.kitchen.id : null);
  state.draft = await api('/api/buyer/orders/draft?kitchenId=' + kitchenId, { method: 'POST', body: items });
  updateCartBadge();
  closeModal();
  toast('Added to your order', 'success');
  var here = parseHash();
  if (here.name === 'order') render();
}

async function clearDraft() {
  await api('/api/buyer/orders/draft', { method: 'DELETE' });
  state.draft = null;
  updateCartBadge();
  render();
  toast('Order cleared', 'info');
}
async function handleOtpRequest(form) {
  var vals = formVals(form);
  if (!/^[6-9]\d{9}$/.test(vals.mobileNumber)) { toast('Enter a valid 10-digit mobile number', 'error'); return; }
  state.otpMobile = vals.mobileNumber;
  var resp = await api('/api/auth/otp/request', { method: 'POST', body: { mobileNumber: vals.mobileNumber } });
  state.otpStep = 'verify';
  openModal('<div class="modal-title">Verify OTP</div>' +
    '<p class="muted small">Demo mode: your OTP is <strong class="price">' + esc(resp.otp) + '</strong></p>' +
    '<form data-form="otp-verify">' +
    '<div class="form-group"><label class="form-label" for="ovCode">4-digit OTP</label>' +
    '<input class="form-input" id="ovCode" name="otpCode" inputmode="numeric" maxlength="4" pattern="[0-9]{4}" placeholder="Enter OTP" required></div>' +
    '<div class="form-group"><label class="form-label" for="ovName">Your Name</label>' +
    '<input class="form-input" id="ovName" name="name" placeholder="Full name"></div>' +
    '<div class="form-group"><label class="form-label" for="ovFlat">Flat / House No.</label>' +
    '<input class="form-input" id="ovFlat" name="flatHouseNumber" placeholder="e.g. A-1204"></div>' +
    '<button class="btn btn-primary btn-block" type="submit">Verify &amp; Continue</button></form>');
}

async function handleOtpVerify(form) {
  var vals = formVals(form);
  var resp = await api('/api/auth/otp/verify', {
    method: 'POST',
    body: {
      mobileNumber: state.otpMobile,
      otpCode: vals.otpCode,
      name: vals.name || undefined,
      flatHouseNumber: vals.flatHouseNumber || undefined
    }
  });
  closeModal();
  await applyAuth(resp);
  toast('Welcome, ' + (resp.name || 'neighbour') + '!', 'success');
  var next = state.pendingAuthNext || '#/home';
  state.pendingAuthNext = '#/home';
  if (location.hash === next) render(); else location.hash = next;
}

async function handlePlaceOrder(form) {
  var vals = formVals(form);
  var btn = form.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    var order = await api('/api/buyer/orders/place', {
      method: 'POST',
      body: {
        paymentStatus: 'PENDING',
        buyerDetails: {
          name: vals.name,
          mobileNumber: vals.mobileNumber,
          society: vals.society,
          building: vals.building,
          flatHouseNumber: vals.flatHouseNumber
        },
        customInstructions: vals.customInstructions || undefined
      }
    });
    state.draft = null;
    updateCartBadge();
    toast('Order placed! Complete the payment to confirm.', 'success');
    location.hash = '#/payment/' + order.id;
  } catch (err) {
    toast(err.message || 'Could not place the order', 'error');
    await refreshDraft();
    if (!state.draft || !state.draft.items || !state.draft.items.length) {
      location.hash = '#/my-orders';
    }
  } finally { btn.disabled = false; }
}

async function handleMarkPaid(orderId) {
  await api('/api/buyer/orders/' + orderId + '/payment-status', {
    method: 'PATCH',
    body: { paymentStatus: 'PAID' }
  });
  var o = await api('/api/buyer/orders/' + orderId);
  toast('Payment confirmed. Thank you!', 'success');
  viewEl().innerHTML = paymentSuccessHtml(o);
}
/* ------------------------- profile / seller actions ------------------------- */
async function handleSaveProfile(form) {
  var vals = formVals(form);
  await api('/api/buyer/profile', { method: 'PUT', body: vals });
  await applyAuth(await api('/api/auth/me'));
  toast('Profile saved', 'success');
}

async function becomeSeller() {
  try {
    await api('/api/auth/become-seller', { method: 'POST' });
  } catch (e) {
    if (e.status === 401) {
      openModal('<div class="modal-title">Verify your number</div>' +
        '<p class="muted small">Verify your mobile number to start selling.</p>' +
        '<form data-form="otp-request">' +
        '<div class="form-group"><label class="form-label" for="otpMobile">Mobile Number</label>' +
        '<input class="form-input" id="otpMobile" name="mobileNumber" inputmode="numeric" pattern="[6-9][0-9]{9}" maxlength="10" placeholder="10-digit mobile number" required></div>' +
        '<button class="btn btn-primary btn-block" type="submit">Send OTP</button></form>');
      return;
    }
    throw e;
  }
  await applyAuth(await api('/api/auth/me'));
  closeModal();
  openModal(kitchenOnboardModalHtml());
}

async function handleOnboardKitchen(form) {
  var vals = formVals(form);
  vals.name = (vals.displayName || 'kitchen').toLowerCase().replace(/[^a-z0-9]+/g, '') + Date.now();
  vals.availableToday = true;
  await api('/api/seller/kitchen', { method: 'POST', body: vals });
  closeModal();
  await applyAuth(await api('/api/auth/me'));
  toast('Kitchen created! Add your first offering.', 'success');
  state.sellerTab = 'offerings';
  location.hash = '#/sell';
}

async function handleSaveKitchen(form) {
  var vals = formVals(form);
  vals.availableToday = $('#kAvail') ? $('#kAvail').value === 'true' : true;
  var k = state.sellerKitchen;
  if (k && k.id != null) {
    await api('/api/seller/kitchen/' + k.id, { method: 'PUT', body: vals });
  } else {
    await api('/api/seller/kitchen', { method: 'POST', body: vals });
  }
  toast('Kitchen profile saved', 'success');
  render();
}

function collectProductForm(form) {
  var vals = formVals(form);
  var availEl = $('#pmAvail'), preEl = $('#pmPre');
  vals.availableToday = availEl ? availEl.value === 'true' : true;
  vals.isPreorder = preEl ? preEl.value === 'true' : false;
  vals.price = Number(vals.price);
  vals.maxQuantity = vals.maxQuantity ? Number(vals.maxQuantity) : null;
  if (!vals.availableDate) delete vals.availableDate;
  if (!vals.orderWindowStart) delete vals.orderWindowStart;
  if (!vals.orderWindowEnd) delete vals.orderWindowEnd;
  return vals;
}

async function handleSaveProduct(form) {
  var vals = collectProductForm(form);
  var id = vals.id;
  delete vals.id;
  if (id) {
    await api('/api/seller/products/' + id, { method: 'PUT', body: vals });
    toast('Offering updated', 'success');
  } else {
    var kitchenId = state.sellerKitchen ? state.sellerKitchen.id : null;
    if (kitchenId == null) { toast('Create your kitchen first', 'error'); return; }
    await api('/api/seller/products?kitchenId=' + kitchenId, { method: 'POST', body: vals });
    toast('Offering published', 'success');
  }
  closeModal();
  render();
}

async function handleQuickPost(form) {
  var vals = formVals(form);
  var m = vals.text.match(/([a-zA-Z][a-zA-Z\s'&-]{2,40}?)(?:\s*[-,\u2013\u2014]\s*|\s+)(?:rs\.?|inr|\u20B9)?\s*(\d{2,5})/i);
  var name = m ? m[1].trim() : vals.text.slice(0, 40).trim();
  var price = m ? Number(m[2]) : 0;
  if (!price || price <= 0) {
    toast('Add a price, e.g. "Hot gulab jamun - 10rs each"', 'error');
    return;
  }
  var kitchenId = state.sellerKitchen ? state.sellerKitchen.id : null;
  if (kitchenId == null) { toast('Create your kitchen first', 'error'); return; }
  await api('/api/seller/products?kitchenId=' + kitchenId, {
    method: 'POST',
    body: {
      name: name,
      description: vals.text,
      price: price,
      priceUnit: 'plate',
      availableToday: true,
      maxQuantity: vals.maxQuantity ? Number(vals.maxQuantity) : 10
    }
  });
  closeModal();
  toast('Quick post published!', 'success');
  render();
}
/* ------------------------- small actions ------------------------- */
async function toggleProductLive(id) {
  var p = state.productIndex[id];
  if (!p) return;
  var body = Object.assign({}, p, { availableToday: p.availableToday === false });
  await api('/api/seller/products/' + id, { method: 'PUT', body: body });
  toast(p.availableToday === false ? 'Offering is live' : 'Offering paused', 'info');
  render();
}

async function deleteProduct(id) {
  var ok = await new Promise(function (resolve) {
    openModal('<div class="modal-title">Delete offering?</div>' +
      '<p class="muted">This will remove the item permanently.</p>' +
      '<div style="display:flex;gap:8px;margin-top:12px">' +
      '<button class="btn btn-danger-ghost" id="mYes" style="flex:1">Delete</button>' +
      '<button class="btn btn-ghost" id="mNo" style="flex:1">Cancel</button></div>');
    $('#mYes').onclick = function () { closeModal(); resolve(true); };
    $('#mNo').onclick = function () { closeModal(); resolve(false); };
  });
  if (!ok) return;
  await api('/api/seller/products/' + id, { method: 'DELETE' });
  toast('Offering deleted', 'info');
  render();
}

async function advanceOrderStatus(orderId, next) {
  await api('/api/seller/orders/' + orderId + '/status', { method: 'PATCH', body: { orderStatus: next } });
  toast('Order marked ' + (STATUS_LABEL[next] || next).toLowerCase(), 'success');
  render();
}

async function cancelSellerOrder(orderId) {
  await api('/api/seller/orders/' + orderId + '/status', { method: 'PATCH', body: { orderStatus: 'CANCELLED' } });
  toast('Order cancelled', 'info');
  render();
}

async function rateOrder(orderId) {
  var rating = await new Promise(function (resolve) {
    openModal('<div class="modal-title">Rate your order</div>' +
      '<p class="muted small">How was the food?</p>' +
      '<div style="display:flex;gap:8px;justify-content:center;margin:12px 0">' +
      [1, 2, 3, 4, 5].map(function (n) {
        return '<button class="btn btn-secondary btn-sm" data-rate="' + n + '">' + '\u2B50'.repeat(n) + '</button>';
      }).join('') + '</div>' +
      '<button class="btn btn-ghost btn-block" data-action="close-modal">Not now</button>');
    $all('[data-rate]').forEach(function (b) {
      b.onclick = function () { closeModal(); resolve(Number(b.getAttribute('data-rate'))); };
    });
  });
  if (!rating) return;
  await api('/api/buyer/orders/' + orderId + '/rate?rating=' + rating, { method: 'POST' });
  state.ratedOrders[orderId] = true;
  toast('Thanks for rating ' + rating + '\u2B50', 'success');
  render();
}

async function reorder(orderId) {
  await api('/api/buyer/orders/' + orderId + '/reorder', { method: 'POST' });
  await refreshDraft();
  toast('Items added to a new order', 'success');
  location.hash = '#/my-orders';
}

async function doLogout() {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
  state.user = null;
  state.draft = null;
  applyUserChrome();
  updateCartBadge();
  toast('Logged out', 'info');
  location.hash = '#/home';
  render();
}

async function copyUpi(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('UPI ID copied', 'success');
  } catch (e) {
    var ta = document.createElement('textarea');
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); toast('UPI ID copied', 'success'); }
    catch (x) { toast('Copy failed \u2014 please copy manually', 'error'); }
    ta.remove();
  }
}

/* ------------------------- event wiring ------------------------- */
function runAction(fn) {
  return Promise.resolve().then(fn).catch(function (err) {
    toast((err && err.message) ? err.message : 'Something went wrong', 'error');
  });
}

function bindEvents() {
  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-action]');
    if (!el) return;
    var pid = el.getAttribute('data-product-id');
    var oid = el.getAttribute('data-order-id');
    runAction(function () {
    switch (el.getAttribute('data-action')) {
      case 'pick-product': openProductModal(Number(pid)); break;
      case 'modal-qty-dec': updateModalQty(-1); break;
      case 'modal-qty-inc': updateModalQty(1); break;
      case 'modal-add':
        if (state.modalProduct) addToDraft(state.modalProduct, state.modalQty || 1);
        break;
      case 'draft-inc': addToDraft(state.productIndex[Number(pid)], 1); break;
      case 'draft-dec': addToDraft(state.productIndex[Number(pid)], -1); break;
      case 'go-checkout': location.hash = '#/checkout'; break;
      case 'clear-draft': clearDraft(); break;
      case 'filter-cat': doFilterCat(el.getAttribute('data-cat')); break;
      case 'clear-cat': state.catFilter = null; render(); break;
      case 'toggle-view':
        state.viewMode = state.viewMode === 'items' ? 'kitchens' : 'items';
        render(); break;
      case 'switch-orders-tab': state.ordersTab = el.getAttribute('data-tab') || 'active'; render(); break;
      case 'switch-seller-tab': state.sellerTab = el.getAttribute('data-tab') || 'offerings'; render(); break;
      case 'new-product': openProductForm(null); break;
      case 'preview-product': previewProduct(Number(pid)); break;
      case 'edit-product':
        openProductForm(state.sellerProducts.find(function (p) { return p.id === Number(pid); }));
        break;
      case 'toggle-product-live': toggleProductLive(Number(pid)); break;
      case 'delete-product': deleteProduct(Number(pid)); break;
      case 'advance-status': advanceOrderStatus(Number(oid), el.getAttribute('data-next')); break;
      case 'cancel-order': cancelSellerOrder(Number(oid)); break;
      case 'copy-upi': copyUpi(el.getAttribute('data-upi') || ''); break;
      case 'mark-paid': handleMarkPaid(Number(oid)); break;
      case 'go-order-detail': location.hash = '#/order-detail/' + oid; break;
      case 'go-pay': location.hash = '#/payment/' + oid; break;
      case 'flip-product-live': flipHidden('pmAvail', el); break;
      case 'flip-product-preorder': flipHidden('pmPre', el); break;
      case 'flip-kitchen-open': flipHidden('kAvail', el); break;
      case 'rate-order': rateOrder(Number(oid)); break;
      case 'reorder': reorder(Number(oid)); break;
      case 'toggle-theme': toggleTheme(); break;
      case 'close-modal': closeModal(); break;
      case 'retry-render': render(); break;
      case 'become-seller': becomeSeller(); break;
      case 'logout': doLogout(); break;
    }
    });
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeModal();
    if (e.target && e.target.closest && e.target.closest('.toggle-switch[data-action="toggle-view"]') &&
      (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      state.viewMode = state.viewMode === 'items' ? 'kitchens' : 'items';
      render();
    }
  });

  document.addEventListener('submit', function (e) {
    var form = e.target.closest('[data-form]');
    if (!form) return;
    e.preventDefault();
    var kind = form.getAttribute('data-form');
    if (kind === 'search') {
      var q = form.q.value.trim();
      if (q) location.hash = '#/search/' + encodeURIComponent(q);
    }
    else if (kind === 'otp-request') runAction(function () { return handleOtpRequest(form); });
    else if (kind === 'otp-verify') runAction(function () { return handleOtpVerify(form); });
    else if (kind === 'place-order') runAction(function () { return handlePlaceOrder(form); });
    else if (kind === 'save-profile') runAction(function () { return handleSaveProfile(form); });
    else if (kind === 'save-kitchen') runAction(function () { return handleSaveKitchen(form); });
    else if (kind === 'save-product') runAction(function () { return handleSaveProduct(form); });
    else if (kind === 'quick-post') runAction(function () { return handleQuickPost(form); });
    else if (kind === 'onboard-kitchen') runAction(function () { return handleOnboardKitchen(form); });
  });
}

function openProductForm(p) {
  openModal(productModalHtml(p || {}));
}

function previewProduct(id) {
  var p = state.productIndex[id];
  if (!p) return;
  var isLive = p.availableToday !== false;
  openModal('<div class="modal-title">' + emojiFor(p.imageUrl || p.name) + ' ' + esc(p.name) + '</div>' +
    '<p class="muted">' + esc(p.description || 'No description yet') + '</p>' +
    '<div class="sumline"><span>Price</span><span class="price">' + money(p.price) +
    (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + '</span></div>' +
    '<div class="sumline"><span>Status</span><span class="badge ' + (isLive ? 'badge-live' : 'badge-paused') + '">' +
    (isLive ? 'Live' : 'Paused') + '</span></div>' +
    (p.remainingQuantity != null ? '<div class="sumline"><span>Remaining</span><span>' + p.remainingQuantity + '</span></div>' : '') +
    '<button class="btn btn-ghost btn-block" data-action="close-modal" style="margin-top:12px">Close</button>');
}

function flipHidden(inputId, el) {
  var inp = $('#' + inputId);
  if (inp) inp.value = (inp.value === 'true' ? 'false' : 'true');
  if (el) el.classList.toggle('on');
}

function updateModalQty(delta) {
  var p = state.modalProduct;
  if (!p) return;
  var max = (p.remainingQuantity != null) ? p.remainingQuantity : 99;
  var next = Math.min(Math.max((state.modalQty || 1) + delta, 1), Math.max(max, 1));
  state.modalQty = next;
  var qEl = $('#modalQtyVal');
  var tEl = $('#modalLineTotal');
  if (qEl) qEl.textContent = String(next);
  if (tEl) tEl.textContent = money(p.price * next);
}

function doFilterCat(cat) {
  state.catFilter = state.catFilter === cat ? null : cat;
  render();
}

/* ------------------------- bootstrap ------------------------- */
initTheme();
bindEvents();

// View updates trigger instantly on ANY hash route change (no page reload).
window.addEventListener('hashchange', render);

// Initial app boot happens on window 'load' (DOM + assets ready). A fallback
// boots immediately if the script loads after the document is already parsed
// (e.g. cached/deferred), so the app can never be left on a blank screen.
var appBooted = false;
async function bootApp() {
  if (appBooted) return;
  appBooted = true;
  try {
    var me = await api('/api/auth/me');
    state.user = me;
    applyUserChrome();
  } catch (e) { state.user = null; }
  await refreshDraft();
  // Bare root URL (no hash) -> default route renders the home marketplace.
  if (!location.hash) location.replace('#/home');
  render();
}

window.addEventListener('load', bootApp);
if (document.readyState === 'interactive' || document.readyState === 'complete') {
  bootApp();
}
