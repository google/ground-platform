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
package org.openforis.collectone.v2.mobile.di

import kotlin.test.Test
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.di.AppConfig
import org.openforis.collectone.v2.app.di.appModules
import org.openforis.collectone.v2.app.testing.assertEveryDefinitionResolves

/** The mobile app's Koin graph can create every definition. */
class MobileModulesTest {

  @Test
  fun everyDefinitionResolves() {
    // The graph of mobileModules, with an in-memory service in place of the remote one.
    val client = SeedUserDataService().client()
    val config =
      AppConfig(
        syncDownloadedSurveysOnly = true,
        userDataClient = { client },
        enableSyncEngine = true,
      )

    assertEveryDefinitionResolves(appModules(config) + mobileViewModelModule)
  }
}
