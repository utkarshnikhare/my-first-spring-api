/**
 * SocioMart - Common Utilities & API Fetchers
 * Global helper functions used across all modules
 */

// ==================== DOM Helpers ====================
function $(sel, root) {
    return (root || document).querySelector(sel);
}

function $all(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
}

function viewEl() {
    return $('#view');
}

// ==================== String Helpers ====================
function esc(v) {
    return String(v == null ? '' : v)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function money(n) {
    var num = Number(n == null ? 0 : n);
    return '₹' + (Number.isInteger(num) ? String(num) : num.toFixed(2));
}

function emojiFor(name) {
    var n = (name || '').toLowerCase();
    if (n.indexOf('dosa') >= 0 || n.indexOf('idli') >= 0 || n.indexOf('rice') >= 0 || n.indexOf('bir') >= 0) return '🍚';
    if (n.indexOf('paneer') >= 0 || n.indexOf('dal') >= 0 || n.indexOf('masala') >= 0 || n.indexOf('curry') >= 0) return '🍛';
    if (n.indexOf('jamun') >= 0 || n.indexOf('sweet') >= 0 || n.indexOf('cake') >= 0) return '🍮';
    if (n.indexOf('coffee') >= 0 || n.indexOf('tea') >= 0) return '☕';
    if (n.indexOf('thali') >= 0 || n.indexOf('meal') >= 0) return '🍱';
    if (n.indexOf('samosa') >= 0 || n.indexOf('pak') >= 0 || n.indexOf('vada') >= 0) return '🥟';
    return '🍲';
}

// ==================== Form Helpers ====================
function formVals(form) {
    var out = {};
    $all('input, textarea, select', form).forEach(function (el) {
        if (!el.name) return;
        if (el.type === 'checkbox') out[el.name] = el.checked;
        else out[el.name] = el.value.trim();
    });
    return out;
}

// ==================== API Fetcher ====================
async function api(path, opts) {
    opts = opts || {};
    path = path.replace(/\/+$/, '');
    if (!path) path = '/';

    // Prepend API base URL if path starts with /api
    var url = path;
    if (path.startsWith('/api') || path.startsWith('/h2-console')) {
        url = CONFIG.API_BASE_URL + path;
    }

    var init = { method: opts.method || 'GET', credentials: 'same-origin', headers: {} };
    if (opts.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(opts.body);
    }

    var res, text, data = null;
    try {
        res = await fetch(url, init);
    } catch (err) {
        toast('Network error: ' + err.message, 'error');
        throw err;
    }

    text = await res.text();
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }

    if (!res.ok) {
        var msg = (data && data.message) || (data && data.error) || ('HTTP ' + res.status);
        if (CONFIG.DEBUG) console.warn('API error:', path, msg);
        throw new ApiError(msg, res.status, data);
    }
    return data;
}

// Custom API Error class
function ApiError(message, status, data) {
    this.message = message;
    this.status = status;
    this.data = data;
}
ApiError.prototype = Object.create(Error.prototype);

// ==================== Toast Notifications ====================
function toast(message, type) {
    var root = $('#toastRoot');
    if (!root) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (type || 'info');
    el.textContent = message;
    root.appendChild(el);
    setTimeout(function () {
        el.style.opacity = '0';
        setTimeout(function () { el.remove(); }, 300);
    }, 3000);
}

// ==================== Theme ====================
function toggleTheme() {
    var html = document.documentElement;
    var current = html.getAttribute('data-theme');
    var next = current === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', next);
    try { localStorage.setItem('sociomart-theme', next); } catch (e) {}
}

function initTheme() {
    var saved;
    try { saved = localStorage.getItem('sociomart-theme'); } catch (e) {}
    if (saved) document.documentElement.setAttribute('data-theme', saved);
}

// ==================== Constants ====================
var STATUS_LABEL = {
    ORDERED: 'Ordered', CONFIRMED: 'Confirmed', DELIVERED: 'Delivered', CANCELLED: 'Cancelled'
};

var NEXT_STATUS = {
    ORDERED: 'CONFIRMED', CONFIRMED: 'DELIVERED'
};

var NEXT_LABEL = {
    CONFIRMED: 'Confirm Order', DELIVERED: 'Mark Delivered'
};

var CAT_KEYWORDS = {
    breakfast: ['dosa', 'idli', 'poha', 'upma', 'paratha', 'omelette', 'tea', 'coffee', 'poori', 'puri', 'sandwich'],
    lunch: ['thali', 'biryani', 'rice', 'dal', 'roti', 'sabzi', 'curry', 'rajma', 'chole', 'sambar', 'paneer'],
    dinner: ['thali', 'roti', 'curry', 'dal', 'paneer', 'biryani', 'paratha', 'khichdi', 'masala'],
    snacks: ['samosa', 'pakora', 'pakoda', 'vada', 'sandwich', 'fritter', 'jamun', 'sweet', 'kachori', 'bhel']
};
