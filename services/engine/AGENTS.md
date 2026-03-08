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

Initial persona:

"Product Engineer Expat"

Rules must align with:

- English environment
- hybrid preference
- product companies
- no consulting
- no early-stage startups

---

### 6. Hard filters

Reject automatically if:

- consulting / dispatch detected
- onsite-only
- salary missing
- abusive overtime signals

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

---

## Long-term role of AI

- signal detection
- interpretation assistance
- explanation generation

NOT:

- autonomous scraping
- uncontrolled model decisions
