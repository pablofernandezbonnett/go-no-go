# Go/No-Go Engine Roadmap

## Phase 0 — Strategy (completed)

- persona defined
- signals defined
- architecture direction set
- product philosophy documented

---

## Phase 1 — MVP Core (completed)

Goal:
Produce first weekly report.

Tasks:

- [x] config/companies.yaml
- [x] basic CLI
- [x] career page fetcher
- [x] simple parser
- [x] signal detection (v1 rules)
- [x] GO / NO-GO output

Deliverable status:
- first weekly digest pipeline available via CLI
- database intentionally deferred
- UI intentionally deferred at this phase (added later in Phase 6)

---

## Phase 2 — Reliability (completed)

- [x] baseline deduplication in career-page extraction
- [x] real fetch from selected career pages (`fetch-web`)
- [x] retry/backoff/cache hardening
- [x] job change detection
- [x] incremental updates
- [x] better language signal detection

---

## Phase 3 — Intelligence (completed, v1)

- [x] company profiling (v1, config-driven tags)
- [x] reputation signals (v1, config + text aggregation)
- [x] company reputation index (0-100)
- [x] engineering environment scoring (v1, rule-based)
- [x] language friction index (0-100)

---

## Phase 4 — Personalization (in progress, baseline available)

- [x] persona-aware evaluation model (priorities + hard filters)
- [ ] preference tuning workflows (beyond static config)
- [ ] opportunity ranking refinements

---

## Phase 5 — Automation (completed, v1)

- [x] scheduled runs (generated script + cron artifacts, manual activation)
- [x] alerts (v1 run-delta anomaly rules in weekly digest + sink dispatch)
- [x] historical trends (v1 run-level snapshots + weekly deltas)

---

## Phase 6 — Productization (in progress)

- [x] internal Ops UI scaffold in this repo (`ops-ui`, Jaspr)
- [x] run execution from UI (`Create Run` + `Runs`/details)
- [x] company onboarding form (`POST /api/config/companies`)
- [x] persona onboarding form (`POST /api/config/personas`)
- [x] settings screen (runtime UI options)
- [ ] richer report browsing UX inside the same UI module
- [ ] auth/access control (currently public/local)

---

## Phase 7 — Discovery Engine (planned, no AI)

Goal:
Discover candidate companies/job sources without crawling the full internet.

Principles:
- curated source-first strategy (quality over volume)
- domain allowlist + request budgets + polite fetch
- deterministic pre-filter and explainable ranking
- human approval before promotion to tracked companies

Tasks:

- [ ] define discovery source registry (`config/discovery-sources.yaml`)
- [ ] add CLI discovery workflow (`discovery ingest`, `discovery rank`, `discovery promote`)
- [ ] implement metadata-first normalization for candidates (`title`, `location`, `salary`, `remote`, `language`, `domain`)
- [ ] implement deterministic discovery scoring + reasons (persona-fit and risk-first)
- [ ] add candidate dedup/canonicalization by company/domain/url
- [ ] add discovery inbox UI (`approve`, `reject`, `promote`)
- [ ] add promotion audit trail (`who/when/why`, local file-based first)
- [ ] add operational KPIs (promotion rate, reject reasons, no-vacancy ratio)

Deliverable status target:
- weekly discovery run over curated sources
- ranked candidate list with explainable reasons
- one-click promotion path from discovery candidate to `config/companies.yaml`
