#!/usr/bin/env bash
#
# Copyright 2026 The Collect One Authors.
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

# Checks for the presence of the Apache 2.0 license header in common file types
# across the repository.
#
# Usage:
#   scripts/license-check.sh           # Check only; exits non-zero if any file is missing the header.
#   scripts/license-check.sh --check   # Same as above.

set -euo pipefail

case "${1:-}" in
  "" | --check) ;;
  *)
    echo "Usage: $0 [--check]" >&2
    exit 2
    ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

LICENSE_MARKER="Licensed under the Apache License, Version 2.0"

missing=()
total=0

while IFS= read -r -d '' file; do
  total=$((total + 1))
  if ! head -n 35 "${file}" | grep -q "${LICENSE_MARKER}"; then
    missing+=("${file}")
  fi
done < <(
  find . \
    \( -name .git -o -name build -o -name .gradle -o -name .kotlin -o -name node_modules -o -name kotlin-js-store -o -name .nx -o -name dist -o -name .cache \) -prune \
    -o -type f \( \
      -name '*.kt' \
      -o -name '*.kts' \
      -o -name '*.proto' \
      -o -name '*.sh' \
      -o -name '*.py' \
      -o -name '*.java' \
      -o -name '*.js' \
      -o -name '*.mjs' \
      -o -name '*.cjs' \
      -o -name '*.ts' \
      -o -name '*.tsx' \
      -o -name '*.jsx' \
      -o -name '*.sql' \
      -o -name '*.yml' \
      -o -name '*.yaml' \
      -o -name '*.xml' \
      -o -name '*.html' \
    \) -print0 | sort -z
)

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "The following ${#missing[@]} file(s) are missing the Apache 2.0 license header:" >&2
  for f in "${missing[@]}"; do
    echo "  ${f}" >&2
  done
  exit 1
fi

echo "All ${total} checked files contain the Apache 2.0 license header." >&2
