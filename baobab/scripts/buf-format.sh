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

# Checks or applies `buf format` to every Protocol Buffer schema under
# baobab/shared/protos/, using a pinned, checksum-verified buf release.
#
# Usage:
#   scripts/buf-format.sh           # Check only; prints a diff and exits non-zero if any file would change.
#   scripts/buf-format.sh --check   # Same as above.
#   scripts/buf-format.sh --fix     # Rewrite files in place.

set -euo pipefail

BUF_VERSION="1.73.0"

mode="check"
case "${1:-}" in
  "" | --check) mode="check" ;;
  --fix) mode="fix" ;;
  *)
    echo "Usage: $0 [--check|--fix]" >&2
    exit 2
    ;;
esac

os="$(uname -s)"
arch="$(uname -m)"
case "${os}-${arch}" in
  Linux-x86_64) asset="buf-Linux-x86_64" sha="8f2986298ad08f0cc1bf999b9797b7c383adf32d7edf0f73d6f1e1a701baeac1" ;;
  Linux-aarch64 | Linux-arm64) asset="buf-Linux-aarch64" sha="902b75267db7f4391e99b7fa0756050e5354234cc0437ef50eee9c788950c7a3" ;;
  Darwin-x86_64) asset="buf-Darwin-x86_64" sha="ff78d0ebf34180ebfa81d370275851ec630fcb088bf33e213fd723d0fd7444a6" ;;
  Darwin-arm64) asset="buf-Darwin-arm64" sha="6e6df0fef4522e4e43dfe7c341873c3f2c29ceb45a9dfa5e0bad5580b8b2022f" ;;
  *)
    echo "error: unsupported platform ${os}-${arch}." >&2
    exit 1
    ;;
esac

cache_dir="${BUF_CACHE_DIR:-${XDG_CACHE_HOME:-${HOME}/.cache}/ground/buf}"
buf="${cache_dir}/${BUF_VERSION}/${asset}"

sha256() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

if [[ ! -x "${buf}" ]] || [[ "$(sha256 "${buf}")" != "${sha}" ]]; then
  mkdir -p "$(dirname "${buf}")"
  echo "Downloading buf ${BUF_VERSION}..." >&2
  tmp="$(mktemp "$(dirname "${buf}")/buf.XXXXXX")"
  curl -fsSL "https://github.com/bufbuild/buf/releases/download/v${BUF_VERSION}/${asset}" -o "${tmp}"
  actual="$(sha256 "${tmp}")"
  if [[ "${actual}" != "${sha}" ]]; then
    rm -f "${tmp}"
    echo "error: buf checksum mismatch (expected ${sha}, got ${actual})." >&2
    exit 1
  fi
  chmod +x "${tmp}"
  mv "${tmp}" "${buf}"
fi

baobab_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${baobab_root}/shared/protos"

if [[ "${mode}" == "fix" ]]; then
  "${buf}" format --write .
  exit 0
fi

if ! "${buf}" format --diff --exit-code .; then
  echo >&2
  echo "The .proto files above are not formatted with buf." >&2
  echo "Run 'baobab/scripts/buf-format.sh --fix' or 'pnpm exec nx run baobab:format' to fix them." >&2
  exit 1
fi

echo "All .proto files under shared/protos are formatted with buf ${BUF_VERSION}." >&2
