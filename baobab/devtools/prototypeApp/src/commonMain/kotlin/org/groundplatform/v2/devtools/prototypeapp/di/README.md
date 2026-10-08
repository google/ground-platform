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

# Dependency Injection & Wiring Layer

**TL;DR:** The `di` package acts as the composition root for `prototypeApp`, instantiating data sources, concrete repositories, and domain use cases inside `AppDataHolder` and exposing domain-typed contracts to the UI layer.

## Scope & Purpose

Rather than using a reflection-heavy DI framework, `prototypeApp` uses manual constructor injection centralized in `di/`:

- `AppDataHolder`: Owns the shared `LocalStore` instance, constructs all `*RepositoryImpl` and data source instances, wires domain use cases (`CompleteFormSubmissionUseCase`, `SyncMutationsUseCase`, `ResolveFormDefForLaunchUseCase`, `EstimateDeviceStorageUseCase`, `SearchPlacesUseCase`, etc.), runs initial `SampleDataSeeder` population, and exposes a combined `StateFlow<AppData>` snapshot alongside domain repository interfaces.
- `AppData`: Immutable aggregate of core reactive domain streams observed across the application.

## Role in the Overall Architecture

`di/` is the **Composition Root** that connects the outer implementations (`data/`, `client/`) to the inner abstractions (`domain/`) consumed by `ui/viewmodel/`:

```
UI Shell & ViewModels (ui/viewmodel/*)
        ↓ receives domain interfaces & use cases from
Composition Root (di/AppDataHolder)
        ↓ instantiates & wires
Repositories (data/repository/*) + Use Cases (domain/usecase/*) + Clients (client/*)
```

## Dependency Rules

- **Inbound access**: Instantiated by the application shell (`PrototypeAppState`) and test harnesses.
- **Outbound dependencies**: Enforced by `LayerDependencyGuardrailTest`, `di/` is the **only** package outside `data/` permitted to import `data.*` (`LocalStore`, `*RepositoryImpl`, `SampleDataSeeder`). It exposes those dependencies via `domain/repository/` and `domain/usecase/` types so that `ui/viewmodel/` never imports `data.*`. `di/` never imports `ui/`.
