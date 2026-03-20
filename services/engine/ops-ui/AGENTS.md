# Go/No-Go Engine Ops UI - Agent Guidelines

This document defines how AI coding agents must behave when contributing to the operations UI.

## Core Purpose

This app is an operations-facing Jaspr UI for the engine repository.

It is not the decision engine itself.

## Rules

- Keep the engine CLI as the execution source of truth.
- Do not reimplement decision logic, pipeline rules, or artifact generation in the UI.
- Keep the UI thin, explicit, and configuration-driven.
- Prefer Jaspr and Dart primitives already in the project before introducing new dependencies.
- Verify engine config files and run contracts before changing forms, defaults, or UI-driven command execution.
- Keep operational literals such as route ids, status values, and input modes centralized as constants.

## Repository Stack

- Jaspr
- Dart

## Run/Test Commands

- Install deps: `dart pub get`
- Static analysis: `dart analyze`
- Run app: `jaspr serve --port 8791 --web-port 5467 --proxy-port 5567`

## UI Guardrails

- Keep UI state explicit and easy to inspect.
- Keep engine command construction deterministic and readable.
- Do not hide engine failures behind vague UI messages.
- Treat `ENGINE_ROOT`, `ENGINE_GRADLEW`, and runtime config files as source-of-truth inputs.

## Source of Truth

- `pubspec.yaml` for runtime and package choices.
- `lib/backend/*`, `lib/services/ops_api.dart`, and `lib/models/ops_models.dart` for engine-facing contracts and orchestration.
- `../config/*.yaml` and the engine docs for the config shapes this UI edits or visualizes.
- `README.md` for setup and `ARCHITECTURE.md` when present for UI boundary decisions.

## Preferred Skills

- Use `jaspr-ui-slice` for Jaspr page/component/service work and browser-facing orchestration.

## Preferred MCPs

- Use Context7 for Jaspr and Dart API verification.
- Use chrome-devtools for browser UI validation and responsive checks.

## Engineering Heuristics

- DRY: prefer one source of truth for stable logic, contracts, and constants. Extract shared behavior when repetition is real and the same fix would otherwise need to be repeated.
- YAGNI: do not add speculative features, extension points, flags, or abstractions for hypothetical future needs.
- KISS: choose the simplest implementation that is easy to explain, test, and change.
- Simple is not easy: invest in small focused functions and clear structure instead of the fastest large-function shortcut.
- Accept small local duplication temporarily when the right abstraction is not yet clear. Extract only when it improves readability and maintainability.

## NEVER Rules

- NEVER move engine decision logic into the UI.
- NEVER make the UI authoritative for engine defaults or runtime contracts.
- NEVER hide command construction in multiple layers that are hard to inspect.

## Incorrect vs Correct

- Incorrect: move engine evaluation logic into the UI because a form already has the input values.
- Correct: keep the UI as an operator layer that builds and runs explicit engine commands.

- Incorrect: hide command defaults or mutate them in multiple places.
- Correct: keep command construction centralized and easy to inspect when debugging a failed run.

## Definition of Done

- `dart analyze` passes or failures are explained.
- Engine ownership and CLI-first boundaries remain intact.

## Docs Update Matrix

- Update `README.md` for run instructions, runtime variables, or visible workflow changes.
- Update `AGENTS.md` when UI operating rules or engine interaction contracts change.
