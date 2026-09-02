#!/usr/bin/env bash
# =============================================================================
#  SocioMart - one-command runner   (macOS / Linux / Windows WSL / Git Bash)
#
#    ./start.sh
#
#  This single command:
#    1. Loads optional overrides from `.env` (falls back to built-in defaults -
#       create `.env` from `.env.example` only if you want a different port).
#    2. Verifies JDK 21+ is available (the ONLY prerequisite for a clean setup).
#    3. Runs the Maven wrapper, which automatically downloads Maven and every
#       project dependency on first run, then compiles the backend.
#    4. Boots Spring Boot - it creates the H2 database schema and seeds the demo
#       marketplace on startup, and serves BOTH the REST API and the SocioMart
#       SPA (the frontend is served by the backend; no separate server).
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")"

# ---- 1. Environment: optional .env overrides, defaults otherwise -------------
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
fi
PORT="${PORT:-8081}"

# ---- 2. Locate Java (JAVA_HOME wins, otherwise PATH) ------------------------
JAVA_BIN=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
fi

if [ -z "$JAVA_BIN" ]; then
    echo
    echo "  ERROR: Java was not found."
    echo "  Install JDK 21 (Temurin: https://adoptium.net), then set JAVA_HOME or add java to PATH."
    echo
    exit 1
fi

JAVA_VER="$("$JAVA_BIN" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)"
MAJOR="${JAVA_VER%%.*}"
if [ "$MAJOR" = "1" ]; then
    MAJOR="$(printf '%s' "${JAVA_VER#1.}" | cut -d. -f1)"
fi
if [ "${MAJOR:-0}" -lt 21 ]; then
    echo
    echo "  ERROR: JDK 21 or newer is required, but Java ${JAVA_VER:-unknown} was found."
    echo "  Install Temurin 21 from https://adoptium.net (tick 'Set JAVA_HOME') and rerun ./start.sh"
    echo
    exit 1
fi

echo "  [setup] Java OK: ${JAVA_VER}   (PORT=${PORT})"

# ---- 3. Boot (the Maven wrapper installs Maven + deps on first run) ----------
cd my-first-spring-api
if [ -x ./mvnw ]; then
    ./mvnw spring-boot:run
else
    mvn spring-boot:run
fi