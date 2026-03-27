## PR Title

`chore(repo): harden public surfaces and add repository policies`

## PR Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [x] Docs
- [ ] Test
- [x] Chore
- [ ] Breaking change

## Summary

Reduce accidental data exposure in the public-facing UIs and add the repository policy files needed before sharing the project as a read-only personal repository.

## Problem / Context

The codebase was moving toward a public GitHub presence, but the UIs still exposed more operational and local-profile detail than they should, and the repository was missing standard public-facing policy files.

## Changes Made

- Added `LICENSE`, `CONTRIBUTING.md`, and `SECURITY.md` for a public repository baseline.
- Removed template residue and sensitive browser exposure from `reports-ui` and `ops-ui`.
- Tightened engine and UI behavior so local paths, logs, and raw profile content stay out of browser surfaces.

## Files Changed (and Why)

- `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`: define public repository policy and contribution/security expectations.
- `README.md`, `ARCHITECTURE.md`, `services/engine/README.md`, `services/engine/ops-ui/README.md`, `apps/reports-ui/README.md`: document the public/private boundary and repository intent.
- `apps/reports-ui/lib/**`: remove template pages and limit what the browser can see from engine artifacts.
- `services/engine/ops-ui/lib/**`: remove template pages and stop exposing raw local config details through the UI.
- `services/engine/src/main/java/**`: tighten exception handling and related hardening paths in the engine.

## How to Test

1. Run `./scripts/verify.sh`.
2. Launch `reports-ui` and confirm candidate profile names and local paths are not exposed in browser-visible payloads.
3. Launch `ops-ui` and confirm raw candidate profile content, local commands, and local filesystem paths are not rendered.

## Validation Evidence

- `./scripts/verify.sh`

## Risks / Trade-offs

- Some operational details are now intentionally hidden from the browser UI, so deeper local debugging should continue to happen through the CLI or server-side logs.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment changes.
- Reviewers should evaluate this branch as a public-readiness hardening step rather than a feature PR.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
