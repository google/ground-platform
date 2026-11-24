/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.openforis.collectone.v2.balloon

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import openforis.collectone.v1.data.SubmissionFieldEntryDef
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import org.openforis.collectone.v2.core.forms.model.FinalizationResult

class BalloonFormViewModelTest {
  private fun sampleForm(id: String, title: String): BalloonFormEntry {
    val formDef =
      FormDef(
        form_id = id,
        title = title,
        version = "1",
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    fields = listOf(FieldDefinition(name = "note", type = DataType.TYPE_STRING)),
                  )
              ),
            bindings = listOf(FieldBinding(field_path = "/data/note", type = DataType.TYPE_STRING)),
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/note",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Note"),
                    )
                )
              )
          ),
      )
    return BalloonFormEntry(id = id, title = title, version = "1", formDef = formDef)
  }

  private class FakeBalloonClient : BalloonClient {
    var fetchedState: BalloonEntityLiveState? = null
    val savedDrafts = mutableListOf<Pair<String, String>>()
    val claimResults = ArrayDeque<BalloonClaimPollResult>()
    var submitHandler:
      (
        config: BalloonConfig,
        viewerSession: BalloonViewerSession,
        form: BalloonFormEntry,
        result: FinalizationResult.Success,
        entries: List<SubmissionFieldEntryDef>,
        submissionId: String,
      ) -> BalloonSubmissionOutcome =
      { _, _, _, _, _, subId ->
        BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
      }

    override suspend fun fetchEntityState(
      serviceUrl: String,
      surveyId: String,
      entityId: String,
    ): BalloonEntityLiveState? = fetchedState

    override suspend fun saveDraft(
      serviceUrl: String,
      surveyId: String,
      entityId: String,
      formId: String,
      recordBase64: String,
    ) {
      savedDrafts += formId to recordBase64
    }

    override suspend fun claimAuthSession(
      serviceUrl: String,
      pkce: BalloonPkceSession,
    ): BalloonClaimPollResult = claimResults.removeFirstOrNull() ?: BalloonClaimPollResult.Pending

    override suspend fun submitRecord(
      config: BalloonConfig,
      viewerSession: BalloonViewerSession,
      form: BalloonFormEntry,
      result: FinalizationResult.Success,
      entries: List<SubmissionFieldEntryDef>,
      submissionId: String,
    ): BalloonSubmissionOutcome =
      submitHandler(config, viewerSession, form, result, entries, submissionId)
  }

  @Test
  fun multiFormConfig_startsOnChooserAndSwitchesToSelectedForm() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val client = FakeBalloonClient()
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        entityId = "plot-1",
        forms = listOf(sampleForm("f1", "Form One"), sampleForm("f2", "Form Two")),
      )
    val vm = BalloonFormViewModel(config, client, dispatcher = dispatcher)
    runCurrent()

    assertNull(vm.uiState.value.activeForm)
    assertNull(vm.uiState.value.formController)
    assertFalse(vm.uiState.value.showFormHeader)

    vm.selectForm(1)
    assertEquals("f2", vm.uiState.value.activeForm?.id)
    assertNotNull(vm.uiState.value.formController)
    assertTrue(vm.uiState.value.showFormHeader)

    vm.backToFormChooser()
    assertNull(vm.uiState.value.activeForm)
    assertNull(vm.uiState.value.formController)
  }

  @Test
  fun liveEntityState_restoresDraftAndDiscardClearsIt() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val draftRecord =
      RecordInstance(
        form_id = "f1",
        form_version = "1",
        data_ =
          RecordNode(
            fields = mapOf("note" to FieldValue(scalar_value = TypedValue(string_value = "Saved")))
          ),
      )
    val client =
      FakeBalloonClient().apply {
        fetchedState =
          BalloonEntityLiveState(
            status = "In progress",
            markerSymbol = "◐",
            draftFormId = "f1",
            draftRecordBase64 = encodeDraftRecordBase64(draftRecord),
          )
      }
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        entityId = "plot-1",
        forms = listOf(sampleForm("f1", "Form One")),
      )
    val vm = BalloonFormViewModel(config, client, dispatcher = dispatcher)
    runCurrent()

    assertTrue(vm.uiState.value.showDraftRestoredBanner)
    assertEquals("f1", vm.uiState.value.restoredDraftFormId)
    assertEquals("In progress", vm.uiState.value.config.status)
    assertEquals(
      "Saved",
      vm.uiState.value.formController
        ?.formState
        ?.fieldStates
        ?.get("/data/note")
        ?.value
        ?.scalar_value
        ?.string_value,
    )

    vm.discardRestoredDraft()
    runCurrent()
    assertFalse(vm.uiState.value.showDraftRestoredBanner)
    assertEquals("f1" to "", client.savedDrafts.last())
  }

  @Test
  fun submitWithoutViewerSession_emitsAuthEffect_pollsAndSubmits() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val client =
      FakeBalloonClient().apply {
        claimResults.addLast(BalloonClaimPollResult.Pending)
        claimResults.addLast(
          BalloonClaimPollResult.Authenticated(
            BalloonViewerSession(authToken = "tok-123", collectorEmail = "user@fao.org")
          )
        )
      }
    val config =
      BalloonConfig(
        serviceUrl = "https://collectone.example.org",
        surveyId = "survey-1",
        datasetId = "plots",
        entityId = "plot-1",
        forms = listOf(sampleForm("f1", "Form One")),
      )
    val vm = BalloonFormViewModel(config, client, dispatcher = dispatcher)
    runCurrent()

    vm.effects.test {
      val controller = vm.uiState.value.formController!!
      controller.updateString("/data/note", "Verified")
      val finalized = controller.finalizeForm() as FinalizationResult.Success

      vm.submitForm(finalized)
      runCurrent()

      val effect = awaitItem()
      assertTrue(effect is BalloonFormEffect.OpenExternalUrl)
      assertTrue(effect.url.startsWith("/balloon-auth.html#"))
      assertNotNull(vm.uiState.value.pendingAuth)

      advanceTimeBy(1_000L)
      runCurrent()

      assertNull(vm.uiState.value.pendingAuth)
      assertNotNull(vm.uiState.value.completedSubmission)
      assertEquals("✓", vm.uiState.value.config.markerSymbol)
    }
  }

  @Test
  fun failedSubmission_supportsRetryAndBackToForm() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var shouldFail = true
    val client =
      FakeBalloonClient().apply {
        submitHandler = { _, _, _, _, _, subId ->
          if (shouldFail) {
            BalloonSubmissionOutcome.UploadFailed("Network offline")
          } else {
            BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
          }
        }
      }
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        entityId = "plot-1",
        forms = listOf(sampleForm("f1", "Form One")),
      )
    val vm = BalloonFormViewModel(config, client, dispatcher = dispatcher)
    runCurrent()

    val controller = vm.uiState.value.formController!!
    val finalized = controller.finalizeForm() as FinalizationResult.Success
    vm.submitForm(finalized)
    runCurrent()

    assertEquals("Network offline", vm.uiState.value.failedSubmission?.message)

    shouldFail = false
    vm.retrySubmission()
    runCurrent()

    assertNull(vm.uiState.value.failedSubmission)
    assertNotNull(vm.uiState.value.completedSubmission)

    vm.startAnotherEntry()
    assertNull(vm.uiState.value.completedSubmission)
  }
}
