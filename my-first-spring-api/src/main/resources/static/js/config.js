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
        // For static hosting (Vercel/GitHub Pages), use production URL
        // Change this to your deployed backend URL
        return 'http://localhost:8081';
    })(),

    // App settings
    APP_NAME: 'SocioMart',
    APP_VERSION: '1.0.0',

    // Session timeout in milliseconds (30 minutes)
    SESSION_TIMEOUT: 30 * 60 * 1000,

    // Enable debug logging
    DEBUG: false
};
