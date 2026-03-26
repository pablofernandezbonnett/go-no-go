#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
app_dir="$repo_root/apps/reports-ui"

port="${REPORTS_UI_PORT:-8792}"
web_port="${REPORTS_UI_WEB_PORT:-5468}"
proxy_port="${REPORTS_UI_PROXY_PORT:-5568}"
reports_root="${REPORTS_ROOT:-$repo_root/services/engine/output}"
engine_root="${ENGINE_ROOT:-$repo_root/services/engine}"

cd "$app_dir"
exec env \
  REPORTS_ROOT="$reports_root" \
  ENGINE_ROOT="$engine_root" \
  jaspr serve \
    --port "$port" \
    --web-port "$web_port" \
    --proxy-port "$proxy_port" \
    "$@"
