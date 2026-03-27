# Candidate Profiles

Local runtime profiles used for candidate-aware evaluation.

## What Belongs Here

- Keep `candidate-profile.example.yaml` tracked as the schema reference.
- Put real profiles in `config/candidate-profiles/<id>.yaml` and keep them untracked.
- `*.example.yaml` files are ignored by the runtime loader.

## How the Engine Uses Them

- candidate stack, domain, and seniority fit
- explanation copy backed by fields such as `education`, `target_roles`, and `differentiators`
- auto-selection of the single real profile when exactly one exists

Use `--candidate-profile none` to disable candidate-aware scoring explicitly.

## Workflow

1. Copy `candidate-profile.example.yaml` to `<id>.yaml`.
2. Fill it with real data.
3. Keep the real file local and run `./gradlew run --args="config validate"`.
