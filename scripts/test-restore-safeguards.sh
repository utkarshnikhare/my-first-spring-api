#!/usr/bin/env bash
# ==============================================================================
# SocioMart Restore Safeguards & Authenticated Encryption Test Suite
# Exercises and proves:
#   1. Missing confirmation refusal
#   2. Production host refusal (unconditional - no bypass)
#   3. Production database name refusal
#   4. AES-256-GCM AEAD authenticated encryption roundtrip
#   5. Tampered ciphertext rejection via AEADBadTagException
#   6. Cleanup of temporary files on failure
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="/tmp/sociomart_safeguard_test_$$"
mkdir -p "${TEST_DIR}"

cleanup() {
  rm -rf "${TEST_DIR}"
}
trap cleanup EXIT

echo "================================================================================"
echo "SOCIOMART RESTORE SAFEGUARDS & AEAD INTEGRITY VERIFICATION SUITE"
echo "================================================================================"

# Create dummy dump file
DUMMY_DUMP="${TEST_DIR}/dummy.dump"
echo "PGDUMP_HEADER_MOCK_DATA_TEST_1234567890" > "${DUMMY_DUMP}"

# Test 1: Missing Confirmation Guard
echo ""
echo "[TEST 1] Verifying refusal when isolated target confirmation is missing..."
TARGET_DB_URL="postgresql://testuser:testpass@localhost:5432/isolated_recovery_target" \
  "${SCRIPT_DIR}/restore-database.sh" --backup-file "${DUMMY_DUMP}" > "${TEST_DIR}/test1.log" 2>&1 && {
    echo "[-] FAILED: Expected failure when confirmation was omitted!"
    exit 1
  } || true

if grep -q "CRITICAL REFUSAL: Isolated recovery target confirmation missing" "${TEST_DIR}/test1.log"; then
  echo "[+] PASS: Missing confirmation correctly triggered CRITICAL REFUSAL."
else
  echo "[-] FAILED: Expected critical refusal message not found in output:"
  cat "${TEST_DIR}/test1.log"
  exit 1
fi

# Test 2: Production Hostname Refusal (Supabase / Render / Prod)
echo ""
echo "[TEST 2] Verifying unconditional refusal against Supabase production host..."
TARGET_DB_URL="postgresql://postgres:secret@db.sociomart-prod.supabase.co:5432/postgres?sslmode=require" \
CONFIRM_ISOLATED_RECOVERY_TARGET="true" \
  "${SCRIPT_DIR}/restore-database.sh" --backup-file "${DUMMY_DUMP}" > "${TEST_DIR}/test2.log" 2>&1 && {
    echo "[-] FAILED: Expected failure when targeting Supabase production!"
    exit 1
  } || true

if grep -q "CRITICAL REFUSAL: Target database identity (db.sociomart-prod.supabase.co / postgres) matches PRODUCTION patterns" "${TEST_DIR}/test2.log"; then
  echo "[+] PASS: Production Supabase host correctly triggered CRITICAL REFUSAL."
else
  echo "[-] FAILED: Expected critical refusal message not found in output:"
  cat "${TEST_DIR}/test2.log"
  exit 1
fi

# Test 3: Production Database Name Refusal
echo ""
echo "[TEST 3] Verifying unconditional refusal against production database name..."
TARGET_DB_URL="postgresql://user:pass@internal-staging:5432/sociomart_production" \
CONFIRM_ISOLATED_RECOVERY_TARGET="true" \
  "${SCRIPT_DIR}/restore-database.sh" --backup-file "${DUMMY_DUMP}" > "${TEST_DIR}/test3.log" 2>&1 && {
    echo "[-] FAILED: Expected failure when targeting database named sociomart_production!"
    exit 1
  } || true

if grep -q "matches PRODUCTION patterns" "${TEST_DIR}/test3.log"; then
  echo "[+] PASS: Production database name correctly triggered CRITICAL REFUSAL."
else
  echo "[-] FAILED: Expected critical refusal message not found in output:"
  cat "${TEST_DIR}/test3.log"
  exit 1
fi

# Test 4: Authenticated Encryption (AES-256-GCM AEAD) Roundtrip
echo ""
echo "[TEST 4] Testing AES-256-GCM AEAD encryption, decryption and tag verification..."
export BACKUP_ENCRYPTION_PASSPHRASE="StrictAEADPassphrase2026!Protected"
ENC_FILE="${TEST_DIR}/test.dump.enc"
DEC_FILE="${TEST_DIR}/test.dump.dec"

java "${SCRIPT_DIR}/BackupCrypto.java" encrypt "${DUMMY_DUMP}" "${ENC_FILE}"
java "${SCRIPT_DIR}/BackupCrypto.java" decrypt "${ENC_FILE}" "${DEC_FILE}"

if diff -u "${DUMMY_DUMP}" "${DEC_FILE}" >/dev/null; then
  echo "[+] PASS: Decrypted plaintext matches original byte-for-byte."
else
  echo "[-] FAILED: Decrypted plaintext differs from original!"
  exit 1
fi

# Test 5: Cryptographic Tampering Rejection
echo ""
echo "[TEST 5] Verifying that flipped bit in AEAD archive causes AEADBadTagException..."
TAMPERED_ENC="${TEST_DIR}/tampered.dump.enc"
cp "${ENC_FILE}" "${TAMPERED_ENC}"
# Invert one byte in the ciphertext payload
printf '\x5A' | dd of="${TAMPERED_ENC}" bs=1 seek=35 count=1 conv=notrunc 2>/dev/null

TAMPERED_DEC="${TEST_DIR}/tampered_decrypted.dump"
java "${SCRIPT_DIR}/BackupCrypto.java" decrypt "${TAMPERED_ENC}" "${TAMPERED_DEC}" > "${TEST_DIR}/tamper.log" 2>&1 && {
  echo "[-] FAILED: Expected decryption failure on tampered archive!"
  exit 1
} || true

if grep -q "Authentication tag mismatch" "${TEST_DIR}/tamper.log"; then
  echo "[+] PASS: Authentication tag mismatch caught and aborted immediately."
else
  echo "[-] FAILED: Tag mismatch not detected:"
  cat "${TEST_DIR}/tamper.log"
  exit 1
fi

# Verify no decrypted plaintext file was left behind
if [ ! -f "${TAMPERED_DEC}" ]; then
  echo "[+] PASS: No plaintext file was created or persisted on tamper failure."
else
  echo "[-] FAILED: Tampered output file was not cleaned up!"
  exit 1
fi

# Test 6: Refusal when target matches SOCIOMART_DB_URL in environment
echo ""
echo "[TEST 6] Verifying refusal when target URL matches SOCIOMART_DB_URL in environment..."
MOCK_PROD_URL="postgresql://produser:prodpass@db.custom-internal.com:5432/prod_custom"
SOCIOMART_DB_URL="${MOCK_PROD_URL}" \
TARGET_DB_URL="${MOCK_PROD_URL}" \
CONFIRM_ISOLATED_RECOVERY_TARGET="true" \
  "${SCRIPT_DIR}/restore-database.sh" --backup-file "${DUMMY_DUMP}" > "${TEST_DIR}/test6.log" 2>&1 && {
    echo "[-] FAILED: Expected failure when target matches SOCIOMART_DB_URL!"
    exit 1
  } || true

if grep -q "matches SOCIOMART_DB_URL in environment" "${TEST_DIR}/test6.log"; then
  echo "[+] PASS: Direct match with SOCIOMART_DB_URL correctly triggered CRITICAL REFUSAL."
else
  echo "[-] FAILED: Expected critical refusal message not found in output:"
  cat "${TEST_DIR}/test6.log"
  exit 1
fi

# Test 7: Fail-closed on remote IP / custom hostname without positive isolation proof
echo ""
echo "[TEST 7] Verifying fail-closed refusal on remote host without positive isolation proof..."
TARGET_DB_URL="postgresql://user:pass@192.0.2.10:5432/generic_app_db" \
CONFIRM_ISOLATED_RECOVERY_TARGET="true" \
  "${SCRIPT_DIR}/restore-database.sh" --backup-file "${DUMMY_DUMP}" > "${TEST_DIR}/test7.log" 2>&1 && {
    echo "[-] FAILED: Expected failure when targeting remote host with generic database name!"
    exit 1
  } || true

if grep -q "does not independently establish isolation" "${TEST_DIR}/test7.log"; then
  echo "[+] PASS: Remote target with generic DB name correctly failed closed."
else
  echo "[-] FAILED: Expected positive isolation failure not found:"
  cat "${TEST_DIR}/test7.log"
  exit 1
fi

echo ""
echo "================================================================================"
echo "[+] ALL 7 RESTORE SAFEGUARDS & AEAD CRYPTO TESTS PASSED SUCCESSFULLY!"
echo "================================================================================"
