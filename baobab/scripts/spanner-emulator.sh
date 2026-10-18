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

# Runs the Cloud Spanner emulator for local development of the user data
# service, using a pinned, checksum-verified release on Linux x86_64 and the
# matching Docker image elsewhere. Data lives in memory and is lost on exit.
#
# Usage:
#   scripts/spanner-emulator.sh     # gRPC on localhost:9010, REST on localhost:9020.
#
# Then start the service against it:
#   cd server/user-data-service && SPANNER_EMULATOR_HOST=localhost:9010 ./gradlew run

set -euo pipefail

EMULATOR_VERSION="1.5.59"
LINUX_X86_64_SHA="394ffc075839d7e930368ce6e97889d713856a7cfaa9b4c9fa708f882cbc4c5f"
GRPC_PORT="${SPANNER_EMULATOR_GRPC_PORT:-9010}"
REST_PORT="${SPANNER_EMULATOR_REST_PORT:-9020}"

os="$(uname -s)"
arch="$(uname -m)"

if [[ "${os}-${arch}" != "Linux-x86_64" ]]; then
  # The emulator only ships Linux x86_64 binaries; use the Docker image elsewhere.
  if ! command -v docker > /dev/null 2>&1; then
    echo "error: the Spanner emulator needs Docker on ${os}-${arch}." >&2
    exit 1
  fi
  exec docker run --rm -p "${GRPC_PORT}:9010" -p "${REST_PORT}:9020" \
    "gcr.io/cloud-spanner-emulator/emulator:${EMULATOR_VERSION}"
fi

cache_dir="${SPANNER_EMULATOR_CACHE_DIR:-${XDG_CACHE_HOME:-${HOME}/.cache}/ground/spanner-emulator}"
install_dir="${cache_dir}/${EMULATOR_VERSION}"

sha256() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

if [[ ! -x "${install_dir}/gateway_main" ]]; then
  mkdir -p "${install_dir}"
  echo "Downloading the Cloud Spanner emulator ${EMULATOR_VERSION}..." >&2
  tmp="$(mktemp "${install_dir}/emulator.XXXXXX.tar.gz")"
  curl -fsSL \
    "https://storage.googleapis.com/cloud-spanner-emulator/releases/${EMULATOR_VERSION}/cloud-spanner-emulator_linux_amd64-${EMULATOR_VERSION}.tar.gz" \
    -o "${tmp}"
  actual="$(sha256 "${tmp}")"
  if [[ "${actual}" != "${LINUX_X86_64_SHA}" ]]; then
    rm -f "${tmp}"
    echo "error: emulator checksum mismatch (expected ${LINUX_X86_64_SHA}, got ${actual})." >&2
    exit 1
  fi
  tar -xzf "${tmp}" -C "${install_dir}"
  rm -f "${tmp}"
fi

echo "Spanner emulator ${EMULATOR_VERSION}: gRPC localhost:${GRPC_PORT}, REST localhost:${REST_PORT}" >&2
cd "${install_dir}"
# The release bundles the libstdc++ it was built against.
LD_LIBRARY_PATH="${install_dir}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}" exec ./gateway_main \
  --hostname localhost \
  --grpc_port "${GRPC_PORT}" \
  --http_port "${REST_PORT}" \
  --copy_emulator_stdout=false
