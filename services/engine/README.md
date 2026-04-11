# Go/No-Go Engine

Explainable CLI for screening engineering roles against explicit personas and generating the artifacts consumed by the browser UIs.

This is a decision engine, not a scraping-at-scale project. It stays CLI-first on purpose: rules, config, and output contracts live here first, and the UIs remain thin companions around those contracts.

Stack: Java 21, Gradle, and Picocli.

## What It Does

The engine fetches or accepts raw job input, normalizes it into a stable job shape, evaluates it against one or more personas, and writes reports that are easy to inspect later. Every recommendation is meant to stay deterministic and explainable.

## Initial Scope

- Track selected companies instead of the whole internet.
- Keep scoring rule-based and explainable.
- Support persona-aware GO / NO_GO decisions.
- Use local candidate profiles for candidate-aware fit when needed.
- Generate artifacts that `ops-ui` and `reports-ui` can consume without duplicating engine logic.

## Key Areas

- `config/`: tracked runtime defaults, company catalog, personas, and signal dictionaries.
- `config/candidate-profiles/`: schema and local runtime inputs for candidate-aware evaluation.
- `examples/`: sample raw text and input fixtures.
- `src/main/java/com/pmfb/gonogo/engine/job`: raw input shaping and normalized job data.
- `src/main/java/com/pmfb/gonogo/engine/signal`: explainable positive and risk signal extraction.
- `src/main/java/com/pmfb/gonogo/engine/decision`: verdict, score, and reasoning assembly.
- `src/main/java/com/pmfb/gonogo/engine/report`: batch, weekly, trend, and ad-hoc output writing.
- `ops-ui/`: engine-owned browser companion for operational workflows.

## Candidate Profile Privacy Contract

This project may use local candidate profiles for candidate-aware evaluation.

Real candidate profiles are private local runtime inputs and must stay outside
version control.

They are operational projections used for offer evaluation and scoring, not the
canonical candidate narrative source.

If candidate-aware evaluation is needed, keep the runtime profile local and
derive it from the private canonical candidate profile maintained outside this
repository.

## Common Workflows

- `./gradlew run --args="config validate"`: validate tracked config files.
- `./gradlew run --args="check examples/raw-job-text.example.txt"`: quick local evaluation from sample text.
- `./gradlew run --args="pipeline run --persona product_expat_engineer"`: fetch, evaluate, and write reports for one persona.
- `./gradlew run --args="evaluate-input --raw-text-file examples/raw-job-text.example.txt --persona product_expat_engineer"`: ad-hoc evaluation without a full pipeline run.
- `./gradlew run --args="rerun-ad-hoc --input-dir output/ad-hoc-evaluations"`: refresh saved ad-hoc artifacts in place.
- `./gradlew run --args="rerun-ad-hoc-matrix --candidate-profile pmfb --input-dir output/ad-hoc-evaluations"`: rebuild saved ad-hoc sources for one candidate profile across all personas.
- `./gradlew installDist && ./build/install/go-no-go-engine/bin/go-no-go-engine tui`: launch the terminal UI.

## Screens

![CLI sample evaluation](../../docs/screenshots/cli-check.png)

![Terminal launcher](../../docs/screenshots/tui-launcher.png)

## Further Reading

- Repository quickstart: [`../../docs/quickstart.md`](../../docs/quickstart.md)
- Advanced guide: [`../../docs/advanced-guide.md`](../../docs/advanced-guide.md)
- Engine config guide: [`config/README.md`](config/README.md)
- Candidate profiles: [`config/candidate-profiles/README.md`](config/candidate-profiles/README.md)

The public repository is read-only by default. Real candidate profiles and other private runtime inputs should stay local and untracked.
