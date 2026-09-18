# Introduction

The Ground 2.0 (`groundplatform.v2.survey` and `groundplatform.v2.data`) data
model specification defines the strongly typed Protocol Buffer (`proto3`)
schemas governing survey administration, geospatial entity registries, access
control lists (ACLs), tiered quotas, field data submissions, and append-only
audit trails. These protocol buffer definitions serve as the canonical schemas
for both **data storage** (persistent server database records and offline client
caches) and **RPCs** (client-server synchronization and service-to-service
communication).

While [ProtoForms](docs/model/forms/00-introduction.md)
(`groundplatform.v2.forms`) defines individual form definitions (`FormDef`) and
form submission records (`RecordInstance`) with full ODK XForms round-trip
compatibility, the `groundplatform.v2.survey` and `groundplatform.v2.data`
packages provide the higher-level multi-form survey workspace, spatial indexing,
and operational records that power Ground 2.0 web and mobile applications.

## Package and File Architecture

All Ground 2.0 schemas reside under `core/protos/` and are organized into
modular `.proto` files under three packages: `groundplatform.v2.forms` (form
definitions and records), `groundplatform.v2.survey` (survey definitions and
metadata), and `groundplatform.v2.data` (user data records):

*   **`survey/survey_def.proto`** (`groundplatform.v2.survey`): Defines
    `SurveyDef` (the root survey container), form launch and multilingual CTA
    configurations (`FormLaunchConfig`), ODK Entity Dataset schemas
    (`EntityDatasetDef`), and map visualization (`MapConfig`, `LayerDef`,
    `GeometryStyle`).
*   **`survey/acl.proto`** (`groundplatform.v2.survey`): Defines standalone
    access control and quota models (`SurveyAcl`, `AclEntry`, `Role`,
    `QuotaTier`, `QuotaLimits`) using unique user identifiers (e.g., Firebase
    Auth UIDs).
*   **`data/entity_record.proto`** (`groundplatform.v2.data`): Defines
    `EntityRecord`, representing a persistent, versioned tabular or geospatial
    entity instance within an Entity Dataset.
*   **`data/submission_record.proto`** (`groundplatform.v2.data`): Defines
    `SubmissionRecord`, representing a finalized, versioned form submission
    transaction linked to a survey, form, and optional entity, embedding either
    a reflection-free `groundplatform.v2.forms.RecordInstance`, raw protobuf
    bytes, or an `groundplatform.v2.forms.EncryptedSubmissionManifest`.
*   **`data/audit_record.proto`** (`groundplatform.v2.data`): Defines
    `AuditRecord`, `FieldDelta`, and `AuditInfo` for platform-wide append-only
    mutation auditing and resource provenance tracking.

## Design Principles

1.  **Separation of Form Definition and Survey Context**: Individual forms
    (`groundplatform.v2.forms.FormDef`) remain portable, ODK-compatible
    definitions that can be shared or exported independently, while `SurveyDef`
    groups multiple forms together with shared lookup tables, map layers, and
    organizational policies.
2.  **Unique User Identity**: All ownership, ACL, and audit fields reference
    immutable user identifiers (such as Firebase Auth UIDs) rather than mutable
    email addresses.
3.  **Reflection-Free Runtime Compatibility**: Operational submission records
    directly embed `groundplatform.v2.forms.RecordInstance` (or encrypted
    manifests) so mobile, web, and server runtimes can process data without
    dynamic schema compilation or protobuf reflection.
4.  **Built-in Spatial & Environmental Traceability**: First-class support for
    local GeoID generation and automated compliance reporting (e.g., EUDR
    deforestation regulations).
