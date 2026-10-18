#!/usr/bin/env bash
#
# Copyright 2026 The Ground Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_SCRIPT="${SCRIPT_DIR}/../devtools/launchpad/server.mjs"

# Resolve Node.js runtime if not present in default PATH
if ! command -v node >/dev/null 2>&1; then
  for dir in "${HOME}/.gradle/nodejs"/node-*/bin; do
    if [[ -x "${dir}/node" ]]; then
      export PATH="${dir}:${PATH}"
      break
    fi
  done
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Error: Node.js runtime not found. Please ensure node (>= 22) is installed and available in PATH." >&2
  exit 1
fi

exec node "${SERVER_SCRIPT}" "$@"
