# Configuration Guide

This folder contains runtime configuration files used by the engine.

Active files:
- `companies.yaml`
- `personas.yaml`
- `blacklist.yaml`
- `runtime.yaml` - operational runtime defaults for fetch/pipeline commands
- `decision-signals.yaml` - keyword and threshold config for selected decision signals
- `candidate-profiles/` - optional runtime candidate profiles used for candidate-aware evaluation

Templates/examples:
- `companies.example.yaml`
- `personas.example.yaml`
- `runtime.example.yaml`
- `decision-signals.example.yaml`
- `candidate-profiles/candidate-profile.example.yaml`
- `candidate-profiles/README.md`

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
- `minimum_salary_yen`: optional salary floor used as a risk threshold (0/omitted = disabled)

`hard_no` baseline (required by validator):
- `onsite_only`

Optional `hard_no` (supported by decision engine):
- `consulting_company`
- `salary_missing`
- `early_stage_startup`
- `japanese_only_environment`
- `workload_overload`
- `forced_relocation`

Notes:
- `hard_no` controls which criteria force `NO_GO`.
- Criteria not present in `hard_no` can still appear as risk signals and reduce score.
- Different personas can classify the same job differently by design.
- `minimum_salary_yen` uses a conservative benchmark for intermediary/consulting ranges instead of assuming the posted maximum is realistic.
- `salary_missing` means no usable salary range was provided. `TBD`, negotiable-only, blank salary, or a single salary number without a range all fail this check.
- By default, salary opacity is a strong negative signal rather than an automatic reject. Add `salary_missing` to `hard_no` only for personas that truly require salary transparency up front.

## candidate-profiles/

Runtime behavior:
- Candidate profiles are optional.
- Commands `check`, `evaluate`, `evaluate-input`, `evaluate-batch`, `pipeline run`, and `pipeline run-all` accept `--candidate-profile`.
- When exactly one profile exists and no explicit `--candidate-profile` is provided, the engine auto-selects it.
- Use `--candidate-profile none` to disable candidate-aware scoring explicitly.
- `*.example.yaml` files under `candidate-profiles/` are ignored by the runtime loader.
- Real candidate profiles are intended to stay local and untracked.

Current candidate-aware signals:
- positive: `candidate_stack_fit`, `candidate_domain_fit`, `candidate_seniority_fit`
- risk: `candidate_stack_gap`, `candidate_domain_gap`, `candidate_seniority_mismatch`

Optional narrative profile fields now used by the engine:
- `education`
- `target_roles`
- `differentiators`

These do not affect the hard scoring directly. They support the deterministic human-reading layer by surfacing adjacent-fit and transferability context.

## runtime.yaml

Top-level key:
- `fetch_web`: operational defaults used when CLI flags are not provided explicitly
- `evaluation`: operational defaults used by batch/pipeline evaluation stages when CLI flags are not provided explicitly

Supported `fetch_web` fields:
- `timeout_seconds`
- `user_agent`
- `retries`
- `backoff_millis`
- `request_delay_millis`
- `max_concurrency`
- `max_concurrency_per_host`
- `robots_mode`
- `cache_ttl_minutes`

Supported `evaluation` fields:
- `max_concurrency`

Priority order:
- explicit CLI flag
- `config/runtime.yaml`
- built-in fallback

This keeps fetch and pipeline behavior configurable at runtime without turning every operational default into a compile-time constant.

## decision-signals.yaml

This file externalizes selected keyword lists and thresholds while keeping the scoring logic in Java.

Current scopes:
- `language`
- `work_life_balance`
- `mobility`
- `job_post_quality`

Supported `language` fields:
- `required_keywords`
- `friction_soft_keywords`
- `medium_high_friction_keywords`
- `high_friction_keywords`
- `assignment_dependent_keywords`
- `optional_or_exempt_keywords`
- `english_friendly_keywords`
- `english_support_environment_keywords`
- `english_support_max_index`
- `assignment_dependent_base_index`
- `assignment_dependent_min_index`

Supported `work_life_balance` fields:
- `overtime_risk_keywords`
- `holiday_policy_risk_keywords`

Supported `mobility` fields:
- `location_mobility_risk_keywords`

Supported `job_post_quality` fields:
- `generic_marketing_risk_keywords`
- `generic_marketing_risk_hit_min`
- `conditions_section_keywords`
- `vague_conditions_risk_keywords`
- `vague_conditions_risk_hit_min`
- `concrete_conditions_keywords`

Use this file to tune market wording without recompiling the engine. Keep the rule logic itself in Java.

## Validation

After editing configuration, run:

```bash
./gradlew run --args="config validate"
```
