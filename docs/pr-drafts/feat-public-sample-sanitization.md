## PR Title

`chore(repo): sanitize example candidate data and ignore local personal files`

## PR Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Docs
- [ ] Test
- [x] Chore
- [ ] Breaking change

## Summary

Sanitize example candidate data in tracked assets and expand ignore rules so local personal files can stay on disk without leaking into the repository.

## Problem / Context

The repository was close to being shared more broadly, but example assets and test fixtures still contained personal-looking identifiers. Local CVs and candidate YAML files also needed stronger ignore coverage.

## Changes Made

- Replaced personal-looking candidate identifiers in tracked examples, screenshots, and tests with neutral sample values.
- Updated ignore rules for local candidate profiles and common resume/CV file patterns.
- Kept the repository examples usable while making the tracked data safer to publish.

## Files Changed (and Why)

- `.gitignore`: ignore local candidate profile and resume-style files that must not be committed.
- `services/engine/README.md`: align the documented sample values with sanitized identifiers.
- `services/engine/src/test/**`: replace personal-looking fixture identifiers in tests.
- `docs/screenshots/cli-check.*`: refresh the CLI example asset to remove personal-looking values.

## How to Test

1. Review the updated sample identifiers in the tracked docs and test fixtures.
2. Confirm local candidate YAML or resume-like files now match the ignore rules.
3. Run `./scripts/verify.sh`.

## Validation Evidence

- `./scripts/verify.sh`

## Risks / Trade-offs

- Ignore patterns are intentionally broad enough to protect local personal files, so future example filenames should avoid matching those patterns unless explicitly intended.

## Backward Compatibility

- [x] No breaking changes
- [ ] Breaking changes (described below)

## Deployment / Rollout Notes

- No deployment changes.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
