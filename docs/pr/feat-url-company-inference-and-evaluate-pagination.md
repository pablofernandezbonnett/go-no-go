## PR Title

`feat(reports-ui,engine,docs): infer recruiter brands from URLs and paginate Recent URLs`

## PR Type

- [x] Feature
- [ ] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Improve the Evaluate workflow by paginating `Recent URLs` in `reports-ui` and making the engine infer a usable recruiter or client label from the source URL when page metadata only yields placeholders such as `Our Client` or `Based in Japan`.

## Problem / Context

The `Recent URLs` table in `reports-ui` was not paginated, which made the Evaluate page harder to scan once history grew. Separately, saved ad-hoc URL evaluations could persist generic `company_name` values like `Our Client` or `Based in Japan`, which are not useful in the UI and hide the recruiter or platform that actually published the role. The existing artifacts also needed a way to be corrected without rerunning evaluation end to end.

## Changes Made

- Added local pagination controls to the `Recent URLs` table in `reports-ui`, including page selection, rows-per-page selection, and visible-range summary text.
- Extracted URL company-name resolution into a shared engine resolver that rejects generic placeholders and falls back to recruiter or platform branding inferred from the URL host when the real employer is unavailable.
- Added a `normalize-ad-hoc-company-names` CLI command to rewrite saved ad-hoc evaluation artifacts in place without re-running evaluation, then used it to clean the current `output/ad-hoc-evaluations` history.

## Files Changed (and Why)

- `apps/reports-ui/lib/pages/evaluate.dart`: paginate `Recent URLs` and reset pagination coherently when filters change.
- `apps/reports-ui/lib/app.dart`: add styles for the new Evaluate pagination controls.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/CompanyNameResolver.java`: centralize company and recruiter name inference from config, title, description, headings, and URL host aliases.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/EvaluateInputCommand.java`: use the shared resolver for future URL-based evaluations.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/NormalizeAdHocCompanyNamesCommand.java`: normalize saved ad-hoc YAML artifacts in place without recomputing evaluations.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/GoNoGoCommand.java`: register the new normalization command.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/EvaluateInputCommandTest.java`: add coverage for recruiter-brand fallback when metadata only exposes placeholders.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/NormalizeAdHocCompanyNamesCommandTest.java`: add coverage for in-place ad-hoc artifact normalization.
- `services/engine/README.md`: document the new ad-hoc normalization workflow.

## How to Test

1. Run `./scripts/verify.sh` from the repository root.
2. From `services/engine`, run `./gradlew test` and confirm the new `EvaluateInputCommand` and `NormalizeAdHocCompanyNamesCommand` coverage passes.
3. Start `reports-ui`, open `http://127.0.0.1:8792/evaluate`, expand `Recent URLs`, and confirm `Rows per page` and `Page` update the visible range correctly.
4. From `services/engine`, run `./gradlew run --args="normalize-ad-hoc-company-names --input-dir output/ad-hoc-evaluations"` and confirm previously generic recruiter artifacts now show values such as `Michael Page` or `Hays`.

## Validation Evidence

- `./scripts/verify.sh`
- `./gradlew test` in `services/engine`
- `dart analyze` in `apps/reports-ui` completed without errors or warnings, only pre-existing non-blocking `info` diagnostics
- Browser validation on `http://127.0.0.1:8792/evaluate` confirmed `Recent URLs` pagination and page-range updates
- `./gradlew run --args="normalize-ad-hoc-company-names --input-dir output/ad-hoc-evaluations"` updated 34 artifacts, left 57 unchanged, and finished with 0 failures

## Risks / Trade-offs

- When the real employer is not present in the page content, the fallback intentionally uses the recruiter or platform brand from the source URL, which is more honest than preserving placeholders but still may not reflect the end client.
- The host-alias mapping is intentionally small and pragmatic; additional recruiter domains may need to be added over time as new sources appear.
- The ad-hoc normalization command rewrites artifacts in place, so reviewers should treat those YAML updates as contract-level data cleanup rather than fresh evaluation output.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment steps are required.
- If old ad-hoc artifacts still contain generic company placeholders in another workspace, run `./gradlew run --args="normalize-ad-hoc-company-names --input-dir output/ad-hoc-evaluations"` once after updating.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
