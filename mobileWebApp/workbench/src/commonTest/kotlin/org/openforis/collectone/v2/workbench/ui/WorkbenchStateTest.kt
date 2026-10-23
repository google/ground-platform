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
package org.openforis.collectone.v2.workbench.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.AppScreen
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.domain.model.ListFilterTab
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.core.forms.media.MediaCaptureKind
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.mobile.ui.MobileAppState

class WorkbenchStateTest {
  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun deviceFormFactor_togglesBetweenMobileAndTablet() {
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
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
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
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
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    assertEquals(null, workbench.xformsXmlError)
    assertTrue(state.customFormDef != null)

    // Launch form runner first
    state.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )
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
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    assertEquals(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      workbench.selectedWorkbenchExampleForm,
    )

    // 1. Simple Single-Point & Land Use Survey (pan allowed via placement-map, 10m accuracy
    // required, 3 entities via save_to, 0 standalone submissions)
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )
    assertEquals("00000036-1a1c-5c51-997f-28b67d813aa3", state.activeSurveyId)
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
        .filterIsInstance<org.openforis.collectone.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_point" }
    assertTrue(samplePointCtrl.appearance.contains("placement-map"))
    assertEquals(10.0, samplePointCtrl.controlDef.geo_config?.accuracy_threshold_meters)

    // 2. Predefined Sample Plots & Forest Assessment Survey (5 sample_plots entities, 3 preloaded
    // assessments)
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.SAMPLE_PLOTS_FOREST_ASSESSMENT,
      launchImmediately = true,
    )
    assertEquals("00000034-c088-5ab9-8b98-e6bccb3dca71", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals("00000016-cc1f-5125-8841-166ab5b5f68f", state.entities.first().id)
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
          sec.rows.any { row ->
            row.fields["name"]?.scalar_value?.string_value == "00000016-cc1f-5125-8841-166ab5b5f68f"
          }
      } == true
    )
    val plotSelectCtrl =
      state.activeFormWizardController!!
        .steps
        .filterIsInstance<org.openforis.collectone.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/sample_plot_entity" }
    assertEquals(5, plotSelectCtrl.options.size)
    assertEquals("00000016-cc1f-5125-8841-166ab5b5f68f", plotSelectCtrl.options.first().value)

    // 3. Commodity Plot Perimeter (walk + pan override) & Plot Center (no pan, <= 5m GPS accuracy,
    // 3 entities via save_to, 0 standalone submissions)
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.COMMODITY_PERIMETER_AND_CENTER,
      launchImmediately = true,
    )
    assertEquals("00000031-ffc6-5c8e-934c-d14ec07533ad", state.activeSurveyId)
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
        .filterIsInstance<org.openforis.collectone.v2.core.forms.ui.FormWizardStep.QuestionStep>()
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
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
        .HOUSEHOLD_SURVEY_PAST_INDIVIDUALS,
      launchImmediately = true,
    )
    assertEquals("00000032-8d62-5717-8074-f4917e0cd76d", state.activeSurveyId)
    assertEquals(6, state.entities.size)
    assertEquals("0000000c-a9ae-59f6-ac51-7f5a8777598f", state.entities.first().id)
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
        .filterIsInstance<org.openforis.collectone.v2.core.forms.ui.FormWizardStep.QuestionStep>()
        .map { it.control }
        .first { it.canonicalPath == "/data/primary_respondent_id" }
    assertEquals(6, respondentSelectCtrl.options.size)
    assertEquals("0000000c-a9ae-59f6-ac51-7f5a8777598f", respondentSelectCtrl.options.first().value)

    // 5. All Form Field Types Showcase ("EUDR & Shade-Tree Field Survey", 5 entities, 6 forms)
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      launchImmediately = true,
    )
    assertEquals("00000033-6b50-51cd-ac94-baea2ea06d65", state.activeSurveyId)
    assertEquals(5, state.entities.size)
    assertEquals(6, state.forms.size)
    assertEquals(null, workbench.xformsXmlError)
    val allTypesFormDef = state.customFormDef!!
    assertEquals("EUDR & Shade-Tree Field Survey", allTypesFormDef.title)
    assertTrue((allTypesFormDef.model?.bindings?.size ?: 0) >= 18)
  }

  @Test
  fun geoPointStepInDataCollectionForm_showsMapAndSupportsCaptureLocationAndAddPointTriggers() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )

    assertTrue(state.isDataCollectionFormOpen)
    val ctrl = assertNotNull(state.activeFormWizardController)
    assertEquals(0, ctrl.currentStepIndex)
    assertTrue(state.isCurrentFormStepGeoPoint)

    // Verify trigger labels for unpanned ("Capture location") vs panned ("Add point")
    assertEquals(
      "Capture location",
      org.openforis.collectone.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = false),
    )
    assertEquals(
      "Add point",
      org.openforis.collectone.v2.core.forms.ui.geoPointPrimaryTriggerLabel(isPanned = true),
    )

    // Capture a location on the required geopoint step, then advance to the next step (select1
    // land_use) -> isCurrentFormStepGeoPoint becomes false
    ctrl.updateGeoPoint("/data/sample_point", -1.292066, 36.821946, 1680.0, 6.4)
    assertTrue(ctrl.nextStep())
    assertFalse(state.isCurrentFormStepGeoPoint)
  }

  @Test
  fun allFieldTypesForm_launchesWithPhotoVideoAndAudioQuestions() {
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
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
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
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
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
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
    assertEquals("00000033-6b50-51cd-ac94-baea2ea06d65", state.activeSurveyId)
    assertEquals(initialEntities, state.entities)
    assertTrue(state.mutations.any { it.isOutbox })
  }

  @Test
  fun airplaneMode_takesTheMobileAppOffline() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialScreen = AppScreen.MAIN_SURVEY, dataHolder = sampleDataHolder())
      )
    val mobile = workbench.mobile

    workbench.updateAirplaneMode(true)
    assertFalse(mobile.isOnline)
    assertFalse(mobile.isPlacesSearchAvailable)
    assertEquals(ListFilterTab.ALL, mobile.listFilterTab)
    assertTrue(mobile.filteredListPlaces.isEmpty())
  }

  @Test
  fun shouldShowWorkbenchChrome_hidesOnMobileBrowsersAndRespectsUrlOverride() {
    val desktopUa =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    val androidUa =
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    val iphoneUa =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    val ipadDesktopClassUa =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"

    // Desktop browser defaults to showing workbench chrome, even on a touchscreen laptop
    assertTrue(shouldShowWorkbenchChrome(search = "", userAgent = desktopUa, maxTouchPoints = 0))
    assertTrue(shouldShowWorkbenchChrome(search = "", userAgent = desktopUa, maxTouchPoints = 10))

    // Mobile browsers default to hiding workbench chrome
    assertFalse(shouldShowWorkbenchChrome(search = "", userAgent = androidUa, maxTouchPoints = 5))
    assertFalse(shouldShowWorkbenchChrome(search = "", userAgent = iphoneUa, maxTouchPoints = 5))
    assertFalse(
      shouldShowWorkbenchChrome(search = "", userAgent = ipadDesktopClassUa, maxTouchPoints = 5)
    )
    assertFalse(
      shouldShowWorkbenchChrome(
        search = "",
        userAgent = desktopUa,
        maxTouchPoints = 1,
        isCoarsePointerWithoutHover = true,
      )
    )

    // Explicit ?chrome=0 / ?chrome=false hides chrome on desktop
    assertFalse(shouldShowWorkbenchChrome(search = "?chrome=0", userAgent = desktopUa))
    assertFalse(shouldShowWorkbenchChrome(search = "?foo=1&chrome=false", userAgent = desktopUa))

    // Explicit ?chrome=1 / ?chrome=true forces chrome on mobile
    assertTrue(shouldShowWorkbenchChrome(search = "?chrome=1", userAgent = androidUa))
    assertTrue(shouldShowWorkbenchChrome(search = "?foo=1&chrome=true", userAgent = iphoneUa))
  }
}
