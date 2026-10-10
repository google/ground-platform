<!--
 Copyright 2026 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Domain Models

**TL;DR:** The `domain/model` package defines pure Kotlin business entities, value objects, and domain invariants shared across repositories, use cases, ViewModels, and UI state.

## Scope & Purpose

Domain models represent the canonical Ground 2.0 vocabulary (Surveys, Forms, Map layers, Data tables, Geospatial entities, Records/Submissions, Mutations, Organizations, and Settings):

- **Core Runtime Models**:
  - `SurveyModels.kt`: `SurveyPreviewItem`, `SurveyConfig`, `SurveyContent`, and survey access/sync states.
  - `FormModels.kt`: `FormPreviewItem`, `FormAvailability`, and form metadata.
  - `MapLayerModels.kt`: `MapLayerItem`, `DatasetKind` (Map layer vs. Data table), geometry types, and styling metadata.
  - `GeospatialEntityModels.kt`: `GeospatialEntityItem`, `EntitySubmissionItem` (Records), geometries, properties, and collection statuses.
  - `MutationModels.kt`: `UploadMutationItem`, `MutationSyncStatus`, and offline queue records.
  - `ImpactModels.kt`: `ImpactEvent`, `ImpactCoverage` (GeoID-deduplicated counts and area), survey lifecycle states, and `SurveyOutcome`.
  - `SurveyOutcomePrompt.kt`: When to ask "What happened with this data?" again (90 days after closing or the last "Not yet"), the outcome chips' "Not yet" exclusivity, and an ISO 8601 UTC parser.
  - `OrganizationModels.kt`: `OrganizationItem`, `OrganizationMember`, `OrganizationRole`, `OrganizationType` (optional profile type with sentence-case labels), and `ImagerySourceItem`.
  - `Countries.kt`: `Country` and the static ISO 3166-1 alpha-2 list (`Countries.all`, 249 codes with English names) used for an organization's optional country, with code lookup/validation and accent-insensitive name-or-code search for the country picker.
  - `AuthModels.kt`, `SettingsModels.kt`, `LocationModels.kt`, `PlaceModels.kt`, `NavigationModels.kt`, `MapClusterModels.kt`: User profiles, app preferences, GNSS fixes, geocoded places, wayfinding metrics, and map cluster structures.
- **Survey & Form Editor Domain (`editor/`)**:
  - `SurveyEditorDraft.kt`, `SurveyEditorModels.kt`, `FormEditorModels.kt`: Authoring models for survey details, forms, questions, save-to bindings, datasets, sampling designs, and sharing rules.
  - `SurveyEditorDerivation.kt` & `FormImport.kt`: Pure derivation of `SurveyEditorDraft` and `EditorForm` from runtime survey data and XForms XML.
  - `SurveyEditorProjection.kt` & `FormPublishing.kt`: Write-time projection from edited drafts back into runtime `FormPreviewItem`, `MapLayerItem`, `GeospatialEntityItem`, and XForms XML.
  - `FormValidationRules.kt` & `FormDatasetLinks.kt`: Pure validation rules and entity-to-form pre-population mapping logic.
  - `FormConceptLinks.kt`: Links between questions and dictionary concepts: `ground:concept` bind attributes (`ConceptLinkAttributes`), survey-level links keyed by field path (`FormConceptLinkSync`), linking with default-state autofill and choice codes (`ConceptLinking`), and non-blocking link warnings (`ConceptLinkValidator`).
  - `SurveyFormTemplates.kt`: Copying a library Form template into a survey (new Form ID, unique title, linked dataset) and saving a survey Form as a template.

## Role in the Overall Architecture

`domain/model/` is the innermost dependency target in the application:

```
Views / ViewModels / Use Cases / Repositories / Data Sources
                        ↓ all depend on
                Domain Models (domain/model/*)
```

## Dependency Rules

- **Inbound access**: May be imported by any layer (`domain/`, `data/`, `di/`, `ui/`).
- **Outbound dependencies**: Pure Kotlin and `org.groundplatform.v2.core.*` shared primitives only. Enforced by `LayerDependencyGuardrailTest`, `domain/model/` **never** imports `domain/repository/`, `domain/usecase/`, `data/`, `client/`, `di/`, or `ui/`.
