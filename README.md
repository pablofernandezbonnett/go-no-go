# Go No-Go

Monorepo for the Go No-Go system.

The engine remains the source of truth. The browser UIs are companion layers for operations and report consumption, not replacements for the CLI contracts.

## Public Status

This repository is published as a personal read-only project for reference.

- License: [MIT](LICENSE)
- Contribution policy: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security policy: [SECURITY.md](SECURITY.md)

Personal candidate profiles, CVs, and other private job-search inputs are local-only runtime files and must not be committed. The tracked repository keeps templates and examples only.

## Stack

- `services/engine`: Java 21 + Gradle + Picocli
- `services/engine/ops-ui`: Jaspr + Dart
- `apps/reports-ui`: Jaspr + Dart

## Repository Map

- `services/engine`: core engine and CLI-driven workflows
- `services/engine/ops-ui`: visual configuration UI owned by the engine
- `apps/reports-ui`: reporting UI plus ad-hoc job evaluation delegated to the engine

## Prerequisites

- Java 21 available on your `PATH`
- Dart SDK installed
- `jaspr_cli` installed for local UI development

## Start Here

- Quickstart: [`docs/quickstart.md`](docs/quickstart.md)
- Advanced guide: [`docs/advanced-guide.md`](docs/advanced-guide.md)
- Engine details: [`services/engine/README.md`](services/engine/README.md)
- Operations UI details: [`services/engine/ops-ui/README.md`](services/engine/ops-ui/README.md)
- Reports UI details: [`apps/reports-ui/README.md`](apps/reports-ui/README.md)
- Documentation index: [`docs/README.md`](docs/README.md)

Essential local commands:

```bash
./scripts/verify.sh
./scripts/run-ops-ui.sh
./scripts/run-reports-ui.sh
cd services/engine && ./gradlew installDist && ./build/install/go-no-go-engine/bin/go-no-go-engine tui
```

## Screenshots

CLI sample evaluation:

![CLI screenshot](docs/screenshots/cli-check.png)

Terminal launcher:

![TUI screenshot](docs/screenshots/tui-launcher.png)

Operations UI:

![Ops UI screenshot](docs/screenshots/ops-ui-home.png)

Reports UI batch view:

![Reports UI batch screenshot](docs/screenshots/reports-ui-batch.png)

## Local UI Ports

- `services/engine/ops-ui`: `http://localhost:8791`
- `apps/reports-ui`: `http://localhost:8792`

Use the root helper scripts so both Jaspr apps keep separate internal `--web-port` and `--proxy-port` values:

```bash
./scripts/run-ops-ui.sh
./scripts/run-reports-ui.sh
```

## Working Agreements

- Cross-project repository rules live in `AGENTS.md` at the repository root.
- Pull requests for this repository use the root `.github/PULL_REQUEST_TEMPLATE.md`.
- Child project documentation remains inside each imported project directory when present.
- Browser UIs intentionally avoid exposing local profile YAML, filesystem paths, shell commands, or live execution logs.

## Verification Entry Point

```bash
./scripts/verify.sh
```

## Migration Note

This repository was created by importing the previous standalone engine and reports UI repositories into a single parent repository while preserving history.
