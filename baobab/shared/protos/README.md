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

This directory houses the canonical Protocol Buffer (`proto3`) schema and gRPC
service definitions shared across Ground 2.0 mobile, web, and backend
components.

## Packages

-   **[`forms/`](forms/)** (`groundplatform.v2.forms`):
    -   `form_def.proto`: `FormDef`, `RecordSchema`, `FieldBinding`, and UI
        control tree definitions (`ControlNode`, `GroupNode`, `RepeatNode`).
    -   `record_instance.proto`: Reflection-free `RecordInstance` and
        hierarchical field/repeat values (`FieldValue`, `RecordNode`) for form
        submissions.
    -   `types.proto`: Shared primitive data types (`DataType`, `TypedValue`,
        geospatial coordinates, and multilingual `TranslationCatalog`).
    -   `audit_log.proto`: Client telemetry and question-level interaction audit
        events.
    -   `encrypted_submission.proto`: Asymmetric envelope encryption manifests
        for end-to-end encrypted submissions.
-   **[`survey/`](survey/)** (`groundplatform.v2.survey`):
    -   `survey_def.proto`: `SurveyDef` root container, `FormLaunchConfig`,
        `EntityDatasetDef`, and `MapConfig` layer definitions.
    -   `acl.proto`: Access control lists (`SurveyAcl`, `AclEntry`, `Role`),
        survey access policies (`SharingPolicy`, `PeerDataVisibility`), and
        organization quota definitions (`QuotaTier`, `QuotaLimits`).
    -   `survey_service.proto`: `SurveyService` gRPC definition for survey
        discovery, offline definition retrieval, ACL queries, and batched atomic
        survey mutations (`MutateSurvey`).
-   **[`data/`](data/)** (`groundplatform.v2.data`):
    -   `entity_record.proto`: Persistent, versioned geospatial/tabular entity
        records (`EntityRecord`).
    -   `submission_record.proto`: Finalized survey submission transactions
        (`SubmissionRecord`).
    -   `audit_record.proto`: Append-only resource mutation audit logs
        (`AuditRecord`, `FieldDelta`, `AuditInfo`).
    -   `data_service.proto`: `DataService` gRPC definition for entity and
        submission retrieval (`GetEntity`, `ListEntities`, `GetSubmission`,
        `ListSubmissions`) and batched atomic data mutations (`MutateData`).

---

## gRPC Backend Services (`SurveyService` & `DataService`)

Ground 2.0 separates control-plane survey configuration (`SurveyService`) from
high-volume field data synchronization (`DataService`). Because mobile clients
download the complete `SurveyDef` and entity datasets to operate **100%
offline**, both services expose simple read/list RPCs paired with a **single
batched mutator RPC** (`MutateSurvey` and `MutateData`).

### 1. Atomic Batched Mutations (`MutateSurvey` & `MutateData`)

Rather than exposing separate `Create`, `Update`, `Patch`, and `Delete` RPCs,
each service provides a single mutator that accepts an ordered sequence of
mutations executed atomically within one transaction:

-   **`SurveyService.MutateSurvey(MutateSurveyRequest)`**: Executes a list of
    `SurveyMutation` steps (`create_survey`, `patch_field`, `patch_acl_role`,
    `patch_access_rules`, or `delete_survey`). Because mutations run in order,
    a single request can create a new `SurveyDef`, add an `EntityDatasetDef`
    (e.g., `"entity_datasets/plots"`), add a `FormDef` referencing that dataset,
    bind a `FormLaunchConfig` and map `LayerDef` to both, and grant collaborator
    roles in one atomic commit.
-   **`DataService.MutateData(MutateDataRequest)`**: Executes a list of
    `DataMutation` steps (`EntityMutation` and `SubmissionMutation`). When an
    offline mobile collector registers a new ad-hoc `EntityRecord` in the field
    and collects one or more `SubmissionRecord`s linked to that new entity's
    `entity_id` (or updates entity properties via `entity_saveto`), the client
    sends the `EntityMutation.create` followed by the dependent
    `SubmissionMutation.create` / `EntityMutation.patch` entries in the same
    batch.

### 2. Create vs. Partial Key-Value Patch (`NullValue` Deletion)

Clients always know whether they are creating a new resource or modifying an
existing one:

-   **`create` (`create_survey` / `EntityMutation.create` / `SubmissionMutation.create`)**:
    Initializes a new `SurveyDef`, `EntityRecord`, or `SubmissionRecord`.
-   **`patch` (`SurveyFieldPatch` / `AclRolePatch` / `EntityPatch` / `SubmissionPatch`)**:
    Mutates individual fields, properties, sub-resources, or ACL entries using a
    key-value `oneof`:
    -   Assigning a typed value in the `oneof` inserts or updates the entry at
        `key` (e.g., `"title"`, `"forms/<form_id>"`,
        `"entity_datasets/<dataset_id>"`, `EntityRecord.properties[key]`, or a
        slash-delimited form field path in `RecordInstance.data`).
    -   Assigning `google.protobuf.NullValue` (`null_value = NULL_VALUE`) in the
        `oneof` **deletes** the targeted property, sub-resource, form answer, or
        user ACL role (`AclRolePatch.null_value`).

### 3. Soft Delete vs. Hard Delete

-   **Soft Delete (via `patch`)**: Soft deletion preserves the record for audit
    history and conflict recovery and is performed via a standard `patch`
    mutation:
    -   **Survey**: `SurveyFieldPatch` with `key = "state"` and
        `state_value = DELETED`.
    -   **Entity / Submission**: `EntityFieldPatch` or `SubmissionFieldPatch`
        with `key = "is_deleted"` and `bool_value = true` (and can be restored
        by patching `bool_value = false`).
-   **Hard Delete (via `delete`)**: Setting `delete_survey = true` (in
    `SurveyMutation`) or `delete = true` (in `EntityMutation` /
    `SubmissionMutation`) permanently purges the resource from storage.

---

## Code Generation

Multiplatform Kotlin data classes, builders, and suspending service interfaces
(`SurveyServiceServer`, `DataServiceServer`) are automatically generated from
these `.proto` files by Square Wire during the [`shared/core`](../core/) Gradle
build (`./gradlew generateCommonMainProtos`).

For full data model documentation, see [`../../docs/model/`](../../docs/model/).
