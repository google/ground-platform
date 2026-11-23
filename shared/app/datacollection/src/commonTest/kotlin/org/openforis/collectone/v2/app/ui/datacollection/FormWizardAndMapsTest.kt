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
package org.openforis.collectone.v2.app.ui.datacollection

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.DataType
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.usecase.FormLaunch
import org.openforis.collectone.v2.app.ui.map.SurveyMapContent
import org.openforis.collectone.v2.app.ui.map.SurveyMapUiState
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.GeoPointMapViewportState
import org.openforis.collectone.v2.core.forms.ui.MapDrawingKind
import org.openforis.collectone.v2.map.CameraPosition
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

/**
 * Tests the form-wizard launch helpers ([FormWizardLaunch.kt]), runner and answer display adapters
 * ([WizardFormRunner], [WizardAnswerDisplay]), survey map content bridges
 * ([DataCollectionMapContent.kt]), and GeoPoint camera math ([geoPointCamera],
 * [writeBackGeoPointCamera]).
 */
class FormWizardAndMapsTest {
  private val wizardXml =
    """
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
      <h:head>
        <h:title>Field Log</h:title>
        <model>
          <instance>
            <data id="field_log" version="1">
              <target_entity/>
              <sample_plot_entity/>
              <location/>
              <details>
                <observer/>
                <unlabeled/>
              </details>
              <trees>
                <species/>
              </trees>
            </data>
          </instance>
          <bind nodeset="/data/target_entity" type="string"/>
          <bind nodeset="/data/sample_plot_entity" type="string"/>
          <bind nodeset="/data/location" type="geopoint"/>
          <bind nodeset="/data/details/observer" type="string"/>
          <bind nodeset="/data/details/unlabeled" type="int"/>
          <bind nodeset="/data/trees/species" type="string"/>
        </model>
      </h:head>
      <h:body>
        <input ref="/data/target_entity" appearance="map-select"><label>Target Parcel</label></input>
        <input ref="/data/sample_plot_entity"><label>Sample Plot</label></input>
        <input ref="/data/location"><label>Location</label></input>
        <group ref="/data/details" appearance="field-list">
          <label>Details</label>
          <input ref="/data/details/observer"><label>Observer</label></input>
          <input ref="/data/details/unlabeled"/>
        </group>
        <group ref="/data/trees">
          <label>Trees</label>
          <repeat nodeset="/data/trees">
            <input ref="/data/trees/species"><label>Species</label></input>
          </repeat>
        </group>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun parseFormDef() = XFormsXmlSerializer.deserializeFormDef(wizardXml)

  private fun entity(id: String, datasetId: String = "plots") =
    GeospatialEntityItem(
      id = id,
      label = "Plot $id",
      datasetId = datasetId,
      datasetName = "Plots",
      layerId = "layer-plots",
      geoId = "GEO-$id",
      geometry = Geometry.Point(LatLng(-0.4, 36.9)),
      areaHectares = 0.0,
      perimeterMeters = 0,
      colorHex = 0xFF2E7D32L,
      properties = emptyMap(),
      submissions = emptyList(),
    )

  private fun formPreview(requiresEntity: Boolean = true) =
    FormPreviewItem(
      id = "form-plots",
      title = "Plot Check",
      description = "",
      version = "1",
      targetDatasetId = if (requiresEntity) "plots" else "",
      targetDatasetName = if (requiresEntity) "Plots" else "",
      questionCount = 3,
      ctaLabel = "Check",
      requiresEntity = requiresEntity,
    )

  @Test
  fun formWizardFor_prepopulatesPresentEntityReferenceFieldsOnlyWhenEntityIdGiven() {
    val formDef = parseFormDef()

    val withoutEntity =
      formWizardFor(FormLaunch(formDef = formDef, entityId = null), FormEnvironment.DEFAULT)
    assertEquals(
      "",
      withoutEntity.formState.fieldStates[ENTITY_REF_FIELD_PATH]
        ?.value
        ?.scalar_value
        ?.string_value
        .orEmpty(),
    )

    val withEntity =
      formWizardFor(FormLaunch(formDef = formDef, entityId = "plot-42"), FormEnvironment.DEFAULT)
    assertEquals(
      "plot-42",
      withEntity.formState.fieldStates[ENTITY_REF_FIELD_PATH]?.value?.scalar_value?.string_value,
    )
    assertEquals(
      "plot-42",
      withEntity.formState.fieldStates["/data/sample_plot_entity"]
        ?.value
        ?.scalar_value
        ?.string_value,
    )
  }

  @Test
  fun stepPredicates_distinguishEntityRefGeometryAndPlainSteps() {
    val controller = FormWizardController(formDef = parseFormDef())
    val steps = controller.steps

    assertFalse(isEntityRefStep(null))
    assertFalse(isWizardStepEntityRef(null))
    assertFalse(isGeometryStep(null))
    assertFalse(isWizardStepGeoPoint(null))

    val targetStep = steps.first { isEntityRefStep(it) }
    assertTrue(isWizardStepEntityRef(targetStep))
    assertFalse(isGeometryStep(targetStep))

    val geoStep = steps.first { isGeometryStep(it) }
    assertTrue(isWizardStepGeoPoint(geoStep))
    assertFalse(isEntityRefStep(geoStep))

    val fieldListStep = steps.first { !isEntityRefStep(it) && !isGeometryStep(it) }
    assertFalse(isWizardStepEntityRef(fieldListStep))
    assertFalse(isWizardStepGeoPoint(fieldListStep))
  }

  @Test
  fun ensureEntityRefStepAndResolveFormDefForLaunch_injectEntityRefStepWhenRequested() {
    val simpleXml =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Notes</h:title>
          <model>
            <instance><data id="notes" version="1"><note/></data></instance>
            <bind nodeset="/data/note" type="string"/>
          </model>
        </h:head>
        <h:body><input ref="/data/note"><label>Note</label></input></h:body>
      </h:html>
      """
        .trimIndent()
    val base = XFormsXmlSerializer.deserializeFormDef(simpleXml)
    val candidates = listOf(entity("p1"), entity("p2"))

    val injected =
      ensureEntityRefStepInFormDef(
        baseFormDef = base,
        form = formPreview(requiresEntity = true),
        candidateEntities = candidates,
        defaultSelectedEntityId = "p2",
      )
    val injectedCtrl = FormWizardController(formDef = injected)
    assertTrue(ENTITY_REF_FIELD_PATH in injectedCtrl.formState.fieldStates)
    assertEquals(
      "p2",
      injectedCtrl.formState.fieldStates[ENTITY_REF_FIELD_PATH]?.value?.scalar_value?.string_value,
    )

    val resolvedWithoutStep =
      resolveFormDefForLaunch(
        customFormDef = base,
        form = formPreview(requiresEntity = true),
        candidateEntities = candidates,
        includeEntityRefStep = false,
      )
    assertFalse(
      ENTITY_REF_FIELD_PATH in
        FormWizardController(formDef = resolvedWithoutStep).formState.fieldStates
    )
  }

  @Test
  fun wizardFormRunnerAndAnswerDisplay_finalizeAndFormatQuestionLabels() {
    val controller = FormWizardController(formDef = parseFormDef())
    controller.updateString("/data/details/observer", "Ada")
    controller.addRepeatInstanceAndOpen("/data/trees")
    val runner = WizardFormRunner(controller)

    assertEquals(controller.formState, runner.formState)
    assertIs<FinalizationResult.Success>(runner.finalizeForm())

    val labels = WizardAnswerDisplay.questionLabels(runner.formState)
    assertEquals("Target Parcel", labels[ENTITY_REF_FIELD_PATH])
    assertEquals("Observer", labels["/data/details/observer"])
    // Unlabeled control inside a field-list falls back to the leaf node name.
    assertEquals("unlabeled", labels["/data/details/unlabeled"])
    // Repeat child is prefixed with its repeat instance label.
    val speciesLabel = labels.entries.first { it.key.endsWith("/species") }.value
    assertTrue(speciesLabel.endsWith("Species"), "Got: $speciesLabel")

    assertEquals(
      "Ada",
      WizardAnswerDisplay.displayValue(
        runner.formState.fieldStates["/data/details/observer"]?.value,
        DataType.TYPE_STRING,
      ),
    )
  }

  @Test
  fun surveyMapContentExtensions_bridgeDataCollectionStateIntoSurveyMaps() {
    val p1 = entity("p1", datasetId = "plots")
    val t1 = entity("t1", datasetId = "trees")
    val form = formPreview(requiresEntity = true)
    val controller = FormWizardController(formDef = parseFormDef())
    val drawing = WebMapDrawingHost(controller = { controller })
    drawing.startDrawing("/data/location", MapDrawingKind.POINT)

    val mapUi = SurveyMapUiState(entities = listOf(p1, t1), visibleMapEntities = listOf(p1, t1))
    val dcUi =
      DataCollectionUiState(
        forms = listOf(form),
        entities = listOf(p1, t1),
        activeFormWizardController = controller,
        activeDataCollectionEntityId = "p1",
        activeDataCollectionFormId = form.id,
        webMapDrawing = drawing,
      )

    val mainMap =
      SurveyMapContent.main(
        uiState = mapUi,
        dataCollection = dcUi,
        pendingIds = setOf("p1"),
        showNavigation = false,
      )
    val entityRefMap =
      SurveyMapContent.entityRefForm(
        uiState = mapUi,
        dataCollection = dcUi,
        form = form,
        pendingIds = emptySet(),
      )
    val geoPointMap =
      SurveyMapContent.geoPointForm(
        uiState = mapUi,
        dataCollection = dcUi,
        pendingIds = emptySet(),
        isFollowingUser = true,
      )

    assertTrue(mainMap.content.sources.isNotEmpty())
    assertTrue(entityRefMap.content.sources.isNotEmpty())
    assertTrue(geoPointMap.content.sources.isNotEmpty())
  }

  @Test
  fun geoPointCamera_andWriteBack_respectPanPermissionThresholdsAndZoomClamp() {
    val mapUi = SurveyMapUiState()
    val gps = gpsLatLng(mapUi)
    val panCalls = mutableListOf<Pair<Float, Float>>()
    val zoomCalls = mutableListOf<Float>()

    fun viewport(panAllowed: Boolean = true, zoom: Float = 17.5f) =
      GeoPointMapViewportState(
        path = "/data/location",
        panAllowed = panAllowed,
        isPanned = false,
        liveGpsLatitude = gps.latitude,
        liveGpsLongitude = gps.longitude,
        targetLatitude = gps.latitude,
        targetLongitude = gps.longitude,
        accuracyMeters = 4.0,
        capturedPoint = null,
        panOffsetLat = 0.001,
        panOffsetLon = -0.002,
        zoomLevel = zoom,
        onPanDeltaPixels = { dx, dy, w, h ->
          assertEquals(1f, w)
          assertEquals(1f, h)
          panCalls += dx to dy
        },
        onZoomDelta = { zoomCalls += it },
        onRecenterGps = {},
      )

    val vp = viewport()
    val expected = geoPointCamera(mapUi, vp)
    assertTrue(abs(expected.center.latitude - (gps.latitude + 0.001)) < 1e-9)
    assertTrue(abs(expected.center.longitude - (gps.longitude - 0.002)) < 1e-9)
    assertEquals(17.5, expected.zoom)

    // Sub-threshold drift triggers neither callback.
    writeBackGeoPointCamera(mapUi, vp, expected)
    assertTrue(panCalls.isEmpty())
    assertTrue(zoomCalls.isEmpty())

    // Disallowed pan still writes back zoom changes, not pan deltas.
    writeBackGeoPointCamera(
      mapUi,
      viewport(panAllowed = false),
      CameraPosition(
        center = LatLng(expected.center.latitude + 0.0016, expected.center.longitude - 0.0016),
        zoom = 18.5,
      ),
    )
    assertTrue(panCalls.isEmpty())
    assertEquals(listOf(1.0f), zoomCalls)

    // At zoom 17.5, spanDegrees = 0.0016; +0.0016 lat -> dy = +1, +0.0016 lon -> dx = -1.
    writeBackGeoPointCamera(
      mapUi,
      vp,
      CameraPosition(
        center = LatLng(expected.center.latitude + 0.0016, expected.center.longitude + 0.0016),
        zoom = 17.5,
      ),
    )
    val (dx, dy) = panCalls.single()
    assertTrue(abs(dx - (-1f)) < 1e-4, "dx=$dx")
    assertTrue(abs(dy - 1f) < 1e-4, "dy=$dy")

    // Extreme zoom values clamp to 13..20 when computing spanDegrees.
    panCalls.clear()
    val zoomedOut = viewport(zoom = 5f)
    val baseOut = geoPointCamera(mapUi, zoomedOut)
    writeBackGeoPointCamera(
      mapUi,
      zoomedOut,
      CameraPosition(
        center = LatLng(baseOut.center.latitude + 0.0016, baseOut.center.longitude),
        zoom = 5.0,
      ),
    )
    assertEquals(1, panCalls.size)
  }
}
