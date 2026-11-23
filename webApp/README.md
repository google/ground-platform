<!--
  Copyright 2026 The Collect One Authors.

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

# Collect One Web Console

`webApp` is the runnable Collect One web console (Kotlin/Wasm): survey
administration, organizations, survey and form authoring, map layers, and the
survey dashboard. It's a thin host: `Main.wasmJs.kt` mounts `WebConsoleApp()`
from [`../shared/web/`](../shared/web/) on the browser window's URL and history
(`WindowBrowserHistory`). Every page has its own path (`/surveys`,
`/surveys/<id>/dashboard`, `/surveys/<id>/editor`, `/surveys/new`,
`/organizations`, `/organizations/<id>`, `/impact`, `/admin`), so links can be
shared and the browser's Back and Forward buttons and reloading work. Firebase
Hosting (`firebase.json`) and the dev server (`webpack.config.d/devServer.js`)
serve `index.html` for these paths, and its `<base href="/">` resolves the
bundle's relative URLs against the site root.

## Architecture

-   [`../shared/web/`](../shared/web/) (`:shared:web`): the web
    console's pages, state (`WebAppState`), and root (`WebConsoleApp()`).
-   [`../shared/app/`](../shared/app/) (`:shared:app`): the
    domain, data, and UI shared with the mobile app. Its JS bridges
    (`pdf-export-bridge.js`, `media-capture-bridge.js`) are copied into this
    bundle and loaded by `index.html`.

Map labels and Places search need a Mapbox access token (basemap tiles don't).
`index.html` reads it from the `mapbox_token` URL parameter,
`window.MAPBOX_ACCESS_TOKEN`, or the `COLLECT_ONE_MAPBOX_TOKEN` local storage key.

## Running Locally

From the repository root, start the webpack dev server (port `8092` by default):

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Or on another port:
./gradlew :webApp:wasmJsBrowserDevelopmentRun -Pport=8095
```

## Connecting to the Server

The web console loads surveys, organizations, and data from the
[Collect One server](../server/README.md), and needs one to
start. Start the service, then the console:

```bash
# From the repository root, in one terminal (listens on http://localhost:8080):
./gradlew :server:run

# In another:
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Tell the dev server where the service is with `USER_DATA_SERVICE_URL`, either in
`local.properties` (`local.properties` in the repository root, shared with `androidApp/`, or
`webApp/local.properties`, which takes precedence) or in the environment when
you start the dev server. Use an address the dev server's machine can reach, such as
`localhost`:

```properties
USER_DATA_SERVICE_URL=http://localhost:8080
```

Or for a single run:

```bash
USER_DATA_SERVICE_URL=http://localhost:8080 ./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

The dev server proxies the service: the page sends its gRPC-Web calls to its
own origin, and the dev server forwards them to `USER_DATA_SERVICE_URL`. So the
browser never needs its own route to the service, and opening the console through
port forwarding, an HTTPS reverse proxy, or the development machine's hostname
all work the same, with no mixed-content or CORS issues.
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

## Signing In

The app signs in with Firebase Authentication (Google accounts) and sends the
user's Firebase ID token with every server call. The Firebase
project comes from `FIREBASE_WEB_CONFIG` (the Firebase web app config object
as JSON), set like `USER_DATA_SERVICE_URL`: as a Gradle property, in the
environment, or in `local.properties`. No Firebase config is checked in;
keep yours outside the repository, for example in `~/.gradle/gradle.properties`
as a single line `FIREBASE_WEB_CONFIG={"apiKey":"…","authDomain":"…",…}`.
The Firebase JS SDK is loaded from Google's CDN when the page starts
(`firebase-auth-bridge.js`), and the session persists across reloads.

Run the server with `AUTH_MODE=dev` (the default), which verifies
the token and treats signed-in users as administrators, or with
`AUTH_MODE=firebase` for the same project. Pages must be served from a domain
the Firebase project authorizes for sign-in (`localhost` is authorized by
default). Without `FIREBASE_WEB_CONFIG` (or with `none`), the app
uses fake sign-in, and the service serves its calls as its development
user.

Development builds (`DEV_SIGN_IN`, default `true`) open a **Development
sign-in** dialog before signing in. It explains how the simulated sign-in
works and asks which app role to simulate against a service in
`AUTH_MODE=dev`: **Administrator** (all sample data) or **Regular user**
(access only through ownership, invitations, organizations, and sharing). With
Firebase you sign in with your own account and the role is sent as
`x-dev-role`; with fake sign-in you sign in as Adam Admin (administrator) or
Ima User (a sample data collector in the Kenya Forest Service). The choice is remembered on the
device. Set `DEV_SIGN_IN=false` for builds served to real users.

## Deploying to a Firebase Hosting Preview Channel

The deployed bundle uses the `USER_DATA_SERVICE_URL` set where it was built, so
set it to a reachable service (for example a Cloud Run deployment) before
deploying; without one, the preview shows setup instructions.

In non-production builds (`dev` / `staging`), set `MOBILE_WEB_URL` (via
`-PMOBILE_WEB_URL=…`, environment variable, or `local.properties`) to link to
the mobile web app from the header and automatically redirect mobile browsers
(bypass the redirect with `?desktop=1`). Local `dev` defaults to
`http://localhost:8093`; set `MOBILE_WEB_URL=none` to disable.

Configure `FIREBASE_PROJECT_ID` and `FIREBASE_SA_EMAIL` via `--project` /
`--sa-email`, environment variables, or the root `local.properties` file:

```bash
# Build and update the default 'web-console' preview channel (7-day expiration):
./scripts/deploy-preview.sh \
  --project your-gcp-project-id \
  --sa-email firebase-adminsdk@your-gcp-project-id.iam.gserviceaccount.com

# Or pick a channel name and expiration, or skip the Gradle build (reading project/SA from env or local.properties):
./scripts/deploy-preview.sh --channel my-feature --expires 14d --skip-build
```
