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
- MUST use the repository PR template for every pull request.
- MUST keep PR titles and descriptions in English.
- MUST name the affected subproject explicitly when a PR is scoped to a single subproject.
- MUST keep `ops-ui` under engine ownership unless an explicit ownership split is planned.
- MUST remove obsolete repository-level automation rather than carrying dead workflows forward.

## Repository Layout

- `services/engine`: core engine and CLI workflows
- `services/engine/ops-ui`: visual configuration UI for the engine
- `apps/reports-ui`: reports UI

## Docs Update Matrix

- Update root `README.md` for repository map, ownership, and contribution flow.
- Update root `AGENTS.md` for parent-repository rules only.
- Update child project docs when behavior or architecture changes inside a child project.

PRs that change repository structure or ownership boundaries without the matching docs updates are not ready to merge.
