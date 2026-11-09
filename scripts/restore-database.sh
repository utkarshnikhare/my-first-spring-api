#!/usr/bin/env bash
# Restore an authenticated backup to an operator-verified disposable local database.
# Usage: restore-database.sh --backup-file <archive.dump.enc> --evidence-file <new-file>
# Set PGHOST=127.0.0.1, PGPORT, PGDATABASE, PGUSER and credentials via libpq
# environment or PGPASSFILE. Set CONFIRM_ISOLATED_RECOVERY_TARGET=true,
# RECOVERY_APPROVED_TARGET=127.0.0.1:<port>/<database>, and a non-secret
# RECOVERY_APPROVAL_REFERENCE identifying the independently approved isolation.
# A loopback address is NOT proof of isolation; never use a production tunnel.
set -euo pipefail
umask 077

BACKUP_FILE=""
EVIDENCE_FILE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-file|--evidence-file)
      [[ $# -ge 2 && -n "$2" ]] || { echo "[-] ERROR: Missing option value." >&2; exit 1; }
      if [[ "$1" == "--backup-file" ]]; then BACKUP_FILE="$2"; else EVIDENCE_FILE="$2"; fi
      shift 2 ;;
    --help)
      sed -n '2,8s/^# \{0,1\}//p' "$0"
      exit 0 ;;
    *)
      echo "[-] ERROR: Unsupported option. Connection URLs and checksum bypasses are not accepted." >&2
      exit 1 ;;
  esac
done

if [[ -n "${TARGET_DB_URL:-}" || -n "${PGSERVICE:-}" || -n "${PGSERVICEFILE:-}" ||
      -n "${PGHOSTADDR:-}" || -n "${PGOPTIONS:-}" ]]; then
  echo "[-] ERROR: Use explicit PGHOST, PGPORT, PGDATABASE and PGUSER; URLs, services and connection overrides are not accepted." >&2
  exit 1
fi
PGHOST="${PGHOST:-}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-}"
PGUSER="${PGUSER:-}"
if [[ "${PGHOST}" != "127.0.0.1" || ! "${PGPORT}" =~ ^[0-9]{1,5}$ ||
      $((10#${PGPORT})) -lt 1 || $((10#${PGPORT})) -gt 65535 ||
      ! "${PGDATABASE}" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ||
      ! "${PGUSER}" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
  echo "[-] CRITICAL REFUSAL: Explicit loopback host, numeric port and simple database/user names required." >&2
  exit 1
fi
if [[ "${CONFIRM_ISOLATED_RECOVERY_TARGET:-}" != "true" ||
      "${RECOVERY_APPROVED_TARGET:-}" != "${PGHOST}:${PGPORT}/${PGDATABASE}" ||
      ! "${RECOVERY_APPROVAL_REFERENCE:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._/-]{2,127}$ ]]; then
  echo "[-] CRITICAL REFUSAL: Independent isolation confirmation, exact approved target and approval reference required." >&2
  exit 1
fi
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" || "$BACKUP_FILE" != *.enc ||
      ! -f "${BACKUP_FILE}.sha256" || -z "$EVIDENCE_FILE" ]]; then
  echo "[-] ERROR: Encrypted backup, checksum manifest and new evidence file are required." >&2
  exit 1
fi
PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"
if [[ "${#PASSPHRASE}" -lt 16 ]]; then
  echo "[-] ERROR: BACKUP_ENCRYPTION_PASSPHRASE must contain at least 16 characters." >&2
  exit 1
fi
for tool in psql pg_restore java sha256sum awk mktemp; do
  command -v "$tool" >/dev/null 2>&1 || { echo "[-] ERROR: Required tool not found: $tool" >&2; exit 1; }
done

# Reserve the evidence file before any database connection or modification.
if ! (set -o noclobber; : > "$EVIDENCE_FILE") 2>/dev/null; then
  echo "[-] ERROR: Cannot create new evidence file; refusing to overwrite." >&2
  exit 1
fi
printf 'status=INCOMPLETE\napproval_reference=%s\napproved_target=%s\nstarted_utc=%s\n' \
  "$RECOVERY_APPROVAL_REFERENCE" "$RECOVERY_APPROVED_TARGET" "$(date -u +%FT%TZ)" >> "$EVIDENCE_FILE"

WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/sociomart_restore_XXXXXXXX")
cleanup() {
  local result=$?
  rm -f "${WORK_DIR}/archive.enc" "${WORK_DIR}/archive.dump" "${WORK_DIR}/validation.txt"
  find "$WORK_DIR" -maxdepth 1 -type f -name 'archive.dump.tmp.*' -delete
  rmdir "$WORK_DIR" || echo "[-] WARNING: Could not remove temporary restore directory: $WORK_DIR" >&2
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cp -- "$BACKUP_FILE" "${WORK_DIR}/archive.enc"
EXPECTED_HASH=$(awk 'NR == 1 {print $1} END {if (NR != 1) exit 1}' "${BACKUP_FILE}.sha256")
[[ "$EXPECTED_HASH" =~ ^[a-fA-F0-9]{64}$ ]] || { echo "[-] ERROR: Invalid SHA-256 manifest." >&2; exit 1; }
ACTUAL_HASH=$(sha256sum "${WORK_DIR}/archive.enc" | awk '{print $1}')
[[ "${EXPECTED_HASH,,}" == "$ACTUAL_HASH" ]] || { echo "[-] ERROR: Backup checksum mismatch." >&2; exit 1; }
printf 'sha256=%s\nchecksum=PASS\n' "$ACTUAL_HASH" >> "$EVIDENCE_FILE"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
java "${SCRIPT_DIR}/BackupCrypto.java" decrypt "${WORK_DIR}/archive.enc" "${WORK_DIR}/archive.dump"
printf 'authenticated_decryption=PASS\n' >> "$EVIDENCE_FILE"

# Caller-supplied attestations cannot establish where a loopback tunnel terminates.
# Do not connect or restore until an independently verifiable isolation mechanism exists.
echo "[-] CRITICAL REFUSAL: Independent target isolation verification is not implemented; restore is disabled." >&2
exit 1
