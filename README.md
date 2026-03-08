# Go No-Go

Monorepo for the Go No-Go system.

## Repository Map

- `services/engine`: core engine and CLI-driven workflows
- `services/engine/ops-ui`: visual configuration UI owned by the engine
- `apps/reports-ui`: reporting UI

## Working Agreements

- Cross-project repository rules live in `AGENTS.md` at the repository root.
- Pull requests for this repository use the root `.github/PULL_REQUEST_TEMPLATE.md`.
- Child project documentation remains inside each imported project directory when present.

## Migration Note

This repository was created by importing the previous standalone engine and reports UI repositories into a single parent repository while preserving history.
