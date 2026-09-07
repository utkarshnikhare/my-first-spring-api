/**
 * SocioMart Buyer App v1.0 — Buyer screens
 * Screens: 1 Home · 2 Food & Kitchens hub · 2A Category detail ·
 * 3 Kitchen discovery · 4 Public kitchen · 4A Ordering sheet ·
 * 5 Order summary · 6 Payment · 7 Comparison · 8 Favourites/Orders/Profile
 */

// ==================== Favourites UI helpers ====================

var FAV_CACHE = null; // Set of favourited kitchen ids; null = not loaded yet

function favSet() {
    return FAV_CACHE && FAV_CACHE.size ? FAV_CACHE : new Set();
}

async function loadFavSet() {
    FAV_CACHE = new Set();
    if (!state.user) return FAV_CACHE;
    try {
        var favs = await api('/api/favourites');
        (favs || []).forEach(function (f) {
            if (f && f.kitchenId) FAV_CACHE.add(String(f.kitchenId));
        });
    } catch (e) { /* not authenticated — hearts render unfavourited */ }
    return FAV_CACHE;
}

function heartBtnHtml(kid, label) {
    var faved = favSet().has(String(kid));
    return '<button class="heart-btn' + (faved ? ' faved' : '') + '" type="button" ' +
        'data-action="toggle-fav-kitchen" data-kid="' + kid + '" ' +
        'aria-label="' + (faved ? 'Remove ' + esc(label) + ' from favourites' : 'Save ' + esc(label) + ' to favourites') + '" ' +
        'aria-pressed="' + (faved ? 'true' : 'false') + '" title="' + (faved ? 'Remove from favourites' : 'Save to favourites') + '">' +
        (faved ? '❤️' : '🤍') + '</button>';
}

/** Local demo imagery: warm gradient tile with a data-emoji fallback glyph. */
function demoImg(emoji, cls) {
    return '<div class="' + (cls || 'demo-img') + '" data-emoji="' + emoji + '"></div>';
}

/** Render a tile that shows an emoji glyph, or an <img> that degrades back
 *  to the emoji (via data-emoji) whenever the remote image cannot load. */
function dishImg(containerCls, emoji, url, alt) {
    return '<div class="' + containerCls + '" data-emoji="' + emoji + '">' +
        (url ? '<img src="' + esc(url) + '" alt="' + esc(alt) + '" onerror="imgFallback(this)">' : emoji) +
        '</div>';
}

/** A kitchen's imageUrl is only usable when it is a real http(s) link (not example.com). */
function usableImageUrl(url) {
    return url && (url.slice(0, 8) === 'https://' || url.slice(0, 7) === 'http://') && url.indexOf('example.com') < 0;
}

// ==================== Shared UI fragments ====================

var LOCATION = 'Pride World City';

function topBarHtml(opts) {
    opts = opts || {};
    return '<div class="top-row">' +
        '<span class="loc-pill">📍 ' + esc(opts.location || LOCATION) + '</span>' +
        '<span class="bell-wrap">' +
        '<button class="icon-btn" type="button" data-action="toggle-notifs" aria-label="Notifications">🔔' +
        '<span class="bell-badge">3</span></button>' +
        '<button class="icon-btn" type="button" data-action="toggle-theme" aria-label="Toggle theme">🌓</button>' +
        '<div class="notif-panel" id="notifPanel" hidden>' +
        '<div class="notif-item unread">🟢 Your kitchen Aarti Kitchen confirmed today\'s menu</div>' +
        '<div class="notif-item unread">🍽️ Poha is live from 4 kitchens near you</div>' +
        '<div class="notif-item">📦 Order #SM1024 marked Ready for pickup</div>' +
        '</div></span></div>';
}

function backBarHtml(title) {
    return '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1 font-700">' + esc(title) + '</h2>' +
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
    var kUrl = '#/kitchen/' + k.id;
    return '<div class="kitchen-card">' +
        '<div class="kc-top">' +
        '<a class="kc-avatar" href="' + kUrl + '" aria-label="Open ' + esc(k.displayName || '') + '">' +
        (usableImageUrl(k.imageUrl) ? '<img src="' + esc(k.imageUrl) + '" alt="' + esc(k.displayName) + '" onerror="imgFallback(this)">' : '🏪') + '</a>' +
        '<div class="kc-info">' +
        '<div class="kc-name-row"><a class="kc-name" href="' + kUrl + '">' + esc(k.displayName) + '</a>' +
        heartBtnHtml(k.id, k.displayName) + '</div>' +
        '<p class="kc-desc">' + esc(k.shortDescription || '') + '</p>' +
        '<div class="kc-meta">' + statusPill(k.status) +
        '<span class="pill pill-grey">' + (k.orderableItemCount || 0) + ' item' + ((k.orderableItemCount || 0) === 1 ? '' : 's') + ' today</span>' +
        (k.rating ? '<span class="pill-gold">★ ' + esc(String(k.rating)) + '</span>' : '') +
        (k.previouslyOrdered ? '<span class="trust-badge">↩ Previously ordered</span>' : '') +
        '</div></div></div>' +
        (preview ? '<p class="kc-items">' + preview + more + '</p>' : '') +
        '<div class="kc-actions"><a class="btn btn-secondary btn-sm" href="' + kUrl + '">View Kitchen →</a></div>' +
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

    // Hero with branding
    h += '<div class="hero">' +
        '<div class="hero-brand"><div class="hero-logo">🏪</div>' +
        '<span class="hero-tagline">COMMUNITY MARKETPLACE</span></div>' +
        '<h1>Welcome to SocioMart</h1>' +
        '<p class="sub">Fresh homemade food from trusted home kitchens near you.</p>' +
        '<p class="prompt">What are you looking for?</p>' +
        '</div>';

    // Tiles grid — Food & Kitchens is the active module
    h += '<div class="tiles-grid">' +
        '<a class="tile" href="#/food">' +
        '<span class="tile-icon">🍽️</span><span class="tile-name">Food &amp; Kitchens</span>' +
        '<span class="pill pill-green">● AVAILABLE NOW</span></a>' +
        '</div>';

    // Favourites row
    h += '<div class="section-gap"><div class="top-row mb-2">' +
        '<h3>❤️ Favourite Kitchens</h3>' +
        '<a class="small text-sm font-700 text-brand no-underline" href="#/favourites">See all →</a></div>' +
        '<div id="favRow">' + await favRowInner() + '</div></div>';

    // Footer — marketplace summary
    h += '<p class="muted small section-gap text-center py-1">' +
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
        // /api/favourites returns a flat array of FavouriteDto (backend), NOT a {kitchens} envelope.
        var kitchens = (favs && favs.length) ? favs : DEMO_FAVOURITES;
        if (!kitchens.length) return emptyHtml('❤️', 'No favourites yet', 'Tap the heart on any kitchen to save it here.');
        return '<div class="fav-row">' + kitchens.slice(0, 6).map(function (k) {
            return '<a class="fav-chip" href="' + (k.kitchenId ? '#/kitchen/' + k.kitchenId : '#/kitchens') + '">' +
                '<span class="fc-emoji">' + (usableImageUrl(k.imageUrl) ? '<img src="' + esc(k.imageUrl) + '" alt="' + esc(k.name) + '" onerror="imgFallback(this)">' : '🏪') + '</span>' +
                '<div class="fc-name">' + esc(k.name) + '</div></a>';
        }).join('') + '</div>';
    } catch (e) { return ''; }
}

// ==================== Screen 2: Food & Kitchens (category hub) ====================

function itemGroupCard(g) {
    return '<a class="item-card" href="#/search/' + encodeURIComponent(g.name) + '">' +
        dishImg('ic-img', emojiFor(g.name), usableImageUrl(g.imageUrl) ? g.imageUrl : '', g.name) +
        '<div class="ic-body"><div class="ic-name">' + esc(g.name) + '</div>' +
        '<div class="ic-sub">' + g.kitchenCount + ' kitchen' + (g.kitchenCount === 1 ? '' : 's') + '</div></div></a>';
}

async function foodHubView() {
    var mode = state.viewMode || 'items';
    await loadFavSet();
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
    h += '<div class="section-gap"><div class="top-row mb-2">' +
        '<h3>❤️ Favourite Kitchens</h3>' +
        '<a class="small text-sm font-700 text-brand no-underline" href="#/favourites">See all →</a></div>' +
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
    h += backBarHtml('All Kitchens');
    await loadFavSet();

    try {
        var counts = await api('/api/discovery/counts');

        // Marketplace header — shareable page, works from a direct URL, keeps nav intact
        h += '<div class="shop-header">' +
            '<h1>🏪 All Kitchens</h1>' +
            '<p class="shop-sub">Fresh homemade food from trusted home kitchens in your community — order today or pre-order for later.</p>' +
            '<div class="shop-stats">' +
            '<span class="shop-stat">🟢 ' + counts.live + ' Live</span>' +
            '<span class="shop-stat">📅 ' + counts.tomorrow + ' Tomorrow</span>' +
            '<span class="shop-stat">🔮 ' + counts.preorder + ' Pre-order</span>' +
            '<span class="shop-stat">🏪 ' + counts.all + ' All</span>' +
            '</div></div>';

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
            h += '<div class="empty-live">' + emptyHtml(m[0], m[1], m[2]) + '</div>';
        } else {
            h += '<div class="kitchen-list">' + kitchens.map(kitchenCardHtml).join('') + '</div>';
        }
    } catch (e) {
        h += '<div class="empty-live">' + emptyHtml('⚠️', 'Could not load kitchens', e.message,
            '<button class="btn btn-primary card-mt" type="button" data-action="set-kitchen-tab" data-tab="' + tab + '">Try again</button>') + '</div>';
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
        await loadFavSet();
        var data = await api('/api/discovery/items?category=' + cat);
        h += '<div class="top-row mb-2"><h3>Explore ' + esc(m[1].toLowerCase()) + ' — ' +
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

        // Hero — banner, avatar, identity, tags, socials (no kitchen-level
        // "Orders Open" indicator; item-level timing lives on each offering card)
        var khAvatar = dishImg('kh-avatar', '🏪', usableImageUrl(k.imageUrl) ? k.imageUrl : '', k.displayName || 'kitchen');
        h += '<div class="kitchen-hero">' +
            '<div class="kh-actions">' +
            '<button class="icon-btn ghost" type="button" data-action="go-back" aria-label="Back">←</button>' +
            '<button class="icon-btn ghost" type="button" data-action="share-kitchen" aria-label="Share">🔗</button></div>' +
            '<div class="kh-identity">' +
            khAvatar +
            '<div><div class="kh-name">' + esc(k.displayName) + '</div>' +
            '<div class="kh-loc">📍 ' + esc((k.society || LOCATION) + (k.building ? ', ' + k.building : '')) + '</div>' +
            (k.rating ? '<div class="kh-loc"><span class="pill-gold">★ ' + esc(String(k.rating)) + '</span> <span class="tiny muted">home kitchen rating</span></div>' : '') +
            '</div></div>' +
            '<div class="kh-tags">' +
            '<span class="kh-tag">Homemade</span><span class="kh-tag">Fresh</span><span class="kh-tag">Daily</span></div>' +
            '<div class="kh-socials">' +
            (k.whatsappLink ? '<a class="kh-tag kh-social-link" href="' + esc(k.whatsappLink) + '" target="_blank" rel="noopener">💬 WhatsApp</a>' : '') +
            (k.instagramLink ? '<a class="kh-social-link instagram" href="' + esc(k.instagramLink) + '" target="_blank" rel="noopener" aria-label="Follow ' + esc(k.displayName) + ' on Instagram">📸 Instagram</a>' : '') +
            '<button class="kh-tag kh-social-link cursor-pointer" type="button" data-action="open-enquiry" data-kid="' + k.id + '" data-kname="' + esc(k.displayName) + '">✉️ Enquire</button>' +
            '</div></div>';

        // Service area — informative, not a warning (area comes from the seller's onboarding data)
        if (k.society) {
            h += '<div class="service-area">' +
                '<span class="sa-icon" aria-hidden="true">🏘️</span>' +
                '<div><span class="sa-title">Service area</span>' +
                'Orders are currently limited to <strong>' + esc(k.society) + '</strong> and may be limited to selected societies.</div></div>';
        }

        // About + gallery (local demo imagery)
        var about = k.description || k.shortDescription || 'A community kitchen on SocioMart.';
        var shortAbout = about.length > 120 ? about.slice(0, 120) : null;
        h += '<div class="card pad card-mb">' +
            '<p class="about-text" id="aboutText">' + esc(shortAbout || about) +
            (shortAbout ? '… <button class="oc-more" type="button" data-action="read-more" data-full="' + encodeURIComponent(about) + '">Read more →</button>' : '') + '</p>' +
            '<h3 class="section-gap mb-2">Kitchen Gallery</h3>' +
            '<div class="gallery-strip">' +
            '<div class="gallery-ph" data-emoji="🏠">🏠</div>' +
            '<div class="gallery-ph" data-emoji="🍛">🍛</div>' +
            '<div class="gallery-ph" data-emoji="🥘">🥘</div>' +
            '<div class="gallery-ph" data-emoji="☕">☕</div>' +
            '</div><p class="tiny muted mt-1">A peek at the kitchen — fresh, home-cooked and made with care.</p></div>';

        // Section 1: Available Today
        h += '<h3 class="section-gap mb-2">🍽️ Available Today</h3>';
        h += today.length ? today.map(function (p) { return offeringCardHtml(p, k, false); }).join('')
            : emptyHtml('🍽️', 'Nothing available today', 'This kitchen has no offerings for today — check pre-orders below.');

        // Section 2: Pre-order
        h += '<h3 class="section-gap mb-2">🔮 Pre-order</h3>';
        h += preorder.length ? preorder.map(function (p) { return offeringCardHtml(p, k, true); }).join('')
            : '<p class="muted small">No pre-order offerings right now.</p>';

        // Coming Up strip (secondary tease of upcoming scheduled dishes)
        var upcoming = preorder.filter(function (p) { return p.availableDate; });
        if (upcoming.length) {
            h += '<h3 class="section-gap mb-2">📅 Coming Up</h3><div class="upcoming-strip">' +
                upcoming.slice(0, 6).map(function (p) {
                    return '<div class="upcoming-card"><span class="date-pill">' + esc(prettyDate(p.availableDate)) + '</span>' +
                        '<div class="font-800 text-sm">' + esc(p.name) + '</div>' +
                        '<div class="tiny muted mt-1">' + money(p.price) + ' · ' + esc(p.priceUnit || 'serving') + '</div>' +
                        '<div class="tiny muted mt-1">⏰ Order by ' + (p.cutoffTime ? esc(prettyTime(p.cutoffTime)) : '—') + '</div></div>';
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
    var isPre = !!p.isPreorder || !!isPreorderSection;
    var kitchenJson = encodeURIComponent(JSON.stringify({ id: kitchen.id, displayName: kitchen.displayName }));
    var timingHtml;
    if (isPre) {
        var cut = p.cutoffTime ? prettyTime(p.cutoffTime) : '—';
        timingHtml = '<span class="oc-cutoff">⏰ Order cutoff: ' + esc(cut) + '</span>' +
            '<div class="oc-delivers">📅 ' + esc(prettyDate(p.availableDate)) + '</div>';
    } else {
        var t1 = p.cutoffTime ? ('Order by ' + prettyTime(p.cutoffTime)) : '';
        var t2 = p.readyByTime ? (' · Ready ' + p.readyByTime) : '';
        timingHtml = '<p class="oc-timing">⏰ ' + esc(t1 + t2) + '</p>';
    }
    return '<div class="offering-card' + (soldOut ? ' sold-out' : '') + (isPre ? ' is-preorder' : '') + '">' +
        dishImg('oc-photo', emojiFor(p.name), usableImageUrl(p.imageUrl) ? p.imageUrl : '', p.name) +
        '<div class="oc-body">' +
        '<div class="oc-name-row"><span class="oc-name">' + esc(p.name) + '</span>' +
        (isPre ? '<span class="oc-preorder-badge">🔮 Pre-order</span>' : '') + '</div>' +
        '<p class="oc-desc">' + esc((p.description || '').slice(0, 70)) +
        ((p.description || '').length > 70 ? '… <button class="oc-more" type="button" data-action="read-more" data-full="' + encodeURIComponent(p.description) + '">More →</button>' : '') + '</p>' +
        '<div class="oc-price">' + money(p.price) + ' <span class="unit">/ ' + esc(p.priceUnit || 'serving') + '</span></div>' +
        timingHtml +
        '<div class="demand-bar"><div class="demand-track"><div class="demand-fill" style="width:' + pct + '%"></div></div>' +
        '<div class="demand-label">' + booked + ' / ' + max + ' booked</div></div>' +
        (soldOut
            ? '<div class="oc-footer"><span class="pill pill-red">🔴 Sold out</span>' +
              '<button class="btn btn-outline btn-sm" disabled>Sold out</button></div>'
            : '<div class="oc-footer"><span class="pill ' + (isPre ? 'pill-blue">🔵 Pre-order' : 'pill-green">🟢 Today') + '</span>' +
              '<button class="btn btn-primary btn-sm" type="button" data-action="open-order-sheet" data-product="' + encodeURIComponent(JSON.stringify(p)) + '" data-kitchen="' + kitchenJson + '">' +
              (isPre ? 'PRE-ORDER' : 'ORDER') + '</button></div>') +
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
        h += '<div class="card pad card-mt card-purple">' +
            '<span class="pill pill-purple">🔮 ' + esc(prettyDate(p.availableDate)) + '</span>' +
            '<p class="small mt-1">For <strong>' + esc(prettyDate(p.availableDate)) + '</strong>' +
            (p.cutoffTime ? ', cutoff ' + prettyTime(p.cutoffTime) : '') + '</p></div>';
    }

    // Type 3: flexible date selector (invalid dates beyond window are not listed)
    if (flex) {
        var dates = flexDates(p);
        sheet.date = dates[0];
        var slots = (p.timeSlots || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
        sheet.slot = slots[0] || null;
        h += '<div class="form-group card-mt"><label class="form-label">Choose date</label>' +
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

        h += '<p class="tiny muted mt-1" id="sheetTotal">Total: ' + money(p.price * sheet.qty) + '</p>' +
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
        '<form data-form="enquiry" data-kid="' + kitchenId + '" class="card-mt">' +
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
                '<a class="btn btn-primary card-mt" href="#/food">Browse Food & Kitchens</a>');
        h += '</div>';
        return h;
    }

    h += '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1 font-700">Order Summary</h2></div>';
    h += '<p class="muted small text-sm card-mb">🏪 ' + esc(cart.kitchenName) + '</p>';

    h += '<div class="card pad card-mb">';
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
            '<button class="oc-more text-danger" type="button" data-action="cart-remove" data-idx="' + idx + '">Remove</button>' +
            '</div></div>';
    });
    h += '<div class="total-row"><span>Item total</span><span>' + money(cartTotal()) + '</span></div></div>';

    h += '<div class="card pad card-mb">' +
        '<div class="form-group mb-0">' +
        '<label class="form-label" for="orderNote">Anything you\'d like the seller to know?</label>' +
        '<textarea class="form-textarea" id="orderNote" rows="2" ' +
        'placeholder="e.g. Less spicy please, ring the bell twice"></textarea></div></div>';

    h += '<div class="sticky-footer-bar"><div class="inner">' +
        '<button class="btn btn-submit btn-block" type="button" data-action="go-checkout">Place Order ✓ — ' + money(cartTotal()) + ' →</button>' +
        '<p class="tiny muted text-center mt-1">Review your items, then choose your payment status on the next step.</p>' +
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
            // Session may be valid even when this tab's UI state isn't (e.g. login
            // happened in another tab). Sync the UI identity so Confirm Order and
            // My Orders show the real buyer. Display-state only — no flow change.
            if (!state.user) {
                try {
                    var me = await api('/api/auth/me');
                    if (me && me.authenticated) state.user = me;
                } catch (e2) {}
            }
            state.pendingCheckout = { note: note };
            navigate('#/confirm'); // Draft created once (idempotent) → review → payment
        });
    } catch (err) {
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    }
}

/** Place the draft order directly with the selected payment status. */
async function placeOrderWithStatus(paymentStatus) {
    if (state.placingOrder) return;
    var note = (state.pendingCheckout && state.pendingCheckout.note) || '';
    state.placingOrder = true;
    try {
        await withAuthGate(async function () {
            var placed = await api('/api/buyer/orders/place', {
                method: 'POST',
                body: { paymentStatus: paymentStatus, customInstructions: note }
            });
            clearCart();
            state.pendingCheckout = null;
            state.lastOrder = placed;
            navigate('#/payment-success');
        });
    } catch (err) {
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    } finally {
        state.placingOrder = false;
    }
}

// ==================== Screen 5b: Confirm Order (review before payment) ====================

/**
 * Confirm Order page — dynamic review of the real backend DRAFT.
 * No API mutations here: opening/reloading this page can never create a
 * duplicate order. Only the "Place Order" button (with the chosen payment
 * status) submits the order via POST /api/buyer/orders/place.
 */
async function confirmOrderView() {
    var h = '<div class="view-enter">';
    var draft = null;
    try {
        draft = await api('/api/buyer/orders/draft');
    } catch (e) { draft = null; }
    if (!draft || !draft.items || !draft.items.length) {
        h += backBarHtml('Confirm Order') +
            emptyHtml('🧾', 'No active order', 'Your order was already submitted or your selection expired.',
                '<a class="btn btn-primary card-mt" href="#/food">Browse Food &amp; Kitchens</a>');
        h += '</div>';
        return h;
    }

    h += '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1 font-700">Confirm Order</h2></div>';

    // 1+2: What am I ordering & from which kitchen
    h += '<div class="card pad card-mb">' +
        '<div class="flex items-center justify-between gap-2">' +
        '<h3 class="font-700">' + esc(draft.kitchen && draft.kitchen.displayName ? draft.kitchen.displayName : 'Kitchen') + '</h3>' +
        '<span class="pill pill-purple">' + esc(draft.orderNumber || '') + '</span></div>' +
        '<p class="muted small mt-1">Review your order before payment</p></div>';

    // 3+4: Items, quantity, prices
    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Your items</h3>';
    (draft.items || []).forEach(function (it) {
        h += '<div class="summary-item">' +
            '<div class="si-top"><span class="si-name">' + esc(it.productName) + '</span>' +
            '<span class="si-price">' + money(it.price * it.quantity) + '</span></div>' +
            '<p class="si-sub">Quantity: ' + it.quantity + ' · ' + money(it.price) + ' each</p>' +
            (it.scheduledDate || it.scheduledSlot
                ? '<p class="si-sub">📅 ' + esc(prettyDate(it.scheduledDate)) + (it.scheduledSlot ? ' · ' + esc(it.scheduledSlot) : '') + '</p>'
                : '') +
            '</div>';
    });
    h += '<div class="total-row"><span>Order total</span><span>' + money(draft.totalAmount) + '</span></div></div>';

    // 5: Delivery
    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Delivery</h3>' +
        '<p class="muted small">📦 Today · standard community delivery slot' +
        ((draft.items || []).some(function (it) { return it.scheduledDate || it.scheduledSlot; })
            ? ' (pre-order items show their chosen date/slot above)' : '') + '</p></div>';

    // 6: Delivery address from the logged-in buyer profile
    var u = state.user || {};
    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Deliver to</h3>' +
        '<p class="font-700 mb-1">' + esc(u.name || 'Buyer') + '</p>' +
        '<p class="muted small">' +
        esc([u.society || LOCATION, u.building, u.flatHouseNumber].filter(Boolean).join(' · ')) +
        (u.mobileNumber ? '<br>📱 ' + esc(u.mobileNumber) : '') + '</p></div>';

    // Remark carried from the Order Summary note
    var note = (state.pendingCheckout && state.pendingCheckout.note) || '';
    if (note) {
        h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Your note to the kitchen</h3>' +
            '<p class="muted small">💬 ' + esc(note) + '</p></div>';
    }

    // 7: Payment-status selection (UI only — no gateway, cards or UPI) + Place Order
    var pref = state.payPreference || 'PAID';
    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Payment status</h3>' +
        '<div class="pay-status' + (pref === 'PAID' ? ' selected' : '') + '" role="radio" aria-checked="' + (pref === 'PAID' ? 'true' : 'false') + '" data-action="select-pay-status" data-status="PAID">' +
        '<span class="ps-radio" aria-hidden="true"></span>' +
        '<span class="ps-icon" aria-hidden="true">✅</span>' +
        '<span class="ps-body"><span class="ps-name">Paid</span>' +
        '<span class="ps-sub">Confirm this order as paid now. Demo selection only.</span></span></div>' +
        '<div class="pay-status' + (pref === 'WILL_PAY_LATER' ? ' selected' : '') + '" role="radio" aria-checked="' + (pref === 'WILL_PAY_LATER' ? 'true' : 'false') + '" data-action="select-pay-status" data-status="WILL_PAY_LATER">' +
        '<span class="ps-radio" aria-hidden="true"></span>' +
        '<span class="ps-icon" aria-hidden="true">⏳</span>' +
        '<span class="ps-body"><span class="ps-name">Will Pay Later</span>' +
        '<span class="ps-sub">Pay when the order is delivered or picked up.</span></span></div>' +
        '<p class="pay-status-note mt-1">🔒 This is only a payment-status selection for your order record — no payment is processed here.</p></div>';

    h += '<div class="sticky-footer-bar"><div class="inner">' +
        '<button class="btn btn-submit btn-block" type="button" data-action="place-order" id="placeOrderBtn">' +
        'Place Order ✓ — ' + money(draft.totalAmount) + '</button>' +
        '<p class="tiny muted text-center mt-1">Review everything above before submitting.</p>' +
        '</div></div>';

    h += '</div>';
    return h;
}

// ==================== Screen 6: Payment & order confirmation (internal demo) ====================

/**
 * Internal demo payment flow — no external gateways, no OTP, no redirects.
 * Reads the real backend DRAFT so the receipt shows true DB data.
 * Methods: UPI (Demo) · Demo Card · Cash on Delivery.
 * PAID  → backend places order as CONFIRMED (paid immediately in demo).
 * COD   → backend places order as ORDERED / payment PENDING (pay on delivery).
 */
var PAY_METHODS = [
    { id: 'upi', icon: '📱', name: 'UPI (Demo)', sub: 'Simulated UPI — no real money moves' },
    { id: 'card', icon: '💳', name: 'Demo Card', sub: 'Simulated card — no card details collected' },
    { id: 'cod', icon: '💵', name: 'Cash on Delivery', sub: 'Pay cash when the order arrives' }
];

async function paymentView() {
    var h = '<div class="view-enter">';
    var draft = null;
    try {
        draft = await api('/api/buyer/orders/draft');
    } catch (e) { draft = null; }
    if (!draft || !draft.items || !draft.items.length) {
        h += backBarHtml('Payment') +
            emptyHtml('🧾', 'No active order', 'Your order was already submitted or your session expired.',
                '<a class="btn btn-primary card-mt" href="#/food">Browse Food &amp; Kitchens</a>');
        h += '</div>';
        return h;
    }

    h += '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1 font-700">Payment</h2></div>';

    // Order summary (real draft data from the shared database)
    h += '<div class="card pad card-mb">' +
        '<div class="flex items-center justify-between">' +
        '<h3 class="font-700">Order Summary</h3>' +
        '<span class="pill pill-purple">' + esc(draft.orderNumber || '') + '</span></div>' +
        '<p class="muted small my-2">🏪 ' + esc(draft.kitchen && draft.kitchen.displayName ? draft.kitchen.displayName : '') + '</p>';
    (draft.items || []).forEach(function (it) {
        h += '<div class="receipt-line"><span>' + esc(it.productName) + ' × ' + it.quantity + '</span><span>' + money(it.price * it.quantity) + '</span></div>';
    });
    h += '<div class="receipt-line font-800 border-top mt-2 pt-2">' +
        '<span>Total payable</span><span>' + money(draft.totalAmount) + '</span></div></div>';

    // Payment method selection (demo only)
    var method = state.payMethod || 'upi';
    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Payment method</h3>' +
        '<p class="tiny muted mb-2">This is a client demo — payments are simulated inside SocioMart. No real money, cards, OTPs or external sites are involved.</p>' +
        PAY_METHODS.map(function (m) {
            var sel = method === m.id;
            return '<label class="pay-method' + (sel ? ' selected' : '') + '" data-action="select-pay-method" data-method="' + m.id + '">' +
                '<span class="pm-radio" aria-hidden="true"></span>' +
                '<span class="pm-icon" aria-hidden="true">' + m.icon + '</span>' +
                '<span class="pm-body"><span class="pm-name">' + m.name + '</span>' +
                '<span class="pm-sub">' + m.sub + '</span></span>' +
                '</label>';
        }).join('');

    // Demo UPI details — clearly fictional, shown for the UPI method
    if (method === 'upi') {
        h += '<div class="upi-box">' +
            '<div class="upi-box-title">Pay to this UPI ID</div>' +
            '<div class="upi-row"><span class="upi-id">demo@sociomart</span>' +
            '<button class="btn btn-secondary btn-sm" type="button" data-action="copy-upi" data-upi="demo@sociomart">Copy</button></div>' +
            '<p class="tiny muted mt-2">Demo payment only — this UPI ID is sample data for demonstration purposes and is not connected to any real account.</p>' +
            '</div>';
    }

    h += '<div class="receipt-line card-mt"><span class="muted">Payment status</span>' +
        '<span class="pill pill-amber">Pending</span></div></div>';

    var isCod = method === 'cod';
    h += '<div class="sticky-footer-bar"><div class="inner">' +
        '<button class="btn btn-primary btn-block" type="button" id="payNowBtn" data-action="confirm-payment"' +
        (isCod ? ' data-cod="1"' : '') + '>' +
        (isCod ? 'PLACE ORDER (PAY ON DELIVERY) — ' : 'PAY NOW — ') + money(draft.totalAmount) + '</button>' +
        '</div></div>';

    h += '</div>';
    return h;
}

/** Confirm payment: processing state → place real order via API → success screen. */
async function confirmPayment(btnEl) {
    if (state.placingOrder) return; // idempotency guard: no double submissions
    var note = (state.pendingCheckout && state.pendingCheckout.note) || '';
    var cod = btnEl && btnEl.dataset.cod === '1';
    state.placingOrder = true;
    var original = btnEl ? btnEl.innerHTML : '';
    if (btnEl) {
        btnEl.disabled = true;
        btnEl.innerHTML = '<span class="btn-spinner"></span> ' + (cod ? 'Placing order...' : 'Processing payment...');
    }
    try {
        await withAuthGate(async function () {
            var placed = await api('/api/buyer/orders/place', {
                method: 'POST',
                body: { paymentStatus: cod ? 'WILL_PAY_LATER' : 'PAID', customInstructions: note }
            });
            clearCart();
            state.pendingCheckout = null;
            state.lastOrder = placed;
            navigate('#/payment-success');
        });
    } catch (err) {
        if (btnEl) { btnEl.disabled = false; btnEl.innerHTML = original; }
        if (!(err instanceof ApiError && err.status === 401)) toast(err.message, 'error');
    } finally {
        state.placingOrder = false;
    }
}

// ==================== Screen 6b: Payment success / order confirmation ====================

function paymentSuccessView() {
    var o = state.lastOrder;
    if (!o) {
        return '<div class="view-enter">' + emptyHtml('🧾', 'No recent order', 'Place an order to see its confirmation here.',
            '<a class="btn btn-primary card-mt" href="#/food">Browse Food &amp; Kitchens</a>') + '</div>';
    }
    var paid = o.paymentStatus === 'PAID';
    var h = '<div class="view-enter pt-3 text-center">';
    h += '<div class="font-size-3-2" aria-hidden="true">✅</div>' +
        '<h1 class="font-800 my-2">Order Confirmed</h1>' +
        '<p class="muted small">' + (paid ? 'Demo payment successful — thank you!' : 'Order placed — pay cash on delivery.') + '</p>';
    h += '<div class="card pad card-mt text-left">' +
        '<div class="receipt-line"><span>Order</span><span>#' + esc(o.orderNumber || '') + '</span></div>' +
        '<div class="receipt-line"><span>Kitchen</span><span>🏪 ' + esc(o.kitchen && o.kitchen.displayName ? o.kitchen.displayName : '') + '</span></div>';
    (o.items || []).forEach(function (it) {
        h += '<div class="receipt-line"><span>' + esc(it.productName) + ' × ' + it.quantity + '</span><span>' + money(it.price * it.quantity) + '</span></div>';
    });
    h += '<div class="receipt-line font-800 border-top mt-2 pt-2">' +
        '<span>Total</span><span>' + money(o.totalAmount) + '</span></div>' +
        '<div class="flex gap-2 card-mt">' +
        (paid ? '<span class="pill pill-green">Payment: PAID (Demo)</span>' : '<span class="pill pill-amber">Payment: Due on delivery</span>') +
        (ORDER_BADGES[o.orderStatus] || '') + '</div></div>';
    h += '<div class="flex gap-3 card-mt">' +
        '<a class="btn btn-primary flex-1" href="#/orders">View My Orders</a>' +
        '<a class="btn btn-secondary flex-1" href="#/home">Back to Home</a></div>';
    h += '</div>';
    return h;
}

// ==================== Screen 7: Search-by-item comparison ====================

async function comparisonView(hash) {
    var itemName = decodeURIComponent(hash.split('/')[2] || '');
    var h = '<div class="view-enter">';
    h += '<div class="top-row"><button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1">' + esc(itemName) + '</h2></div>';

    try {
        var data = await api('/api/discovery/offers?item=' + encodeURIComponent(itemName));
        var offers = data.offers || [];
        h += '<p class="muted small mb-3">Currently available from ' + offers.length +
            ' kitchen' + (offers.length === 1 ? '' : 's') + '</p>';

        if (!offers.length) {
            h += emptyHtml('🔍', 'No offers right now', 'No kitchen currently lists "' + itemName +
                '". Try the Food & Kitchens hub for other dishes.',
                '<a class="btn btn-primary card-mt" href="#/food">Back to Food & Kitchens</a>');
        } else {
            h += offers.map(function (o) {
                return '<div class="compare-card">' +
                    '<div class="cc-top">' +
                    '<div class="kc-avatar">' + (o.kitchenImageUrl ? '<img src="' + esc(o.kitchenImageUrl) + '" alt="' + esc(o.kitchenDisplayName) + '">' : '🏪') + '</div>' +
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
    // Favourites are kitchen-only (no favourite food-item UI).
    await loadFavSet();
    var h = '<div class="view-enter"><div class="page-head"><h1>❤️ Favourite Kitchens</h1>' +
        '<p class="muted small">Save up to 3 community kitchens for quick access.</p></div>';

    var favs = null;
    if (state.user) {
        try { favs = await api('/api/favourites'); } catch (e) { favs = null; }
    }
    var list = favs && favs.length ? favs : DEMO_FAVOURITES;
    if (!list || !list.length) {
        var demoNote = !state.user ? '<p class="tiny muted mt-1">Showing demo favourites — log in to see yours.</p>' : '';
        h += emptyHtml('❤️', 'No favourite kitchens yet', 'Tap the heart on any kitchen to save it here.') + demoNote;
    } else {
        h += list.map(function (f) {
            var kid = f.kitchenId || null;
            var faved = kid && favSet().has(String(kid));
            var href = kid ? '#/kitchen/' + kid : '#/kitchens';
            return '<div class="fav-list-item">' +
                '<a class="fli-emoji" href="' + href + '" aria-label="Open ' + esc(f.name) + '">' +
                (usableImageUrl(f.imageUrl) ? '<img src="' + esc(f.imageUrl) + '" alt="' + esc(f.name) + '" onerror="imgFallback(this)">' : '🏪') + '</a>' +
                '<span class="fli-body"><a class="fli-name" href="' + href + '">' + esc(f.name) + '</a>' +
                '<span class="fli-sub">' + esc(f.subtitle || 'Community kitchen on SocioMart') + '</span></span>' +
                (faved && kid ? heartBtnHtml(kid, f.name) : '') +
                '</div>';
        }).join('');
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
        h += emptyHtml('🔐', 'Login to view your history', 'Your orders and enquiries appear here.',
            '<button class="btn btn-primary card-mt" type="button" data-action="open-login">Log in</button>');
        h += '</div>';
        return h;
    }

    if (tab === 'orders') {
        try {
            var orders = await api('/api/buyer/orders/my');
            var all = [].concat(orders.active || [], orders.completed || []);
            if (!all.length) {
                h += emptyHtml('📋', 'No orders yet', 'When you place your first order it will show up here.',
                    '<a class="btn btn-primary card-mt" href="#/food">Browse Food &amp; Kitchens</a>');
            } else {
                var filter = state.ordersFilter || 'all';
                var groups = {
                    all: all,
                    active: all.filter(function (o) { return o.orderStatus !== 'CANCELLED' && o.orderStatus !== 'DELIVERED' && o.orderStatus !== 'COMPLETED'; }),
                    completed: all.filter(function (o) { return o.orderStatus === 'DELIVERED' || o.orderStatus === 'COMPLETED'; }),
                    cancelled: all.filter(function (o) { return o.orderStatus === 'CANCELLED'; })
                };
                h += '<div class="segmented my-1">' +
                    ['all', 'active', 'completed', 'cancelled'].map(function (f) {
                        return '<button type="button" class="' + (filter === f ? 'active' : '') + '" data-action="set-orders-filter" data-filter="' + f + '">' +
                            f.charAt(0).toUpperCase() + f.slice(1) + '</button>';
                    }).join('') + '</div>';
                var list = groups[filter];
                if (!list || !list.length) {
                    h += emptyHtml('🗂️', 'Nothing in "' + filter + '"', 'Try another filter to see your orders.');
                } else {
                    h += list.map(function (o) {
                        var cancelled = o.orderStatus === 'CANCELLED';
                        var payStatus = o.paymentStatus || '';
                        var badgeClass = cancelled ? 'cancelled' : (payStatus === 'PAID' ? 'paid' : 'pending');
                        // Surface the real payment status clearly: Paid vs Will Pay Later vs Pending.
                        var badgeText = cancelled ? 'Cancelled' :
                            (payStatus === 'PAID' ? 'Paid' :
                            (payStatus === 'WILL_PAY_LATER' ? 'Will Pay Later' :
                            (payStatus || 'Pending')));
                        var itemLines = (o.items || []).map(function (it) {
                            return '<div class="odc-buyer-row"><span class="odc-food">' + esc(it.productName) + '</span><span class="odc-qty">×' + it.quantity + ' · ' + money(it.price * it.quantity) + '</span></div>';
                        }).join('');
                        var orderDate = new Date(o.createdAt);
                        var grid = '<div class="odc-grid">' +
                            '<div><div class="odc-label">Kitchen</div><div class="odc-value">' + esc(o.kitchen ? o.kitchen.displayName : '—') + '</div></div>' +
                            '<div><div class="odc-label">Order Date</div><div class="odc-value">' + orderDate.toLocaleDateString() + ' · ' + orderDate.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) + '</div></div>' +
                            '<div><div class="odc-label">Order Status</div><div class="odc-value">' + (ORDER_BADGES[o.orderStatus] || o.orderStatus) + '</div></div>' +
                            '<div><div class="odc-label">Total</div><div class="odc-value">' + money(o.totalAmount) + '</div></div>' +
                            '</div>';
                        var remark = o.customInstructions ? '<div class="odc-remark">📝 ' + esc(o.customInstructions) + '</div>' : '';
                        return '<a class="odc-order-card block no-underline ' + (cancelled ? 'cancelled' : '') + '" href="#/order/' + o.id + '">' +
                            '<div class="odc-top-row"><span class="odc-order-id">#' + esc(o.orderNumber) + '</span><span class="odc-badge ' + badgeClass + '">' + badgeText + '</span></div>' +
                            itemLines +
                            grid +
                            remark +
                            '<p class="tiny muted mt-1">Tap for details →</p></a>';
                    }).join('');
                }
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
                        '<p class="si-sub mt-1">' + esc(en.message) + '</p>' +
                        '<p class="tiny muted mt-1">' + new Date(en.createdAt).toLocaleString() + '</p></div>' +
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

// ==================== Screen 8b: Buyer Order Detail ====================

async function orderDetailView(hash) {
    var orderId = hash.split('/')[2];
    var o;
    try {
        o = await api('/api/buyer/orders/' + encodeURIComponent(orderId));
    } catch (e) {
        return '<div class="view-enter">' + backBarHtml('Order Detail') +
            emptyHtml('🔍', 'Order not found', e.message,
                '<a class="btn btn-primary card-mt" href="#/orders">Back to My Orders</a>') + '</div>';
    }
    var paid = o.paymentStatus === 'PAID';
    var h = '<div class="view-enter">';
    h += '<div class="top-row">' +
        '<button class="icon-btn" type="button" data-action="go-back" aria-label="Back">←</button>' +
        '<h2 class="flex-1">Order Detail</h2></div>';

    h += '<div class="card pad card-mb">' +
        '<div class="flex items-center justify-between">' +
        '<div><div class="si-name text-sm">#' + esc(o.orderNumber) + '</div>' +
        '<p class="si-sub">🏪 ' + esc(o.kitchen ? o.kitchen.displayName : '') + '</p></div>' +
        (ORDER_BADGES[o.orderStatus] || '') + '</div>' +
        '<p class="tiny muted mt-1">Placed ' + new Date(o.createdAt).toLocaleString() + '</p></div>';

    h += '<div class="card pad card-mb"><h3 class="font-700 mb-2">Items</h3>';
    (o.items || []).forEach(function (it) {
        h += '<div class="receipt-line"><span>' + esc(it.productName) + ' × ' + it.quantity + '</span><span>' + money(it.price * it.quantity) + '</span></div>' +
            '<p class="tiny muted mb-1">' + money(it.price) + ' each' +
            (it.scheduledDate ? ' · ' + esc(prettyDate(it.scheduledDate)) : '') +
            (it.scheduledSlot ? ' · ' + esc(it.scheduledSlot) : '') + '</p>';
    });
    h += '<div class="receipt-line font-800 border-top mt-2 pt-2">' +
        '<span>Item total</span><span>' + money(o.totalAmount) + '</span></div></div>';

    h += '<div class="card pad card-mb">' +
        '<div class="flex justify-between py-1"><span class="cc-label">Payment status</span>' +
        (paid ? '<span class="pill pill-green">PAID (Demo)</span>' : '<span class="pill pill-amber">PENDING</span>') + '</div>' +
        '<div class="flex justify-between py-1"><span class="cc-label">Order status</span>' +
        '<span class="cc-value">' + esc(o.orderStatus || '') + '</span></div>' +
        (o.buyer ? '<div class="flex justify-between py-1"><span class="cc-label">Deliver to</span>' +
            '<span class="cc-value">' + esc([o.buyer.flatHouseNumber, state.user && state.user.building, state.user && state.user.society].filter(Boolean).join(', ') || '—') + '</span></div>' : '') +
        (o.customInstructions ? '<div class="card-mt"><span class="cc-label">Remarks</span>' +
            '<p class="si-sub mt-1">' + esc(o.customInstructions) + '</p></div>' : '') +
        '</div>';

    h += '<div class="flex gap-3">' +
        '<a class="btn btn-secondary flex-1" href="#/orders">← My Orders</a>' +
        (o.kitchen && o.kitchen.id ? '<a class="btn btn-secondary flex-1" href="#/kitchen/' + o.kitchen.id + '">Visit Kitchen</a>' : '') +
        '</div>';

    h += '</div>';
    return h;
}

// ==================== Screen 8: Profile ====================

async function profileView() {
    var h = '<div class="view-enter"><div class="page-head"><h1>Profile</h1></div>';

    if (!state.user) {
        // Logged-out state: login prompt (Spec Screen 8)
        h += '<div class="card pad text-center">' +
            '<div class="font-size-2-4 mb-2">👋</div>' +
            '<h2>Welcome to SocioMart</h2>' +
            '<p class="muted small my-2">Login with your mobile number to place orders, save favourites and send enquiries.</p>' +
            '<form data-form="profile-login">' +
            '<div class="form-group"><input class="form-input" name="mobileNumber" inputmode="numeric" maxlength="10" placeholder="10-digit mobile number" required></div>' +
            '<button class="btn btn-primary btn-block" type="submit">Log in</button></form>' +
            '<p class="tiny muted" class="mt-2">You can keep browsing without logging in.</p>' +
            '</div></div>';
        return h;
    }

    // Logged-in state
    var u = state.user;
    h += '<div class="card pad card-mb">' +
        '<div class="flex items-center gap-3">' +
        '<div class="kc-avatar kc-avatar-sm">' + esc((u.name || '?').charAt(0).toUpperCase()) + '</div>' +
        '<div><h2>' + esc(u.name || 'Buyer') + '</h2>' +
        '<p class="muted small">📱 ' + esc(u.mobileNumber || '') + '</p></div></div></div>';

    h += '<form data-form="profile-edit"><div class="card pad card-mb">' +
        '<div class="profile-row"><span class="pr-label">Name</span>' +
        '<input class="form-input form-input-sm" name="name" value="' + esc(u.name || '') + '"></div>' +
        '<div class="profile-row"><span class="pr-label">Community / Society</span>' +
        '<input class="form-input form-input-sm" name="society" value="' + esc(u.society || LOCATION) + '"></div>' +
        '<div class="profile-row"><span class="pr-label">Building</span>' +
        '<input class="form-input form-input-sm" name="building" value="' + esc(u.building || '') + '"></div>' +
        '<div class="profile-row"><span class="pr-label">Flat #</span>' +
        '<input class="form-input form-input-sm" name="flatHouseNumber" value="' + esc(u.flatHouseNumber || '') + '"></div>' +
        '<button class="btn btn-secondary btn-block btn-sm mt-2" type="submit">Save Profile</button></div></form>';

    h += '<div class="card pad"><button class="btn btn-danger btn-block" type="button" data-action="logout">Log Out</button></div>';

    h += '</div>';
    return h;
}

