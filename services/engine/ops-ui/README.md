# Go/No-Go Engine Operations UI

Engine-owned Jaspr UI for composing runs and editing the safe parts of engine config without making the browser authoritative.

Stack: Jaspr and Dart.

## What It Is For

This app is the operator layer around the engine. It helps you build explicit pipeline requests, inspect sanitized run status, and manage a small set of config-backed inputs while keeping `go-no-go-engine` as the execution source of truth.

## Main Areas

- `Create Run`: build a pipeline request with persona, candidate profile mode, fetch policy, and company scope.
- `Runs`: inspect queued, running, succeeded, and failed runs with sanitized summaries.
- `Companies`: add or review tracked companies stored in engine config.
- `Personas`: create or tune persona settings, including salary floors.
- `Candidate Profiles`: list available profile ids without exposing local YAML contents.
- `Settings`: adjust local UI polling and refresh behavior.

## Start Here

- Repository quickstart: [`../../../docs/quickstart.md`](../../../docs/quickstart.md)
- Advanced guide: [`../../../docs/advanced-guide.md`](../../../docs/advanced-guide.md)

Run from the repository root:

```bash
./scripts/run-ops-ui.sh
```

Default local URL:

- `http://localhost:8791`

The helper script already sets `ENGINE_ROOT` and dedicated Jaspr ports so this app can run beside `reports-ui`.

## Boundary Rules

- The engine CLI remains the execution source of truth.
- Engine config files and runtime contracts stay authoritative.
- Filesystem paths, shell commands, candidate profile contents, and live logs stay server-side.

## Screen

![Operations UI create run screen](../../../docs/screenshots/ops-ui-home.png)
