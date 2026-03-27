## PR Title

`chore(repo): add Jaspr launch helpers and stabilize verify`

## PR Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [x] Docs
- [ ] Test
- [x] Chore
- [ ] Breaking change

## Summary

Add repository-level launch helpers for both Jaspr apps and make the verification flow more stable when run from the monorepo root.

## Problem / Context

Running both Jaspr apps in parallel required remembering project-specific working directories and non-conflicting ports. The repository-level verification flow also needed a clean step to avoid stale Jaspr state.

## Changes Made

- Added root-level helper scripts for `ops-ui` and `reports-ui` launches.
- Documented the expected port allocation and monorepo-root commands in the relevant READMEs.
- Updated `scripts/verify.sh` to clean Jaspr state before the build step.

## Files Changed (and Why)

- `scripts/run-ops-ui.sh`: launch `ops-ui` from the monorepo root with dedicated ports.
- `scripts/run-reports-ui.sh`: launch `reports-ui` from the monorepo root with dedicated ports.
- `scripts/verify.sh`: clean Jaspr build state before the build step.
- `README.md`, `apps/reports-ui/README.md`, `services/engine/ops-ui/README.md`: document the new launch commands and port behavior.

## How to Test

1. Run `./scripts/run-ops-ui.sh`.
2. Run `./scripts/run-reports-ui.sh` in a separate shell.
3. Run `./scripts/verify.sh`.

## Validation Evidence

- `./scripts/verify.sh`

## Risks / Trade-offs

- The helper scripts encode a default port convention, so any future local override should stay aligned with the documentation to avoid confusion.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment changes.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
