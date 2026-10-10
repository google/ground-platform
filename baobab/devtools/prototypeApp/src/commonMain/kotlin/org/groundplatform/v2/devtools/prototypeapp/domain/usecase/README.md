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

# Domain Use Cases

**TL;DR:** The `domain/usecase` package encapsulates reusable business workflows, multi-repository transactional orchestration, and complex domain calculations.

## Scope & Purpose

While ViewModels may call single repository methods directly for simple CRUD or toggle operations, any business rule that coordinates multiple repositories, requires atomic transactions, or performs non-trivial domain computation belongs in a dedicated Use Case:

- **Data Collection & Form Workflows**:
  - `LaunchFormUseCase` & `ResolveFormDefForLaunchUseCase`: Resolves the active `FormDef` (custom workbench XML, published survey form XML, or fallback preset) and computes initial entity pre-population values.
  - `CompleteFormSubmissionUseCase`: Extracts geometries and properties from completed form answers and atomically writes the new record/submission, updates entity status/geometry, and appends an upload mutation inside `TransactionRunner`.
- **Survey & Organization Authoring**:
  - `CreateSurveyUseCase`: Constructs and persists a new survey along with its initial forms and map layers.
  - `CreateOrganizationUseCase` & `ManageOrganizationMembersUseCase`: Validates and executes organization creation (including the optional type and ISO 3166-1 alpha-2 country code, refusing unknown codes), member invitations, and role updates.
  - `ManageLibraryUseCase`, `ResolveLibraryUseCase` & `SearchConceptsUseCase`: Validate and apply organization library edits (concepts, templates, Purpose Packs, hidden global entries), resolve a survey's library (its organization's entries, then global ones), and search concepts across languages with accent folding and typo tolerance.
  - `GenerateSamplePlotsUseCase`: Computes systematic grid or random sample plot geometries within a survey area polygon.
- **Map, Wayfinding & Synchronization**:
  - `ClusterMapFeaturesUseCase`: Groups nearby geospatial entities into zoom-dependent map clusters.
  - `ComputeWayfindingNavigationUseCase`: Calculates bearing, distance, and step progression from user GNSS coordinates to a target entity.
  - `SearchPlacesUseCase`: Normalizes and executes geocoding queries against `PlaceRepository`.
  - `SyncMutationsUseCase`: Transitions pending offline mutations through upload synchronization states.
  - `RecordImpactEventUseCase`: Records a "data was used" event with the survey's organization, purposes, and actor; offline events upload with `SyncMutationsUseCase`.
  - `SurveyLifecycleUseCase`: Closes, archives, and reopens surveys (recording `SURVEY_CLOSED` with GeoID-deduplicated coverage when a survey stops collecting data) and stores the "What happened with this data?" answer as its `SurveyOutcome`.
  - `ExportSurveyDataUseCase`: GeoJSON and export profile (EUDR GeoJSON) exports mapped through concept links, recorded as export events.
- **Diagnostics & Prototype Utilities**:
  - `EstimateDeviceStorageUseCase`: Computes storage footprints for surveys, offline basemaps, and pending media.
  - `GeneratePrototypeRandomSitesUseCase`: Generates synthetic geospatial entities for prototype stress-testing.

## Role in the Overall Architecture

Use Cases sit between **ViewModels** and **Domain Repositories**:

```
Feature ViewModels (ui/viewmodel/*)
        ↓ invokes
Domain Use Cases (domain/usecase/*)
        ↓ orchestrates
Domain Repositories (domain/repository/*) & Domain Models (domain/model/*)
```

## Dependency Rules

- **Inbound access**: Wired in `di/AppDataHolder` and invoked by `ui/viewmodel/*`.
- **Outbound dependencies**: Depends only on `domain/model/` and `domain/repository/`. Enforced by `LayerDependencyGuardrailTest`, `domain/usecase/` **never** imports `data/`, `client/`, `di/`, or `ui/`.
