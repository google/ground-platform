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

# Data Layer

**TL;DR:** The `data` package implements the domain repository contracts, coordinates local and remote data sources, and manages initial sample data seeding for the Ground 2.0 `prototypeApp`.

## Scope & Purpose

The Data layer is responsible for persisting, retrieving, synchronizing, and seeding application data while hiding storage and transport details behind the interfaces defined in `domain/repository/`. It is organized into three architectural sub-components:

- `datasource/`: Encapsulates storage engines (`LocalStore` / `InMemoryLocalStore`), device data providers (`DeviceLocationDataSource`, `PrototypeAuthDataSource`), and remote API data sources (`MapboxPlacesDataSource`).
- `repository/`: Concrete `*RepositoryImpl` implementations of `domain/repository/` interfaces and `LocalStoreTransactionRunner`.
- `seed/`: Initial sample datasets (`PrototypeFake*Data`, `SurveyEditorSampleData`) and `SampleDataSeeder`, which populates `LocalStore` on initial startup or prototype reset.

## Role in the Overall Architecture

The Data layer sits between the **Domain** ring and the **System & Framework (`client/`)** ring, fulfilling the Dependency Inversion Principle:

```
Domain Repositories (domain/repository/*)
        ↑ implements
Repository Implementations (data/repository/*)
        ↓ delegates to
Data Sources (data/datasource/*) & Seeder (data/seed/*)
        ↓ wraps
System & Framework Clients (client/*)
```

## Dependency Rules

- **Inbound access**: Outside of `data/` itself, only the dependency injection container (`di/AppDataHolder`) may import `data.*` classes. Neither `domain/` nor `ui/` (including ViewModels and Composables) may depend on `data.*`.
- **Outbound dependencies**: `data/` depends on `domain/model/` and `domain/repository/` (to implement domain contracts) and `client/` (to access platform APIs). Enforced by `LayerDependencyGuardrailTest`, `data/` **never** imports `di/` or `ui/`.
