/**
 * SocioMart Buyer App v1.0 — Buyer screens
 * Screens: 1 Home · 2 Food & Kitchens hub · 2A Category detail ·
 * 3 Kitchen discovery · 4 Public kitchen · 4A Ordering sheet ·
 * 5 Order summary · 6 Payment · 7 Comparison · 8 Favourites/Orders/Profile
 */

// ==================== Shared UI fragments ====================

var LOCATION = 'Pride World City';

function topBarHtml(opts) {
    opts = opts || {};
    return '<div class="top-row">' +
        '<span class="loc-pill">📍 ' + esc(opts.location || LOCATION) + '</span>' +
        '<span class="bell-wrap">' +
        '<button class="icon-btn" type="button" data-action="toggle-notifs" aria-label="Notifications">🔔' +
        '<span class="bell-badge">3</span></button>' +
        '<div class="notif-panel" id="notifPanel" hidden>' +
        '<div class="notif-item unread">🟢 Your kitchen Aarti Kitchen confirmed today\'s menu</div>' +
        '<div class="notif-item unread">🍽️ Poha is live from 4 kitchens near you</div>' +
        '<div class="notif-item">📦 Order #SM1024 marked Ready for pickup</div>' +
        '</div></span></div>';
}

function backBarHtml(title) {
    return '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 style="flex:1">' + esc(title) + '</h2>' +
        '<span class="bell-wrap"><button class="icon-btn" type="button" data-action="noop" aria-label="Notifications">🔔<span class="bell-badge">3</span></button></span>' +
        '</div>';
}

function statusPill(status) {
    switch (status) {
        case 'LIVE_NOW': return '<span class="pill pill-green">🟢 Taking orders</span>';
        case 'PRE_ORDER': return '<span class="pill pill-blue">🔵 Pre-orders open</span>';
        case 'TOMORROW': return '<span class="pill pill-blue">🔵 Tomorrow</span>';
        case 'CLOSED': return '<span class="pill pill-grey">⚪ Currently closed</span>';
        default: return '';
    }
}

function kitchenCardHtml(k) {
    var items = (k.itemNames || []);
    var preview = items.slice(0, 5).map(esc).join(' · ');
    var more = items.length > 5 ? ' <strong>+' + (items.length - 5) + ' more</strong>' : '';
    var emoji = k.imageUrl ? '' : '🏪';
    return '<div class="kitchen-card">' +
        '<div class="kc-top">' +
        '<div class="kc-avatar">' + emoji + '</div>' +
        '<div class="kc-info">' +
        '<div class="kc-name"><span>' + esc(k.displayName) + '</span>' +
        '<button class="heart-btn" type="button" data-action="toggle-fav-kitchen" data-kid="' + k.id + '" aria-label="Favourite">🤍</button></div>' +
        '<p class="kc-desc">' + esc(k.shortDescription || '') + '</p>' +
        '<div class="kc-meta">' + statusPill(k.status) +
        '<span class="pill pill-grey">' + (k.orderableItemCount || 0) + ' items today</span>' +
        (k.previouslyOrdered ? '<span class="trust-badge">↩ Previously ordered</span>' : '') +
        '</div></div></div>' +
        (preview ? '<p class="kc-items">' + preview + more + '</p>' : '') +
        '<div class="kc-actions"><a class="btn btn-secondary btn-sm" href="#/kitchen/' + k.id + '">View Kitchen →</a></div>' +
        '</div>';
}

function fulfilmentBadge(item) {
    // Color-coded fulfillment timing (Screen 5 spec)
    if (item.isPreorder && item.preorderType === 'FLEXIBLE') {
        return '<span class="fulfil-badge fb-purple">🟣 Pre-order — choose your slot</span>';
    }
    if (item.isPreorder || item.scheduledDate) {
        return '<span class="fulfil-badge fb-blue">🔵 Delivery ' + esc(prettyDate(item.scheduledDate)) +
            (item.readyBy ? ' by ' + esc(prettyTime(item.readyBy)) : '') + '</span>';
    }
    var rb = item.readyBy || '';
    if (rb.indexOf('evening') >= 0 || rb.indexOf('PM') >= 0) {
        return '<span class="fulfil-badge fb-amber">🟡 ' + esc(rb.replace('today', 'this evening').replace('Delivery by', 'Delivery by')) + '</span>';
    }
    return '<span class="fulfil-badge fb-green">🟢 ' + esc(item.readyBy || 'Delivery by 4:00 PM today') + '</span>';
}

function emptyHtml(icon, title, message, actionHtml) {
    return '<div class="empty"><span class="empty-icon">' + icon + '</span>' +
        '<div class="empty-title">' + esc(title) + '</div>' +
        '<p>' + esc(message) + '</p>' + (actionHtml || '') + '</div>';
}

// ==================== Screen 1: Home (landing) ====================

async function homeView() {
    var h = '<div class="view-enter">' + topBarHtml();

    // Hero with logo + tagline "Discover. Connect."
    h += '<div class="hero">' +
        '<div class="hero-brand"><div class="hero-logo">🏪</div>' +
        '<span class="hero-tagline">Discover. Connect.</span></div>' +
        '<h1>Welcome to SocioMart</h1>' +
        '<p class="sub">Your community marketplace.</p>' +
        '<p class="prompt">What are you looking for?</p>' +
        '</div>';

    // Tiles grid — Food & Kitchens is the active module
    h += '<div class="tiles-grid">' +
        '<a class="tile" href="#/food" style="border-color:var(--accent)">' +
        '<span class="tile-icon">🍽️</span><span class="tile-name">Food &amp; Kitchens</span>' +
        '<span class="pill pill-green">● AVAILABLE NOW</span></a>' +
        '</div>';

    // Footer — marketplace summary
    h += '<p class="muted small section-gap" style="text-align:center; padding: 8px 12px 0">' +
        'SocioMart connects you with home kitchens and fresh food from people you trust. ' +
        'Order today or pre-order for later.' +
        '</p></div>';
    return h;
}

// Demo favourites pre-populate the Favourites experience (Screen 8 spec)
var DEMO_FAVOURITES = [
    { kitchenId: 1, name: 'Aarti Kitchen', type: 'KITCHEN' },
    { kitchenId: 3, name: 'Dakshin Kitchen', type: 'KITCHEN' },
    { kitchenId: 7, name: 'Deccan Kitchen', type: 'KITCHEN' }
];

async function favRowInner() {
    try {
        var favs = state.user ? await api('/api/favourites') : null;
        var kitchens = (favs && favs.kitchens && favs.kitchens.length) ? favs.kitchens : DEMO_FAVOURITES;
        if (!kitchens.length) return emptyHtml('❤️', 'No favourites yet', 'Tap the heart on any kitchen to save it here.');
        return '<div class="fav-row">' + kitchens.slice(0, 6).map(function (k) {
            return '<a class="fav-chip" href="' + (k.kitchenId ? '#/kitchen/' + k.kitchenId : '#/kitchens') + '">' +
                '<span class="fc-emoji">🏪</span><div class="fc-name">' + esc(k.name) + '</div></a>';
        }).join('') + '</div>';
    } catch (e) { return ''; }
}

// ==================== Screen 2: Food & Kitchens (category hub) ====================

function itemGroupCard(g) {
    var emoji = g.imageUrl ? '' : emojiFor(g.name);
    return '<a class="item-card" href="#/search/' + encodeURIComponent(g.name) + '">' +
        '<div class="ic-img">' + emoji + '</div>' +
        '<div class="ic-body"><div class="ic-name">' + esc(g.name) + '</div>' +
        '<div class="ic-sub">' + g.kitchenCount + ' kitchen' + (g.kitchenCount === 1 ? '' : 's') + '</div></div></a>';
}

async function foodHubView() {
    var mode = state.viewMode || 'items';
    var h = '<div class="view-enter">' + topBarHtml();
    h += '<div class="page-head"><h1>Food &amp; Kitchens</h1>' +
        '<p class="muted small">What\'s available in your community today?</p></div>';

    // The single, exclusive search entry point across the buyer food experience
    h += '<form class="search-box" data-form="search">' +
        '<span class="search-icon">🔍</span>' +
        '<input class="form-input" id="foodSearch" name="q" placeholder="Search food items..." autocomplete="off">' +
        '</form>';

    // Category tiles (SPECIAL supported by backend, surfaced only when non-empty)
    try {
        var cats = await api('/api/discovery/categories');
        var special = cats.find(function (c) { return c.category === 'SPECIAL' && c.itemCount > 0; });
        var tiles = cats.filter(function (c) { return c.category !== 'SPECIAL'; });
        h += '<div class="cat-grid">' + tiles.map(function (c) {
            return '<a class="cat-tile" href="#/category/' + c.category + '">' +
                '<span class="cat-emoji">' + c.emoji + '</span>' +
                '<span class="cat-name">' + esc(c.label) + '</span>' +
                '<span class="cat-count">' + c.itemCount + ' items</span></a>';
        }).join('');
        if (special) {
            h += '<a class="cat-tile" href="#/category/SPECIAL"><span class="cat-emoji">' + special.emoji + '</span>' +
                '<span class="cat-name">' + esc(special.label) + '</span><span class="cat-count">' + special.itemCount + ' items</span></a>';
        }
        h += '</div>';
    } catch (e) {
        h += emptyHtml('📡', 'Could not load categories', e.message);
    }

    // Mode toggle: By Items vs By Kitchens
    h += '<div class="segmented">' +
        '<button type="button" class="' + (mode === 'items' ? 'active' : '') + '" data-action="set-mode" data-mode="items">By Items</button>' +
        '<button type="button" class="' + (mode === 'kitchens' ? 'active' : '') + '" data-action="set-mode" data-mode="kitchens">By Kitchens</button>' +
        '</div>';

    try {
        if (mode === 'items') {
            var data = await api('/api/discovery/items');
            var items = data.items || [];
            h += items.length ? '<div class="items-grid">' + items.map(itemGroupCard).join('') + '</div>'
                : emptyHtml('🍳', 'Nothing cooking yet', 'No food items are available right now. Check back soon!');
        } else {
            var kitchens = await api('/api/discovery/category-kitchens');
            h += kitchens.length ? '<div class="kitchen-list">' + kitchens.map(kitchenCardHtml).join('') + '</div>'
                : emptyHtml('🏪', 'No kitchens yet', 'No community kitchens are open right now.');
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Something went wrong', e.message);
    }

    // Favourites row
    h += '<div class="section-gap"><div class="top-row" style="margin-bottom:8px">' +
        '<h3>❤️ Favourite Kitchens</h3>' +
        '<a class="small" style="color:var(--brand-3); font-weight:700; text-decoration:none" href="#/favourites">See all →</a></div>' +
        '<div id="favRow">' + await favRowInner() + '</div></div>';

    h += '</div>';
    return h;
}

// ==================== Screen 3: Kitchen discovery ====================

var DISCOVERY_TABS = [
    { id: 'LIVE_NOW', label: 'Live Now' },
    { id: 'TOMORROW', label: 'Tomorrow' },
    { id: 'PREORDER', label: 'Pre-order' },
    { id: 'ALL', label: 'All' }
];

async function kitchensView() {
    var tab = state.kitchenTab || 'LIVE_NOW';
    var h = '<div class="view-enter">';
    h += backBarHtml('Kitchens in your community');

    try {
        var counts = await api('/api/discovery/counts');
        h += '<p class="muted small" style="margin-bottom:14px">' +
            counts.live + ' Live · ' + counts.tomorrow + ' Tomorrow · ' + counts.preorder + ' Pre-order · ' +
            counts.all + ' All</p>';

        h += '<div class="capsule-row">' + DISCOVERY_TABS.map(function (t) {
            return '<button type="button" class="capsule ' + (t.id === tab ? 'active' : '') + '" data-action="set-kitchen-tab" data-tab="' + t.id + '">' + t.label + '</button>';
        }).join('') + '</div>';

        var kitchens = await api('/api/discovery/kitchens?tab=' + tab);
        if (!kitchens.length) {
            var msgs = {
                LIVE_NOW: ['🟢', 'No kitchens are live right now', 'Check the Tomorrow or Pre-order tabs — or come back a bit later.'],
                TOMORROW: ['📅', 'No tomorrow offers yet', 'Kitchens have not listed anything for tomorrow.'],
                PREORDER: ['🔮', 'No pre-orders open', 'No kitchens are accepting pre-orders at the moment.'],
                ALL: ['🏪', 'No kitchens registered yet', 'Be the first — tell a neighbour to open their kitchen on SocioMart!']
            };
            var m = msgs[tab] || msgs.LIVE_NOW;
            h += emptyHtml(m[0], m[1], m[2]);
        } else {
            h += '<div class="kitchen-list">' + kitchens.map(kitchenCardHtml).join('') + '</div>';
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Could not load kitchens', e.message);
    }
    h += '</div>';
    return h;
}


var CAT_META = { BREAKFAST: ['🌅', 'Breakfast'], LUNCH: ['🍛', 'Lunch'], DINNER: ['🌙', 'Dinner'], SNACKS: ['🥟', 'Snacks'], SPECIAL: ['✨', 'Special'] };

async function categoryView(hash) {
    var cat = hash.split('/')[2] || 'LUNCH';
    var mode = state.catMode || 'items';
    var m = CAT_META[cat] || ['🍽️', cat];

    var h = '<div class="view-enter">';
    h += backBarHtml(m[0] + ' ' + m[1]);

    // In-place category switcher — no page reload
    var cats = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACKS', 'SPECIAL'];
    h += '<div class="capsule-row">' + cats.map(function (c) {
        return '<button type="button" class="capsule ' + (c === cat ? 'active' : '') + '" data-action="switch-cat" data-cat="' + c + '">' +
            (CAT_META[c] ? CAT_META[c][0] : '🍽️') + ' ' + (CAT_META[c] ? CAT_META[c][1] : c) + '</button>';
    }).join('') + '</div>';

    try {
        var data = await api('/api/discovery/items?category=' + cat);
        h += '<div class="top-row" style="margin-bottom:10px"><h3>Explore ' + esc(m[1].toLowerCase()) + ' — ' +
            data.count + ' items</h3></div>';
        h += '<div class="segmented">' +
            '<button type="button" class="' + (mode === 'items' ? 'active' : '') + '" data-action="set-cat-mode" data-mode="items">By Items</button>' +
            '<button type="button" class="' + (mode === 'kitchens' ? 'active' : '') + '" data-action="set-cat-mode" data-mode="kitchens">By Kitchens</button>' +
            '</div>';
        if (mode === 'items') {
            var items = data.items || [];
            h += items.length ? '<div class="items-grid">' + items.map(itemGroupCard).join('') + '</div>'
                : emptyHtml('🍽️', 'No items in this category', 'Nothing is available here right now — try another category.');
        } else {
            var kitchens = await api('/api/discovery/category-kitchens?category=' + cat);
            h += kitchens.length ? '<div class="kitchen-list">' + kitchens.map(kitchenCardHtml).join('') + '</div>'
                : emptyHtml('🏪', 'No kitchens for this category', 'Check back later for new kitchens.');
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Could not load', e.message);
    }
    h += '</div>';
    return h;
}

// ==================== Screen 4: Public kitchen page ====================

async function kitchenPageView(hash) {
    var id = hash.split('/')[2];
    var h = '<div class="view-enter">';
    try {
        var detail = await api('/api/kitchens/id/' + id);
        var k = detail.kitchen;
        var today = (detail.products || []).filter(function (p) { return !p.isPreorder; });
        var preorder = (detail.preorderProducts && detail.preorderProducts.length)
            ? detail.preorderProducts
            : (detail.products || []).filter(function (p) { return p.isPreorder; });

        // Hero — banner, avatar, identity, tags, socials, status
        h += '<div class="kitchen-hero">' +
            '<div class="kh-actions">' +
            '<button class="icon-btn ghost" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<button class="icon-btn ghost" type="button" data-action="share-kitchen" aria-label="Share">🔗</button></div>' +
            '<div class="kh-identity">' +
            '<div class="kh-avatar">' + (k.imageUrl ? '' : '🏪') + '</div>' +
            '<div><div class="kh-name">' + esc(k.displayName) + '</div>' +
            '<div class="kh-loc">📍 ' + esc((k.society || LOCATION) + (k.building ? ', ' + k.building : '')) + '</div></div></div>' +
            '<div class="kh-tags">' +
            '<span class="kh-tag">Homemade</span><span class="kh-tag">Fresh</span><span class="kh-tag">Daily</span></div>' +
            '<div class="kh-status">' + (k.availableToday
                ? '<span class="pill pill-green">🟢 Orders Open · until ' + esc(prettyTime(k.orderDeadline || '21:00')) + '</span>'
                : '<span class="pill pill-grey">⚪ Currently closed</span>') + '</div>' +
            '<div class="kh-socials">' +
            (k.whatsappLink ? '<a class="kh-tag" href="' + esc(k.whatsappLink) + '" target="_blank" rel="noopener">💬 WhatsApp</a>' : '') +
            (k.instagramLink ? '<a class="kh-tag" href="' + esc(k.instagramLink) + '" target="_blank" rel="noopener">📸 Instagram</a>' : '') +
            '<button class="kh-tag" type="button" data-action="open-enquiry" data-kid="' + k.id + '" data-kname="' + esc(k.displayName) + '" style="cursor:pointer">✉️ Enquire</button>' +
            '</div></div>';

        // About + gallery
        var about = k.description || k.shortDescription || 'A community kitchen on SocioMart.';
        var shortAbout = about.length > 120 ? about.slice(0, 120) : null;
        h += '<div class="card pad" style="margin-bottom:12px">' +
            '<p class="about-text" id="aboutText">' + esc(shortAbout || about) +
            (shortAbout ? '… <button class="oc-more" type="button" data-action="read-more" data-full="' + encodeURIComponent(about) + '">Read more →</button>' : '') + '</p>' +
            '<h3 class="section-gap" style="margin-bottom:8px">Kitchen Gallery</h3>' +
            '<div class="gallery-strip">' +
            '<div class="gallery-ph">📷</div><div class="gallery-ph">🍛</div><div class="gallery-ph">🥘</div><div class="gallery-ph">☕</div>' +
            '</div><button class="oc-more" type="button" data-action="noop" style="margin-top:6px">View all →</button></div>';

        // Section 1: Available Today
        h += '<h3 class="section-gap" style="margin-bottom:10px">🍽️ Available Today</h3>';
        h += today.length ? today.map(function (p) { return offeringCardHtml(p, k, false); }).join('')
            : emptyHtml('🍽️', 'Nothing available today', 'This kitchen has no offerings for today — check pre-orders below.');

        // Section 2: Pre-order
        h += '<h3 class="section-gap" style="margin-bottom:10px">🔮 Pre-order</h3>';
        h += preorder.length ? preorder.map(function (p) { return offeringCardHtml(p, k, true); }).join('')
            : '<p class="muted small">No pre-order offerings right now.</p>';

        // Coming Up strip (secondary tease of upcoming scheduled dishes)
        var upcoming = preorder.filter(function (p) { return p.availableDate; });
        if (upcoming.length) {
            h += '<h3 class="section-gap" style="margin-bottom:10px">📅 Coming Up</h3><div class="upcoming-strip">' +
                upcoming.slice(0, 6).map(function (p) {
                    return '<div class="upcoming-card"><span class="date-pill">' + esc(prettyDate(p.availableDate)) + '</span>' +
                        '<div style="font-weight:800; font-size:.82rem">' + esc(p.name) + '</div>' +
                        '<div class="tiny muted" style="margin-top:2px">' + money(p.price) + ' · ' + esc(p.priceUnit || 'serving') + '</div></div>';
                }).join('') + '</div>';
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Kitchen not available', e.message);
    }
    h += '</div>';
    return h;
}

function offeringCardHtml(p, kitchen, isPreorderSection) {
    var soldOut = p.soldOut || (p.remainingQuantity != null && p.remainingQuantity <= 0);
    var max = p.maxQuantity || ((p.bookedQuantity || 0) + (p.remainingQuantity || 0)) || 50;
    var booked = p.bookedQuantity || 0;
    var pct = max > 0 ? Math.min(100, Math.round(booked / max * 100)) : 0;
    var cutoffTxt = p.cutoffTime ? ('Order by ' + prettyTime(p.cutoffTime)) : '';
    var timingTxt = p.readyByTime ? (' · Ready ' + p.readyByTime) : '';
    if (isPreorderSection && p.availableDate) {
        cutoffTxt = 'For ' + prettyDate(p.availableDate).toLowerCase() + ', order by ' + (p.cutoffTime ? prettyTime(p.cutoffTime) : '—');
    }
    var kitchenJson = encodeURIComponent(JSON.stringify({ id: kitchen.id, displayName: kitchen.displayName }));
    return '<div class="offering-card' + (soldOut ? ' sold-out' : '') + '">' +
        '<button class="heart-btn oc-heart" type="button" data-action="toggle-fav-product" data-pid="' + p.id + '" aria-label="Favourite">🤍</button>' +
        '<div class="oc-photo">' + emojiFor(p.name) + '</div>' +
        '<div class="oc-body">' +
        '<div class="oc-name-row"><span class="oc-name">' + esc(p.name) + '</span></div>' +
        '<p class="oc-desc">' + esc((p.description || '').slice(0, 70)) +
        ((p.description || '').length > 70 ? '… <button class="oc-more" type="button" data-action="read-more" data-full="' + encodeURIComponent(p.description) + '">More →</button>' : '') + '</p>' +
        '<div class="oc-price">' + money(p.price) + ' <span class="unit">/ ' + esc(p.priceUnit || 'serving') + '</span></div>' +
        '<div class="demand-bar"><div class="demand-track"><div class="demand-fill" style="width:' + pct + '%"></div></div>' +
        '<div class="demand-label">' + booked + ' / ' + max + ' booked</div></div>' +
        '<p class="oc-timing">⏰ ' + esc(cutoffTxt + timingTxt) + '</p>' +
        (soldOut
            ? '<div class="oc-footer"><span class="pill pill-red">🔴 Sold out</span>' +
              '<button class="btn btn-outline btn-sm" disabled>Sold out</button></div>'
            : '<div class="oc-footer"><span class="pill ' + (isPreorderSection ? 'pill-blue">🔵 Pre-order' : 'pill-green">🟢 Today') + '</span>' +
              '<button class="btn btn-primary btn-sm" type="button" data-action="open-order-sheet" data-product="' + encodeURIComponent(JSON.stringify(p)) + '" data-kitchen="' + kitchenJson + '">' +
              (isPreorderSection ? 'PRE-ORDER' : 'ORDER') + '</button></div>') +
        '</div></div>';
}

// ==================== Screen 4A: Item ordering flow (bottom sheet) ====================

var sheet = { product: null, kitchen: null, qty: 1, date: null, slot: null };

function openOrderSheet(productJson, kitchenJson) {
    var p = JSON.parse(decodeURIComponent(productJson));
    var k = JSON.parse(decodeURIComponent(kitchenJson));
    sheet = { product: p, kitchen: k, qty: 1, date: null, slot: null };

    var flex = p.isPreorder && p.preorderType === 'FLEXIBLE';
    var fixed = p.isPreorder && !flex;
    var max = p.remainingQuantity || p.maxQuantity || 10;

    var h = '<h3 class="sheet-title">' + esc(p.name) + '</h3>' +
        '<p class="sheet-sub">' + esc(k.displayName) + ' · ' + money(p.price) + ' / ' + esc(p.priceUnit || 'serving') + '</p>';

    // Type 2: fixed date context revealed here (cutoffs only inside ordering flow)
    if (fixed) {
        h += '<div class="card pad" style="margin-top:12px; background:var(--purple-soft); border:none">' +
            '<span class="pill pill-purple">🔮 ' + esc(prettyDate(p.availableDate)) + '</span>' +
            '<p class="small" style="margin-top:6px">For <strong>' + esc(prettyDate(p.availableDate)) + '</strong>' +
            (p.cutoffTime ? ', cutoff ' + prettyTime(p.cutoffTime) : '') + '</p></div>';
    }

    // Type 3: flexible date selector (invalid dates beyond window are not listed)
    if (flex) {
        var dates = flexDates(p);
        sheet.date = dates[0];
        var slots = (p.timeSlots || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
        sheet.slot = slots[0] || null;
        h += '<div class="form-group" style="margin-top:14px"><label class="form-label">Choose date</label>' +
            '<div class="slot-row">' + dates.map(function (d) {
                return '<button type="button" class="slot-chip" data-action="set-sheet-date" data-date="' + d + '">' + prettyDate(d) + '</button>';
            }).join('') + '</div></div>';
        if (slots.length) {
            h += '<div class="form-group"><label class="form-label">Choose time slot</label>' +
                '<div class="slot-row">' + slots.map(function (s) {
                    return '<button type="button" class="slot-chip" data-action="set-sheet-slot" data-slot="' + esc(s) + '">' + esc(s) + '</button>';
                }).join('') + '</div></div>';
        }
    }

    h += '<div class="qty-row"><span class="small muted">Quantity</span>' +
        '<div class="qty-counter">' +
        '<button class="qty-btn" type="button" data-action="sheet-qty" data-dir="-1" ' + (sheet.qty <= 1 ? 'disabled' : '') + '>−</button>' +
        '<span class="qty-val" id="sheetQty">' + sheet.qty + '</span>' +
        '<button class="qty-btn" type="button" data-action="sheet-qty" data-dir="1" ' + (sheet.qty >= max ? 'disabled' : '') + '>+</button>' +
        '</div></div>';

    h += '<p class="tiny muted" style="text-align:center; margin-bottom:10px" id="sheetTotal">Total: ' + money(p.price * sheet.qty) + '</p>' +
        '<button class="btn btn-primary btn-block" type="button" data-action="sheet-add">Add to Order</button>';

    openSheet(h);
    highlightSheetSelection();
}

function flexDates(p) {
    var out = [];
    var start = new Date(p.availableDate + 'T00:00:00');
    var end = p.availableUntilDate ? new Date(p.availableUntilDate + 'T00:00:00') : start;
    for (var d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        out.push(d.toISOString().slice(0, 10));
    }
    return out;
}

function highlightSheetSelection() {
    $all('[data-action="set-sheet-date"]').forEach(function (b) {
        b.classList.toggle('active', b.dataset.date === sheet.date);
    });
    $all('[data-action="set-sheet-slot"]').forEach(function (b) {
        b.classList.toggle('active', b.dataset.slot === sheet.slot);
    });
}

function sheetQty(dir) {
    var p = sheet.product;
    var max = p.remainingQuantity || p.maxQuantity || 10;
    sheet.qty = Math.min(max, Math.max(1, sheet.qty + dir));
    var q = $('#sheetQty');
    if (q) q.textContent = sheet.qty;
    var t = $('#sheetTotal');
    if (t) t.textContent = 'Total: ' + money(p.price * sheet.qty);
    $all('[data-action="sheet-qty"]').forEach(function (b) {
        b.disabled = (b.dataset.dir === '-1' && sheet.qty <= 1) || (b.dataset.dir === '1' && sheet.qty >= max);
    });
}

function sheetAdd() {
    var p = sheet.product, k = sheet.kitchen;
    var item = {
        productId: p.id, name: p.name, price: p.price, unit: p.priceUnit || 'serving', qty: sheet.qty,
        isPreorder: !!p.isPreorder, preorderType: p.preorderType || null,
        readyBy: p.readyByTime || null, cutoff: p.cutoffTime || null,
        scheduledDate: p.isPreorder ? (sheet.date || p.availableDate || null) : null,
        scheduledSlot: p.isPreorder ? sheet.slot : null
    };
    addToCart(item, k, function (committed) {
        if (committed) closeSheet();
    });
}

// ==================== Enquiry sheet (identity-bound, Screen 4) ====================

function openEnquirySheet(kitchenId, kitchenName) {
    openSheet('<h3 class="sheet-title">✉️ Enquire with ' + esc(kitchenName) + '</h3>' +
        '<p class="sheet-sub">Ask about ingredients, timings or custom requests.</p>' +
        '<form data-form="enquiry" data-kid="' + kitchenId + '" style="margin-top:14px">' +
        '<div class="form-group"><textarea class="form-textarea" name="message" rows="3" ' +
        'placeholder="e.g. Do you make gluten-free parathas?" required></textarea></div>' +
        '<button class="btn btn-primary btn-block" type="submit">Send Enquiry</button></form>');
}

// ==================== Screen 5: Order summary & unified checkout ====================

async function orderSummaryView() {
    var cart = getCart();
    var h = '<div class="view-enter">';
    if (!cart || !cart.items || !cart.items.length) {
        h += backBarHtml('Order Summary') +
            emptyHtml('🛒', 'Your order is empty', 'Add something delicious from a community kitchen first.',
                '<a class="btn btn-primary" href="#/food" style="margin-top:14px">Browse Food & Kitchens</a>');
        h += '</div>';
        return h;
    }

    h += '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 style="flex:1">Order Summary</h2></div>';
    h += '<p class="muted small" style="margin-bottom:12px">🏪 ' + esc(cart.kitchenName) + '</p>';

    h += '<div class="card pad" style="margin-bottom:12px">';
    cart.items.forEach(function (item, idx) {
        h += '<div class="summary-item">' +
            '<div class="si-top"><span class="si-name">' + esc(item.name) + '</span>' +
            '<span class="si-price">' + money(item.price * item.qty) + '</span></div>' +
            '<p class="si-sub">' + money(item.price) + ' × ' + item.qty + ' ' + esc(item.unit || '') + '</p>' +
            fulfilmentBadge(item) +
            (item.isPreorder && item.preorderType === 'FLEXIBLE'
                ? '<div class="si-controls"><span class="tiny muted">Slot: ' + esc(prettyDate(item.scheduledDate)) + ' · ' + esc(item.scheduledSlot || '') + '</span></div>'
                : '') +
            '<div class="si-controls">' +
            '<div class="qty-counter">' +
            '<button class="qty-btn" type="button" data-action="cart-qty" data-idx="' + idx + '" data-dir="-1" ' + (item.qty <= 1 ? 'disabled' : '') + '>−</button>' +
            '<span class="qty-val">' + item.qty + '</span>' +
            '<button class="qty-btn" type="button" data-action="cart-qty" data-idx="' + idx + '" data-dir="1">+</button>' +
            '</div>' +
            '<button class="oc-more" type="button" data-action="cart-remove" data-idx="' + idx + '" style="color:var(--danger)">Remove</button>' +
            '</div></div>';
    });
    h += '<div class="total-row"><span>Item total</span><span>' + money(cartTotal()) + '</span></div></div>';

    h += '<div class="card pad" style="margin-bottom:12px">' +
        '<div class="form-group" style="margin-bottom:0">' +
        '<label class="form-label" for="orderNote">Anything you\'d like the seller to know?</label>' +
        '<textarea class="form-textarea" id="orderNote" rows="2" ' +
        'placeholder="e.g. Less spicy please, ring the bell twice"></textarea></div></div>';

    h += '<div class="sticky-footer-bar"><div class="inner">' +
        '<button class="btn btn-primary btn-block" type="button" data-action="go-checkout">PLACE ORDER — ' + money(cartTotal()) + ' →</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

async function cartQty(idx, dir) {
    var cart = getCart();
    if (!cart || !cart.items[idx]) return;
    var max = cart.items[idx].maxQuantity || 50;
    cart.items[idx].qty = Math.min(max, Math.max(1, cart.items[idx].qty + dir));
    saveCart(cart);
    await render();
}

async function cartRemove(idx) {
    var cart = getCart();
    if (!cart || !cart.items[idx]) return;
    cart.items.splice(idx, 1);
    saveCart(cart);
    await render();
}

/** Build the draft on the backend, then go to the payment screen. */
async function goCheckout() {
    var cart = getCart();
    if (!cart || !cart.items || !cart.items.length) { toast('Your order is empty', 'error'); return; }
    var note = $('#orderNote') ? $('#orderNote').value.trim() : '';
    var items = cart.items.map(function (i) {
        return { productId: i.productId, quantity: i.qty, scheduledDate: i.scheduledDate || null, scheduledSlot: i.scheduledSlot || null };
    });
    try {
        await withAuthGate(async function () {
            await api('/api/buyer/orders/draft?kitchenId=' + cart.kitchenId, { method: 'POST', body: items });
            state.pendingCheckout = { note: note };
            navigate('#/payment');
        });
    } catch (err) {
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    }
}

// ==================== Screen 6: Payment & order confirmation ====================

async function paymentView() {
    var cart = getCart();
    if (!cart || !cart.items || !cart.items.length) {
        return '<div class="view-enter">' + emptyHtml('🧾', 'No active order', 'Your order was already submitted.',
            '<a class="btn btn-primary" href="#/home" style="margin-top:14px">Back to Home</a>') + '</div>';
    }
    var kitchen = null;
    try {
        var detail = await api('/api/kitchens/id/' + cart.kitchenId);
        kitchen = detail.kitchen;
    } catch (e) { kitchen = null; }
    var upiId = (kitchen && kitchen.upiId) || 'sociomart@upi';
    var qrData = encodeURIComponent('upi://pay?pa=' + upiId + '&pn=' + encodeURIComponent(cart.kitchenName) + '&am=' + cartTotal() + '&cu=INR');

    var h = '<div class="view-enter">';
    h += '<div class="top-row"><h2 style="flex:1">Payment</h2></div>';

    // Order receipt
    h += '<div class="card pad" style="margin-bottom:12px">' +
        '<div style="text-align:center"><span class="pill pill-purple">Order #SM' + Date.now().toString().slice(-4) + '</span></div>';
    cart.items.forEach(function (item) {
        h += '<div class="receipt-line"><span>' + esc(item.name) + ' × ' + item.qty + '</span><span>' + money(item.price * item.qty) + '</span></div>';
    });
    h += '<div class="receipt-line" style="font-weight:800; border-top:1px solid var(--border); margin-top:6px; padding-top:10px">' +
        '<span>Total</span><span>' + money(cartTotal()) + '</span></div></div>';

    // Claim-based UPI payment instructions
    h += '<div class="card pad"><h3>Pay via UPI</h3>' +
        '<p class="muted small" style="margin-top:4px">Scan the seller\'s QR or copy the UPI ID, then confirm below.</p>' +
        '<div class="qr-box"><img alt="UPI QR code" src="https://api.qrserver.com/v1/create-qr-code/?size=190x190&data=' + qrData + '">' +
        '<div class="upi-row" style="width:100%"><span class="upi-id">' + esc(upiId) + '</span>' +
        '<button class="btn btn-secondary btn-sm" type="button" data-action="copy-upi" data-upi="' + esc(upiId) + '">Copy</button></div></div>' +
        '<div class="modal-actions">' +
        '<button class="btn btn-outline" type="button" data-action="pay-later">I\'ll pay later</button>' +
        '<button class="btn btn-primary" type="button" data-action="have-paid">I have paid</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

async function submitOrder(claimedPaid) {
    var cart = getCart();
    if (!cart || !cart.items || !cart.items.length) { toast('Your order is empty', 'error'); return; }
    var note = (state.pendingCheckout && state.pendingCheckout.note) || '';
    var items = cart.items.map(function (i) {
        return { productId: i.productId, quantity: i.qty, scheduledDate: i.scheduledDate || null, scheduledSlot: i.scheduledSlot || null };
    });
    try {
        await withAuthGate(async function () {
            await api('/api/buyer/orders/draft?kitchenId=' + cart.kitchenId, { method: 'POST', body: items });
            await api('/api/buyer/orders/place', {
                method: 'POST',
                body: { paymentStatus: claimedPaid ? 'PAID' : 'PENDING', customInstructions: note }
            });
            clearCart();
            state.pendingCheckout = null;
            toast(claimedPaid ? 'Order confirmed — payment pending verification' : 'Order placed — payment pending', claimedPaid ? 'success' : 'info');
            navigate('#/home'); // Spec: both actions route back to Screen 1
        });
    } catch (err) {
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    }
}

// ==================== Screen 7: Search-by-item comparison ====================

async function comparisonView(hash) {
    var itemName = decodeURIComponent(hash.split('/')[2] || '');
    var h = '<div class="view-enter">';
    h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 style="flex:1">' + esc(itemName) + '</h2></div>';

    try {
        var data = await api('/api/discovery/offers?item=' + encodeURIComponent(itemName));
        var offers = data.offers || [];
        h += '<p class="muted small" style="margin-bottom:14px">Currently available from ' + offers.length +
            ' kitchen' + (offers.length === 1 ? '' : 's') + '</p>';

        if (!offers.length) {
            h += emptyHtml('🔍', 'No offers right now', 'No kitchen currently lists "' + itemName +
                '". Try the Food & Kitchens hub for other dishes.',
                '<a class="btn btn-primary" href="#/food" style="margin-top:14px">Back to Food & Kitchens</a>');
        } else {
            h += offers.map(function (o) {
                return '<div class="compare-card">' +
                    '<div class="cc-top">' +
                    '<div class="kc-avatar">' + (o.kitchenImageUrl ? '' : '🏪') + '</div>' +
                    '<div class="kc-info"><div class="kc-name">' + esc(o.kitchenDisplayName) + '</div>' +
                    '<p class="kc-desc">' + esc(o.tagline || '') + '</p>' +
                    '<div class="kc-meta">' + statusPill(o.status) +
                    (o.soldOut ? '<span class="pill pill-red">🔴 Sold out</span>' : '') +
                    (o.previouslyOrdered ? '' : '') + '</div></div></div>' +
                    '<div class="cc-grid">' +
                    '<div class="cc-cell"><div class="cc-label">Price</div><div class="cc-value">' +
                    money(o.price) + ' / ' + esc(o.priceUnit || 'serving') + '</div></div>' +
                    '<div class="cc-cell"><div class="cc-label">Status</div><div class="cc-value">' +
                    (o.soldOut ? 'Sold out' : (o.preorder ? 'Pre-order' : 'Available today')) + '</div></div>' +
                    '<div class="cc-cell"><div class="cc-label">Order by</div><div class="cc-value">' +
                    esc(o.orderBy ? prettyTime(o.orderBy) : '—') + '</div></div>' +
                    '<div class="cc-cell"><div class="cc-label">Ready by</div><div class="cc-value">' +
                    esc(o.readyBy || '—') + '</div></div>' +
                    '</div>' +
                    '<a class="btn btn-secondary btn-block btn-sm" href="#/kitchen/' + o.kitchenId + '">Visit ' +
                    esc(o.kitchenDisplayName) + '\'s Kitchen →</a>' +
                    '</div>';
            }).join('');
        }
    } catch (e) {
        h += emptyHtml('⚠️', 'Could not load offers', e.message);
    }
    h += '</div>';
    return h;
}

// ==================== Screen 8: Favourites ====================

async function favouritesView() {
    var tab = state.favTab || 'kitchens';
    var h = '<div class="view-enter"><div class="page-head"><h1>Favourites</h1></div>';

    h += '<div class="segmented">' +
        ['kitchens', 'food'].map(function (t) {
            return '<button type="button" class="' + (tab === t ? 'active' : '') + '" data-action="set-fav-tab" data-tab="' + t + '">' +
                t.charAt(0).toUpperCase() + t.slice(1) + '</button>';
        }).join('') + '</div>';

    if (tab === 'kitchens' || tab === 'food') {
        var favs = null;
        if (state.user) {
            try { favs = await api('/api/favourites'); } catch (e) { favs = null; }
        }
        var list = favs ? (tab === 'kitchens' ? favs.kitchens : favs.food) : (tab === 'kitchens' ? DEMO_FAVOURITES : []);
        if (!list || !list.length) {
            var demoNote = !state.user ? '<p class="tiny muted" style="margin-top:6px">Showing demo favourites — log in to see yours.</p>' : '';
            h += emptyHtml('❤️', tab === 'kitchens' ? 'No favourite kitchens yet' : 'No favourite food yet',
                tab === 'kitchens' ? 'Tap the heart on any kitchen to save it here.' : 'Tap the heart on any dish to save it here.') + demoNote;
        } else {
            h += list.map(function (f) {
                var href = f.kitchenId ? '#/kitchen/' + f.kitchenId : '#/food';
                return '<a class="fav-list-item" href="' + href + '">' +
                    '<span class="fli-emoji">' + (tab === 'kitchens' ? '🏪' : emojiFor(f.name)) + '</span>' +
                    '<span class="fli-body"><span class="fli-name">' + esc(f.name) + '</span>' +
                    '<span class="fli-sub">' + esc(f.subtitle || f.kitchenName || '') + '</span></span>' +
                    (f.price ? '<span class="si-price">' + money(f.price) + '</span>' : '') +
                    '</a>';
            }).join('');
        }
    }
    h += '</div>';
    return h;
}

// ==================== Screen 8: Orders & Enquiries ====================

var ORDER_BADGES = {
    ORDERED: '<span class="pill pill-amber">🟠 Pending</span>',
    CONFIRMED: '<span class="pill pill-green">🟢 Confirmed</span>',
    READY: '<span class="pill pill-blue">🔵 Ready</span>',
    DELIVERED: '<span class="pill pill-grey">✓ Delivered</span>',
    COMPLETED: '<span class="pill pill-green">✓ Completed</span>',
    CANCELLED: '<span class="pill pill-red">🔴 Cancelled</span>'
};

async function ordersView() {
    var tab = state.ordersTab || 'orders';
    var h = '<div class="view-enter"><div class="page-head"><h1>Orders &amp; Enquiries</h1></div>';

    h += '<div class="segmented">' +
        '<button type="button" class="' + (tab === 'orders' ? 'active' : '') + '" data-action="set-orders-tab" data-tab="orders">Orders</button>' +
        '<button type="button" class="' + (tab === 'enquiries' ? 'active' : '') + '" data-action="set-orders-tab" data-tab="enquiries">Enquiries</button>' +
        '</div>';

    if (!state.user) {
        h += emptyHtml('🔐', 'Login to view your history', 'Your orders and enquiries appear here once you verify your mobile number.',
            '<button class="btn btn-primary" type="button" data-action="open-login" style="margin-top:14px">Login with OTP</button>');
        h += '</div>';
        return h;
    }

    if (tab === 'orders') {
        try {
            var orders = await api('/api/buyer/orders/my');
            var all = [].concat(orders.active || [], orders.completed || []);
            if (!all.length) {
                h += emptyHtml('📋', 'No orders yet', 'When you place your first order it will show up here.',
                    '<a class="btn btn-primary" href="#/food" style="margin-top:14px">Browse Food & Kitchens</a>');
            } else {
                h += all.map(function (o) {
                    var inner = '<div class="order-card"><div class="oc-top-row">' +
                        '<div><div class="si-name">#' + esc(o.orderNumber) + '</div>' +
                        '<p class="si-sub">🏪 ' + esc(o.kitchen ? o.kitchen.displayName : '') + ' · ' +
                        new Date(o.createdAt).toLocaleDateString() + '</p></div>' +
                        (ORDER_BADGES[o.orderStatus] || '') + '</div>';
                    (o.items || []).forEach(function (it) {
                        inner += '<div class="receipt-line"><span>' + esc(it.productName) + ' × ' + it.quantity + '</span></div>';
                    });
                    inner += '<div class="receipt-line" style="font-weight:800"><span>Total</span><span>' + money(o.totalAmount) + '</span></div>' +
                        '<p class="tiny muted" style="margin-top:4px">' +
                        (o.paymentStatus === 'PAID' ? 'Payment claimed — awaiting seller verification' : 'Payment pending') + '</p></div>';
                    return inner;
                }).join('');
            }
        } catch (e) {
            h += emptyHtml('⚠️', 'Could not load orders', e.message);
        }
    } else {
        try {
            var enquiries = await api('/api/enquiries/my');
            if (!enquiries.length) {
                h += emptyHtml('✉️', 'No enquiries yet', 'Use the Enquire button on any kitchen to start a conversation.');
            } else {
                h += enquiries.map(function (en) {
                    return '<div class="order-card"><div class="oc-top-row">' +
                        '<div><div class="si-name">🏪 ' + esc(en.kitchenName) + '</div>' +
                        '<p class="si-sub" style="margin-top:4px">' + esc(en.message) + '</p>' +
                        '<p class="tiny muted" style="margin-top:4px">' + new Date(en.createdAt).toLocaleString() + '</p></div>' +
                        (en.status === 'SELLER_RESPONDED'
                            ? '<span class="pill pill-green">🟢 Seller responded</span>'
                            : '<span class="pill pill-amber">🟠 Waiting for response</span>') +
                        '</div></div>';
                }).join('');
            }
        } catch (e) {
            h += emptyHtml('⚠️', 'Could not load enquiries', e.message);
        }
    }
    h += '</div>';
    return h;
}

// ==================== Screen 8: Profile ====================

async function profileView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Profile</h1></div>';

    if (!state.user) {
        // Logged-out state: login prompt (Spec Screen 8)
        h += '<div class="card pad" style="text-align:center">' +
            '<div style="font-size:2.4rem; margin-bottom:8px">👋</div>' +
            '<h2>Welcome to SocioMart</h2>' +
            '<p class="muted small" style="margin:6px 0 16px">Login with your mobile number to place orders, save favourites and send enquiries.</p>' +
            '<form data-form="profile-login">' +
            '<div class="form-group"><input class="form-input" name="mobileNumber" inputmode="numeric" maxlength="10" placeholder="10-digit mobile number" required></div>' +
            '<button class="btn btn-primary btn-block" type="submit">Login with OTP</button></form>' +
            '<p class="tiny muted" style="margin-top:10px">You can keep browsing without logging in.</p>' +
            '</div></div>';
        return h;
    }

    // Logged-in state
    var u = state.user;
    h += '<div class="card pad" style="margin-bottom:12px">' +
        '<div style="display:flex; align-items:center; gap:14px">' +
        '<div class="kc-avatar" style="width:60px; height:60px; font-size:1.7rem">' + esc((u.name || '?').charAt(0).toUpperCase()) + '</div>' +
        '<div><h2>' + esc(u.name || 'Buyer') + '</h2>' +
        '<p class="muted small">📱 ' + esc(u.mobileNumber || '') + '</p></div></div></div>';

    h += '<form data-form="profile-edit"><div class="card pad" style="margin-bottom:12px">' +
        '<div class="profile-row"><span class="pr-label">Name</span>' +
        '<input class="form-input" name="name" value="' + esc(u.name || '') + '" style="max-width:200px; padding:8px 10px"></div>' +
        '<div class="profile-row"><span class="pr-label">Community / Society</span>' +
        '<input class="form-input" name="society" value="' + esc(u.society || LOCATION) + '" style="max-width:200px; padding:8px 10px"></div>' +
        '<div class="profile-row"><span class="pr-label">Building</span>' +
        '<input class="form-input" name="building" value="' + esc(u.building || '') + '" style="max-width:200px; padding:8px 10px"></div>' +
        '<div class="profile-row"><span class="pr-label">Flat #</span>' +
        '<input class="form-input" name="flatHouseNumber" value="' + esc(u.flatHouseNumber || '') + '" style="max-width:200px; padding:8px 10px"></div>' +
        '<button class="btn btn-secondary btn-block btn-sm" type="submit" style="margin-top:12px">Save Profile</button></div></form>';

    h += '<div class="card pad"><button class="btn btn-danger btn-block" type="button" data-action="logout">Log Out</button></div>';

    h += '</div>';
    return h;
}
