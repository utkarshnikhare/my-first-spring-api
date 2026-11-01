#!/usr/bin/env bash
# ==============================================================================
# SocioMart Production Database Backup Script
# Generates a compressed, checksum-verified, encrypted logical PostgreSQL backup.
#
# Usage:
#   export SOCIOMART_DB_URL="postgresql://user:password@host:port/database?sslmode=require"
#   ./scripts/backup-database.sh [options]
#
# Options:
#   --output-dir <path>     Directory to save the backup (default: ./backups/exports)
#   --encrypt               Encrypt backup file using AES-256-CBC via OpenSSL
#   --retention-days <days> Retain local backups for N days (default: 7)
#   --help                  Display this help message
# ==============================================================================

set -euo pipefail

OUTPUT_DIR="./backups/exports"
ENCRYPT=false
RETENTION_DAYS=7
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="sociomart_backup_${TIMESTAMP}"

# Parse command line flags
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --encrypt)
      ENCRYPT=true
      shift
      ;;
    --retention-days)
      RETENTION_DAYS="$2"
      shift 2
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

# Ensure database URL is supplied via environment (NEVER as a command-line argument)
RAW_DB_URL="${SOCIOMART_DB_URL:-${DATABASE_URL:-}}"
if [[ -z "${RAW_DB_URL}" && -z "${PGDATABASE:-}" ]]; then
  echo "[-] ERROR: Database connection string not set. Please export SOCIOMART_DB_URL or PG* variables." >&2
  exit 1
fi

# Ensure pg_dump utility is available
if ! command -v pg_dump >/dev/null 2>&1; then
  echo "[-] ERROR: pg_dump utility not found. Please install postgresql-client." >&2
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

if [[ -n "${RAW_DB_URL}" ]]; then
  rem="${RAW_DB_URL#*://}"
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

# Validate essential connection parameters
if [[ -z "${PGHOST}" || -z "${PGDATABASE}" || -z "${PGUSER}" ]]; then
  echo "[-] ERROR: Missing essential connection parameters (host, dbname, or user)." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

RAW_BACKUP_FILE="${OUTPUT_DIR}/${BACKUP_NAME}.dump"
FINAL_FILE="${RAW_BACKUP_FILE}"
SECURE_PGPASS=""

# Cleanup handler for temporary credentials and failed backups
cleanup() {
  local exit_code=$?
  if [[ -n "${SECURE_PGPASS}" && -f "${SECURE_PGPASS}" ]]; then
    rm -f "${SECURE_PGPASS}"
  fi
  if [[ "$exit_code" -ne 0 && -n "${RAW_BACKUP_FILE}" && -f "${RAW_BACKUP_FILE}" ]]; then
    rm -f "${RAW_BACKUP_FILE}"
  fi
  exit "$exit_code"
}
trap cleanup EXIT ERR INT TERM

# Create temporary permission-restricted pgpass file (mode 0600)
# This prevents password exposure in ps / /proc/PID/cmdline
SECURE_PGPASS=$(mktemp "${TMPDIR:-/tmp}/sociomart_pgpass_XXXXXX" 2>/dev/null || mktemp)
chmod 0600 "${SECURE_PGPASS}"
echo "${PGHOST}:${PGPORT}:${PGDATABASE}:${PGUSER}:${PGPASSWORD}" > "${SECURE_PGPASS}"
export PGPASSFILE="${SECURE_PGPASS}"
export PGSSLMODE

# Unset cleartext password and URL from environment
unset PGPASSWORD
unset RAW_DB_URL
unset SOCIOMART_DB_URL
unset DATABASE_URL

echo "[*] Starting database backup at $(date -u +"%Y-%m-%dT%H:%M:%SZ")..."
echo "[*] Connecting to host: ${PGHOST}:${PGPORT}, database: ${PGDATABASE}, user: ${PGUSER}"
START_TIME=$(date +%s)

# Execute pg_dump using separate non-sensitive connection parameters
# Passwords are read strictly via PGPASSFILE, NEVER appearing in process arguments
pg_dump \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${RAW_BACKUP_FILE}"

# Restrict permissions on generated raw dump
chmod 0600 "${RAW_BACKUP_FILE}"

# Verify backup artifact exists and is non-empty
if [[ ! -s "${RAW_BACKUP_FILE}" ]]; then
  echo "[-] ERROR: Backup generation failed or produced an empty file." >&2
  rm -f "${RAW_BACKUP_FILE}"
  exit 1
fi

FILE_SIZE=$(wc -c < "${RAW_BACKUP_FILE}" | tr -d ' ')
if [[ "${FILE_SIZE}" -lt 500 ]]; then
  echo "[-] ERROR: Backup file is abnormally small (${FILE_SIZE} bytes). Aborting." >&2
  rm -f "${RAW_BACKUP_FILE}"
  exit 1
fi

# Optional AES-256-CBC Encryption
if [[ "${ENCRYPT}" == "true" ]]; then
  PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
  if [[ -z "${PASSPHRASE}" ]]; then
    echo "[-] ERROR: --encrypt requested but BACKUP_ENCRYPTION_PASSPHRASE is not set." >&2
    rm -f "${RAW_BACKUP_FILE}"
    exit 1
  fi
  if [[ "${#PASSPHRASE}" -lt 16 ]]; then
    echo "[-] ERROR: BACKUP_ENCRYPTION_PASSPHRASE must be at least 16 characters long." >&2
    rm -f "${RAW_BACKUP_FILE}"
    exit 1
  fi
  ENCRYPTED_FILE="${RAW_BACKUP_FILE}.enc"
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 \
    -in "${RAW_BACKUP_FILE}" \
    -out "${ENCRYPTED_FILE}" \
    -pass env:BACKUP_ENCRYPTION_PASSPHRASE
  rm -f "${RAW_BACKUP_FILE}"
  chmod 0600 "${ENCRYPTED_FILE}"
  FINAL_FILE="${ENCRYPTED_FILE}"
fi

# Generate SHA-256 checksum manifest
CHECKSUM=$(sha256sum "${FINAL_FILE}" | awk '{print $1}')
echo "${CHECKSUM}  $(basename "${FINAL_FILE}")" > "${FINAL_FILE}.sha256"
chmod 0644 "${FINAL_FILE}.sha256"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Write metadata manifest
cat <<EOF > "${FINAL_FILE}.meta.json"
{
  "backupName": "${BACKUP_NAME}",
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "artifact": "$(basename "${FINAL_FILE}")",
  "sizeBytes": ${FILE_SIZE},
  "sha256": "${CHECKSUM}",
  "encrypted": ${ENCRYPT},
  "durationSeconds": ${DURATION}
}
EOF
chmod 0644 "${FINAL_FILE}.meta.json"

# Retention enforcement: Remove local backups older than RETENTION_DAYS
if [[ "${RETENTION_DAYS}" -gt 0 ]]; then
  echo "[*] Cleaning up backups older than ${RETENTION_DAYS} days in ${OUTPUT_DIR}..."
  find "${OUTPUT_DIR}" -type f -name "sociomart_backup_*" -mtime +"${RETENTION_DAYS}" -exec rm -f {} +
fi

echo "[+] Backup SUCCESSFUL!"
echo "    Artifact: ${FINAL_FILE}"
echo "    Size:     ${FILE_SIZE} bytes"
echo "    SHA-256:  ${CHECKSUM}"
echo "    Duration: ${DURATION}s"
