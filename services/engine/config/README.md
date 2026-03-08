# Configuration Guide

This folder contains runtime configuration files used by the engine.

Active files:
- `companies.yaml`
- `personas.yaml`
- `blacklist.yaml`

Templates/examples:
- `companies.example.yaml`
- `personas.example.yaml`

Use the example files as schema guides when adding new entries.

## companies.yaml

Top-level key:
- `companies`: list of company objects

Company fields:
- `id`: lowercase snake_case identifier (`^[a-z0-9_]+$`)
- `name`: display name used in reports
- `career_url`: main source for job extraction (`http/https`)
- `corporate_url`: company site for context/reputation signals (`http/https`)
- `type_hint`: free-form type hint (example: `saas_product`, `product_enterprise`)
- `region`: free-form region hint (example: `japan`)
- `notes`: optional context note used by the decision engine
- `profile_tags`: optional positive tags
- `risk_tags`: optional risk tags

Allowed `profile_tags`:
- `expat_friendly`
- `engineering_brand`
- `strong_wlb`
- `stable_public`
- `product_leader`
- `reputation_strong`

Allowed `risk_tags`:
- `language_friction_high`
- `overtime_risk`
- `reputation_risk`
- `layoff_risk`

## personas.yaml

Top-level key:
- `personas`: list of persona objects

Persona fields:
- `id`: lowercase snake_case identifier (`^[a-z0-9_]+$`)
- `description`: short human-readable profile description
- `priorities`: ordered preference list (used for weighted scoring)
- `hard_no`: hard-reject rules for this persona
- `acceptable_if`: softer constraints tolerated by this persona

`hard_no` baseline (required by validator):
- `consulting_company`
- `onsite_only`
- `salary_missing`

Optional `hard_no` (supported by decision engine):
- `early_stage_startup`
- `japanese_only_environment`
- `workload_overload`
- `forced_relocation`

Notes:
- `hard_no` controls which criteria force `NO_GO`.
- Criteria not present in `hard_no` can still appear as risk signals and reduce score.
- Different personas can classify the same job differently by design.

## Validation

After editing configuration, run:

```bash
./gradlew run --args="config validate"
```
