<!--
  Copyright 2026 The Ground Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Mobile UI & ViewModels (`org.groundplatform.v2.mobile.ui`)

Contains Compose Multiplatform screens, ViewModels, and navigation shared
identically between `androidApp` and `iosApp`, including:
- Map home screen and layer drawer split into two self-describing categories:
  - **Data collection sites** (Geospatial Entity Lists / spatial master tables):
    tapping a site pin opens its current status and launches site-first actions
    (e.g., `[ + Inspect Site ]`, `[ + Update Info ]`).
  - **Form Submissions** (completed submission GPS instances for `geopoint`,
    `geotrace`, and `geoshape` questions): displays historical coverage and
    completed visits/logs visually distinct from active sites.
- Survey selector and offline area download screens.
- Mobile form data collection host wrapping `MobileFormRunner` from `shared/ui`.
