<!--
 Copyright 2026 The Collect One Authors.

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

# Dependency Injection & Wiring Layer

**TL;DR:** The `di` package is the composition root of both clients: the Koin modules that build data sources, repositories, use cases, and ViewModels, and expose domain-typed contracts to the UI layer. See "Dependency Injection with Koin" in `docs/technical/client/architecture.md`.

The package is split across modules (see `docs/technical/client/modules.md`): `shared/app/data` holds the data-layer graph (`AppConfig`, `UserDataServiceConfig`, `platformModule`, `dataModule`, `UserDataSyncController`, `UserDataSyncState`, `BackendErrorMonitor`); each feature module holds the Koin module of its ViewModel (`mapModule`, `dataCollectionModule`, `dashboardModule`, `onboardingModule`, `settingsModule`, `listSearchModule`); this module holds `domainModule`, `appModules`, and `AppDataHolder`.

## Scope & Purpose

- `AppConfig`: Which user data service the graph talks to and how it syncs.
- `platformModule(...)`: Platform services each host supplies (`AuthClient`, `PushSubscriptionClient`, `PdfExportClient`, and optionally the device's local database, `SqlDriverFactory`, with the `LocalDatabaseConnection` the stores share).
- **Stopping the graph** (`stopKoin` or closing a `koinApplication`) runs the `onClose` hooks: the app scope is cancelled, stopping sync and background writes, and the local database is closed.
- `dataModule(config)`: The app scope, the shared `LocalStore` (written through to the local database when the platform module provides one, else in memory), the user data service client and sync engine, every `*RepositoryImpl`, `UserDataSyncController`, and `BackendErrorMonitor`, as app-wide singletons.
- `domainModule`: The use cases both clients use, as factories.
- The feature modules' Koin modules: The ViewModels both clients use, one feature each, as `viewModel { }` definitions.
- `appModules(config, platform)`: The shared graph. `shared/web` (`webModules`) and `shared/mobile` (`mobileModules`) add their own modules.
- `AppDataHolder`: A migration facade over the graph for the session states that still read their dependencies from one object. It wires nothing, restores the signed-in session and then starts the initial sync, and exposes the combined `StateFlow<AppData>` snapshot. Don't add members to it.
- `UserDataSyncController`: Runs the initial sync from the user data service (or seeds the sample data without one), retries it with exponential backoff, starts the sync engine and connects it to the platform lifecycle, and relays which views are on screen to it.
- `BackendErrorMonitor`: Picks the backend error the UI shows (rejected edits first, then transient outbox and pull errors while online) and handles dismissing and retrying it.
- `AppData`: Immutable aggregate of core reactive domain streams observed across the application.

## Role in the Overall Architecture

`di/` is the **Composition Root** that connects the outer implementations (`data/`, `client/`) to the inner abstractions (`domain/`) consumed by the feature ViewModels (`ui/<feature>/`):

```
UI Shell & ViewModels (ui/<feature>/*ViewModel)
        ↓ receive domain interfaces & use cases through their constructors from
Composition Root (di/*Module)
        ↓ instantiates & wires
Repositories (data/repository/*) + Use Cases (domain/usecase/*) + Clients (client/*)
```

## Dependency Rules

- **Inbound access**: Hosts start Koin with these modules; session states and tests use `AppDataHolder` while they migrate. Unit tests construct classes directly with fakes, without Koin; `AppModulesTest` checks that the graph can create every definition.
- **Outbound dependencies**: Enforced by `LayerDependencyArchitectureTest`, `di/` is the **only** package outside `data/` permitted to import `data.*` (`LocalStore`, `*RepositoryImpl`, `SampleDataSeeder`). It exposes those dependencies via `domain/repository/` and `domain/usecase/` types so that the feature ViewModels (`ui/<feature>/`) never imports `data.*`. Of `ui/`, `di/` imports only the ViewModels it defines.
