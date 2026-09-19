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

# Ground 2.0 Protocol Buffer Schemas (`shared/protos/`)

This directory houses the canonical Protocol Buffer (`proto3`) schema
definitions shared across Ground 2.0 mobile, web, and backend components.

## Packages

-   **[`forms/`](forms/)** (`groundplatform.v2.forms`):
    -   `form_def.proto`: `FormDef`, `RecordSchema`, `FieldBinding`, and UI
        control tree definitions (`ControlNode`, `GroupNode`, `RepeatNode`).
    -   `record_instance.proto`: Reflection-free `RecordInstance` and
        hierarchical field/repeat values for form submissions.
    -   `types.proto`: Shared primitive data types (`DataType`, `Value`,
        geospatial coordinates, and multilingual `LocalizedText`).
    -   `audit_log.proto`: Client telemetry and question-level interaction audit
        events.
    -   `encrypted_submission.proto`: Asymmetric envelope encryption manifests
        for end-to-end encrypted submissions.
-   **[`survey/`](survey/)** (`groundplatform.v2.survey`):
    -   `survey_def.proto`: `SurveyDef` root container, `FormLaunchConfig`,
        `EntityDatasetDef`, and `MapConfig` layer definitions.
    -   `acl.proto`: Access control lists (`SurveyAcl`, `AclEntry`, `Role`) and
        organization quota definitions (`QuotaTier`, `QuotaLimits`).
-   **[`data/`](data/)** (`groundplatform.v2.data`):
    -   `entity_record.proto`: Persistent, versioned geospatial/tabular entity
        records (`EntityRecord`).
    -   `submission_record.proto`: Finalized survey submission transactions
        (`SubmissionRecord`).
    -   `audit_record.proto`: Append-only resource mutation audit logs
        (`AuditRecord`, `FieldDelta`, `AuditInfo`).

## Code Generation

Multiplatform Kotlin data classes and builders are automatically generated from
these `.proto` files by Square Wire during the [`shared/core`](../core/) Gradle
build (`./gradlew generateCommonMainProtos`).

For full data model documentation, see [`../../docs/model/`](../../docs/model/).
