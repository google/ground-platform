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

# Ground 2.0 Protocol Buffer Schemas (`shared/protos/`)

This directory houses the canonical Protocol Buffer (`proto3`) schema and gRPC
service definitions shared across Ground 2.0 mobile, web, and backend
components.

## Core Mental Model & Package Mapping

Ground 2.0's protocol buffer schemas directly model the XForms & Entities
distinction between stateful master tables and immutable encounter logs:

-   **Tables = Current State (Persistent Master Data)**: Defined by
    `EntityDatasetDef` (`groundplatform.v2.survey`) and stored as `EntityRecord`
    rows (`groundplatform.v2.data`), where each row represents a real-world
    object (site, plot, asset, or participant). Forms can **Populate a Table**
    (create entity via `entities` sheet `list_name` + `save_to`), **Update a
    Table** (select via `select_one_from_file <table_name>.csv`, set
    `entity_id`, and map `save_to`), or **Reference a Table** (read-only lookup
    via `select_one_from_file` or `instance('<table_name>')/root/item[...]`).
-   **Forms = Transactions / Events (Encounter Logs)**: Defined by `FormDef`
    (`groundplatform.v2.forms`) and persisted as immutable `SubmissionRecord` /
    `RecordInstance` event records (`groundplatform.v2.data`) preserving GPS,
    timestamps, and raw inputs. Standalone questionnaires with no `entities`
    sheet act as **Log Only** forms.
-   **Map Layer Categories (`LayerDef.source`)**: Rather than nesting map layers
    under form menus, `MapConfig.layers` separates layers into two
    self-describing categories:
    -   **Map features** (`entity_dataset_id`): Geospatial Entity Lists
        (spatial master tables) representing target features on the map that
        open site-first actions (e.g., `[ + Inspect Site ]`,
        `[ + Update Info ]`).
    -   **Form Submissions** (`form_geometry`): Completed submission GPS
        instances (`geopoint`, `geotrace`, or `geoshape`) displaying historical
        coverage and visits visually distinct from active sites.

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
        discovery, offline definition retrieval, ACL queries, and idempotent
        batched survey mutations (`MutateSurvey`).
-   **[`data/`](data/)** (`groundplatform.v2.data`):
    -   `entity_record.proto`: Persistent, versioned geospatial/tabular entity
        records (`EntityRecord`).
    -   `submission_record.proto`: Finalized survey submission transactions
        (`SubmissionRecord`).
    -   `audit_record.proto`: Append-only resource mutation audit logs
        (`AuditRecord`, `FieldDelta`, `AuditInfo`).
    -   `data_service.proto`: `DataService` gRPC definition for entity and
        submission retrieval (`GetEntity`, `ListEntities`, `GetSubmission`,
        `ListSubmissions`) and idempotent batched data mutations (`MutateData`).

---

## gRPC Backend Services (`SurveyService` & `DataService`)

Ground 2.0 separates control-plane survey configuration (`SurveyService`) from
high-volume field data synchronization (`DataService`). Because mobile clients
download the complete `SurveyDef` and entity datasets to operate **100%
offline**, both services expose read/list RPCs paired with a **single idempotent
batched mutator RPC** (`MutateSurvey` and `MutateData`).

### 1. Client-Defined UUIDs & Merged `create` / `update` Idempotency

All resource identifiers (`survey_id`, `form_id`, `dataset_id`, `layer_id`,
`entity_id`, and `submission_id`) are generated by the client (RFC 4122 v4
UUIDs or stable client-assigned keys) before mutations are queued or sent.

Because UUIDs are always known upfront, **`create` and `update` mutations are
merged into a single key-value `update` operation**:

-   When the backend receives an `update` (`SurveyMutation.update_field`,
    `SurveyMutation.update_acl_role`, `SurveyMutation.update_access_rules`,
    `EntityMutation.update`, or `SubmissionMutation.update`) targeting a UUID
    that does not yet exist in storage, it automatically initializes a new
    `SurveyDef`, `EntityRecord`, or `SubmissionRecord` with that client-defined
    UUID and applies the sequence of key-value field updates.
-   If the record already exists—including when an offline mobile client retries
    a previously committed batch after an interrupted network connection—the
    backend applies the exact same key-value field updates in place, converging
    to the identical final state without failing with `ALREADY_EXISTS` or
    creating duplicate records.

### 2. Atomic Batched Mutations (`MutateSurvey` & `MutateData`)

Rather than exposing separate `Create`, `Update`, `Patch`, and `Delete` RPCs,
each service provides a single mutator that executes an ordered sequence of
idempotent mutations within one atomic transaction:

-   **`SurveyService.MutateSurvey(MutateSurveyRequest)`**: Executes a list of
    `SurveyMutation` steps (`update_field`, `update_acl_role`,
    `update_access_rules`, or `delete_survey`) against `survey_id`. Because
    mutations run sequentially in one transaction, a single request can
    initialize a new survey (`"title"`, `"description"`, `"state"`), add an
    `EntityDatasetDef` (`"entity_datasets/plots"`), add a `FormDef`
    (`"forms/plot_survey"`) referencing that dataset, bind a `FormLaunchConfig`
    and map `LayerDef` to both, and grant collaborator roles in one atomic
    commit.
-   **`DataService.MutateData(MutateDataRequest)`**: Executes a list of
    `DataMutation` steps (`EntityMutation` and `SubmissionMutation`). When an
    offline mobile collector registers a new ad-hoc `EntityRecord` in the field
    and collects one or more `SubmissionRecord`s linked to that new entity's
    client-generated `entity_id` (or updates entity properties via
    `entity_saveto`), the client sends `EntityMutation.update` followed by the
    dependent `SubmissionMutation.update` entries in the same batch.

### 3. Key-Value `update` & `NullValue` Deletion

Every `update` mutation (`SurveyFieldPatch`, `AclRolePatch`, `EntityPatch`,
`SubmissionPatch`) modifies individual fields, properties, sub-resources, or ACL
entries using a key-value `oneof`:

-   Assigning a typed value in the `oneof` sets or updates the entry at `key`
    (e.g., `"title"`, `"forms/<form_id>"`, `"entity_datasets/<dataset_id>"`,
    `EntityRecord.properties[key]`, or a slash-delimited form field path in
    `RecordInstance.data`).
-   Assigning `google.protobuf.NullValue` (`null_value = NULL_VALUE`) in the
    `oneof` **deletes** the targeted property, sub-resource, form answer, or
    user ACL role (`AclRolePatch.null_value`), acting as an idempotent no-op if
    the key is already absent.

### 4. Soft Delete vs. Hard Delete

-   **Soft Delete (via `update`)**: Soft deletion preserves the record for audit
    history and conflict recovery and is performed via a standard key-value
    `update` mutation:
    -   **Survey**: `SurveyFieldPatch` with `key = "state"` and
        `state_value = DELETED`.
    -   **Entity / Submission**: `EntityFieldPatch` or `SubmissionFieldPatch`
        with `key = "is_deleted"` and `bool_value = true` (and can be restored
        by updating `bool_value = false`).
-   **Hard Delete (via `delete`)**: Setting `delete_survey = true` (in
    `SurveyMutation`) or `delete = true` (in `EntityMutation` /
    `SubmissionMutation`) permanently purges the resource from storage (acting
    as an idempotent no-op if the target UUID has already been purged).

---

## Code Generation

Multiplatform Kotlin data classes, builders, and suspending service interfaces
(`SurveyServiceServer`, `DataServiceServer`) are automatically generated from
these `.proto` files by Square Wire during the [`shared/core`](../core/) Gradle
build (`./gradlew generateCommonMainProtos`).

For full data model documentation, see [`../../docs/model/`](../../docs/model/).
