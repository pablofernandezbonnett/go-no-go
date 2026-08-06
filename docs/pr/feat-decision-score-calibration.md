## PR Title

feat(engine): calibrate decision scores and job evidence

## PR Type

- [x] Feature
- [x] Bug fix
- [x] Docs
- [x] Test
- [x] Chore
- [ ] Refactor
- [ ] Breaking change

## Summary

Improves job-post extraction and candidate-aware evaluation while updating the
two Jaspr applications to the supported runtime. Decision scores now align with
the final verdict, and the reports UI shows both the decision-band score and
the raw margin for a weighted `NO_GO`.

## Problem / Context

Decorated recruiter posts could infer a checklist item as the role title, and
unnamed recruiter opportunities were under-signalled. The former theoretical
score normalization also produced contradictory output: a raw `0` could be
`NO_GO · 0/100`, while raw `1` was displayed near `77/100`.

## Changes Made

- Added candidate-specific JLPT-gap and role-scope/salary signals with visible reasoning.
- Improved raw-job extraction for decorated salary, JLPT, and `Hiring:` headings.
- Detect unnamed recruiter-led opportunities, keep their risk explainable, and
  avoid treating an “ideal Tech Lead background” as a manager requirement.
- Map scores into `NO_GO` (0–49), `GO_WITH_CAUTION` (50–69), and `GO` (70–100)
  decision bands; hard-filter results remain capped at 20.
- Updated reports score rendering, dependency constraints and locks, setup/run
  documentation, verification workflow, and local `.agents/` ignore rules.

## Files Changed (and Why)

- `services/engine/src/main/java/**`: parser, candidate profile, signals, verdict score mapping, and explanations.
- `services/engine/src/test/java/**`: regression coverage for parsing, recruiter evidence, score bands, JLPT gaps, and salary scope.
- `services/engine/ENGINE.md` and `services/engine/config/**`: document engine policy and candidate configuration.
- `apps/reports-ui/lib/pages/**`: render engine-owned score data without reimplementing decision logic.
- `apps/reports-ui/pubspec.*`, `services/engine/ops-ui/pubspec.*`, root docs, and CI: align Jaspr/Dart runtime and local workflow guidance.

## How to Test

1. Run `./scripts/verify.sh` from the repository root.
2. Run `cd services/engine && ./gradlew test --tests 'com.pmfb.gonogo.engine.decision.DecisionEngineV1Test' --tests 'com.pmfb.gonogo.engine.job.RawJobParserTest'`.
3. Re-evaluate a decorated recruiter post and confirm the `Hiring:` title, anonymous-employer risk, and `49/100 · raw -1` style output.
4. Re-evaluate the PayPay Card TokyoDev role and confirm title, company, partial-remote policy, and `GO_WITH_CAUTION` remain intact.

## Validation Evidence

- Engine parser and decision tests pass.
- `dart analyze` passes for `apps/reports-ui` and `services/engine/ops-ui`.
- `jaspr build` completes for `apps/reports-ui` through the repository verifier.
- Targeted artifacts were regenerated for the recruiter post and PayPay Card role.

## Risks / Trade-offs

- Decision bands intentionally make scores comparable to verdicts, not a direct
  probability or market-value estimate.
- Recruiter anonymity is a verification risk; it is not a fraud accusation.
- Existing generated artifacts retain their historical content until rerun.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

Artifact fields and command interfaces are unchanged. Only the meaning of the
existing numeric score is made decision-aligned.

## Deployment / Rollout Notes

- No migration or environment variable is required.
- Regenerate saved ad-hoc artifacts when the new score calibration should apply
  to historical evaluations.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
