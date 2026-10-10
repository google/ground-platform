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

# Ground 2.0 Documentation (`docs/`)

Architectural design specifications and Protocol Buffer data model documentation
for Ground 2.0.

## Sections

-   **[`product/`](product/)**:
    -   [`prd.md`](product/prd.md): Ground 2.0 Product Requirements, system
        architecture, and end-to-end technical design.
    -   [`xforms-integration.md`](product/xforms-integration.md): XForms & ODK
        Entities integration architecture, Entity-only map model, default
        1-Form-to-1-Entity provisioning, and automatic `save_to` schema
        synchronization.
    -   [`concept-brief.md`](product/concept-brief.md): Condensed summary of the
        Ground 2.0 objective, key improvements over 1.0, scope, and roles.
    -   [`ceo-integration.md`](product/ceo-integration.md): Collect Earth Online
        (CEO) functional inventory, desk-to-field plot flagging integration
        architecture, and specification delta analysis.
    -   [`terminology.md`](product/terminology.md): Ground 2.0 terminology and
        cross-platform parity with CEO, ODK, KoboToolbox, XLSForm, Survey123,
        and Open Foris Arena.
    -   [`impacts.md`](product/impacts.md): Platform impact analysis.
    -   [`impact-measurement.md`](product/impact-measurement.md): Proposed MAP
        impact measurement approach (Purpose Packs, indicator tags, spatial
        overlays, outcome events, and attribution rubric).
    -   [`website-outline.md`](product/website-outline.md): Public documentation
        and portal outline.
    -   [`future-work.md`](product/future-work.md): Future roadmap and
        extensibility plans.
-   **[`technical/`](technical/)**:
    -   [`client/architecture.md`](technical/client/architecture.md): Ground 2.0
        Kotlin Clean Architecture, MVVM pattern with Compose Multiplatform,
        dependency rules, and hardware/service client communication
        guidelines.
    -   [`model/`](technical/model/README.md): Protocol Buffer data model
        overview.
        -   [`forms/`](technical/model/forms/00-introduction.md): ProtoForms
            (`FormDef`, `RecordInstance`) specification and XForms mapping.
        -   [`survey/`](technical/model/survey/00-introduction.md): Survey
            definitions (`SurveyDef`), entity datasets, map configurations, and
            ACL/quotas.
        -   [`data/`](technical/model/data/01-entity-records.md): Operational
            entity, submission, and audit record specifications, plus
            [impact events and survey outcomes](technical/model/data/04-impact-events.md).
        -   [`library/`](technical/model/library/00-introduction.md):
            Organization libraries—concepts (the dictionary), form templates,
            Purpose Packs, and export profiles.
-   **[`ux/`](ux/)**:
    -   [`content-guidelines.md`](ux/content-guidelines.md): Ground 2.0 UX
        writing standards, voice and tone, domain terminology, and content
        guidelines for user-facing copy.

## Summary: Mental Model & Terminology Mapping to XForms

See [`product/xforms-integration.md`](product/xforms-integration.md) for full
details.

### The Core Mental Model

-   **Data tables / Map layers = Current State (Persistent Master Data)**: Flat,
    stateful master datasets (`EntityDatasetDef` / `EntityRecord`) where each
    row represents a real-world object (site, plot, asset, or participant).
-   **Forms = Transactions / Events (Encounter Logs)**: Questionnaires
    (`FormDef`) filled out in the field. Completed submissions
    (`SubmissionRecord` / `RecordInstance`) are immutable event records
    preserving GPS, timestamps, and raw inputs.

### Default Survey Designer Behavior (`1 Form → 1 Entity Dataset`)

-   **Automatic Provisioning**: Creating a new spatial form in the Survey
    Designer automatically provisions a backing `EntityDatasetDef` and
    configures `EntityDeclaration(action = CREATE)` with `save_to` bindings so
    every submission appends a new Entity on the map.
-   **Live Schema Sync**: Adding, renaming, or removing top-level questions in
    the Form Designer automatically synchronizes `FieldBinding.entity_saveto`
    and `EntityDatasetDef.properties`.
-   **Multi-Form Follow-Ups**: Subsequent forms in the same survey can target an
    existing `EntityDatasetDef` (`action = UPDATE` / `UPSERT`) via
    `select_one_from_file <table_name>.csv`.

### Map UI & Field Workflow (Entity-Only Map)

-   **Entities Only on the Map**: The map exclusively renders **Map features**
    (`GEOSPATIAL` Entity Lists), never separate raw submission geometry
    layers.
-   **Submissions in the Site Timeline**: Tapping a site marker opens its
    current status card, chronological `SubmissionRecord` history, and available
    follow-up actions (e.g., `[ + Inspect Site ]`, `[ + Update Info ]`).
