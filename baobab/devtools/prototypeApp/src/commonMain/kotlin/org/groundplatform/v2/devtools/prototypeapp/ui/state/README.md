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

# UI State & Events

**TL;DR:** The `ui/state` package defines the immutable `*UiState` data classes rendered by Compose screens, the one-shot `*Event` sealed hierarchies emitted by ViewModels, and scoped UI-infrastructure state holders.

## Scope & Purpose

In Unidirectional Data Flow (UDF), each feature ViewModel exposes a single `StateFlow<*UiState>` representing everything its screen needs to render at a given instant, plus an `events: Flow<*Event>` for transient cross-feature effects:

- `OnboardingUiState.kt`: Sign-in status, Terms of Service acceptance, survey picker list, and onboarding step progression.
- `SettingsUiState.kt`: User preferences, measurement units, theme selection, offline basemap toggles, and storage usage breakdowns.
- `SurveyMapUiState.kt`: Map viewport state, camera follow mode, basemap/imagery selection, visible map layers and entities, map clusters, active wayfinding banner state, and GNSS status chip.
- `DashboardUiState.kt`: Web dashboard split-pane layout, entity/record filter tabs, upload status filters, collapsed dataset state, and mutation sync counts.
- `OrganizationUiState.kt`: Organization list, selected organization details, member roster, role editor dialogs, and organization-scoped imagery sources.
- `SurveyEditorUiState.kt`: Active `SurveyEditorDraft`, dirty/saving indicators, selected editor section, validation issues, sampling design state, and nested `FormEditorUiState`.
- `DataCollectionUiState.kt`: Active form wizard state, eligible entities for form launch, selected entity/record details, active PDF report sheet, QR code modal state, and `WebMapDrawingHost` (transient interactive vertex drawing state for web form geometry questions).
- `WorkbenchUiState.kt`: Prototype workbench tooling state (XForms XML editor, parse errors, device simulator frame dimensions/orientation, and debug toggles).

## Role in the Overall Architecture

`ui/state/` forms the contract between **Feature ViewModels** and **Compose Views**:

```
Feature ViewModels (ui/viewmodel/*)
        ↓ produces StateFlow<*UiState> & Flow<*Event>
UI State & Events (ui/state/*)
        ↓ consumed as immutable parameters by
Compose Screens (ui/<feature>/*, ui/screen/*)
```

## Dependency Rules

- **Inbound access**: Constructed/updated by `ui/viewmodel/*` and read by `ui/<feature>/*` and `ui/screen/*`.
- **Outbound dependencies**: Depends on `domain/model/` and `org.groundplatform.v2.core.*` form primitives. Enforced by `LayerDependencyGuardrailTest`, `ui/state/` **never** imports `data/`, `client/`, `di/`, `domain/repository/`, or `domain/usecase/`.
