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
package org.openforis.collectone.v2.app.ui.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.ui.AppViewModels

/** The record surfaces' state derived from the session's ViewModels ([SurveyRecordsUiStates]). */
class SurveyRecordsUiStatesTest {
  private val holder = sampleDataHolder()
  private val viewModels = AppViewModels.create(holder)

  private fun states(isAlwaysOnline: Boolean = false) =
    surveyRecordsUiStatesOf(viewModels, holder.appData.value, notice = null, isAlwaysOnline)

  private val entityWithSubmissions
    get() = viewModels.dataCollection.uiState.value.entities.first { it.submissions.isNotEmpty() }

  @Test
  fun theWebConsole_isAlwaysOnline() {
    assertTrue(states(isAlwaysOnline = true).isOnline)
  }

  @Test
  fun submissionsOfAFormTheSurveyNoLongerHas_areGroupedUnderTheirOwnFormTitle() {
    val entity = entityWithSubmissions
    val orphan =
      entity.submissions.first().copy(formId = "deleted-form", formTitle = "Old tree census")

    val group = states().submissionGroupsOf(entity.copy(submissions = listOf(orphan))).single()

    assertEquals("deleted-form", group.form.id)
    assertEquals("Old tree census", group.form.title)
    assertEquals(entity.datasetId, group.form.targetDatasetId)
    assertEquals(listOf(orphan), group.submissions)
  }

  @Test
  fun offline_listsOnlySubmissionsStoredOnTheDevice_andDropsEmptyForms() {
    val entity = entityWithSubmissions
    val remoteOnly =
      entity.copy(submissions = listOf(entity.submissions.first().copy(id = "remote")))

    val online = states().copy(isOnline = true).availableSubmissionGroupsOf(remoteOnly)
    val offline = states().copy(isOnline = false).availableSubmissionGroupsOf(remoteOnly)

    assertEquals(1, online.size)
    assertEquals(emptyList(), offline)
  }

  @Test
  fun aSurveyThatCantCollectData_offersNoForms() {
    val entity = entityWithSubmissions
    val states = states()
    val readOnly = states.copy(dataCollection = states.dataCollection.copy(canCollectData = false))

    val details = readOnly.entityDetailsUiState(entity, onWeb = true, badges = viewModels.surveyMap)

    assertEquals(emptyList(), details.formLaunchers)
  }
}
