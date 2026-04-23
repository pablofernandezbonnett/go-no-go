# Contributing

This repository is public, but it is kept read-only by default.

## Default Path

If you want to reuse or adapt the workflow for your own local setup:

- fork the repository
- customize the personas, workflows, and local runtime inputs around your own process

That is the expected path for most people.

## Direct Contributions

If you want to contribute back to this repository itself:

- contact me first
- if it makes sense, I can add you as a contributor

The goal is to keep the repository curated and consistent instead of turning it into an
open-ended shared hiring tool.

## Commercial Use

Commercial use is not allowed.

See `LICENSE` for the repository licensing split between documentation and code.

## Repository conventions

- Security issues should follow [SECURITY.md](SECURITY.md), not public bug reports.
- Personal candidate profiles, CVs, resumes, and other private job-search inputs must stay local and untracked.
- Keep only example templates under `config/candidate-profiles/`.

## Maintainer Checklist

When maintainers make changes:

1. Update the relevant root or child documentation when behavior or ownership changes.
2. Run `./scripts/verify.sh` for root-level or cross-project work.
3. Keep PR titles and descriptions in English and name the affected subproject when scoped.
