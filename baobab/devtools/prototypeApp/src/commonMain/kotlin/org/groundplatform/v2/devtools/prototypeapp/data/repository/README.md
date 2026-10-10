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

# Repository Implementations

**TL;DR:** The `data/repository` package contains concrete `*RepositoryImpl` classes that implement the domain repository contracts in `domain/repository/` and mediate between data sources and domain models.

## Scope & Purpose

Each repository implementation owns a cohesive domain aggregate and coordinates `LocalStore` alongside device or remote data sources:

- `SurveyRepositoryImpl`: Reactive queries and mutations for surveys, forms, map layers, geospatial entities, and records/submissions.
- `SurveyEditorRepositoryImpl`: Loads, observes, derives, and transactionally saves `SurveyEditorDraft` changes, projecting published editor drafts back into runtime forms, map layers, and entities.
- `MutationRepositoryImpl`: Manages the local offline mutation queue and synchronization status transitions.
- `ImpactEventRepositoryImpl`: Stores impact events in the `LocalStore`, ignoring duplicate IDs.
- `OrganizationRepositoryImpl`: Manages organization metadata, member rosters, role assignments, and organization-scoped imagery sources.
- `LibraryRepositoryImpl`: Stores one `OrganizationLibrary` per organization in `LocalStore`, refusing changes that break the library ownership and ID rules.
- `AuthRepositoryImpl`: Coordinates sign-in/sign-out flows and active user profile state across `PrototypeAuthDataSource` and `LocalStore`.
- `SettingsRepositoryImpl`: Persists user preferences, offline basemap settings, and device diagnostics state.
- `LocationRepositoryImpl`: Provides device GPS location updates via `DeviceLocationDataSource`.
- `PlaceRepositoryImpl`: Executes geocoding and place search queries via `MapboxPlacesDataSource`.
- `SampleDataRepositoryImpl`: Triggers initial seeding and full prototype state resets via `SampleDataSeeder`.
- `LocalStoreTransactionRunner`: Implements `TransactionRunner` to execute multi-repository domain use cases inside a single atomic `LocalStore` transaction.

## Role in the Overall Architecture

Repository implementations bridge the **Domain** layer and the **Data Sources** layer:

```
Use Cases (domain/usecase/*) & ViewModels (ui/viewmodel/*)
        ↓ depend on interfaces in
Domain Repository Contracts (domain/repository/*)
        ↑ implemented by
Repository Implementations (data/repository/*)
        ↓ orchestrate
Data Sources (data/datasource/*)
```

## Dependency Rules

- **Inbound access**: Instantiated exclusively in `di/AppDataHolder` and exposed to the rest of the application via their `domain/repository/` interfaces.
- **Outbound dependencies**: May depend on `domain/model/`, `domain/repository/`, `data/datasource/`, and `data/seed/`. Enforced by `LayerDependencyGuardrailTest`, `data/repository/` **never** imports `di/` or `ui/`.
