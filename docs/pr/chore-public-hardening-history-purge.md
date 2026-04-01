## PR Title

`chore(repo): harden local network boundaries and purge personal history`

## PR Type

- [ ] Feature
- [x] Bug fix
- [ ] Refactor
- [x] Docs
- [x] Test
- [x] Chore
- [ ] Breaking change

## Summary

Harden local-only execution paths against private-network access, remove browser-visible local paths from report artifacts, and rewrite repository history to purge the old personal candidate profile file and related sample strings.

## Problem / Context

The repository was close to public-read-only shape, but there were still three material issues: `fetch-web` could still reach private/local destinations through the standard engine path, `reports-ui` still exposed local artifact paths in some report payloads, and git history still contained an old private candidate profile file plus older personal sample strings in tests and docs.

## Changes Made

- Locked the engine HTTP fetch path to public URLs only, including the default `fetch-web` path and manual redirect handling.
- Tightened `ops-ui` company/run validation and sanitized browser-visible report artifacts so local paths stay server-side.
- Rewrote repository history to remove the old private candidate profile file from all refs and replace the old personal sample strings with generic placeholders.

## Files Changed (and Why)

- `services/engine/src/main/java/com/pmfb/gonogo/engine/DirectInputSecurity.java`, `services/engine/src/main/java/com/pmfb/gonogo/engine/job/CareerPageHttpFetcher.java`, `services/engine/src/main/java/com/pmfb/gonogo/engine/config/ConfigValidator.java`: make public-URL validation authoritative across direct evaluation, config validation, and the default web fetch path.
- `services/engine/ops-ui/lib/backend/engine_config_repository.dart`, `services/engine/ops-ui/lib/backend/run_manager.dart`, `services/engine/ops-ui/lib/main.server.dart`: reject unsafe company URLs, tighten run request identifiers, and keep the UI loopback-bound by default.
- `apps/reports-ui/lib/report_access/report_index.dart`, `apps/reports-ui/lib/backend/evaluation_input_safety.dart`, `apps/reports-ui/lib/backend/evaluation_runner.dart`, `apps/reports-ui/lib/main.server.dart`: prevent unsafe ad-hoc URL input and strip local artifact paths from browser-visible payloads.
- `services/engine/src/test/java/com/pmfb/gonogo/engine/job/CareerPageHttpFetcherTest.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/config/ConfigValidatorTest.java`, `services/engine/src/test/java/com/pmfb/gonogo/engine/PipelineRunCommandTest.java`: cover the hardening behavior and keep the pipeline test independent from loopback URLs.
- `docs/pr/**`, `docs/README.md`: rename the PR notes folder from `docs/pr-drafts` to `docs/pr` and preserve this PR document alongside the older ones.

## How to Test

1. Run `./scripts/verify.sh`.
2. Confirm `reports-ui` no longer returns `source_file`, `batch_json`, or `weekly_digest` paths in browser-visible report payloads.
3. Confirm `ops-ui` rejects local/private company URLs and invalid persona ids.
4. Confirm the removed private profile file no longer appears anywhere in repository history.

## Validation Evidence

- `./scripts/verify.sh`
- `git rev-list --all -- <removed-private-profile-path> | wc -l`
- `git fsck --full --no-reflogs`

## Risks / Trade-offs

- The history rewrite changes commit SHAs across affected branches, so any existing clones or open work based on the old history must be resynced.
- Loopback-only defaults are safer, but anyone intentionally exposing the UIs to a LAN must now opt in explicitly with the bind env vars.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- Force-push all rewritten branches and tags to the remote after review.
- Any old clones should be re-cloned or hard-reset to the rewritten history.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
