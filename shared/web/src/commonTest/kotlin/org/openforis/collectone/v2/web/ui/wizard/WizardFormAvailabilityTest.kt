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
package org.openforis.collectone.v2.web.ui.wizard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.data.seed.SampleSurveyEditorData
import org.openforis.collectone.v2.app.domain.model.FormAvailability
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.CreateSurveyWizardStep
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod
import org.openforis.collectone.v2.web.navigation.CreateSurveyRoute
import org.openforis.collectone.v2.web.navigation.NavigationRecorder
import org.openforis.collectone.v2.web.navigation.SurveyDashboardRoute
import org.openforis.collectone.v2.web.ui.WebAppState
import org.openforis.collectone.v2.web.ui.createSurveyWizard
import org.openforis.collectone.v2.web.ui.surveyEditor

/**
 * Verifies that surveys created through the web wizard are available on web by default and preserve
 * any per-form availability and geometry capture edits made in Step 5 across Step 6 and save.
 */
class WizardFormAvailabilityTest {

  @Test
  fun otherPurposePolygonSurvey_isWebAndMobileReadyAndLaunchesFromCollectData() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val navigation = NavigationRecorder(state, start = CreateSurveyRoute())
    val wizard = state.createSurveyWizard
    val surveyId = wizard.uiState.value.workingSurveyId

    wizard.updateTitle("Whisp test")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Custom")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.updatePrimaryLayerGeometryType(GeometryType.POLYGON)
    wizard.nextStep() // FORM_QUESTIONS
    wizard.nextStep() // SHARING
    wizard.finishWizard()

    val dashboardRoute = assertIs<SurveyDashboardRoute>(navigation.currentRoute)
    assertEquals(surveyId, dashboardRoute.surveyId)
    state.openSurveyOnWeb(surveyId)

    val dcState = state.viewModels.dataCollection.uiState.value
    val webForm = assertNotNull(dcState.webForms.singleOrNull())
    assertEquals("Whisp test", webForm.title)
    assertEquals(FormAvailability.BOTH, webForm.availability)

    state.viewModels.dataCollection.launchFormFromFab(webForm.id)
    assertNotNull(state.viewModels.dataCollection.uiState.value.activeFormWizardController)
  }

  @Test
  fun enablingWebAndFixingGeometryInStepFive_survivesSharingAndFinishWizard() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val navigation = NavigationRecorder(state, start = CreateSurveyRoute())
    val wizard = state.createSurveyWizard
    val surveyId = wizard.uiState.value.workingSurveyId

    // Import an XForm with a GPS-only geopoint, which FormImport marks mobile-only until fixed.
    val gpsOnlyXForm =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Field Polygon Survey</h:title>
          <model>
            <instance>
              <data id="field_polygon">
                <location/>
                <notes/>
              </data>
            </instance>
            <bind nodeset="/data/location" type="geoshape" required="true()"/>
            <bind nodeset="/data/notes" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/location"><label>Boundary</label></input>
          <input ref="/data/notes"><label>Notes</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()

    wizard.selectStartMethod(WizardStartMethod.IMPORT_XFORMS)
    wizard.importXFormsXml("field_polygon.xml", gpsOnlyXForm)
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack(SurveyWizardRules.OTHER_PURPOSE_ID)
    wizard.updateOtherPurpose("Custom")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS

    val primaryFormKey = assertNotNull(wizard.uiState.value.primaryForm).key
    val formEditor = state.surveyEditor.formEditor(primaryFormKey)
    assertEquals(FormAvailability.BOTH, formEditor.uiState.value.form.availability)

    // Switch to mobile-only, then re-enable web availability via the preview target action.
    formEditor.updateAvailability(FormAvailability.MOBILE)
    assertEquals(FormAvailability.MOBILE, formEditor.uiState.value.form.availability)
    formEditor.selectPreviewTarget(FormPreviewTarget.WEB)
    formEditor.enableOnPreviewTarget()
    assertEquals(FormAvailability.BOTH, formEditor.uiState.value.form.availability)

    // Advancing to Sharing and finishing the wizard must preserve the availability edit.
    wizard.nextStep() // SHARING
    assertEquals(CreateSurveyWizardStep.SHARING, wizard.uiState.value.currentStep)
    wizard.finishWizard()

    assertIs<SurveyDashboardRoute>(navigation.currentRoute)
    state.openSurveyOnWeb(surveyId)
    val savedForm = assertNotNull(state.surveyEditor.uiState.value.forms.singleOrNull()).form
    assertEquals(FormAvailability.BOTH, savedForm.availability)
    assertTrue(state.viewModels.dataCollection.uiState.value.webForms.isNotEmpty())
  }

  @Test
  fun disablingWebInStepFive_isPreservedOnSave() {
    val state = WebAppState(dataHolder = sampleDataHolder())
    state.createSurveyWizard.startWizard(SampleOrganizationsData.KENYA_FOREST_SERVICE)
    val wizard = state.createSurveyWizard
    val surveyId = wizard.uiState.value.workingSurveyId

    wizard.updateTitle("Mobile-Only Field Check")
    wizard.nextStep() // MODE_AND_PURPOSE
    wizard.togglePurposePack("ferm_restoration")
    wizard.nextStep() // SURVEY_AREA
    state.surveyEditor.setSurveyArea(SampleSurveyEditorData.kenyaSurveyArea())
    wizard.nextStep() // MAP_FEATURES
    wizard.nextStep() // FORM_QUESTIONS

    val primaryFormKey = assertNotNull(wizard.uiState.value.primaryForm).key
    val formEditor = state.surveyEditor.formEditor(primaryFormKey)
    assertEquals(FormAvailability.BOTH, formEditor.uiState.value.form.availability)

    formEditor.updateAvailability(FormAvailability.MOBILE)
    wizard.nextStep() // SHARING
    wizard.finishWizard()

    state.openSurveyOnWeb(surveyId)
    assertEquals(
      FormAvailability.MOBILE,
      assertNotNull(state.surveyEditor.uiState.value.forms.singleOrNull()).form.availability,
    )
    assertTrue(state.viewModels.dataCollection.uiState.value.webForms.isEmpty())
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
  }
}
