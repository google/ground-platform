<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the 'License');
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an 'AS IS' BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Ground 2.0 Web Console

`webApp` is the runnable Ground 2.0 web console (Kotlin/Wasm): survey
administration, organizations, survey and form authoring, map layers, and the
survey dashboard. It's a thin host: `Main.wasmJs.kt` mounts `WebConsoleApp()`
from [`../shared/web/`](../shared/web/) and keeps the page in sync with the URL
hash (`#surveys`, `#dashboard`, `#survey-editor`, `#create-survey`,
`#organizations`, `#organization/<id>`).

## Architecture

-   [`../shared/web/`](../shared/web/) (`org.groundplatform.v2:web`): the web
    console's pages, state (`WebAppState`), and root (`WebConsoleApp()`).
-   [`../shared/app/`](../shared/app/) (`org.groundplatform.v2:app`): the
    domain, data, and UI shared with the mobile app. Its JS bridges
    (`pdf-export-bridge.js`, `media-capture-bridge.js`) are copied into this
    bundle and loaded by `index.html`.

Map labels and Places search need a Mapbox access token (basemap tiles don't).
`index.html` reads it from the `mapbox_token` URL parameter,
`window.MAPBOX_ACCESS_TOKEN`, or the `GROUND_MAPBOX_TOKEN` local storage key.

## Running Locally

From `webApp/`, start the webpack dev server (port `8092` by default):

```bash
./gradlew wasmJsBrowserDevelopmentRun

# Or on another port:
./gradlew wasmJsBrowserDevelopmentRun -Pport=8095
```

## Connecting to the User Data Service

The web console loads surveys, organizations, and data from the
[user data service](../server/user-data-service/README.md), and needs one to
start. Start the service, then the console:

```bash
# In server/user-data-service/ (listens on http://localhost:8080):
./gradlew run

# In webApp/:
./gradlew wasmJsBrowserDevelopmentRun
```

Tell the dev server where the service is with `USER_DATA_SERVICE_URL`, either in
`local.properties` (`baobab/local.properties`, shared with `androidApp/`, or
`webApp/local.properties`, which takes precedence) or in the environment when
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
browser never needs its own route to the service, and opening the console through
port forwarding, the Cloudtop HTTPS proxy (`*.proxy.googlers.com`), or the
Cloudtop's hostname all work the same, with no mixed-content or CORS issues.
The dev server re-reads `local.properties` on every request, so a change takes
effect on the next reload. The service can't be chosen from the page URL, so a
link can't point the console at another backend.

If no URL is configured, the console doesn't start: it shows setup instructions
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
# Build and update the default 'web-console' preview channel (7-day expiration):
./scripts/deploy-preview.sh

# Or pick a channel name and expiration, or skip the Gradle build:
./scripts/deploy-preview.sh --channel my-feature --expires 14d --skip-build
```
