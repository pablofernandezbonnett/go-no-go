# Candidate Profiles

This directory stores candidate profiles used by the runtime engine for candidate-aware evaluation.

Current status:

- These files are loaded by the runtime engine.
- `config validate` validates them together with the rest of the config.
- If exactly one profile exists, runtime commands auto-select it unless `--candidate-profile none` is passed.
- `*.example.yaml` files in this directory are ignored by the runtime loader.
- Real candidate profiles should stay local and untracked.

Current runtime use:

- stack-fit scoring
- seniority-fit scoring
- domain-fit scoring
- recommendation explanations grounded in actual profile evidence

Still planned:

- candidate-aware salary-fit scoring using candidate-specific expectations

Conventions:

- keep a tracked template file for reference
- keep real candidate profiles local and untracked
- English comments/documentation
- keep production-proven skills separate from learning goals
- keep gaps explicit
- keep salary and role targeting assumptions current

Suggested workflow:

1. Copy `candidate-profile.example.yaml` to `config/candidate-profiles/<your_id>.yaml`
2. Fill it with your real profile data
3. Keep that real file untracked
