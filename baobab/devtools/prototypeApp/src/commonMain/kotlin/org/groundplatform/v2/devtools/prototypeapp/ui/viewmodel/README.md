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

# ViewModels & User Actions

**TL;DR:** The `ui/viewmodel` package contains feature ViewModels (`*ViewModel`) and user-intent interfaces (`*Actions`) that transform reactive domain streams and ephemeral session state into `StateFlow<*UiState>`.

## Scope & Purpose

Each feature area in `prototypeApp` is backed by a focused ViewModel that implements a corresponding `*Actions` interface:

- `OnboardingViewModel` (`OnboardingActions`): Authentication, Terms of Service acceptance, and initial survey selection.
- `SettingsViewModel` (`SettingsActions`): App preferences, offline basemap management, and storage diagnostics via `EstimateDeviceStorageUseCase`.
- `SurveyMapViewModel` (`SurveyMapActions`): Map viewport, camera follow, basemap/imagery toggles, layer visibility, feature clustering (`ClusterMapFeaturesUseCase`), wayfinding (`ComputeWayfindingNavigationUseCase`), and place search (`SearchPlacesUseCase`).
- `DashboardViewModel` (`DashboardActions`): Web survey list, dashboard split-pane layout, entity/record filtering, survey creation (`CreateSurveyUseCase`), and offline mutation synchronization (`SyncMutationsUseCase`).
- `OrganizationViewModel` (`OrganizationActions`): Organization directory, organization creation (`CreateOrganizationUseCase`), member invitations and role management (`ManageOrganizationMembersUseCase`), and organization imagery sources.
- `SurveyEditorViewModel` (`SurveyEditorActions`) & `FormEditorViewModel` (`FormEditorActions`): Reactive survey draft editing (`SurveyEditorRepository`), form question authoring, live form preview controller, validation, and sample plot generation (`GenerateSamplePlotsUseCase`).
- `DataCollectionViewModel` (`DataCollectionActions`): Form launch (`LaunchFormUseCase`), entity pre-population, interactive web geometry drawing, submission completion (`CompleteFormSubmissionUseCase`), entity/record selection ownership, QR code display, and PDF report export.
- `WorkbenchViewModel` (`WorkbenchActions`): Prototype-only developer tooling (live XForms XML editor/presets, device frame simulator, synthetic site generation, sync-status cycling, and full prototype data reset).

## Role in the Overall Architecture

ViewModels sit between **Compose Views** and the **Domain Layer**:

```
Compose Views (ui/<feature>/*)
   │  ↑ observes StateFlow<*UiState>
   ↓ calls *Actions
Feature ViewModels (ui/viewmodel/*)
   │  ↑ combines Flow<DomainModel> + ephemeral MutableStateFlow<Session>
   ↓ invokes
Domain Use Cases (domain/usecase/*) & Domain Repositories (domain/repository/*)
```

Cross-feature coordination (such as selecting an entity on the map and opening its details in the dashboard or data-collection sheet) happens via one-shot `*Event` flows collected by the application shell (`PrototypeAppState`).

## Dependency Rules

- **Inbound access**: Instantiated in the application shell (`PrototypeAppState`) using dependencies from `di/AppDataHolder`, and passed to Compose screens as `*Actions` alongside collected `*UiState`.
- **Outbound dependencies**: Depends on `ui/state/`, `domain/usecase/`, `domain/repository/`, and `domain/model/`. Enforced by `LayerDependencyGuardrailTest`, `ui/viewmodel/` **never** imports `data.*` (only `di/AppDataHolder` touches `data.*`). Foreground UI infrastructure exemptions (`FormWizardController` and `PdfExportClient`) are documented inline and guarded by `LayerDependencyGuardrailTest`.
