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


# UI & Presentation Layer

**TL;DR:** The `ui` packages implement the Compose Multiplatform presentation layer both clients share, using a unidirectional data flow (UDF) MVVM pattern (`Views → ViewModels → Domain`). They are split across Gradle modules by feature (see [`docs/technical/client/modules.md`](../../../../../../../../../docs/technical/client/modules.md)).

## Scope & Purpose

The UI layer renders application state and forwards user intents to ViewModels without containing business logic or direct data-store access.

- **Feature packages**, each in its own module: `map/` (`:shared:app:map`), `datacollection/` (`:shared:app:datacollection`), `dashboard/` (`:shared:app:dashboard`), `onboarding/` (`:shared:app:onboarding`), `settings/` (`:shared:app:settings`), and `listsearch/` (`:shared:app:listsearch`). Each holds its feature's `*ViewModel` and `*Actions` interface, its immutable `*UiState` and one-shot `*Event`s, and its stateless Compose views that consume `(*UiState, *Actions)`.
- **Session shell** (`session/`, this module): `SessionCoordinator`, `SurveyRecordsUiStates`, and the other pieces that wire one client session's features together, plus the session's banners and developer sign-in dialog. `AppViewModels` (this package) groups the session's ViewModels.
- **Shared presentation code** (`:shared:app:ui-common`): `common/` (feature-agnostic widgets and foreground platform bridges), `text/` (domain text mappers), `navigation/` (navigation models), and `viewmodel/` (ViewModel helpers).

## Role in the Overall Architecture

```
Compose Views (ui/<feature>/*)
   │  ↑ collects StateFlow<*UiState>
   ↓ invokes *Actions
Feature ViewModels (ui/<feature>/*ViewModel)
   │  ↑ observes Flow<DomainModel>
   ↓ invokes suspend functions
Domain Use Cases & Repositories (domain/usecase/*, domain/repository/*)
```

Cross-feature coordination (such as selecting an entity on the map and opening its details) happens through one-shot `*Event` flows collected by `session/SessionCoordinator`, which tells the client shell (`MobileAppState` or `WebAppState`, through `SessionShell`) what to show.

## Dependency Rules

- **Inbound access**: Entry point invoked by the client shells in `shared/mobile` and `shared/web`.
- **Outbound dependencies**:
  - Compose views depend on their feature's state and `*Actions`, on `ui/common/`, `ui/text/`, and `ui/navigation/`, and on `domain/model/`. A feature module may build on a lower feature (data collection builds on the map), never on a higher one or on the session shell.
  - Feature ViewModels depend on their feature's state, `domain/usecase/`, `domain/repository/`, and `domain/model/`.
  - Enforced by the module graph (`ModuleGraphTest`) and `LayerDependencyArchitectureTest`: **no** file in `ui/` may import `data.*`, and **no** Compose view may import `client.*`.
