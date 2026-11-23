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
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.openforis.collectone.v2.balloon

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef

class BalloonFormScreenUiTest {
  private fun sampleFormEntry(
    id: String,
    title: String,
    questionLabels: List<Pair<String, String>>,
  ): BalloonFormEntry {
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
                    fields =
                      questionLabels.map { (fieldName, _) ->
                        FieldDefinition(name = fieldName, type = DataType.TYPE_STRING)
                      },
                  )
              ),
            bindings =
              questionLabels.map { (fieldName, _) ->
                FieldBinding(field_path = "/data/$fieldName", type = DataType.TYPE_STRING)
              },
          ),
        view =
          ViewDef(
            components =
              questionLabels.map { (fieldName, label) ->
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/$fieldName",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = label),
                    )
                )
              }
          ),
      )
    return BalloonFormEntry(
      id = id,
      title = title,
      version = "1",
      formDef = formDef,
    )
  }

  @Test
  fun singleForm_skipsChooser_andRendersFormQuestionsDirectly() = runComposeUiTest {
    val singleForm =
      sampleFormEntry(
        id = "form-water",
        title = "Washing Station Effluent & Water Check",
        questionLabels = listOf("water_ph" to "Water pH reading"),
      )
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        datasetId = "washing_stations",
        datasetName = "Cooperative Washing Stations",
        entityId = "ws-1",
        entityName = "Othaya Wet Mill",
        forms = listOf(singleForm),
      )

    setContent {
      BalloonFormScreen(
        config = config,
        onSubmitRecord = { _, _, _, subId, _ ->
          BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
        },
      )
    }

    onNodeWithTag("balloon_form_chooser").assertDoesNotExist()
    onNodeWithTag("balloon_change_form_button").assertDoesNotExist()
    onNodeWithText("Water pH reading").assertIsDisplayed()
  }

  @Test
  fun multipleForms_rendersChooserFirst_opensSelectedForm_andAllowsChangingForm() =
    runComposeUiTest {
      val eudrForm =
        sampleFormEntry(
          id = "form-eudr-baseline",
          title = "EUDR Parcel Baseline Registration",
          questionLabels =
            listOf(
              "cultivar_count" to "Cultivar count",
              "deforestation_free" to "Deforestation-free attestation",
            ),
        )
      val householdForm =
        sampleFormEntry(
          id = "form-household-interview",
          title = "Smallholder Household Socio-Economic Survey",
          questionLabels = listOf("household_size" to "Household member count"),
        )
      val config =
        BalloonConfig(
          surveyId = "survey-1",
          datasetId = "coffee_parcels",
          datasetName = "Smallholder Coffee Parcels",
          entityId = "parcel-1",
          entityName = "Kamau Highland Lot",
          forms = listOf(eudrForm, householdForm),
        )

      setContent {
        BalloonFormScreen(
          config = config,
          onSubmitRecord = { _, _, _, subId, _ ->
            BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
          },
        )
      }

      // 1. Multi-form balloon shows the form chooser before opening any form's questions.
      onNodeWithTag("balloon_form_chooser").assertIsDisplayed()
      onNodeWithText("Choose a form to complete").assertIsDisplayed()
      onNodeWithText("Kamau Highland Lot • Smallholder Coffee Parcels").assertIsDisplayed()
      onNodeWithTag("balloon_form_option_form-eudr-baseline").assertIsDisplayed()
      onNodeWithText("2 questions").assertIsDisplayed()
      onNodeWithTag("balloon_form_option_form-household-interview").assertIsDisplayed()
      onNodeWithText("1 question").assertIsDisplayed()
      onNodeWithText("Cultivar count").assertDoesNotExist()
      onNodeWithText("Household member count").assertDoesNotExist()

      // 2. Picking the second form opens its questions and shows the "Change form" top bar.
      onNodeWithTag("balloon_form_option_form-household-interview").performClick()
      onNodeWithTag("balloon_form_chooser").assertDoesNotExist()
      onNodeWithTag("balloon_change_form_button").assertIsDisplayed()
      onNodeWithText("Household member count").assertIsDisplayed()
      onNodeWithText("Cultivar count").assertDoesNotExist()

      // 3. Clicking "Change form" returns to the chooser so the collector can switch forms.
      onNodeWithTag("balloon_change_form_button").performClick()
      onNodeWithTag("balloon_form_chooser").assertIsDisplayed()
      onNodeWithTag("balloon_form_option_form-eudr-baseline").performClick()
      onNodeWithText("Cultivar count").assertIsDisplayed()
      onNodeWithText("Household member count").assertDoesNotExist()
    }

  @Test
  fun noFormsEnabledForEarth_rendersNoFormsAvailableCard() = runComposeUiTest {
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        datasetId = "coffee_parcels",
        entityId = "parcel-1",
        entityName = "Kamau Highland Lot",
        forms = emptyList(),
      )

    setContent {
      BalloonFormScreen(
        config = config,
        onSubmitRecord = { _, _, _, subId, _ ->
          BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
        },
      )
    }

    onNodeWithTag("balloon_no_forms_card").assertIsDisplayed()
    onNodeWithText("No Google Earth forms available").assertIsDisplayed()
  }

  @Test
  fun defaultDemoBalloonForm_rendersColorCodedChoiceOptions() = runComposeUiTest {
    setContent {
      BalloonFormScreen(
        config = BalloonConfig(),
        onSubmitRecord = { _, _, _, subId, _ ->
          BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
        },
      )
    }

    onNodeWithTag("choice-color-stripe-forest", useUnmergedTree = true).assertExists()
    onNodeWithTag("choice-color-swatch-forest", useUnmergedTree = true).assertExists()
    onNodeWithTag("choice-color-stripe-cropland", useUnmergedTree = true).assertExists()
    onNodeWithTag("choice-color-swatch-cropland", useUnmergedTree = true).assertExists()
  }

  @Test
  fun restoresInProgressDraftOnMount_andAllowsDiscardingDraft() = runComposeUiTest {
    val waterForm =
      sampleFormEntry(
        id = "form-water",
        title = "Washing Station Effluent & Water Check",
        questionLabels = listOf("water_ph" to "Water pH reading"),
      )
    val draftRecord =
      RecordInstance(
        form_id = "form-water",
        form_version = "1",
        data_ =
          RecordNode(
            fields =
              mapOf("water_ph" to FieldValue(scalar_value = TypedValue(string_value = "6.85")))
          ),
      )
    val draftBase64 = encodeDraftRecordBase64(draftRecord)
    var savedDrafts = mutableListOf<Pair<String, String>>()

    val config =
      BalloonConfig(
        surveyId = "survey-1",
        datasetId = "washing_stations",
        datasetName = "Cooperative Washing Stations",
        entityId = "ws-1",
        entityName = "Othaya Wet Mill",
        forms = listOf(waterForm),
      )

    setContent {
      BalloonFormScreen(
        config = config,
        onFetchEntityState = { _, _, _ ->
          BalloonEntityLiveState(
            status = "In progress",
            markerSymbol = "◐",
            draftFormId = "form-water",
            draftRecordBase64 = draftBase64,
          )
        },
        onSaveDraft = { _, _, _, formId, recordBase64 -> savedDrafts += formId to recordBase64 },
        onSubmitRecord = { _, _, _, subId, _ ->
          BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
        },
      )
    }

    onNodeWithTag("balloon_draft_restored_banner").assertIsDisplayed()
    onNodeWithText("In-progress draft restored").assertIsDisplayed()
    onNodeWithText("6.85").assertIsDisplayed()

    onNodeWithTag("balloon_discard_draft_button").performClick()
    onNodeWithTag("balloon_draft_restored_banner").assertDoesNotExist()
    assertEquals("form-water" to "", savedDrafts.last())
  }

  @Test
  fun incompatiblePhotoAndGeometryFields_showTitleHintAndWarningInBalloon_andAllowSubmission() =
    runComposeUiTest {
      val formDef =
        FormDef(
          form_id = "form-earth-fallback",
          title = "Plot Verification",
          version = "1",
          model =
            ModelDef(
              primary_instance =
                PrimaryInstance(
                  record_schema =
                    RecordSchema(
                      name = "data",
                      fields =
                        listOf(
                          FieldDefinition(name = "land_cover", type = DataType.TYPE_STRING),
                          FieldDefinition(name = "canopy_photo", type = DataType.TYPE_BINARY),
                          FieldDefinition(name = "parcel_boundary", type = DataType.TYPE_GEOSHAPE),
                        ),
                    )
                ),
              bindings =
                listOf(
                  FieldBinding(
                    field_path = "/data/land_cover",
                    type = DataType.TYPE_STRING,
                    required_expression = "true()",
                  ),
                  FieldBinding(
                    field_path = "/data/canopy_photo",
                    type = DataType.TYPE_BINARY,
                    required_expression = "true()",
                  ),
                  FieldBinding(
                    field_path = "/data/parcel_boundary",
                    type = DataType.TYPE_GEOSHAPE,
                    required_expression = "true()",
                  ),
                ),
            ),
          view =
            ViewDef(
              components =
                listOf(
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/land_cover",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Dominant land cover"),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/canopy_photo",
                        type = ControlType.CONTROL_UPLOAD,
                        media_type = "image/*",
                        label = LabelDef(text = "Canopy photo"),
                        hint = LabelDef(text = "Face north from plot center"),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/parcel_boundary",
                        type = ControlType.CONTROL_INPUT,
                        appearance = "placement-map",
                        label = LabelDef(text = "Parcel boundary"),
                        hint = LabelDef(text = "Trace the outer boundary"),
                      )
                  ),
                )
            ),
        )
      val entry =
        BalloonFormEntry(
          id = "form-earth-fallback",
          title = "Plot Verification",
          version = "1",
          formDef = formDef,
        )
      val config =
        BalloonConfig(
          surveyId = "survey-1",
          datasetId = "plots",
          datasetName = "Sample Plots",
          entityId = "plot-1",
          entityName = "Plot 1",
          forms = listOf(entry),
        )
      var submitted = false

      setContent {
        BalloonFormScreen(
          config = config,
          onSubmitRecord = { _, _, _, subId, _ ->
            submitted = true
            BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
          },
        )
      }

      onNodeWithText("Canopy photo").assertExists()
      onNodeWithText("Face north from plot center").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/canopy_photo").assertExists()
      onNodeWithText(
          "Photo questions aren't available on Google Earth. This question will be skipped. Contact the survey organizer to resolve this."
        )
        .assertExists()
      onNodeWithText("Take photo").assertDoesNotExist()

      onNodeWithText("Parcel boundary").assertExists()
      onNodeWithText("Trace the outer boundary").assertExists()
      onNodeWithTag("variant_incompatible_warning_/data/parcel_boundary").assertExists()
      onNodeWithText(
          "Polygon questions aren't available on Google Earth. This question will be skipped. Contact the survey organizer to resolve this."
        )
        .assertExists()

      // Answering the compatible required field submits cleanly even though the incompatible photo
      // and polygon fields were marked required in the form definition.
      onAllNodes(hasSetTextAction())[0].performTextInput("Closed forest")
      onNodeWithText("Submit").performClick()
      waitForIdle()
      assertTrue(submitted)
    }

  @Test
  fun uploadFailure_showsErrorPreservesAnswersAndDraft_andRetriesToConfirmation() =
    runComposeUiTest {
      val waterForm =
        sampleFormEntry(
          id = "form-water",
          title = "Washing Station Effluent & Water Check",
          questionLabels = listOf("water_ph" to "Water pH reading"),
        )
      val config =
        BalloonConfig(
          surveyId = "survey-1",
          datasetId = "washing_stations",
          datasetName = "Cooperative Washing Stations",
          entityId = "ws-1",
          entityName = "Othaya Wet Mill",
          serviceUrl = "https://api.example.org",
          authToken = "demo-jwt",
          collectorEmail = "collector@example.org",
          forms = listOf(waterForm),
        )
      var submitAttempts = 0
      val savedDrafts = mutableListOf<Pair<String, String>>()

      setContent {
        BalloonFormScreen(
          config = config,
          onSaveDraft = { _, _, _, formId, recordBase64 -> savedDrafts += formId to recordBase64 },
          onSubmitRecord = { _, _, _, subId, _ ->
            submitAttempts++
            if (submitAttempts <= 2) {
              BalloonSubmissionOutcome.UploadFailed("Server rejected submission (gRPC 7)")
            } else {
              BalloonSubmissionOutcome.SyncedToServer(
                submissionId = subId,
                nextMarkerSymbol = "◐",
                collectorEmail = "collector@example.org",
              )
            }
          },
        )
      }

      onAllNodes(hasSetTextAction())[0].performTextInput("7.12")
      onNodeWithText("Submit").performClick()
      waitForIdle()

      // First failure shows error card instead of confirmation and does not clear server draft.
      assertEquals(1, submitAttempts)
      onNodeWithTag("balloon_upload_error_card").assertIsDisplayed()
      onNodeWithText("Couldn't sync submission to Collect One").assertIsDisplayed()
      onNodeWithText("Server rejected submission (gRPC 7)").assertIsDisplayed()
      onNodeWithText("Submitted to Collect One").assertDoesNotExist()
      assertTrue(savedDrafts.none { (_, payload) -> payload.isEmpty() })

      // Clicking "Back to form" restores the form with the collector's entered answer intact.
      onNodeWithTag("balloon_back_to_form_button").performClick()
      waitForIdle()
      onNodeWithTag("balloon_upload_error_card").assertDoesNotExist()
      onNodeWithText("7.12").assertIsDisplayed()

      // Submitting again fails once more, then clicking "Retry upload" succeeds and shows receipt.
      onNodeWithText("Submit").performClick()
      waitForIdle()
      assertEquals(2, submitAttempts)
      onNodeWithTag("balloon_upload_error_card").assertIsDisplayed()

      onNodeWithTag("balloon_retry_upload_button").performClick()
      waitForIdle()
      assertEquals(3, submitAttempts)
      onNodeWithTag("balloon_upload_error_card").assertDoesNotExist()
      onNodeWithText("Submitted to Collect One").assertIsDisplayed()
      assertEquals("form-water" to "", savedDrafts.last())
    }

  @Test
  fun unansweredQuestions_showOneBasedNumberBadges_andAnsweredSwitchToCheckmark() =
    runComposeUiTest {
      val formDef =
        FormDef(
          form_id = "form-earth-numbers",
          title = "Water Quality Check",
          version = "1",
          model =
            ModelDef(
              primary_instance =
                PrimaryInstance(
                  record_schema =
                    RecordSchema(
                      name = "data",
                      fields =
                        listOf(
                          FieldDefinition(name = "entity_id", type = DataType.TYPE_STRING),
                          FieldDefinition(name = "water_ph", type = DataType.TYPE_STRING),
                          FieldDefinition(name = "turbidity", type = DataType.TYPE_STRING),
                        ),
                    )
                ),
              bindings =
                listOf(
                  FieldBinding(field_path = "/data/entity_id", type = DataType.TYPE_STRING),
                  FieldBinding(field_path = "/data/water_ph", type = DataType.TYPE_STRING),
                  FieldBinding(field_path = "/data/turbidity", type = DataType.TYPE_STRING),
                ),
            ),
          view =
            ViewDef(
              components =
                listOf(
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/entity_id",
                        type = ControlType.CONTROL_INPUT,
                        appearance = "map-select",
                        label = LabelDef(text = "Target station"),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/water_ph",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Water pH reading"),
                      )
                  ),
                  ViewComponent(
                    control =
                      ControlDef(
                        field_ref = "/data/turbidity",
                        type = ControlType.CONTROL_INPUT,
                        label = LabelDef(text = "Turbidity (NTU)"),
                      )
                  ),
                )
            ),
        )
      val config =
        BalloonConfig(
          surveyId = "survey-1",
          datasetId = "washing_stations",
          datasetName = "Cooperative Washing Stations",
          entityId = "ws-1",
          entityName = "Othaya Wet Mill",
          forms =
            listOf(
              BalloonFormEntry(
                id = "form-earth-numbers",
                title = "Water Quality Check",
                version = "1",
                formDef = formDef,
              )
            ),
        )

      setContent {
        BalloonFormScreen(
          config = config,
          onSubmitRecord = { _, _, _, subId, _ ->
            BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
          },
        )
      }

      // The hidden map-select entityref step is filtered out, so visible questions number 1 and 2.
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Pending")
      onNodeWithTag("compact_question_number_2", useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Pending")
      onNodeWithTag("compact_question_number_3", useUnmergedTree = true).assertDoesNotExist()

      // Answering question 1 switches its badge to the answered checkmark while question 2 stays 2.
      onAllNodes(hasSetTextAction())[0].performTextInput("7.0")
      waitForIdle()
      onNodeWithTag("compact_question_number_1", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithContentDescription("Answered", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("compact_question_number_2", useUnmergedTree = true).assertIsDisplayed()
    }

  @Test
  fun tabTraversal_skipsQuestionAndSectionHeadingsAndAllowsHeaderClicks() = runComposeUiTest {
    val formDef =
      FormDef(
        form_id = "form-earth-tab",
        title = "Plot Tab Order Check",
        version = "1",
        model =
          ModelDef(
            primary_instance =
              PrimaryInstance(
                record_schema =
                  RecordSchema(
                    name = "data",
                    fields =
                      listOf(
                        FieldDefinition(name = "observer", type = DataType.TYPE_STRING),
                        FieldDefinition(
                          name = "site",
                          type = DataType.TYPE_MESSAGE,
                          fields =
                            listOf(
                              FieldDefinition(name = "canopy_pct", type = DataType.TYPE_INT32)
                            ),
                        ),
                        FieldDefinition(name = "canopy_photo", type = DataType.TYPE_BINARY),
                        FieldDefinition(name = "remarks", type = DataType.TYPE_STRING),
                      ),
                  )
              ),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "/data/observer",
                  type = DataType.TYPE_STRING,
                  required_expression = "true()",
                ),
                FieldBinding(
                  field_path = "/data/site/canopy_pct",
                  type = DataType.TYPE_INT32,
                  required_expression = "true()",
                ),
                FieldBinding(
                  field_path = "/data/canopy_photo",
                  type = DataType.TYPE_BINARY,
                  required_expression = "true()",
                ),
                FieldBinding(
                  field_path = "/data/remarks",
                  type = DataType.TYPE_STRING,
                  required_expression = "true()",
                ),
              ),
          ),
        view =
          ViewDef(
            components =
              listOf(
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/observer",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Observer name"),
                    )
                ),
                ViewComponent(
                  group =
                    GroupDef(
                      field_ref = "/data/site",
                      appearance = "field-list",
                      label = LabelDef(text = "Canopy metrics"),
                      components =
                        listOf(
                          ViewComponent(
                            control =
                              ControlDef(
                                field_ref = "/data/site/canopy_pct",
                                type = ControlType.CONTROL_INPUT,
                                label = LabelDef(text = "Canopy cover (%)"),
                              )
                          )
                        ),
                    )
                ),
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/canopy_photo",
                      type = ControlType.CONTROL_UPLOAD,
                      media_type = "image/*",
                      label = LabelDef(text = "Canopy photo"),
                    )
                ),
                ViewComponent(
                  control =
                    ControlDef(
                      field_ref = "/data/remarks",
                      type = ControlType.CONTROL_INPUT,
                      label = LabelDef(text = "Interpreter remarks"),
                    )
                ),
              )
          ),
      )
    val config =
      BalloonConfig(
        surveyId = "survey-1",
        datasetId = "plots",
        datasetName = "Sample Plots",
        entityId = "plot-1",
        entityName = "Plot 1",
        forms =
          listOf(
            BalloonFormEntry(
              id = "form-earth-tab",
              title = "Plot Tab Order Check",
              version = "1",
              formDef = formDef,
            )
          ),
      )

    setContent {
      BalloonFormScreen(
        config = config,
        onSubmitRecord = { _, _, _, subId, _ ->
          BalloonSubmissionOutcome.SyncedToServer(subId, "✓")
        },
      )
    }

    onNode(hasText("Canopy metrics") and isHeading()).assertExists()

    val inputs = onAllNodes(hasSetTextAction())
    assertEquals(3, inputs.fetchSemanticsNodes().size)

    inputs[0].performClick()
    waitForIdle()
    inputs[0].assertIsFocused()

    // Forward Tab moves directly between inputs, skipping section headings, question headers,
    // collapse chevrons, and the incompatible photo warning card.
    inputs[0].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    inputs[1].assertIsFocused()
    onNode(hasText("Canopy metrics") and isHeading())
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    onNodeWithText("Canopy cover (%) *").assertIsNotFocused()

    inputs[1].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    inputs[2].assertIsFocused()
    onNodeWithText("Canopy photo").assertIsNotFocused()
    onNodeWithText("Interpreter remarks *").assertIsNotFocused()

    inputs[2].performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    onNodeWithText("Review & submit").assertIsFocused()

    // Shift+Tab mirrors forward order without stopping on any heading or chevron.
    onNodeWithText("Review & submit").performKeyInput {
      withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) }
    }
    waitForIdle()
    inputs[2].assertIsFocused()

    inputs[2].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    inputs[1].assertIsFocused()

    inputs[1].performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    waitForIdle()
    inputs[0].assertIsFocused()

    // Pointer/click toggling on headings remains active.
    onNodeWithText("Observer name *").performClick()
    waitForIdle()
    assertEquals(2, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    onNodeWithContentDescription("Expand question").performClick()
    waitForIdle()
    assertEquals(3, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
  }
}
