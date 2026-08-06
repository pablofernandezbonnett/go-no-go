# Go/No-Go Engine — Strategic Foundation

## Why this exists

Searching for IT product jobs in Japan as an expat is inefficient and frustrating.

Problems:

- Fragmented job sources
- Consulting disguised as product roles
- Opaque salary structures
- "English OK" that still requires Japanese
- "Japanese ability influences assignment/team placement" even when it is not phrased as a hard requirement
- Excessive overtime culture
- Tokyo-centric bias
- Wasted interview processes

The biggest cost is not job search time.

The biggest cost is:
Wasted interview energy.

---

## Core Mission

Reduce wasted interviews for expat engineers in Japan.

The engine discovers real opportunities and determines whether it is worth entering a hiring process.

---

## What this is NOT

- Not a job board
- Not a mass scraper
- Not a FAANG tracker
- Not an AI hype project

It is a decision support system.

---

## Target Persona (v1 baseline)

Product-oriented expat engineer in Japan who:

- Works in English
- Prefers hybrid
- Values engineering culture
- Wants stable product companies
- Avoids early-stage startups
- Avoids onsite-only roles

Baseline default:

- Consulting / dispatch is undesirable and should be treated as a hard reject for the default product-focused persona.

Persona variation:

- Consulting may be tolerated but penalized rather than auto-rejected, depending on persona config.
- Onsite-only should be treated as a scored risk by default, and only become a hard reject when a persona explicitly includes `onsite_only` in `hard_no`.
- Persona-specific policy belongs in runtime config, not in agent instructions.

---

## Core Problems We Solve

1. Hard to discover good product jobs
2. Hard to know if entering process is worth it

Therefore the system focuses on:

- Opportunity Discovery
- Go/No-Go Decision

---

## What Defines a Good Opportunity (v1)

- English-friendly environment
- Hybrid work
- Clear salary range
- Product ownership
- Transparent benefits
- Interview process that looks experience-driven rather than puzzle-heavy
- Real flextime or low disclosed overtime

---

## What Defines a Deceptive Opportunity

- "Fast-paced environment"
- Overtime included in salary
- No salary range
- Vague role definition
- Consulting language
- Onsite-only
- Algorithm-heavy screening language when interview ROI is likely low

---

## Engine Philosophy

Not all good jobs are good for everyone.

The system evaluates:

- Job signals
- Company context
- Language friction
- Interview ROI
- Candidate transferability and adjacent-fit evidence

And provides:

- GO
- NO-GO
- GO with caution

With explanation and a deterministic human-reading layer.

---

## Decision Hierarchy

1. Hard filters (reject immediately)
2. Risk signals
3. Positive signals
4. Persona alignment
5. Verdict generation

Human-reading layer:

- access fit
- execution fit
- domain fit
- opportunity quality
- interview ROI

These dimensions are derived from scored signals and candidate profile evidence.
They do not replace the verdict; they explain it in more human terms.

Decision-aligned score rule:

- Scores are mapped to verdict bands, rather than to the theoretical range of
  every possible signal: weighted `NO_GO` is `0–49`, `GO_WITH_CAUTION` is
  `50–69`, and `GO` is `70–100`. This keeps adjacent raw scores from jumping
  from a very low display score to a high one when they cross a verdict
  threshold.
- A hard-filter `NO_GO` retains its capped score (at most `20/100`) because
  positive evidence can still be useful context, but it can never override the
  explicit filter.

Current hard-filter baseline:

- abusive overtime signals

Persona-configurable hard filters may also include:

- onsite-only
- consulting / dispatch
- missing or non-transparent salary
- early-stage startup
- Japanese-only environment
- workload overload
- forced relocation

Candidate-aware language hard filter:

- A candidate profile may declare a verified JLPT level (`N5`–`N1`). An explicit
  role requirement that is at least two levels above it is a critical access gap
  and forces `NO_GO`. This is candidate-specific, not a claim that every
  foreign candidate has the same language constraint.

Salary seriousness rule:

- A role is only salary-transparent if it provides an explicit salary range.

Role-scope salary alignment:

- `role_scope_salary_misaligned` is a weighted salary risk, not a market-rate
  estimate. It fires only when the advertised ceiling is at or below ¥6M and
  the post simultaneously requires at least three years of experience,
  responsibility for requirements/architecture/upstream work, and four or more
  distinct technologies. The three explicit conditions keep a broad but junior
  role from being penalized merely for having a low range.

Anonymous recruiter posts:

- `anonymous_employer_risk` also covers an unnamed employer with at least two
  recruiter-style indicators, such as an invitation to connect, a vague
  long-term opportunity, or an unspecified large-scale organization. It is a
  verification risk, not a claim that the opportunity is fraudulent.
- `TBD`, negotiable-only wording, blank salary, or a single salary number without a range are treated as non-transparent.
- Non-transparent salary is a strong negative signal by default and remains available as a persona-level hard filter when needed.

---

## Architecture Principles

- CLI-first
- Deterministic
- Explainable
- Config-driven
- Intelligence > scraping
- No overengineering

Operational expectation:

- Keep market wording data such as keyword lists and simple thresholds in config when practical.
- Keep decision flow, conflict resolution, and scoring logic in Java.

---

## Long-Term Vision

- Language friction index
- Engineering environment scoring
- Company intelligence graph
- Persona customization
- Historical job tracking
- Community layer (optional)

---

## Guiding Constraint

If the tool does not reduce wasted interviews,
it is not doing its job.
