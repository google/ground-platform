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
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

read_local_prop() {
  local key="$1"
  local props_file="${APP_DIR}/../local.properties"
  if [[ -f "${props_file}" ]]; then
    sed -n "s/^${key}=//p" "${props_file}" 2>/dev/null | tail -n 1 | tr -d '\r'
  fi
}

CHANNEL="web-console"
EXPIRES="7d"
PROJECT_ID="${FIREBASE_PROJECT_ID:-$(read_local_prop FIREBASE_PROJECT_ID)}"
SA_EMAIL="${FIREBASE_SA_EMAIL:-$(read_local_prop FIREBASE_SA_EMAIL)}"
SKIP_BUILD=false
SKIP_APK=false

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Deploy or update webApp to a Firebase Hosting preview channel.

Options:
  --channel <name>    Channel ID (default: web-console)
  --expires <period>  Expiration duration (e.g. 7d, 14d, 30d; default: 7d)
  --project <id>      Firebase project ID (or set FIREBASE_PROJECT_ID)
  --sa-email <email>  Service account email (or set FIREBASE_SA_EMAIL)
  --skip-apk          Skip building and copying the Android APK
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
    --project)
      PROJECT_ID="$2"
      shift 2
      ;;
    --sa-email)
      SA_EMAIL="$2"
      shift 2
      ;;
    --skip-apk)
      SKIP_APK=true
      shift
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

if [[ -z "${PROJECT_ID}" || -z "${SA_EMAIL}" ]]; then
  echo "Error: FIREBASE_PROJECT_ID (--project) and FIREBASE_SA_EMAIL (--sa-email) must be set via flags, environment, or local.properties." >&2
  exit 1
fi

# Ensure node and npx are on PATH
if ! command -v node &>/dev/null || ! command -v npx &>/dev/null; then
  GRADLE_NODE_DIR="$(ls -d "${HOME}"/.gradle/nodejs/node-*/bin 2>/dev/null | head -n 1 || true)"
  if [[ -n "${GRADLE_NODE_DIR}" && -d "${GRADLE_NODE_DIR}" ]]; then
    export PATH="${GRADLE_NODE_DIR}:${PATH}"
  fi
fi

if [[ "${SKIP_BUILD}" != "true" ]]; then
  echo "==> Building staging Wasm bundle (WASM_OPT=${WASM_OPT:-fast})..."
  "${APP_DIR}/../gradlew" -p "${APP_DIR}/.." :webApp:wasmJsBrowserDistribution \
    -PAPP_ENV=staging \
    -PWASM_OPT="${WASM_OPT:-fast}"
fi

if [[ "${SKIP_APK}" != "true" && "${SKIP_BUILD}" != "true" ]]; then
  echo "==> Building Android APK..."
  "${APP_DIR}/../gradlew" -p "${APP_DIR}/.." :androidApp:assembleRelease -PAPP_ENV=staging
  echo "==> Copying APK to webApp distribution..."
  cp "${APP_DIR}/../androidApp/build/outputs/apk/release/androidApp-release.apk" "${APP_DIR}/build/dist/wasmJs/productionExecutable/collect-one.apk"
  if [[ -d "${APP_DIR}/../mobileWebApp/build/dist/wasmJs/productionExecutable" ]]; then
    cp "${APP_DIR}/../androidApp/build/outputs/apk/release/androidApp-release.apk" "${APP_DIR}/../mobileWebApp/build/dist/wasmJs/productionExecutable/collect-one.apk"
  fi
fi

IOS_IPA_SOURCE="${IOS_IPA_PATH:-${APP_DIR}/../iosApp/build/outputs/ipa/collect-one.ipa}"
if [[ -f "${IOS_IPA_SOURCE}" ]]; then
  echo "==> Staging iOS IPA into webApp distribution..."
  cp "${IOS_IPA_SOURCE}" "${APP_DIR}/build/dist/wasmJs/productionExecutable/collect-one.ipa"
  if [[ -d "${APP_DIR}/../mobileWebApp/build/dist/wasmJs/productionExecutable" ]]; then
    cp "${IOS_IPA_SOURCE}" "${APP_DIR}/../mobileWebApp/build/dist/wasmJs/productionExecutable/collect-one.ipa"
  fi
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
