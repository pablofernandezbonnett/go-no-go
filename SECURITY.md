# Security Policy

## Supported scope

Security fixes are applied on the current default branch only.

The most relevant areas for this repository are:

- accidental exposure of local candidate data, CVs, or private runtime inputs
- browser endpoints leaking filesystem paths, shell commands, logs, or raw local profile content
- committed secrets or credentials
- unsafe handling of local-only artifacts that should remain untracked

## Reporting a vulnerability

Please avoid opening public issues for security reports.

If private vulnerability reporting is available on the repository host, use that channel first. If it is not available, contact the maintainer through a private channel on the hosting platform before public disclosure.

Include:

- affected path or feature
- impact and exposure conditions
- reproduction steps
- whether private local data can be disclosed through the issue

## Operational expectations

- Real candidate profiles, CVs, resumes, and similar personal files are expected to stay local and untracked.
- The repository should keep templates and examples only.
- Browser-facing surfaces should expose the minimum safe metadata required for local use.
