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
package org.openforis.collectone.v2.app.domain.usecase

import com.google.type.Date
import com.google.type.TimeOfDay
import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.EntityDeclaration
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.domain.model.AnswerDisplay
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MeasurementUnit
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SubmissionNotice
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.messageKey
import org.openforis.collectone.v2.app.testing.FakeClock
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer
import org.openforis.collectone.v2.core.geo.GeometryProtos
import org.openforis.collectone.v2.core.geo.geoid.AgStackGeoId
import org.openforis.collectone.v2.map.GeometryType

class CompleteFormSubmissionUseCaseTest {
  private val formDef: FormDef = XFormsXmlSerializer.deserializeFormDef(PLOT_VISIT_XFORM)
  private val clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000L))
  private val surveys = FakeSurveyRepository()
  private val mutations = FakeMutationRepository()
  private val transactions = CountingTransactionRunner()
  private var nextId = 0
  private val useCase =
    CompleteFormSubmissionUseCase(
      surveyRepository = surveys,
      mutationRepository = mutations,
      transactionRunner = transactions,
      answerDisplay = LabelsFromPathsAnswerDisplay,
      clock = clock,
      newId = { "id-${++nextId}" },
    )

  private fun record(vararg answers: Pair<String, String>): RecordInstance {
    val session = FormSession(formDef)
    answers.forEach { (path, value) -> session.updateString(path, value) }
    return session.state.recordInstance
  }

  private fun complete(
    recordInstance: RecordInstance = record("/data/land_use" to "Forest (closed)"),
    entityStates: List<EntityState> = emptyList(),
    activeFormId: String? = null,
    activeEntityId: String? = null,
    selectedEntityId: String? = null,
    launchedWithoutEntity: Boolean = true,
  ) = runNow {
    useCase(
      recordInstance = recordInstance,
      entityStates = entityStates,
      formRunner = null,
      customFormDef = formDef,
      activeDataCollectionFormId = activeFormId,
      activeDataCollectionEntityId = activeEntityId,
      selectedEntityId = selectedEntityId,
      wasFormLaunchedWithoutEntity = launchedWithoutEntity,
      signedInUserName = "Ana",
      signedInUserEmail = "ana@example.org",
      userGpsCoordinatesLabel = "12.30, 4.50 (±3 m)",
      userGpsNormalizedX = 0.5f,
      userGpsNormalizedY = 0.5f,
      gnssAccuracy = Measurement(3.0, MeasurementUnit.METER, decimals = 0),
    )
  }

  @Test
  fun noForms_returnsNullAndChangesNothing() {
    val result = complete()

    assertNull(result)
    assertTrue(mutations.mutations.isEmpty())
    assertTrue(surveys.standaloneSubmissions.isEmpty())
  }

  @Test
  fun formWithoutDataset_recordsAStandaloneSubmission() {
    surveys.forms = listOf(form(targetDatasetId = ""))

    val result = assertNotNull(complete())

    val submission = surveys.standaloneSubmissions.single()
    assertEquals("id-1", submission.id)
    assertEquals("", submission.entityId)
    assertEquals("Plot visit", submission.formTitle)
    assertEquals("7", submission.formVersion)
    assertEquals("Ana", submission.collectorName)
    assertEquals(clock.current, submission.clientUpdatedAt)
    assertEquals(SyncStatus.UPLOADING, submission.syncStatus)
    assertEquals(
      listOf(Triple("land_use", "Land use", "Forest (closed)")),
      submission.fields.map { Triple(it.questionName, it.questionLabel, it.answerValue) },
    )
    val mutation = mutations.mutations.single()
    assertEquals("mut-sub-id-1", mutation.id)
    assertEquals(MutationOperationKind.CREATE_SUBMISSION, mutation.operationKind)
    assertEquals("Standalone Field Log (12.30, 4.50)", mutation.targetLabel)
    assertEquals("2027-01-15 08:00:00 UTC", mutation.operationTimestamp)
    assertEquals("2027-01-15 08:00:00 UTC", mutation.startedTimestamp)
    assertNull(result.selectedEntityId)
    assertEquals("id-1", result.selectedSubmissionId)
    assertEquals(SubmissionNotice.STANDALONE_SUBMITTED, result.notice.messageKey)
    assertEquals(
      listOf<Any>("Plot visit", "12.30, 4.50"),
      (result.notice as DomainMessage.Text).args,
    )
  }

  @Test
  fun completesInsideASingleTransaction() {
    surveys.forms = listOf(form(targetDatasetId = ""))

    complete()

    assertEquals(1, transactions.transactions)
  }

  @Test
  fun emptyRecord_storesTheFormActionAsTheOnlyField() {
    surveys.forms = listOf(form(targetDatasetId = ""))

    complete(recordInstance = record())

    val field = surveys.standaloneSubmissions.single().fields.single()
    assertEquals("organizer_action_cta", field.questionName)
    assertEquals("Completed Form Action (Visit)", field.questionLabel)
    assertEquals("Verified in field (±3.0 m)", field.answerValue)
  }

  @Test
  fun entityCreateDeclaration_createsAMapFeatureFromTheSaveToProperties() {
    surveys.forms = listOf(form(targetDatasetId = "plots", requiresEntity = false))
    surveys.mapLayers = listOf(layer(id = "layer-plots", colorHex = 0xFF112233))
    val create =
      EntityState(
        dataset = "plots",
        declaration = EntityDeclaration(),
        shouldCreate = true,
        shouldUpdate = false,
        entityId = "plot-uuid",
        label = "Plot 7",
        properties =
          mapOf(
            "geometry" to TypedValue(string_value = "1.5 2.5"),
            "status" to TypedValue(string_value = "Open"),
            "visit_date" to TypedValue(date_value = Date(year = 2026, month = 11, day = 9)),
            "visit_time" to
              TypedValue(time_value = TimeOfDay(hours = 14, minutes = 5, seconds = 9)),
            "recorded_at" to TypedValue(timestamp_value = ofEpochSecond(1_794_241_920L, 0L)),
            "unanswered_date" to TypedValue(date_value = Date(year = 0, month = 1, day = 1)),
            "empty_value" to TypedValue(),
            "blank" to TypedValue(string_value = " "),
          ),
      )

    val result = assertNotNull(complete(entityStates = listOf(create)))

    val entity = surveys.entities.single()
    assertEquals("plot-uuid", entity.id)
    assertTrue(entity.isFieldCreated)
    // The land use answer becomes the label's sub-category.
    assertEquals("Plot 7 • Forest", entity.label)
    assertEquals(
      AgStackGeoId.computeCandidate(GeometryProtos.point(1.5, 2.5)),
      entity.geoId,
    )
    assertEquals("layer-plots", entity.layerId)
    assertEquals(0xFF112233, entity.colorHex)
    assertEquals(GeometryType.POINT, entity.geometry?.type)
    // Only `save_to` properties are written to the feature; temporal values are ISO-8601 and
    // blank/unanswered values are dropped.
    assertEquals(
      mapOf(
        "geometry" to "1.5 2.5",
        "status" to "Open",
        "visit_date" to "2026-11-09",
        "visit_time" to "14:05:09",
        "recorded_at" to "2026-11-09T16:32:00Z",
      ),
      entity.properties,
    )
    assertEquals("id-1", entity.submissions.single().id)
    assertEquals(
      listOf(
        "mut-create-ent-plot-uuid" to MutationSyncState.QUEUED,
        "mut-sub-id-1" to MutationSyncState.UPLOADING,
      ),
      mutations.mutations.map { it.id to it.state },
    )
    assertEquals("plot-uuid", result.selectedEntityId)
    assertEquals("id-1", result.selectedSubmissionId)
    assertEquals(SubmissionNotice.ENTITY_CREATED, result.notice.messageKey)
  }

  @Test
  fun formWithDatasetButNoDeclaration_createsAFeatureWithAGeneratedIdAndLabel() {
    surveys.forms = listOf(form(targetDatasetId = "tree_plots", requiresEntity = false))

    complete(recordInstance = record("/data/notes" to "windy"))

    val entity = surveys.entities.single()
    assertEquals("id-1", entity.id)
    assertEquals("tree plots #1", entity.label)
    assertEquals("layer-tree-plots", entity.layerId)
    // Without `save_to` bindings the answers stay on the feature.
    assertEquals(mapOf("notes" to "windy"), entity.properties)
    // No geometry was saved: a placeholder point at the collector's position.
    assertEquals(GeometryType.POINT, entity.geometry?.type)
  }

  @Test
  fun existingEntity_advancesItsWorkflowStatus() {
    surveys.forms = listOf(form(targetDatasetId = "plots"))
    surveys.entities = listOf(entity(id = "plot-1", markerSymbol = "○"))

    val result = assertNotNull(complete(selectedEntityId = "plot-1", launchedWithoutEntity = false))

    val updated = surveys.entities.single()
    assertEquals("In progress", updated.properties["status"])
    assertEquals("◐", updated.properties["marker-symbol"])
    assertEquals("#F9AB00", updated.properties["marker-color"])
    assertEquals("id-1", updated.submissions.first().id)
    assertEquals(
      listOf(MutationOperationKind.CREATE_SUBMISSION, MutationOperationKind.UPDATE_ENTITY),
      mutations.mutations.map { it.operationKind },
    )
    assertEquals("Marked plot as In progress", mutations.mutations[1].title)
    assertEquals("plot-1", result.selectedEntityId)
    assertNull(result.selectedSubmissionId)
    assertFalse(result.updateSelectedSubmissionId)
    assertEquals(SubmissionNotice.ENTITY_SUBMITTED, result.notice.messageKey)
  }

  @Test
  fun existingEntity_inProgressBecomesCompleted() {
    surveys.forms = listOf(form(targetDatasetId = "plots"))
    surveys.entities = listOf(entity(id = "plot-1", markerSymbol = "◐"))

    complete(activeEntityId = "plot-1")

    val properties = surveys.entities.single().properties
    assertEquals("Completed", properties["status"])
    assertEquals("✓", properties["marker-symbol"])
  }

  @Test
  fun existingEntity_saveToStatusWinsOverTheDefaultWorkflow() {
    surveys.forms = listOf(form(targetDatasetId = "plots"))
    surveys.entities = listOf(entity(id = "plot-1", markerSymbol = "○"))
    val update =
      EntityState(
        dataset = "plots",
        declaration = EntityDeclaration(),
        shouldCreate = false,
        shouldUpdate = true,
        entityId = "plot-1",
        label = "",
        properties = mapOf("status" to TypedValue(string_value = "Needs review")),
      )

    complete(entityStates = listOf(update), activeEntityId = "plot-1")

    val properties = surveys.entities.single().properties
    assertEquals("Needs review", properties["status"])
    assertEquals("◐", properties["marker-symbol"])
  }

  @Test
  fun existingEntity_saveToGeometryUpdatesGeometryAndRecomputesGeoId() {
    surveys.forms = listOf(form(targetDatasetId = "plots"))
    surveys.entities = listOf(entity(id = "plot-1", markerSymbol = "○"))
    val update =
      EntityState(
        dataset = "plots",
        declaration = EntityDeclaration(),
        shouldCreate = false,
        shouldUpdate = true,
        entityId = "plot-1",
        label = "",
        properties =
          mapOf(
            "geometry" to
              TypedValue(
                string_value =
                  "30.31145 76.88855;30.31073 76.88841;30.31070 76.88945;30.31145 76.88855"
              )
          ),
      )

    complete(entityStates = listOf(update), activeEntityId = "plot-1")

    val updated = surveys.entities.single()
    assertEquals(GeometryType.POLYGON, updated.geometry?.type)
    assertTrue(AgStackGeoId.isValidFormat(updated.geoId))
  }

  @Test
  fun unknownEntityOnAFormThatRequiresOne_returnsNull() {
    surveys.forms = listOf(form(targetDatasetId = "plots"))

    val result = complete(activeEntityId = "missing")

    assertNull(result)
    assertTrue(mutations.mutations.isEmpty())
  }

  @Test
  fun activeFormIdPicksTheFormAmongSeveral() {
    surveys.forms =
      listOf(form(id = "other", targetDatasetId = "plots"), form(id = "log", targetDatasetId = ""))

    complete(activeFormId = "log")

    assertEquals("log", surveys.standaloneSubmissions.single().formId)
  }

  private fun form(
    id: String = "plot-visit",
    targetDatasetId: String,
    requiresEntity: Boolean = targetDatasetId.isNotBlank(),
  ) =
    FormPreviewItem(
      id = id,
      title = "Fallback title",
      description = "",
      version = "1",
      targetDatasetId = targetDatasetId,
      targetDatasetName = "",
      questionCount = 2,
      ctaLabel = "Visit",
      requiresEntity = requiresEntity,
    )

  private fun layer(id: String, colorHex: Long) =
    MapLayerItem(id = id, label = id, colorHex = colorHex, geometryType = null, isVisible = true)

  private fun entity(id: String, markerSymbol: String) =
    GeospatialEntityItem(
      id = id,
      label = "Plot one",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "layer-plots",
      geoId = "P-1",
      geometry = null,
      areaHectares = 1.0,
      perimeterMeters = 400,
      colorHex = 0xFF000000,
      properties = mapOf("marker-symbol" to markerSymbol),
      submissions = emptyList(),
      typeNoun = "Plot",
    )

  /** Labels questions by their last path segment and shows string answers as they are. */
  private object LabelsFromPathsAnswerDisplay : AnswerDisplay {
    override fun questionLabels(state: FormState): Map<String, String> =
      state.fieldStates.values
        .filter { !it.canonicalPath.contains("/meta") }
        .associate { it.canonicalPath to LABELS.getValue(it.canonicalPath.substringAfterLast('/')) }

    override fun displayValue(value: FieldValue?, dataType: DataType): String =
      value?.scalar_value?.string_value.orEmpty()
  }

  private companion object {
    val LABELS = mapOf("land_use" to "Land use", "notes" to "Notes")

    val PLOT_VISIT_XFORM =
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Plot visit</h:title>
          <model>
            <instance>
              <data id="plot_visit" version="7">
                <land_use/>
                <notes/>
                <meta><instanceID/></meta>
              </data>
            </instance>
            <bind nodeset="/data/land_use" type="string"/>
            <bind nodeset="/data/notes" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/land_use"><label>Land use</label></input>
          <input ref="/data/notes"><label>Notes</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()
  }
}
