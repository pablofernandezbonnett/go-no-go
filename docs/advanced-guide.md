# Advanced Guide

Operational guide for the whole monorepo. This keeps the longer command list out of the README files.

## Prerequisites

- Java 21 on your `PATH`
- Dart SDK installed
- `jaspr_cli` installed

## Repository Verification

From the repository root:

```bash
./scripts/verify.sh
```

This runs:

- `./gradlew test` in `services/engine`
- `dart analyze` in `services/engine/ops-ui`
- `dart analyze`, `jaspr clean`, and `jaspr build` in `apps/reports-ui`

## Engine CLI

Work from `services/engine` unless stated otherwise.

### Invocation Forms

Use either:

```bash
./gradlew run --args="<subcommand and flags>"
```

or:

```bash
./gradlew installDist
./build/install/go-no-go-engine/bin/go-no-go-engine <subcommand and flags>
```

Notes:

- The logical root command is `gonogo`.
- The local installed launcher is `go-no-go-engine`.

### Essential Commands

Show CLI help:

```bash
./gradlew run --args="--help"
```

Validate runtime config:

```bash
./gradlew run --args="config validate"
```

Smart check from URL, file, YAML, or inline text:

```bash
./gradlew run --args="check https://www.fastretailing.com/careers/en/job-description/?id=1588"
```

Run the default full pipeline:

```bash
./gradlew run --args="run"
```

Equivalent explicit command:

```bash
./gradlew run --args="pipeline run-all"
```

Run one persona pipeline:

```bash
./gradlew run --args="pipeline run --persona product_expat_engineer --fetch-web-first"
```

Refresh saved ad-hoc evaluation artifacts:

```bash
./gradlew run --args="rerun-ad-hoc --input-dir output/ad-hoc-evaluations"
```

Launch the terminal UI:

```bash
./gradlew installDist
./build/install/go-no-go-engine/bin/go-no-go-engine tui
```

The TUI is keyboard-driven:

- arrows to move
- `Space` to toggle multi-select items
- `Enter` to confirm

### Candidate Profiles

- Real candidate profiles stay local and untracked.
- Tracked examples live under `config/candidate-profiles/`.
- Use `--candidate-profile none` to disable candidate-aware scoring explicitly.

## Operations UI

From the repository root:

```bash
./scripts/run-ops-ui.sh
```

Open:

- `http://localhost:8791`

Optional overrides:

- `OPS_UI_PORT`
- `OPS_UI_WEB_PORT`
- `OPS_UI_PROXY_PORT`
- `OPS_UI_BIND_HOST` (`localhost` by default; use `0.0.0.0` only on purpose)
- `ENGINE_ROOT`
- `ENGINE_GRADLEW`

## Reports UI

From the repository root:

```bash
./scripts/run-reports-ui.sh
```

Open:

- `http://localhost:8792`

Optional overrides:

- `REPORTS_UI_PORT`
- `REPORTS_UI_WEB_PORT`
- `REPORTS_UI_PROXY_PORT`
- `REPORTS_UI_BIND_HOST` (`localhost` by default; use `0.0.0.0` only on purpose)
- `REPORTS_ROOT`
- `ENGINE_ROOT`
- `ENGINE_GRADLEW`

## Running Both Jaspr UIs Together

Use the root helper scripts:

```bash
./scripts/run-ops-ui.sh
./scripts/run-reports-ui.sh
```

Why this matters:

- `--port` is the final app URL.
- `--web-port` is the internal webdev resource server.
- `--proxy-port` is the Jaspr proxy server.

If you see `Address already in use` on `5467`, `5468`, `5567`, or `5568`, the collision is in Jaspr's internal dev services, not the public app URL.

## Artifact Paths

- Engine output root: `services/engine/output/`
- Saved ad-hoc evaluations: `services/engine/output/ad-hoc-evaluations/`
- Reports UI default `REPORTS_ROOT`: `services/engine/output/`

## Further Reading

- Repository overview: [`../README.md`](../README.md)
- Engine README: [`../services/engine/README.md`](../services/engine/README.md)
- Operations UI README: [`../services/engine/ops-ui/README.md`](../services/engine/ops-ui/README.md)
- Reports UI README: [`../apps/reports-ui/README.md`](../apps/reports-ui/README.md)
