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

## Phase 4 - Personalization (in progress, baseline available)

- [x] persona-aware evaluation model (priorities + hard filters)
- [x] candidate profile reference artifacts stored in repo (`config/candidate-profiles/`)
- [x] candidate profile loader + schema validation
- [x] combined evaluation model: persona preferences + candidate evidence
- [x] candidate-aware fit scoring (stack, seniority, domain)
- [ ] candidate-aware salary fit
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

## Phase 6.5 — Performance And Throughput (next)

Goal:
Reduce end-to-end runtime without making the engine harder to reason about.

Principles:
- keep deterministic outputs
- parallelize at clear boundaries first
- preserve robots.txt and per-host politeness guarantees
- prefer simple Java concurrency primitives over reactive rewrites
- measure first, then optimize the slow path

Execution plan:

### Step 1 — Baseline Timing And Visibility

Purpose:
Understand where time is actually spent before changing concurrency behavior.

Tasks:

- [x] add phase timing to `fetch-web` summary (per company + total)
- [x] add timing to pipeline summary (fetch, normalize, evaluate, report)
- [x] log cache hit/miss counts and retry counts
- [x] document a simple local benchmarking workflow

Expected outcome:
- clear baseline for fetch bottlenecks
- confidence about where parallelism will help most

### Step 2 — Safe Fetch Concurrency Per Company

Purpose:
Speed up `fetch-web` while keeping the current imperative code shape.

Tasks:

- [x] introduce bounded company-level concurrency in `CareerPageFetchService`
- [x] use Java virtual threads for blocking fetch tasks
- [x] keep output ordering deterministic after task completion
- [x] add a small CLI/config surface such as `--max-concurrency`
- [x] keep default conservative rather than “max speed”

Expected outcome:
- meaningful speedup on multi-company runs
- minimal complexity increase

### Step 3 — Host-Aware Politeness Under Concurrency

Purpose:
Make parallel fetch safe for shared hosts and ATS providers.

Tasks:

- [x] replace shared mutable fetch timing maps with concurrency-safe host state
- [x] enforce per-host delay correctly under parallel execution
- [x] add optional `--max-concurrency-per-host`
- [x] keep robots resolution cached per host without duplicate fetches

Expected outcome:
- concurrent fetch without breaking request-delay guarantees
- better behavior on shared ATS domains

### Step 4 — Parallel Evaluation In `pipeline run`

Purpose:
Reduce evaluation time when many raw jobs are already present.

Tasks:

- [x] parallelize job evaluation after raw parsing / change detection
- [x] preserve stable ordering in batch outputs
- [x] keep report writing and final aggregation single-threaded
- [x] add tests for deterministic output ordering

Expected outcome:
- faster batch evaluation with no report-format surprises

### Step 5 — Parallel Persona Runs In `pipeline run-all`

Purpose:
Speed up multi-persona execution only after shared inputs are stable.

Tasks:

- [x] isolate shared intermediate artifacts where needed
- [x] parallelize per-persona evaluation runs after fetch stage is complete
- [x] keep fetch stage single-execution and shared
- [x] add a conservative `--persona-concurrency` option

Expected outcome:
- faster `run-all` for multiple personas
- no collisions in outputs or state files

### Step 6 — Optional HTTP Client Upgrade (only if still needed)

Purpose:
Avoid premature complexity if virtual-thread concurrency already solves the problem.

Tasks:

- [ ] reassess whether `HttpClient.sendAsync(...)` is necessary
- [ ] only consider async fetch if bounded virtual-thread fetch still underperforms
- [ ] avoid reactive framework adoption unless there is clear evidence

Expected outcome:
- no unnecessary rewrite
- higher complexity only if justified by measurements

First implementation target:
- Step 1
- then Step 2 with company-level virtual-thread concurrency

Non-goals for this track:
- no reactive rewrite
- no microservice split
- no speculative infra work

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
