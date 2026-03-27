#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
app_dir="$repo_root/services/engine/ops-ui"

port="${OPS_UI_PORT:-8791}"
web_port="${OPS_UI_WEB_PORT:-5467}"
proxy_port="${OPS_UI_PROXY_PORT:-5567}"
engine_root="${ENGINE_ROOT:-$repo_root/services/engine}"

cd "$app_dir"
exec env \
  ENGINE_ROOT="$engine_root" \
  jaspr serve \
    --port "$port" \
    --web-port "$web_port" \
    --proxy-port "$proxy_port" \
    "$@"
