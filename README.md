# Go/No-Go Engine

Decision support tool for expat engineers looking for product engineering roles in Japan.

The Go/No-Go Engine is a decision engine, not a scraping-at-scale project. It helps reduce wasted interview cycles by evaluating opportunities with explicit and explainable rules.

## Mission

Reduce low-ROI interview processes by:

- discovering relevant opportunities from selected companies
- extracting useful hiring and work-condition signals
- applying persona-aware GO / NO-GO logic
- explaining every recommendation clearly

## Initial Persona

`Product Engineer Expat`

- English-first environment preferred
- Hybrid/remote preferred
- Product companies preferred
- No consulting/dispatch roles
- No early-stage startup preference

## Decision Principles

- Intelligence over volume
- Deterministic and reproducible outputs
- Explainability is mandatory
- Rule-based logic before ML
- CLI-first; UI is not the MVP priority

## Hard Filters (Auto Reject)

- consulting / dispatch detected
- onsite-only roles
- salary missing
- abusive overtime signals

## Current Stack

- Java 25 (default, configurable)
- Gradle (wrapper)
- Picocli (CLI)
- JUnit 5 (test structure ready)

Spring Boot is intentionally deferred until API/persistence/multi-user needs appear.

## Project Status

Early MVP scaffold is initialized.

Implemented now:

- Gradle Java project setup
- CLI entrypoint (`gonogo` root command)
- initial package structure for core modules
- YAML config loader + validator
- `gonogo config validate` command
- `gonogo fetch` command to normalize raw text into YAML
- `gonogo fetch-web` command to fetch selected career pages from `config/companies.yaml`
- `gonogo evaluate` command (persona-aware)
- `gonogo evaluate-input` command for direct URL/raw-text evaluation
- `gonogo check` short command with input autodetection (URL / raw text / file path)
- `gonogo evaluate-batch` command with markdown/json report generation
- `gonogo weekly-digest` command from batch JSON reports
- `gonogo pipeline run` end-to-end orchestration
- `gonogo pipeline run-all` one-shot orchestration for all personas + all companies
- job change detection (`NEW`/`UPDATED`/`UNCHANGED`/`REMOVED`) with persisted state in pipeline
- incremental pipeline mode (`--incremental-only`) to evaluate only changed jobs
- enhanced language-friction detection (EN/JP required vs optional patterns)
- language friction index (`language_friction_index`, 0-100)
- company profiling + reputation signals from `config/companies.yaml` tags
- company reputation aggregation + index (`company_reputation_index`, 0-100)
- engineering environment scoring signals (v1, rule-based from job text)
- run-level trend history and weekly deltas in pipeline output
- trend anomaly alerts (v1) derived from run deltas
- `gonogo schedule` command to generate non-active scheduled-run artifacts (script + cron file)
- embedded Ops UI (`ops-ui`, Jaspr) with left navigation and dedicated screens (`Create Run`, `Runs`, `Company`, `Persona`, `Settings`)
- Ops UI API endpoint to add companies into YAML config (`POST /api/config/companies`)
- Ops UI API endpoint to add personas into YAML config (`POST /api/config/personas`)
- deterministic `DecisionEngineV1` with explainable output
- baseline tests for decision rules, raw parsing, and report writing
- regression fixtures for decision outcomes (`src/test/resources/fixtures/decision-regression/cases.yaml`)

Not implemented yet:

- richer signal coverage and calibration
- external notification adapters (email/Slack/webhook)

## Project Structure

```text
config/
├── companies.yaml
├── personas.yaml
└── blacklist.yaml

examples/
├── raw-job-text.example.txt
└── job-input.example.yaml

src/main/java/com/pmfb/gonogo/engine
├── Main.java
├── GoNoGoCommand.java
├── company/
├── job/
├── signal/
├── decision/
└── report/
```

Configuration templates and field definitions:

- `config/README.md`
- `config/companies.example.yaml`
- `config/personas.example.yaml`

## Getting Started

Prerequisites:

- Java 25 installed

Show CLI help:

```bash
./gradlew run --args="--help"
```

## Quick Start Commands

Validate configuration:

```bash
./gradlew run --args="config validate"
```

Quick smart evaluation (auto-detects URL, raw text file, job YAML, or inline text):

```bash
./gradlew run --args="check https://www.fastretailing.com/careers/en/job-description/?id=1588"
```

Run everything in one command (recommended default):

```bash
./gradlew run --args="run"
```

Equivalent explicit command:

```bash
./gradlew run --args="pipeline run-all"
```

What `pipeline run-all` does by default:

- fetches all companies from `config/companies.yaml`
- evaluates all personas from `config/personas.yaml`
- writes per-persona batch reports into `output/`
- writes per-persona weekly reports into `output/weekly-<persona>.md`

Run all with customization examples:

```bash
./gradlew run --args="pipeline run-all --company-ids mercari,moneyforward --personas product_expat_engineer"
```

```bash
./gradlew run --args="pipeline run-all --fetch-web-max-jobs-per-company 20 --fetch-web-request-delay-millis 1500"
```

```bash
./gradlew run --args="pipeline run-all --skip-fetch-web"
```

Run one persona pipeline (still defaults to all companies when fetch stage runs):

```bash
./gradlew run --args="pipeline run --persona product_expat_engineer --fetch-web-first"
```

Operations UI (MVP, in this repo):

```bash
cd ops-ui
dart pub get
jaspr serve
```

Default URL: `http://localhost:8791`

Current Ops UI capabilities:

- Create runs with explicit pipeline parameters
- View run history and run details (logs, command, status)
- Add company entries to `config/companies.yaml` from UI form
- Add persona entries to `config/personas.yaml` from UI form
- Local UI settings (poll interval and auto-refresh)

## Command Reference

`config validate`

- Validates `config/companies.yaml`, `config/personas.yaml`, `config/blacklist.yaml`.

`fetch`

- Converts one raw text file into one normalized job YAML.

`fetch-web`

- Fetches career pages and generates raw text files.
- Default behavior: all companies if `--company-ids` is omitted.

`evaluate`

- Evaluates one job YAML for one persona.

`evaluate-input`

- Evaluates directly from `--job-url` or from raw text (`--raw-text-file` / `--raw-text`).
- Useful for quick checks without generating intermediate YAML files.

`check` (aliases: `quick-check`, `qc`)

- Short smart wrapper with defaults for daily usage.
- Default persona is `product_expat_engineer`.
- Auto mode detection:
  - `http/https` input -> `evaluate-input --job-url`
  - existing `.yaml/.yml` file -> `evaluate --job-file`
  - existing non-YAML file -> `evaluate-input --raw-text-file`
  - otherwise -> `evaluate-input --raw-text`
- Optional `--mode` override: `auto`, `url`, `raw-text`, `raw-file`, `job-yaml`.

`evaluate-batch`

- Evaluates all YAML files from an input directory for one persona.

`weekly-digest`

- Builds one markdown digest from one batch JSON file.

`pipeline run`

- Full flow for one persona: optional fetch, normalize, evaluate, batch report, weekly digest.
- Default persona: `product_expat_engineer`.

`pipeline run-all` (aliases: `pipeline all`, `pipeline full`)

- Full flow for all personas and all companies by default.
- Best command for regular end-to-end runs.

`run`

- Root-level shortcut for `pipeline run-all`.
- Best ultra-short command for daily execution.

`schedule`

- Generates script + cron example files (not activated automatically).
- Default generated run command is `pipeline run-all`.

Generate weekly digest from batch JSON:

```bash
./gradlew run --args="weekly-digest --input-json output/batch-evaluation-product_expat_engineer.json --output-file output/weekly.md"
```

Generate scheduled-run artifacts (script + cron entry, not activated automatically):

```bash
./gradlew run --args="schedule"
```

Strict mode for one persona (fail if normalization produced warnings like `TBD` salary):

```bash
./gradlew run --args="pipeline run --persona product_expat_engineer --fetch-web-first --fail-on-warnings"
```

Quick evaluate from a direct job URL:

```bash
./gradlew run --args="evaluate-input --persona product_expat_engineer --job-url https://www.fastretailing.com/careers/en/job-description/?id=1588"
```

Same quick evaluation using the short smart command:

```bash
./gradlew run --args="check https://www.fastretailing.com/careers/en/job-description/?id=1588"
```

Quick evaluate from raw text file:

```bash
./gradlew run --args="evaluate-input --persona product_expat_engineer --raw-text-file examples/raw-job-text.example.txt"
```

Build:

```bash
./gradlew build
```

Notes:

- The first Gradle run downloads the configured Gradle distribution.
- This repository uses the Gradle wrapper, so a local Gradle install is not required.
- You can override the Java toolchain version with `-PgonogoJavaVersion=<major>` (example: `-PgonogoJavaVersion=21`).

## Job Input YAML

Required fields for `--job-file`:

- `company_name`
- `title`
- `location`
- `salary_range`
- `remote_policy`
- `description`

Evaluation output includes:

- `score` on a normalized `0-100` scale
- `raw_score` (internal weighted score) plus its range, e.g. `raw_score: 6 (range -17..16)`
- `language_friction_index` on a normalized `0-100` scale
- `company_reputation_index` on a normalized `0-100` scale
- explicit `positive_signals`, `risk_signals`, and `reasoning`

Batch evaluation output includes:

- markdown report with summary, results, change status (`NEW`/`UPDATED`/`UNCHANGED`), removed jobs, and parsing errors
- json report with structured fields for automation/integration, including `change_status`, `removed_items`, `language_friction_index`, and `company_reputation_index`

Weekly digest output includes:

- high-signal markdown summary grouped by `GO`, `GO_WITH_CAUTION`, `NO_GO`
- aggregated risk and hard-reject patterns across the batch

Regression fixtures:

- decision fixtures live in `src/test/resources/fixtures/decision-regression/cases.yaml`
- run only fixture regression tests with:
  `./gradlew test --tests com.pmfb.gonogo.engine.decision.DecisionEngineV1RegressionFixturesTest`

## Raw Text Normalization (`fetch`)

`gonogo fetch` extracts `job-file` fields from raw text using deterministic rules:

- labeled fields (`Company:`, `Title:`, `Location:`, `Salary:`)
- currency pattern fallback for salary ranges
- keyword-based remote policy inference (`Remote`, `Hybrid`, `Onsite-only`)
- optional overrides: `--company-name`, `--title`

If a field cannot be extracted, the command uses explicit fallback values and prints warnings.

## Career Page Fetching (`fetch-web`)

`gonogo fetch-web` reads `config/companies.yaml`, downloads selected career pages, and extracts job-like snippets into raw text files suitable for pipeline input.

Company URL fields:

- `career_url`: source used by `fetch-web` for job extraction.
- `corporate_url`: company homepage/context source used for culture/reputation enrichment.

Default output shape:

- `output/raw/<company-id>/<index>-<slug>.txt`

Pipeline integration:

- `pipeline run --fetch-web-first` executes a fetch stage into `--raw-input-dir` before normalization/evaluation.
- Recursive raw-file discovery is enabled automatically when `--fetch-web-first` is used.
- `pipeline run --company-ids ...` is a convenience alias that enables fetch stage automatically.
- If no job candidates are found on the first page, fetch-web discovers likely job-board links and retries extraction.
- Extractor includes anti-noise filters to avoid corporate/non-vacancy pages (for example sustainability/news/workplace sections).
- Corporate/workplace/benefits pages are captured into a separate company-context stream (not as job postings).
- Company context extraction runs independently from job extraction, so context can still be generated even when no vacancies are detected.
- Convenience defaults in `pipeline run`:
  - `--persona=product_expat_engineer`
  - `--raw-input-dir=output/raw`
  - `--fetch-web-max-jobs-per-company=5`
- Convenience defaults in `pipeline run-all`:
  - all personas from `config/personas.yaml`
  - all companies from `config/companies.yaml`
  - `--fetch-web-max-jobs-per-company=12`
- Reliability flags are available in both commands:
  `--retries`, `--backoff-millis`, `--request-delay-millis`, `--cache-dir`, `--cache-ttl-minutes`, `--disable-cache`
  and pipeline-prefixed variants like `--fetch-web-retries`.
- By default, fetch-web enforces a host-level politeness delay (`--request-delay-millis=1200`).
- For pipeline fetch stage, the equivalent flag is `--fetch-web-request-delay-millis`.
- Robots policy for remote sites is configurable via `--robots-mode` / `--fetch-web-robots-mode`:
  `strict` (default), `warn`, `off`.
- On fetch failure, stale cache is used as fallback when available.
- Company context files are generated in `output/company-context` as deduplicated YAML (`<company-id>.yaml`)
  (or `--context-output-dir` / `--company-context-dir`).
- Job change detection is enabled by default in `pipeline run` and persists state in:
  `output/job-change-state-<persona>.yaml` (or `--change-state-file`).
- Disable change detection with `--disable-change-detection`.
- Change fingerprints ignore volatile fetch metadata like `Fetched At:` to avoid false `UPDATED` results.
- Use `--incremental-only` to evaluate only `NEW`/`UPDATED` jobs while still reporting `UNCHANGED`/`REMOVED` counts.
- Trend history is enabled by default in `pipeline run` and persists run snapshots in:
  `output/trend-history-<persona>.yaml` (or `--trend-history-file`).
- Use `--trend-history-max-runs` to cap retained snapshots and `--disable-trend-history` to disable.
- Trend alerts are enabled by default when trend history is active; disable with `--disable-trend-alerts`.
- Alert dispatch is sink-based and integration-agnostic via `--alert-sinks` (`none`, `stdout`, `json-file`).

## Company Profiling (v1)

`config/companies.yaml` now supports optional intelligence tags per company:

- `profile_tags`: positive company context hints
- `risk_tags`: company-level risk hints

Supported `profile_tags`:

- `expat_friendly`
- `engineering_brand`
- `strong_wlb`
- `stable_public`
- `product_leader`
- `reputation_strong`

Supported `risk_tags`:

- `language_friction_high`
- `overtime_risk`
- `reputation_risk`
- `layoff_risk`

These tags feed deterministic decision signals (for example `english_environment`, `engineering_culture`, `company_reputation_positive`, `company_reputation_risk`) and appear in explainable output.

`DecisionEngineV1` now also consumes company context from:

- config (`notes`, `profile_tags`, `risk_tags`, `corporate_url` hints)
- fetched company-context files (workplace/benefits/culture pages)

as additional deterministic input for language/reputation interpretation.

## robots.txt (UI deployment)

The repository includes a conservative `robots.txt` at the project root:

- `User-agent: *`
- `Disallow: /`

This is a privacy-safe default for report UIs so search engines do not index generated job evaluations.

## Engineering Environment Signals (v1)

`DecisionEngineV1` now detects engineering environment quality/risk from job description text:

- positive signal: `engineering_environment`
- risk signal: `engineering_environment_risk`

Positive indicators include ownership clarity, blameless incident practices, runbook culture, and sustainable on-call terms.

Risk indicators include `24/7` or unbounded on-call, frequent incidents/firefighting, and legacy pressure language.

## Reputation Aggregation (v1)

`DecisionEngineV1` now computes a company reputation aggregate index:

- `company_reputation_index` (0-100, higher is stronger reputation confidence)

Inputs are deterministic and configuration-driven:

- company profile/risk tags (`reputation_strong`, `reputation_risk`, `layoff_risk`, `stable_public`)
- job-text reputation indicators (positive and risk evidence)

Decision signals derived from the index:

- positive: `company_reputation_positive`, `company_reputation_positive_strong`
- risk: `company_reputation_risk`, `company_reputation_risk_high`

## Run Trend History (v1)

`pipeline run` appends a run snapshot and compares against the previous run:

- writes persistent history state (`trend-history-<persona>.yaml`)
- appends `## Trend vs Previous Run` to `weekly.md`
- includes global deltas (`go`, `go_with_caution`, `no_go`, averages)
- includes top company movers by average score delta

## Trend Alerts (v1)

When a previous run exists, `pipeline run` appends a `## Trend Alerts` section to `weekly.md` with deterministic alerts such as:

- `NO_GO` jumps
- `GO` drops
- average score drops
- language friction spikes
- reputation index drops
- company-level degradations (score/no-go rate)

Alert delivery is intentionally integration-agnostic in this phase:

- `none`: disable dispatch
- `stdout`: print alerts in pipeline logs
- `json-file`: emit machine-readable alert payload

This keeps integration points ready for future adapters (Slack/email/webhook) without coupling current logic.

## Scheduled Runs (v1)

`gonogo schedule` generates:

- a runnable shell script (default: `scripts/run-pipeline-scheduled.sh`)
- a cron example entry file (default: `scripts/cron-pipeline.example`)

Important:

- It does not activate cron automatically.
- Activation is manual when you decide: `crontab scripts/cron-pipeline.example`.

## Next MVP Steps

1. Calibrate rule weights/thresholds against fixture outcomes and add drift checks.
2. Improve extraction coverage for dynamic/JS-heavy career pages.
3. Add delivery channels for alerts (email/Slack/Webhook).
