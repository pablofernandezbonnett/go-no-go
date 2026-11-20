## PR Title

`refactor(engine,ops-ui,reports-ui): update stable dependencies and streamline workflows`

## PR Type

- [ ] Feature
- [x] Bug fix
- [x] Refactor
- [x] Docs
- [x] Test
- [x] Chore
- [x] Breaking change

## Summary

Update both Jaspr UIs to compatible stable dependencies and simplify engine, operations, and report workflows. Make incremental state persistence safer, preserve unrelated persona YAML content, and load report bodies only for the selected run and view.

## Problem / Context

The UIs had dependency versions and SDK guidance that needed alignment with the stable Jaspr 0.23 toolchain. An interrupted engine run could advance incremental change state before its reports were written. Persona tuning rebuilt the entire YAML file, and the reports index read every artifact body even when a page only needed run metadata. The TUI also required a restart to pick up config changes.

## Changes Made

- Pinned compatible stable Jaspr and build-tool versions for both UIs, updated lockfiles and setup docs, and specified Dart 3.13.x.
- Persisted incremental change state after successful report generation; extracted salary-range parsing into a focused class.
- Made `ops-ui` record process-start failures and save persona tuning through a targeted, atomic YAML edit.
- Split `reports-ui` loading into a metadata index and selected-run, selected-view artifact requests; guarded against stale responses when switching runs quickly.
- Added TUI config reload and simplified the command preview, including redaction of pasted text.
- Refined the repository and UI agent guidance without adding new architectural layers.

## Files Changed (and Why)

- `apps/reports-ui/pubspec.yaml`, `services/engine/ops-ui/pubspec.yaml` and lockfiles: align both UIs with compatible stable releases.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/PipelineRunCommand.java`, `decision/SalaryRangeParser.java`, `report/JobChangeDetector.java`: keep incremental state consistent with generated reports and isolate salary parsing.
- `services/engine/ops-ui/lib/backend/engine_config_repository.dart`, `persona_tuning_yaml_editor.dart`, `run_manager.dart`: preserve unrelated YAML content and report failed runs reliably.
- `apps/reports-ui/lib/report_access/`, `lib/main.server.dart`, `lib/services/reports_api.dart`, `lib/pages/`: load artifact content on demand and keep run selection current.
- `services/engine/src/main/java/com/pmfb/gonogo/engine/tui/`: reload configuration in place and show a safer command preview.
- `AGENTS.md` and relevant READMEs/guides: document stable dependency policy, local entry points, SDK requirements, and changed workflows.

## How to Test

1. Use Dart 3.13.x and the documented Java toolchain; run `dart pub get` in both UI projects.
2. Run `./scripts/verify.sh` from the repository root.
3. Start the UIs with `./scripts/run-ops-ui.sh` and `./scripts/run-reports-ui.sh`.
4. In `ops-ui`, edit persona tuning and confirm unrelated YAML fields and comments remain; start a run with an invalid executable and confirm it ends as failed.
5. In `reports-ui`, open the Runs page, switch runs in the report views, and confirm each view loads the selected run's content and handles a rapid switch correctly.
6. In the engine TUI, change a config file, select **Reload config**, and confirm the menu reflects the change without restarting.

## Validation Evidence

- Engine tests passed during `./scripts/verify.sh` with `ORG_GRADLE_PROJECT_gonogoJavaVersion=27` in the local environment.
- `dart analyze` passed for both UIs; the final `reports-ui` analysis found no issues.
- Targeted smoke checks passed for persona YAML preservation and selected-view report loading; the TUI reload behavior has a focused test.
- `git diff --check` passed.
- The local `jaspr build` step could not complete: the installed Dart SDK is 3.14.0, while these manifests require `>=3.13.0 <3.14.0`. Re-run the full build with Dart 3.13.x before merging.

## Risks / Trade-offs

- Dart 3.11 and 3.12 environments must upgrade to Dart 3.13.x; Dart 3.14 is outside the current supported range.
- Report content is fetched when a view opens, adding one request per selected run and view. Malformed artifact content is reported when that view loads rather than during the metadata scan.
- The UI build and browser flows still need verification with the supported SDK.

## Backward Compatibility

- [ ] No breaking changes
- [x] Breaking changes (described below)

The minimum Dart SDK for both UIs changes from 3.11 to 3.13, and the supported range excludes 3.14. No engine artifact format migration is required.

## Deployment / Rollout Notes

- Install Dart 3.13.x and `jaspr_cli` 0.23.5 for UI development and builds.
- Run `dart pub get` in each UI project to use the committed lockfiles. No data migration is required.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [ ] Local verification completed (UI build and browser check require Dart 3.13.x)
