# PR Title

`feat(repo): humanize public results and add ad-hoc rerun matrix`

## PR Type

- [x] Feature
- [ ] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Humanize the public `reports-ui` evaluation surfaces around engine-owned `human_reading` fields, add a matrix rerun command for rebuilding saved ad-hoc sources across personas for one candidate profile, and tighten the public read-only onboarding docs.

## Problem / Context

The browser-facing result views still leaned too hard on raw signal ids and console-style output, which made the repo weaker as a public read-only example. At the same time, saved ad-hoc evaluations could be rerun one artifact at a time, but there was no focused command for rebuilding the same saved sources across all personas for a fixed candidate profile. The root docs also needed a cleaner first-clone path and a more explicit public-read-only posture.

## Changes Made

- Reworked `reports-ui` evaluation and job-detail views so they lead with plain-English human-reading summaries, fit pills, and clearer decision-factor copy before exposing raw engine details.
- Added shared payload/component support for `human_reading` fields and updated report copy to explain that public UI surfaces prefer human-readable output over raw diagnostics.
- Extracted reusable ad-hoc rerun support in the engine and added `rerun-ad-hoc-matrix` to rebuild unique saved sources across multiple personas for one candidate profile with stable artifact naming.
- Updated root and engine docs for first-clone setup, Jaspr dependency install steps, public read-only expectations, and the new rerun workflows.

## Files Changed (and Why)

- `apps/reports-ui/lib/components/evaluation_human_reading.dart`: new reusable UI section for human-readable summaries, fit pills, and narrative pros/risks.
- `apps/reports-ui/lib/models/human_reading_payload.dart`, `apps/reports-ui/lib/models/evaluation_payload.dart`, `apps/reports-ui/lib/models/batch_item_payload.dart`: parse and carry `human_reading` fields through the UI payload layer.
- `apps/reports-ui/lib/pages/evaluate.dart`, `apps/reports-ui/lib/pages/job_detail.dart`, `apps/reports-ui/lib/pages/reports_view_helpers.dart`, `apps/reports-ui/lib/app.dart`: shift result rendering toward public-friendly summaries and clearer copy.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/RerunAdHocMatrixCommand.java`: add matrix rerun command for one candidate profile across one or more personas.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/AdHocEvaluationRerunSupport.java`, `AdHocArtifactFileNameResolver.java`, `EvaluateInputExecutor.java`, `RerunAdHocEvaluationsCommand.java`, `GoNoGoCommand.java`: extract reusable rerun support, stable artifact naming, and register the new command.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/RerunAdHocEvaluationsCommandTest.java`, `RerunAdHocMatrixCommandTest.java`: cover the extracted rerun support and the new matrix behavior.
- `README.md`, `docs/quickstart.md`, `docs/advanced-guide.md`, `services/engine/README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `apps/reports-ui/README.md`: document the public read-only flow, first-time setup, and the public-facing behavior of the updated surfaces.

## How to Test

1. From the repository root, run `./scripts/verify.sh`.
2. From `services/engine`, run `./gradlew run --args="rerun-ad-hoc-matrix --help"` and confirm the new command is available.
3. From the repository root, run `./scripts/run-reports-ui.sh` and verify the evaluate and job-detail pages lead with the human-readable summary cards instead of raw console-style output.

## Validation Evidence

- `./scripts/verify.sh`
- `services/engine`: `./gradlew test`
- `services/engine/ops-ui`: `dart analyze`
- `apps/reports-ui`: `dart analyze`, `jaspr clean`, `jaspr build`

## Risks / Trade-offs

- The public-facing results now intentionally hide more raw console-style detail by default, so debugging from the browser may require going back to engine artifacts or CLI output.
- `reports-ui` still carries existing non-blocking Dart `info` lint warnings unrelated to this change set.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deploy or migration work is required.
- Public repo readers now have a clearer clone-and-run path without adding private candidate data.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
