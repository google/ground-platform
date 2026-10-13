/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.workbench.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.app.domain.model.AppScreen
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.app.domain.model.ListFilterTab
import org.groundplatform.v2.app.ui.map.SurveyMap
import org.groundplatform.v2.app.ui.map.SurveyMapContent
import org.groundplatform.v2.core.forms.media.MediaCaptureKind
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.mobile.ui.MobileAppState

class WorkbenchStateTest {
  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun deviceFormFactor_togglesBetweenMobileAndTablet() {
    val workbench = WorkbenchState(MobileAppState())
    val state = workbench.mobile

    // Defaults to Mobile (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, workbench.deviceFormFactor)
    assertEquals(404, workbench.deviceFormFactor.frameWidthDp)
    assertEquals(764, workbench.deviceFormFactor.frameHeightDp)

    // Toggle to Tablet (780 × 620 dp)
    workbench.toggleDeviceFormFactor()
    assertEquals(DeviceFormFactor.TABLET, workbench.deviceFormFactor)
    assertEquals(780, workbench.deviceFormFactor.frameWidthDp)
    assertEquals(620, workbench.deviceFormFactor.frameHeightDp)

    // Explicitly select Mobile
    workbench.selectDeviceFormFactor(DeviceFormFactor.MOBILE)
    assertEquals(DeviceFormFactor.MOBILE, workbench.deviceFormFactor)
  }

  @Test
  fun rotateDevice_rotatesMobileAndTabletBetweenPortraitAndLandscape() {
    val workbench = WorkbenchState(MobileAppState())
    val state = workbench.mobile

    // Mobile defaults to Portrait (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, workbench.deviceFormFactor)
    assertEquals(DeviceOrientation.PORTRAIT, workbench.deviceOrientation)
    assertFalse(workbench.isDeviceRotated)
    assertEquals(404, workbench.effectiveFrameWidthDp)
    assertEquals(764, workbench.effectiveFrameHeightDp)
    assertEquals("404 × 764 dp", workbench.effectiveDimensionsLabel)

    // Rotate Mobile -> Landscape (764 × 404 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.LANDSCAPE, workbench.deviceOrientation)
    assertTrue(workbench.isDeviceRotated)
    assertEquals(764, workbench.effectiveFrameWidthDp)
    assertEquals(404, workbench.effectiveFrameHeightDp)
    assertEquals("764 × 404 dp", workbench.effectiveDimensionsLabel)

    // Rotate Mobile back -> Portrait (404 × 764 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, workbench.deviceOrientation)
    assertFalse(workbench.isDeviceRotated)
    assertEquals(404, workbench.effectiveFrameWidthDp)
    assertEquals(764, workbench.effectiveFrameHeightDp)

    // Switch to Tablet -> defaults to Landscape (780 × 620 dp)
    workbench.selectDeviceFormFactor(DeviceFormFactor.TABLET)
    assertEquals(DeviceFormFactor.TABLET, workbench.deviceFormFactor)
    assertEquals(DeviceOrientation.LANDSCAPE, workbench.deviceOrientation)
    assertFalse(workbench.isDeviceRotated)
    assertEquals(780, workbench.effectiveFrameWidthDp)
    assertEquals(620, workbench.effectiveFrameHeightDp)
    assertEquals("780 × 620 dp", workbench.effectiveDimensionsLabel)

    // Rotate Tablet -> Portrait (620 × 780 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, workbench.deviceOrientation)
    assertTrue(workbench.isDeviceRotated)
    assertEquals(620, workbench.effectiveFrameWidthDp)
    assertEquals(780, workbench.effectiveFrameHeightDp)
    assertEquals("620 × 780 dp", workbench.effectiveDimensionsLabel)
  }

  @Test
  fun updateCustomXFormsXml_parsesValidAndInvalidXForms_andRefreshesActiveController() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = AppScreen.MAIN_SURVEY))
    val state = workbench.mobile
    assertEquals(null, workbench.xformsXmlError)
    assertTrue(state.customFormDef != null)

    // Launch form runner first
    state.launchFormForEntity(entityId = "entity-shade-201", formId = "form-shade-canopy-audit")
    assertTrue(state.isDataCollectionFormOpen)

    // 1. Update with valid Baobab Biometrics XForms XML -> refreshes open FormWizardController
    workbench.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML)
    assertEquals(null, workbench.xformsXmlError)
    assertEquals("Shade Tree & Baobab Biometrics", state.customFormDef?.title)
    assertEquals(
      "Shade Tree & Baobab Biometrics",
      state.activeFormWizardController?.formState?.formDef?.title,
    )

    // 2. Update with invalid XML -> populates xformsXmlError and clears customFormDef
    workbench.updateCustomXFormsXml("<h:html><unclosed_tag></h:html>")
    assertTrue(workbench.xformsXmlError != null)
    assertEquals(null, state.customFormDef)

    // 3. Reset to default sample XForms XML -> clears error and restores default FormDef
    workbench.resetDefaultXFormsXml()
    assertEquals(null, workbench.xformsXmlError)
    assertEquals("EUDR & Shade-Tree Field Survey", state.customFormDef?.title)

    // 4. Clearing customXFormsXml uses built-in fallback FormDef per form ID
    workbench.updateCustomXFormsXml("")
    assertEquals(null, workbench.xformsXmlError)
    assertEquals(null, state.customFormDef)
    assertEquals(
      "Seasonal Shade Tree & Canopy Audit",
      state.activeFormWizardController?.formState?.formDef?.title,
    )
    state.closeActiveFormRunner()
    assertFalse(state.isDataCollectionFormOpen)
  }

  @Test
  fun selectWorkbenchExampleForm_swapsAndValidatesAllFiveExampleForms() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = AppScreen.MAIN_SURVEY))
    val state = workbench.mobile
    assertEquals(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      workbench.selectedWorkbenchExampleForm,
    )

    // 1. Simple Single-Point & Land Use Survey (pan allowed via placement-map, 10m accuracy
    // required, 3 entities via save_to, 0 standalone submissions)
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )
    assertEquals("survey-single-point-land-use", state.activeSurveyId)
    assertEquals(3, state.entities.size)
    assertEquals(0, state.standaloneSubmissions.size)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertFalse(state.forms.first().requiresEntity)
    assertEquals(null, workbench.xformsXmlError)
    assertTrue(state.isDataCollectionFormOpen)
    val singlePointFormDef = state.customFormDef!!
    assertEquals("Simple Point & Land Use Observation", singlePointFormDef.title)
    val samplePointCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_point" }
    assertTrue(samplePointCtrl.appearance.contains("placement-map"))
    assertEquals(10.0, samplePointCtrl.controlDef.geo_config?.accuracy_threshold_meters)

    // 2. Predefined Sample Plots & Forest Assessment Survey (5 sample_plots entities, 3 preloaded
    // assessments)
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      launchImmediately = true,
    )
    assertEquals("survey-sample-plots-forest", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals("plot_sp01", state.entities.first().id)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertTrue(state.forms.first().requiresEntity)
    assertEquals(null, workbench.xformsXmlError)
    val samplePlotsFormDef = state.customFormDef!!
    assertEquals("Sample Plot Entity & Forest Stand Assessment", samplePlotsFormDef.title)
    assertTrue(
      samplePlotsFormDef.model?.secondary_instances?.any { sec ->
        sec.id == "sample_plots" &&
          sec.rows.any { row -> row.fields["name"]?.scalar_value?.string_value == "plot_sp01" }
      } == true
    )
    val plotSelectCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_plot_entity" }
    assertEquals(5, plotSelectCtrl.options.size)
    assertEquals("plot_sp01", plotSelectCtrl.options.first().value)

    // 3. Commodity Plot Perimeter (walk + pan override) & Plot Center (no pan, <= 5m GPS accuracy,
    // 3 entities via save_to, 0 standalone submissions)
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER,
      launchImmediately = true,
    )
    assertEquals("survey-commodity-perimeter-center", state.activeSurveyId)
    assertEquals(3, state.entities.size)
    assertEquals(0, state.standaloneSubmissions.size)
    assertEquals(3, state.allSubmissions.size)
    assertEquals(3, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertFalse(state.forms.first().requiresEntity)
    assertEquals(null, workbench.xformsXmlError)
    val commodityFormDef = state.customFormDef!!
    assertEquals("Forest-Risk Commodity Plot Perimeter & Center Mapping", commodityFormDef.title)
    val commodityQuestionControls =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
    val perimeterCtrl = commodityQuestionControls.first {
      it.canonicalPath == "/data/plot_perimeter"
    }
    val centerCtrl = commodityQuestionControls.first {
      it.canonicalPath == "/data/plot_center_point"
    }
    assertTrue(perimeterCtrl.appearance.contains("placement-map"))
    assertFalse(centerCtrl.appearance.contains("placement-map"))
    assertEquals(5.0, centerCtrl.controlDef.geo_config?.accuracy_threshold_meters)

    // 4. Household Survey (6 preloaded past_individuals entities, 4 follow-up submissions)
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.HOUSEHOLD_SURVEY_PAST_INDIVIDUALS,
      launchImmediately = true,
    )
    assertEquals("survey-household-past-individuals", state.activeSurveyId)
    assertEquals(6, state.entities.size)
    assertEquals("ind_101", state.entities.first().id)
    assertEquals(4, state.allSubmissions.size)
    assertEquals(2, state.submissionGeometries.size)
    assertEquals(1, state.forms.size)
    assertTrue(state.forms.first().requiresEntity)
    assertEquals(null, workbench.xformsXmlError)
    val householdFormDef = state.customFormDef!!
    assertEquals("Household Panel Survey (Preloaded Past Individuals)", householdFormDef.title)
    val respondentSelectCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.groundplatform.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/primary_respondent_id" }
    assertEquals(6, respondentSelectCtrl.options.size)
    assertEquals("ind_101", respondentSelectCtrl.options.first().value)

    // 5. All Form Field Types Showcase ("EUDR & Shade-Tree Field Survey", 5 entities, 6 forms)
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      launchImmediately = true,
    )
    assertEquals("survey-kenya-coffee", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals(6, state.forms.size)
    assertEquals(null, workbench.xformsXmlError)
    val allTypesFormDef = state.customFormDef!!
    assertEquals("EUDR & Shade-Tree Field Survey", allTypesFormDef.title)
    assertTrue((allTypesFormDef.model?.bindings?.size ?: 0) >= 18)
  }

  @Test
  fun geoPointStepInDataCollectionForm_showsMapAndSupportsCaptureLocationAndAddPointTriggers() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = AppScreen.MAIN_SURVEY))
    val state = workbench.mobile
    workbench.selectWorkbenchExampleForm(
      org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )

    assertTrue(state.isDataCollectionFormOpen)
    val ctrl = assertNotNull(state.activeFormWizardController)
    assertEquals(0, ctrl.currentStepIndex)
    assertTrue(state.isCurrentFormStepGeoPoint)

    // Verify trigger labels for unpanned ("Capture location") vs panned ("Add point")
    assertEquals(
      "Capture location",
      org.groundplatform.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = false),
    )
    assertEquals(
      "Add point",
      org.groundplatform.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = true),
    )

    // Advance to the next step (select1 land_use) -> isCurrentFormStepGeoPoint becomes false
    assertTrue(ctrl.nextStep())
    assertFalse(state.isCurrentFormStepGeoPoint)
  }

  @Test
  fun allFieldTypesForm_launchesWithPhotoVideoAndAudioQuestions() {
    val workbench = WorkbenchState(MobileAppState())
    val state = workbench.mobile
    workbench.selectWorkbenchExampleForm(
      WorkbenchExampleForm.ALL_FIELD_TYPES,
      launchImmediately = true,
    )
    val kinds =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<FormWizardStep.QuestionStep>()
        .mapNotNull { it.control.mediaCapture?.kind }
    assertTrue(
      kinds.containsAll(
        listOf(MediaCaptureKind.PHOTO, MediaCaptureKind.VIDEO, MediaCaptureKind.AUDIO)
      ),
      kinds.toString(),
    )
  }

  @Test
  fun updateActiveSurveyDetails_updatesActiveSurveyTitleAndDescription() {
    val workbench = WorkbenchState(MobileAppState())
    workbench.updateActiveSurveyDetails(
      title = "Updated Survey Title",
      description = "Updated survey description from the survey editor.",
    )

    assertEquals("Updated Survey Title", workbench.mobile.activeSurvey.title)
    assertEquals(
      "Updated survey description from the survey editor.",
      workbench.mobile.activeSurvey.description,
    )
  }

  @Test
  fun reset_reseedsSampleData() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = AppScreen.MAIN_SURVEY))
    val state = workbench.mobile
    val initialEntities = state.entities
    state.entities =
      initialEntities +
        List(10) { i ->
          initialEntities.first().copy(id = "entity-extra-$i", submissions = emptyList())
        }
    state.syncAllOutboxMutations()
    assertEquals(initialEntities.size + 10, state.entities.size)

    workbench.resetSampleData()
    assertEquals("survey-kenya-coffee", state.activeSurveyId)
    assertEquals(initialEntities, state.entities)
    assertTrue(state.mutations.any { it.isOutbox })
  }

  @Test
  fun airplaneMode_takesTheMobileAppOffline() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = AppScreen.MAIN_SURVEY))
    val mobile = workbench.mobile

    workbench.updateAirplaneMode(true)
    assertFalse(mobile.isOnline)
    assertFalse(mobile.isPlacesSearchAvailable)
    assertEquals(ListFilterTab.ALL, mobile.listFilterTab)
    assertTrue(mobile.filteredListPlaces.isEmpty())
  }
}
