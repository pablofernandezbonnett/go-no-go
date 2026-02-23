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
- no database and no UI (as planned)

No database.
No UI.

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

## Phase 6 — Productization (optional)

- API
- simple web UI
- expat community beta
