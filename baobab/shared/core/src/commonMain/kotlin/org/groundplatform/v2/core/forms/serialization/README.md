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

# XForms Protobuf Serializer (`xforms-proto-serializer`)

A Kotlin Multiplatform (KMP) library
(`org.groundplatform.v2:xforms-proto-serializer`) that serializes and
deserializes [ODK XForms](https://getodk.github.io/xforms-spec/) XML documents
(`FormDef` form definitions and `RecordInstance` submission instances) to and
from the **ProtoForms** protocol buffer model (generated with
[Square Wire](https://square.github.io/wire/)).

--------------------------------------------------------------------------------

## Overview

The library bridges ODK XForms XML and strongly-typed Protocol Buffer
representations defined in the `groundplatform.v2.forms` schema:

-   **Form Definitions (`FormDef`)**: Converts `<h:html>` ODK XForms
    documents—including `<model>` (`<instance>`, `<bind>`, `<itext>`
    translations, secondary `<instance>` choice datasets) and `<body>` UI
    controls (`<input>`, `<select1>`, `<select>`, `<range>`, `<rank>`,
    `<upload>`, `<trigger>`, `<group>`, `<repeat>`)—to and from
    `groundplatform.v2.forms.FormDef` messages.
-   **Submission Instances (`RecordInstance`)**: Converts `<data id="...">` XML
    submission payloads—supporting nested groups, repeats, strongly-typed scalar
    values, coordinates/geometries, select lists, and metadata—to and from
    `groundplatform.v2.forms.RecordInstance` messages.

--------------------------------------------------------------------------------

## Supported Targets

This library is written in pure Kotlin (`commonMain`) with zero
platform-specific XML dependencies, supporting the following Kotlin
Multiplatform targets:

Target     | Source Set   | Description
:--------- | :----------- | :-------------------------
**JVM**    | `jvmMain`    | Android, Server & Desktop
**JS**     | `jsMain`     | Browser & Node.js (IR)
**WasmJS** | `wasmJsMain` | WebAssembly (Browser/Node)
**iOS**    | `iosMain`    | Apple iOS (Arm64 & X64)

--------------------------------------------------------------------------------

## Usage

All serialization and deserialization entry points are available on the
`XFormsXmlSerializer` singleton object in package
`org.groundplatform.v2.core.forms.serialization`.

### 1. Form Definitions (`FormDef`)

Deserialize an ODK XForms `<h:html>` XML document into a `FormDef` protobuf
object and serialize it back to XML:

```kotlin
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import groundplatform.v2.forms.FormDef

val xformsXmlString = """
  <h:html xmlns="http://www.w3.org/2002/xforms"
          xmlns:h="http://www.w3.org/1999/xhtml"
          xmlns:jr="http://openrosa.org/javarosa">
    <h:head>
      <h:title>Tree Survey</h:title>
      <model>
        <instance>
          <data id="tree_survey" version="1">
            <species/>
            <height_m/>
          </data>
        </instance>
        <bind nodeset="/data/species" type="string" required="true()"/>
        <bind nodeset="/data/height_m" type="decimal"/>
      </model>
    </h:head>
    <h:body>
      <input ref="/data/species">
        <label>Tree Species</label>
      </input>
      <input ref="/data/height_m">
        <label>Height (meters)</label>
      </input>
    </h:body>
  </h:html>
""".trimIndent()

// Deserialize XForms XML string -> FormDef proto
val formDef: FormDef = XFormsXmlSerializer.deserializeFormDef(xformsXmlString)

// Inspect the deserialized protobuf model
println("Form Title: ${formDef.title}")
println("Form ID: ${formDef.instanceId}")
println("Controls count: ${formDef.body.controls.size}")

// Serialize FormDef proto -> ODK XForms XML string
val serializedFormXml: String = XFormsXmlSerializer.serialize(
  formDef = formDef,
  prettyPrint = true,
)
```

### 2. Submission Instances (`RecordInstance`)

Deserialize an ODK XForms `<data>` submission XML string into a `RecordInstance`
protobuf object and serialize it back to XML:

```kotlin
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import groundplatform.v2.forms.RecordInstance

val submissionXmlString = """
  <data id="tree_survey" version="1">
    <meta>
      <instanceID>uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98</instanceID>
    </meta>
    <species>Quercus robur</species>
    <height_m>18.5</height_m>
  </data>
""".trimIndent()

// Deserialize submission XML string -> RecordInstance proto
// Optionally pass `formDef` or `schema` to guide exact field type resolution:
val recordInstance: RecordInstance = XFormsXmlSerializer.deserializeRecordInstance(
  xml = submissionXmlString,
  formDef = formDef, // optional
)

// Serialize RecordInstance proto -> ODK XForms submission XML string
val serializedSubmissionXml: String = XFormsXmlSerializer.serialize(
  record = recordInstance,
  prettyPrint = true,
)
```

--------------------------------------------------------------------------------

## Building and Running Tests

### Java Environment Requirement

Gradle and the Kotlin toolchain require **Java 21**. If your system default Java
version is newer than Java 21, set `JAVA_HOME` before invoking `./gradlew`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Run JVM Unit and Round-Trip Tests

To execute all JVM unit tests and data-driven XForms round-trip tests:

```bash
./gradlew jvmTest
```

### Run Multiplatform Checks and Compilations

To run the full test suite and verify compilation across JVM, JS, and WasmJS
targets:

```bash
./gradlew check compileKotlinJs compileKotlinWasmJs
```
