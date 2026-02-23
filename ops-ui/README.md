# Go/No-Go Engine Operations UI

Browser UI for operating the CLI pipeline from the same repository.

This app is intentionally lightweight and CLI-first:

- Keeps `go-no-go-engine` as the execution source of truth.
- Lets you configure and trigger runs from a form.
- Shows run status, command, output folder, and live logs.

## Features (MVP)

- Load personas and company list from `../config`.
- Create pipeline runs with controlled parameters.
- Queue runs and execute them one at a time.
- Track run status: `queued`, `running`, `succeeded`, `failed`.
- Inspect command args and logs per run.

## Runtime defaults

- Default port: `8791`
- Default engine root: parent folder (`..`)
- Default command: `./gradlew run --args="pipeline run ..."`

## Start

```bash
cd ops-ui
dart pub get
jaspr serve
```

Open:

- `http://localhost:8791`

## Optional env vars

- `OPS_UI_PORT` or `PORT`: override server port.
- `ENGINE_ROOT`: override engine repository path.
- `ENGINE_GRADLEW`: override gradle wrapper command path.

## Responsive UI

The UI includes responsive behavior for desktop and mobile:

- single-column form on small screens
- horizontal overflow handling for runs table
- stacked actions and compact panel spacing on narrow devices
