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

# Data Sources

**TL;DR:** The `data/datasource` package provides focused adapters for local persistence, device sensors/auth, and remote network endpoints, shielding repositories from low-level storage and `client/` details.

## Scope & Purpose

Data sources handle single-responsibility I/O operations across three categories:

- `local/store/` (`LocalStore`, `InMemoryLocalStore`): The reactive in-memory persistence engine that acts as the single source of truth for surveys, forms, map layers, geospatial entities, submissions, mutations, organizations, organization libraries, auth state, and app settings. Supports atomic multi-collection writes via `LocalStore.Transaction`.
- `local/LibraryProtoMapper`: Maps organization library domain models to and from their `groundplatform.v2.library` protos (`LibraryBundle`), for seed files, imports, exports, and sync.
- `device/` (`DeviceLocationDataSource`, `PrototypeAuthDataSource`): Adapters that bridge device-level `client/location/` and `client/auth/` implementations into structured data operations.
- `remote/` (`MapboxPlacesDataSource`): Remote network data source for querying geocoding and place search endpoints.

## Role in the Overall Architecture

Data sources sit immediately below `data/repository/` and above `client/`:

```
Repository Implementations (data/repository/*)
        ↓ reads / mutates
Data Sources (data/datasource/local, device, remote)
        ↓ invokes
Platform & Network Clients (client/*)
```

Repositories never invoke `client/` APIs directly; instead, they coordinate one or more data sources and write authoritative state into `LocalStore`.

## Dependency Rules

- **Inbound access**: Consumed by `data/repository/*`, `data/seed/SampleDataSeeder`, and wired in `di/AppDataHolder`.
- **Outbound dependencies**: May import `domain/model/` and `client/*`. Enforced by `LayerDependencyGuardrailTest`, `data/datasource/` **never** imports `di/` or `ui/`.
