## PR Title

`feat(reports-ui,engine,docs): polish evaluation UX and split startup guides`

## PR Type

- [x] Feature
- [ ] Bug fix
- [x] Refactor
- [x] Docs
- [x] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Improve the `reports-ui` evaluation experience, make persona hard-reject policy less rigid in the engine, and move monorepo startup guidance into a short quickstart plus a separate advanced guide.

## Problem / Context

The Evaluate screen still had rough edges for day-to-day use: long raw URLs in the table, text-heavy action buttons, large saved-evaluation panels that forced too much scrolling, and no compact summary of positive versus negative aspects. In parallel, the engine still treated `onsite_only` as a validator-enforced hard baseline even though that policy should remain persona-specific. The repository documentation had also grown too repetitive across the root and child READMEs.

## Changes Made

- Added shared resizable table headers in `reports-ui` and applied them to `Runs`, `Batch`, and `Evaluate`.
- Improved Evaluate URL history with shorter source links, icon-only actions with tooltips, saved-result strengths/watchouts, arrow-led collapsible artifact sections, human-readable timestamps, and a sticky sidebar that stays visible on long pages.
- Removed the validator-enforced global requirement for `onsite_only` and documented it as a persona-level choice.
- Added `docs/quickstart.md` and `docs/advanced-guide.md`, then slimmed the main README files to point to those guides instead of repeating long startup instructions.

## Files Changed (and Why)

- `apps/reports-ui/lib/app.dart`: add shared table, icon-button, signal-summary, and disclosure styles.
- `apps/reports-ui/lib/components/header.dart`: keep the sidebar sticky and scrollable on long screens.
- `apps/reports-ui/lib/pages/reports_view_helpers.dart`: centralize report-table helpers, width presets, and label humanization.
- `apps/reports-ui/lib/pages/evaluate.dart`: polish recent URL actions, saved-evaluation modal, and summarized evaluation rendering.
- `apps/reports-ui/lib/pages/runs.dart`, `apps/reports-ui/lib/pages/batch.dart`: move tables onto the shared resizable table helper.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/config/ConfigValidator.java`: stop forcing `onsite_only` into every persona.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/config/ConfigValidatorTest.java`: lock the new validator behavior with focused coverage.
- `services/engine/ENGINE.md`, `services/engine/config/README.md`, `services/engine/config/personas.example.yaml`: align policy docs with persona-specific onsite handling.
- `README.md`, `docs/README.md`, `docs/quickstart.md`, `docs/advanced-guide.md`, `services/engine/README.md`, `services/engine/ops-ui/README.md`, `apps/reports-ui/README.md`: reduce README sprawl and centralize startup guidance.

## How to Test

1. Run `./scripts/verify.sh` from the repository root.
2. Start `reports-ui` and open `http://localhost:8792/evaluate`.
3. Expand `Recent URLs`, confirm the table shows the shorter `Source` link plus icon-only actions, and open one saved evaluation.
4. In the saved-evaluation modal, confirm `Strengths` and `Watchouts` render, timestamps are human-readable only, and the detailed panels show only the arrow disclosure cue.
5. Scroll to the bottom of the Evaluate page and confirm the left sidebar remains visible.
6. From `services/engine`, run `./gradlew test --tests com.pmfb.gonogo.engine.config.ConfigValidatorTest` and verify a persona without `onsite_only` is accepted.

## Validation Evidence

- `./scripts/verify.sh`
- `dart analyze` in `apps/reports-ui` completed without errors, only existing non-blocking `info` findings
- `./gradlew test --tests com.pmfb.gonogo.engine.config.ConfigValidatorTest --tests com.pmfb.gonogo.engine.config.RuntimeSettingsConfigTest --tests com.pmfb.gonogo.engine.config.DecisionSignalsConfigTest`
- Browser validation on `http://localhost:8794/evaluate` confirmed the sticky sidebar, shorter `Source` link, human-readable generated date, and arrow-only disclosure affordance in the saved-evaluation modal

## Risks / Trade-offs

- The resizable-column behavior relies on browser-native CSS resize handles on the header content, which is intentionally lightweight but less sophisticated than a full JS table-resize system.
- Existing shipped personas still include `onsite_only`; this PR changes the baseline policy contract, not the current config defaults.
- `reports-ui` still has existing analyzer `info` findings around style ordering and a few legacy hints that remain non-blocking.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment steps are required.
- Reviewers should use the new `docs/quickstart.md` for the shortest local startup path and `docs/advanced-guide.md` for the full command set.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
