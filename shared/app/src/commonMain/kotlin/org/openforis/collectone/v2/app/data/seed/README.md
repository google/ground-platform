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

# Initial Data Seeding

**TL;DR:** `SampleDataSeeder` fills `LocalStore` with the initial state of the
[user data service](../../../../../../../../../../../server/userDataService/README.md)
on first launch, and again on a reset to sample data. The sample data itself
no longer ships in the app.

## Scope & Purpose

- `SampleDataSeeder`: Pulls the service's state through `GrpcSyncDataSource`
  when the store is empty or `SEED_VERSION` advances, and on an explicit reset
  (`SampleDataRepository.resetToSampleData()`). With no service configured, it
  leaves the store as is (the apps don't start without one; tests seed stores
  directly).
- The sample datasets (`Sample*Data`, `GlobalLibrarySeedData`,
  `SurveyEditorSampleData`) are test fixtures in `shared/app/testing`, in this
  same package. `UserDataSeedGenerator` turns them into the service's seed, and
  `writeSampleData()` writes them straight into a store for tests.

## Role in the Overall Architecture

```
SampleDataRepositoryImpl (data/repository/)
        ↓ invokes
SampleDataSeeder (data/seed/)
        ↓ pulls through
GrpcSyncDataSource (data/datasource/remote/) ← user data service
        ↓ writes to
LocalStore (data/datasource/local/store/)
```

Once seeded, all application features read and mutate data exclusively through
`LocalStore` via repositories.

## Dependency Rules

- **Inbound access**: Only `SampleDataRepositoryImpl` uses `SampleDataSeeder`;
  `SEED_VERSION` and `DEFAULT_ACTIVE_SURVEY_ID` are also read by tests.
- **Outbound dependencies**: Depends only on `data/datasource/local/store/` and
  `data/datasource/remote/`. Never imports `di/` or `ui/`.
