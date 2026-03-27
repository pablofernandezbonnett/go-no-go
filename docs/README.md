# Documentation Assets

This folder contains repository documentation assets, including stable screenshots and preserved PR draft documents used for project tracking.

## Files

- `quickstart.md`: minimum command set for getting the whole monorepo running locally
- `advanced-guide.md`: longer operational guide covering engine CLI and both Jaspr UIs
- `pr-drafts/`: preserved PR draft documents for notable repository branches and review history
- `screenshots/cli-check.html`: source template for the CLI screenshot
- `screenshots/tui-launcher.html`: source template for the TUI screenshot
- `screenshots/terminal.css`: shared styling for terminal-style screenshots
- `screenshots/cli-check.png`: rendered CLI screenshot used in the READMEs
- `screenshots/tui-launcher.png`: rendered TUI screenshot used in the READMEs
- `screenshots/ops-ui-home.png`: Operations UI home screen capture
- `screenshots/reports-ui-batch.png`: Reports UI batch screen capture
- `screenshots/reports-ui-runs.png`: optional Reports UI runs capture
- `screenshots/reports-ui-evaluate.png`: optional Reports UI evaluate capture

## Prerequisites

- Java 21 available on your `PATH`
- Dart SDK installed
- `jaspr_cli` available on your `PATH`

## Start Here

- Repository quickstart: [`quickstart.md`](quickstart.md)
- Full operational guide: [`advanced-guide.md`](advanced-guide.md)

## Regenerating Terminal Screenshots

These screenshots are intentionally stable documentation renders, not raw terminal captures.

Validate the current commands first:

```bash
cd services/engine
./gradlew installDist
./build/install/go-no-go-engine/bin/go-no-go-engine check examples/raw-job-text.example.txt
./build/install/go-no-go-engine/bin/go-no-go-engine tui
```

Then:

1. Update `docs/screenshots/cli-check.html` or `docs/screenshots/tui-launcher.html` if the visible output changed.
2. Keep shared terminal styling in `docs/screenshots/terminal.css`.
3. Open the HTML file in a browser.
4. Export a PNG screenshot and overwrite the matching file in `docs/screenshots/`.

Recommended capture shape:

- desktop width
- the full terminal frame visible
- no browser chrome in the final PNG

## Regenerating Browser Screenshots

Run the Operations UI:

From the monorepo root:

```bash
./scripts/run-ops-ui.sh
```

Run the Reports UI:

From the monorepo root:

```bash
./scripts/run-reports-ui.sh
```

Capture the pages used by the documentation:

- `http://localhost:8791` for `screenshots/ops-ui-home.png`
- `http://localhost:8792/batch` for `screenshots/reports-ui-batch.png`

Optional extra captures:

- `http://localhost:8792/` for `screenshots/reports-ui-runs.png`
- `http://localhost:8792/evaluate` for `screenshots/reports-ui-evaluate.png`

These root helper scripts already switch into the correct app directories and keep the distinct `--web-port` and `--proxy-port` values required to run both Jaspr apps together.

## After Updating Assets

1. Check the affected README files render the new images correctly.
2. Keep file names stable unless there is a clear reason to rename them.
3. Run repository verification from the root:

```bash
./scripts/verify.sh
```
