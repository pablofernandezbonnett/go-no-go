## PR Title

`chore(repo): upgrade public runtime toolchains`

## PR Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [x] Docs
- [ ] Test
- [x] Chore
- [ ] Breaking change

## Summary

Upgrade the engine and both Jaspr UIs to their current compatible toolchains, align the public Java contract with the installed Java 26 runtime, and make fresh-clone verification reproducible.

## Problem / Context

The repository declared Java 21 while the active development runtime is Java 26. It also installed an unpinned Jaspr CLI while both UIs depended on the older Jaspr 0.22 line, causing current CLI builds to reject the dependency constraints.

## Changes Made

- Upgraded both UIs to Jaspr 0.23.2 and refreshed direct build, router, lint, and transitive lockfile dependencies compatible with Dart 3.10.
- Pinned the Jaspr CLI to 0.23.2 in CI and setup documentation.
- Aligned the engine Gradle property, GitHub Actions runtime, and repository documentation on Java 26.
- Removed the remaining actionable Dart analyzer diagnostics and retained the generated Jaspr server-options update.

## Files Changed (and Why)

- `services/engine/gradle.properties`, `services/engine/build.gradle.kts`, `.github/workflows/go-no-go-verify.yml`: make Java 26 the engine and CI runtime contract.
- `services/engine/ops-ui/pubspec.yaml`, `apps/reports-ui/pubspec.yaml`, and their lockfiles: move both Jaspr applications to the 0.23 toolchain and current compatible build dependencies.
- `services/engine/ops-ui/analysis_options.yaml`, `apps/reports-ui/analysis_options.yaml`: use the current Jaspr lint plugin; disable only the noisy reports CSS ordering diagnostic.
- `apps/reports-ui/lib/main.server.dart`, `apps/reports-ui/lib/models/reports_index_payload.dart`, `apps/reports-ui/lib/pages/reports_view_helpers.dart`, `apps/reports-ui/lib/main.server.options.dart`: apply analyzer fixes and commit the framework-generated source update.
- `README.md`, `ARCHITECTURE.md`, `AGENTS.md`, `docs/quickstart.md`, `docs/advanced-guide.md`, `services/engine/README.md`, `services/engine/AGENTS.md`: document the Java 26 and Jaspr CLI 0.23.2 requirements.

## How to Test

1. Ensure Java 26 and Dart 3.10+ are available on `PATH`.
2. Install the CLI with `dart pub global activate jaspr_cli ^0.23.2`.
3. Run `./scripts/verify.sh` from the repository root.

## Validation Evidence

- `./scripts/verify.sh`
- `cd services/engine && ./gradlew test --warning-mode all` with Java 26
- `dart analyze` in `services/engine/ops-ui` and `apps/reports-ui`
- `jaspr build` in `apps/reports-ui`

## Risks / Trade-offs

- Java 26 is now required for engine builds and CI; contributors using Java 21 must upgrade their local runtime.
- `build_runner` remains at 2.15.1 because 2.15.2 requires Dart 3.11, while the repository continues to support Dart 3.10.
- The reports UI retains its manually grouped CSS declarations, so only its `styles_ordering` lint is disabled; all other configured lints remain active.

## Backward Compatibility

- [ ] No breaking changes
- [x] Breaking changes (described below)

Engine contributors and CI environments must provide Java 26. The browser UI routes, engine artifact contracts, and runtime behavior are unchanged.

## Deployment / Rollout Notes

- Ensure GitHub Actions can provision Temurin 26 before merging.
- Contributors should refresh UI dependencies with `dart pub get` after pulling the change.

## Checklist

- [x] Scope is focused and aligned with the issue
- [x] Code follows project conventions
- [x] Tests added/updated where needed
- [x] Documentation updated (`README.md`, `AGENTS.md`, etc.)
- [x] Local verification completed
