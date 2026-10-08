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

# Top-Level Screen Host

**TL;DR:** The `ui/screen` package contains `AppScreenHost`, the top-level Compose routing and screen composition entry point for the mobile/tablet survey experience.

## Scope & Purpose

While individual feature screens live in their own packages (`ui/onboarding/`, `ui/map/`, `ui/settings/`, `ui/datacollection/`, `ui/navigation/`), `ui/screen/AppScreenHost.kt` coordinates top-level screen transitions within the mobile device frame:

- Collects `StateFlow<*UiState>` streams from the feature ViewModels (`OnboardingViewModel`, `SettingsViewModel`, `SurveyMapViewModel`, `DataCollectionViewModel`).
- Routes between the onboarding/sign-in flow, survey selector, active survey map screen (`MainSurveyScreen`), settings sub-screens, and active data-collection form wizard (`DataCollectionFormScreen`).
- Hosts modal overlays that span across sub-screens (navigation drawer, entity QR code dialog, PDF share sheet, and snackbar notifications).

## Role in the Overall Architecture

`ui/screen/` acts as the root UI router connecting the application shell (`PrototypeAppState`) to stateless feature Composables:

```
Application Shell (PrototypeApp / PrototypeAppState)
        ↓ hosts
Top-Level Screen Router (ui/screen/AppScreenHost)
        ↓ collects StateFlow<*UiState> & delegates to
Stateless Feature Screens (ui/onboarding/*, ui/map/*, ui/datacollection/*, ui/settings/*)
```

## Dependency Rules

- **Inbound access**: Invoked by `PrototypeApp.kt` and top-level UI tests.
- **Outbound dependencies**: Depends on `ui/state/`, `ui/viewmodel/`, `ui/<feature>/`, and `domain/model/`. Enforced by `LayerDependencyGuardrailTest`, `ui/screen/` **never** imports `data/`, `client/`, or `di/`.
