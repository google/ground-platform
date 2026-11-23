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


# ViewModel Helpers

**TL;DR:** The `ui/viewmodel` package (in `:shared:app:ui-common`) holds the helpers every feature ViewModel uses: `viewModelScopeOn` and `ViewModelDispatcher` (the coroutine scope and dispatcher a ViewModel runs on, injectable in tests) and `saveInto` (keeping part of a ViewModel's state in its `SavedStateHandle` across Android process death).

## Where the ViewModels Live

Each feature's ViewModel sits in its feature's package, next to its UI state and views (see [`docs/technical/client/modules.md`](../../../../../../../../../../../docs/technical/client/modules.md)):

| Feature | Package | Module |
| :--- | :--- | :--- |
| Survey map (`SurveyMapViewModel`, `SurveyMapActions`) | `ui/map/` | `:shared:app:map` |
| Data collection (`DataCollectionViewModel`, `DataCollectionActions`) | `ui/datacollection/` | `:shared:app:datacollection` |
| Dashboard (`DashboardViewModel`, `DashboardActions`) | `ui/dashboard/` | `:shared:app:dashboard` |
| Onboarding (`OnboardingViewModel`, `OnboardingActions`) | `ui/onboarding/` | `:shared:app:onboarding` |
| Settings (`SettingsViewModel`, `SettingsActions`) | `ui/settings/` | `:shared:app:settings` |
| List search (`ListSearchViewModel`, `ListSearchActions`) | `ui/listsearch/` | `:shared:app:listsearch` |

The web console's and the workbench's own ViewModels live in `shared/web` and `mobileWebApp/workbench`.

## Dependency Rules

- **Inbound access**: Used by the feature ViewModels of every presentation module.
- **Outbound dependencies**: AndroidX lifecycle (`ViewModel`, `SavedStateHandle`) and coroutines only.
