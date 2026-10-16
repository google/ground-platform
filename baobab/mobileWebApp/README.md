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

## Connecting to the User Data Service

The mobile app loads surveys, organizations, and data from the
[user data service](../server/user-data-service/README.md), and needs one to
start. Start the service, then the app:

```bash
# In server/user-data-service/ (listens on http://localhost:8080):
./gradlew run

# In mobileWebApp/:
./gradlew wasmJsBrowserDevelopmentRun
```

Tell the dev server where the service is with `USER_DATA_SERVICE_URL`, either in
`local.properties` (`baobab/local.properties`, shared with `androidApp/`, or
`mobileWebApp/local.properties`, which takes precedence) or in the environment when
you start the dev server. Use an address the Cloudtop itself can reach, such as
`localhost`:

```properties
USER_DATA_SERVICE_URL=http://localhost:8080
```

Or for a single run:

```bash
USER_DATA_SERVICE_URL=http://localhost:8080 ./gradlew wasmJsBrowserDevelopmentRun
```

The dev server proxies the service: the page sends its gRPC-Web calls to its
own origin, and the dev server forwards them to `USER_DATA_SERVICE_URL`. So the
browser never needs its own route to the service, and opening the app through
port forwarding, the Cloudtop HTTPS proxy (`*.proxy.googlers.com`), or the
Cloudtop's hostname all work the same, with no mixed-content or CORS issues.
The dev server re-reads `local.properties` on every request, so a change takes
effect on the next reload. The service can't be chosen from the page URL, so a
link can't point the app at another backend.

If no URL is configured, the app doesn't start: it shows setup instructions
instead (and logs them to the browser console). If the service can't be
reached, it shows an error banner with the cause and a Retry button, and the
dev server logs the failed request.

Production bundles (`wasmJsBrowserDistribution` and preview deploys) have no
dev server, so the build bakes in the URL from `-PUSER_DATA_SERVICE_URL=…`, the
environment, or `local.properties`, in that order. It must be reachable from
users' browsers, and use HTTPS if the bundle is served over HTTPS.

## Deploying to a Firebase Hosting Preview Channel

The deployed bundle uses the `USER_DATA_SERVICE_URL` set where it was built, so
set it to a reachable service (for example a Cloud Run deployment) before
deploying; without one, the preview shows setup instructions.

```bash
# Build and update the default 'mobile-preview' preview channel (7-day expiration):
./scripts/deploy-preview.sh

# Or pick a channel name and expiration, or skip the Gradle build:
./scripts/deploy-preview.sh --channel my-feature --expires 14d --skip-build
```
