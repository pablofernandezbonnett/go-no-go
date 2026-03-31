# Candidate Profiles

Local runtime profiles used for candidate-aware evaluation.

## What Belongs Here

- Keep `candidate-profile.example.yaml` tracked as the schema reference.
- Put real profiles in `config/candidate-profiles/<id>.yaml` and keep them untracked.
- `*.example.yaml` files are ignored by the runtime loader.

## Candidate Profile Contract

Files under `config/candidate-profiles/*.yaml` are local runtime profiles used
by the engine.

They are derived operational profiles optimized for:

- stack fit
- domain fit
- seniority fit
- differentiators
- target-role evaluation

They are not the canonical source of truth for the candidate profile.

The canonical candidate profile is maintained privately outside this
repository. When candidate facts change, update the private canonical profile
first and then refresh the local runtime profile as needed.

Never commit real candidate profiles to this repository. Keep only examples and
schema references tracked.

## How the Engine Uses Them

- candidate stack, domain, and seniority fit
- explanation copy backed by fields such as `education`, `target_roles`, and `differentiators`
- auto-selection of the single real profile when exactly one exists

Use `--candidate-profile none` to disable candidate-aware scoring explicitly.

## Workflow

1. Copy `candidate-profile.example.yaml` to `<id>.yaml`.
2. Fill it with real data.
3. Keep the real file local and run `./gradlew run --args="config validate"`.
