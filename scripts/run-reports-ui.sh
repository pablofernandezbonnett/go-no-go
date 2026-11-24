#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
app_dir="$repo_root/apps/reports-ui"

port="${REPORTS_UI_PORT:-8792}"
reports_root="${REPORTS_ROOT:-$repo_root/services/engine/output}"
engine_root="${ENGINE_ROOT:-$repo_root/services/engine}"

cd "$app_dir"
exec env \
  REPORTS_ROOT="$reports_root" \
  ENGINE_ROOT="$engine_root" \
  jaspr serve \
    --port "$port" \
    "$@"
