# Documentation Assets

Repository docs, screenshot sources, and preserved PR notes.

## Main Files

- `quickstart.md`: minimum commands for getting the monorepo running locally.
- `advanced-guide.md`: deeper CLI/UI setup, runtime notes, and troubleshooting.
- `pr/`: preserved PR documents for notable repository branches.
- `screenshots/`: stable assets used by the README files.

## Screenshot Set

- `screenshots/cli-check.html` and `screenshots/cli-check.png`: sample CLI evaluation render.
- `screenshots/tui-launcher.html` and `screenshots/tui-launcher.png`: terminal launcher render.
- `screenshots/ops-ui-home.png`: current Operations UI create-run capture.
- `screenshots/reports-ui-evaluate.png`: evaluate workflow with live results.
- `screenshots/reports-ui-evaluate-modal.png`: saved evaluation modal from URL history.
- `screenshots/reports-ui-batch.png`: batch report table capture.
- `screenshots/reports-ui-runs.png`: optional runs overview capture.

## Refreshing Terminal Screenshots

Validate the current commands first:

```bash
cd services/engine
./gradlew installDist
./build/install/go-no-go-engine/bin/go-no-go-engine check examples/raw-job-text.example.txt
./build/install/go-no-go-engine/bin/go-no-go-engine tui
```

Then:

1. Update the matching HTML template if the visible output changed.
2. Keep shared styling in `screenshots/terminal.css`.
3. Export the PNG without browser chrome.

## Refreshing Browser Screenshots

From the monorepo root:

```bash
./scripts/run-ops-ui.sh
./scripts/run-reports-ui.sh
```

Current capture targets:

- `http://localhost:8791` -> `screenshots/ops-ui-home.png`
- `http://localhost:8792/evaluate` -> `screenshots/reports-ui-evaluate.png`
- `http://localhost:8792/evaluate` with a saved-evaluation modal open -> `screenshots/reports-ui-evaluate-modal.png`
- `http://localhost:8792/batch?run=root` -> `screenshots/reports-ui-batch.png`
- `http://localhost:8792/` -> `screenshots/reports-ui-runs.png` (optional)

## Finish

1. Check that the affected README files render the new images correctly.
2. Keep file names stable unless there is a clear reason to rename them.
3. Run repository verification:

```bash
./scripts/verify.sh
```
