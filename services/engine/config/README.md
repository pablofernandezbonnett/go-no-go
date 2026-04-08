# Engine Configuration

These files drive the engine at runtime. Keep tracked examples in Git, and keep real personal data local.

## Files

- `companies.yaml`: curated company catalog and context tags used during fetch and evaluation.
- `personas.yaml`: scoring priorities, hard rejects, and optional salary floor.
- `blacklist.yaml`: company or source exclusions.
- `runtime.yaml`: operational fetch and evaluation defaults used when CLI flags omit them.
- `decision-signals.yaml`: editable keyword and threshold sets for selected rule families.
- `candidate-profiles/`: optional local candidate-aware inputs.

Template files live beside the active files and act as schema references:

- `companies.example.yaml`
- `personas.example.yaml`
- `runtime.example.yaml`
- `decision-signals.example.yaml`
- `candidate-profiles/candidate-profile.example.yaml`

## Schema Highlights

### `companies.yaml`

- Top-level key: `companies`
- Required fields: `id`, `name`, `career_url`
- Common optional fields: `corporate_url`, `type_hint`, `region`, `notes`
- `profile_tags`: `expat_friendly`, `engineering_brand`, `strong_wlb`, `stable_public`, `product_leader`, `reputation_strong`
- `risk_tags`: `language_friction_high`, `overtime_risk`, `reputation_risk`, `layoff_risk`

### `personas.yaml`

- Top-level key: `personas`
- Core fields: `id`, `description`, `priorities`, `hard_no`, `acceptable_if`
- Optional field: `minimum_salary_yen`
- Common `hard_no` values: `onsite_only`, `consulting_company`, `salary_missing`, `early_stage_startup`, `japanese_only_environment`, `workload_overload`, `forced_relocation`

Notes:

- `hard_no` defines what forces `NO_GO` for that persona.
- If `onsite_only` is omitted from `hard_no`, onsite-only roles still emit `onsite_bias` and are penalized without being auto-rejected.
- Criteria outside `hard_no` can still appear as risk signals and reduce score.
- `minimum_salary_yen` is a conservative threshold, not a guess about the posted maximum.
- `salary_missing` means there is no usable range. `TBD`, negotiable-only text, blank salary, or a single number without a range all count as missing.

### `candidate-profiles/`

- Candidate profiles are optional runtime inputs.
- Commands such as `check`, `evaluate`, `evaluate-input`, `evaluate-batch`, `pipeline run`, and `pipeline run-all` accept `--candidate-profile`.
- `*.example.yaml` files are ignored by the runtime loader.
- Real profiles should stay local and untracked.

Current candidate-aware signals:

- positive: `candidate_stack_fit`, `candidate_domain_fit`, `candidate_seniority_fit`
- risk: `candidate_stack_gap`, `candidate_domain_gap`, `candidate_seniority_mismatch`

Optional narrative fields such as `education`, `target_roles`, and `differentiators` support the human-readable explanation layer.

### `runtime.yaml`

- `fetch_web`: timeout, user agent, retries, backoff, delay, concurrency, robots mode, and cache defaults
- `evaluation`: evaluation concurrency defaults

Priority order:

1. explicit CLI flag
2. `config/runtime.yaml`
3. built-in fallback

### `decision-signals.yaml`

Current scopes:

- `language`
- `work_life_balance`
- `mobility`
- `job_post_quality`

Use this file to tune market wording and thresholds without recompiling the engine. Keep the rule logic itself in Java.

## Validate

After editing configuration, run:

```bash
./gradlew run --args="config validate"
```
