# Candidate Profiles

This directory stores candidate profiles used by the runtime engine for candidate-aware evaluation.

Current status:

- These files are loaded by the runtime engine.
- `config validate` validates them together with the rest of the config.
- If exactly one profile exists, runtime commands auto-select it unless `--candidate-profile none` is passed.

Current runtime use:

- stack-fit scoring
- seniority-fit scoring
- domain-fit scoring
- recommendation explanations grounded in actual profile evidence

Still planned:

- candidate-aware salary-fit scoring using candidate-specific expectations

Conventions:

- one YAML file per candidate
- English comments/documentation
- keep production-proven skills separate from learning goals
- keep gaps explicit
- keep salary and role targeting assumptions current
