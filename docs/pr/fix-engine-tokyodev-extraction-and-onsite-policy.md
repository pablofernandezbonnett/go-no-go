## PR Title

`fix(engine): stabilize TokyoDev extraction and make onsite-only persona-specific`

## PR Type

- [ ] Feature
- [x] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Fix false positives in `services/engine` URL evaluation for TokyoDev-style pages, make candidate-fit explanations concrete, and change `onsite_only` so it is a scored risk by default instead of a universal hard reject.

## Problem / Context

TokyoDev job pages were still producing misleading evaluations because the engine could miss the visible hero metadata, including `No remote`, while also picking titles from unrelated cards or related-job sections. That led to incorrect `remote_friendly` outcomes, weak candidate-fit explanations, and `Unspecified` remote policy warnings even when the page clearly said `No remote`. In parallel, the shipped personas still treated `onsite_only` as a hard reject across the board, which made the default and strict personas harsher than intended and contradicted the newer persona-specific policy direction.

## Changes Made

- Fixed URL extraction to keep job-page hero content, prioritize real job headings, trim non-job sections like `Related jobs`, and preserve nearby metadata used for remote-policy inference.
- Normalized TokyoDev-style `No remote` to `Onsite-only`, ignored hiring-process mentions like remote interviews, tightened `candidate_stack_fit`, and made domain/seniority explanations concrete in both reasoning and human-reading output.
- Changed persona defaults so onsite-only is penalized through `onsite_bias` unless a persona explicitly opts into `onsite_only` as a hard reject, and aligned regression fixtures plus config/docs with that rule.

## Files Changed (and Why)

- `services/engine/src/main/java/com/pmfb/gonogo/engine/EvaluateInputCommand.java`: fix detail-page title/body extraction and stop trimming away job hero metadata.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/job/RawJobParser.java`: normalize `No remote` / `Partially remote` correctly and ignore remote wording inside interview-process lines.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/job/JobPostingExtractor.java`: preserve header/snippet metadata so TokyoDev cards keep signals like `No remote` and `Apply from abroad`.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/decision/DecisionEngineV1.java`: tighten stack-fit detection, add candidate-fit reasoning, and emit `onsite_bias` by default unless a persona explicitly hard-rejects onsite-only.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/decision/HumanReadingSynthesizer.java`: explain why domain and seniority fit match the candidate instead of only listing signal IDs.
- `services/engine/config/personas.yaml`, `services/engine/config/personas.example.yaml`, `services/engine/config/README.md`, `services/engine/ENGINE.md`: align shipped persona defaults and docs with persona-specific onsite-only policy.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/EvaluateInputCommandTest.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/decision/DecisionEngineV1Test.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/decision/DecisionTestFixtureSupport.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/job/JobPostingExtractorTest.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/job/RawJobParserTest.java`, `services/engine/src/test/resources/fixtures/decision-regression/cases.yaml`: lock the extraction, policy, and regression behavior with targeted coverage.
- `docs/pr/fix-engine-tokyodev-extraction-and-onsite-policy.md`: keep the PR description tracked in the repository docs folder.

## How to Test

1. `cd services/engine`
2. Run `./gradlew test`
3. Run `./gradlew --quiet --console=plain run --args='evaluate-input --persona product_expat_engineer --candidate-profile pmfb --job-url https://www.tokyodev.com/companies/lunaris/jobs/software-engineer --output-format json --timeout-seconds 20'`
4. Confirm the result shows `title: Software Engineer`, `remote_policy: Onsite-only`, no `normalization_warnings`, and `onsite_bias` instead of `onsite-only work policy detected`
5. Repeat with `product_expat_engineer_pragmatic` and confirm the result keeps `GO_WITH_CAUTION` with `salary_below_persona_floor` plus `onsite_bias`

## Validation Evidence

- `cd services/engine && ./gradlew test`
- `cd services/engine && ./gradlew test --tests 'com.pmfb.gonogo.engine.EvaluateInputCommandTest' --tests 'com.pmfb.gonogo.engine.decision.DecisionEngineV1Test'`
- `cd services/engine && ./gradlew test --tests 'com.pmfb.gonogo.engine.decision.DecisionEngineV1RegressionFixturesTest'`
- Reran `evaluate-input` for Lunaris with `product_expat_engineer`, `product_expat_engineer_strict_stability`, and `product_expat_engineer_pragmatic`; the saved artifacts now show `Onsite-only` with `onsite_bias`, and the pragmatic persona also keeps the salary-floor risk

## Risks / Trade-offs

- The default and strict shipped personas are now less aggressive about onsite-only roles, so reviewers should confirm that `onsite_bias` plus score pressure is the desired baseline for those personas.
- Combined risks such as missing salary plus onsite-only can still land in `NO_GO` through score alone, even without a hard reject.
- Some sites may still return anti-bot challenge shells to the raw HTTP fetcher; this PR fixes the extraction logic when real page content is available, but it does not add a browser-backed fetch path.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment steps are required.
- Reviewers should refresh any previously generated Lunaris artifacts if they want the new remote-policy and onsite-penalty behavior reflected in the saved output.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
