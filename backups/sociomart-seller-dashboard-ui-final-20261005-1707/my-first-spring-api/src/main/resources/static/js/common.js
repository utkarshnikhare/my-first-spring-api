/**
 * SocioMart Buyer App v1.0 — Common utilities
 * DOM helpers · API fetcher · single-kitchen cart (Spec 1.2) ·
 * deferred auth gate (Spec 1.1) · modal & bottom-sheet infra
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
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function money(n) {
    var num = Number(n == null ? 0 : n);
    return '₹' + (Number.isInteger(num) ? String(num) : num.toFixed(2));
}

/**
 * Graceful image fallback (guardrail M): swap a broken image for the
 * container's emoji so no broken-image icon or console error remains.
 * Containers may carry a data-emoji attribute; kitchens default to 🏪,
 * everything else to a plate. Safe to call multiple times per element.
 */
function imgFallback(imgEl) {
    if (!imgEl || imgEl.getAttribute('data-fb')) return;
    imgEl.setAttribute('data-fb', '1');
    var parent = imgEl.parentElement;
    if (!parent) { imgEl.style.display = 'none'; return; }
    var emoji = parent.getAttribute('data-emoji');
    if (!emoji) emoji = /avatar|fav-chip|fli-emoji|kc-/.test(parent.className) ? '🏪' : '🍽️';
    parent.textContent = emoji;
}

function emojiFor(name) {
    var n = (name || '').toLowerCase();
    if (n.indexOf('dosa') >= 0 || n.indexOf('idli') >= 0 || n.indexOf('rice') >= 0 || n.indexOf('biryani') >= 0) return '🍚';
    if (n.indexOf('paneer') >= 0 || n.indexOf('dal') >= 0 || n.indexOf('curry') >= 0 || n.indexOf('thali') >= 0) return '🍛';
    if (n.indexOf('jamun') >= 0 || n.indexOf('sweet') >= 0 || n.indexOf('cake') >= 0 || n.indexOf('kheer') >= 0 || n.indexOf('katli') >= 0) return '🍮';
    if (n.indexOf('coffee') >= 0 || n.indexOf('tea') >= 0 || n.indexOf('chai') >= 0) return '☕';
    if (n.indexOf('samosa') >= 0 || n.indexOf('pak') >= 0 || n.indexOf('vada') >= 0 || n.indexOf('burger') >= 0) return '🥟';
    if (n.indexOf('paratha') >= 0 || n.indexOf('puri') >= 0 || n.indexOf('naan') >= 0 || n.indexOf('roti') >= 0 || n.indexOf('chapati') >= 0) return '🫓';
    if (n.indexOf('lassi') >= 0 || n.indexOf('salad') >= 0) return '🥗';
    if (n.indexOf('noodle') >= 0) return '🍜';
    return '🍲';
}

function prettyTime(hhmm) {
    if (!hhmm) return '';
    var s = String(hhmm).trim();
    if (/[AP]M$/i.test(s)) {
        return s.replace(/\s+/g, ' ').replace(/\s*([AP]M)\s*$/i, ' $1');
    }
    var parts = s.split(':');
    var h = parseInt(parts[0], 10);
    var m = parts[1] || '00';
    if (isNaN(h)) return hhmm;
    var ampm = h >= 12 ? 'PM' : 'AM';
    h = h % 12;
    if (h === 0) h = 12;
    return h + ':' + m + ' ' + ampm;
}

function prettyDate(iso) {
    if (!iso) return '';
    var d = new Date(iso + 'T00:00:00');
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var diff = Math.round((d - today) / 86400000);
    if (diff === 1) return 'Tomorrow';
    if (diff === 0) return 'Today';
    return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

function formVals(form) {
    var out = {};
    $all('input, textarea, select', form).forEach(function (el) {
        if (!el.name) return;
        if (el.type === 'checkbox') {
            out[el.name] = el.checked;
        } else {
            out[el.name] = el.value.trim();
        }
    });
    return out;
}

function emptyHtml(icon, title, message, actionHtml) {
    return '<div class="empty"><span class="empty-icon">' + icon + '</span>' +
        '<div class="empty-title">' + esc(title) + '</div>' +
        '<p>' + esc(message) + '</p>' + (actionHtml || '') + '</div>';
}

// ==================== CSRF ====================

/**
 * Read the CSRF token the server published in the XSRF-TOKEN cookie.
 *
 * The backend uses Spring Security's CookieCsrfTokenRepository (see
 * SecurityConfig). For any profile where CSRF is enabled the server rejects
 * every unsafe request (POST/PUT/PATCH/DELETE) that does not echo the cookie
 * back in the X-XSRF-TOKEN header - it answers 403 Forbidden, which is what
 * used to break "Add Area" and every other state-changing Admin action.
 * When CSRF is disabled (the demo profile) no cookie is set and this returns
 * null, which is harmless: the extra header is simply ignored.
 */
function csrfTokenFromCookie() {
    var name = 'XSRF-TOKEN';
    var parts = (document.cookie || '').split(';');
    for (var i = 0; i < parts.length; i++) {
        var part = parts[i].trim();
        if (part.indexOf(name + '=') === 0) {
            return decodeURIComponent(part.substring(name.length + 1));
        }
    }
    return null;
}

/** Header name the backend reads the token from. */
var CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
// ==================== API Fetcher ====================

async function api(path, opts) {
    opts = opts || {};
    path = path.replace(/\/+$/, '');
    if (!path) path = '/';
    var url = (path.startsWith('/api') || path.startsWith('/h2-console')) ? CONFIG.API_BASE_URL + path : path;
    var init = { method: (opts.method || 'GET'), credentials: 'same-origin', headers: {} };
    var csrf = csrfTokenFromCookie();
    if (csrf) init.headers[CSRF_HEADER_NAME] = csrf;
    if (opts.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(opts.body);
    }
    var res;
    var text;
    var data = null;
    try {
        res = await fetch(url, init);
    } catch (err) {
        toast('Network error: ' + err.message, 'error');
        throw err;
    }
    text = await res.text();
    try {
        data = text ? JSON.parse(text) : null;
    } catch (e) {
        data = text;
    }
    if (!res.ok) {
        var msg = (data && data.message) || (data && data.error) || ('HTTP ' + res.status);
        if (res.status === 403 && (msg === 'Forbidden' || msg === 'Access Denied')) {
            msg = 'Your session is missing a security token. Please reload the page and sign in again.';
        }
        if (CONFIG.DEBUG) console.warn('API error:', path, msg);
        throw new ApiError(msg, res.status, data);
    }
    return data;
}

function ApiError(message, status, data) {
    this.message = message;
    this.status = status;
    this.data = data;
}
ApiError.prototype = Object.create(Error.prototype);
ApiError.prototype.constructor = ApiError;

// ==================== Toasts ====================

function toast(message, type) {
    var root = $('#toastRoot');
    if (!root) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (type || 'info');
    el.textContent = message;
    root.appendChild(el);
    setTimeout(function () {
        el.style.opacity = '0';
        setTimeout(function () {
            el.remove();
        }, 300);
    }, 3000);
}

// ==================== Notifications ====================

var NOTIFICATION_CACHE = null;

function notificationBadgeHtml() {
    return '<button class="icon-btn" type="button" data-action="toggle-notifs" aria-label="Notifications">🔔' +
        '<span class="bell-badge" data-notification-badge hidden>0</span></button>';
}

function notificationPanelHtml(id) {
    return '<div class="notif-panel" id="' + esc(id || 'notifPanel') + '" hidden></div>';
}

function notificationItemHtml(n) {
    return '<div class="notif-item unread" data-action="read-notification" data-notification-id="' + esc(n.id) + '">' +
        '<div class="font-700">' + esc(n.title || 'Notification') + '</div>' +
        '<div>' + esc(n.body || '') + '</div></div>';
}

function updateNotificationUi(items) {
    NOTIFICATION_CACHE = Array.isArray(items) ? items : [];
    var badges = $all('[data-notification-badge]');
    badges.forEach(function (badge) {
        badge.textContent = String(NOTIFICATION_CACHE.length);
        badge.hidden = NOTIFICATION_CACHE.length === 0;
    });
    $all('.notif-panel').forEach(function (panel) {
        panel.innerHTML = NOTIFICATION_CACHE.length
            ? NOTIFICATION_CACHE.map(notificationItemHtml).join('')
            : '<div class="muted small">No unread notifications.</div>';
    });
}

async function loadUnreadNotifications() {
    if (!document.querySelector('[data-notification-badge], .notif-panel')) return;
    try {
        updateNotificationUi(await api('/api/notifications'));
    } catch (err) {
        if (err instanceof ApiError && err.status === 401) updateNotificationUi([]);
        else updateNotificationUi([]);
    }
}

async function toggleNotifications(panelId) {
    var panel = panelId ? document.getElementById(panelId) : $('#notifPanel');
    if (!panel) return;
    if (panel.hidden) await loadUnreadNotifications();
    panel.hidden = !panel.hidden;
}

async function readNotification(id) {
    if (!id) return;
    try {
        await api('/api/notifications/' + encodeURIComponent(id) + '/read', { method: 'PATCH' });
        await loadUnreadNotifications();
    } catch (err) {
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    }
}

// ==================== Theme ====================

var THEME_META_COLORS = { light: '#FCF9F5', dark: '#1A1410' };

/** Sync every theme toggle button's pressed state + the browser UI colour. */
function applyThemeUiState() {
    var theme = document.documentElement.getAttribute('data-theme') || 'light';
    $all('[data-action="toggle-theme"]').forEach(function (btn) {
        btn.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
        btn.title = theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme';
    });
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', THEME_META_COLORS[theme] || THEME_META_COLORS.light);
}

function toggleTheme() {
    var html = document.documentElement;
    var next = html.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', next);
    try {
        localStorage.setItem('sociomart-theme', next);
    } catch (e) {}
    applyThemeUiState();
}
function initTheme() {
    var saved = null;
    try {
        saved = localStorage.getItem('sociomart-theme');
    } catch (e) {}
    if (!saved && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        // Sensible default: respect the OS preference until the user chooses.
        saved = 'dark';
    }
    if (saved) {
        document.documentElement.setAttribute('data-theme', saved);
    }
    applyThemeUiState();
}

// ==================== Modal ====================

function openModal(html) {
    var root = $('#modalRoot');
    root.innerHTML = '<div class="modal-backdrop"><div class="modal">' + html + '</div></div>';
    root.hidden = false;
    root.querySelector('.modal-backdrop').addEventListener('click', function (e) {
        if (e.target === e.currentTarget) {
            closeModal();
        }
    });
}
function closeModal() {
    var root = $('#modalRoot');
    root.hidden = true;
    root.innerHTML = '';
}
function confirmModal(opts) {
    openModal(
        '<div class="modal-icon">' + (opts.icon || '⚠️') + '</div>' +
        '<h3>' + esc(opts.title) + '</h3>' +
        '<p>' + esc(opts.message) + '</p>' +
        '<div class="modal-actions">' +
        '<button class="btn btn-outline" id="modalCancel">' + esc(opts.cancelLabel || 'Cancel') + '</button>' +
        '<button class="btn btn-primary" id="modalOk">' + esc(opts.okLabel || 'Confirm') + '</button>' +
        '</div>'
    );
    $('#modalCancel').onclick = function () {
        closeModal();
        if (opts.onCancel) {
            opts.onCancel();
        }
    };
    $('#modalOk').onclick = function () {
        closeModal();
        if (opts.onOk) {
            opts.onOk();
        }
    };
}

// ==================== Bottom sheet (Screen 4A) ====================

function openSheet(html) {
    var root = $('#sheetRoot');
    root.innerHTML = '<div class="sheet-backdrop"></div><div class="sheet">' +
        '<div class="sheet-handle"></div>' + html + '</div>';
    root.hidden = false;
    root.querySelector('.sheet-backdrop').addEventListener('click', closeSheet);
}
function closeSheet() {
    var root = $('#sheetRoot');
    root.hidden = true;
    root.innerHTML = '';
}

// ==================== Single-Kitchen Cart (Spec 1.2) ====================
/**
 * The cart lives in localStorage and is bound to exactly ONE kitchen at a time.
 * Switching kitchens requires explicit confirmation — cancelling retains the
 * original cart untouched.
 */

function getCart() {
    try {
        return JSON.parse(localStorage.getItem('sociomart-cart')) || null;
    } catch (e) {
        return null;
    }
}
function saveCart(cart) {
    if (!cart || !cart.items || !cart.items.length) {
        localStorage.removeItem('sociomart-cart');
    } else {
        localStorage.setItem('sociomart-cart', JSON.stringify(cart));
    }
    updateCartBar();
}
function cartItemCount(cart) {
    cart = cart || getCart();
    if (!cart || !cart.items) return 0;
    return cart.items.reduce(function (s, i) {
        return s + i.qty;
    }, 0);
}
function cartTotal(cart) {
    cart = cart || getCart();
    if (!cart || !cart.items) return 0;
    return cart.items.reduce(function (s, i) {
        return s + i.price * i.qty;
    }, 0);
}
function clearCart() {
    saveCart(null);
}

function updateCartBar() {
    var bar = $('#viewOrderBar');
    if (!bar) return;
    var n = cartItemCount();
    var view = $('#view');
    var hasStickyFooter = view && view.querySelector('.sticky-footer-bar');
    bar.hidden = (n === 0) || !!hasStickyFooter;
    $('#vobCount').textContent = n;
    var word = $('#vobItemWord');
    if (word) word.textContent = (n === 1 ? 'item' : 'items');
    $('#vobTotal').textContent = money(cartTotal());
    if (view) {
        if (n === 0 || hasStickyFooter) view.classList.remove('has-cart-bar');
        else view.classList.add('has-cart-bar');
    }
}

/** Add an item, enforcing the single-kitchen constraint. onDone(committed) fires after. */
function addToCart(newItem, kitchen, onDone) {
    var cart = getCart();
    if (cart && cart.kitchenId !== kitchen.id) {
        // Spec 1.2: modal confirmation before clearing — cancel retains the cart.
        confirmModal({
            icon: '🍲',
            title: 'Switch kitchen?',
            message: 'You are moving to another kitchen. Your existing order will be cleared.',
            okLabel: 'Clear & Switch',
            cancelLabel: 'Keep My Cart',
            onOk: function () {
                clearCart();
                commitItem(newItem, kitchen);
                if (onDone) {
                    onDone(true);
                }
            },
            onCancel: function () {
                if (onDone) {
                    onDone(false);
                }
            }
        });
        return;
    }
    commitItem(newItem, kitchen);
    if (onDone) {
        onDone(true);
    }
}

function commitItem(newItem, kitchen) {
    var cart = getCart() || { kitchenId: kitchen.id, kitchenName: kitchen.displayName, items: [] };
    cart.kitchenId = kitchen.id;
    cart.kitchenName = kitchen.displayName;
    var existing = cart.items.find(function (i) {
        return i.productId === newItem.productId &&
            i.scheduledDate === newItem.scheduledDate &&
            i.scheduledSlot === newItem.scheduledSlot;
    });
    if (existing) {
        existing.qty += newItem.qty;
    } else {
        cart.items.push(newItem);
    }
    saveCart(cart);
    toast('Added to your order from ' + kitchen.displayName, 'success');
}

// ==================== Deferred Auth Gate (Spec 1.1) ====================
/**
 * Identity-bound actions (place order, toggle favourite, submit enquiry) call
 * requireAuth(). If the buyer is not logged in, the login modal opens and the
 * action resumes automatically after successful login.
 */

function requireAuth(onAuthenticated) {
    if (state.user) {
        return confirmSessionThen(onAuthenticated);
    }
    state.pendingAuthAction = onAuthenticated;
    openAuthModal();
    return Promise.resolve();
}

/**
 * Run the action only while the server still recognises this session.
 *
 * A truthy state.user is NOT proof of a live server session: the session can be
 * gone (server restart, idle expiry, invalidated elsewhere) while the tab still
 * holds the old identity. Re-running the action in that state just fails again
 * and the buyer sees a dead button, so confirm with the server first and fall
 * back to the login gate when the session is stale.
 *
 * Only the session probe is guarded here — errors raised by the action itself
 * must still reach the caller's own handler.
 */
async function confirmSessionThen(onAuthenticated) {
    var live = false;
    try {
        var me = await api('/api/auth/me');
        if (me && me.authenticated) {
            state.user = me;
            live = true;
        }
    } catch (e) {
        // Probe failed (network/parse): treat the session as unverified.
    }
    if (live) return onAuthenticated();
    // Stale or unknown session — clear the local identity and ask for login.
    state.user = null;
    state.pendingAuthAction = onAuthenticated;
    openAuthModal();
}

function openAuthModal() {
    openModal(
        '<div class="modal-icon">🔐</div>' +
        '<h3>Login required</h3>' +
        '<p>Enter your mobile number to continue. You can keep browsing freely.</p>' +
        '<form id="authForm" class="mt-3">' +
        '<div class="form-group">' +
        '<label class="form-label" for="authMobile">Mobile number</label>' +
        '<input class="form-input" id="authMobile" name="mobileNumber" inputmode="numeric" maxlength="10" placeholder="10-digit mobile number" required>' +
        '</div>' +
        '<button class="btn btn-primary btn-block" type="submit" id="authSubmit">Log in</button>' +
        '</form>'
    );
    $('#authForm').addEventListener('submit', function (e) {
        e.preventDefault();
        handleAuthLogin();
    });
}

async function handleAuthLogin() {
    var mobile = $('#authMobile').value.trim();
    if (!/^\d{10}$/.test(mobile)) {
        toast('Enter a valid 10-digit mobile number', 'error');
        return;
    }
    var btn = $('#authSubmit');
    btn.disabled = true;
    btn.innerHTML = '<span class="btn-spinner"></span> Logging in...';
    var me;
    try {
        me = await api('/api/auth/demo-login', { method: 'POST', body: { mobileNumber: mobile } });
    } catch (err) {
        btn.disabled = false;
        btn.textContent = 'Log in';
        toast('Login failed: ' + err.message, 'error');
        return;
    }
    state.user = me;
    closeModal();
    toast('Welcome, ' + (me.name || 'neighbour') + '!', 'success');
    var action = state.pendingAuthAction;
    state.pendingAuthAction = null;
    // Resume the deferred action outside the login try/catch: a failure here is
    // the action's own error and must be reported as such, not as a login error.
    try {
        if (action) {
            await action();
        } else if (typeof render === 'function') {
            await render();
        }
    } catch (err) {
        toast(err.message || 'Could not continue. Please try again.', 'error');
    }
}

/** Wrap an identity-bound action: run it, auto-open the auth gate on 401. */
async function withAuthGate(actionFn) {
    try {
        return await actionFn();
    } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
            // Hand the action to the auth gate. Awaited so a deferred failure
            // surfaces to our caller instead of becoming an unhandled rejection.
            return await requireAuth(actionFn);
        }
        throw err;
    }
}
