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

# Collect One Protocol Buffer Schemas (`shared/protos/`)

This directory houses the canonical Protocol Buffer (`proto3`) schema and gRPC
service definitions shared across Collect One mobile, web, and backend
components.

## Core Mental Model & Package Mapping

Collect One's protocol buffer schemas directly model the XForms & Entities
distinction between stateful master tables and immutable encounter logs:

-   **Data tables / Map layers = Current State (Persistent Master Data)**: Defined
    by `EntityDatasetDef` (`groundplatform.v2.survey`) and stored as
    `EntityRecord` rows (`groundplatform.v2.data`), where each row represents a
    real-world object (site, plot, asset, or participant). Forms can **Populate a
    Data table / Map layer** (create entity via `entities` sheet `list_name` +
    `save_to`), **Update a Data table / Map layer** (select via
    `select_one_from_file <table_name>.csv`, set `entity_id`, and map
    `save_to`), or **Reference a Data table** (read-only lookup via
    `select_one_from_file` or `instance('<table_name>')/root/item[...]`).
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
-   **[`survey/`](survey/)** (`groundplatform.v2.survey`):
    -   `survey_def.proto`: `SurveyDef` root container, `FormLaunchConfig`,
        `EntityDatasetDef`, and `MapConfig` layer definitions.
    -   `acl.proto`: Access control lists (`SurveyAcl`, `AclEntry`, `Role`),
        survey access policies (`SharingPolicy`, `PeerDataVisibility`), and
        organization quota definitions (`QuotaTier`, `QuotaLimits`).
    -   `organization.proto`: Multi-tenant `Organization` (the counterpart of a
        Collect Earth Online institution) and its memberships
        (`OrganizationMember`, `OrganizationRole`, `MembershipStatus`).
        Surveys reference one via `SurveyDef.organization_id`; its Managers
        inherit `SURVEY_ORGANIZER` access to every survey in it.
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
    -   `impact_event.proto`: Append-only "data was used" signals for impact
        measurement (`ImpactEvent`, `ImpactEventType`).
    -   `impact_aggregate.proto`: Privacy-safe rows produced by the impact
        aggregation job (`ImpactAggregate`), per survey, organization, country,
        global, or grid cell scope.
    -   `data_service.proto`: `DataService` gRPC definition for entity and
        submission retrieval (`GetEntity`, `ListEntities`, `GetSubmission`,
        `ListSubmissions`) and idempotent batched data mutations (`MutateData`).
-   **[`library/`](library/)** (`groundplatform.v2.library`): Organization
    libraries (see
    [`docs/technical/model/library/`](../../docs/technical/model/library/)).
    -   `concept.proto`: Dictionary concepts (`ConceptDef`), code lists
        (`CodeList`, `CodeListItem`), multilingual `LocalizedText`, and the
        `Aggregation`, `PrivacyClass`, `Pillar`, and `LibraryStatus` enums.
    -   `template.proto`: Reusable form templates (`FormTemplateDef`).
    -   `purpose_pack.proto`: Survey-creation purposes (`PurposePackDef`) and
        export profiles (`ExportProfileDef`).
    -   `library_settings.proto`: Per-organization settings
        (`OrganizationLibrarySettings`), such as hidden global entries.
    -   `library_bundle.proto`: `LibraryBundle`, the top-level message of the
        global seed files (`shared/assets/library/<vocabulary>.textproto`) and
        of library imports and exports.


## gRPC Backend Services (`SurveyService` & `DataService`)

Collect One separates control-plane survey configuration (`SurveyService`) from
high-volume field data synchronization (`DataService`). Because mobile clients
download the complete `SurveyDef` and entity datasets to operate **100%
offline**, both services expose read/list RPCs paired with a **single idempotent
batched mutator RPC** (`MutateSurvey` and `MutateData`).

### Client-Defined UUIDs & Merged `create` / `update` Idempotency

All resource identifiers (`survey_id`, `form_id`, `dataset_id`, `layer_id`,
`entity_id`, and `submission_id`) and nested repeated item keys (`id`) are
generated by the client (RFC 4122 v4 UUIDs or stable client-assigned keys)
before mutations are queued or sent.

Because UUIDs are always known upfront, **`create`, `update`, and `delete`
mutations use sparse instances of the domain proto itself (`T upsert` and `T
delete`)**:

-   When the backend receives a mutation (`SurveyMutation`,
    `OrganizationMutation`, `LibraryMutation`, `EntityMutation`, or
    `SubmissionMutation`) targeting a UUID that does not yet exist in storage,
    it automatically initializes a new record with that client-defined UUID and
    applies the sparse `delete` mask followed by the sparse `upsert` tree.
-   If the record already exists—including when an offline mobile client retries
    a previously committed batch after an interrupted network connection—the
    backend applies the exact same sparse `delete` and `upsert` in place,
    converging to the identical final state without failing with
    `ALREADY_EXISTS` or creating duplicate records.

### Atomic Batched Mutations (`MutateSurvey` & `MutateData`)

Rather than exposing separate `Create`, `Update`, `Patch`, and `Delete` RPCs or
maintaining parallel `*Patch` schemas, each service provides a single mutator
that executes an ordered sequence of idempotent sparse-proto mutations within
one atomic transaction:

-   **`SurveyService.MutateSurvey(MutateSurveyRequest)`**: Executes a list of
    `SurveyMutation` steps (`upsert`, `delete`, `acl_upsert`, `acl_delete`,
    `trash_survey`, `restore_survey`, `save_working_copy`,
    `discard_working_copy`) against `survey_id`. Because mutations run
    sequentially in one transaction, a single request can initialize a new
    survey (`upsert.title`, `upsert.description`, `upsert.state`), add an
    `EntityDatasetDef` (`upsert.entity_datasets`), add a `FormDef`
    (`upsert.forms`) referencing that dataset, bind a `FormLaunchConfig` and map
    `LayerDef` to both, and grant collaborator roles (`acl_upsert.entries`) in
    one atomic commit.
-   **`DataService.MutateData(MutateDataRequest)`**: Executes a list of
    `DataMutation` steps (`EntityMutation` and `SubmissionMutation`). When an
    offline mobile collector registers a new ad-hoc `EntityRecord` in the field
    and collects one or more `SubmissionRecord`s linked to that new entity's
    client-generated `entity_id` (or updates entity properties via
    `entity_saveto`), the client sends `EntityMutation` (`upsert` / `delete`)
    followed by the dependent `SubmissionMutation` (`upsert` / `delete`) entries
    in the same batch.

### Sparse Proto `upsert` & Structural `delete` Masks

Every mutation carries two optional sparse instances of the target domain proto
`T` (`upsert` and `delete`), evaluated in order as `merge(applyDelete(base,
delete), upsert)`:

-   **Structural `delete` mask (`T delete`)**: Uses the domain proto `T` itself
    to address paths to clear or remove, where the presence of any non-default
    value at a leaf means "delete":
    -   **Scalar field** set to any non-default value in `delete` → clears the
        field on `base` to its proto3 default (`""`, `false`, `0`,
        `UNSPECIFIED`).
    -   **Repeated value list** (e.g., `supported_languages`, `purpose_ids`,
        `program_ids`, `quarantined_user_ids`, `hidden_global_entry_ids`, or
        geometry vertices) non-empty in `delete` → clears the list to `[]`.
    -   **Map entry** `k -> v` in `delete`: if `v` is a primitive or an empty
        message, removes key `k` from the map; if `v` is a message with child
        fields set, recurses into `v`.
    -   **Sub-message or keyed repeated item** (matched by its ID/`key` field)
        in `delete`: if it is a **leaf** (no fields set other than its ID/key),
        removes the item from the repeated list or clears the sub-message; if it
        is a **branch** (has child fields set beyond its ID/key), retains the
        item and recurses into those child fields.
-   **Sparse `upsert` merge (`T upsert`)**:
    -   **Scalars**: Non-default scalars in `upsert` overwrite the target field
        (clearing to default is expressed via `delete`).
    -   **Repeated value lists**: Non-empty repeated value lists in `upsert`
        replace the list.
    -   **Keyed repeated entities**: Items in `upsert` are matched by their
        stable ID (`form_id`, `id`, `entity_id`, `user_id`/`email`, etc.),
        recursively merged if already present or appended if new, and ordered by
        their `sort_order` field.

### Deleting Surveys, Entities, and Submissions

-   **Surveys (Trash)**: `trash_survey = true` (in `SurveyMutation`) moves a
    survey to Trash by setting `SurveyDef.trash_time`; `restore_survey = true`
    brings it back. Trashed surveys disappear from survey lists and stop
    syncing, and the server purges them 30 days after `trash_time`. Clients
    can't hard-delete surveys, and `SurveyState.DELETED` is only a server
    tombstone marker on `SurveyOverview`.
-   **Entities and submissions (soft delete & restore)**: Setting a leaf
    `delete` mask (`EntityMutation.delete` or `SubmissionMutation.delete` with
    no child fields set beyond IDs, or `delete.is_deleted = true`) or
    `upsert.is_deleted = true` soft-deletes the record (`is_deleted = true`),
    preserving a tombstone so delta syncs report the deletion. Setting
    `delete.is_deleted = true` alongside `upsert` fields (or clearing
    `is_deleted` via `delete`) restores a soft-deleted record.


## Code Generation

Multiplatform Kotlin data classes, builders, and suspending service interfaces
(`SurveyServiceServer`, `DataServiceServer`) are automatically generated from
these `.proto` files by Square Wire during the [`shared/core`](../core/) Gradle
build (`./gradlew generateCommonMainProtos`).

For full data model documentation, see [`../../docs/model/`](../../docs/model/).
