/**
 * SocioMart Admin App V1 — operational console.
 *
 * The eight primary sections are the product. Kitchens, Items/Offerings and
 * Enquiries still have working views because the Sellers, Buyers and Orders
 * screens link into them, but they are no longer top-level navigation. The
 * Diagnose / Health / Console routes are retained so an existing bookmark or a
 * developer link still resolves — they are simply not reachable from the normal
 * Admin navigation, which is the point of V1.
 */
var A = { me: null, role: null, loginMobile: null, trafficPeriod: 'today', kitchenFilter: '', dashPeriod: 'today', buyerAreaId: '', buyerSocietyId: '' };
// Resolved BEFORE the route table below. `adminAnalyticsView` used to be assigned
// further down the file with `var`, and a `var` is not initialised until execution
// reaches it - so while the route table was being built the '#/analytics' entry
// captured `undefined`, and clicking Analytics fell through to Home.
// adminTrafficView is a hoisted function declaration, and calling it only returns
// its inner view function, so this is safe to do here.
var adminAnalyticsView = adminTrafficView();
var adminRoutes = {
    // ---- Admin V1 primary sections ----
    '#/dashboard': adminHomeView,
    '#/approvals': adminPendingView,
    '#/sellers': adminSellersView,
    '#/buyers': adminBuyersView,
    '#/orders': adminOrdersView,
    '#/analytics': adminAnalyticsView,
    '#/locations': adminLocationsView,
    '#/exports': adminExportsView,
    // Admin V1 governance + drill-downs. Reached from the Dashboard ("Governance"
    // panel) and from the row-level View / Details buttons; they are deliberately
    // not extra top-level navigation items.
    '#/audit': adminAuditView,
    '#/retention': adminRetentionView,
    '#/seller-detail': adminSellerDetailView,
    '#/buyer-detail': adminBuyerDetailView,
    // ---- Retained, no longer primary navigation ----
    '#/kitchens': adminKitchensView,
    '#/offerings': adminOfferingsView,
    '#/enquiries': adminEnquiriesView,
    '#/diagnostics': adminDiagnosticsView,
    '#/health': adminHealthView,
    '#/console': adminConsoleView,
    // ---- Legacy aliases: old bookmarks resolve instead of dead-ending ----
    '#/home': adminHomeView,
    '#/pending': adminPendingView
};
function adminResolveRoute(hash) {
    if (adminRoutes[hash]) return { fn: adminRoutes[hash], arg: hash };
    return { fn: adminHomeView, arg: '#/dashboard' };
}
/**
 * Normalise the address bar to a real route.
 *
 * An unknown hash (typo, stale bookmark, old link) used to render the Home content
 * while leaving the bogus hash in the URL and with NO nav item highlighted, so the
 * console looked broken. We now resolve to the actual route and correct the URL.
 */
function adminNormaliseHash() {
    var hash = location.hash || '#/home';
    if (adminRoutes[hash]) return hash;
    return '#/home';
}
async function adminRender() {
    if (!A.role) { await adminGate(); return; }
    var hash = adminNormaliseHash();
    if (location.hash !== hash) {
        // replaceState avoids pushing a bogus entry onto the history stack.
        history.replaceState(null, '', hash);
    }
    var route = adminResolveRoute(hash);
    var view = viewEl();
    view.innerHTML = '<div class="page-loading"><div class="spinner"></div></div>';
    try {
        view.innerHTML = await route.fn(route.arg) || '';
        adminUpdateNav(hash);
        window.scrollTo(0, 0);
        adminRestoreFocus();
    } catch (err) {
        view.innerHTML = adminErrorView(err);
    }
}
/**
 * Error state with an explicit retry.
 *
 * Previously the whole view was replaced by a bare "Error" heading with the raw
 * message and no way to recover short of reloading the page. An empty result and a
 * failed request must look different, and the operator must be able to retry.
 */
function adminErrorView(err) {
    var msg = (err && err.message) ? err.message : 'Something went wrong.';
    return '<div class="view-enter"><div class="section-head admin-section-head">' +
        '<div><h1>Could not load this view</h1>' +
        '<p class="muted small">The data could not be fetched. Nothing was changed.</p></div></div>' +
        '<div class="card pad"><p class="admin-error-msg">' + esc(msg) + '</p>' +
        '<div class="admin-actions"><button class="btn btn-primary btn-block" type="button" ' +
        'data-action="admin-retry">Retry</button></div></div></div>';
}
function adminUpdateNav(hash) {
    $all('.nav-item').forEach(function (el) { el.classList.remove('active'); });
    var key = hash.replace(/^#\//, '').split('/')[0];
    // Admin V1 renamed two sections, so a legacy bookmark (#/home, #/pending) must
    // still light up the tab that now owns it rather than leaving no nav item
    // highlighted and making the console look broken.
    if (key === 'home') key = 'dashboard';
    if (key === 'pending') key = 'approvals';
    var el = document.querySelector('[data-nav="' + key + '"]');
    if (el) el.classList.add('active');
}
function adminNavigate(hash) { if (location.hash === hash) adminRender(); else location.hash = hash; }
// ==================== SEARCH ====================
/**
 * Debounced search state.
 *
 * Searching used to fire on every keystroke: the `keyup` handler re-rendered the
 * whole view, which DESTROYED the input that had focus. Typing "SM" into the order
 * search left a single "S" in the box and focus was lost, so the operator could
 * never enter a term. Now typing is debounced, and focus plus the caret position
 * are restored after the re-render.
 */
A.searchTerms = A.searchTerms || {};
A._searchTimer = null;

function adminSearchTerm(key) { return A.searchTerms[key] || ''; }

/** Debounced handler bound to a search box. */
function adminSearchHandler(key, id) {
    var input = document.getElementById(id);
    if (!input) return;
    A.searchTerms[key] = input.value;
    if (A._searchTimer) clearTimeout(A._searchTimer);
    A._searchTimer = setTimeout(function () { adminRender(); }, 250);
}

/** Re-focus the active search box and put the caret back where it was. */
function adminRestoreFocus() {
    var id = document.activeElement && document.activeElement.id;
    var remembered = A._focusId;
    var want = remembered || id;
    if (!want || want.indexOf('adminSearch') !== 0) return;
    var input = document.getElementById(want);
    if (!input) return;
    input.focus();
    var pos = typeof A._caretPos === 'number' ? A._caretPos : (input.value || '').length;
    try { input.setSelectionRange(pos, pos); } catch (e) { /* type without selection */ }
}

/**
 * Renders the standard search + filter bar used by the list screens.
 * `filters` is an array of { key, label } rendered as capsule buttons.
 */
function adminSearchBar(opts) {
    var term = adminSearchTerm(opts.key);
    var h = '<div class="admin-filters admin-searchbar">';
    h += '<label class="admin-search">' +
        '<span class="sr-only">Search ' + esc(opts.label || 'records') + '</span>' +
        '<span class="as-icon" aria-hidden="true">🔍</span>' +
        '<input type="search" class="form-input form-input-sm" id="adminSearch_' + esc(opts.key) + '" ' +
        'placeholder="' + esc(opts.placeholder || 'Search…') + '" value="' + esc(term) + '" ' +
        'autocomplete="off" spellcheck="false"></label>';
    (opts.filters || []).forEach(function (f) {
        h += '<button class="capsule' + (f.active ? ' active' : '') + '" type="button" ' +
            'data-action="' + esc(opts.filterAction) + '" data-key="' + esc(f.key) + '" data-value="' + esc(f.value) + '"' +
            (f.active ? ' aria-current="true"' : '') + '>' + esc(f.label) + '</button>';
    });
    h += '</div>';
    return h;
}

/** Case-insensitive "does any of these fields contain the term" match. */
function adminMatches(term, fields) {
    var q = (term || '').trim().toLowerCase();
    if (!q) return true;
    return fields.some(function (f) {
        return f !== null && f !== undefined && String(f).toLowerCase().indexOf(q) >= 0;
    });
}

/** Empty-state that distinguishes "no matches" from "nothing exists yet". */
function adminEmptyState(term, noun) {
    if ((term || '').trim()) {
        return '<div class="admin-empty">No ' + esc(noun) + ' match “' + esc(term.trim()) + '”.</div>';
    }
    return '<div class="admin-empty">No ' + esc(noun) + ' yet.</div>';
}
function greeting() { var h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; }
/** Readable date/time for admin rows, e.g. "7 Sep 2026, 10:42 AM". */
function adminDate(iso) {
    if (!iso) return '—';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return esc(String(iso));
    var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    var h12 = d.getHours() % 12 || 12;
    var ampm = d.getHours() < 12 ? 'AM' : 'PM';
    return d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear() +
        ', ' + h12 + ':' + String(d.getMinutes()).padStart(2, '0') + ' ' + ampm;
}

// ==================== AUTH GATE ====================
async function adminGate() {
    var me = null;
    try { me = await api('/api/auth/me'); } catch (e) { me = null; }
    if (me && me.authenticated && (me.role === 'ADMIN' || me.role === 'SUPER_ADMIN')) {
        A.me = me; A.role = me.role;
        showAdminApp();
        await adminRender();
    } else if (me && me.authenticated) {
        renderBlockedScreen(me.role || 'UNKNOWN');
    } else {
        renderLoginScreen();
    }
}
function showAdminApp() {
    var top = $('#adminTopbar'), nav = $('#adminNav');
    if (top) top.classList.remove('hidden');
    if (nav) nav.classList.remove('hidden');
    document.body.classList.add('admin-authed');
    adminUpdateNav(location.hash || '#/dashboard');
    var badge = $('#adminRoleBadge'), nm = $('#adminName');
    if (badge) { badge.textContent = A.role; badge.classList.toggle('super', A.role === 'SUPER_ADMIN'); }
    if (nm) nm.textContent = A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin';
}
function renderLoginScreen() {
    viewEl().innerHTML =
        '<div class="login-wrap"><div class="admin-login">' +
        '<div class="al-brand-row"><div class="al-brand">🛡️ SocioMart Admin</div>' +
        '<button class="icon-btn" type="button" data-action="toggle-theme" aria-label="Toggle theme">🌓</button></div>' +
        '<h2>Admin Sign In</h2>' +
        '<p class="muted small">Enter your mobile number to sign in. This console accepts ADMIN &amp; SUPER_ADMIN accounts.</p>' +
        '<div class="form-group"><label for="alMobile">Mobile number</label><input id="alMobile" inputmode="numeric" maxlength="10" placeholder="10-digit mobile" autocomplete="tel"></div>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="admin-login">Sign In</button>' +
        '<a class="al-back" href="/index.html">← Back to buyer app</a>' +
        '</div></div>';
}
function renderBlockedScreen(role) {
    viewEl().innerHTML =
        '<div class="blocked-wrap"><div class="admin-blocked">' +
        '<div class="ab-icon">🚫</div>' +
        '<h2>Not an Admin account</h2>' +
        '<p>You are signed in as <strong>' + esc(role) + '</strong>. The Admin console is restricted to ADMIN and SUPER_ADMIN accounts.</p>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="open-buyer">Open Buyer App</button>' +
        '<button class="btn btn-outline btn-block" type="button" data-action="logout">Logout</button>' +
        '</div></div>';
}

// ==================== ACTIONS ====================
/**
 * Guard against a double submission.
 *
 * Approve/Reject previously fired a POST per click; an impatient double click could
 * send two mutations before the list refreshed. The button is disabled and marked
 * aria-busy for the duration of the request.
 */
async function adminRunOnce(btn, fn) {
    if (btn && btn.disabled) return;
    if (btn) { btn.disabled = true; btn.setAttribute('aria-busy', 'true'); }
    try {
        await fn();
    } finally {
        if (btn && btn.isConnected) { btn.disabled = false; btn.removeAttribute('aria-busy'); }
    }
}

async function adminAction(action, t) {
    try {
        switch (action) {
            case 'admin-login': {
                var mobile = A.loginMobile || ($('#alMobile') ? $('#alMobile').value : '');
                if (!mobile || !/^[6-9]\d{9}$/.test(mobile)) throw new Error('Enter a valid 10-digit mobile number');
                A.loginMobile = mobile;
                var resp = await api('/api/auth/demo-login', { method: 'POST', body: { mobileNumber: mobile } });
                if (resp && resp.authenticated && (resp.role === 'ADMIN' || resp.role === 'SUPER_ADMIN')) {
                    A.me = resp; A.role = resp.role;
                    showAdminApp();
                    toast('Welcome, ' + (resp.name || 'Admin') + '!', 'success');
                    await adminRender();
                } else {
                    renderBlockedScreen(resp ? resp.role : 'UNKNOWN');
                }
                break;
            }
            case 'login-back': renderLoginScreen(); break;
            case 'toggle-theme': if (typeof toggleTheme === 'function') toggleTheme(); break;
            case 'logout':
                await api('/api/auth/logout', { method: 'POST' });
                A.me = null; A.role = null; A.loginMobile = null;
                document.body.classList.remove('admin-authed');
                location.href = '/index.html';
                break;
            case 'open-buyer': location.href = '/index.html'; break;
            case 'admin-order-axis-clear': {
                // Clearing resets every axis at once, so a filter can never be
                // stranded in a state the operator cannot see.
                A.orderFilters = {};
                await adminRender();
                break;
            }
            case 'go-tab': adminNavigate(t.dataset.hash); break;
            case 'admin-export': {
                // A real browser download, not an API call into JS: the CSV is a
                // file, and letting the browser save it keeps the generated-at
                // banner and the attachment filename intact.
                var url = adminExportUrl(t.dataset.domain);
                var a = document.createElement('a');
                a.href = url;
                a.download = '';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                break;
            }
            case 'approve-seller': {
                var id = Number(t.dataset.id);
                await adminRunOnce(t, async function () {
                    await api('/api/admin/sellers/' + id + '/approve', { method: 'POST' });
                    toast('Seller approved', 'success');
                });
                await adminRender();
                break;
            }
            case 'open-reject': {
                openModal(
                    '<h3>Reject ' + esc(t.dataset.name) + '?</h3>' +
                     '<p class="muted small">Add a reason (shown to the seller) — optional.</p>' +
                     '<div class="form-group"><textarea id="rejectReason" class="admin-textarea" rows="2" maxlength="200" placeholder="Reason (optional)"></textarea></div>' +
                    '<div class="modal-actions">' +
                    '<button class="btn btn-outline" type="button" data-action="cancel-reject">Cancel</button>' +
                    '<button class="btn btn-danger" type="button" data-action="confirm-reject" data-id="' + t.dataset.id + '" data-name="' + esc(t.dataset.name || 'seller') + '">Reject</button>' +
                    '</div>');
                break;
            }
            case 'cancel-reject': closeModal(); break;
            case 'confirm-reject': {
                var id = Number(t.dataset.id);
                var reason = $('#rejectReason') ? $('#rejectReason').value.trim() : '';
                var name = t.dataset.name || 'seller';
                closeModal();
                // Confirm before the consequential action, not after.
                var ok = window.confirm('Reject ' + name + '?' +
                    (reason ? '\n\nReason: ' + reason : '\n\nNo reason was given.'));
                if (!ok) break;
                await adminRunOnce(t, async function () {
                    await api('/api/admin/sellers/' + id + '/reject', { method: 'POST', body: { reason: reason || null } });
                    toast('Seller rejected', 'success');
                });
                await adminRender();
                break;
            }
            // ===== Seller detail drill-down (handover 7.1) =====
            case 'admin-open-seller-detail': {
                A.sellerDetailId = Number(t.dataset.id);
                adminNavigate('#/seller-detail');
                break;
            }
            case 'admin-back-sellers': adminNavigate('#/sellers'); break;
            case 'admin-open-buyer-detail': {
                A.buyerDetailId = Number(t.dataset.id);
                adminNavigate('#/buyer-detail');
                break;
            }
            case 'admin-back-buyers': adminNavigate('#/buyers'); break;
            case 'reactivate-seller': {
                // Handover 7.1: a suspended seller is restored through the same
                // Approve decision - no separate endpoint exists, and none is needed.
                var rId = Number(t.dataset.id);
                if (!window.confirm('Reactivate ' + (t.dataset.name || 'this seller') + '?\n\nTheir storefronts become visible to buyers again.')) break;
                await adminRunOnce(t, async function () {
                    await api('/api/admin/sellers/' + rId + '/approve', { method: 'POST' });
                    toast('Seller reactivated', 'success');
                });
                await adminRender();
                break;
            }
            /**
             * One modal serves every "reason required/optional" decision:
             * Request Changes, Suspend, Pause storefront, Remove storefront,
             * Block buyer and the internal Support note. The mode travels on the
             * buttons, so wording and endpoint are chosen by data rather than by
             * six near-identical code paths.
             */
            case 'open-admin-reason': {
                var mode = t.dataset.mode || '';
                var copy = {
                    requestChanges: { title: 'Request changes from ' + esc(t.dataset.name || 'seller'), hint: 'The seller stays in the approval queue and cannot serve until they resubmit.', label: 'What must change?', required: true, verb: 'Send request' },
                    suspend: { title: 'Suspend ' + esc(t.dataset.name || 'seller'), hint: 'Their storefronts stop serving buyers immediately. The seller can be reactivated later.', label: 'Reason (shown to the seller)', required: true, verb: 'Suspend' },
                    pause: { title: 'Pause storefront ' + esc(t.dataset.name || ''), hint: 'Buyers stop seeing this storefront until it is resumed. Nothing is deleted.', label: 'Internal note (optional)', required: false, verb: 'Pause' },
                    removeStorefront: { title: 'Remove storefront ' + esc(t.dataset.name || ''), hint: 'The storefront is withdrawn from the buyer app. This is a serious step - the seller is NOT deleted.', label: 'Reason (shown to the seller)', required: true, verb: 'Remove storefront' },
                    block: { title: 'Block ' + esc(t.dataset.name || 'buyer'), hint: 'A blocked buyer cannot place orders. Existing orders are kept.', label: 'Reason (internal)', required: true, verb: 'Block' },
                    note: { title: 'Support note - ' + esc(t.dataset.name || 'buyer'), hint: 'Internal only. Visible to Admins on this screen, never to the buyer.', label: 'Note', required: false, verb: 'Save note' }
                }[mode];
                if (!copy) { toast('Unknown action', 'error'); break; }
                openModal(
                    '<h3>' + copy.title + '</h3>' +
                    '<p class="muted small">' + copy.hint + '</p>' +
                    '<div class="form-group"><textarea id="adminReasonInput" class="admin-textarea" rows="3" maxlength="300" placeholder="' + copy.label + '"></textarea></div>' +
                    '<div class="muted tiny" style="margin-bottom:8px">' + copy.label + (copy.required ? ' - required' : ' - optional') + '</div>' +
                    '<div class="modal-actions">' +
                    '<button class="btn btn-outline" type="button" data-action="cancel-admin-reason">Cancel</button>' +
                    '<button class="btn btn-danger" type="button" data-action="confirm-admin-reason" data-mode="' + esc(mode) + '" data-id="' + esc(t.dataset.id || '') + '" data-name="' + esc(t.dataset.name || '') + '" data-required="' + copy.required + '">' + copy.verb + '</button>' +
                    '</div>');
                break;
            }
            case 'cancel-admin-reason': closeModal(); break;
            case 'confirm-admin-reason': {
                var rMode = t.dataset.mode;
                var rName = t.dataset.name || 'this record';
                var input = $('#adminReasonInput');
                var text = input ? input.value.trim() : '';
                if (t.dataset.required === 'true' && !text) {
                    // The modal stays open so the operator does not lose what they typed.
                    toast('A reason is required', 'error');
                    break;
                }
                var rId = Number(t.dataset.id);
                closeModal();
                var confirmCopy = {
                    requestChanges: 'Ask ' + rName + ' to change something?\n\nReason: ' + text,
                    suspend: 'Suspend ' + rName + '?\n\nReason: ' + text,
                    pause: 'Pause this storefront?\n\n' + (text ? 'Note: ' + text : 'No note given.'),
                    removeStorefront: 'Remove this storefront from the buyer app?\n\nReason: ' + text,
                    block: 'Block ' + rName + '?\n\nReason: ' + text,
                    note: 'Save this support note for ' + rName + '?'
                }[rMode];
                if (!window.confirm(confirmCopy || ('Confirm ' + rMode + '?'))) break;
                var calls = {
                    requestChanges: { url: '/api/admin/sellers/' + rId + '/request-changes', body: { reason: text }, done: 'Changes requested' },
                    suspend: { url: '/api/admin/sellers/' + rId + '/suspend', body: { reason: text }, done: 'Seller suspended' },
                    pause: { url: '/api/admin/storefronts/' + rId + '/pause', body: { note: text || null }, done: 'Storefront paused' },
                    removeStorefront: { url: '/api/admin/storefronts/' + rId + '/remove', body: { reason: text }, done: 'Storefront removed' },
                    block: { url: '/api/admin/buyers/' + rId + '/block', body: { reason: text }, done: 'Buyer blocked' },
                    note: { url: '/api/admin/buyers/' + rId + '/support-note', body: { note: text || null }, done: 'Support note saved' }
                }[rMode];
                if (!calls) { toast('Unknown action', 'error'); break; }
                await adminRunOnce(t, async function () {
                    await api(calls.url, { method: 'POST', body: calls.body });
                    toast(calls.done, 'success');
                });
                await adminRender();
                break;
            }
            case 'storefront-resume': {
                var sId = Number(t.dataset.id);
                if (!window.confirm('Resume this storefront?\n\nBuyers see it again straight away.')) break;
                await adminRunOnce(t, async function () {
                    await api('/api/admin/storefronts/' + sId + '/resume', { method: 'POST' });
                    toast('Storefront resumed', 'success');
                });
                await adminRender();
                break;
            }
            case 'buyer-unblock': {
                var bId = Number(t.dataset.id);
                if (!window.confirm('Unblock ' + (t.dataset.name || 'this buyer') + '?\n\nThey can place orders again.')) break;
                await adminRunOnce(t, async function () {
                    await api('/api/admin/buyers/' + bId + '/unblock', { method: 'POST' });
                    toast('Buyer unblocked', 'success');
                });
                await adminRender();
                break;
            }
            // ===== Governance: retention window (handover 12) =====
            case 'retention-set': {
                var daysField = $('#retentionDays');
                var days = daysField ? parseInt(daysField.value, 10) : NaN;
                if (isNaN(days) || days < 1) { toast('Enter a whole number of days (1 or more)', 'error'); break; }
                if (!window.confirm('Change the detailed-order retention window to ' + days + ' days?\n\nThe purge preview updates immediately; nothing is deleted right now.')) break;
                await adminRunOnce(t, async function () {
                    await api('/api/admin/retention', { method: 'POST', body: { retentionDays: days } });
                    toast('Retention set to ' + days + ' days', 'success');
                });
                await adminRender();
                break;
            }
            case 'admin-audit-domain': {
                A.auditDomain = t.dataset.value || '';
                await adminRender();
                break;
            }
            case 'admin-analytics-clear': {
                A.analyticsFilter = {};
                await adminRender();
                break;
            }
            case 'admin-order-filter': {
                A.orderFilter = t.dataset.filter || 'all';
                await adminRender();
                break;
            }
            case 'admin-seller-status': {
                A.sellerStatus = t.dataset.value || '';
                await adminRender();
                break;
            }
            case 'admin-offering-status': {
                A.offeringStatus = t.dataset.value || '';
                await adminRender();
                break;
            }
            case 'admin-enquiry-status': {
                A.enquiryStatus = t.dataset.value || '';
                await adminRender();
                break;
            }
            case 'admin-clear-search': {
                A.searchTerms = {};
                await adminRender();
                break;
            }
            case 'console-create-admin': {
                var nameEl = $('#consoleAdminName');
                var mobEl = $('#consoleAdminMobile');
                var name = nameEl ? nameEl.value.trim() : '';
                var mobile = mobEl ? mobEl.value.trim() : '';
                var errBox = $('#consoleAdminError');
                function showErr(msg) {
                    if (!errBox) return;
                    errBox.hidden = false;
                    errBox.textContent = msg;
                }
                // Client-side check first so the obvious mistake never round-trips;
                // the server validates independently and stays authoritative.
                if (!/^[6-9]\d{9}$/.test(mobile)) {
                    showErr('Enter a valid 10-digit mobile number.');
                    if (mobEl) mobEl.focus();
                    break;
                }
                if (errBox) errBox.hidden = true;
                // Creating an admin can change an existing account's role - confirm first.
                var okCreate = window.confirm('Create an Admin account for ' + mobile + '?'
                    + (name ? '\n\nName: ' + name : '')
                    + '\n\nIf this mobile already belongs to a Buyer, that account becomes an Admin.');
                if (!okCreate) break;
                await adminRunOnce(t, async function () {
                    var created = await api('/api/superadmin/admins', {
                        method: 'POST',
                        body: { name: name || null, mobileNumber: mobile }
                    });
                    toast('Admin ' + (created && created.name ? created.name : mobile) + ' created', 'success');
                });
                await adminRender();
                break;
            }
            case 'console-demote-admin': {
                var did = Number(t.dataset.id);
                var dname = t.dataset.name || 'this admin';
                var okDemote = window.confirm('Demote ' + dname + ' back to a Buyer?\n\n'
                    + 'They lose all Admin console access immediately. Their account is kept.');
                if (!okDemote) break;
                await adminRunOnce(t, async function () {
                    await api('/api/superadmin/admins/' + did, { method: 'DELETE' });
                    toast(dname + ' demoted to Buyer', 'success');
                });
                await adminRender();
                break;
            }
            case 'admin-retry': {
                await adminRender();
                break;
            }
            case 'admin-order-search': {
                // Kept for compatibility with the old keyup path; the debounced
                // input handler below is what runs now.
                adminSearchHandler('orders', 'adminSearch_orders');
                break;
            }
            case 'admin-order-detail': {
                var oid = t.dataset.id;
                // Pass the ID, not the record. This handler used to fetch the order
                // and then hand the resulting OBJECT to adminOrderDetailView(id),
                // which fetched '/api/admin/orders/' + id a second time - producing
                // /api/admin/orders/[object Object] (HTTP 400) and leaving the list
                // on screen. The view does its own fetch.
                viewEl().innerHTML = await adminOrderDetailView(oid);
                break;
            }
            case 'admin-back-orders': {
                A.orderDetailId = null;
                location.hash = '#/orders';
                break;
            }
            case 'admin-seller-sort': {
                // Same column toggles direction; a new column starts descending,
                // because the useful default for every numeric column here is
                // "biggest first".
                var key = t.dataset.key;
                if (A.sellerSort === key) A.sellerSortDir = A.sellerSortDir === 'asc' ? 'desc' : 'asc';
                else { A.sellerSort = key; A.sellerSortDir = 'desc'; }
                adminRender();
                break;
            }
            case 'admin-dash-period': {
                A.dashPeriod = t.dataset.period || 'today';
                await adminRender();
                break;
            }
            case 'admin-traffic-period': {
                A.trafficPeriod = t.dataset.period || 'today';
                await adminRender();
                break;
            }
            case 'admin-set-kitchen-filter': {
                A.kitchenFilter = t.dataset.filter || '';
                await adminRender();
                break;
            }
            case 'admin-edit-service-areas': {
                var kid = Number(t.dataset.kid);
                var kitchen = await api('/api/admin/kitchens');
                var k = kitchen.find(function (x) { return x.id === kid; });
                if (!k) { toast('Kitchen not found', 'error'); break; }
                // Coverage is edited from the Admin-owned Area/Society master and
                // submitted as Society IDs. Only active records are selectable.
                var adminAreas = [];
                try { adminAreas = await api('/api/admin/coverage-options') || []; } catch (eSoc) { adminAreas = []; }
                var coveredIds = (k.servedSocietyIds || []).map(Number);
                var preselectArea = '';
                adminAreas.forEach(function (a) {
                    (a.societies || []).forEach(function (s) {
                        if (coveredIds.indexOf(Number(s.id)) !== -1 && !preselectArea) preselectArea = String(a.id);
                    });
                });
                A.adminAreas = adminAreas;
                A.adminCoverageAreaId = preselectArea;
                A.adminCoverageIds = coveredIds;
                // Same restore rule as the seller picker: the restored Area counts as
                // already loaded so the first render keeps the persisted IDs instead of
                // preselecting every active Society in that Area.
                A.adminCoverageLoadedAreaId = preselectArea;
                var adminAreaOpts = adminAreas.map(function (a) {
                    return '<option value="' + esc(a.id) + '"' + (String(a.id) === preselectArea ? ' selected' : '') + '>' + esc(a.name) + '</option>';
                }).join('');
                var h = '<div class="view-enter"><div class="page-head"><h1>Service Areas</h1></div>' +
                    '<p class="muted small">Manage delivery societies for <strong>' + esc(k.displayName || k.name) + '</strong>.</p>' +
                    '<div class="form-group"><label class="form-label">Select an area, then tick its societies</label>' +
                    (adminAreas.length ? '' : '<div class="muted small" style="margin-bottom:6px">No active areas exist yet. Add one under Manage Areas &amp; Societies first.</div>') +
                    '<div class="form-row-2" style="margin-top:8px"><select class="form-input" id="adminCoverageAreaSelect" data-action="admin-select-coverage-area" aria-label="Area"' + (adminAreas.length ? '' : ' disabled') + '><option value="">Select area</option>' + adminAreaOpts + '</select></div>' +
                    '<div id="adminCoverageSocietyList" style="margin-top:8px"></div>' +
                    '</div>' +
                    '<div class="admin-actions">' +
                    '<button class="btn btn-primary btn-block" type="button" data-action="admin-save-service-areas" data-kid="' + kid + '">Save Changes</button>' +
                    '<button class="btn btn-secondary btn-block" type="button" data-action="admin-cancel-edit-service-areas">Cancel</button>' +
                    '</div></div>';
                viewEl().innerHTML = h;
                renderAdminCoverageSocieties();
                break;
            }
            case 'admin-add-service-area': { break; }
            case 'admin-remove-service-area': { break; }
            case 'admin-save-service-areas': {
                var kid2 = Number(t.dataset.kid);
                // Society IDs are authoritative. When no area is chosen the fields are
                // omitted so the save cannot silently clear an existing coverage.
                var body = {};
                if (A.adminCoverageAreaId) {
                    body.areaId = Number(A.adminCoverageAreaId);
                    body.societyIds = (A.adminCoverageIds || []).map(Number);
                }
                await api('/api/admin/kitchens/' + kid2 + '/service-areas', { method: 'PATCH', body: body });
                toast('Service areas saved', 'success');
                await adminRender();
                break;
            }
            case 'admin-cancel-edit-service-areas': {
                await adminRender();
                break;
            }
            // ---- Visibility diagnostic (read-only) ----
            case 'admin-diag-focus': {
                A.diagKitchenId = t.dataset.kitchenId ? String(t.dataset.kitchenId) : '';
                adminNavigate('#/diagnostics');
                break;
            }
            case 'admin-diag-buyer-open': {
                A.diagBuyerId = t.dataset.id ? String(t.dataset.id) : '';
                A.diagKitchenId = '';
                adminNavigate('#/diagnostics');
                break;
            }
            // ---- Manage Areas & Societies ----
            case 'loc-add-area': {
                var areaNameInput = $('#locNewAreaName');
                var areaName = areaNameInput && areaNameInput.value ? areaNameInput.value.trim() : '';
                if (!areaName) { toast('Enter an area name', 'error'); break; }
                // Guard against a double submit while the request is in flight.
                if (t.disabled) break;
                t.disabled = true;
                try {
                    await api('/api/admin/areas', { method: 'POST', body: { name: areaName } });
                    toast('Area added', 'success');
                    await adminRender();
                } finally { t.disabled = false; }
                break;
            }
            case 'loc-rename-begin': {
                A.locRenaming = { type: t.dataset.type, id: Number(t.dataset.id) };
                await adminRender();
                break;
            }
            case 'loc-rename-cancel': {
                A.locRenaming = null;
                await adminRender();
                break;
            }
            case 'loc-rename-save': {
                var field = $('#' + (t.dataset.field || ''));
                var newName = field && field.value ? field.value.trim() : '';
                if (!newName) { toast('Enter a name', 'error'); break; }
                if (t.disabled) break;
                t.disabled = true;
                try {
                    var rtype = t.dataset.type, rid = Number(t.dataset.id);
                    var url = rtype === 'area' ? '/api/admin/areas/' + rid : '/api/admin/societies/' + rid;
                    await api(url, { method: 'PATCH', body: { name: newName } });
                    A.locRenaming = null;
                    toast('Renamed', 'success');
                    await adminRender();
                } finally { t.disabled = false; }
                break;
            }
            case 'loc-toggle': {
                var ttype = t.dataset.type, tid = Number(t.dataset.id);
                var tname = t.dataset.label || '';
                var activate = t.dataset.active === 'true';
                var prompt = activate
                    ? 'Re-enable "' + tname + '"?\n\nIt becomes selectable again for new buyer and seller choices.'
                    : 'Disable "' + tname + '"?\n\nExisting records that reference it are kept - only NEW selections are blocked.';
                if (!window.confirm(prompt)) break;
                if (t.disabled) break;
                t.disabled = true;
                try {
                    var turl = ttype === 'area' ? '/api/admin/areas/' + tid : '/api/admin/societies/' + tid;
                    await api(turl, { method: 'PATCH', body: { active: !activate } });
                    toast(activate ? 'Re-enabled' : 'Disabled', 'success');
                    await adminRender();
                } finally { t.disabled = false; }
                break;
            }
            case 'loc-add-society': {
                var aid = Number(t.dataset.aid);
                var socField = $('#locNewSociety_' + aid);
                var socName = socField && socField.value ? socField.value.trim() : '';
                if (!socName) { toast('Enter a society name', 'error'); break; }
                if (t.disabled) break;
                t.disabled = true;
                try {
                    await api('/api/admin/societies', { method: 'POST', body: { areaId: aid, name: socName } });
                    toast('Society added', 'success');
                    await adminRender();
                } finally { t.disabled = false; }
                break;
            }
        }
    } catch (err) { toast(err.message, 'error'); }
}

// ==================== VIEWS ====================
async function adminHomeView() {
    // Handover 4/17: date selector (Today / Last 5 Days / Custom) at the top.
    // The window is resolved SERVER-side, so every card below is computed over
    // exactly the same set of orders and can never disagree with its neighbour.
    var dashPeriod = A.dashPeriod || 'today';
    var data = await api('/api/admin/dashboard?date=' + encodeURIComponent(dashPeriod));
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + greeting() + ', ' +
        esc(A.me ? (A.me.name || A.me.mobileNumber || 'Admin') : 'Admin') +
        '</h1><p class="muted small">Marketplace overview — live shared-database figures</p></div></div>';
    h += '<div class="admin-filters" style="margin-bottom:12px">';
    [['today', 'Today'], ['last5', 'Last 5 Days']].forEach(function (p) {
        h += '<button class="capsule' + (dashPeriod === p[0] ? ' active' : '') + '" type="button" ' +
            'data-action="admin-dash-period" data-period="' + p[0] + '"' +
            (dashPeriod === p[0] ? ' aria-current="true"' : '') + '>' + p[1] + '</button>';
    });
    h += '<label class="capsule" style="cursor:pointer">' +
        '<span class="sr-only">Custom date</span>' +
        '<input type="date" class="form-input form-input-sm" id="adminDashCustomDate" ' +
        'style="width:auto;border:0;background:transparent" ' +
        'value="' + esc(/^\d{4}-\d{2}-\d{2}$/.test(dashPeriod) ? dashPeriod : '') + '" ' +
        'aria-label="Custom dashboard date"></label>';
    h += '<span class="muted tiny" style="align-self:center">Showing: <strong>' +
        esc(data.selectedPeriod || 'Today') + '</strong></span>';
    h += '</div>';
    var win = data.selectedPeriod || 'Today';

    // Operational stat grid — every figure comes from /api/admin/dashboard (no hardcoding)
    h += '<div class="dash-grid">';
    function dashCard(icon, num, label, sub, hash) {
        var open = hash ? '<a class="dash-card" href="' + hash + '" data-action="go-tab" data-hash="' + hash + '">'
                        : '<div class="dash-card">';
        var close = hash ? '</a>' : '</div>';
        return open +
            '<div class="dc-top"><span class="dc-icon">' + icon + '</span><span class="dc-num">' + num + '</span></div>' +
            '<div class="dc-label">' + label + '</div>' +
            (sub ? '<div class="dc-sub">' + sub + '</div>' : '') +
            close;
    }
    // Handover 4/17 layout:
    //   row 1: Traffic | Orders | Recorded Order Value | Pending Approvals
    //   row 2: Buyers   | Sellers | Attention Needed
    // Two grids keep the two rows visually distinct instead of one long strip.
    h += '<div class="dash-grid">';
    h += dashCard('&#128200;', (data.marketplaceViewsInPeriod || 0) + ' / ' + (data.storefrontViewsInPeriod || 0),
        'Traffic', 'marketplace / storefront views in ' + esc(win), '#/analytics');
    h += dashCard('&#128230;', data.ordersInPeriod || 0, 'Orders',
        esc(win) + ' · all time: ' + (data.totalOrders || 0), '#/orders');
    // Deliberately NOT "Revenue": SocioMart does not process buyer payments, so
    // this is the value of orders recorded on the marketplace and nothing more.
    h += dashCard('&#128202;', money(data.recordedOrderValueInPeriod || 0), 'Recorded Order Value',
        esc(win) + ' · all time: ' + money(data.totalOrderValue || 0), '#/orders');
    h += dashCard('&#9203;', data.pendingSellers || 0, 'Pending Approvals',
        'seller applications awaiting review', '#/approvals');
    h += '</div>';

    h += '<div class="dash-grid">';
    h += dashCard('&#128100;', data.totalBuyers || 0, 'Buyers',
        (data.buyersInPeriod || 0) + ' ordering in ' + esc(win), '#/buyers');
    h += dashCard('&#128101;', data.totalSellers || 0, 'Sellers',
        (data.approvedSellers || 0) + ' approved · ' + (data.pendingSellers || 0) + ' pending', '#/sellers');
    // Highlighted only when something is actually open, so the dashboard does not
    // cry wolf on a healthy day. Count comes from the same attention model the
    // Pending Actions panel renders below.
    h += dashCard('&#9888;', data.attentionNeeded || 0, 'Attention Needed',
        (data.attentionNeeded || 0) === 0 ? 'nothing waiting on the Admin team' : 'items need a decision',
        '#/orders');
    h += '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recorded Order Value</h3>' +
        '<p class="muted tiny" style="margin:0 0 8px">Total value of orders placed on the marketplace (not platform revenue).</p>';
    h += '<div class="flex gap-2 wrap"><div class="flex-1 min-140"><div class="muted small">Total</div><div class="font-700 font-size-2">' + money(data.totalOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1 min-140"><div class="muted small">Today</div><div class="font-700 font-size-2">' + money(data.todayOrderValue || 0) + '</div></div>';
    h += '<div class="flex-1 min-140"><div class="muted small">This Month</div><div class="font-700 font-size-2">' + money(data.monthOrderValue || 0) + '</div></div></div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Payment Status</h3>' +
        '<div class="flex gap-2 wrap">' +
        '<div class="flex-1 min-140"><span class="pill pill-green">● Paid</span><div class="font-700 green mt-1">' + (data.paidCount || 0) + ' orders</div><div class="muted small">' + money(data.paidValue || 0) + '</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-amber">● Will Pay Later</span><div class="font-700 orange mt-1">' + (data.willPayLaterCount || 0) + ' orders</div><div class="muted small">' + money(data.willPayLaterValue || 0) + '</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-grey">● Pending</span><div class="font-700 mt-1">' + (data.pendingPaymentCount || 0) + ' orders</div><div class="muted small">' + money(data.pendingPaymentValue || 0) + '</div></div>' +
        '</div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Order Status</h3>' +
        '<p class="muted tiny" style="margin:0 0 12px">Every order sits in exactly one of these buckets; they sum to the total.</p>' +
        '<div class="flex gap-2 wrap">' +
        '<div class="flex-1 min-140"><span class="pill pill-amber">Awaiting seller</span><div class="font-700 orange mt-1">' + (data.ordersAwaitingSellerConfirmation || 0) + ' orders</div><div class="muted small">placed, not yet confirmed</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-blue">In fulfilment</span><div class="font-700 mt-1">' + (data.ordersInFulfilment || 0) + ' orders</div><div class="muted small">confirmed / ready</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-green">Fulfilled</span><div class="font-700 green mt-1">' + (data.ordersFulfilled || 0) + ' orders</div><div class="muted small">delivered / completed</div></div>' +
        '<div class="flex-1 min-140"><span class="pill pill-red">Cancelled</span><div class="font-700 red mt-1">' + (data.ordersCancelled || 0) + ' orders</div><div class="muted small">inventory restored</div></div>' +
        '</div></div>';

    // Recent Orders: the newest recorded orders, straight from the Admin orders
    // endpoint so the dashboard and the Orders screen can never disagree.
    try {
        var recent = await api('/api/admin/orders') || [];
        h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recent Orders</h3>';
        if (!recent.length) {
            h += '<div class="admin-empty admin-empty-inline">No orders recorded yet.</div>';
        } else {
            h += '<ul class="attention-list">';
            recent.slice(0, 8).forEach(function (o) {
                h += '<li><a class="attention-row" href="#/orders" data-action="go-tab" data-hash="#/orders">' +
                    '<span class="att-label">' + esc(o.orderNumber || ('Order ' + o.id)) + '</span>' +
                    '<span class="att-count">' + money(o.totalAmount || 0) + '</span>' +
                    '<span class="att-go" aria-hidden="true">&rsaquo;</span></a></li>';
            });
            h += '</ul>';
        }
        h += '</div>';
    } catch (eRec) {
        // Recent orders are supporting detail; omit the card rather than fail the
        // whole dashboard.
    }

    // Pending Actions: ONE compact panel, served by /api/admin/attention so the
    // dashboard does not re-derive these counts from four different payloads.
    var attention = [];
    try {
        attention = await api('/api/admin/attention') || [];
    } catch (eAtt) {
        // Attention is a convenience panel; if it cannot load we say so rather
        // than rendering an empty list that reads as "nothing needs attention".
        attention = null;
    }
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Pending Actions</h3>';
    if (attention === null) {
        h += '<div class="admin-empty admin-empty-inline">Pending actions could not be loaded.</div>';
    } else if (!attention.length) {
        h += '<div class="admin-empty admin-empty-inline">&#127881; Nothing is waiting on the Admin team right now.</div>';
    } else {
        h += '<ul class="attention-list">';
        attention.forEach(function (a) {
            h += '<li><a class="attention-row" href="' + a.hash + '" data-action="go-tab" data-hash="' + a.hash + '">' +
                '<span class="att-label">' + esc(a.label) + '</span>' +
                '<span class="att-count">' + a.count + '</span>' +
                '<span class="att-go" aria-hidden="true">&rsaquo;</span></a></li>';
        });
        h += '</ul>';
    }
    h += '</div>';

    // Governance shortcuts (Admin V1): the audit trail and the data-retention
    // control are reached from here rather than from the eight primary nav items.
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Governance</h3>' +
        '<p class="muted tiny" style="margin:0 0 10px">Every Admin decision is recorded; the retention window controls how long detailed orders are kept.</p>' +
        '<div class="admin-actions">' +
        '<button class="btn btn-secondary" type="button" data-action="go-tab" data-hash="#/audit">Admin action history</button>' +
        '<button class="btn btn-secondary" type="button" data-action="go-tab" data-hash="#/retention">Data retention</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

/**
 * Admin V1 Exports.
 *
 * <p>A manual Download screen - no scheduled jobs, no reporting framework. Each
 * export hits the existing {@code /api/admin/exports/<domain>.csv} endpoint,
 * which applies the SAME filters the Orders screen uses, so a downloaded file
 * can never contain more rows than the operator is looking at.</p>
 *
 * <p>The Orders export carries the filter controls inline; Sellers, Buyers and
 * Analytics are whole-of-platform snapshots.</p>
 */
async function adminExportsView() {
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Exports</h1>' +
        '<p class="muted small">Download operational data as CSV. Every file is generated now and carries its own timestamp.</p>' +
        '</div></div>';

    // The Orders screen holds the monitoring axes; the export form mirrors them,
    // so the file matches exactly what the operator last filtered to.
    var of = A.orderFilters || {};
    function expOpt(value, label, selected) {
        return '<option value="' + value + '"' + (selected === value ? ' selected' : '') + '>' + label + '</option>';
    }
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Orders export</h3>' +
        '<p class="muted tiny" style="margin:0 0 10px">Apply filters here and the downloaded file contains exactly the matching orders.</p>' +
        '<div class="flex gap-2 wrap">' +
        '<input class="form-input" type="date" id="expDate" aria-label="Order date" value="' + esc(of.date || '') + '" style="max-width:180px">' +
        '<select class="form-input" id="expCategory" aria-label="Category" style="max-width:180px">' +
        expOpt('', 'All categories', of.category || '') + expOpt('KITCHEN', 'Kitchen', of.category || '') +
        expOpt('HOMEMADE_PRODUCTS', 'Homemade Products', of.category || '') + '</select>' +
        '<select class="form-input" id="expPayment" aria-label="Payment" style="max-width:180px">' +
        expOpt('', 'All payments', of.payment || '') + expOpt('PAID', 'Paid', of.payment || '') +
        expOpt('PENDING', 'Pending', of.payment || '') + expOpt('WILL_PAY_LATER', 'Will pay later', of.payment || '') + '</select>' +
        '<select class="form-input" id="expDelivery" aria-label="Delivery" style="max-width:180px">' +
        expOpt('', 'All delivery', of.delivery || '') + expOpt('delivered', 'Delivered', of.delivery || '') +
        expOpt('not_delivered', 'Not delivered', of.delivery || '') + '</select>' +
        '<select class="form-input" id="expStatus" aria-label="Order status" style="max-width:180px">' +
        expOpt('', 'All statuses', of.status || '') + expOpt('ORDERED', 'Ordered', of.status || '') +
        expOpt('CONFIRMED', 'Confirmed', of.status || '') + expOpt('READY', 'Ready', of.status || '') +
        expOpt('DELIVERED', 'Delivered', of.status || '') + expOpt('COMPLETED', 'Completed', of.status || '') +
        expOpt('CANCELLED', 'Cancelled', of.status || '') + expOpt('DRAFT', 'Draft', of.status || '') + '</select>' +
        '</div>' +
        '<div class="admin-actions"><button class="btn btn-primary" type="button" data-action="admin-export" data-domain="orders">Download Orders CSV</button></div>' +
        '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Platform snapshots</h3>' +
        '<p class="muted tiny" style="margin:0 0 10px">Whole-of-platform figures, exported as they stand right now.</p>' +
        '<div class="admin-actions">' +
        '<button class="btn" type="button" data-action="admin-export" data-domain="sellers">Sellers CSV</button>' +
        '<button class="btn" type="button" data-action="admin-export" data-domain="buyers">Buyers CSV</button>' +
        '<button class="btn" type="button" data-action="admin-export" data-domain="analytics">Analytics CSV</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

/** Builds the export URL, carrying only the filters the operator actually set. */
function adminExportUrl(domain) {
    var url = '/api/admin/exports/' + encodeURIComponent(domain) + '.csv';
    if (domain !== 'orders') return url;
    var q = [];
    var date = $('#expDate'), cat = $('#expCategory'), pay = $('#expPayment'), del = $('#expDelivery'), st = $('#expStatus');
    if (date && date.value) q.push('date=' + encodeURIComponent(date.value));
    if (cat && cat.value) q.push('category=' + encodeURIComponent(cat.value));
    if (pay && pay.value) q.push('payment=' + encodeURIComponent(pay.value));
    if (del && del.value) q.push('delivery=' + encodeURIComponent(del.value));
    // Order-status axis, added with the monitoring filters so the download and
    // the Orders screen can always be narrowed the same way.
    if (st && st.value) q.push('status=' + encodeURIComponent(st.value));
    return q.length ? url + '?' + q.join('&') : url;
}

async function adminPendingView() {
    var list = await api('/api/admin/sellers/pending');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Pending Approvals</h1><p class="muted small">' + (list ? list.length : 0) + ' seller(s) awaiting your decision</p></div></div>';
    if (!list || !list.length) {
        return h + '<div class="admin-empty">🎉 All caught up — no pending seller approvals.</div>';
    }
    list.forEach(function (s) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(s.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(s.name || 'Unknown') + '</div>' +
            '<div class="sr-meta">📱 ' + esc(s.mobileNumber || '—') + ' · Registered ' + adminDate(s.registeredAt) + '</div>' +
            '</div>' +
            '<div class="sr-actions">' +
            '<button class="btn btn-success btn-sm" type="button" data-action="approve-seller" data-id="' + s.id + '">Approve</button>' +
            // Handover 7.1: Request Changes is a first-class decision, not a rejection.
            '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="requestChanges" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Request Changes</button>' +
            '<button class="btn btn-outline btn-sm" type="button" data-action="open-reject" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Reject</button>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminBuyersView() {
    // Handover 8 search (name / mobile / area / society / ORDER ID) is resolved
    // SERVER-side, so an order number pasted from a support ticket finds the
    // right buyer, and location narrowing uses stable ids, not display text.
    var term = adminSearchTerm('buyers');
    var q = [];
    if (term) q.push('search=' + encodeURIComponent(term));
    if (A.buyerAreaId) q.push('areaId=' + encodeURIComponent(A.buyerAreaId));
    if (A.buyerSocietyId) q.push('societyId=' + encodeURIComponent(A.buyerSocietyId));
    var list = await api('/api/admin/buyers' + (q.length ? '?' + q.join('&') : ''));
    var rows = list || [];
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Buyers</h1><p class="muted small">' +
        rows.length + ' of ' + (list ? list.length : 0) + ' registered buyers</p></div></div>';
    h += adminSearchBar({ key: 'buyers', label: 'buyers', placeholder: 'Name, mobile, society, order ID…' });
    if (!rows.length) {
        return h + adminEmptyState(term, 'buyers') + '</div>';
    }
    rows.forEach(function (b) {
        var statusPill = b.blocked
            ? '<span class="pill pill-red">Blocked</span>'
            : '<span class="pill pill-green">Active</span>';
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(b.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(b.name || 'Unknown') + ' ' + statusPill + '</div>' +
            '<div class="sr-meta">📱 ' + esc(b.mobileNumber || '—') + ' · ' + esc(b.society || '') + (b.building ? ', ' + esc(b.building) : '') + '</div>' +
            '<div class="sr-meta">Orders: ' + (b.orderCount || 0) + ' · Value: ' + money(b.totalOrderValue || 0) + ' · Favourites: ' + (b.favouriteKitchens || 0) + '</div>' +
            (b.blocked && b.blockedReason ? '<div class="sr-meta sr-reason">⚠ ' + esc(b.blockedReason) + '</div>' : '') +
            '</div>' +
            // Handover 8: Buyer detail (profile/location, orders, account state,
            // support note) plus the Block / Unblock control. The visibility
            // diagnostic stays as the read-only observation tool.
            '<div class="sr-actions">' +
            '<button class="btn btn-secondary btn-sm" type="button" data-action="admin-open-buyer-detail" data-id="' + b.id + '">Details</button>' +
            (b.blocked
                ? '<button class="btn btn-success btn-sm" type="button" data-action="buyer-unblock" data-id="' + b.id + '" data-name="' + esc(b.name || 'Buyer') + '">Unblock</button>'
                : '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="block" data-id="' + b.id + '" data-name="' + esc(b.name || 'Buyer') + '">Block</button>') +
            '<button class="btn btn-secondary btn-sm" type="button" ' +
            'data-action="admin-diag-buyer-open" data-id="' + b.id + '">Check visibility</button></div>' +
            '</div>';
    });
    h += '</div>';
    return h;
}

async function adminSellersView() {
    // One request per render. An earlier version also called /api/admin/sellers a
    // second time to build the status counts, which doubled the request and made
    // the tab fail when the second call raced. Counts now come from the same array,
    // matching every other list screen.
    var list = await api('/api/admin/sellers');
    var status = A.sellerStatus || '';
    var term = adminSearchTerm('sellers');
    var counts = { '': (list || []).length, PENDING: 0, APPROVED: 0, REJECTED: 0, SUSPENDED: 0, CHANGES_REQUESTED: 0 };
    (list || []).forEach(function (s) {
        if (counts[s.sellerApprovalStatus] !== undefined) counts[s.sellerApprovalStatus]++;
    });
    var rows = (list || []).filter(function (s) {
        if (status && (s.sellerApprovalStatus || '') !== status) return false;
        return adminMatches(term, [s.name, s.mobileNumber, s.kitchenName, s.area, s.sellerApprovalStatus]);
    });
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Sellers</h1><p class="muted small">' +
        rows.length + ' of ' + (list ? list.length : 0) + ' sellers</p></div></div>';
    h += adminSearchBar({
        key: 'sellers',
        label: 'sellers',
        placeholder: 'Name, mobile, kitchen…',
        filterAction: 'admin-seller-status',
        filters: [
            { label: 'All (' + counts[''] + ')', value: '', active: status === '' },
            { label: 'Pending (' + counts.PENDING + ')', value: 'PENDING', active: status === 'PENDING' },
            { label: 'Approved (' + counts.APPROVED + ')', value: 'APPROVED', active: status === 'APPROVED' },
            { label: 'Rejected (' + counts.REJECTED + ')', value: 'REJECTED', active: status === 'REJECTED' },
            { label: 'Changes Requested (' + counts.CHANGES_REQUESTED + ')', value: 'CHANGES_REQUESTED', active: status === 'CHANGES_REQUESTED' },
            { label: 'Suspended (' + counts.SUSPENDED + ')', value: 'SUSPENDED', active: status === 'SUSPENDED' },
        ],
    });
    if (!rows.length) {
        return h + adminEmptyState(term, 'sellers') + '</div>';
    }
    rows.forEach(function (s) {
        var st = s.sellerApprovalStatus || '';
        // Handover 7.1: "Request Changes" is an open decision state, exactly like
        // Pending and Rejected - the seller cannot serve until it is resolved.
        var needsAction = st === 'PENDING' || st === 'REJECTED' || st === 'CHANGES_REQUESTED';
        // The decision set adapts to the current state: open applications get
        // Approve / Request Changes / Reject; an approved seller can be Suspended;
        // a suspended seller is restored with Reactivate (the same endpoint as
        // Approve). View is always available.
        var actions = '<div class="sr-actions">' +
            '<button class="btn btn-secondary btn-sm" type="button" data-action="admin-open-seller-detail" data-id="' + s.id + '">View</button>';
        if (needsAction) {
            actions += '<button class="btn btn-success btn-sm" type="button" data-action="approve-seller" data-id="' + s.id + '">Approve</button>' +
                '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="requestChanges" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Request Changes</button>' +
                '<button class="btn btn-outline btn-sm" type="button" data-action="open-reject" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Reject</button>';
        } else if (st === 'APPROVED') {
            actions += '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="suspend" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Suspend</button>';
        } else if (st === 'SUSPENDED') {
            actions += '<button class="btn btn-success btn-sm" type="button" data-action="reactivate-seller" data-id="' + s.id + '" data-name="' + esc(s.name || 'Seller') + '">Reactivate</button>';
        }
        actions += '</div>';
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">' + esc(String(s.name || '?').charAt(0).toUpperCase()) + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(s.name || 'Unknown') + ' <span class="pill pill-' + (st === 'APPROVED' ? 'green' : (st === 'PENDING' || st === 'CHANGES_REQUESTED') ? 'amber' : 'grey') + '">' + esc(st || '') + '</span></div>' +
            '<div class="sr-meta">📱 ' + esc(s.mobileNumber || '—') + ' · ' + esc(s.kitchenName || 'No kitchen') + ' · ' + esc(s.area || '') + '</div>' +
            '<div class="sr-meta">Live: ' + (s.liveOfferings || 0) + '/' + (s.totalOfferings || 0) + ' offerings · Registered ' + adminDate(s.createdAt) + '</div>' +
            // A seller's statusReason is persisted and returned by /api/admin/sellers,
            // but nothing in the Admin UI rendered them - so the reason a seller was
            // rejected or suspended could not be audited anywhere. Surface it inline.
            (s.statusReason ? '<div class="sr-meta sr-reason">⚠ ' + esc(s.statusReason) + '</div>' : '') +
            // The row only ever describes the first kitchen; say so when there are more.
            (s.kitchenCount > 1 ? '<div class="sr-meta">🍳 ' + s.kitchenCount + ' kitchens (showing the first)</div>' : '') +
            '</div>' +
            actions +
            '</div>';
    });
    h += '</div>';
    return h;
}

async function adminKitchensView() {
    var filter = A.kitchenFilter || '';
    var url = '/api/admin/kitchens' + (filter ? '?sellerType=' + encodeURIComponent(filter) : '');
    var list = await api(url);
    var term = adminSearchTerm('kitchens');
    var rows = (list || []).filter(function (k) {
        return adminMatches(term, [k.displayName, k.name, k.sellerName, k.area, k.serviceAreas]);
    });
    var title = filter === 'HOMEMADE_PRODUCTS' ? 'Homemade Stores' : filter === 'KITCHEN' ? 'Kitchens' : 'All Kitchens';
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + esc(title) + '</h1><p class="muted small">' +
        rows.length + ' of ' + (list ? list.length : 0) + ' stores</p></div></div>';
    h += adminSearchBar({
        key: 'kitchens',
        label: 'stores',
        placeholder: 'Store, seller, service area…',
        filterAction: 'admin-set-kitchen-filter',
        filters: [
            { label: 'All', value: '', active: !filter },
            { label: 'Homemade Products', value: 'HOMEMADE_PRODUCTS', active: filter === 'HOMEMADE_PRODUCTS' },
            { label: 'Kitchens', value: 'KITCHEN', active: filter === 'KITCHEN' },
        ],
    });
    if (!rows.length) {
        return h + adminEmptyState(term, 'stores') + '</div>';
    }
    rows.forEach(function (k) {
        var areas = k.serviceAreas || k.area || '';
        var areaText = areas ? areas.split(',').map(function (a) { return a.trim(); }).filter(Boolean).join(', ') : '';
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🏪</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(k.displayName || k.name) + ' <span class="pill pill-' + (k.hasLiveOfferings ? 'green' : 'grey') + '">' + (k.hasLiveOfferings ? 'Live' : 'No live items') + '</span></div>' +
            '<div class="sr-meta">Seller: ' + esc(k.sellerName || '—') + ' · ' + esc(k.area || '') + '</div>' +
            '<div class="sr-meta">Offerings: ' + (k.liveOfferings || 0) + ' live / ' + (k.totalOfferings || 0) + ' total</div>' +
            (areaText ? '<div class="sr-meta">Service Areas: ' + esc(areaText) + '</div>' : '<div class="sr-meta muted small">No service areas configured</div>') +
            // Availability is a real operational fact; coverage is the Admin control.
            '<div class="sr-meta">' + (k.availableToday ? '🟢 Available today' : '⚪ Not available today') +
            ' · ' + (k.hasLiveOfferings ? 'has live items' : 'no live items') + '</div>' +
            // Handover 7.3: Admin-side storefront state, surfaced where it matters.
            (k.removed ? '<div class="sr-meta sr-reason">🚫 Removed from the buyer app by Admin</div>' : '') +
            (k.paused ? '<div class="sr-meta sr-reason">⏸ Paused by Admin — buyers cannot see this storefront</div>' : '') +
            '<div class="mt-1"><button class="btn btn-secondary btn-sm" type="button" data-action="admin-edit-service-areas" data-kid="' + k.id + '">Manage Service Areas</button> ' +
            '<button class="btn btn-secondary btn-sm" type="button" data-action="admin-diag-focus" data-kitchen-id="' + k.id + '">Diagnose</button> ' +
            // Handover 7.3 storefront controls. Pause and Resume are the same
            // switch; Remove is the serious step and always asks for a reason.
            (k.paused
                ? '<button class="btn btn-success btn-sm" type="button" data-action="storefront-resume" data-id="' + k.id + '" data-name="' + esc(k.displayName || k.name || '') + '">Resume</button> '
                : '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="pause" data-id="' + k.id + '" data-name="' + esc(k.displayName || k.name || '') + '">Pause</button> ') +
            '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="removeStorefront" data-id="' + k.id + '" data-name="' + esc(k.displayName || k.name || '') + '">Remove</button>' +
            '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOfferingsView() {
    var list = await api('/api/admin/offerings');
    var term = adminSearchTerm('offerings');
    var status = A.offeringStatus || '';
    var rows = (list || []).filter(function (p) {
        if (status && (p.status || '') !== status) return false;
        return adminMatches(term, [p.name, p.kitchenName, p.sellerName, p.status, p.priceUnit]);
    });
    var counts = { '': (list || []).length, LIVE: 0, PRE_ORDER: 0, SOLD_OUT: 0 };
    (list || []).forEach(function (p) {
        if (counts[p.status] !== undefined) counts[p.status]++;
    });
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Offerings</h1><p class="muted small">' +
        rows.length + ' of ' + (list ? list.length : 0) + ' offerings</p></div></div>';
    h += adminSearchBar({
        key: 'offerings',
        label: 'offerings',
        placeholder: 'Offering, kitchen, seller…',
        filterAction: 'admin-offering-status',
        filters: [
            { label: 'All (' + counts[''] + ')', value: '', active: status === '' },
            { label: 'Live (' + counts.LIVE + ')', value: 'LIVE', active: status === 'LIVE' },
            { label: 'Pre-order (' + counts.PRE_ORDER + ')', value: 'PRE_ORDER', active: status === 'PRE_ORDER' },
            { label: 'Sold out (' + counts.SOLD_OUT + ')', value: 'SOLD_OUT', active: status === 'SOLD_OUT' },
        ],
    });
    if (!rows.length) {
        return h + adminEmptyState(term, 'offerings') + '</div>';
    }
    rows.forEach(function (p) {
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🍽️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(p.name || '') + ' <span class="pill pill-' + (p.status === 'LIVE' ? 'green' : p.status === 'PRE_ORDER' ? 'blue' : p.status === 'SOLD_OUT' ? 'grey' : 'grey') + '">' + esc(p.status || '') + '</span></div>' +
            '<div class="sr-meta">' + esc(p.kitchenName || '') + (p.sellerName ? ' · ' + esc(p.sellerName) : '') + ' · ' + money(p.price || 0) + (p.priceUnit ? ' / ' + esc(p.priceUnit) : '') + (p.category ? ' · ' + esc(p.category) : '') + '</div>' +
            // maxQuantity/bookedQuantity are returned by /api/admin/offerings but were never
            // rendered, so an admin could see only the remainder and not how much was booked.
            '<div class="sr-meta">' + (p.availableDate ? '📅 ' + esc(p.availableDate) + ' ' : '') + (p.cutoffTime ? '⏰ ' + esc(p.cutoffTime) + ' ' : '') + 'Qty: ' + (p.remainingQuantity != null ? p.remainingQuantity : '∞') + (p.maxQuantity != null ? ' (booked ' + (p.bookedQuantity || 0) + ' of ' + p.maxQuantity + ')' : '') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOrdersView() {
    var filter = A.orderFilter || 'all';
    var search = adminSearchTerm('orders');
    A.orderSearch = search; // keep the server-side search param in step
    // Admin V1: the monitoring axes are held in one place and sent as independent
    // query parameters. The backend composes them with AND, so clearing one never
    // silently drops another.
    var f = A.orderFilters || {};
    var qs = '?filter=' + encodeURIComponent(filter) + '&search=' + encodeURIComponent(search);
    if (f.date) qs += '&date=' + encodeURIComponent(f.date);
    if (f.category) qs += '&category=' + encodeURIComponent(f.category);
    if (f.payment) qs += '&payment=' + encodeURIComponent(f.payment);
    if (f.delivery) qs += '&delivery=' + encodeURIComponent(f.delivery);
    if (f.status) qs += '&status=' + encodeURIComponent(f.status);
    if (f.societyId) qs += '&societyId=' + encodeURIComponent(f.societyId);
    if (f.areaId) qs += '&areaId=' + encodeURIComponent(f.areaId);
    // Seller / buyer drill-down axes (handover 6): the same params the detail
    // screens and the export use, so one filter language covers every surface.
    if (f.sellerId) qs += '&sellerId=' + encodeURIComponent(f.sellerId);
    if (f.buyerId) qs += '&buyerId=' + encodeURIComponent(f.buyerId);
    // The seller / buyer axes need their option lists. Requests run together; a
    // failing option list only empties its own dropdown, it never blanks the table.
    var results = await Promise.all([
        api('/api/admin/orders' + qs),
        api('/api/admin/sellers').catch(function () { return []; }),
        api('/api/admin/buyers').catch(function () { return []; }),
        // Area/Society option lists for the two new filter axes. Each degrades to
        // an empty list on its own, so a location failure never blanks Orders.
        api('/api/admin/locations').catch(function () { return null; })
    ]);
    var list = results[0];
    var sellerOptions = results[1] || [];
    var buyerOptions = results[2] || [];
    var locations = results[3] || null;
    var areaOptions = locations ? (locations.areas || []) : [];
    // Societies follow the chosen Area so the pair can never contradict itself;
    // with no Area chosen every society is offered.
    var societyOptions = [];
    areaOptions.forEach(function (a) {
        if (!f.areaId || String(a.id) === String(f.areaId)) {
            (a.societies || []).forEach(function (s) { societyOptions.push(s); });
        }
    });
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Orders</h1><p class="muted small">' + (list ? list.length : 0) + ' orders</p></div></div>';
    h += adminSearchBar({
        key: 'orders',
        label: 'orders',
        placeholder: 'Order number, buyer, kitchen…',
        filterAction: 'admin-order-filter',
        filters: [
            { label: 'All', value: 'all', active: filter === 'all' },
            { label: 'Last 3 Days', value: 'last3days', active: filter === 'last3days' },
        ],
    });

    // Admin V1 monitoring filters. Each <select>/<input> is independent; the
    // backend ANDs them, so any combination is meaningful.
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Filters</h3>' +
        '<div class="flex gap-2 wrap">' +
        '<input class="form-input" type="date" id="ordDate" data-action="admin-order-axis" data-axis="date" aria-label="Order date" style="max-width:170px">' +
        '<select class="form-input" id="ordCategory" data-action="admin-order-axis" data-axis="category" aria-label="Category" style="max-width:170px">' +
        '<option value="">All categories</option><option value="KITCHEN">Kitchen</option>' +
        '<option value="HOMEMADE_PRODUCTS">Homemade Products</option></select>' +
        '<select class="form-input" id="ordPayment" data-action="admin-order-axis" data-axis="payment" aria-label="Payment" style="max-width:170px">' +
        '<option value="">All payments</option><option value="PAID">Paid</option>' +
        '<option value="PENDING">Pending</option><option value="WILL_PAY_LATER">Will pay later</option></select>' +
        '<select class="form-input" id="ordDelivery" data-action="admin-order-axis" data-axis="delivery" aria-label="Delivery" style="max-width:170px">' +
        '<option value="">All delivery</option><option value="delivered">Delivered</option>' +
        '<option value="not_delivered">Not delivered</option></select>' +
        '<select class="form-input" id="ordStatus" data-action="admin-order-axis" data-axis="status" aria-label="Order status" style="max-width:170px">' +
        '<option value="">All statuses</option><option value="ORDERED">Ordered</option>' +
        '<option value="CONFIRMED">Confirmed</option><option value="READY">Ready</option>' +
        '<option value="DELIVERED">Delivered</option><option value="COMPLETED">Completed</option>' +
        '<option value="CANCELLED">Cancelled</option><option value="DRAFT">Draft</option></select>' +
        // Drill-down axes: pick a seller or a buyer and the list narrows to their
        // orders; combined with the other axes the backend ANDs them all.
        '<select class="form-input" id="ordSeller" data-action="admin-order-axis" data-axis="sellerId" aria-label="Seller" style="max-width:200px">' +
        '<option value="">All sellers</option>' + sellerOptions.map(function (s) {
            return '<option value="' + s.id + '"' + (String(f.sellerId || '') === String(s.id) ? ' selected' : '') + '>' + esc(s.name || ('Seller ' + s.id)) + '</option>';
        }).join('') + '</select>' +
        '<select class="form-input" id="ordBuyer" data-action="admin-order-axis" data-axis="buyerId" aria-label="Buyer" style="max-width:200px">' +
        '<option value="">All buyers</option>' + buyerOptions.map(function (b) {
            return '<option value="' + b.id + '"' + (String(f.buyerId || '') === String(b.id) ? ' selected' : '') + '>' + esc(b.name || ('Buyer ' + b.id)) + '</option>';
        }).join('') + '</select>' +
        // Handover 5/9: Area and Society are first-class order filters. The
        // backend already matched on the buyer's stable ids; these controls make
        // that reachable. Society options follow the chosen Area so the pair can
        // never contradict itself.
        '<select class="form-input" id="ordArea" data-action="admin-order-axis" data-axis="areaId" aria-label="Area" style="max-width:180px">' +
        '<option value="">All areas</option>' + (areaOptions || []).map(function (a) {
            return '<option value="' + a.id + '"' + (String(f.areaId || '') === String(a.id) ? ' selected' : '') + '>' + esc(a.name) + '</option>';
        }).join('') + '</select>' +
        '<select class="form-input" id="ordSociety" data-action="admin-order-axis" data-axis="societyId" aria-label="Society" style="max-width:200px">' +
        '<option value="">All societies</option>' + (societyOptions || []).map(function (s) {
            return '<option value="' + s.id + '"' + (String(f.societyId || '') === String(s.id) ? ' selected' : '') + '>' + esc(s.name) + '</option>';
        }).join('') + '</select>' +
        '<button class="btn" type="button" data-action="admin-order-axis-clear">Clear filters</button>' +
        '</div></div>';

    if (!list || !list.length) {
        return h + adminEmptyState(search, 'orders') + '</div>';
    }
    list.forEach(function (o) {
        var os = o.orderStatus || '';
        var ps = o.paymentStatus || '';
        var osPill = os === 'ORDERED' ? '<span class="pill pill-amber">🟠 ' + esc(os) + '</span>'
            : os === 'CONFIRMED' ? '<span class="pill pill-green">🟢 ' + esc(os) + '</span>'
            : os === 'READY' ? '<span class="pill pill-blue">🔵 ' + esc(os) + '</span>'
            : os === 'DELIVERED' ? '<span class="pill pill-grey">✓ ' + esc(os) + '</span>'
            : os === 'COMPLETED' ? '<span class="pill pill-green">✓ ' + esc(os) + '</span>'
            : os === 'CANCELLED' ? '<span class="pill pill-red">🔴 ' + esc(os) + '</span>'
            : '<span class="pill pill-grey">' + esc(os) + '</span>';
        var psPill = ps === 'PAID' ? '<span class="pill pill-green">● Paid</span>'
            : ps === 'WILL_PAY_LATER' ? '<span class="pill pill-amber">● Will Pay Later</span>'
            : ps === 'PENDING' ? '<span class="pill pill-grey">● Pending</span>'
            : (ps ? '<span class="pill pill-grey">' + esc(ps) + '</span>' : '');
        // Delivery is the SELLER's own record on the shared Order row. Admin only
        // displays it - there is deliberately no Admin-side delivery control, so
        // the console can never become a second delivery system.
        var dsPill = o.deliveryStatus === 'DELIVERED'
            ? '<span class="pill pill-grey">✓ Delivered</span>'
            : '<span class="pill pill-amber">Not delivered</span>';
        var catLabel = o.category === 'HOMEMADE_PRODUCTS' ? 'Homemade' : 'Kitchen';
        h += '<div class="seller-row" data-action="admin-order-detail" data-id="' + o.id + '">' +
            '<div class="sr-avatar">📦</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">#' + esc(o.orderNumber || String(o.id)) + ' · ' + esc(o.kitchenName || '') + '</div>' +
            '<div class="sr-meta">Buyer: ' + esc(o.buyerName || '—') + ' · ' + esc(o.buyerMobile || '') + '</div>' +
            '<div class="sr-meta os-badges">' + osPill + ' ' + psPill + ' ' + dsPill +
                ' <span class="pill pill-grey">' + esc(catLabel) + '</span>' +
                ' <strong>' + money(o.totalAmount || 0) + '</strong></div>' +
            '<div class="sr-meta">' + adminDate(o.orderTime || o.createdAt) + (o.society ? ' · ' + esc(o.society) : '') + '</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

async function adminOrderDetailView(id) {
    var o = await api('/api/admin/orders/' + id);
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Order #' + esc(o.orderNumber || String(o.id)) + '</h1><p class="muted small">' + adminDate(o.orderTime || o.createdAt) + '</p></div></div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Customer</h3>';
    h += '<p><strong>' + esc(o.buyerName || '—') + '</strong> · ' + esc(o.buyerMobile || '') + '</p>';
    h += '<p class="muted small">' + esc(o.buyerSociety || '') + (o.buyerBuilding ? ', ' + esc(o.buyerBuilding) : '') + (o.buyerFlat ? ', Flat ' + esc(o.buyerFlat) : '') + '</p>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Kitchen & Seller</h3>';
    h += '<p><strong>Kitchen:</strong> ' + esc(o.kitchenName || '—') + ' (ID: ' + o.kitchenId + ')</p>';
    h += '<p><strong>Seller:</strong> ' + esc(o.sellerName || '—') + ' (ID: ' + o.sellerId + ')</p>';
    h += '<p class="muted small">' + esc(o.kitchenSociety || '') + (o.kitchenBuilding ? ', ' + esc(o.kitchenBuilding) : '') + '</p>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Items</h3>';
    (o.items || []).forEach(function (it) {
        h += '<div class="flex items-center gap-2" style="padding:8px 0;border-bottom:1px solid #eee;">';
        h += '<div class="flex-1"><strong>' + esc(it.productName || 'Item') + '</strong></div>';
        h += '<div class="muted small">Qty: ' + it.quantity + '</div>';
        h += '<div class="muted small">Unit Price: ' + money(it.price || 0) + '</div>';
        h += '<div class="muted small">Line Total: ' + money(it.total || 0) + '</div>';
        h += '</div>';
    });
    h += '<div class="total-row" style="margin-top:12px;"><span>Order Total</span><span>' + money(o.totalAmount || 0) + '</span></div>';
    h += '</div>';
    h += '<div class="card pad card-mb">';
    h += '<h3>Status</h3>';
    h += '<p><strong>Payment:</strong> ' + esc(o.paymentStatus || '—') + '</p>';
    h += '<p><strong>Order:</strong> ' + esc(o.orderStatus || '—') + '</p>';
    if (o.customInstructions) {
        h += '<p class="muted small"><strong>Note:</strong> ' + esc(o.customInstructions) + '</p>';
    }
    h += '</div>';
    h += '<button class="btn btn-secondary btn-block" data-action="admin-back-orders">← Back to Orders</button>';
    h += '</div>';
    return h;
}

async function adminEnquiriesView() {
    var list = await api('/api/admin/enquiries');
    var term = adminSearchTerm('enquiries');
    var status = A.enquiryStatus || '';
    var rows = (list || []).filter(function (e) {
        if (status && (e.status || '') !== status) return false;
        return adminMatches(term, [e.kitchenName, e.userName, e.message, e.status]);
    });
    var counts = { '': (list || []).length };
    (list || []).forEach(function (e) { counts[e.status] = (counts[e.status] || 0) + 1; });
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Enquiries</h1><p class="muted small">' +
        rows.length + ' of ' + (list ? list.length : 0) + ' enquiries</p></div></div>';
    h += adminSearchBar({
        key: 'enquiries',
        label: 'enquiries',
        placeholder: 'Kitchen, buyer, message…',
        filterAction: 'admin-enquiry-status',
        filters: [
            { label: 'All (' + counts[''] + ')', value: '', active: status === '' },
            { label: 'Awaiting response (' + (counts.WAITING_FOR_RESPONSE || 0) + ')', value: 'WAITING_FOR_RESPONSE', active: status === 'WAITING_FOR_RESPONSE' },
            { label: 'Seller responded (' + (counts.SELLER_RESPONDED || 0) + ')', value: 'SELLER_RESPONDED', active: status === 'SELLER_RESPONDED' },
        ],
    });
    if (!rows.length) {
        return h + adminEmptyState(term, 'enquiries') + '</div>';
    }
    rows.forEach(function (e) {
        var es = e.status || '';
        var esPill = es === 'WAITING_FOR_RESPONSE' ? '<span class="pill pill-amber">⏳ Awaiting response</span>'
            : es === 'SELLER_RESPONDED' ? '<span class="pill pill-green">✓ Seller responded</span>'
            : (es ? '<span class="pill pill-grey">' + esc(es) + '</span>' : '');
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">✉️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(e.kitchenName || 'Kitchen') + ' ' + esPill + '</div>' +
            '<div class="sr-meta">From: ' + esc(e.userName || 'Buyer') + ' · ' + adminDate(e.createdAt) + '</div>' +
            '<div class="sr-meta eq-message">“' + esc(e.message || '') + '”</div>' +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

/**
 * Manage Areas & Societies — the Admin-owned location master.
 *
 * <p>Reads GET /api/admin/locations (the full tree, inactive records included so
 * they can be re-enabled) and drives the add / rename / enable-disable actions
 * against /api/admin/areas and /api/admin/societies. Disabling is a soft flag:
 * master records are never deleted, so buyers, sellers and historical orders
 * keep resolving. The buyer and seller dropdowns only ever receive the active
 * subset through their own endpoints.</p>
 */
async function adminLocationsView() {
    var data = await api('/api/admin/locations') || {};
    var areas = data.areas || [];
    var renaming = A.locRenaming || null;

    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Manage Areas &amp; Societies</h1>' +
        '<p class="muted small">' +
        (data.activeAreaCount || 0) + ' active of ' + (data.areaCount || 0) + ' areas · ' +
        (data.activeSocietyCount || 0) + ' active of ' + (data.societyCount || 0) + ' societies' +
        '</p></div></div>';

    // ---- Add a new Area ----
    h += '<div class="card pad card-mb">' +
        '<div class="form-group" style="margin-bottom:0">' +
        '<label class="form-label" for="locNewAreaName">New area</label>' +
        '<div class="form-row-2">' +
        '<input class="form-input" id="locNewAreaName" maxlength="160" placeholder="e.g. Charholi">' +
        '<button class="btn btn-primary" type="button" data-action="loc-add-area">Add Area</button>' +
        '</div></div></div>';

    if (!areas.length) {
        h += '<div class="admin-empty">No areas yet. Add the first area above — new buyer and seller choices' +
            ' only appear once an area (and a society under it) exists.</div></div>';
        return h;
    }

    areas.forEach(function (area) {
        var renamingArea = renaming && renaming.type === 'area' && renaming.id === area.id;
        h += '<div class="card pad card-mb">';

        // ---- Area header row ----
        if (renamingArea) {
            h += '<div class="sr-name">📍 Rename area</div>' +
                '<div class="form-row-2" style="margin-top:8px">' +
                '<input class="form-input" id="locRenameArea_' + area.id + '" maxlength="160" value="' + esc(area.name) + '">' +
                '<div class="admin-actions" style="display:flex;gap:8px">' +
                '<button class="btn btn-primary btn-sm" type="button" data-action="loc-rename-save" ' +
                'data-type="area" data-id="' + area.id + '" data-field="locRenameArea_' + area.id + '">Save</button>' +
                '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-rename-cancel">Cancel</button>' +
                '</div></div>';
        } else {
            h += '<div class="seller-row loc-row" style="border:none;padding-bottom:4px">' +
                '<div class="sr-avatar">📍</div>' +
                '<div class="sr-body">' +
                '<div class="sr-name">' + esc(area.name) +
                (area.active ? '' : ' <span class="pill pill-grey">Disabled</span>') + '</div>' +
                '<div class="sr-meta">' + (area.societyCount || 0) + ' societies · added ' + adminDate(area.createdAt) + '</div>' +
                // Handover 11 usage counts, so an operator can see which
                // locations are genuinely in use before disabling one.
                '<div class="sr-meta">&#128100; ' + (area.buyerCount || 0) + ' buyers · ' +
                '&#127978; ' + (area.sellerCount || 0) + ' sellers</div>' +
                '</div>' +
                '<div class="admin-actions" style="display:flex;gap:8px;flex-wrap:wrap">' +
                '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-rename-begin" ' +
                'data-type="area" data-id="' + area.id + '">Rename</button>' +
                '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-toggle" ' +
                'data-type="area" data-id="' + area.id + '" data-label="' + esc(area.name) + '" ' +
                'data-active="' + (area.active ? 'true' : 'false') + '">' +
                (area.active ? 'Disable' : 'Re-enable') + '</button>' +
                '</div></div>';
        }
        // ---- Societies under this Area ----
        var societies = area.societies || [];
        if (!societies.length) {
            h += '<div class="muted small" style="padding:8px 0">No societies yet in this area.</div>';
        }
        societies.forEach(function (soc) {
            var renamingSoc = renaming && renaming.type === 'society' && renaming.id === soc.id;
            if (renamingSoc) {
                h += '<div class="form-row-2" style="margin-top:8px">' +
                    '<input class="form-input" id="locRenameSociety_' + soc.id + '" maxlength="160" value="' + esc(soc.name) + '">' +
                    '<div class="admin-actions" style="display:flex;gap:8px">' +
                    '<button class="btn btn-primary btn-sm" type="button" data-action="loc-rename-save" ' +
                    'data-type="society" data-id="' + soc.id + '" data-field="locRenameSociety_' + soc.id + '">Save</button>' +
                    '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-rename-cancel">Cancel</button>' +
                    '</div></div>';
                return;
            }
            h += '<div class="seller-row loc-row" style="border:none;padding-bottom:4px">' +
                '<div class="sr-avatar">🏠</div>' +
                '<div class="sr-body">' +
                '<div class="sr-name">' + esc(soc.name) +
                (soc.active ? '' : ' <span class="pill pill-grey">Disabled</span>') + '</div>' +
                '<div class="sr-meta">added ' + adminDate(soc.createdAt) + '</div>' +
                // Per-society usage. A brand-new society reads 0 buyers / 0
                // sellers, which is the visible proof of handover 11's rule
                // that sellers must opt in rather than inherit new societies.
                '<div class="sr-meta">&#128100; ' + (soc.buyerCount || 0) + ' buyers · ' +
                '&#127978; ' + (soc.sellerCount || 0) + ' sellers</div>' +
                '</div>' +
                '<div class="admin-actions" style="display:flex;gap:8px;flex-wrap:wrap">' +
                '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-rename-begin" ' +
                'data-type="society" data-id="' + soc.id + '">Rename</button>' +
                '<button class="btn btn-secondary btn-sm" type="button" data-action="loc-toggle" ' +
                'data-type="society" data-id="' + soc.id + '" data-label="' + esc(soc.name) + '" ' +
                'data-active="' + (soc.active ? 'true' : 'false') + '">' +
                (soc.active ? 'Disable' : 'Re-enable') + '</button>' +
                '</div></div>';
        });

        // ---- Add a Society to this Area ----
        h += '<div class="form-row-2" style="margin-top:8px">' +
            '<input class="form-input" id="locNewSociety_' + area.id + '" maxlength="160" placeholder="New society name">' +
            '<button class="btn btn-secondary" type="button" data-action="loc-add-society" data-aid="' + area.id + '">' +
            'Add Society</button></div>';

        h += '</div>';
    });

    h += '</div>';
    return h;
}

function adminPlaceholderView(title, copy, icon) {
    return async function () {
        return '<div class="view-enter">' +
            '<div class="section-head admin-section-head"><div><h1>' + title + '</h1><p class="muted small">' + copy + '</p></div></div>' +
            '<div class="placeholder-note"><div class="font-size-2 mb-2">' + icon + '</div><strong>' + title + '</strong> — arrives in a later increment.</div></div>';
    };
}

// adminSellersView is the real registry backed by GET /api/admin/sellers —
// it was previously shadowed by a placeholder, dead-ending the Sellers tab
// despite a fully working backend. The real view is defined above.
// adminAnalyticsView is initialised before the route table (see the top of this
// file) so that '#/analytics' resolves to a real view function.
// adminConsoleView is the real Super Admin console (admin accounts) defined above.
// Feature flags, seller grants and platform settings are intentionally NOT exposed
// there: the backend endpoints exist but the business rules do not.

function adminTrafficView() {
    return async function () {
        var period = A.trafficPeriod || 'today';
        // Seller performance and recorded order value share one filter set
        // (date / area / society), the same axes the Orders screen exposes.
        var af = A.analyticsFilter || {};
        var fq = [];
        if (af.date) fq.push('date=' + encodeURIComponent(af.date));
        if (af.areaId) fq.push('areaId=' + encodeURIComponent(af.areaId));
        if (af.societyId) fq.push('societyId=' + encodeURIComponent(af.societyId));
        var fqs = fq.length ? '?' + fq.join('&') : '';
        // One round of fetches; each section degrades on its own instead of
        // blanking the whole screen.
        var results = await Promise.all([
            api('/api/admin/traffic?period=' + encodeURIComponent(period)).catch(function () { return null; }),
            api('/api/admin/seller-analytics' + fqs).catch(function () { return []; }),
            api('/api/admin/recorded-order-value' + fqs).catch(function () { return null; }),
            api('/api/admin/coverage-options').catch(function () { return []; })
        ]);
        var data = results[0];
        var sellers = results[1] || [];
        var value = results[2];
        var areas = results[3] || [];

        var h = '<div class="view-enter"><div class="section-head admin-section-head"><div><h1>Analytics</h1>' +
            '<p class="muted small">Traffic, seller performance and recorded order value — all measured from real marketplace activity</p></div></div>';

        // ---- Traffic (period axis) ----
        h += '<h2 class="font-700 mb-2">Traffic</h2>' +
            '<div class="admin-filters">' +
            '<button class="btn btn-sm ' + (period === 'today' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="today">Today</button>' +
            '<button class="btn btn-sm ' + (period === 'week' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="week">This Week</button>' +
            '<button class="btn btn-sm ' + (period === 'month' ? 'btn-primary' : 'btn-secondary') + '" data-action="admin-traffic-period" data-period="month">This Month</button>' +
            '</div>' +
            '<div id="trafficContent">' + (data ? '' : '<div class="admin-empty">Traffic analytics could not be loaded.</div>') + '</div>';
        if (data) {
            // Fill the container after adminRender has placed this markup.
            setTimeout(function () { renderTrafficContent(data, period); }, 0);
        }
        // ---- Shared filters (date / area / society) ----
        var socOpts = [];
        (areas || []).forEach(function (a) {
            if (!af.areaId || String(a.id) === String(af.areaId)) {
                (a.societies || []).forEach(function (s) { socOpts.push(s); });
            }
        });
        h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Filters</h3>' +
            '<p class="muted tiny" style="margin:0 0 10px">Applies to seller performance and recorded order value below. Clear a control to drop that constraint.</p>' +
            '<div class="flex gap-2 wrap">' +
            '<input class="form-input" type="date" id="anDate" data-action="admin-analytics-axis" data-axis="date" aria-label="Date" value="' + esc(af.date || '') + '" style="max-width:170px">' +
            '<select class="form-input" id="anArea" data-action="admin-analytics-axis" data-axis="areaId" aria-label="Area" style="max-width:200px">' +
            '<option value="">All areas</option>' + (areas || []).map(function (a) {
                return '<option value="' + a.id + '"' + (String(af.areaId || '') === String(a.id) ? ' selected' : '') + '>' + esc(a.name) + '</option>';
            }).join('') + '</select>' +
            // The society list follows the chosen area so the pair stays consistent.
            '<select class="form-input" id="anSociety" data-action="admin-analytics-axis" data-axis="societyId" aria-label="Society" style="max-width:220px">' +
            '<option value="">All societies</option>' + socOpts.map(function (s) {
                return '<option value="' + s.id + '"' + (String(af.societyId || '') === String(s.id) ? ' selected' : '') + '>' + esc(s.name) + '</option>';
            }).join('') + '</select>' +
            '<button class="btn" type="button" data-action="admin-analytics-clear">Clear filters</button>' +
            '</div></div>';

        // ---- Recorded order value (handover 10) ----
        h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recorded Order Value</h3>';
        if (!value) {
            h += '<div class="admin-empty admin-empty-inline">Recorded order value could not be loaded.</div>';
        } else {
            h += '<p class="muted tiny" style="margin:0 0 10px">Value of orders recorded on the marketplace — not platform revenue. Covers the selected filters' +
                (af.date ? ' for ' + esc(af.date) : '') + '.</p>' +
                '<div class="flex gap-2 wrap">' +
                '<div class="flex-1 min-140"><div class="muted small">Orders</div><div class="font-700 font-size-2">' + (value.orderCount || 0) + '</div></div>' +
                '<div class="flex-1 min-140"><div class="muted small">Total value</div><div class="font-700 font-size-2">' + money(value.recordedOrderValue || 0) + '</div></div>' +
                '<div class="flex-1 min-140"><div class="muted small">Average order value</div><div class="font-700 font-size-2">' + money(value.averageOrderValue || 0) + '</div></div>' +
                '</div>';
            // Handover 10: the breakdown the total is made of. Rendered from the
            // same filtered rows as the headline, so the parts always sum to the
            // whole. Groups with no recorded value are omitted rather than shown blank.
            h += breakdownBlock('By Area', value.byArea) +
                breakdownBlock('By Society', value.bySociety) +
                breakdownBlock('By Seller', value.bySeller);
        }
        h += '</div>';

        // ---- Seller performance (handover 6: sortable seller table) ----
        h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Seller Performance</h3>' +
            '<p class="muted tiny" style="margin:0 0 10px">Views come from storefront and offering visits; conversion is orders ÷ storefront views and is only shown where views are tracked.</p>';
        if (!sellers.length) {
            h += '<div class="admin-empty admin-empty-inline">No seller activity matches these filters.</div>';
        }
        // Sortable because the handover asks for a "sortable seller table": the
        // operator usually wants the biggest contributor, or the worst converter.
        var sortKey = A.sellerSort || 'recordedOrderValue';
        var sortDir = A.sellerSortDir === 'asc' ? 1 : -1;
        var sorted = sellers.slice().sort(function (x, y) {
            var a = sortValue(x, sortKey), b = sortValue(y, sortKey);
            if (typeof a === 'string' || typeof b === 'string') {
                return String(a).localeCompare(String(b)) * sortDir;
            }
            return (a - b) * sortDir;
        });
        if (sellers.length) {
            h += '<div class="admin-filters" style="margin-bottom:10px">' +
                [['recordedOrderValue', 'Recorded value'], ['orders', 'Orders'],
                 ['storefrontViews', 'Storefront views'], ['sellerName', 'Seller']]
                .map(function (s) {
                    var active = sortKey === s[0];
                    return '<button class="capsule' + (active ? ' active' : '') + '" type="button" ' +
                        'data-action="admin-seller-sort" data-key="' + s[0] + '">' + s[1] +
                        (active ? (sortDir === -1 ? ' ↓' : ' ↑') : '') + '</button>';
                }).join('') +
                '</div>';
        }
        sorted.forEach(function (s) {
            h += '<div class="seller-row"><div class="sr-avatar">🍳</div><div class="sr-body">' +
                '<div class="sr-name">' + esc(s.sellerName || 'Seller') + ' <span class="muted small">' + esc(s.kitchenName || '') + '</span></div>' +
                '<div class="sr-meta">Orders: ' + (s.orders || 0) + ' · Recorded value: <strong>' + money(s.recordedOrderValue || 0) + '</strong> · Avg: ' + money(s.averageOrderValue || 0) + '</div>' +
                '<div class="sr-meta">Views: ' + (s.storefrontViews || 0) + ' storefront · ' + (s.offeringViews || 0) + ' offerings · Conversion: ' +
                    (s.conversionRate == null ? '<span class="muted">not tracked</span>' : (s.conversionRate + '%')) + '</div>' +
                '</div></div>';
        });
        h += '</div>';
        h += '</div>';
        return h;
    };
}

/** Comparable value for one seller-table column; missing numbers sort as 0. */
function sortValue(row, key) {
    var v = row[key];
    if (key === 'sellerName') return String(v || '').toLowerCase();
    return Number(v || 0);
}

/** One "X of Y · count orders" breakdown list, or nothing when there is no data. */
function breakdownBlock(title, rows) {
    if (!rows || !rows.length) return '';
    return '<h4 class="font-700 mt-2 mb-1" style="font-size:0.95rem">' + esc(title) + '</h4>' +
        rows.slice(0, 10).map(function (r) {
            return '<div class="sr-meta" style="display:flex;justify-content:space-between;gap:10px">' +
                '<span>' + esc(r.label || 'Unassigned') + '</span>' +
                '<span>' + (r.orderCount || 0) + ' orders · <strong>' + money(r.recordedOrderValue || 0) + '</strong></span>' +
                '</div>';
        }).join('') +
        (rows.length > 10 ? '<div class="muted tiny">+ ' + (rows.length - 10) + ' more</div>' : '');
}

/**
 * Admin action history (handover 14).
 *
 * <p>Every consequential Admin decision is written server-side to the audit
 * trail. This screen is read-only: there is deliberately no way to edit or
 * delete an entry, because an audit log that Admin can rewrite is not an audit
 * log. The domain chips filter client-side over the newest page of entries.</p>
 */
async function adminAuditView() {
    var domain = A.auditDomain || '';
    var limit = A.auditLimit || 200;
    var list = await api('/api/admin/audit-log?limit=' + encodeURIComponent(limit));
    var rows = (list || []).filter(function (a) {
        return !domain || (a.targetType || '') === domain;
    });
    var domains = ['', 'SELLER', 'BUYER', 'ORDER', 'PLATFORM'];
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Admin Action History</h1>' +
        '<p class="muted small">' + rows.length + ' of the newest ' + (list || []).length + ' recorded actions</p></div></div>';
    h += '<div class="admin-filters">';
    domains.forEach(function (d) {
        h += '<button class="btn btn-sm ' + (domain === d ? 'btn-primary' : 'btn-secondary') + '" type="button" data-action="admin-audit-domain" data-value="' + d + '">' +
            (d === '' ? 'All' : esc(d.charAt(0) + d.slice(1).toLowerCase())) + '</button>';
    });
    h += '</div>';
    if (!rows.length) {
        return h + '<div class="admin-empty">No recorded actions' + (domain ? ' for ' + esc(domain) : '') + ' yet.</div></div>';
    }
    rows.forEach(function (a) {
        var pill = a.action === 'SELLER_APPROVED' || a.action === 'BUYER_UNBLOCKED' || a.action === 'STOREFRONT_RESUMED' ? 'pill-green'
            : a.action === 'SELLER_REJECTED' || a.action === 'SELLER_SUSPENDED' || a.action === 'BUYER_BLOCKED' || a.action === 'STOREFRONT_REMOVED' ? 'pill-red'
            : 'pill-grey';
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🗒️</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(a.action || 'ACTION') + ' <span class="pill ' + pill + '">' + esc(a.targetType || '') + '</span></div>' +
            '<div class="sr-meta">By ' + esc(a.actorName || ('Admin ' + (a.actorId || ''))) + ' · ' + adminDate(a.at) + '</div>' +
            '<div class="sr-meta">Target: ' + esc(a.targetLabel || (a.targetType ? a.targetType + ' ' + (a.targetId || '') : '—')) +
                (a.oldState || a.newState ? ' · ' + esc(a.oldState || '—') + ' → ' + esc(a.newState || '—') : '') + '</div>' +
            (a.reason ? '<div class="sr-meta sr-reason">📝 ' + esc(a.reason) + '</div>' : '') +
            '</div></div>';
    });
    h += '</div>';
    return h;
}

/**
 * Data retention (handover 12).
 *
 * <p>Detailed orders older than the window can be purged; the analytics
 * aggregates (recorded at transition time, handover 18) survive, so platform
 * numbers stay correct on both sides of a purge. The purge itself is a
 * scheduled, server-side job - this screen reports the resolved configuration
 * and changes the window. It never deletes anything on the spot.</p>
 */
async function adminRetentionView() {
    var d = await api('/api/admin/retention');
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Data Retention</h1>' +
        '<p class="muted small">How long detailed order records are kept, and what a purge would cover</p></div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Current window</h3>' +
        '<div class="flex gap-2 wrap">' +
        '<div class="flex-1 min-140"><div class="muted small">Retention</div><div class="font-700 font-size-2">' + (d.retentionDays || 0) + ' days</div></div>' +
        '<div class="flex-1 min-140"><div class="muted small">Purge cutoff</div><div class="font-700 mt-1">' + (d.purgeCutoffDate ? esc(String(d.purgeCutoffDate)) : '—') + '</div></div>' +
        '<div class="flex-1 min-140"><div class="muted small">Detailed rows past the window</div><div class="font-700 mt-1">' + (d.detailedRowsPastWindow || 0) + '</div></div>' +
        '</div>' +
        '<p class="muted tiny" style="margin:10px 0 0">Setting: <code>' + esc(d.settingKey || '') + '</code>' +
        (d.destructivePurgeEnabled === false ? ' · Destructive purge is DISABLED — rows are counted, never deleted.' : '') + '</p>' +
        '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Change the window</h3>' +
        '<p class="muted tiny" style="margin:0 0 10px">Shorter windows purge sooner; the change is recorded in the Admin action history. Buyers and sellers are never notified of a purge.</p>' +
        '<div class="flex gap-2 wrap" style="align-items:flex-end">' +
        '<div><label class="form-label" for="retentionDays">Retention (days)</label>' +
        '<input class="form-input" type="number" min="1" id="retentionDays" value="' + (d.retentionDays || 30) + '" style="max-width:140px"></div>' +
        '<button class="btn btn-primary" type="button" data-action="retention-set">Save retention</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

/**
 * Seller detail (handover 7.2): identity/contact, enabled types, status,
 * storefronts with Admin controls, recorded activity, recent orders and the
 * Admin action history - everything a support question about a seller needs,
 * on one screen.
 */
async function adminSellerDetailView() {
    var id = A.sellerDetailId;
    if (!id) {
        return '<div class="view-enter"><div class="section-head admin-section-head"><div><h1>Seller</h1>' +
            '<p class="muted small">Open a seller from the Sellers list to see their full record.</p></div></div>' +
            '<button class="btn btn-secondary" type="button" data-action="admin-back-sellers">← Back to Sellers</button></div>';
    }
    var d = await api('/api/admin/sellers/' + id + '/detail');
    var st = d.status || '';
    var stPill = st === 'APPROVED' ? 'green' : (st === 'PENDING' || st === 'CHANGES_REQUESTED') ? 'amber' : st === 'SUSPENDED' ? 'red' : 'grey';
    var stat = function (label, value) {
        return '<div class="flex-1 min-140"><div class="muted small">' + label + '</div><div class="font-700 mt-1">' + value + '</div></div>';
    };
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + esc(d.name || 'Seller') +
        ' <span class="pill pill-' + stPill + '">' + esc(st || '') + '</span></h1>' +
        '<p class="muted small">📱 ' + esc(d.mobileNumber || '—') + ' · ' + esc(d.enabledTypes || 'NONE') +
        ' · ' + (d.approvedAt ? 'approved ' + adminDate(d.approvedAt) + (d.approvedBy ? ' by ' + esc(d.approvedBy) : '') : 'no approval recorded') + '</p></div></div>';
    if (d.statusReason) {
        h += '<div class="card pad"><div class="sr-reason">⚠ ' + esc(d.statusReason) + '</div></div>';
    }

    // Decision row: the same handlers the list rows use, so state and audit
    // behave identically wherever the decision is taken.
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Decision</h3><div class="admin-actions">' +
        '<button class="btn btn-secondary" type="button" data-action="admin-back-sellers">← Back to Sellers</button>';
    if (st === 'SUSPENDED') {
        h += '<button class="btn btn-success" type="button" data-action="reactivate-seller" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Reactivate</button>';
    } else if (st !== 'APPROVED') {
        h += '<button class="btn btn-success" type="button" data-action="approve-seller" data-id="' + d.id + '">Approve</button>';
    }
    if (st !== 'SUSPENDED') {
        h += '<button class="btn btn-outline" type="button" data-action="open-admin-reason" data-mode="requestChanges" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Request Changes</button>' +
            '<button class="btn btn-outline" type="button" data-action="open-admin-reason" data-mode="suspend" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Suspend</button>';
    }
    h += '<button class="btn btn-outline" type="button" data-action="open-reject" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Reject</button>' +
        '</div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recorded activity</h3>' +
        '<div class="flex gap-2 wrap">' +
        stat('Orders', d.orderCount || 0) + stat('Delivered', d.deliveredCount || 0) + stat('Cancelled', d.cancelledCount || 0) +
        stat('Recorded Order Value', money(d.recordedOrderValue || 0)) + stat('Average Order Value', money(d.averageOrderValue || 0)) +
        '</div></div>';
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Storefronts (' + (d.storefronts || []).length + ')</h3>';
    if (!(d.storefronts || []).length) {
        h += '<div class="admin-empty admin-empty-inline">No storefronts on record.</div>';
    }
    (d.storefronts || []).forEach(function (k) {
        var coverage = k.servedSocieties && k.servedSocieties.length ? k.servedSocieties.join(', ') : (k.serviceAreas || '');
        h += '<div class="seller-row">' +
            '<div class="sr-avatar">🏪</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(k.name || 'Storefront') +
                (k.paused ? ' <span class="pill pill-amber">Paused</span>' : ' <span class="pill pill-green">Active</span>') + '</div>' +
            '<div class="sr-meta">' + esc(k.sellerType || '') + ' · ' + (k.availableToday ? '🟢 Available today' : '⚪ Not available today') +
                ' · Offerings: ' + (k.offeringCount || 0) + '</div>' +
            '<div class="sr-meta">Coverage: ' + esc(coverage || 'not configured') + '</div>' +
            '<div class="sr-meta">Traffic: ' + (k.storefrontViews || 0) + ' storefront views · ' + (k.offeringViews || 0) + ' offering views</div>' +
            '</div>' +
            '<div class="sr-actions">' +
            (k.paused
                ? '<button class="btn btn-success btn-sm" type="button" data-action="storefront-resume" data-id="' + k.id + '" data-name="' + esc(k.name || '') + '">Resume</button>'
                : '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="pause" data-id="' + k.id + '" data-name="' + esc(k.name || '') + '">Pause</button>') +
            '<button class="btn btn-outline btn-sm" type="button" data-action="open-admin-reason" data-mode="removeStorefront" data-id="' + k.id + '" data-name="' + esc(k.name || '') + '">Remove</button>' +
            '</div></div>';
    });
    h += '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recent orders</h3>';
    if (!(d.recentOrders || []).length) {
        h += '<div class="admin-empty admin-empty-inline">No orders yet.</div>';
    } else {
        d.recentOrders.forEach(function (o) {
            h += '<div class="sr-meta" style="padding:6px 0">#' + esc(o.orderNumber || String(o.id)) + ' · ' + esc(o.buyerName || 'Buyer') +
                ' · ' + esc(o.orderStatus || '') + ' · ' + adminDate(o.orderTime || o.createdAt) +
                ' · <strong>' + money(o.totalAmount || 0) + '</strong></div>';
        });
    }
    h += '</div>';

    h += adminAuditHistoryHtml(d.auditHistory, 'Seller');
    return h;
}

/**
 * Compact Admin action history, shared by the Seller and Buyer detail screens
 * so the same decision reads the same way wherever it is audited.
 */
function adminAuditHistoryHtml(history, noun) {
    var h = '<div class="card pad card-mt"><h3 class="font-700 mb-2">' + esc(noun) + ' action history</h3>';
    if (!history || !history.length) {
        return h + '<div class="admin-empty admin-empty-inline">No Admin actions recorded for this ' + esc(noun.toLowerCase()) + ' yet.</div></div>';
    }
    history.forEach(function (a) {
        h += '<div class="seller-row"><div class="sr-avatar">🗒️</div><div class="sr-body">' +
            '<div class="sr-name">' + esc(a.action || '') + (a.newState ? ' <span class="pill pill-grey">' + esc(a.newState) + '</span>' : '') + '</div>' +
            '<div class="sr-meta">By ' + esc(a.actorName || 'Admin') + ' · ' + adminDate(a.at) + (a.oldState ? ' · from ' + esc(a.oldState) : '') + '</div>' +
            (a.reason ? '<div class="sr-meta sr-reason">' + esc(a.reason) + '</div>' : '') +
            '</div></div>';
    });
    return h + '</div>';
}

/**
 * Buyer detail (handover 8): profile/location needed for order support, recent
 * orders with payment/delivery status, account state (Active / Blocked) and the
 * internal support note. The note never leaves the Admin surface.
 */
async function adminBuyerDetailView() {
    var id = A.buyerDetailId;
    if (!id) {
        return '<div class="view-enter"><div class="section-head admin-section-head"><div><h1>Buyer</h1>' +
            '<p class="muted small">Open a buyer from the Buyers list to see their full record.</p></div></div>' +
            '<button class="btn btn-secondary" type="button" data-action="admin-back-buyers">← Back to Buyers</button></div>';
    }
    var d = await api('/api/admin/buyers/' + id);
    var stat = function (label, value) {
        return '<div class="flex-1 min-140"><div class="muted small">' + label + '</div><div class="font-700 mt-1">' + value + '</div></div>';
    };
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>' + esc(d.name || 'Buyer') +
        ' <span class="pill ' + (d.blocked ? 'pill-red">Blocked' : 'pill-green">Active') + '</span></h1>' +
        '<p class="muted small">📱 ' + esc(d.mobileNumber || '—') + ' · joined ' + adminDate(d.joinedAt) + '</p></div></div>';
    if (d.blocked && d.blockedReason) {
        h += '<div class="card pad"><div class="sr-reason">⚠ Blocked' + (d.blockedAt ? ' on ' + adminDate(d.blockedAt) : '') + ': ' + esc(d.blockedReason) + '</div></div>';
    }

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Account</h3><div class="admin-actions">' +
        '<button class="btn btn-secondary" type="button" data-action="admin-back-buyers">← Back to Buyers</button>' +
        (d.blocked
            ? '<button class="btn btn-success" type="button" data-action="buyer-unblock" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Unblock</button>'
            : '<button class="btn btn-outline" type="button" data-action="open-admin-reason" data-mode="block" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">Block</button>') +
        '<button class="btn btn-secondary" type="button" data-action="open-admin-reason" data-mode="note" data-id="' + d.id + '" data-name="' + esc(d.name || '') + '">' +
        (d.supportNote ? 'Edit support note' : 'Add support note') + '</button>' +
        '</div>' +
        (d.supportNote ? '<div class="sr-meta sr-reason" style="margin-top:8px">🗒️ ' + esc(d.supportNote) + '</div>'
                       : '<p class="muted tiny" style="margin:8px 0 0">No internal support note yet.</p>') +
        '</div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Profile & location</h3>' +
        '<div class="flex gap-2 wrap">' +
        stat('Society', esc(d.society || '—') + (d.societyId ? ' <span class="muted tiny">#' + d.societyId + '</span>' : '')) +
        stat('Area', esc(d.area || '—')) +
        stat('Building', esc(d.building || '—')) +
        stat('Flat / House', esc(d.flatHouseNumber || '—')) +
        '</div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recorded activity</h3>' +
        '<div class="flex gap-2 wrap">' +
        stat('Orders', d.orderCount || 0) + stat('Delivered', d.deliveredCount || 0) +
        stat('Cancelled', d.cancelledCount || 0) + stat('Paid', d.paidCount || 0) +
        stat('Recorded Order Value', money(d.recordedOrderValue || 0)) +
        '</div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Recent orders</h3>';
    if (!(d.recentOrders || []).length) {
        h += '<div class="admin-empty admin-empty-inline">No orders yet.</div>';
    } else {
        d.recentOrders.forEach(function (o) {
            h += '<div class="sr-meta" style="padding:6px 0">#' + esc(o.orderNumber || String(o.id)) + ' · ' + esc(o.kitchenName || 'Store') +
                ' · ' + esc(o.orderStatus || '') + ' / ' + esc(o.paymentStatus || '') + ' / ' + esc(o.deliveryStatus || '') +
                ' · ' + adminDate(o.orderTime || o.createdAt) + ' · <strong>' + money(o.totalAmount || 0) + '</strong></div>';
        });
    }
    h += '</div>';

    h += adminAuditHistoryHtml(d.auditHistory, 'Buyer');
    h += '</div>';
    return h;
}

/**
 * Platform Console — Super Admin only.
 *
 * <p>Scope is deliberately limited to the administrator-account workflows the
 * backend genuinely supports: list, create and demote, all through the existing
 * /api/superadmin/admins endpoints. Server-side authorization stays the authority
 * (the route is hidden for ordinary Admins, and /api/superadmin/** rejects them
 * regardless).
 *
 * <p>Feature flags, seller grants and platform settings are NOT exposed here.
 * Those endpoints exist, but what a valid limit or price is, and who may change
 * it, are undefined product rules - so they stay unavailable rather than being
 * guessed at.
 */
async function adminConsoleView() {
    if (A.role !== 'SUPER_ADMIN') {
        return '<div class="view-enter"><div class="section-head admin-section-head">' +
            '<div><h1>Platform Console</h1>' +
            '<p class="muted small">Restricted to Super Admin accounts.</p></div></div>' +
            '<div class="card pad"><div class="admin-empty admin-empty-inline">🚫 ' +
            'You are signed in as an Admin, which cannot manage administrator accounts. ' +
            'Server-side authorization blocks these operations regardless of this screen.</div></div></div>';
    }

    var admins = await api('/api/superadmin/admins');
    var adminCount = 0, superCount = 0;
    (admins || []).forEach(function (a) {
        if (a.role === 'SUPER_ADMIN') superCount++; else adminCount++;
    });

    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Platform Console</h1>' +
        '<p class="muted small">Administrator accounts — ' + superCount + ' Super Admin, ' + adminCount + ' Admin</p></div></div>';

    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Administrator Accounts</h3>';
    h += '<p class="muted tiny" style="margin:0 0 12px">Read-only list of every account with platform access.</p>';
    if (!admins || !admins.length) {
        h += '<div class="admin-empty">No administrator accounts yet.</div>';
    } else {
        h += '<div class="seller-row"><div class="sr-avatar">🛡️</div><div class="sr-body">';
        admins.forEach(function (a) {
            var isSuper = a.role === 'SUPER_ADMIN';
            // The backend refuses to demote a Super Admin, or the last Admin.
            var canDemote = !isSuper && adminCount > 1;
            var why = isSuper ? 'Super Admin accounts cannot be demoted'
                : (adminCount > 1 ? '' : 'This is the last Admin account');
            h += '<div class="console-admin-row">';
            h += '<div class="ca-main">';
            h += '<div class="ca-name">' + esc(a.name || 'Unnamed') +
                ' <span class="pill ' + (isSuper ? 'pill-blue' : 'pill-green') + '">' + esc(a.role) + '</span></div>';
            h += '<div class="muted small">📱 ' + esc(a.mobileNumber || '—') + '</div>';
            h += '</div>';
            if (isSuper) {
                h += '<div class="sr-actions"><button class="btn btn-sm btn-secondary" type="button" disabled ' +
                    'title="' + esc(why) + '">Not demotable</button></div>';
            } else if (!canDemote) {
                h += '<div class="sr-actions"><button class="btn btn-sm btn-secondary" type="button" disabled ' +
                    'title="' + esc(why) + '">Last Admin</button></div>';
            } else {
                h += '<div class="sr-actions"><button class="btn btn-sm btn-outline" type="button" ' +
                    'data-action="console-demote-admin" data-id="' + a.id + '" data-name="' + esc(a.name || 'Admin') +
                    '" data-mobile="' + esc(a.mobileNumber || '') + '">Demote</button></div>';
            }
            h += '</div>';
        });
        h += '</div></div>';
    }
    h += '</div>';

    // ---- create administrator ----
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Add Administrator</h3>';
    h += '<p class="muted tiny" style="margin:0 0 12px">Creates an Admin account, or promotes an existing Buyer account. ' +
        'It cannot promote a Seller — that would detach their kitchen and offerings.</p>';
    h += '<div class="form-group"><label class="form-label" for="consoleAdminName">Name</label>' +
        '<input class="form-input" id="consoleAdminName" type="text" maxlength="60" autocomplete="off" placeholder="Admin name"></div>';
    h += '<div class="form-group"><label class="form-label" for="consoleAdminMobile">Mobile number</label>' +
        '<input class="form-input" id="consoleAdminMobile" type="tel" inputmode="numeric" maxlength="10" autocomplete="off" placeholder="10-digit mobile"></div>';
    h += '<div id="consoleAdminError" class="admin-error-msg" hidden></div>';
    h += '<div class="admin-actions"><button class="btn btn-primary btn-block" type="button" ' +
        'data-action="console-create-admin">Create Admin</button></div>';
    h += '</div>';

    // ---- deliberately not exposed ----
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Not available in this console</h3>';
    h += '<p class="muted small" style="margin:0">Feature flags, seller feature grants and platform settings have backend ' +
        'endpoints but no agreed business rules (valid limits, pricing, and who may change them). They stay closed ' +
        'rather than being exposed without a defined policy.</p></div>';

    h += '</div>';
    return h;
}

function renderTrafficContent(data, period) {
    var container = document.getElementById('trafficContent');
    if (!container) return;
    if (!data || !data.series || !data.series.length) {
        container.innerHTML = '<div class="admin-empty">No buyer or seller activity found for this period.</div>';
        return;
    }
    var buyers = data.activeBuyers || 0;
    var sellers = data.activeSellers || 0;
    var h = '<div class="dash-grid card-mt">' +
        '<div class="dash-card"><div class="dc-top"><span class="dc-icon">🛒</span><span class="dc-num">' + buyers + '</span></div><div class="dc-label">Active Buyers</div><div class="dc-sub">' + periodLabel(period) + '</div></div>' +
        '<div class="dash-card"><div class="dc-top"><span class="dc-icon">👨‍🍳</span><span class="dc-num">' + sellers + '</span></div><div class="dc-label">Active Sellers</div><div class="dc-sub">' + periodLabel(period) + '</div></div>' +
        '</div>';
    h += '<div class="card pad card-mt"><h3 class="font-700 mb-2">Traffic Trend</h3><p class="muted tiny" style="margin:0 0 12px">Distinct active Buyers and Sellers over the selected period.</p>';
    h += '<div class="traffic-chart-wrap">' + trafficSvgChart(data.series, period) + '</div>';
    h += '<div class="flex gap-2 wrap" style="margin-top:14px">' +
        '<div class="flex-1 min-140"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#4F46E5;margin-right:6px;vertical-align:middle"></span> Buyers</div>' +
        '<div class="flex-1 min-140"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#16A34A;margin-right:6px;vertical-align:middle"></span> Sellers</div>' +
        '</div></div>';
    container.innerHTML = h;
}

/**
 * Renders the society checkboxes for the Admin-selected Area. Choosing an Area
 * preselects that area's full active set, so an admin sees the same default the
 * seller gets; unticking narrows coverage. Nothing is persisted until Save.
 */
function renderAdminCoverageSocieties() {
    var list = $('#adminCoverageSocietyList');
    if (!list) return;
    var areaId = A.adminCoverageAreaId || '';
    var area = null;
    (A.adminAreas || []).forEach(function (a) { if (String(a.id) === String(areaId)) area = a; });
    list.innerHTML = '';
    if (!areaId || !area) {
        list.innerHTML = '<div class="muted small">Select an area to choose its societies.</div>';
        return;
    }
    var societies = area.societies || [];
    if (!societies.length) {
        list.innerHTML = '<div class="muted small">This area has no active societies yet.</div>';
        return;
    }
    if (String(A.adminCoverageLoadedAreaId || '') !== String(areaId)) {
        A.adminCoverageIds = societies.map(function (s) { return Number(s.id); });
        A.adminCoverageLoadedAreaId = String(areaId);
    } else {
        A.adminCoverageIds = (A.adminCoverageIds || []).filter(function (id) {
            return societies.some(function (s) { return Number(s.id) === Number(id); });
        });
    }
    var html = '';
    societies.forEach(function (s) {
        var on = A.adminCoverageIds.indexOf(Number(s.id)) !== -1;
        html += '<label class="sa-pill" style="display:inline-flex;align-items:center;gap:6px;margin:0 6px 6px 0;cursor:pointer">'
            + '<input type="checkbox" data-action="admin-toggle-coverage-society" data-id="' + esc(s.id) + '"' + (on ? ' checked' : '') + '>'
            + '<span>' + esc(s.name) + '</span></label>';
    });
    list.innerHTML = html;
}

// ==================== Buyer <-> Kitchen visibility diagnostic ====================

/**
 * "Why can't this buyer see this kitchen?"
 *
 * The verdict and the reasons come straight from the backend, which calls the SAME
 * KitchenVisibility predicates the buyer-facing endpoints use. This screen only
 * renders that answer - it holds no eligibility logic of its own and never changes
 * buyer, kitchen or coverage data.
 */
async function adminDiagnosticsView() {
    var buyers, kitchens;
    try {
        buyers = await api('/api/admin/buyers');
        kitchens = await api('/api/admin/kitchens');
    } catch (e) {
        return adminErrorView(e);
    }
    if (!A.diagBuyerId) A.diagBuyerId = (buyers && buyers[0]) ? String(buyers[0].id) : '';

    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>Visibility Diagnostic</h1>' +
        '<p class="muted small">Pick a buyer to see which kitchens they can see, and exactly why. ' +
        'Read-only — nothing here changes any record.</p></div></div>';

    h += '<div class="card pad card-mb"><div class="form-group" style="margin-bottom:0">' +
        '<label class="form-label" for="diagBuyerSelect">Buyer</label>' +
        '<select class="form-input" id="diagBuyerSelect" data-action="admin-diag-buyer">' +
        (buyers || []).map(function (b) {
            var id = String(b.id);
            return '<option value="' + esc(id) + '"' + (id === String(A.diagBuyerId) ? ' selected' : '') + '>' +
                esc((b.name || 'Unknown') + ' · ' + (b.mobileNumber || '—') +
                    (b.society ? ' · ' + b.society : '')) + '</option>';
        }).join('') + '</select></div>';

    if (kitchens && kitchens.length) {
        h += '<div class="form-group" style="margin-top:10px;margin-bottom:0">' +
            '<label class="form-label" for="diagKitchenSelect">Focus one kitchen (optional)</label>' +
            '<select class="form-input" id="diagKitchenSelect" data-action="admin-diag-kitchen">' +
            '<option value="">All kitchens</option>' +
            kitchens.map(function (k) {
                var id = String(k.id);
                return '<option value="' + esc(id) + '"' +
                    (id === String(A.diagKitchenId) ? ' selected' : '') + '>' +
                    esc(k.displayName || k.name) + '</option>';
            }).join('') + '</select></div>';
    }
    h += '</div>';

    if (!A.diagBuyerId) {
        h += '<div class="admin-empty">No buyers are registered yet.</div></div>';
        return h;
    }

    var qs = '?buyerId=' + encodeURIComponent(A.diagBuyerId);
    if (A.diagKitchenId) qs += '&kitchenId=' + encodeURIComponent(A.diagKitchenId);

    var data;
    try {
        data = await api('/api/admin/diagnostics/visibility' + qs);
    } catch (e) {
        h += '<div class="card pad"><p class="admin-error-msg">' +
            esc(e.message || 'Could not run the diagnostic.') + '</p></div></div>';
        return h;
    }

    var buyer = data.buyer || {};
    var sum = data.summary || {};
    h += '<div class="card pad card-mb">' +
        '<div class="sr-name" style="margin-bottom:6px">' + esc(buyer.name || 'Buyer') +
        ' <span class="muted small">· ' + esc(buyer.mobileNumber || '—') + '</span></div>' +
        '<div class="sr-meta">Society: ' + (buyer.society ? esc(buyer.society) : '<span class="pill pill-grey">not set</span>') +
        (buyer.area ? ' · Area: ' + esc(buyer.area) : '') + '</div>' +
        (buyer.profileComplete
            ? '<div class="sr-meta">Profile complete — can place orders</div>'
            : '<div class="sr-meta">Profile incomplete — missing: ' +
              esc((buyer.profileMissing || []).join(', ')) + '</div>') +
        '<div class="sr-meta mt-1"><strong>' + (sum.visible || 0) + '</strong> of ' +
        (sum.totalKitchens || 0) + ' kitchens visible · <strong>' + (sum.blocked || 0) + '</strong> blocked</div>' +
        '</div>';

    var results = data.results || [];
    if (!results.length) {
        h += '<div class="admin-empty">No kitchens to evaluate.</div></div>';
        return h;
    }

    h += '<div class="card pad">';
    results.forEach(function (r) {
        var ok = !!r.visible;
        h += '<div class="seller-row" style="border:none;padding:10px 0">' +
            '<div class="sr-avatar">' + (ok ? '✅' : '⛔') + '</div>' +
            '<div class="sr-body">' +
            '<div class="sr-name">' + esc(r.kitchenName || ('Kitchen ' + r.kitchenId)) +
            ' <span class="pill pill-' + (ok ? 'green' : 'grey') + '">' + (ok ? 'Visible' : 'Blocked') + '</span></div>' +
            '<div class="sr-meta">' + esc(r.sellerName || '—') + ' · coverage: ' + esc(r.coverageMode || '—') +
            ((r.kitchenCoverage && r.kitchenCoverage.length)
                ? ' (' + esc(r.kitchenCoverage.join(', ')) + ')' : '') + '</div>' +
            '<div class="sr-meta muted small">publiclyVisible=' + !!r.publiclyVisible +
            ' · paused=' + !!r.paused + ' · serviceAreaVisible=' + !!r.serviceAreaVisible + '</div>' +
            '<ul class="muted small" style="margin:6px 0 0 16px;padding:0">' +
            (r.reasons || []).map(function (x) { return '<li>' + esc(x) + '</li>'; }).join('') +
            '</ul>' +
            '<button class="btn btn-secondary btn-sm" type="button" style="margin-top:6px" ' +
            'data-action="admin-diag-focus" data-kitchen-id="' + esc(r.kitchenId) + '">Focus this kitchen</button>' +
            '</div></div>';
    });
    h += '</div></div>';
    return h;
}
// ==================== System health ====================

/**
 * Lightweight, factual runtime status. Every value is reported by
 * GET /api/admin/system-health, which reads the real Spring profile, the real demo
 * gate and a real JPA round trip. Nothing here is simulated or estimated.
 */
async function adminHealthView() {
    var data;
    try {
        data = await api('/api/admin/system-health');
    } catch (e) {
        return adminErrorView(e);
    }
    var db = data.database || {};
    var master = data.locationMaster || {};
    var h = '<div class="view-enter">';
    h += '<div class="section-head admin-section-head"><div><h1>System Health</h1>' +
        '<p class="muted small">Live facts reported by the running application. Read-only.</p></div></div>';

    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Application</h3>' +
        '<div class="sr-meta">Name: ' + esc(data.application || '—') + '</div>' +
        '<div class="sr-meta">Active profiles: ' + esc((data.activeProfiles || []).join(', ') || 'default') + '</div>' +
        '<div class="sr-meta">Demo login enabled: ' + (data.demoLoginEnabled ? 'yes' : 'no') + '</div>' +
        '</div>';

    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Database</h3>' +
        (db.reachable
            ? '<div class="sr-meta">Status: <span class="pill pill-green">reachable</span></div>' +
              '<div class="sr-meta">Users: ' + (db.users || 0) + ' · Kitchens: ' + (db.kitchens || 0) +
              ' · Orders: ' + (db.orders || 0) + '</div>'
            : '<div class="sr-meta">Status: <span class="pill pill-red">unreachable</span></div>' +
              '<div class="sr-meta">' + esc(db.error || '') + '</div>') +
        '</div>';

    h += '<div class="card pad"><h3 class="font-700 mb-2">Location master</h3>' +
        '<div class="sr-meta">Areas: ' + (master.areas || 0) + ' · Societies: ' + (master.societies || 0) + '</div>' +
        '<div class="mt-1"><a class="btn btn-secondary btn-sm" href="#/locations" data-action="go-tab" ' +
        'data-hash="#/locations">Manage Areas &amp; Societies</a></div>' +
        '</div>';

    h += '</div>';
    return h;
}

// ANCHOR-VIEWS

function periodLabel(period) {
    if (period === 'today') return 'Today';
    if (period === 'week') return 'This Week';
    if (period === 'month') return 'This Month';
    return period;
}

function trafficSvgChart(series, period) {
    var width = 800;
    var height = 280;
    var pad = { top: 20, right: 20, bottom: 40, left: 45 };
    var chartW = width - pad.left - pad.right;
    var chartH = height - pad.top - pad.bottom;

    var maxVal = 0;
    series.forEach(function (pt) {
        maxVal = Math.max(maxVal, pt.buyers || 0, pt.sellers || 0);
    });
    if (maxVal === 0) maxVal = 1;

    var xStep = chartW / Math.max(series.length - 1, 1);
    var pointsBuyers = series.map(function (pt, i) {
        return [pad.left + i * xStep, pad.top + chartH - ((pt.buyers || 0) / maxVal) * chartH];
    });
    var pointsSellers = series.map(function (pt, i) {
        return [pad.left + i * xStep, pad.top + chartH - ((pt.sellers || 0) / maxVal) * chartH];
    });

    function polyline(pts) {
        return pts.map(function (p) { return p[0] + ',' + p[1]; }).join(' ');
    }

    function dots(pts, color) {
        return pts.map(function (p, i) {
            return '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="4" fill="' + color + '" stroke="#fff" stroke-width="2" />' +
                '<title>' + (series[i].label || '') + ': Buyers ' + (series[i].buyers || 0) + ', Sellers ' + (series[i].sellers || 0) + '</title>';
        }).join('');
    }

    var xLabels = series.map(function (pt, i) {
        var x = pad.left + i * xStep;
        var show = series.length <= 12 || i % Math.ceil(series.length / 12) === 0 || i === series.length - 1;
        if (!show) return '';
        return '<text x="' + x + '" y="' + (height - 8) + '" text-anchor="middle" font-size="11" fill="var(--muted)">' + esc(pt.label) + '</text>';
    }).join('');

    var yTicks = 5;
    var yLabels = '';
    for (var i = 0; i <= yTicks; i++) {
        var val = Math.round((maxVal / yTicks) * i);
        var y = pad.top + chartH - (i / yTicks) * chartH;
        yLabels += '<text x="' + (pad.left - 8) + '" y="' + (y + 4) + '" text-anchor="end" font-size="11" fill="var(--muted)">' + val + '</text>';
        yLabels += '<line x1="' + pad.left + '" y1="' + y + '" x2="' + (width - pad.right) + '" y2="' + y + '" stroke="var(--border)" stroke-width="1" opacity="0.5" />';
    }

    return '<svg viewBox="0 0 ' + width + ' ' + height + '" style="width:100%;height:auto;max-height:280px" preserveAspectRatio="xMidYMid meet">' +
        '<rect x="' + pad.left + '" y="' + pad.top + '" width="' + chartW + '" height="' + chartH + '" fill="var(--surface)" rx="8" />' +
        yLabels +
        xLabels +
        '<polyline fill="none" stroke="#4F46E5" stroke-width="3" points="' + polyline(pointsBuyers) + '" />' +
        '<polyline fill="none" stroke="#16A34A" stroke-width="3" points="' + polyline(pointsSellers) + '" />' +
        dots(pointsBuyers, '#4F46E5') +
        dots(pointsSellers, '#16A34A') +
        '</svg>';
}

// ==================== DELEGATED CLICKS ====================
document.addEventListener('click', async function (ev) {
    var t = ev.target.closest('[data-action]');
    if (!t) return;
    if (t.tagName === 'A' && t.getAttribute('href')) { ev.preventDefault(); }
    await adminAction(t.dataset.action, t);
});

// ==================== BOOT ====================
window.addEventListener('hashchange', function () { if (A.role) adminRender(); });
window.addEventListener('DOMContentLoaded', function () { initTheme(); adminGate(); });

/**
 * Debounced search input.
 *
 * The previous `keyup` handler re-rendered on every keystroke, destroying the very
 * input that had focus. Typing "SM" left a single "S" behind and the operator could
 * not enter a search term at all. We now record the caret, debounce the re-render,
 * and restore focus afterwards.
 */
document.addEventListener('input', function (ev) {
    var el = ev.target;
    if (!el || !el.id || el.id.indexOf('adminSearch_') !== 0) return;
    var key = el.id.substring('adminSearch_'.length);
    A._focusId = el.id;
    A._caretPos = el.selectionStart;
    adminSearchHandler(key, el.id);
});
document.addEventListener('keyup', function (ev) {
    var el = ev.target;
    if (el && el.id && el.id.indexOf('adminSearch_') === 0) A._caretPos = el.selectionStart;
});
// <select> and checkbox interactions report through 'change', not 'click'.
document.addEventListener('change', function (ev) {
    // Dashboard custom date (handover 4/17 "Custom where applicable"). An empty
    // box falls back to Today rather than sending an unparseable value.
    if (ev.target.id === 'adminDashCustomDate') {
        A.dashPeriod = ev.target.value || 'today';
        adminRender();
        return;
    }
    // Admin V1 Orders monitoring axes. 'change' is the only correct source: a
    // 'click' on a <select> reports the PREVIOUS selection and would re-render
    // over the freshly chosen value.
    var axis = ev.target.closest('[data-action="admin-order-axis"]');
    if (axis) {
        var axisKey = axis.dataset.axis;
        A.orderFilters = A.orderFilters || {};
        // A blank control means "no constraint on this axis", never "match nothing".
        if (axis.value) A.orderFilters[axisKey] = axis.value;
        else delete A.orderFilters[axisKey];
        adminRender();
        return;
    }
    // Analytics screen axes (date / area / society) - same blank-means-any rule.
    var anAxis = ev.target.closest('[data-action="admin-analytics-axis"]');
    if (anAxis) {
        A.analyticsFilter = A.analyticsFilter || {};
        if (anAxis.value) A.analyticsFilter[anAxis.dataset.axis] = anAxis.value;
        else delete A.analyticsFilter[anAxis.dataset.axis];
        adminRender();
        return;
    }
    var areaSel = ev.target.closest('[data-action="admin-select-coverage-area"]');
    if (areaSel) {
        A.adminCoverageAreaId = areaSel.value;
        A.adminCoverageLoadedAreaId = '';   // fresh full-area preselect
        renderAdminCoverageSocieties();
        return;
    }
    var socBox = ev.target.closest('[data-action="admin-toggle-coverage-society"]');
    if (socBox) {
        var sid = Number(socBox.dataset.id);
        var idx = (A.adminCoverageIds || []).indexOf(sid);
        if (socBox.checked && idx === -1) A.adminCoverageIds = (A.adminCoverageIds || []).concat([sid]);
        if (!socBox.checked && idx !== -1) A.adminCoverageIds = A.adminCoverageIds.filter(function (id) { return id !== sid; });
        return;
    }
    var diagBuyer = ev.target.closest('[data-action="admin-diag-buyer"]');
    if (diagBuyer) {
        A.diagBuyerId = diagBuyer.value;
        A.diagKitchenId = '';
        adminRender();
        return;
    }
    var diagKitchen = ev.target.closest('[data-action="admin-diag-kitchen"]');
    if (diagKitchen) {
        A.diagKitchenId = diagKitchen.value;
        adminRender();
        return;
    }
});
