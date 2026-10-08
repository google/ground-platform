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

# Sample Data & Seeding

**TL;DR:** The `data/seed` package encapsulates all built-in prototype fixture datasets and `SampleDataSeeder`, which populates `LocalStore` on initial app launch or prototype reset.

## Scope & Purpose

To make `prototypeApp` self-contained for UX evaluation and automated testing without requiring a live backend, `data/seed/` defines realistic initial datasets and seeding logic:

- `SampleDataSeeder`: Populates `LocalStore` atomically at startup (when empty or when `SEED_VERSION` advances) and on explicit user reset (`SampleDataRepository.reseed()`).
- `PrototypeFakeSurveysData`: Seed surveys, form metadata, and built-in XForms XML definitions.
- `PrototypeFakeMapLayersData`: Seed map layers and data tables per survey.
- `PrototypeFakeEntitiesData`: Seed geospatial entities, geometries, properties, and historical form submissions/records.
- `PrototypeFakeMutationsData`: Seed offline upload/mutation queue entries.
- `PrototypeFakeOrganizationsData`: Seed organizations, member rosters, and imagery sources.
- `SurveyEditorSampleData`: Supplemental editor-only fixtures (collaborator rosters, sample design configurations, and survey area polygons) merged into derived drafts at seed time.

## Role in the Overall Architecture

`data/seed/` is an internal sub-component of the **Data** layer that writes initial state into `LocalStore`:

```
SampleDataRepositoryImpl (data/repository/)
        ↓ invokes
SampleDataSeeder (data/seed/)
        ↓ reads fixtures & writes to
LocalStore (data/datasource/local/store/)
```

Once seeded, all application features read and mutate data exclusively through `LocalStore` via repositories—never by reading `PrototypeFake*Data` constants directly.

## Dependency Rules

- **Inbound access**: Enforced by `SampleDataGuardrailTest`, **only** files inside `data/seed/` (and `SampleDataRepositoryImpl` / `AppDataHolder` referencing `SampleDataSeeder`) may reference `PrototypeFake*Data` or `SurveyEditorSamples`. UI components, ViewModels, Use Cases, and other Repositories are strictly forbidden from reading seed fixtures directly.
- **Outbound dependencies**: Depends only on `domain/model/` and `data/datasource/local/store/`. Never imports `di/` or `ui/`.
