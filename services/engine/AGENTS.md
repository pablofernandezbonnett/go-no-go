# Go/No-Go Engine — Agent Guidelines

This document defines how AI coding agents must behave when contributing to this project.

---

## Core principle

This is NOT a scraping project.
This is a decision engine.

Intelligence > volume.

---

## Development priorities

1. Decision logic
2. Signal extraction
3. Company/job context
4. Reliability
5. UI last

---

## Rules

General implementation rule:

- Keep classes and services small and focused.
- Split them before they accumulate multiple responsibilities or become hard to scan.

### 1. Do NOT optimize for scraping scale

We track selected companies.
Not the entire internet.

---

### 2. Avoid premature Spring Boot usage

Start CLI-first.

Spring only if:

- API needed
- persistence required
- multi-user scenario

---

### 3. Explainability required

Every decision must be explainable.

No black-box scoring.

Output must include:

- positive signals
- risk signals
- reasoning

---

### 4. Human context over tech hype

Do NOT assume:

- tech blog = good company
- OSS activity = good environment
- FAANG = good opportunity

Focus on:

- work-life
- stability
- language friction
- ownership

---

### 5. Persona-aware design

- Keep decision behavior persona-aware and configuration-driven.
- Do not hardcode candidate-specific or persona-specific policy directly into AGENTS rules.
- Product behavior, default personas, and decision-policy expectations belong in `ENGINE.md` and runtime config docs.

---

### 6. Hard filters

- Hard-filter logic must stay explicit and explainable.
- Model hard filters through engine policy and config, not through hidden heuristics.
- When hard-filter behavior changes, update `ENGINE.md` and the relevant runtime docs.

---

### 7. Code quality

- deterministic outputs
- reproducible runs
- configuration-driven behavior
- simple modules first
- centralize operational string literals as named constants (for example sink ids/status labels such as `none`, `stdout`, `json-file`, `NEW`, `UPDATED`, `UNCHANGED`)

Avoid:

- overengineering
- microservices
- unnecessary infra

---

## Contribution style

Prefer:

- small modules
- explicit logic
- clear naming
- rule-based systems

Avoid:

- heavy ML early
- complex abstractions
- magic heuristics

---

## Code Contracts (Java)

- Do NOT throw `RuntimeException` or `Exception` generically. Use typed exception classes
  in the `exception/` package.
- No static singletons — pass dependencies via constructor.
- Before adding a utility class, verify whether picocli / jsoup / snakeyaml already covers
  the needed functionality.

## Repository Stack

- Java 21
- Gradle
- Picocli

## Run/Test Commands

- Run engine: `./gradlew run --args="run"`
- Run tests: `./gradlew test`

## Verification Guardrails

- Verify runtime config files, candidate profiles, and output artifact contracts before changing command behavior or report formats.
- Treat generated output structure under `output/` as a public contract for downstream consumers unless an explicit migration is documented.

## Source of Truth

- `ENGINE.md` for engine behavior, personas, and decision-policy expectations.
- `build.gradle.kts` and `settings.gradle.kts` for stack and dependency choices.
- `config/*.yaml` for runtime config, personas, companies, and decision signals.
- `src/main/java/com/pmfb/gonogo/engine/**` for command flow, config loading, decision logic, and output contracts.
- `README.md` and `ARCHITECTURE.md` for setup, execution flow, and boundary decisions.

## Preferred Skills

- Use `jvm-service-guardrails` for Java, Gradle, Picocli, config-backed behavior, and output-contract changes.

## Preferred MCPs

- Use Context7 for Java, Picocli, Gradle, and library API verification.

## Engineering Heuristics

- DRY: prefer one source of truth for stable logic, contracts, and constants. Extract shared behavior when repetition is real and the same fix would otherwise need to be repeated.
- YAGNI: do not add speculative features, extension points, flags, or abstractions for hypothetical future needs.
- KISS: choose the simplest implementation that is easy to explain, test, and change.
- Simple is not easy: invest in small focused functions and clear structure instead of the fastest large-function shortcut.
- Accept small local duplication temporarily when the right abstraction is not yet clear. Extract only when it improves readability and maintainability.

## NEVER Rules

- NEVER turn a CLI-local feature into Spring or persistence work before there is a real API or multi-user need.
- NEVER hide scoring or rejection behavior behind undocumented heuristics.
- NEVER rename output contract fields, sink ids, or status labels casually.

## Incorrect vs Correct

- Incorrect: add Spring Boot controllers or persistence layers for a feature that is still CLI-local.
- Correct: keep the behavior in the CLI and service/config layers until there is a real API or multi-user need.

- Incorrect: hide a reject or score change behind an untracked heuristic with no reasoning output.
- Correct: model it as an explicit signal, hard filter, or config-backed rule and keep the explanation visible in the output.

- Incorrect: rename output fields, sink ids, or status labels casually because the engine still compiles.
- Correct: treat engine artifacts as downstream contracts and update tests, docs, and consumers together.

## Definition of Done

- `./gradlew test` passes or failures are explained.
- Engine docs stay aligned when commands, config, or output contracts change.

## Docs Update Matrix

- Update `README.md` for setup, CLI usage, runtime config, or output flow changes.
- Update `AGENTS.md` when engine operating rules, output contracts, or verification rules change.

---

## Long-term role of AI

- signal detection
- interpretation assistance
- explanation generation

NOT:

- autonomous scraping
- uncontrolled model decisions
