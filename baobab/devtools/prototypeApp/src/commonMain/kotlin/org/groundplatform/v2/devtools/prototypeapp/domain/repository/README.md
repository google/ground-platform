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

# Domain Repository Contracts

**TL;DR:** The `domain/repository` package defines the pure Kotlin interfaces through which Use Cases and ViewModels observe and mutate application state.

## Scope & Purpose

Following the Dependency Inversion Principle, repository contracts are owned by the Domain layer and implemented by the Data layer (`data/repository/`). Each interface exposes cold/hot Kotlin `Flow` streams for reactive state observation and `suspend` functions for state mutations:

- `SurveyRepository`: Observe and mutate surveys, active survey selection, forms, map layers, geospatial entities, and records/submissions.
- `SurveyEditorRepository`: Load, observe (`observeDraft`), and save (`saveDraft`) `SurveyEditorDraft` instances with automatic runtime projection.
- `MutationRepository`: Observe and update the offline upload/mutation queue.
- `OrganizationRepository`: Observe and mutate organizations, members, roles, and imagery sources.
- `AuthRepository`: Observe authentication state and trigger sign-in/sign-out transitions.
- `SettingsRepository`: Observe and update user preferences, units, theme, and offline basemap settings.
- `LocationRepository`: Query and update device GNSS/GPS location state.
- `PlaceRepository`: Search geocoded places for map navigation and survey area selection.
- `SampleDataRepository`: Seed or reset the prototype data store.
- `TransactionRunner`: Execute multi-repository writes atomically within a single transaction.

## Role in the Overall Architecture

`domain/repository/` decouples business logic and presentation from persistence details:

```
ViewModels (ui/viewmodel/*) & Use Cases (domain/usecase/*)
        ↓ call interfaces in
Domain Repository Contracts (domain/repository/*)
        ↑ implemented by
Repository Implementations (data/repository/*)
```

Because ViewModels and Use Cases depend only on these interfaces, unit tests in `commonTest` can inject lightweight fake repositories or in-memory test stores without spinning up platform clients.

## Dependency Rules

- **Inbound access**: Consumed by `domain/usecase/`, `ui/viewmodel/`, `di/`, and implemented by `data/repository/`.
- **Outbound dependencies**: Depends only on `domain/model/` and `kotlinx.coroutines.flow.Flow`. Enforced by `LayerDependencyGuardrailTest`, `domain/repository/` **never** imports `data/`, `client/`, `di/`, or `ui/`.
