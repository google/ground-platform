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

# UI & Presentation Layer

**TL;DR:** The `ui` package implements the Compose Multiplatform presentation layer using a unidirectional data flow (UDF) MVVM pattern (`Views → ViewModels → Domain`).

## Scope & Purpose

The UI layer renders application state and forwards user intents to ViewModels without containing business logic or direct data-store access. It is organized into horizontal architectural sub-packages and vertical feature screen packages:

- **Architectural Component Sub-packages**:
  - `viewmodel/`: Feature `*ViewModel` state holders and `*Actions` user-intent interfaces (`OnboardingViewModel`, `SettingsViewModel`, `SurveyMapViewModel`, `DashboardViewModel`, `OrganizationViewModel`, `SurveyEditorViewModel`, `FormEditorViewModel`, `DataCollectionViewModel`, `WorkbenchViewModel`).
  - `state/`: Immutable `*UiState` snapshots, one-shot `*Event` sealed interfaces, and documented UI-infrastructure state holders (`WebMapDrawingHost`).
  - `screen/`: Top-level Compose screen host (`AppScreenHost`) that binds the application shell to feature screens.
  - `common/`: Reusable, feature-agnostic Compose widgets and foreground UI platform bridges (file pickers, downloads, media capture, cursors).
- **Per-Feature Screen Packages**:
  - `onboarding/`, `settings/`, `map/`, `dashboard/`, `organization/`, `surveyeditor/`, `formeditor/`, `datacollection/`, `navigation/`, `workbench/`: Stateless Compose screens and feature-scoped visual components that consume `(*UiState, *Actions)`.

## Role in the Overall Architecture

The UI layer follows strict Unidirectional Data Flow:

```
Compose Views (ui/<feature>/*, ui/screen/*)
   │  ↑ collects StateFlow<*UiState>
   ↓ invokes *Actions
Feature ViewModels (ui/viewmodel/*)
   │  ↑ observes Flow<DomainModel>
   ↓ invokes suspend functions
Domain Use Cases & Repositories (domain/usecase/*, domain/repository/*)
```

## Dependency Rules

- **Inbound access**: Entry point invoked by platform `Main` / `PrototypeApp` hosts.
- **Outbound dependencies**:
  - Compose Views (`ui/<feature>/*`, `ui/screen/*`, `ui/common/*`) depend on `ui/state/`, `ui/viewmodel/` (`*Actions` interfaces), and `domain/model/`.
  - Feature ViewModels (`ui/viewmodel/*`) depend on `ui/state/`, `domain/usecase/`, `domain/repository/`, and `domain/model/`.
  - Enforced by `LayerDependencyGuardrailTest`, **no** file in `ui/` may import `data.*`, and **no** Compose View may import `client.*` (with `PdfExportClient` restricted to `DataCollectionViewModel`).
