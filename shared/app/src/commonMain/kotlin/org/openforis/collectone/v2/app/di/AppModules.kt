/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The dependency graph both clients share, by layer: [platform] services and the [dataModule]
 * (store, sync, and repositories) from `shared/app/data`, the [AppDataHolder] facade, the
 * [domainModule] (use cases), and the ViewModels both clients use, one Koin module per feature
 * module. Each client adds its own modules (`webModule` in `shared/web`, `workbenchModule` in the
 * workbench).
 *
 * Views get ViewModels with `koinViewModel()`, which keeps one instance per `ViewModelStoreOwner`
 * (the Android activity, or the browser window) and clears it with its owner. ViewModels that keep
 * UI state across Android process death take the `SavedStateHandle` `koinViewModel()` provides.
 */
fun appModules(config: AppConfig, platform: Module = platformModule()): List<Module> =
  listOf(
    platform,
    dataModule(config),
    appDataHolderModule(),
    domainModule,
    mapModule,
    dataCollectionModule,
    dashboardModule,
    onboardingModule,
    listSearchModule,
    settingsModule,
  )

/**
 * The [AppDataHolder] facade, created with the graph: it starts the initial sync. A new module per
 * graph, as a module keeps the instances of its singletons.
 */
private fun appDataHolderModule(): Module = module {
  single(createdAtStart = true) { AppDataHolder(getKoin()) }
}
