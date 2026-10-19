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
-->

# Golden-Screenshot Integration Tests

End-to-end tests that drive the real web console (`webApp`) and mobile app (`mobileWebApp`) in
headless Chromium against a local user data service (`server/userDataService`), and compare each
screen with a checked-in golden image.

## Running the tests

You need Node.js 22 or later and what the Gradle builds need (a JDK and the Android SDK; see the
apps' READMEs). From this directory:

```bash
npm ci              # Once, and after package-lock.json changes
npm run setup       # Once per Playwright version: downloads Chromium's headless shell
npm test            # Builds the apps and the service, then runs the tests
npm run report      # Opens the HTML report, with diffs for any failed screenshots
```

`npm test` runs the Gradle builds first (`installDist` for the service, `wasmJsBrowserDistribution`
for both apps), which can take several minutes even when little changed. Run `npx playwright test`
instead to reuse the last builds.

CI runs the same `npm test` in the `e2e` job of
[`.github/workflows/check.yml`](../.github/workflows/check.yml) and uploads the report when it fails.

## Running tests manually

Build once with `npm run build`, then pick what to run and how to watch it:

```bash
npx playwright test --project=webConsole        # One app: webConsole or mobileApp
npx playwright test tests/mobileApp.spec.ts     # One file
npx playwright test -g "sign-in page"           # Tests whose title matches
npx playwright test --headed                    # Shows the browser while the tests run
npx playwright test --ui                        # Interactive runner: pick tests, watch, time-travel
npx playwright test --debug -g "sign-in page"   # Steps through one test in the Playwright Inspector
```

Each run starts its own user data service and bundle server, and stops them when it ends.

To try the apps by hand with the exact setup the tests use, start both servers yourself, from this
directory, in two terminals:

```bash
# Terminal 1: the user data service, with its sample data
PORT=18180 USER_DATA_STORE=memory AUTH_MODE=dev \
  ../server/userDataService/build/install/user-data-service/bin/user-data-service

# Terminal 2: the web console and the mobile app bundles
node harness/serve-apps.mjs
```

On Windows (PowerShell), start the service with
`$env:PORT=18180; $env:USER_DATA_STORE="memory"; $env:AUTH_MODE="dev"; ..\server\userDataService\build\install\user-data-service\bin\user-data-service.bat`.

Then open the web console at <http://127.0.0.1:18192/> and the mobile app at
<http://127.0.0.1:18193/?chrome=0> (leave out `?chrome=0` to see it in the workbench's simulated
device). Stop both servers before running the tests, which need the same ports. Set
`E2E_USER_DATA_SERVICE_PORT`, `E2E_WEB_CONSOLE_PORT`, or `E2E_MOBILE_APP_PORT` to use other ports
(for the service, set `PORT` to the same value).

## Updating goldens

When a change alters what a screen looks like on purpose, regenerate its goldens and review the
images before you commit them:

```bash
npm run test:update                       # All goldens
npx playwright test --update-snapshots -g "signs in"   # Tests whose title matches
```

Goldens are in [`goldens/`](goldens/), one directory per test file.

## How it works

- **Real builds.** The tests run the production bundles that `wasmJsBrowserDistribution` builds for
  deployment. [`harness/serve-apps.mjs`](harness/serve-apps.mjs) serves them and supplies the two
  generated config scripts: one points the apps at the test's user data service, and the other
  turns Firebase and the development sign-in dialog off, so signing in uses the service's
  development user.
- **Real service.** Playwright starts the user data service binary with its bundled sample data
  (`USER_DATA_STORE=memory`, `AUTH_MODE=dev`) on its own port, so the tests can run alongside the
  launchpad. Before each test, [`tests/fixtures.ts`](tests/fixtures.ts) calls `POST /debug/reset`
  to restore the in-memory store to its initial seed state.
- **Same pixels everywhere.** Compose draws text with the fonts bundled in the app (Skia compiled
  to Wasm), and Chromium renders WebGL with its bundled software renderer (SwiftShader), so the
  goldens don't depend on the machine's fonts or GPU. The tests block every request that isn't to
  the local apps or service; without the Mapbox GL JS script, maps fall back to Compose's own
  renderer.
- **Finding controls.** Compose draws the UI into a canvas and mirrors it into a hidden
  accessibility tree. Composite cards and controls define explicit roles, labels
  (`contentDescription`), merged descendants (`mergeDescendants = true`), and deterministic
  traversal indices (`isTraversalGroup` / `traversalIndex`) so tests can find controls by role and
  name (`getByRole`) and click where they are drawn ([`tap`](tests/fixtures.ts)).
- **Waiting for the app.** [`expectScreen`](tests/fixtures.ts) waits until the app has stopped
  calling the user data service, then retries the screenshot comparison until it matches or times
  out.

## Adding a test

Add a `*.spec.ts` file under [`tests/`](tests/) and a matching project in
[`playwright.config.ts`](playwright.config.ts) if it needs a new app or viewport. Import `test`,
`tap`, and `expectScreen` from [`tests/fixtures.ts`](tests/fixtures.ts), run
`npx playwright test --update-snapshots` to create its goldens, and check the new images.
