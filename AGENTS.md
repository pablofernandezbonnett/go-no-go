# Agents Contract - Go No-Go Monorepo

Operational contract for the Go No-Go parent repository.

## Scope

- Applies to the repository root and cross-project changes.
- Applies to repository-level documentation, PR hygiene, and ownership boundaries.
- Child project-specific rules remain in descendant files when present.

## Hard Rules

- MUST keep the monorepo layout stable unless an explicit migration is planned.
- MUST keep application code inside its designated subproject directory.
- MUST keep repository-level documentation in English.
- MUST make the active stack explicit in repository-level docs when repository structure changes.
- MUST use the repository PR template for every pull request.
- MUST keep PR titles and descriptions in English.
- MUST name the affected subproject explicitly when a PR is scoped to a single subproject.
- MUST keep `ops-ui` under engine ownership unless an explicit ownership split is planned.
- MUST remove obsolete repository-level automation rather than carrying dead workflows forward.
- MUST verify source-of-truth project docs and run `./scripts/verify.sh` for root-level or cross-project changes.

## Repository Layout

- `services/engine`: core engine and CLI workflows
- `services/engine/ops-ui`: visual configuration UI for the engine
- `apps/reports-ui`: reports UI

## Repository Stack

- Java 25 + Gradle + Picocli
- Jaspr + Dart

## Verification Entry Point

- Run repository verify: `./scripts/verify.sh`

## Agent Routing

- For `services/engine/**`, consult `services/engine/AGENTS.md` first.
- For `services/engine/ops-ui/**`, consult `services/engine/ops-ui/AGENTS.md` first.
- For `apps/reports-ui/**`, consult `apps/reports-ui/AGENTS.md` first.
- For root docs, workflows, and repository scripts, this file stays authoritative.
- For cross-project contract changes, consult every affected child `AGENTS.md` plus the root docs.

## Root Sources of Truth

- Root `README.md`, `ARCHITECTURE.md`, and `AGENTS.md` for repository boundaries, ownership, and stack map.
- `scripts/verify.sh` for repository-wide verification.
- Child manifests, child docs, and child `AGENTS.md` files for stack-specific runtime behavior.

## Preferred Skills

- Use `jvm-service-guardrails` for Java/Gradle engine work in `services/engine`.
- Use `jaspr-ui-slice` for Jaspr/Dart UI work in `services/engine/ops-ui` and `apps/reports-ui`.

## Preferred MCPs

- Use Context7 for Java, Gradle, Picocli, Jaspr, and Dart API verification.
- Use chrome-devtools for browser-facing validation of the Jaspr UIs.

## Engineering Heuristics

- DRY: prefer one source of truth for stable logic, contracts, and constants. Extract shared behavior when repetition is real and the same fix would otherwise need to be repeated.
- YAGNI: do not add speculative features, extension points, flags, or abstractions for hypothetical future needs.
- KISS: choose the simplest implementation that is easy to explain, test, and change.
- Simple is not easy: invest in small focused functions and clear structure instead of the fastest large-function shortcut.
- Accept small local duplication temporarily when the right abstraction is not yet clear. Extract only when it improves readability and maintainability.

## NEVER Rules

- NEVER describe the engine and both UIs as equivalent owners of system behavior.
- NEVER change repository-level boundaries or `ops-ui` ownership silently.

## Incorrect vs Correct

- Incorrect: treat the monorepo as if both UIs were peers with the engine in ownership and architecture.
- Correct: keep the engine authoritative, keep `ops-ui` under engine ownership, and document that boundary at the root.

- Incorrect: change cross-project structure or stack composition and only update a child README.
- Correct: update the root docs when repository-level boundaries, ownership, or verification expectations change.

## Docs Update Matrix

- Update root `README.md` for repository map, ownership, and contribution flow.
- Update root `AGENTS.md` for parent-repository rules only.
- Update root `ARCHITECTURE.md` for stack composition, ownership boundaries, and verification strategy.
- Update child project docs when behavior or architecture changes inside a child project.

PRs that change repository structure or ownership boundaries without the matching docs updates are not ready to merge.
