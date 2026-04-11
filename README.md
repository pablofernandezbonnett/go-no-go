# Go No-Go

Local-first monorepo for screening engineering opportunities before they turn into expensive interview loops.

Go No-Go keeps the engine CLI as the source of truth. `ops-ui` stays under engine ownership for run orchestration and config work, while `reports-ui` is a browser companion for reading artifacts and running ad-hoc evaluations through the engine.

## Overview

The repository is built around one explicit workflow: collect or paste job input, normalize it, evaluate it against one or more personas, then publish artifacts that are easy to inspect from the terminal or the browser.

## Motivation

The project exists to reduce low-signal interview effort, especially for international product engineers targeting Japan. It prefers deterministic rules, visible trade-offs, and curated company tracking over opaque recommendation systems or scraping at internet scale.

## Initial Scope

- Track selected companies instead of crawling the whole market.
- Keep evaluation persona-aware and explainable.
- Keep private candidate data local and untracked.
- Use thin UIs for operations and report reading without moving engine logic out of the CLI.

## Repository Map

- `services/engine`: Java CLI that owns config, evaluation logic, and output artifacts.
- `services/engine/ops-ui`: engine-owned Jaspr UI for runs, companies, personas, and local operational settings.
- `apps/reports-ui`: Jaspr UI for reading engine artifacts and triggering ad-hoc evaluations through the engine.

## Stack Snapshot

- Engine: Java 21 + Gradle + Picocli
- Browser UIs: Jaspr + Dart

## Start Here

- Quickstart: [`docs/quickstart.md`](docs/quickstart.md)
- Advanced guide: [`docs/advanced-guide.md`](docs/advanced-guide.md)
- Engine: [`services/engine/README.md`](services/engine/README.md)
- Operations UI: [`services/engine/ops-ui/README.md`](services/engine/ops-ui/README.md)
- Reports UI: [`apps/reports-ui/README.md`](apps/reports-ui/README.md)
- Docs assets: [`docs/README.md`](docs/README.md)

Prerequisites:

- Java 21 available on your `PATH`
- Dart SDK 3.10+ available on your `PATH`
- `jaspr_cli` installed for local UI development

You can clone and run the repository without adding personal candidate data. The tracked config and example files are enough to build the engine and both UIs.

First-time local setup:

```bash
git clone https://github.com/pablofernandezbonnett/go-no-go.git
cd go-no-go
dart pub global activate jaspr_cli
(cd services/engine/ops-ui && dart pub get)
(cd apps/reports-ui && dart pub get)
```

If `jaspr` is still not found after activation, add the Dart pub global bin directory to your `PATH` and restart your shell.

Essential local commands:

```bash
./scripts/verify.sh
./scripts/run-ops-ui.sh
./scripts/run-reports-ui.sh
cd services/engine && ./gradlew installDist && ./build/install/go-no-go-engine/bin/go-no-go-engine tui
```

Default local URLs:

- `ops-ui`: `http://localhost:8791`
- `reports-ui`: `http://localhost:8792`

## Surface Preview

### Engine

![CLI sample evaluation](docs/screenshots/cli-check.png)

### Operations UI

![Operations UI create run screen](docs/screenshots/ops-ui-home.png)

### Reports UI

![Reports UI evaluate workflow](docs/screenshots/reports-ui-evaluate.png)

## Public Status

This repository is published as a read-only example of a personal tool I use locally.

- License: [MIT](LICENSE)
- Contribution policy: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security policy: [SECURITY.md](SECURITY.md)

Personal candidate profiles, CVs, and other private job-search inputs must stay local and untracked. The repository keeps templates and examples only.
