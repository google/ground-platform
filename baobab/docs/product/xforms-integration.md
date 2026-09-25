---
# Copyright 2026 The Ground Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
onedoc_gdoc_url: https://docs.google.com/document/d/1aYEZItFSCmn1lziASTUM5YakNeaswvL65IuFDbzDgqY
onedoc_tab_title: XForms Integration
---

# XForms Integration & Entity-First Map Architecture

**[SHARED EXTERNALLY]**

Authors: [@gmiceli](http://who/gmiceli) \
Last modified: [2026-09-24](google-date:2026-09-24T16:25:00Z)

## Overview & Motivation

**Ground 2.0** adopts the industry-standard **XForms / XLSForm** and **ODK Entities** specifications as its foundational data model (`ProtoForms`). This enables zero-configuration `.xlsx` and `.xml` round-tripping with ODK Central, KoboToolbox, ArcGIS Survey123, and Open Foris Arena while preserving Ground's hallmark **map-first, low-ceremony user experience**.

A central design question when marrying a map-centric field tool with XForms is: **What appears on the map—Map features (Entities), Form Submissions, or both?**

### Why Ground 2.0 Uses an Entity-Only Map

In early iterations of the XForms transition, map layers could point to either a **Geospatial Entity Dataset** (`entity_dataset_id`) or a **Form Submission Geometry Question** (`form_geometry`). In practice, showing both Entities and raw Submissions as separate map layers introduces severe UX friction:

1.  **The "Double Pin / Wrong Layer" Problem**: If a form registers a new plot (`CREATE` entity) *and* records a polygon geometry in the submission, rendering both Entities and Submissions paints duplicate, overlapping polygons on the map. Conversely, if a simple survey only creates Submissions without an Entity Dataset, its features appear under a static "Submissions" layer that lacks site status, identity, or follow-up actions.
2.  **Cognitive Overhead for Non-GIS Organizers**: Asking survey organizers to choose between configuring an XLSForm `entities` sheet with `save_to` mappings versus a standalone form just to get interactive pins on a map violates Ground's first guiding principle: **Proportional Complexity (Simple by Default, Powerful on Demand)**.
3.  **Loss of Longitudinal Upgradeability**: In Ground 1.0, *every* feature on the map was a **Map feature (Location of Interest)**, and every submission was attached to a site. If a 2.0 survey initially collects raw submissions without backing Entities, the organizer cannot later add a follow-up inspection form (`Year 2 Survival Audit`) against those same plots without migrating data.

### The Architectural Decision

To keep the user experience simple and unified:

1.  **The Map Only Displays Entities (Map features)**: Every spatial feature rendered on the Web Console and Mobile App map represents a stateful **Entity** (`EntityRecord` in a `GEOSPATIAL` `EntityDatasetDef`). Raw `SubmissionRecord` geometries are never rendered as independent map layers.
2.  **Default 1-Form-to-1-Entity Creation in the Survey Designer**: When an organizer creates a new form in the Survey Designer, Ground **automatically provisions a backing Entity Dataset** (`EntityDatasetDef`) and configures the form's `EntityDeclaration` to **append a new Entity on each submission** (`action: CREATE` with `save_to` / `entity_saveto` bindings).
3.  **Automatic Schema Synchronization**: As questions are added, renamed, or modified in the Survey Designer, Ground automatically keeps the backing `EntityDatasetDef` schema and the form's `save_to` mappings in sync behind the scenes.
4.  **Submissions Live in the Entity History Timeline**: Every completed form still produces an immutable, timestamped `SubmissionRecord` linked to the Entity's UUID (`entity_id`). Tapping an Entity on the map opens its **current state** (from the Entity properties) alongside its chronological **Submission History** timeline and available follow-up forms (`[ + Inspect Site ]`, `[ + Update Info ]`).

---

## Prior Art Across the XForms Ecosystem

Ground 2.0's automatic Entity provisioning and schema synchronization builds directly on proven patterns across leading XForms and XLSForm platforms:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Platform | Underlying Standard | Map Representation | How Form-to-Entity Schema Sync Works |
| :--- | :--- | :--- | :--- |
| **ArcGIS Survey123** | **XLSForm / XForms** | **Entities only (Feature Layers).** The map exclusively renders records from the backing spatial Feature Layer, never separate raw submission layers. | **Automatic.** Creating a new form in Survey123 Designer automatically provisions a hosted Feature Layer and binds every question to a table column. Adding or removing questions in the visual designer automatically adds or syncs columns in the Feature Layer schema. Follow-up forms target the existing layer via `submission_url`. |
| **CommCare (Dimagi)** | **XForms + `<case>` blocks** | **Entities only (Cases).** Mobile and web maps display registered Cases; tapping a Case opens its detail view and launches follow-up forms. | **Assisted / Auto-Save.** CommCare's visual Form Builder provides an *"Auto-save questions to Case Properties"* mode so authors do not have to hand-maintain `<case><update>` XML bindings as questionnaires evolve. |
| **ODK Central & Collect** | **XLSForm + ODK Entities (`save_to`)** | **Entity-first Map (`select_one_from_file <entities>.geojson`).** ODK introduced Entities specifically to replace static submission maps with stateful site maps. | **Manual in XLSForm.** Spreadsheet authors must manually define the `entities` sheet and copy field names into the `save_to` column for every question. Automating `save_to` generation in visual builders is a top community usability request. |
| **KoboToolbox** | **XLSForm** | **Submissions only.** Lacks native server-managed Entity layers. | **None.** Because the map only displays raw submissions, multi-visit monitoring produces duplicate stacked points for the same physical site. |

<!-- mdformat on -->

---

## Terminology & XLSForm Mapping

Ground 2.0 aligns its user-facing concepts directly with **XLSForm**, **ODK Entities**, **KoboToolbox**, and **ArcGIS Survey123** so surveys round-trip losslessly between Ground's visual WYSIWYG Designer and standard `.xlsx` / `.xml` files:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Ground 2.0 Term | Ground 1.0 Equivalent | ODK / XLSForm / Survey123 Equivalent | Purpose in Ground 2.0 |
| :--- | :--- | :--- | :--- |
| **Survey** (`SurveyDef`) | Survey | Project / Workbook | Top-level organizational container managing permissions, forms, site tables, map layers, and quotas |
| **Map features / Table** (`EntityDatasetDef` / `EntityRecord`) | Map feature (LOI) | Entity List (`entities` sheet / Feature Layer) | Stateful master dataset of real-world objects (plots, trees, clinics, or participants) rendered on the map |
| **Form** (`FormDef`) | Job | Form (`survey` sheet) | Questionnaire defining questions, skip logic, validation rules, and `save_to` entity creation/update rules |
| **Question / Group / Repeat / Note** | Task | Question / `begin group` / `begin repeat` / `note` | Individual form inputs, collapsible sections, repeatable sub-interviews, and read-only instructions |
| **Submission** (`SubmissionRecord`) | Submission | Submission / Instance (`<data>`) | Immutable, time-stamped encounter record preserving raw inputs, GPS metadata, and form version |

<!-- mdformat on -->

### Form–Table Interaction Patterns in XLSForm

Under the hood, every Ground 2.0 form interaction with a Site Table maps to standard XLSForm and ODK Entities constructs:

1.  **Populate a Site Table (Create New Entity — *Default for Initial Form*)**:
    *   *User Experience*: Field collector drops a pin or walks a plot boundary and fills out the form; a new site appears immediately on the map.
    *   *XLSForm Mapping*:
        *   `entities` sheet: `list_name` set to the target `EntityDatasetDef.id`, `create_condition` set to `true()` (or a conditional expression), and `label` set to the title expression.
        *   `survey` sheet: The primary geometry question maps `save_to = geometry`, and top-level questions map `save_to = <property_name>`.
2.  **Update a Site Table (Follow-Up / Inspection Form)**:
    *   *User Experience*: Collector taps an existing site on the map and selects a follow-up action (`[ + Inspect Site ]`, `[ + Update Info ]`). The form pre-selects that site, logs a new `SubmissionRecord`, and updates the site's current properties and workflow status on the map.
    *   *XLSForm Mapping*:
        *   `survey` sheet: Includes a site reference field (`select_one_from_file <dataset_id>.csv` or `select_one_from_file <dataset_id>.geojson`).
        *   `entities` sheet: `list_name` set to `<dataset_id>`, `entity_id` bound to the selected site's UUID (`${target_site}`), and `update_condition` enabled.
        *   `survey` sheet: Fields that modify the site's current state specify `save_to = <property_name>`.
3.  **Reference a Table (Read-Only Lookup)**:
    *   *User Experience*: Form looks up reference data (e.g., species taxonomy, village codes, or cooperative rosters) or pre-fills read-only context without modifying the table.
    *   *XLSForm Mapping*:
        *   `survey` sheet: Uses `select_one_from_file <table_name>.csv` for cascading choices or `instance('<table_name>')/root/item[...]` in `calculate` / `note` fields, with no `entities` sheet or `save_to` bindings.
4.  **Tabular / Non-Spatial Registry**:
    *   *User Experience*: Form registers or updates non-spatial entities (e.g., a cooperative member roster without coordinates).
    *   *XLSForm Mapping*: Same `entities` + `save_to` structure, backed by an `EntityDatasetDef` with `type: TABULAR` (managed in the Tables view rather than rendered as a map layer).

---

## Default Survey Designer Behavior & Schema Synchronization

To make the Entity-only map seamless for non-technical organizers, the Web Console Survey Designer automates entity provisioning and schema synchronization:

### 1. Automatic Entity Dataset Creation (`1 Form → 1 Entity Dataset` Default)

When an organizer creates the first form in a new survey (e.g., *"Tree Planting Registration"*):

1.  **Provision `EntityDatasetDef`**: The Designer automatically creates a companion `EntityDatasetDef` (`id: "tree_planting_registration"`, `type: GEOSPATIAL`, `field_creation_enabled: true`) and adds a corresponding `LayerDef` to `SurveyDef.map_config.layers`.
2.  **Configure `EntityDeclaration`**: The form's `ModelDef.entities` block is initialized with:
    *   `dataset`: `"tree_planting_registration"`
    *   `action`: `CREATE`
    *   `id_expression`: `"uuid()"` (populated on `EVENT_INSTANCE_FIRST_LOAD`)
    *   `condition_expression`: `"true()"`
3.  **Bind Primary Geometry**: The form's primary spatial question (`geopoint`, `geotrace`, or `geoshape`) is automatically bound to `entity_saveto: "geometry"` and sets `EntityDatasetDef.geometry_type`.
4.  **Bind Entity Label**: The first required text or select question (or a configurable title template such as `concat(${farmer_name}, ' - ', ${plot_code})`) is bound to `EntityDeclaration.label_expression`.

### 2. Live Form-to-Entity Schema Synchronization

As the organizer edits the form in the Survey Designer, Ground keeps `EntityDatasetDef.properties` and `FieldBinding.entity_saveto` synchronized automatically:

<!-- mdformat off(b/556740108: multiline tables break in GFM and OneDoc) -->

| Designer Action on Form | Automatic `FormDef` (`entity_saveto`) Update | Automatic `EntityDatasetDef` Schema Update |
| :--- | :--- | :--- |
| **Add top-level question** (`text`, `int`, `decimal`, `date`, `select_*`, `image`) | Sets `FieldBinding.entity_saveto = sanitize(field_name)` | Appends matching `EntityPropertyDefinition(name, type, label, required)` |
| **Add first spatial question** (`geopoint`, `geotrace`, `geoshape`) | Sets `FieldBinding.entity_saveto = "geometry"` | Sets `EntityDatasetDef.type = GEOSPATIAL` and `geometry_type = field.type` |
| **Rename question / update label** | Preserves stable `entity_saveto` property identifier | Updates `EntityPropertyDefinition.label` to match the new question label |
| **Delete question** | Removes field and `entity_saveto` from the new `FormDef` version | **Soft-deprecates** the property in `EntityDatasetDef` (retains existing values on previously created entities) |

<!-- mdformat on -->

---

## Practical Edge Cases & Resolution Rules

While automatic `1:1` form-to-entity syncing handles simple surveys effortlessly, the Survey Designer enforces five rules to handle advanced XForms patterns cleanly:

### 1. Multi-Form Workflows (`CREATE` vs. `UPDATE` on Shared Site Layers)

*   **Scenario**: An organizer adds a *second* form to a survey (e.g., Form 1 = *"Register Plot"*, Form 2 = *"Annual Coffee Audit"*). If Form 2 automatically created a *new* Entity Dataset, audits would appear on a separate map layer instead of updating the registered plots.
*   **Resolution**:
    *   When adding an additional form to a survey that already has a Site Layer (`EntityDatasetDef`), the Designer prompts the organizer with two clear options:
        1.  **Follow up on existing sites (Default for Form 2+)**: Configures `EntityDeclaration(dataset = existing_dataset_id, action = UPDATE)` (or `UPSERT` if field collectors can also register new plots in the same form), auto-inserts the hidden site selector (`select_one_from_file <dataset_id>.csv`), and syncs any *new* questions added in Form 2 as additional columns on the shared `EntityDatasetDef`.
        2.  **Create a new map layer / site type**: Creates a separate `EntityDatasetDef` and `LayerDef` (e.g., mapping *Processing Mills* on a separate layer from *Farm Plots*).

### 2. Forms with Multiple Spatial Questions

*   **Scenario**: A single form captures both a **Plot Boundary** (`geoshape`) and a **Farmhouse Entrance Point** (`geopoint`). An ODK Entity (`EntityRecord`) has a single primary `geometry` field used for map rendering.
*   **Resolution**:
    *   The **first spatial question** in the form is designated as the **Primary Map Geometry** (`entity_saveto: "geometry"`), which drives the map pin/polygon on the main layer.
    *   Any additional spatial questions in the same form are saved as standard entity properties (`entity_saveto: "farmhouse_entrance"` with `TYPE_GEOPOINT`) and rendered inside the Entity's detail view when the site is selected—or can optionally be toggled as the Primary Map Geometry in the Question settings card.

### 3. Repeat Groups (`<repeat>`), Notes, and Reserved Identifiers

*   **Scenario**: In the ODK Entities specification, parent Entity records are flat key-value structures; questions nested inside a `<repeat>` group (e.g., measuring 20 individual trees inside one plot) cannot `save_to` flat columns on the parent Entity without overwriting each iteration. Additionally, ODK Entities reserves `name`, `label`, and prefixes starting with `__` (`__id`, `__version`, `__trunkVersion`, `__branchId`).
*   **Resolution**:
    *   **Repeats**: Questions inside a `<repeat>` block are **excluded from parent `entity_saveto` by default**. All repeat instances are preserved in full inside the `SubmissionRecord` (visible in the site's Submission History drawer and in harmonized CSV exports). Optionally, top-level repeat aggregations (`count(${tree_repeat})`, `sum(${tree_count})`) are auto-bound via `save_to` onto the parent Entity, or an advanced user can bind a repeat to a child `EntityDatasetDef` (`RepeatDef.entities`).
    *   **Notes**: Read-only `note` elements are excluded from `entity_saveto` and `EntityDatasetDef`.
    *   **Identifier Sanitization**: If a form question is named `name`, `label`, or `__*`, the Designer automatically prefixes the generated `entity_saveto` property name (e.g., `field_name` or `field_label`) while binding `label` to `EntityDeclaration.label_expression`.

### 4. Visual Workflow State Progression on the Map (`○` → `◐` → `✓`)

*   **Scenario**: Supervisors and field enumerators need immediate visual feedback on the map showing which preloaded sites are **Pending (`○`)**, **In Progress (`◐`)**, or **Completed (`✓`)**.
*   **Resolution**:
    *   Because the map renders Entities styled via [`simplestyle-spec` properties](https://github.com/mapbox/simplestyle-spec) (`marker-symbol`, `marker-color`, `stroke`, `fill`), forms automatically include calculated `entity_saveto` bindings that update the target Entity's visual status property upon submission—transitioning a site from **Pending (`○`, `#E65100`)** to **In Progress (`◐`, `#F9AB00`)** to **Completed (`✓`, `#1E8E3E`)** completely offline.

### 5. Importing External XLSForms (`.xlsx`) Without an `entities` Sheet

*   **Scenario**: A partner imports an existing KoboToolbox or ODK XLSForm (`.xlsx`) that only contains `survey` and `choices` sheets (no `entities` sheet or `save_to` column).
*   **Resolution**:
    *   Upon XLSForm import, if the workbook does not declare an `entities` sheet and contains at least one spatial question (`geopoint`, `geotrace`, or `geoshape`), Ground **automatically synthesizes the default `EntityDatasetDef` and `save_to` bindings** using the rules above.
    *   The imported form immediately works with Ground's Entity-only map out of the box, and exporting the survey back to `.xlsx` produces a standards-compliant XLSForm complete with the generated `entities` sheet and `save_to` column.

