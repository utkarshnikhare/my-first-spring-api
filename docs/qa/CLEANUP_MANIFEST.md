# SocioMart Cleanup Manifest

**Audit Date:** 2026-10-27
**Auditor:** Principal Engineer / QA Lead

## Classification summary

- Essential source documents: docs/Homemade_Products_Spec_Full.md, docs/qa/PROJECT_QUALITY_RULES.md, docs/qa/MASTER_TEST_CHECKLIST.md, docs/qa/PREORDER_MIDNIGHT_DEFECT_INVESTIGATION.md, and original defect screenshots referenced in DOCUMENT_INVENTORY.md.
- Essential project documentation: README.md, render.yaml, Dockerfile, docs/qa/* (authoritative).
- Current QA artifacts: docs/qa/PROJECT_WORK_HISTORY.md, DOCUMENT_INVENTORY.md, REQUIREMENTS_TRACEABILITY.md, DEFECT_REGISTER.md, CLEANUP_MANIFEST.md, TEST_EXECUTION_REPORT.md, RELEASE_REPORT.md.
- Redundant artifacts (NOT deleted in this pass): backups/ (tracked, 864 files) — full snapshot of pre-redesign tree; recoverable via git history. Deletion deferred because it is tracked and may be considered legitimate backup; removal would be a large destructive change requiring explicit approval.
- Uncertain files: docs-tools/ (gitignored, untracked, ~100 diagnostic scripts), docs/quarantine/ (untracked legacy notes), extracted_spec.txt, response.txt. Retained because purpose/ownership unclear and may contain unique evidence; not deleted.

## Candidate actions

| Path | Type | Reason | Dependency check | Action | Retained replacement |
|---|---|---|---|---|---|
| backups/sociomart-pre-ui-redesign-20261005-1615/ | tracked backup | Full old tree snapshot; redundant with git history | None referenced by build | RETAINED (deferred) | git history |
| docs-tools/* | untracked scripts | Diagnostic/QA helper scripts | Not referenced by build/CI | RETAINED (uncertain) | n/a |
| docs/quarantine/* | untracked notes | Legacy handover notes | Not referenced | RETAINED (uncertain) | n/a |
| extracted_spec.txt | untracked | Possible spec extract | Not referenced | RETAINED (uncertain) | n/a |
| response.txt | untracked | Unknown | Not referenced | RETAINED (uncertain) | n/a |
| target/ | build output | Generated | gitignored | Not tracked; safe to delete locally but not required | mvn rebuild |

## Deletions performed

None. This audit intentionally avoids broad or destructive deletions. Only documentation consolidation (new/updated authoritative files) was performed.
