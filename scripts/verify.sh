#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_cmd dart
require_cmd jaspr

required_files=(
  "README.md"
  "ARCHITECTURE.md"
  "AGENTS.md"
  "services/engine/AGENTS.md"
  "services/engine/ops-ui/AGENTS.md"
  "apps/reports-ui/AGENTS.md"
)

for path in "${required_files[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "missing required file: $path" >&2
    exit 1
  fi
done

(
  cd services/engine
  ./gradlew test
)

(
  cd services/engine/ops-ui
  dart analyze
)

(
  cd apps/reports-ui
  dart analyze
  jaspr clean
  jaspr build
)
