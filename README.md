# Go No-Go

Monorepo for the Go No-Go system.

## Repository Map

- `services/engine`: core engine and CLI-driven workflows
- `services/engine/ops-ui`: visual configuration UI owned by the engine
- `apps/reports-ui`: reporting UI plus ad-hoc job evaluation delegated to the engine

## Local UI Ports

- `services/engine/ops-ui`: `http://localhost:8791` by default
- `apps/reports-ui`: `http://localhost:8792` by default

When running both Jaspr UIs at the same time, the HTTP ports are not enough by themselves.
`jaspr serve` also uses internal dev ports:

- `--web-port`: default `5467`
- `--proxy-port`: default `5567`

Recommended local dev commands:

```bash
cd services/engine/ops-ui
jaspr serve --port 8791 --web-port 5467 --proxy-port 5567
```

```bash
cd apps/reports-ui
jaspr serve --port 8792 --web-port 5468 --proxy-port 5568
```

## Working Agreements

- Cross-project repository rules live in `AGENTS.md` at the repository root.
- Pull requests for this repository use the root `.github/PULL_REQUEST_TEMPLATE.md`.
- Child project documentation remains inside each imported project directory when present.

## Migration Note

This repository was created by importing the previous standalone engine and reports UI repositories into a single parent repository while preserving history.
