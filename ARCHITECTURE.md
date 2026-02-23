# Go/No-Go Engine Architecture

## Philosophy

Decision intelligence first.
Fetch only selected targets.
UI supports operations, but decision quality remains the core.

---

## Runtime Topology

1. CLI engine (`src/main/java/...`)
2. YAML config/state (`config/`, output state files)
3. Ops UI server/client (`ops-ui`, Jaspr SSR)

The Ops UI is a companion layer for operating the CLI pipeline. It is not a replacement for the CLI.

---

## Core Pipeline

1. Company registry loading (`config/companies.yaml`)
2. Career page fetch (polite, constrained, selected companies)
3. Job posting extraction + normalization
4. Signal extraction (language, salary, remote, consulting risk, culture/environment signals)
5. Persona-aware decision engine (hard filters + weighted scoring)
6. Reporting outputs (batch JSON/Markdown, weekly digest, trend history, alerts)

---

## Modules

### 1. Configuration Layer

Sources:
- `config/companies.yaml`
- `config/personas.yaml`
- `config/blacklist.yaml`

Properties:
- deterministic loading
- validation before execution
- configuration-driven behavior

### 2. Fetch + Extraction Layer

Responsibilities:
- fetch selected company pages
- extract likely vacancies
- separate non-job context (culture/benefits/workplace) for company context
- output normalized job artifacts

### 3. Decision Layer

Responsibilities:
- apply hard filters first
- compute explainable scoring
- produce explicit positive/risk reasons

Output model:
- normalized score (`0-100`)
- raw score and range
- language friction index (`0-100`)
- company reputation index (`0-100`)
- verdict and reasoning

### 4. Reporting Layer

Responsibilities:
- batch evaluation reports
- weekly digest generation
- run-to-run trend deltas and anomaly alerts

### 5. Operations UI Layer (`ops-ui`)

Backend endpoints:
- `GET /api/health`
- `GET /api/config`
- `POST /api/config/companies`
- `GET /api/runs`
- `GET /api/runs/:runId`
- `POST /api/runs`

Screens:
- `Create Run`
- `Runs` (list + details)
- `Config` (company onboarding form)
- `Settings` (UI runtime options)

Default port:
- `8791` (override with `PORT` or `OPS_UI_PORT`)

---

## Data Contract (current)

### Company

- `id`
- `name`
- `career_url`
- `corporate_url`
- `type_hint`
- `region`
- `notes`
- `profile_tags`
- `risk_tags`

### Job

- `id`
- `company_id`
- `title`
- `location`
- `salary_range`
- `remote_policy`
- `description`
- extracted signal fields

### Evaluation

- `job_id`
- `score`
- `raw_score` (+ range)
- `verdict`
- `reasoning`
- `positive_signals`
- `risk_signals`

---

## Deferred by Design

- database persistence
- multi-user auth model
- Spring Boot API as main runtime
- external notification integrations (Slack/Email) beyond agnostic sink contracts
