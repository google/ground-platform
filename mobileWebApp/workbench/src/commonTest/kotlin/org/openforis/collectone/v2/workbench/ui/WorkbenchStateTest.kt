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
import org.openforis.collectone.v2.app.domain.model.DeviceFormFactor
import org.openforis.collectone.v2.app.domain.model.DeviceOrientation
import org.openforis.collectone.v2.app.ui.map.SurveyMap
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.core.forms.media.MediaCaptureKind
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

class WorkbenchStateTest {
  private fun SurveyMap.entityFeatures() =
    content.sources.first { it.id == SurveyMapContent.ENTITY_SOURCE }.features

  @Test
  fun deviceFormFactor_togglesBetweenMobileAndTablet() {
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
    val state = workbench.mobile

    // Defaults to Mobile (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, workbench.viewModel.uiState.value.deviceFormFactor)
    assertEquals(404, workbench.viewModel.uiState.value.deviceFormFactor.frameWidthDp)
    assertEquals(764, workbench.viewModel.uiState.value.deviceFormFactor.frameHeightDp)

    // Toggle to Tablet (780 × 620 dp)
    workbench.toggleDeviceFormFactor()
    assertEquals(DeviceFormFactor.TABLET, workbench.viewModel.uiState.value.deviceFormFactor)
    assertEquals(780, workbench.viewModel.uiState.value.deviceFormFactor.frameWidthDp)
    assertEquals(620, workbench.viewModel.uiState.value.deviceFormFactor.frameHeightDp)

    // Explicitly select Mobile
    workbench.selectDeviceFormFactor(DeviceFormFactor.MOBILE)
    assertEquals(DeviceFormFactor.MOBILE, workbench.viewModel.uiState.value.deviceFormFactor)
  }

  @Test
  fun rotateDevice_rotatesMobileAndTabletBetweenPortraitAndLandscape() {
    val workbench = WorkbenchState(MobileAppState(dataHolder = sampleDataHolder()))
    val state = workbench.mobile

    // Mobile defaults to Portrait (404 × 764 dp)
    assertEquals(DeviceFormFactor.MOBILE, workbench.viewModel.uiState.value.deviceFormFactor)
    assertEquals(DeviceOrientation.PORTRAIT, workbench.viewModel.uiState.value.deviceOrientation)
    assertFalse(workbench.viewModel.uiState.value.isDeviceRotated)
    assertEquals(404, workbench.viewModel.uiState.value.effectiveFrameWidthDp)
    assertEquals(764, workbench.viewModel.uiState.value.effectiveFrameHeightDp)
    assertEquals("404 × 764 dp", workbench.viewModel.uiState.value.effectiveDimensionsLabel)

    // Rotate Mobile -> Landscape (764 × 404 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.LANDSCAPE, workbench.viewModel.uiState.value.deviceOrientation)
    assertTrue(workbench.viewModel.uiState.value.isDeviceRotated)
    assertEquals(764, workbench.viewModel.uiState.value.effectiveFrameWidthDp)
    assertEquals(404, workbench.viewModel.uiState.value.effectiveFrameHeightDp)
    assertEquals("764 × 404 dp", workbench.viewModel.uiState.value.effectiveDimensionsLabel)

    // Rotate Mobile back -> Portrait (404 × 764 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, workbench.viewModel.uiState.value.deviceOrientation)
    assertFalse(workbench.viewModel.uiState.value.isDeviceRotated)
    assertEquals(404, workbench.viewModel.uiState.value.effectiveFrameWidthDp)
    assertEquals(764, workbench.viewModel.uiState.value.effectiveFrameHeightDp)

    // Switch to Tablet -> defaults to Landscape (780 × 620 dp)
    workbench.selectDeviceFormFactor(DeviceFormFactor.TABLET)
    assertEquals(DeviceFormFactor.TABLET, workbench.viewModel.uiState.value.deviceFormFactor)
    assertEquals(DeviceOrientation.LANDSCAPE, workbench.viewModel.uiState.value.deviceOrientation)
    assertFalse(workbench.viewModel.uiState.value.isDeviceRotated)
    assertEquals(780, workbench.viewModel.uiState.value.effectiveFrameWidthDp)
    assertEquals(620, workbench.viewModel.uiState.value.effectiveFrameHeightDp)
    assertEquals("780 × 620 dp", workbench.viewModel.uiState.value.effectiveDimensionsLabel)

    // Rotate Tablet -> Portrait (620 × 780 dp)
    workbench.rotateDevice()
    assertEquals(DeviceOrientation.PORTRAIT, workbench.viewModel.uiState.value.deviceOrientation)
    assertTrue(workbench.viewModel.uiState.value.isDeviceRotated)
    assertEquals(620, workbench.viewModel.uiState.value.effectiveFrameWidthDp)
    assertEquals(780, workbench.viewModel.uiState.value.effectiveFrameHeightDp)
    assertEquals("620 × 780 dp", workbench.viewModel.uiState.value.effectiveDimensionsLabel)
  }

  @Test
  fun updateCustomXFormsXml_parsesValidAndInvalidXForms_andRefreshesActiveController() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    assertTrue(state.viewModels.dataCollection.uiState.value.customFormDef != null)

    // Launch form runner first
    state.viewModels.dataCollection.launchFormForEntity(
      entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
      formId = "form-shade-canopy-audit",
    )
    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)

    // 1. Update with valid Baobab Biometrics XForms XML -> refreshes open FormWizardController
    workbench.updateCustomXFormsXml(BAOBAB_BIOMETRICS_SAMPLE_XFORMS_XML)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    assertEquals(
      "Shade Tree & Baobab Biometrics",
      state.viewModels.dataCollection.uiState.value.customFormDef?.title,
    )
    assertEquals(
      "Shade Tree & Baobab Biometrics",
      state.viewModels.dataCollection.uiState.value.activeFormWizardController
        ?.formState
        ?.formDef
        ?.title,
    )

    // 2. Update with invalid XML -> populates xformsXmlError and clears customFormDef
    workbench.updateCustomXFormsXml("<h:html><unclosed_tag></h:html>")
    assertTrue(workbench.viewModel.uiState.value.xformsXmlError != null)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.customFormDef)

    // 3. Reset to default sample XForms XML -> clears error and restores default FormDef
    workbench.resetDefaultXFormsXml()
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    assertEquals(
      "EUDR & Shade-Tree Field Survey",
      state.viewModels.dataCollection.uiState.value.customFormDef?.title,
    )

    // 4. Clearing customXFormsXml uses built-in fallback FormDef per form ID
    workbench.updateCustomXFormsXml("")
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    assertEquals(null, state.viewModels.dataCollection.uiState.value.customFormDef)
    assertEquals(
      "Seasonal Shade Tree & Canopy Audit",
      state.viewModels.dataCollection.uiState.value.activeFormWizardController
        ?.formState
        ?.formDef
        ?.title,
    )
    state.viewModels.dataCollection.closeActiveFormRunner()
    assertFalse(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
  }

  @Test
  fun selectWorkbenchExampleForm_swapsAndValidatesAllFiveExampleForms() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    assertEquals(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.ALL_FIELD_TYPES,
      workbench.viewModel.uiState.value.selectedWorkbenchExampleForm,
    )

    // 1. Simple Single-Point & Land Use Survey (pan allowed via placement-map, 10m accuracy
    // required, 3 entities via save_to, 0 standalone submissions)
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )
    assertEquals(
      "00000036-1a1c-5c51-997f-28b67d813aa3",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(3, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(0, state.viewModels.dataCollection.uiState.value.standaloneSubmissions.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.allSubmissions.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.submissionGeometries.size)
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
    assertFalse(state.viewModels.dataCollection.uiState.value.forms.first().requiresEntity)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    val singlePointFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
    assertEquals("Simple Point & Land Use Observation", singlePointFormDef.title)
    val samplePointCtrl =
      state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
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
    assertEquals(
      "00000034-c088-5ab9-8b98-e6bccb3dca71",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(5, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(
      "00000016-cc1f-5125-8841-166ab5b5f68f",
      state.viewModels.dataCollection.uiState.value.entities.first().id,
    )
    assertEquals(3, state.viewModels.dataCollection.uiState.value.allSubmissions.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.submissionGeometries.size)
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
    assertTrue(state.viewModels.dataCollection.uiState.value.forms.first().requiresEntity)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    val samplePlotsFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
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
      state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
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
    assertEquals(
      "00000031-ffc6-5c8e-934c-d14ec07533ad",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(3, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(0, state.viewModels.dataCollection.uiState.value.standaloneSubmissions.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.allSubmissions.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.submissionGeometries.size)
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
    assertFalse(state.viewModels.dataCollection.uiState.value.forms.first().requiresEntity)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    val commodityFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
    assertEquals("Forest-Risk Commodity Plot Perimeter & Center Mapping", commodityFormDef.title)
    val commodityQuestionControls =
      state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
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
    assertEquals(
      "00000032-8d62-5717-8074-f4917e0cd76d",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(6, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(
      "0000000c-a9ae-59f6-ac51-7f5a8777598f",
      state.viewModels.dataCollection.uiState.value.entities.first().id,
    )
    assertEquals(4, state.viewModels.dataCollection.uiState.value.allSubmissions.size)
    assertEquals(2, state.viewModels.dataCollection.uiState.value.submissionGeometries.size)
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
    assertTrue(state.viewModels.dataCollection.uiState.value.forms.first().requiresEntity)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    val householdFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
    assertEquals("Household Panel Survey (Preloaded Past Individuals)", householdFormDef.title)
    val respondentSelectCtrl =
      state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
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
    assertEquals(
      "00000033-6b50-51cd-ac94-baea2ea06d65",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(5, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(6, state.viewModels.dataCollection.uiState.value.forms.size)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    val allTypesFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
    assertEquals("EUDR & Shade-Tree Field Survey", allTypesFormDef.title)
    assertTrue((allTypesFormDef.model?.bindings?.size ?: 0) >= 18)

    // 6. Google Earth Sample-Plot Interpretation ("Peatland & Canopy Change Interpretation",
    // 5 polygon plots, 3 preloaded submissions, strictly Earth-compatible fields)
    workbench.selectWorkbenchExampleForm(
      WorkbenchExampleForm.GOOGLE_EARTH_PLOT_INTERPRETATION,
      launchImmediately = true,
    )
    assertEquals(
      "00000037-7e4a-5b2c-9d18-3f8a61c4e902",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(5, state.viewModels.dataCollection.uiState.value.entities.size)
    assertEquals(3, state.viewModels.dataCollection.uiState.value.allSubmissions.size)
    assertEquals(1, state.viewModels.dataCollection.uiState.value.forms.size)
    assertTrue(state.viewModels.dataCollection.uiState.value.forms.first().openInGoogleEarth)
    assertTrue(state.viewModels.dataCollection.uiState.value.forms.first().requiresEntity)
    assertEquals(null, workbench.viewModel.uiState.value.xformsXmlError)
    val earthFormDef = state.viewModels.dataCollection.uiState.value.customFormDef!!
    assertEquals("Peatland & Canopy Change Interpretation (Google Earth)", earthFormDef.title)
  }

  @Test
  fun geoPointStepInDataCollectionForm_showsMapAndSupportsCaptureLocationAndAddPointTriggers() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    workbench.selectWorkbenchExampleForm(
      org.openforis.collectone.v2.core.forms.ui.WorkbenchExampleForm.SINGLE_POINT_LAND_USE,
      launchImmediately = true,
    )

    assertTrue(state.viewModels.dataCollection.uiState.value.isDataCollectionFormOpen)
    val ctrl =
      assertNotNull(state.viewModels.dataCollection.uiState.value.activeFormWizardController)
    assertEquals(0, ctrl.currentStepIndex)
    assertTrue(state.viewModels.dataCollection.uiState.value.isCurrentFormStepGeoPoint)

    // Capture a location on the required geopoint step, then advance to the next step (select1
    // land_use) -> isCurrentFormStepGeoPoint becomes false
    ctrl.updateGeoPoint("/data/sample_point", -1.292066, 36.821946, 1680.0, 6.4)
    assertTrue(ctrl.nextStep())
    assertFalse(state.viewModels.dataCollection.uiState.value.isCurrentFormStepGeoPoint)
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
      state.viewModels.dataCollection.uiState.value.activeFormWizardController!!
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

    assertEquals("Updated Survey Title", workbench.mobile.activeSurveyAccess.activeSurvey.title)
    assertEquals(
      "Updated survey description from the survey editor.",
      workbench.mobile.activeSurveyAccess.activeSurvey.description,
    )
  }

  @Test
  fun reset_reseedsSampleData() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val state = workbench.mobile
    val initialEntities = state.viewModels.dataCollection.uiState.value.entities
    val added =
      List(10) { i ->
        initialEntities.first().copy(id = "entity-extra-$i", submissions = emptyList())
      }
    state.dataHolder.launch {
      state.dataHolder.surveyRepository.setEntities(initialEntities + added)
    }
    state.viewModels.dashboard.syncAllOutboxMutations()
    assertEquals(
      initialEntities.size + 10,
      state.viewModels.dataCollection.uiState.value.entities.size,
    )

    workbench.resetSampleData()
    assertEquals(
      "00000033-6b50-51cd-ac94-baea2ea06d65",
      state.viewModels.dashboard.uiState.value.activeSurveyId,
    )
    assertEquals(initialEntities, state.viewModels.dataCollection.uiState.value.entities)
    assertTrue(state.viewModels.dashboard.uiState.value.mutations.any { it.isOutbox })
  }

  @Test
  fun airplaneMode_takesTheMobileAppOffline() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())
      )
    val mobile = workbench.mobile

    workbench.updateAirplaneMode(true)
    assertFalse(mobile.viewModels.surveyMap.uiState.value.isOnline)
    assertFalse(mobile.viewModels.surveyMap.uiState.value.isPlacesSearchAvailable)
    assertTrue(mobile.viewModels.listSearch.uiState.value.places.isEmpty())
  }

  @Test
  fun airplaneModeOnSignIn_doesNotEmitPlacesNoticesAndClearsOnReconnect() {
    val workbench =
      WorkbenchState(
        MobileAppState(initialRoute = MobileRoute.SignIn, dataHolder = sampleDataHolder())
      )
    val mobile = workbench.mobile

    workbench.updateAirplaneMode(true)
    assertFalse(mobile.viewModels.onboarding.uiState.value.isOnline)
    assertEquals(null, mobile.notices.notice.value)

    mobile.viewModels.onboarding.signInWithGoogle()
    assertTrue(mobile.notices.notice.value?.startsWith("Cannot sign in") == true)

    workbench.updateAirplaneMode(false)
    assertTrue(mobile.viewModels.onboarding.uiState.value.isOnline)
    assertEquals(null, mobile.notices.notice.value)
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

  @Test
  fun downloadUrls_resolveFromHostOriginAndIgnoreBlank() {
    assertEquals(
      "https://mobile.example.org/collect-one.apk",
      apkDownloadUrl("https://mobile.example.org"),
    )
    assertEquals(
      "https://mobile.example.org/collect-one-ios.html",
      iosDownloadUrl("https://mobile.example.org"),
    )
    assertEquals("https://example.com/collect-one.apk", apkDownloadUrl("https://example.com/"))
    assertEquals("https://example.com/collect-one-ios.html", iosDownloadUrl("https://example.com/"))
    assertEquals("", apkDownloadUrl(""))
    assertEquals("", iosDownloadUrl(""))
  }
}
