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
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

CHANNEL="baobab"
EXPIRES="7d"
PROJECT_ID="gnd-dev"
SA_EMAIL="firebase-adminsdk-tcdf1@gnd-dev.iam.gserviceaccount.com"
SKIP_BUILD=false

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Deploy or update prototypeApp to a Firebase Hosting preview channel in gnd-dev.

Options:
  --channel <name>    Channel ID (default: baobab)
  --expires <period>  Expiration duration (e.g. 7d, 14d, 30d; default: 7d)
  --skip-build        Skip running Gradle build before deploying
  -h, --help          Show this help message
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --channel)
      CHANNEL="$2"
      shift 2
      ;;
    --expires)
      EXPIRES="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      ;;
  esac
done

# Ensure node and npx are on PATH
if ! command -v node &>/dev/null || ! command -v npx &>/dev/null; then
  GRADLE_NODE_DIR="$(ls -d /usr/local/google/home/gmiceli/.gradle/nodejs/node-*/bin 2>/dev/null | head -n 1 || true)"
  if [[ -n "${GRADLE_NODE_DIR}" && -d "${GRADLE_NODE_DIR}" ]]; then
    export PATH="${GRADLE_NODE_DIR}:${PATH}"
  fi
fi

if [[ "${SKIP_BUILD}" != "true" ]]; then
  echo "==> Building production Wasm bundle..."
  (cd "${APP_DIR}" && ./gradlew wasmJsBrowserDistribution)
fi

echo "==> Acquiring short-lived credentials for ${PROJECT_ID}..."
TEMP_KEY_FILE="$(mktemp /tmp/gnd-deploy-key-XXXXXX.json)"

cleanup() {
  local key_id
  if [[ -f "${TEMP_KEY_FILE}" ]]; then
    key_id="$(python3 -c "import json; print(json.load(open('${TEMP_KEY_FILE}')).get('private_key_id', ''))" 2>/dev/null || true)"
    if [[ -n "${key_id}" ]]; then
      echo "==> Cleaning up temporary service account key (${key_id})..."
      gcloud iam service-accounts keys delete "${key_id}" \
        --iam-account="${SA_EMAIL}" \
        --project="${PROJECT_ID}" \
        --quiet 2>/dev/null || true
    fi
    rm -f "${TEMP_KEY_FILE}"
  fi
}
trap cleanup EXIT INT TERM

gcloud iam service-accounts keys create "${TEMP_KEY_FILE}" \
  --iam-account="${SA_EMAIL}" \
  --project="${PROJECT_ID}" \
  --quiet

echo "==> Waiting for key propagation..."
sleep 6

echo "==> Deploying to Firebase Hosting channel '${CHANNEL}'..."
export GOOGLE_APPLICATION_CREDENTIALS="${TEMP_KEY_FILE}"
(
  cd "${APP_DIR}"
  max_attempts=3
  attempt=1
  while [[ ${attempt} -le ${max_attempts} ]]; do
    if npx --yes firebase-tools hosting:channel:deploy "${CHANNEL}" \
      --project "${PROJECT_ID}" \
      --expires "${EXPIRES}"; then
      break
    fi
    if [[ ${attempt} -lt ${max_attempts} ]]; then
      echo "==> Authentication propagation retry in 5s (attempt ${attempt}/${max_attempts})..."
      sleep 5
    fi
    attempt=$((attempt + 1))
  done
)
