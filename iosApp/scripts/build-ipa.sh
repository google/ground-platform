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
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${IOS_DIR}/.." && pwd)"

METHOD="ad-hoc"
CONFIGURATION="Release"
TEAM_ID=""
SERVICE_URL=""
MAPBOX_TOKEN=""
STAGE_BASE_URL=""
MANIFEST_ONLY=""
OUTPUT_DIR="${IOS_DIR}/build/outputs/ipa"
BUNDLE_ID="org.openforis.collectone.v2.iosApp"
APP_TITLE="Collect One"
APP_VERSION="2.0"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Build a development iOS .ipa for Collect One on macOS and optionally stage it
(plus an Apple OTA manifest plist) into mobileWebApp's hosting bundle.

Options:
  --method <mode>          Export method: ad-hoc (default), development,
                           enterprise, or unsigned (no signing cert; sideload
                           via AltStore, SideStore, Sideloadly, or Xcode)
  --configuration <cfg>    Xcode build configuration: Release (default) or Debug
  --team-id <id>           Apple Developer Team ID (for signed builds)
  --service-url <url>      USER_DATA_SERVICE_URL baked into Info.plist
  --mapbox-token <token>   MAPBOX_ACCESS_TOKEN baked into Info.plist
  --stage-base-url <url>   Copy collect-one.ipa into mobileWebApp/webApp dist
                           directories and generate collect-one-ios.plist using
                           <url> as the HTTPS origin (e.g.
                           https://mobile.example.org)
  --manifest-only <url>    Skip building and only write collect-one-ios.plist
                           for <url>/collect-one.ipa into ${OUTPUT_DIR}
  --output-dir <dir>       Output directory (default: iosApp/build/outputs/ipa)
  -h, --help               Show this help message
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --method)
      METHOD="$2"
      shift 2
      ;;
    --configuration)
      CONFIGURATION="$2"
      shift 2
      ;;
    --team-id)
      TEAM_ID="$2"
      shift 2
      ;;
    --service-url)
      SERVICE_URL="$2"
      shift 2
      ;;
    --mapbox-token)
      MAPBOX_TOKEN="$2"
      shift 2
      ;;
    --stage-base-url)
      STAGE_BASE_URL="${2%/}"
      shift 2
      ;;
    --manifest-only)
      MANIFEST_ONLY="${2%/}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
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

write_ota_manifest() {
  local base_url="$1"
  local dest_file="$2"
  mkdir -p "$(dirname "${dest_file}")"
  cat > "${dest_file}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>items</key>
  <array>
    <dict>
      <key>assets</key>
      <array>
        <dict>
          <key>kind</key>
          <string>software-package</string>
          <key>url</key>
          <string>${base_url}/collect-one.ipa</string>
        </dict>
      </array>
      <key>metadata</key>
      <dict>
        <key>bundle-identifier</key>
        <string>${BUNDLE_ID}</string>
        <key>bundle-version</key>
        <string>${APP_VERSION}</string>
        <key>kind</key>
        <string>software</string>
        <key>title</key>
        <string>${APP_TITLE}</string>
      </dict>
    </dict>
  </array>
</dict>
</plist>
EOF
}

if [[ -n "${MANIFEST_ONLY}" ]]; then
  write_ota_manifest "${MANIFEST_ONLY}" "${OUTPUT_DIR}/collect-one-ios.plist"
  echo "Wrote ${OUTPUT_DIR}/collect-one-ios.plist for ${MANIFEST_ONLY}/collect-one.ipa"
  exit 0
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Error: building an iOS .ipa requires macOS and Xcode." >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Error: xcodebuild not found. Install Xcode and its command line tools." >&2
  exit 1
fi

case "${METHOD}" in
  ad-hoc|development|enterprise|unsigned) ;;
  *)
    echo "Error: unsupported --method '${METHOD}'." >&2
    exit 1
    ;;
esac

case "${CONFIGURATION}" in
  Release|Debug) ;;
  *)
    echo "Error: unsupported --configuration '${CONFIGURATION}' (expected Release or Debug)." >&2
    exit 1
    ;;
esac

CONFIG_LOWER="$(printf '%s' "${CONFIGURATION}" | tr '[:upper:]' '[:lower:]')"

mkdir -p "${OUTPUT_DIR}"

echo "==> Building Kotlin iOS device framework (${CONFIGURATION})..."
GRADLE_LINK_TASK=":shared:mobile:link${CONFIGURATION}FrameworkIosArm64"
(cd "${REPO_DIR}" && ./gradlew -Pcollectone.ios=true "${GRADLE_LINK_TASK}" --no-daemon --console=plain)

if command -v xcodegen >/dev/null 2>&1; then
  echo "==> Regenerating iosApp.xcodeproj with XcodeGen..."
  (cd "${IOS_DIR}" && xcodegen generate)
fi

WORK_DIR="$(mktemp -d /tmp/collect-one-ios-build-XXXXXX)"
trap 'rm -rf "${WORK_DIR}"' EXIT INT TERM

XCODE_ARGS=(
  -project "${IOS_DIR}/iosApp.xcodeproj"
  -scheme iosApp
  -configuration "${CONFIGURATION}"
  -destination "generic/platform=iOS"
  "FRAMEWORK_SEARCH_PATHS=${REPO_DIR}/shared/mobile/build/bin/iosArm64/${CONFIG_LOWER}Framework"
)
if [[ -n "${TEAM_ID}" ]]; then
  XCODE_ARGS+=("DEVELOPMENT_TEAM=${TEAM_ID}")
fi
if [[ -n "${SERVICE_URL}" ]]; then
  XCODE_ARGS+=("USER_DATA_SERVICE_URL=${SERVICE_URL}")
fi
if [[ -n "${MAPBOX_TOKEN}" ]]; then
  XCODE_ARGS+=("MAPBOX_ACCESS_TOKEN=${MAPBOX_TOKEN}")
fi

IPA_OUT="${OUTPUT_DIR}/collect-one.ipa"

if [[ "${METHOD}" == "unsigned" ]]; then
  echo "==> Building unsigned iosApp.app and packaging collect-one.ipa..."
  DERIVED_DATA="${WORK_DIR}/DerivedData"
  xcodebuild build \
    "${XCODE_ARGS[@]}" \
    -derivedDataPath "${DERIVED_DATA}" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY=""
  APP_BUNDLE="$(find "${DERIVED_DATA}/Build/Products" -maxdepth 3 -name 'iosApp.app' | head -n 1)"
  if [[ -z "${APP_BUNDLE}" || ! -d "${APP_BUNDLE}" ]]; then
    echo "Error: could not locate built iosApp.app." >&2
    exit 1
  fi
  PAYLOAD_DIR="${WORK_DIR}/ipa-stage/Payload"
  mkdir -p "${PAYLOAD_DIR}"
  cp -R "${APP_BUNDLE}" "${PAYLOAD_DIR}/iosApp.app"
  (cd "${WORK_DIR}/ipa-stage" && zip -qry "${IPA_OUT}" Payload)
else
  ARCHIVE_PATH="${WORK_DIR}/iosApp.xcarchive"
  EXPORT_DIR="${WORK_DIR}/export"
  EXPORT_PLIST="${WORK_DIR}/ExportOptions.plist"
  echo "==> Archiving iosApp (${METHOD})..."
  xcodebuild archive \
    "${XCODE_ARGS[@]}" \
    -archivePath "${ARCHIVE_PATH}"

  cat > "${EXPORT_PLIST}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key>
  <string>${METHOD}</string>
  <key>signingStyle</key>
  <string>automatic</string>
$(if [[ -n "${TEAM_ID}" ]]; then echo "  <key>teamID</key><string>${TEAM_ID}</string>"; fi)
</dict>
</plist>
EOF

  echo "==> Exporting collect-one.ipa..."
  xcodebuild -exportArchive \
    -archivePath "${ARCHIVE_PATH}" \
    -exportPath "${EXPORT_DIR}" \
    -exportOptionsPlist "${EXPORT_PLIST}"

  BUILT_IPA="$(find "${EXPORT_DIR}" -maxdepth 2 -name '*.ipa' | head -n 1)"
  if [[ -z "${BUILT_IPA}" || ! -f "${BUILT_IPA}" ]]; then
    echo "Error: xcodebuild did not produce an .ipa in ${EXPORT_DIR}." >&2
    exit 1
  fi
  cp "${BUILT_IPA}" "${IPA_OUT}"
fi

echo "==> Built ${IPA_OUT}"

if [[ -n "${STAGE_BASE_URL}" ]]; then
  if [[ "${METHOD}" != "unsigned" ]]; then
    write_ota_manifest "${STAGE_BASE_URL}" "${OUTPUT_DIR}/collect-one-ios.plist"
  fi
  for dist_dir in \
    "${REPO_DIR}/mobileWebApp/build/dist/wasmJs/productionExecutable" \
    "${REPO_DIR}/webApp/build/dist/wasmJs/productionExecutable"; do
    if [[ -d "${dist_dir}" ]]; then
      cp "${IPA_OUT}" "${dist_dir}/collect-one.ipa"
      if [[ -f "${OUTPUT_DIR}/collect-one-ios.plist" ]]; then
        cp "${OUTPUT_DIR}/collect-one-ios.plist" "${dist_dir}/collect-one-ios.plist"
      fi
      echo "==> Staged iOS artifacts into ${dist_dir}"
    fi
  done
fi
