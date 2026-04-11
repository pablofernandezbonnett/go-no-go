# Contributing

## Current mode

This repository is currently published as a read-only personal reference project.

- External pull requests are not being accepted at this time.
- General feature requests, support requests, and repo-hosted collaboration are not part of the current workflow.
- Public issues may be closed or left unanswered when they do not affect the repository's documented local-only boundaries.
- Security issues should follow [SECURITY.md](SECURITY.md), not public bug reports.

## Repository conventions

- Repository-level documentation stays in English.
- Keep the engine as the source of truth for behavior and artifacts.
- Keep `ops-ui` under engine ownership.
- Do not commit local candidate profiles, CVs, resumes, or other personal job-search inputs.
- Keep only example templates under `config/candidate-profiles/`.

## Maintainer checklist

When maintainers make changes:

1. Update the relevant root or child documentation when behavior or ownership changes.
2. Run `./scripts/verify.sh` for root-level or cross-project work.
3. Keep PR titles and descriptions in English and name the affected subproject when scoped.
