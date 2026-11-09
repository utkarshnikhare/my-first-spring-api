#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/sociomart_safeguard_XXXXXXXX")
trap 'rm -rf -- "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/bin"
export MOCK_LOG="$TEST_DIR/restore.log"

cat > "$TEST_DIR/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf 'psql invoked\n' >> "$MOCK_LOG"
exit 1
EOF
cat > "$TEST_DIR/bin/pg_restore" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_LOG"
[[ "${MOCK_RESTORE_FAIL:-}" != 1 ]]
EOF
chmod +x "$TEST_DIR/bin/psql" "$TEST_DIR/bin/pg_restore"
export PATH="$TEST_DIR/bin:$PATH"
export PGHOST=127.0.0.1 PGPORT=5439 PGDATABASE=disposable PGUSER=testuser
export CONFIRM_ISOLATED_RECOVERY_TARGET=true
export RECOVERY_APPROVED_TARGET=127.0.0.1:5439/disposable RECOVERY_APPROVAL_REFERENCE=DR-123
export BACKUP_ENCRYPTION_PASSPHRASE=synthetic-test-passphrase
unset TARGET_DB_URL PGSERVICE PGSERVICEFILE PGHOSTADDR PGOPTIONS 2>/dev/null || true
printf 'synthetic pg archive content\n' > "$TEST_DIR/source.dump"
java "$SCRIPT_DIR/BackupCrypto.java" encrypt "$TEST_DIR/source.dump" "$TEST_DIR/source.dump.enc"
sha256sum "$TEST_DIR/source.dump.enc" > "$TEST_DIR/source.dump.enc.sha256"

run_restore() {
  bash "$SCRIPT_DIR/restore-database.sh" --backup-file "$TEST_DIR/source.dump.enc" --evidence-file "$TEST_DIR/$1.evidence" "${@:2}" > "$TEST_DIR/$1.log" 2>&1
}
expect_failure() {
  local name=$1
  shift
  if run_restore "$name" "$@"; then
    echo "FAIL: $name unexpectedly succeeded" >&2
    exit 1
  fi
  echo "PASS: $name refused"
}
assert_no_restore() {
  [[ ! -s "$MOCK_LOG" ]] || { echo "FAIL: unexpected pg_restore invocation" >&2; exit 1; }
}

CONFIRM_ISOLATED_RECOVERY_TARGET=false expect_failure missing_confirmation
RECOVERY_APPROVED_TARGET=127.0.0.1:5439/other expect_failure target_mismatch
RECOVERY_APPROVAL_REFERENCE='' expect_failure missing_approval
PGHOST=192.0.2.1 expect_failure remote_host
TARGET_DB_URL=synthetic-value expect_failure url_environment
expect_failure url_argument --target-db-url synthetic-value
expect_failure skip_checksum --skip-checksum
mv "$TEST_DIR/source.dump.enc.sha256" "$TEST_DIR/manifest.saved"
expect_failure missing_checksum
mv "$TEST_DIR/manifest.saved" "$TEST_DIR/source.dump.enc.sha256"
cp "$TEST_DIR/source.dump.enc" "$TEST_DIR/original.enc"
printf 'x' >> "$TEST_DIR/source.dump.enc"
expect_failure checksum_mismatch
sha256sum "$TEST_DIR/source.dump.enc" > "$TEST_DIR/source.dump.enc.sha256"
expect_failure authentication_mismatch
cp "$TEST_DIR/original.enc" "$TEST_DIR/source.dump.enc"
sha256sum "$TEST_DIR/source.dump.enc" > "$TEST_DIR/source.dump.enc.sha256"
printf 'do not overwrite\n' > "$TEST_DIR/existing.evidence"
expect_failure existing
grep -qx 'do not overwrite' "$TEST_DIR/existing.evidence"

expect_failure isolation_unverified
grep -q 'Independent target isolation verification is not implemented' "$TEST_DIR/isolation_unverified.log"
grep -qx 'checksum=PASS' "$TEST_DIR/isolation_unverified.evidence"
grep -qx 'authenticated_decryption=PASS' "$TEST_DIR/isolation_unverified.evidence"
grep -qx 'status=INCOMPLETE' "$TEST_DIR/isolation_unverified.evidence"
assert_no_restore
echo "PASS: synthetic restore safeguard regression suite"
