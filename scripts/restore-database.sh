#!/usr/bin/env bash
# ==============================================================================
# SocioMart Database Restoration Script
# Restores a verified backup into a target PostgreSQL database with hardened safeguards.
#
# Usage:
#   export TARGET_DB_URL="postgresql://user:password@isolated-host:port/isolated_db?sslmode=require"
#   export CONFIRM_ISOLATED_RECOVERY_TARGET="true"
#   ./scripts/restore-database.sh --backup-file <path-to-backup> [options]
#
# Options:
#   --backup-file <path>              Path to the backup dump file (.dump or .dump.enc)
#   --target-db-url <url>             Optional target DB URL override
#   --confirm-isolated-target         Confirm that target is an isolated non-production database
#   --skip-checksum                   Bypass checksum verification (NOT RECOMMENDED)
#   --help                            Display this help message
# ==============================================================================

set -euo pipefail

BACKUP_FILE=""
TARGET_URL="${TARGET_DB_URL:-${SOCIOMART_DB_URL:-}}"
CONFIRM_ISOLATED="${CONFIRM_ISOLATED_RECOVERY_TARGET:-false}"
SKIP_CHECKSUM=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-file)
      BACKUP_FILE="$2"
      shift 2
      ;;
    --target-db-url)
      TARGET_URL="$2"
      shift 2
      ;;
    --confirm-isolated-target)
      CONFIRM_ISOLATED="true"
      shift
      ;;
    --skip-checksum)
      SKIP_CHECKSUM=true
      shift
      ;;
    --help)
      grep '^# ' "$0" | cut -c 3-
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "${BACKUP_FILE}" || ! -f "${BACKUP_FILE}" ]]; then
  echo "[-] ERROR: --backup-file must specify a valid, existing backup file." >&2
  exit 1
fi

if [[ -z "${TARGET_URL}" && -z "${PGDATABASE:-}" ]]; then
  echo "[-] ERROR: Target database connection parameters not provided. Set TARGET_DB_URL." >&2
  exit 1
fi

# Parse connection components securely
urldecode() {
  local url_encoded="${1//+/ }"
  printf '%b' "${url_encoded//%/\\x}"
}

PGHOST="${PGHOST:-}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-}"
PGPASSWORD="${PGPASSWORD:-}"
PGDATABASE="${PGDATABASE:-}"
PGSSLMODE="${PGSSLMODE:-require}"

if [[ -n "${TARGET_URL}" ]]; then
  rem="${TARGET_URL#*://}"
  if [[ "$rem" == *"@"* ]]; then
    userinfo="${rem%%@*}"
    rem="${rem#*@}"
    raw_user="${userinfo%%:*}"
    PGUSER=$(urldecode "$raw_user")
    if [[ "$userinfo" == *":"* ]]; then
      raw_pass="${userinfo#*:}"
      PGPASSWORD=$(urldecode "$raw_pass")
    fi
  fi
  hostport="${rem%%/*}"
  if [[ "$rem" == *"/"* ]]; then
    rem="${rem#*/}"
  else
    rem=""
  fi
  PGHOST="${hostport%%:*}"
  if [[ "$hostport" == *":"* ]]; then
    PGPORT="${hostport#*:}"
  fi
  if [[ "$rem" == *"?"* ]]; then
    PGDATABASE="${rem%%\?*}"
    query="${rem#*\?}"
    if [[ "$query" =~ sslmode=([^&]+) ]]; then
      PGSSLMODE="${BASH_REMATCH[1]}"
    fi
  else
    PGDATABASE="$rem"
  fi
fi

if [[ -z "${PGHOST}" || -z "${PGDATABASE}" || -z "${PGUSER}" ]]; then
  echo "[-] ERROR: Incomplete target database parameters (host, dbname, or user)." >&2
  exit 1
fi

# ==============================================================================
# HARDENED RESTORE SAFEGUARDS
# ==============================================================================

# Safeguard 1: Require explicit isolated recovery confirmation
if [[ "${CONFIRM_ISOLATED}" != "true" ]]; then
  echo "[-] CRITICAL REFUSAL: Isolated recovery target confirmation missing." >&2
  echo "    Restoration will NOT proceed without explicit confirmation that the target" >&2
  echo "    is a non-production, disposable recovery database." >&2
  echo "    To confirm, provide --confirm-isolated-target or export CONFIRM_ISOLATED_RECOVERY_TARGET=true." >&2
  exit 1
fi

# Safeguard 2: Detect and reject known production database hostnames and database identities
if [[ "${PGHOST}" =~ (supabase\.co|render\.com|prod|production) || "${PGDATABASE}" =~ (prod|production) ]]; then
  echo "[-] CRITICAL REFUSAL: Target database identity (${PGHOST} / ${PGDATABASE}) matches PRODUCTION patterns." >&2
  echo "    Restoration into a live production database is strictly prohibited by this tool." >&2
  echo "    For emergency disaster recovery, follow the documented manual runbook in docs/PRODUCTION_DATABASE_BACKUP_AND_DISASTER_RECOVERY.md." >&2
  exit 1
fi

# Ensure pg_restore utility is available
if ! command -v pg_restore >/dev/null 2>&1; then
  echo "[-] ERROR: pg_restore utility not found. Please install postgresql-client." >&2
  exit 1
fi

# Create temporary permission-restricted pgpass file (mode 0600)
SECURE_PGPASS=$(mktemp "${TMPDIR:-/tmp}/sociomart_restore_pgpass_XXXXXX" 2>/dev/null || mktemp)
chmod 0600 "${SECURE_PGPASS}"
echo "${PGHOST}:${PGPORT}:${PGDATABASE}:${PGUSER}:${PGPASSWORD}" > "${SECURE_PGPASS}"
export PGPASSFILE="${SECURE_PGPASS}"
export PGSSLMODE

# Unset cleartext passwords and URLs from environment
unset PGPASSWORD
unset TARGET_URL
unset TARGET_DB_URL
unset SOCIOMART_DB_URL

RESTORE_INPUT="${BACKUP_FILE}"
TEMP_DECRYPTED=""

cleanup() {
  local exit_code=$?
  if [[ -n "${TEMP_DECRYPTED}" && -f "${TEMP_DECRYPTED}" ]]; then
    rm -f "${TEMP_DECRYPTED}"
  fi
  if [[ -n "${SECURE_PGPASS}" && -f "${SECURE_PGPASS}" ]]; then
    rm -f "${SECURE_PGPASS}"
  fi
  exit "$exit_code"
}
trap cleanup EXIT ERR INT TERM

# Safeguard 3: Pre-flight target database identity and connectivity verification
# If psql is available, verify the connection and target database identity before invoking pg_restore
if command -v psql >/dev/null 2>&1; then
  echo "[*] Probing target database connectivity and identity..."
  PROBE_OUT=$(psql \
    --host="${PGHOST}" \
    --port="${PGPORT}" \
    --username="${PGUSER}" \
    --dbname="${PGDATABASE}" \
    --tuples-only \
    --no-align \
    --command="SELECT current_database(), current_user, pg_is_in_recovery();" 2>&1) || {
      echo "[-] ERROR: Target database pre-flight probe failed: ${PROBE_OUT}" >&2
      echo "    pg_restore will NOT be executed. Aborting before making any modifications." >&2
      exit 1
    }
  PROBE_DB=$(echo "${PROBE_OUT}" | awk -F'|' '{print $1}')
  PROBE_USER=$(echo "${PROBE_OUT}" | awk -F'|' '{print $2}')
  echo "[+] Target database verified: database='${PROBE_DB}', user='${PROBE_USER}'."

  # Double-check probed database name
  if [[ "${PROBE_DB}" =~ (prod|production) ]]; then
    echo "[-] CRITICAL REFUSAL: Probed database name '${PROBE_DB}' appears to be a production database." >&2
    exit 1
  fi
fi

# Step 1: Checksum verification
if [[ "${SKIP_CHECKSUM}" == "false" ]]; then
  CHECKSUM_FILE="${BACKUP_FILE}.sha256"
  if [[ -f "${CHECKSUM_FILE}" ]]; then
    echo "[*] Verifying SHA-256 integrity checksum..."
    EXPECTED_HASH=$(awk '{print $1}' "${CHECKSUM_FILE}")
    ACTUAL_HASH=$(sha256sum "${BACKUP_FILE}" | awk '{print $1}')
    if [[ "${EXPECTED_HASH}" != "${ACTUAL_HASH}" ]]; then
      echo "[-] INTEGRITY VIOLATION: Computed hash (${ACTUAL_HASH}) does not match expected (${EXPECTED_HASH}). Aborting." >&2
      exit 1
    fi
    echo "[+] Checksum MATCHED (${ACTUAL_HASH})."
  else
    echo "[!] WARNING: No .sha256 manifest found for ${BACKUP_FILE}."
  fi
fi

# Step 2: Authenticated Decryption if needed
if [[ "${BACKUP_FILE}" =~ \.enc$ ]]; then
  echo "[*] Decrypting AES-256-GCM AEAD backup archive and verifying authentication tag..."
  PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
  if [[ -z "${PASSPHRASE}" ]]; then
    echo "[-] ERROR: Backup is encrypted but BACKUP_ENCRYPTION_PASSPHRASE is unset." >&2
    exit 1
  fi
  TEMP_DECRYPTED=$(mktemp "${TMPDIR:-/tmp}/sociomart_decrypted_XXXXXX.dump" 2>/dev/null || mktemp)
  chmod 0600 "${TEMP_DECRYPTED}"
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  java "${SCRIPT_DIR}/BackupCrypto.java" decrypt "${BACKUP_FILE}" "${TEMP_DECRYPTED}" || {
    echo "[-] CRITICAL REFUSAL: Authenticated decryption failed. Backup archive may be corrupted or tampered with." >&2
    rm -f "${TEMP_DECRYPTED}"
    exit 1
  }
  RESTORE_INPUT="${TEMP_DECRYPTED}"
fi

echo "[*] Executing database restore into target database at $(date -u +"%Y-%m-%dT%H:%M:%SZ")..."
START_TIME=$(date +%s)

# Execute pg_restore using separate non-sensitive connection parameters
# Passwords are read strictly via PGPASSFILE, NEVER appearing in process arguments
pg_restore \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  "${RESTORE_INPUT}" || {
    echo "[-] WARNING: pg_restore exited with warnings/errors. Inspect target database." >&2
  }

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo "[+] Restoration completed in ${DURATION}s."
echo "[*] Recommended post-restore validation:"
echo "    - Verify Flyway schema history count: SELECT count(*) FROM flyway_schema_history;"
echo "    - Verify business table counts: users, kitchens, products, orders, order_items."
echo "    - Verify sequence alignment: SELECT last_value FROM orders_id_seq;"
