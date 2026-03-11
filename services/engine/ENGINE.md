# Go/No-Go Engine — Strategic Foundation

## Why this exists

Searching for IT product jobs in Japan as an expat is inefficient and frustrating.

Problems:

- Fragmented job sources
- Consulting disguised as product roles
- Opaque salary structures
- "English OK" that still requires Japanese
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

- The engine may support alternate personas where consulting is tolerated but penalized rather than auto-rejected.
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

---

## What Defines a Deceptive Opportunity

- "Fast-paced environment"
- Overtime included in salary
- No salary range
- Vague role definition
- Consulting language
- Onsite-only

---

## Engine Philosophy

Not all good jobs are good for everyone.

The system evaluates:

- Job signals
- Company context
- Language friction
- Interview ROI

And provides:

- GO
- NO-GO
- GO with caution

With explanation.

---

## Decision Hierarchy

1. Hard filters (reject immediately)
2. Risk signals
3. Positive signals
4. Persona alignment
5. Verdict generation

Current hard-filter baseline:

- onsite-only
- missing or non-transparent salary
- abusive overtime signals

Persona-configurable hard filters may also include:

- consulting / dispatch
- early-stage startup
- Japanese-only environment
- workload overload
- forced relocation

Salary seriousness rule:

- A role is only salary-transparent if it provides an explicit salary range.
- `TBD`, negotiable-only wording, blank salary, or a single salary number without a range are treated as non-transparent.

---

## Architecture Principles

- CLI-first
- Deterministic
- Explainable
- Config-driven
- Intelligence > scraping
- No overengineering

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
