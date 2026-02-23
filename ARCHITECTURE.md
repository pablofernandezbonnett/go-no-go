# Go/No-Go Engine Architecture

## Philosophy

Backend intelligence > scraping > UI

The system prioritizes signal extraction and decision logic over volume or interface.

---

# 🧱 MVP Architecture

## Pipeline

1. Company registry
2. Career page fetch
3. Job detection
4. Signal extraction
5. Decision engine
6. Report generation

---

## Modules

### 1. Company Registry

Input:
- config/companies.yaml

Contains:
- career URLs
- company type hints
- region
- notes

---

### 2. Job Fetcher

Responsibilities:
- download career pages
- detect job listings
- normalize structure

---

### 3. Job Parser

Extracts:
- title
- location
- salary
- remote/hybrid
- language signals
- stack keywords

---

### 4. Signal Engine

Converts text into structured signals:

- salary_transparency
- language_friction
- consulting_risk
- product_ownership
- overtime_risk
- engineering_environment

---

### 5. Go/No-Go Engine

Applies rules:

- Hard filters
- Weighted signals
- Persona alignment

Outputs:
- GO
- NO-GO
- GO with caution

+ explanation

---

### 6. Reporter

Generates:

- weekly.md
- jobs.json

---

# 📐 Data Model (initial)

## Company

- id
- name
- career_url
- type_hint
- region
- notes

## Job

- id
- company_id
- title
- location
- salary_range
- language_signals
- remote_policy
- stack_keywords
- raw_text

## Evaluation

- job_id
- score
- verdict
- explanation
- risks
- positive_signals

---

# 🚀 Future Architecture

## Phase 2

- Spring Boot API
- Database persistence
- Daily incremental crawler
- Personalization layer

## Phase 3

- Company intelligence graph
- Layoff detection
- Reputation aggregation
- Community feedback loop

## Phase 4

- SaaS platform
- User personas
- Opportunity alerts
