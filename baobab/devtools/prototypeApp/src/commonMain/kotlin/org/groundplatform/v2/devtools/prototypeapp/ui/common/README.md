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

# Shared UI Components & Foreground Platform Bridges

**TL;DR:** The `ui/common` package contains reusable, feature-agnostic Compose Multiplatform widgets and foreground UI platform bridges shared across multiple screens.

## Scope & Purpose

Components in `ui/common/` have no coupling to a single feature's `*UiState` or `*ViewModel` and fall into two categories:

- **Design System & Visual Widgets**:
  - `GroundChips.kt`, `GeoIdText.kt`, `GoogleLogo.kt`: Shared typography, status chips, formatted Geo ID badges, and branding elements.
  - `HorizontalScrollWithMouseDrag.kt`: Pointer-input modifier enabling click-and-drag horizontal scrolling on desktop/web targets.
- **Foreground UI Platform Bridges (`expect` / `actual`)**:
  - `PlatformFileDownload.kt` & `PlatformTextFilePicker.kt`: Browser/JVM file save and open dialogs triggered directly by user gestures (e.g., GeoJSON layer import or XForms XML download).
  - `PlatformResizeCursor.kt`: Split-pane resize pointer cursor styling on desktop/web.
  - `PrototypeMediaCapture.kt`: Interactive camera/photo capture UI hook for form photo questions.

## Role in the Overall Architecture

`ui/common/` provides foundational building blocks to the per-feature UI packages (`ui/map/`, `ui/dashboard/`, `ui/surveyeditor/`, `ui/datacollection/`, etc.):

```
Feature Screens (ui/<feature>/*, ui/screen/*)
        ↓ composes
Shared UI Components (ui/common/*)
```

## Dependency Rules

- **Inbound access**: Imported by any feature screen package in `ui/`.
- **Outbound dependencies**: May depend on Compose runtime/UI libraries and `domain/model/`. Enforced by `LayerDependencyGuardrailTest`, `ui/common/` **never** imports `data/`, `client/`, `di/`, `domain/repository/`, or `domain/usecase/`.
