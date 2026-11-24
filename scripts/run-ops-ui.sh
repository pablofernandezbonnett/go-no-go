#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
app_dir="$repo_root/services/engine/ops-ui"

port="${OPS_UI_PORT:-8791}"
engine_root="${ENGINE_ROOT:-$repo_root/services/engine}"

cd "$app_dir"
exec env \
  ENGINE_ROOT="$engine_root" \
  jaspr serve \
    --port "$port" \
    "$@"
