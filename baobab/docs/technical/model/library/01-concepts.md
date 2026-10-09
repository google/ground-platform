<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Concepts and the Dictionary

A **concept** (`ConceptDef`) is a standard field definition in an
organization's library. Taken together, the concepts available to a survey form
its **dictionary**. Linking a form question to a concept tells Ground what the
answer *means*, so answers can be validated, exported, and aggregated
consistently across surveys, organizations, and languages.

## Concept Schema (`ConceptDef`)

```protobuf
message ConceptDef {
  // Stable ID. Global concepts use `<vocabulary>.<name>` (e.g.,
  // "eudr.commodity"); organization concepts use
  // `org.<organization_id>.<name>`.
  string id = 1;

  // Revision of this concept's metadata. Clarifications and new translations
  // increment the version; a change in meaning requires a new ID.
  int32 version = 2;

  // Owning library ("all-users" for global concepts).
  string organization_id = 3;

  // Localized display label and description.
  groundplatform.v2.forms.LocalizedString label = 4;
  groundplatform.v2.forms.LocalizedString description = 5;

  // Localized synonyms used by Survey Designer search.
  repeated groundplatform.v2.forms.LocalizedString keywords = 6;

  // Suggested data type and unit (UCUM code, e.g., "ha", "m3", "kg").
  groundplatform.v2.forms.DataType data_type = 7;
  string unit = 8;

  // Allowed values for select concepts.
  CodeList code_list = 9;

  // How dashboards roll up answers.
  Aggregation aggregation = 10;

  // What may leave the organization and what is suppressed publicly.
  PrivacyClass privacy_class = 11;

  // Goal mapping (e.g., "deforestation_free_supply_chains") and MAP pillar.
  // For organization concepts these are suggestions, reported separately.
  repeated string goals = 12;
  Pillar pillar = 13;

  // Source framework references (e.g., regulation article URLs).
  repeated string references = 14;

  LibraryStatus status = 15;

  // For promoted concepts: the previous organization concept IDs that now
  // resolve to this concept.
  repeated string aliases = 16;

  groundplatform.v2.data.AuditInfo audit_info = 17;
}

message CodeList {
  repeated CodeListItem items = 1;
}

message CodeListItem {
  // Language-independent value (e.g., "coffee").
  string code = 1;
  groundplatform.v2.forms.LocalizedString label = 2;

  // External identifiers (e.g., HS code, AGROVOC URI).
  map<string, string> external_ids = 3;
}

enum Aggregation {
  AGGREGATION_UNSPECIFIED = 0;
  // Count distinct map features with a non-empty answer.
  COUNT_DISTINCT_FEATURES = 1;
  // Count distinct map features per code-list value.
  COUNT_BY_CODE = 2;
  SUM = 3;
  MEAN = 4;
  // Share of map features with each code-list value.
  SHARE_BY_CODE = 5;
  // Not aggregated (e.g., names and identifiers).
  NONE = 6;
}

enum PrivacyClass {
  PRIVACY_CLASS_UNSPECIFIED = 0;
  // Aggregates may appear in platform-wide and public dashboards.
  AGGREGATE_PUBLIC = 1;
  // Aggregates are visible only within the owning organization.
  ORG_ONLY = 2;
  // Excluded from all cross-organization and public aggregates.
  SENSITIVE = 3;
}

enum Pillar {
  PILLAR_UNSPECIFIED = 0;
  MITIGATION = 1;
  ADAPTATION = 2;
  PROTECTION = 3;
}

enum LibraryStatus {
  LIBRARY_STATUS_UNSPECIFIED = 0;
  DRAFT = 1;
  STABLE = 2;
  DEPRECATED = 3;
}
```

## IDs and Versioning

| Rule | Example |
| :--- | :--- |
| Global IDs use `<vocabulary>.<name>` | `eudr.commodity`, `ferm.trees_surviving`, `core.area_ha` |
| Organization IDs use `org.<organization_id>.<name>` | `org.toroton-fcs.cherry_delivery_kg` |
| Clarifications, new translations, and new code-list values increment `version` | `eudr.commodity@2` adds Vietnamese labels |
| A change in meaning, unit, or aggregation requires a **new ID** | `ferm.area_restored_ha` → `ferm.area_under_restoration_ha` |
| Removing a code-list value is not allowed; deprecate the concept instead | — |
| Promoting an organization concept to the global library records the old ID in `aliases` | `aliases: "org.toroton-fcs.shade_tree_count"` |

## Linking Form Fields (`ConceptRef`)

Questions link to concepts through a `ConceptRef` on the question's
`FieldBinding`. Entity properties carry the same reference, so aggregation can
read the current state of map features directly.

```protobuf
message ConceptRef {
  // Concept ID, including the `org.<organization_id>.` prefix for
  // organization concepts.
  string concept_id = 1;
  int32 version = 2;
}

message FieldBinding {
  // ... fields 1–16 ...
  ConceptRef concept_ref = 17;
}

message EntityPropertyDefinition {
  // ... fields 1–4 ...
  ConceptRef concept_ref = 5;
}
```

*   **Choices**: each `ChoiceItem` of a linked select question maps to a
    code-list value through `ChoiceItem.properties["ground_code"]` (e.g., the
    choice labeled "Café" maps to `coffee`). Choices without a mapping are
    reported by the form validator.
*   **`save_to` inheritance**: when a linked question saves to an entity
    property (`FieldBinding.entity_saveto`), the Survey Designer copies its
    `concept_ref` to the matching `EntityPropertyDefinition`.
*   **Runtime**: form engines ignore `concept_ref`. It affects design-time
    validation, exports, and server-side aggregation only.

## XForms and XLSForm Serialization

Concept references round-trip as a namespaced bind attribute. ODK Collect,
Enketo, KoboToolbox, and ArcGIS Survey123 ignore unknown bind attributes, so
forms remain portable.

<!-- mdformat off -->

| Representation | Encoding |
| :--- | :--- |
| XForms | `<bind nodeset="/data/commodity" type="string" ground:concept="eudr.commodity@1"/>` with `xmlns:ground="http://groundplatform.org/xforms"` on the root element |
| XForms choices | A `ground_code` child element on each item of the choice list's secondary instance (`<item><name>cafe</name><label>Café</label><ground_code>coffee</ground_code></item>`), like any other extra choice column |
| XLSForm `survey` sheet | `bind::ground:concept` column (e.g., `eudr.commodity@1`) |
| XLSForm `choices` sheet | `ground_code` column (an underscore rather than a colon, because extra choice columns become XML element names) |

<!-- mdformat on -->

## Aggregation Scope

| Concept owner | Survey and organization dashboards | Global dashboard |
| :--- | :--- | :--- |
| Global ("All users") | Included | Included in the main goal totals |
| Organization, without suggested goals | Included | Not included |
| Organization, with suggested goals | Included | Reported separately under **Organization-suggested indicators**, grouped by suggested goal and organization; never added to the main goal totals |

Across all scopes, `SENSITIVE` concepts and organizations that have opted out
of platform-wide aggregation are excluded from cross-organization figures.
Plots are deduplicated by GeoID before aggregation.
