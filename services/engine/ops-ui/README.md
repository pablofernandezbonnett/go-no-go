# Go/No-Go Engine Operations UI

Browser UI for operating the CLI pipeline from the same repository.

This app is intentionally lightweight and CLI-first:

- Keeps `go-no-go-engine` as the execution source of truth.
- Lets you configure and trigger runs from a form.
- Shows run status and sanitized request summaries.

![Operations UI screenshot](../../../docs/screenshots/ops-ui-home.png)

## Features (MVP)

- Load personas, candidate profiles, and company list from `../config`.
- Create pipeline runs with controlled parameters.
- Choose runtime candidate-profile mode for each run (`Auto`, `None`, or explicit profile id).
- Queue runs and execute them one at a time.
- Track run status: `queued`, `running`, `succeeded`, `failed`.
- Inspect run request settings and lifecycle timestamps in the browser.
- Create personas with salary-floor support and tune existing persona weights/strategy.
- Browse candidate profile ids without exposing YAML content or personal fields.

## Privacy posture

- Candidate profiles are local runtime inputs; the browser UI only exposes stable ids.
- Filesystem paths, shell commands, and live execution logs stay server-side.
- Internal server errors return sanitized messages instead of raw exception text.

## Runtime defaults

- Default port: `8791`
- Default engine root: parent folder (`..`)
- Default command: `./gradlew run --args="pipeline run ..."`

Prerequisites:

- Dart SDK installed
- `jaspr_cli` available on your `PATH`

## Start Here

- Repository quickstart: [`../../../docs/quickstart.md`](../../../docs/quickstart.md)
- Full operational guide: [`../../../docs/advanced-guide.md`](../../../docs/advanced-guide.md)

Run from the repository root:

```bash
./scripts/run-ops-ui.sh
```

Open:

- `http://localhost:8791`

The root helper script already:

- starts from the monorepo root
- changes into `services/engine/ops-ui`
- sets `ENGINE_ROOT` to the engine project by default
- pins dedicated Jaspr `--web-port` and `--proxy-port` values so it can run alongside `reports-ui`

For custom ports, environment overrides, or Jaspr collision troubleshooting, use the advanced guide.

## Responsive UI

The UI includes responsive behavior for desktop and mobile:

- single-column form on small screens
- horizontal overflow handling for runs table
- stacked actions and compact panel spacing on narrow devices
