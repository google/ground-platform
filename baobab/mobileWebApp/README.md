<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

# Ground 2.0 Mobile App for the Web

`mobileWebApp` runs the Ground 2.0 mobile app (`GroundMobileApp()` from
[`../shared/mobile/`](../shared/mobile/)) in the browser with Kotlin/Wasm, for
testing and UX review without an Android or iOS build.

## Modes

-   **Workbench (desktop default, or `?chrome=1`)**: The mobile app runs inside
    a simulated device. The controls beside it switch between phone and tablet,
    rotate the device, and toggle dark theme and airplane mode.
-   **Full window (mobile browser default, or `?chrome=0`)**: When opened on a
    mobile browser (or with `?chrome=0` in the URL), the workbench chrome is
    hidden and the mobile app fills the browser viewport like a native app.

## Structure

-   **`workbench/`** (`org.groundplatform.v2:workbench`): A library with the
    workbench: `WorkbenchState`, the simulated device page
    (`MobileWorkbenchPage`), the root
    `MobileWorkbenchApp()`, XForms presets, and the simulation and reset tools
    (`WorkbenchViewModel`).
-   **`src/wasmJsMain/`**: The thin host: `Main.wasmJs.kt` and `index.html`
    (Mapbox GL, token lookup, and the JS bridges copied from
    [`../shared/app/`](../shared/app/)).

Map labels and Places search need a Mapbox access token (basemap tiles don't).
`index.html` reads it from the `mapbox_token` URL parameter,
`window.MAPBOX_ACCESS_TOKEN`, or the `GROUND_MAPBOX_TOKEN` local storage key.

## Running Locally

From `mobileWebApp/`, start the webpack dev server (port `8093` by default).
`./gradlew` here runs the Gradle wrapper from
[`../shared/ui/`](../shared/ui/).

```bash
./gradlew wasmJsBrowserDevelopmentRun

# Or on another port:
./gradlew wasmJsBrowserDevelopmentRun -Pport=8095
```

Run the workbench tests with `./gradlew :workbench:jvmTest`.

## Deploying to a Firebase Hosting Preview Channel

```bash
# Build and update the default 'mobile-preview' preview channel (7-day expiration):
./scripts/deploy-preview.sh

# Or pick a channel name and expiration, or skip the Gradle build:
./scripts/deploy-preview.sh --channel my-feature --expires 14d --skip-build
```
