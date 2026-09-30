/**
 * SocioMart - Configuration
 *
 * API base URL resolution.
 *
 * The frontend and the backend are the SAME Spring Boot application: index.html,
 * seller.html and admin.html are served from src/main/resources/static by the very
 * app that exposes /api/**. server.port is ${PORT:8081} in every profile, so the
 * documented default backend port is 8081 - but because the app serves its own
 * pages, the API is always reachable at the page's own origin, whatever port that
 * origin happens to use.
 *
 * That is why the default is a same-origin relative URL ('' -> /api/...). The
 * previous code instead returned http://localhost:8081 whenever the page was on
 * localhost, which is wrong precisely in the case it was meant to cover: run the
 * app on any other port (8080, 8099, -Dserver.port=...) and every request was sent
 * to a port nothing was listening on, so each screen silently fell back to its
 * error state. (When something unrelated already occupies 8081 it is worse - the
 * app talks to a different application entirely.)
 *
 * Override for genuinely split front/backends (e.g. a JS dev server on :3000
 * proxying to the API): define window.SOCIO_API_BASE_URL before this script runs,
 * e.g.
 *     <script>window.SOCIO_API_BASE_URL = 'http://127.0.0.1:8081';</script>
 *     <script src="/js/config.js"></script>
 * An invalid override throws instead of quietly falling back to a wrong origin.
 * Cross-origin frontends additionally need matching CORS on the backend, which
 * this project does not enable - same-origin is the supported arrangement.
 */

/** Resolve the API base URL from an optional explicit override. */
function resolveApiBaseUrl() {
    var raw = typeof window !== 'undefined' ? window.SOCIO_API_BASE_URL : undefined;

    // No override: same-origin. Correct for Render, for localhost on the default
    // 8081, and for any other local port, because the app serves its own pages.
    if (raw === undefined || raw === null) return '';

    if (typeof raw !== 'string') {
        throw new TypeError('SOCIO_API_BASE_URL must be a string, got ' + typeof raw);
    }

    var value = raw.trim();
    // An explicit empty string is a valid way to ask for same-origin.
    if (value === '') return '';

    var parsed;
    try {
        parsed = new URL(value);
    } catch (e) {
        throw new SyntaxError(
            'SOCIO_API_BASE_URL is not a valid absolute URL: ' + JSON.stringify(raw) +
            ' - use a full origin such as http://127.0.0.1:8081, or omit it for same-origin');
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        throw new SyntaxError('SOCIO_API_BASE_URL must use http or https, got ' + parsed.protocol);
    }
    // The caller concatenates the base with a path that already starts with '/',
    // so a trailing slash would produce '//api/...'.
    return value.replace(/\/+$/, '');
}

const CONFIG = {
    /**
     * API Base URL
     * - Default: '' (same-origin). The Spring app serves both the pages and the
     *   API, so this is correct on Render and on any local port.
     * - Split frontend/backend: set window.SOCIO_API_BASE_URL (see above).
     */
    API_BASE_URL: resolveApiBaseUrl(),

    // App settings
    APP_NAME: 'SocioMart',
    APP_VERSION: '1.0.0',

    // Session timeout in milliseconds (30 minutes)
    SESSION_TIMEOUT: 30 * 60 * 1000,

    // Enable debug logging
    DEBUG: false
};
