/**
 * SocioMart - Configuration
 * Externalized API base URL for different deployment environments
 */

const CONFIG = {
    /**
     * API Base URL
     * - Development: http://localhost:8081
     * - Production: Set to your deployed server URL
     * - Tunnel: Set to your localtunnel/ngrok URL
     */
    API_BASE_URL: (() => {
        // Auto-detect: if served from localhost, use localhost:8081
        if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
            return 'http://localhost:8081';
        }
        // Served from a non-local host (tunnel / LAN / deployed server):
        // the frontend is served by the same Spring app, so same-origin
        // relative URLs always hit the correct backend from any device.
        return '';
    })(),

    // App settings
    APP_NAME: 'SocioMart',
    APP_VERSION: '1.0.0',

    // Session timeout in milliseconds (30 minutes)
    SESSION_TIMEOUT: 30 * 60 * 1000,

    // Enable debug logging
    DEBUG: false
};
