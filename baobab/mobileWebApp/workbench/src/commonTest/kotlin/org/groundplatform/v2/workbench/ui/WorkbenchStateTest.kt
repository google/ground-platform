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
import org.groundplatform.v2.app.domain.model.DeviceFormFactor
import org.groundplatform.v2.app.domain.model.DeviceOrientation
import org.groundplatform.v2.app.domain.model.MainSurveyViewMode
import org.groundplatform.v2.app.domain.model.PrototypeScreen
import org.groundplatform.v2.app.ui.map.SurveyMap
import org.groundplatform.v2.app.ui.map.SurveyMapContent
import org.groundplatform.v2.app.ui.map.SurveyMapIds
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
    val workbench = WorkbenchState(MobileAppState(initialScreen = PrototypeScreen.MAIN_SURVEY))
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
  fun addRandomSites_appendsFiveThousandPolygonsEachClickAndScalesClustering() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = PrototypeScreen.MAIN_SURVEY))
    val state = workbench.mobile
    assertEquals(5, state.entities.size)

    // First click: +5,000 features -> 5,005 total features
    workbench.addRandomSites(count = 5_000)
    assertEquals(5_005, state.entities.size)
    assertEquals(5_005, state.activeSurvey.entityCount)
    assertEquals(PrototypeScreen.MAIN_SURVEY, state.currentScreen)
    assertEquals(MainSurveyViewMode.MAP, state.mainViewMode)
    assertTrue(state.entities.any { it.id == "entity-rnd-6" && it.geometryTypeLabel == "Polygon" })
    assertTrue(state.activeSurveyNotice?.contains("5000") == true)

    // Second click: +5,000 features -> 10,005 total features (>10,000 polygons)
    workbench.addRandomSites(count = 5_000)
    assertEquals(10_005, state.entities.size)
    assertEquals(10_005, state.activeSurvey.entityCount)
    assertTrue(state.entities.any { it.id == "entity-rnd-10005" })

    // Verify spatial clustering handles >10,000 features across [-2.2, 3.2] x [-2.2, 3.2] and
    // progressively covers a broader and broader area as the user zooms out further
    repeat(3) { state.zoomOutMap() } // -2.25f: not yet clustered
    assertFalse(state.isMapClusteringActive)

    state.zoomOutMap() // -3.00f
    assertTrue(state.isMapClusteringActive)
    val clustersAtStep1 = state.mapFeatureClusters
    assertTrue(clustersAtStep1.size > 10)
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep1.sumOf { it.totalCount })

    state.zoomOutMap() // -3.75f
    val clustersAtStep2 = state.mapFeatureClusters
    assertTrue(clustersAtStep2.size < clustersAtStep1.size)
    assertTrue(
      clustersAtStep2.maxOf { it.diameterNormalized } >
        clustersAtStep1.maxOf { it.diameterNormalized }
    )
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep2.sumOf { it.totalCount })

    state.zoomOutMap() // -4.50f
    val clustersAtStep3 = state.mapFeatureClusters
    assertTrue(clustersAtStep3.size < clustersAtStep2.size)
    assertTrue(
      clustersAtStep3.maxOf { it.diameterNormalized } >
        clustersAtStep2.maxOf { it.diameterNormalized }
    )
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep3.sumOf { it.totalCount })

    state.zoomOutMap() // -5.25f
    val clustersAtStep4 = state.mapFeatureClusters
    assertTrue(clustersAtStep4.size <= clustersAtStep3.size)
    assertEquals(state.visibleMapClusterFeatures.size, clustersAtStep4.sumOf { it.totalCount })

    // Zoom out to regional view (-6.00f, ~9.3z): all 10,005 features across [-2.2, 3.2] merge into
    // a single regional cluster balloon covering the entire 5.4 x 5.4 area, including LineStrings
    state.zoomOutMap() // -6.00f
    val clustersAtDeepZoomOut = state.mapFeatureClusters
    assertEquals(1, clustersAtDeepZoomOut.size)
    assertEquals(10_005, clustersAtDeepZoomOut.first().siteCount)
    assertTrue(clustersAtDeepZoomOut.first().features.any { it.id == "entity-shade-201" })
    assertTrue(
      clustersAtDeepZoomOut.first().diameterNormalized >
        clustersAtStep3.maxOf { it.diameterNormalized }
    )

    val clusteredMap = SurveyMapContent.main(state, showNavigation = true)
    assertTrue(clusteredMap.entityFeatures().isEmpty())
    assertTrue(clusteredMap.markers.keys.any { SurveyMapIds.clusterIdOf(it) != null })
  }

  @Test
  fun selectWorkbenchExampleForm_swapsAndValidatesAllFiveExampleForms() {
    val workbench = WorkbenchState(MobileAppState(initialScreen = PrototypeScreen.MAIN_SURVEY))
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
    val workbench = WorkbenchState(MobileAppState(initialScreen = PrototypeScreen.MAIN_SURVEY))
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
}
