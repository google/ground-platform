#!/usr/bin/env bash
#
# Copyright 2026 The Collect One Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

# Wrapper around Binaryen's wasm-opt invoked by WasmAppConventionPlugin.
#
# Kotlin/Wasm's BinaryenWorkAction does not forward task environment variables to wasm-opt, so this
# wrapper:
#   1. Supports `--collectone-copy-only` (used by `-PWASM_OPT=none`) to copy the linked `.wasm`
#      directly without running `wasm-opt`, while keeping BinaryenExec's built-in `.mjs` syncing.
#   2. Caps BINARYEN_CORES (default: half of host CPUs, clamped to 1..4) so wasm-opt does not
#      saturate all cores or starve the desktop/other tasks.
#   3. Serializes concurrent wasm-opt invocations across parallel Gradle builds and git worktrees
#      on the same host via flock (when available), preventing multiple multi-GiB wasm-opt runs
#      from thrashing swap simultaneously. Set WASM_OPT_NO_LOCK=1 to opt out.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") <--collectone-copy-only | real-wasm-opt> [args...]" >&2
  exit 2
fi

if [[ "$1" == "--collectone-copy-only" ]]; then
  shift
  INPUT=""
  OUTPUT=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -o|--output)
        OUTPUT="${2:-}"
        shift 2
        ;;
      -*)
        shift
        ;;
      *)
        INPUT="$1"
        shift
        ;;
    esac
  done
  if [[ -z "${INPUT}" || -z "${OUTPUT}" ]]; then
    echo "wasm-opt-wrapper.sh --collectone-copy-only: missing input or -o output" >&2
    exit 2
  fi
  mkdir -p "$(dirname "${OUTPUT}")"
  exec cp -f "${INPUT}" "${OUTPUT}"
fi

REAL_WASM_OPT="$1"
shift

CORES=""
if [[ "${1:-}" == --collectone-cores=* ]]; then
  CORES="${1#--collectone-cores=}"
  shift
fi

if [[ -n "${CORES}" ]]; then
  export BINARYEN_CORES="${CORES}"
elif [[ -z "${BINARYEN_CORES:-}" ]]; then
  CPUS="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
  DEFAULT_CORES=$(( CPUS / 2 ))
  (( DEFAULT_CORES < 1 )) && DEFAULT_CORES=1
  (( DEFAULT_CORES > 4 )) && DEFAULT_CORES=4
  export BINARYEN_CORES="${DEFAULT_CORES}"
fi

if [[ "${WASM_OPT_NO_LOCK:-0}" != "1" ]] && command -v flock >/dev/null 2>&1; then
  LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/collect-one-wasm-opt-${USER:-$(id -u)}.lock"
  exec flock "${LOCK_FILE}" "${REAL_WASM_OPT}" "$@"
fi

exec "${REAL_WASM_OPT}" "$@"
