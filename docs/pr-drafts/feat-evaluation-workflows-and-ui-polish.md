## PR Title

`feat(engine,reports-ui,ops-ui): improve evaluation workflows and polish local UIs`

## PR Type

- [x] Feature
- [ ] Bug fix
- [x] Refactor
- [x] Docs
- [ ] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Improve engine evaluation behavior, refresh the public-facing reports workflow, and bring the CLI companion UI closer to the same usability bar as `reports-ui`.

## Problem / Context

The engine and both UIs had several gaps after the previous hardening work: Java 21 alignment was incomplete, some evaluation flows failed on real ATS pages, salary opacity was over-penalized, saved ad-hoc evaluations were not reusable from the UI, and both Jaspr UIs still needed layout and interaction polish.

## Changes Made

- Aligned the engine with Java 21 and tightened JLine prompt behavior.
- Improved evaluation handling for client-rendered job pages and softened salary opacity from a hard reject to a configurable risk.
- Added saved ad-hoc evaluation access in `reports-ui`, improved report viewers, and reduced page-level remounts.
- Brought `ops-ui` layouts, headers, filters, and run detail composition up to the same visual and operational quality bar.

## Files Changed (and Why)

- `services/engine/gradle.properties`, `services/engine/README.md`, `services/engine/src/main/java/**`, `services/engine/src/test/**`: Java 21 alignment, evaluation behavior, salary policy, TUI prompt fixes, and supporting tests.
- `apps/reports-ui/lib/**`: local-state navigation improvements, saved evaluation viewing, richer report rendering, and better YAML/report presentation.
- `services/engine/ops-ui/lib/**`: improved layout, filtering, run inspection, and operational UX polish.
- `README.md`, `ARCHITECTURE.md`, `docs/README.md`, `services/engine/AGENTS.md`, `services/engine/config/*.yaml`: keep repository and engine docs aligned with the new behavior.

## How to Test

1. Run `./gradlew test -PgonogoJavaVersion=21` from `services/engine`.
2. Run `./scripts/verify.sh` from the repository root.
3. Launch `reports-ui`, evaluate a URL, and confirm saved ad-hoc results can be reopened without rerunning the engine.
4. Launch `ops-ui` and verify `Runs`, `Personas`, `Candidate Profiles`, and `Settings` render with the tightened layout and local filters.

## Validation Evidence

- `./gradlew test -PgonogoJavaVersion=21`
- `./scripts/verify.sh`
- `dart analyze lib/app.dart` in `services/engine/ops-ui`

## Risks / Trade-offs

- This branch still bundles several closely related improvements, so review is easier if done commit-by-commit inside the PR.
- Historical saved ad-hoc evaluations keep their original output, even if newer engine rules would score them differently.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment changes.
- Review this PR on top of the earlier stacked branches so the scope stays manageable.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
