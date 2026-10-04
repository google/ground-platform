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

# Checks or applies ktfmt (Google style) formatting to every Kotlin source and
# Gradle script under baobab/.
#
# Baobab is split into several independent Gradle builds, so this script runs a
# single pinned ktfmt release across all of them instead of relying on each
# build applying the ktfmt Gradle plugin.
#
# Usage:
#   scripts/ktfmt.sh           # Check only; exits non-zero if any file would change.
#   scripts/ktfmt.sh --check   # Same as above.
#   scripts/ktfmt.sh --fix     # Rewrite files in place.

set -euo pipefail

KTFMT_VERSION="0.64"
KTFMT_SHA256="5b3d5286fd2defcc7dc8e28c21ddf156cc6b2d8682bdcd929ce4333e7a6201f2"
KTFMT_JAR_NAME="ktfmt-${KTFMT_VERSION}-with-dependencies.jar"
KTFMT_URL="https://repo1.maven.org/maven2/com/facebook/ktfmt/${KTFMT_VERSION}/${KTFMT_JAR_NAME}"

mode="check"
case "${1:-}" in
  "" | --check) mode="check" ;;
  --fix) mode="fix" ;;
  *)
    echo "Usage: $0 [--check|--fix]" >&2
    exit 2
    ;;
esac

if ! command -v java > /dev/null 2>&1; then
  echo "error: java is required to run ktfmt (JDK 11+)." >&2
  exit 1
fi

cache_dir="${KTFMT_CACHE_DIR:-${XDG_CACHE_HOME:-${HOME}/.cache}/ground/ktfmt}"
jar="${cache_dir}/${KTFMT_JAR_NAME}"

sha256() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

if [[ ! -f "${jar}" ]] || [[ "$(sha256 "${jar}")" != "${KTFMT_SHA256}" ]]; then
  mkdir -p "${cache_dir}"
  echo "Downloading ktfmt ${KTFMT_VERSION}..." >&2
  tmp="$(mktemp "${cache_dir}/ktfmt.XXXXXX")"
  curl -fsSL "${KTFMT_URL}" -o "${tmp}"
  actual="$(sha256 "${tmp}")"
  if [[ "${actual}" != "${KTFMT_SHA256}" ]]; then
    rm -f "${tmp}"
    echo "error: ktfmt checksum mismatch (expected ${KTFMT_SHA256}, got ${actual})." >&2
    exit 1
  fi
  mv "${tmp}" "${jar}"
fi

baobab_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${baobab_root}"

files=()
while IFS= read -r -d '' f; do
  files+=("${f}")
done < <(
  find . \
    \( -name build -o -name .gradle -o -name .kotlin -o -name node_modules -o -name kotlin-js-store \) -prune \
    -o -type f \( -name '*.kt' -o -name '*.kts' \) -print0 | sort -z
)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No Kotlin files found." >&2
  exit 0
fi

if [[ "${mode}" == "fix" ]]; then
  java -jar "${jar}" --google-style --quiet "${files[@]}"
  exit 0
fi

if ! java -jar "${jar}" --google-style --dry-run --set-exit-if-changed "${files[@]}"; then
  echo >&2
  echo "The files above are not formatted with ktfmt (Google style)." >&2
  echo "Run 'baobab/scripts/ktfmt.sh --fix' or 'pnpm exec nx run baobab:format' to fix them." >&2
  exit 1
fi

echo "All ${#files[@]} Kotlin files are formatted with ktfmt ${KTFMT_VERSION}." >&2
