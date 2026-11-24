## PR Title

`fix(engine,ops-ui,reports-ui): improve workflow reliability and support Dart 3.14`

## PR Type

- [ ] Feature
- [x] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [x] Chore
- [x] Breaking change

## Summary

Fix persona tuning edits, report navigation when the run changes in browser history, and the order in which the pipeline publishes trend data. Both UIs now resolve, build, and start with Dart 3.14 and the normal local commands.

## Problem / Context

Blank lines could interrupt a persona weights edit, and rewriting the weights could drop comments. A hydrated report page could keep showing the previous run after its URL changed. The pipeline could dispatch alerts and update trend history before it knew whether the weekly digest could be written.

With Dart 3.14 on the PATH, the previous Jaspr toolchain could not resolve its build dependencies. `jaspr build` and the launch scripts then failed when the build daemon tried to start.

## Changes Made

- Use parsed YAML node positions to update persona tuning and retain comments in the edited section.
- Pass the initial run from the server router to each report page, then reload when browser history changes the run. Ignore stale responses and responses after a page is disposed.
- Write the weekly digest before dispatching trend alerts or updating trend history.
- Update both UI toolchains to Dart 3.14, Jaspr and Jaspr Builder 0.24.0-beta.2, and Jaspr Router 0.10.0-beta.2. Resolve Build Runner 2.16.2 and Build Web Compilers 4.8.12 in both lockfiles.
- Regenerate the Reports UI client options for the new Jaspr parameter API. Keep the launch helpers on ports 8791 and 8792 while Jaspr chooses its internal ports.

## Files Changed (and Why)

- `services/engine/ops-ui/lib/backend/persona_tuning_yaml_editor.dart` handles YAML edit boundaries and comments.
- `apps/reports-ui/lib/app.dart`, `lib/pages/`, and the generated Jaspr options carry the selected run through routing, hydration, and browser history.
- Engine `PipelineRunCommand.java` and `PipelineRunCommandTest.java` enforce and verify the order of report writes and trend updates.
- Both UI manifests and lockfiles define the Dart 3.14 toolchain. The root launch scripts use the supported Jaspr 0.24 options, and the setup guides document the new SDK and CLI.

## How to Test

1. Confirm `dart --version` reports 3.14.x and run `dart pub global activate jaspr_cli 0.24.0-beta.2`.
2. Run `dart pub update` and `jaspr build` in both UI directories. From the repository root, run `./scripts/verify.sh` with the configured Java toolchain.
3. Start `./scripts/run-ops-ui.sh` and `./scripts/run-reports-ui.sh` in separate terminals. Open the apps on ports 8791 and 8792.
4. In Ops UI, save persona tuning with inline comments and a blank line between weights. Check the new values and retained comments in `personas.yaml`.
5. In Reports UI, open a weekly report. In the browser console, run the snippet below with the ID of another run that has a weekly report. Confirm that report loads, then use Back to return to the first report.

   ```js
   const url = new URL(location.href);
   url.searchParams.set('run', 'second-run-id');
   history.pushState(null, '', url);
   window.dispatchEvent(new PopStateEvent('popstate'));
   ```

6. Run the engine with a directory as the weekly output path. Confirm it exits with an error and leaves the trend history and JSON alert output untouched. Retry with a writable file path.

## Validation Evidence

- With Dart 3.14.0 on the normal PATH, `dart pub update` and `jaspr build` passed in both UI projects. `./scripts/verify.sh` passed with the local Java 27 override.
- Both launch scripts served requests at the same time on ports 8791 and 8792. Reports UI loaded the run index and opened a weekly report in the browser.
- Before the toolchain upgrade, Chrome confirmed a query-only change to a second run and Back to the first without reloading. A Dart smoke check covered blank lines between weights, inline and standalone comments, resetting tuning, CRLF input, and a file without a final newline.
- `git diff --check` passed.

## Risks / Trade-offs

- The YAML editor expects persona entries written as block mappings. Comments inside rewritten tuning fields may move to standalone comment lines.
- Batch reports are written before the weekly digest. A digest write failure can leave the batch reports on disk while trend history and alert outputs stay untouched.
- The latest stable Jaspr Builder requires analyzer 12.x, which conflicts with the web compiler that supports Dart 3.14. The prerelease exception is limited to Jaspr, its builder, router, and CLI.

## Backward Compatibility

- [ ] No breaking changes
- [x] Breaking changes (described below)

Both UIs now require Dart 3.14.x and the matching Jaspr CLI. The launch scripts no longer accept the internal web and proxy port overrides.

## Deployment / Rollout Notes

- Activate Jaspr CLI with Dart 3.14 so the global executable uses the matching SDK. The launch scripts retain the existing app URLs.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated
- [x] Local verification completed
