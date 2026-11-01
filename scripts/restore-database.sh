#!/usr/bin/env bash
# ==============================================================================
# SocioMart Database Restoration Script
# Restores a verified backup into a target PostgreSQL database with safety guards.
#
# Usage:
#   export TARGET_DB_URL="postgresql://user:password@isolated-host:port/isolated_db?sslmode=require"
#   ./scripts/restore-database.sh --backup-file <path-to-backup> [options]
#
# Options:
#   --backup-file <path>       Path to the backup dump file (.dump or .dump.enc)
#   --target-db-url <url>      Optional target DB URL override
#   --skip-checksum            Bypass checksum verification (NOT RECOMMENDED)
#   --help                     Display this help message
# ==============================================================================

set -euo pipefail

BACKUP_FILE=""
TARGET_URL="${TARGET_DB_URL:-${SOCIOMART_DB_URL:-}}"
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

if [[ -z "${TARGET_URL}" ]]; then
  echo "[-] ERROR: Target database connection string not provided. Set TARGET_DB_URL." >&2
  exit 1
fi

# ==============================================================================
# SAFETY GUARD: Prevent accidental restore over live production database
# ==============================================================================
if [[ "${TARGET_URL}" =~ (prod|supabase\.co) && -z "${ALLOW_PRODUCTION_OVERWRITE:-}" ]]; then
  echo "[-] CRITICAL REFUSAL: Target database appears to be a LIVE PRODUCTION environment." >&2
  echo "    Restoration must be executed ONLY against an isolated recovery database." >&2
  echo "    If an emergency disaster recovery overwrite of production is explicitly required," >&2
  echo "    you must set ALLOW_PRODUCTION_OVERWRITE=true in your environment." >&2
  exit 1
fi

# Ensure pg_restore and psql are available
if ! command -v pg_restore >/dev/null 2>&1; then
  echo "[-] ERROR: pg_restore utility not found. Please install postgresql-client." >&2
  exit 1
fi

RESTORE_INPUT="${BACKUP_FILE}"
TEMP_DECRYPTED=""

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

# Step 2: Decrypt if needed
if [[ "${BACKUP_FILE}" =~ \.enc$ ]]; then
  echo "[*] Decrypting AES-256 backup archive..."
  PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
  if [[ -z "${PASSPHRASE}" ]]; then
    echo "[-] ERROR: Backup is encrypted but BACKUP_ENCRYPTION_PASSPHRASE is unset." >&2
    exit 1
  fi
  TEMP_DECRYPTED=$(mktemp)
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "${BACKUP_FILE}" \
    -out "${TEMP_DECRYPTED}" \
    -pass env:BACKUP_ENCRYPTION_PASSPHRASE
  RESTORE_INPUT="${TEMP_DECRYPTED}"
fi

cleanup() {
  if [[ -n "${TEMP_DECRYPTED}" && -f "${TEMP_DECRYPTED}" ]]; then
    rm -f "${TEMP_DECRYPTED}"
  fi
}
trap cleanup EXIT

echo "[*] Executing database restore into target database at $(date -u +"%Y-%m-%dT%H:%M:%SZ")..."
START_TIME=$(date +%s)

# Execute pg_restore
pg_restore \
  --dbname="${TARGET_URL}" \
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
