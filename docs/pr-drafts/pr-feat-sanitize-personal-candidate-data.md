## PR Title

`feat(engine,reports-ui,ops-ui,repo): harden public sharing and improve evaluation workflows`

## PR Type

- [x] Feature
- [ ] Bug fix
- [x] Refactor
- [x] Docs
- [x] Test
- [x] Chore
- [ ] Breaking change

## Summary

Prepare the monorepo to be shared publicly by sanitizing personal-looking sample data, hardening browser-visible surfaces, improving engine evaluation behavior, and polishing both local Jaspr UIs.

## Problem / Context

This branch collects the work needed before exposing the repository as a read-only personal project. The repo still needed stronger protection against leaking local candidate data, better public-facing repository policy files, more reliable local launch flows for the Jaspr apps, and multiple engine and UI improvements discovered during real-world usage.

## Changes Made

- Sanitized tracked candidate-related sample values and expanded ignore rules for local personal files.
- Added public repository policy files and reduced sensitive browser exposure in `reports-ui` and `ops-ui`.
- Added monorepo-level Jaspr launch helpers and stabilized verification for both UIs.
- Improved engine evaluation workflows, Java 21 alignment, saved ad-hoc evaluation reuse, and UI composition across `reports-ui` and `ops-ui`.

## Files Changed (and Why)

- `.gitignore`: ignore local personal editor metadata and candidate/resume files that should never be committed.
- `README.md`, `ARCHITECTURE.md`, `AGENTS.md`, `docs/README.md`: keep repository-level documentation aligned with the public-sharing posture and updated workflows.
- `CONTRIBUTING.md`, `LICENSE`, `SECURITY.md`: add public repository policy and contribution/security guidance.
- `scripts/run-ops-ui.sh`, `scripts/run-reports-ui.sh`, `scripts/verify.sh`: make local UI launches reproducible and verification more stable.
- `services/engine/src/main/java/**`: improve evaluation behavior, salary handling, TUI prompts, and Java 21 alignment.
- `services/engine/src/test/**`: update coverage and fixtures for the new engine behavior.
- `apps/reports-ui/lib/**`: improve saved evaluation access, local-state UX, report rendering, and YAML/result presentation.
- `services/engine/ops-ui/lib/**`: harden exposed data, improve layout and filtering, and polish the CLI companion UI.

## How to Test

1. Run `./scripts/verify.sh` from the repository root.
2. Run `./gradlew test -PgonogoJavaVersion=21` from `services/engine`.
3. Start both Jaspr apps from the repository root with `./scripts/run-ops-ui.sh` and `./scripts/run-reports-ui.sh`.
4. In `reports-ui`, evaluate a URL, confirm saved ad-hoc results can be reopened, and review the updated report/YAML presentation.
5. In `ops-ui`, verify `Runs`, `Personas`, `Candidate Profiles`, and `Settings` show the tightened layout and do not expose local-sensitive data.

## Validation Evidence

- `./scripts/verify.sh`
- `./gradlew test -PgonogoJavaVersion=21`
- `dart analyze lib/app.dart` in `services/engine/ops-ui`
- `reports-ui` still reports non-blocking analyzer `info` messages, but repository verification passes end-to-end

## Risks / Trade-offs

- This branch is broad because it predates the stacked split into smaller PR branches; review is easier if done by commit group.
- Saved historical ad-hoc evaluations remain immutable snapshots, so older artifacts may reflect previous engine rules.
- `reports-ui` still has existing analyzer `info` findings that are intentionally non-blocking for now.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment or migration steps are required.
- If this branch is reviewed directly, note that the same scope has already been split into smaller stacked branches for easier review.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
