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
package org.openforis.collectone.v2.web.di

import kotlin.test.Test
import kotlin.test.assertIs
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.LocalStore
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.createAppDataHolder
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.testing.assertEveryDefinitionResolves
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorViewModel

/** The web console's Koin graph ([webModules]) can create every definition. */
class WebModulesTest {

  @Test
  fun everyDefinitionResolves() {
    val client = SeedUserDataService().client()
    val config = AppConfig(userDataClient = { client })
    // The parameters the web session passes: its Survey editor and its survey map's scene callback.
    val surveyEditor =
      createAppDataHolder(overrides = module { includes(webDomainModule, webViewModelModule) })
        .newViewModel<SurveyEditorViewModel>()
    val showSceneOnMap: (ImagerySource?) -> Unit = {}

    assertEveryDefinitionResolves(webModules(config), surveyEditor, showSceneOnMap)
  }

  @Test
  fun webConsole_keepsTheStoreInMemory() {
    // The web console passes no local database: its outbox lives only as long as the page.
    val client = SeedUserDataService().client()
    val koin = koinApplication { modules(webModules(AppConfig(userDataClient = { client }))) }.koin

    assertIs<InMemoryLocalStore>(koin.get<LocalStore>())
  }
}
