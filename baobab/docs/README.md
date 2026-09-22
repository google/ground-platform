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

-   **[`design/`](design/)**:
    -   [`design.md`](design/design.md): Ground 2.0 PRD, system architecture,
        ODK XForms mental model & terminology mapping, and end-to-end technical
        design.
    -   [`impacts.md`](design/impacts.md): Platform impact analysis.
    -   [`website-outline.md`](design/website-outline.md): Public documentation
        and portal outline.
    -   [`future-work.md`](design/future-work.md): Future roadmap and
        extensibility plans.
-   **[`model/`](model/)**:
    -   [`forms/`](model/forms/00-introduction.md): ProtoForms (`FormDef`,
        `RecordInstance`) specification and ODK XForms mapping.
    -   [`survey/`](model/survey/00-introduction.md): Survey definitions
        (`SurveyDef`), entity datasets, map configurations, and ACL/quotas.
    -   [`data/`](model/data/01-entity-records.md): Operational entity,
        submission, and audit record specifications.

## Summary: Mental Model & Terminology Mapping to ODK XForms

### 1. The Core Mental Model

-   **Tables = Current State (Persistent Master Data)**: Flat, stateful master
    datasets on ODK Central (`EntityDatasetDef` / `EntityRecord`) where each row
    represents a real-world object (site, plot, asset, or participant).
-   **Forms = Transactions / Events (Encounter Logs)**: Questionnaires
    (`FormDef`) filled out in the field. Completed submissions
    (`SubmissionRecord` / `RecordInstance`) are immutable event records
    preserving GPS, timestamps, and raw inputs.

### 2. For Survey Organizers / Form Designers: Forms & Tables

Organizers define how a form interacts with master tables using standard XLSForm
syntax:

-   **Populate a Table (Create Record)**:
    -   *ODK Concept*: Form configured to create a new entity.
    -   *XLSForm Mapping*:
        -   `entities` sheet: `list_name` specified, `create_condition`
            (optional).
        -   `survey` sheet: Target fields use the `save_to` column to populate
            table attributes.
-   **Update a Table (Update Record)**:
    -   *ODK Concept*: Form configured to update an existing entity.
    -   *XLSForm Mapping*:
        -   `survey` sheet: Select question using
            `select_one_from_file <table_name>.csv`.
        -   `entities` sheet: `entity_id` set to the selected entity's ID.
        -   `survey` sheet: Updated fields mapped to table attributes via
            `save_to`.
-   **Reference a Table (Lookup / Read-Only)**:
    -   *ODK Concept*: Consuming an Entity List or external dataset without
        writing back.
    -   *XLSForm Mapping*:
        -   `survey` sheet: `select_one_from_file <table_name>.csv` used for
            choices/filtering, or pre-filling read-only `calculate` / `note`
            fields with `instance('<table_name>')/root/item[...]`. No `save_to`
            mapping.
-   **Log Only (Standard Survey)**:
    -   *ODK Concept*: Traditional standalone XForm.
    -   *XLSForm Mapping*: Standard `survey` and `choices` sheets only. No
        `entities` sheet.

### 3. For Data Collectors (Map UI & Field Workflow)

The map layer drawer is split into two self-describing categories rather than
nesting layers under form menus:

-   **Data collection sites**:
    -   *ODK Concept*: Geospatial Entity Lists (spatial master tables) attached
        to the project.
    -   *Field Interaction*: Represents the target locations/features on the
        map. Tapping a site pin opens its current status and launches available
        actions (e.g., `[ + Inspect Site ]`, `[ + Update Info ]`).
    -   *Why*: Avoids duplicating the layer across multiple forms that interact
        with the same site, enabling a natural "site-first" workflow.
-   **Form Submissions**:
    -   *ODK Concept*: Form submission GPS instances (`geopoint`, `geotrace`, or
        `geoshape` questions recorded in completed submission instances).
    -   *Field Interaction*: Displays historical coverage and completed
        visits/logs on the map. Kept visually distinct from active sites.
