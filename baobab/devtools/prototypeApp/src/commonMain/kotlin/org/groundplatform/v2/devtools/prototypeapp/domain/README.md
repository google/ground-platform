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

# Domain Layer

**TL;DR:** The `domain` package is the innermost core of the Ground 2.0 `prototypeApp` Clean Architecture, containing pure Kotlin business models, repository contracts, and reusable use cases with zero dependencies on UI, storage, or platform frameworks.

## Scope & Purpose

The Domain layer defines *what* the application does independently of *how* data is stored or rendered. It is divided into three sub-packages:

- `model/`: Immutable domain entities, value objects, geometry/measurement types, and pure domain transformation rules (including `model/editor/` for Survey and Form authoring models, XForms import/export, derivation, projection, and validation).
- `repository/`: Abstract repository interfaces (`SurveyRepository`, `SurveyEditorRepository`, `MutationRepository`, `OrganizationRepository`, `AuthRepository`, `SettingsRepository`, `LocationRepository`, `PlaceRepository`, `SampleDataRepository`, `TransactionRunner`) that define reactive `Flow` queries and `suspend` mutation contracts.
- `usecase/`: Reusable business logic, multi-repository transactional workflows, and domain algorithms (`CompleteFormSubmissionUseCase`, `LaunchFormUseCase`, `GenerateSamplePlotsUseCase`, `ClusterMapFeaturesUseCase`, `ComputeWayfindingNavigationUseCase`, `CreateSurveyUseCase`, etc.).

## Role in the Overall Architecture

In the concentric Clean Architecture model, `domain/` occupies the center ring:

```
UI Layer (ui/viewmodel/*, ui/state/*, ui/<feature>/*)
        ↓ depends on
Domain Layer (domain/usecase/* → domain/repository/* → domain/model/*)
        ↑ implemented by
Data Layer (data/repository/*)
```

Both the **UI** layer and the **Data** layer depend inward on `domain/`. Repository interfaces live here so that ViewModels and Use Cases can orchestrate data access without coupling to `data/`.

## Dependency Rules

- **Inbound access**: Consumed by `ui/`, `data/`, and `di/`.
- **Outbound dependencies**: Pure Kotlin (`kotlinx.coroutines`, `org.groundplatform.v2.core.*` shared domain primitives). Enforced by `LayerDependencyGuardrailTest`, `domain/` **never** imports `data/`, `client/`, `di/`, `ui/`, or Compose UI libraries.
