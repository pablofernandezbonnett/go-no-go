## PR Title

`fix(engine,ops-ui,reports-ui): improve workflow reliability`

## PR Type

- [ ] Feature
- [x] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [ ] Chore
- [ ] Breaking change

## Summary

Fix persona tuning edits, report navigation when the run changes in browser history, and the order in which the pipeline publishes trend data.

## Problem / Context

Blank lines could interrupt a persona weights edit, and rewriting the weights could drop comments. A hydrated report page could keep showing the previous run after its URL changed. The pipeline could dispatch alerts and update trend history before it knew whether the weekly digest could be written.

## Changes Made

- Use parsed YAML node positions to update persona tuning and retain comments in the edited section.
- Pass the initial run from the server router to each report page, then reload when browser history changes the run. Ignore stale responses and responses after a page is disposed.
- Write the weekly digest before dispatching trend alerts or updating trend history.
- Document the affected workflows in each project.

## Files Changed (and Why)

- `services/engine/ops-ui/lib/backend/persona_tuning_yaml_editor.dart` handles YAML edit boundaries and comments.
- `apps/reports-ui/lib/app.dart`, `lib/pages/`, and the generated Jaspr options carry the selected run through routing, hydration, and browser history.
- Engine `PipelineRunCommand.java` and `PipelineRunCommandTest.java` enforce and verify the order of report writes and trend updates.

## How to Test

1. Run `./scripts/verify.sh` from the repository root with Dart 3.13.x and the configured Java toolchain.
2. In Ops UI, save persona tuning with inline comments and a blank line between weights. Check the new values and retained comments in `personas.yaml`.
3. In Reports UI, open a weekly report. In the browser console, run the snippet below with the ID of another run that has a weekly report. Confirm that report loads, then use Back to return to the first report.

   ```js
   const url = new URL(location.href);
   url.searchParams.set('run', 'second-run-id');
   history.pushState(null, '', url);
   window.dispatchEvent(new PopStateEvent('popstate'));
   ```

4. Run the engine with a directory as the weekly output path. Confirm it exits with an error and leaves the trend history and JSON alert output untouched. Retry with a writable file path.

## Validation Evidence

- `./scripts/verify.sh` passed with Dart 3.13.7 and the local Java 27 override. This covered the engine tests, both Dart analyses, and the Reports UI build.
- Chrome confirmed a direct weekly report link, a query-only change to a second run, and Back to the first run without a page reload.
- A focused Dart smoke check covered a blank line between weights, inline and standalone comments, resetting tuning, CRLF input, and a file without a final newline.
- `git diff --check` passed.

## Risks / Trade-offs

- The YAML editor expects persona entries written as block mappings. Comments inside rewritten tuning fields may move to standalone comment lines.
- Batch reports are written before the weekly digest. A digest write failure can leave the batch reports on disk while trend history and alert outputs stay untouched.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No new dependencies or configuration changes are required.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated
- [x] Local verification completed
