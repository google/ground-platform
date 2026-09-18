<!--
  IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors)
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

# ProtoForms Form Debugger (`devtools/formdebugger`)

A Kotlin Multiplatform (KMP) Compose single-page web application for inspecting,
converting, and debugging ODK XForms XML definitions, ProtoForms `textproto` /
`JSON` representations, and real-time XPath expressions.

## Features

1.  **Format Selector (`textproto` / `JSON` Radio Buttons)**

    -   Toggle the right-hand representation between Protocol Buffer Text Format
        (`textproto`) and Canonical Proto3 JSON (`JSON`).

2.  **Form Definition (`FormDef`) Bidirectional Editor**

    -   Paste or edit an ODK XForms XML definition (`<h:html>...</h:html>`) on
        the left, or edit the equivalent `groundplatform.v2.forms.FormDef`
        (`textproto` or `JSON`) on the right.
    -   Modifying either field automatically updates the other representation in
        real-time.

3.  **Record Instance (`RecordInstance`) Bidirectional Editor**

    -   Paste or edit an ODK submission XML payload (`<data
        id="...">...</data>`) on the left, or edit the equivalent
        `groundplatform.v2.forms.RecordInstance` (`textproto` or `JSON`) on the
        right.
    -   Synchronizes bidirectionally in real-time using field types defined in
        the active `FormDef`.

4.  **Real-Time XPath Evaluator**

    -   Enter any ODK XForms XPath expression (e.g. `/data/species`,
        `concat(/data/species, ' - ', /data/height_m)`, `count(/data/*)`).
    -   Evaluates immediately in real-time against the active `FormDef` and
        `RecordInstance` and displays the formatted string result, type,
        boolean/number coercions, and matched node-set paths in a read-only
        output panel.

## Running the Local Development Web Server

Ensure Java 21 is active (or rely on `org.gradle.java.home` configured in
`gradle.properties`).

From the `devtools/formdebugger/` directory, start the local
`webpack-dev-server` using either the **WasmJS** (recommended, faster runtime)
or **JS (IR)** target:

```bash
# Start the WasmJS browser development server on port 8090:
./gradlew wasmJsBrowserDevelopmentRun

# Or with continuous live-reload on source code changes:
./gradlew wasmJsBrowserDevelopmentRun --continuous

# Alternatively, start the JS (IR) browser development server:
./gradlew jsBrowserDevelopmentRun
```

### Accessing the Web App

The development server binds to `0.0.0.0:8090` (with `allowedHosts: 'all'`) so
it can be accessed both locally and remotely from a Google Cloudtop:

-   **Local machine**: `http://localhost:8090/`
-   **Cloudtop proxy URL**: `http://<hostname>.c.googlers.com:8090/` (e.g.
    `http://$(hostname -f):8090/`)

### Custom Port Override

Port `8090` is used by default (as port `8080` is frequently occupied on
Cloudtop environments). To run the server on a custom port, pass
`-Pport=<PORT>`:

```bash
./gradlew wasmJsBrowserDevelopmentRun -Pport=8095
```

## Running Tests & Building Production Bundles

```bash
# Run JVM unit tests verifying bidirectional XML <-> textproto sync and XPath evaluation:
./gradlew jvmTest

# Build standalone production static web bundles (output under build/dist/):
./gradlew jsBrowserDistribution wasmJsBrowserDistribution
```
