/**
 * SocioMart Service Worker
 * PWA offline support, intelligent asset caching, and network resilience.
 */

const CACHE_NAME = 'sociomart-shell-v9';
const CORE_SHELL_ASSETS = [
    '/',
    '/index.html',
    '/seller.html',
    '/admin.html',
    '/empty.js',
    '/manifest.webmanifest',
    '/css/styles.css?v=22',
    '/css/seller.css?v=4.1',
    '/css/admin.css?v=15',
    '/js/config.js',
    '/js/common.js?v=15',
    '/js/buyer.js?v=15',
    '/js/seller.js?v=4.1',
    '/js/admin.js?v=15',
    '/js/app.js?v=10',
    '/icons/icon.svg',
    '/icons/icon-192.png',
    '/icons/icon-512.png'
];

// Install: pre-cache application shell
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            return cache.addAll(CORE_SHELL_ASSETS);
        }).then(() => self.skipWaiting())
    );
});

// Activate: clean up outdated version caches immediately
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => {
            return Promise.all(
                keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
            );
        }).then(() => self.clients.claim())
    );
});

self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
});

// Fetch event
self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);

    // 0. Neutralize Netlify HUD / Badge injection script
    if (url.pathname.includes('/.netlify/scripts/hud')) {
        event.respondWith(
            new Response('/* Netlify HUD neutralized */', {
                status: 200,
                headers: { 'Content-Type': 'application/javascript' }
            })
        );
        return;
    }

    // 1. Dynamic API and Admin login calls: NETWORK-FIRST (Never serve stale financial or auth data)
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/admin/login')) {
        event.respondWith(
            fetch(request).catch(() => {
                return new Response(
                    JSON.stringify({
                        error: 'Offline',
                        offline: true,
                        message: 'Network unreachable. Please check your internet connection.'
                    }),
                    {
                        status: 503,
                        headers: { 'Content-Type': 'application/json' }
                    }
                );
            })
        );
        return;
    }

    // 2. Google Fonts & CDN assets: Cache-First with stale-while-revalidate
    if (url.hostname.includes('fonts.googleapis.com') || url.hostname.includes('fonts.gstatic.com')) {
        event.respondWith(
            caches.match(request).then((cached) => {
                const networkFetch = fetch(request).then((response) => {
                    if (response && response.status === 200) {
                        const copy = response.clone();
                        caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
                    }
                    return response;
                }).catch(() => null);
                return cached || networkFetch;
            })
        );
        return;
    }

    // 3. Navigation / HTML pages: Network-first, fallback to cached document
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request).then((response) => {
                if (response && response.status === 200) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
                }
                return response;
            }).catch(async () => {
                const cached = await caches.match(request);
                if (cached) return cached;
                if (url.pathname.startsWith('/seller')) return caches.match('/seller.html');
                if (url.pathname.startsWith('/admin')) return caches.match('/admin.html');
                if (url.pathname.startsWith('/buyer')) return caches.match('/index.html');
                return caches.match('/index.html');
            })
        );
        return;
    }

    // 4. Static scripts & styles (CSS, JS): Stale-While-Revalidate
    // Ensures instant PWA loads with background refresh so deployments arrive automatically
    if (url.pathname.startsWith('/css/') || url.pathname.startsWith('/js/')) {
        event.respondWith(
            caches.open(CACHE_NAME).then((cache) => {
                return cache.match(request).then((cached) => {
                    const networkFetch = fetch(request).then((response) => {
                        if (response && response.status === 200) {
                            cache.put(request, response.clone());
                        }
                        return response;
                    }).catch(() => null);
                    return cached || networkFetch;
                });
            })
        );
        return;
    }

    // 5. Media & Icons (SVG, PNG, ICO, WebManifest): Cache-first, fallback to network
    event.respondWith(
        caches.match(request).then((cached) => {
            if (cached) return cached;
            return fetch(request).then((response) => {
                if (response && response.status === 200 && request.method === 'GET') {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
                }
                return response;
            });
        })
    );
});
