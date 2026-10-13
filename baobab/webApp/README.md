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

## Deploying to a Firebase Hosting Preview Channel

```bash
# Build and update the default 'web-console' preview channel (7-day expiration):
./scripts/deploy-preview.sh

# Or pick a channel name and expiration, or skip the Gradle build:
./scripts/deploy-preview.sh --channel my-feature --expires 14d --skip-build
```
