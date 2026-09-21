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

# Ground 2.0 Web Console (`webApp`)

`org.groundplatform.v2.web` is the runnable Compose Multiplatform Web
application (`wasmJs` / `js`) for Ground 2.0 survey administration, form
authoring, map layer management, and submission data inspection.

## Architecture

Unlike [`../androidApp/`](../androidApp/) and [`../iosApp/`](../iosApp/)—which
share the offline field data collection experience, services, and UI in
[`../shared/mobile/`](../shared/mobile/)—`webApp` implements a separate desktop/web
workflow and depends directly on the cross-platform foundation modules:

-   [`../shared/core/`](../shared/core/) (`org.groundplatform.v2:protoforms`)
    for data models, serialization, XPath evaluation, and the `FormEngine`.
-   [`../shared/ui/`](../shared/ui/) (`org.groundplatform.v2:protoforms-ui`) for
    shared Material 3 theming (`GroundTheme`) and interactive form previews
    (`MobilePhoneFrame`, `MobileFormRunner`).

## Running Locally

From `webApp/`:

```bash
./gradlew wasmJsBrowserDevelopmentRun
```
