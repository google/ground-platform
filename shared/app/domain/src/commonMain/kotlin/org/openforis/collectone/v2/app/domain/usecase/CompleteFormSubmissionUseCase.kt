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

import kotlin.time.Clock
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.app.domain.model.AnswerDisplay
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.EntityShape
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.FormRunner
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.Measurement
import org.openforis.collectone.v2.app.domain.model.MutationLogItem
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.SubmissionFieldEntry
import org.openforis.collectone.v2.app.domain.model.SubmissionNotice
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyMapAnchor
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.computeAgStackGeoId
import org.openforis.collectone.v2.app.domain.model.deriveEntitySyncStatus
import org.openforis.collectone.v2.app.domain.model.deviceFormEnvironment
import org.openforis.collectone.v2.app.domain.model.formatMutationTime
import org.openforis.collectone.v2.app.domain.model.matchesAgStackGeoId
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.app.domain.model.odkGeometry
import org.openforis.collectone.v2.app.domain.repository.MutationRepository
import org.openforis.collectone.v2.app.domain.repository.SurveyRepository
import org.openforis.collectone.v2.app.domain.repository.TransactionRunner
import org.openforis.collectone.v2.core.forms.engine.FormSession
import org.openforis.collectone.v2.core.forms.model.EntityState
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.temporal.formatIsoInstant
import org.openforis.collectone.v2.core.forms.temporal.formatIsoTime
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.map.GeometryType

/** Outcome of finalizing an active form submission via [CompleteFormSubmissionUseCase]. */
data class FormSubmissionCompletionResult(
  val selectedEntityId: String?,
  val selectedSubmissionId: String?,
  /** What happened, for the notice shown to the collector. */
  val notice: DomainMessage,
  val updateSelectedSubmissionId: Boolean = true,
)

/**
 * Multi-repository domain use case orchestrating [SurveyRepository] and [MutationRepository] when a
 * collector completes a form submission:
 * 1. Extracts answered [SubmissionFieldEntry] items from the finalized [RecordInstance].
 * 2. Evaluates XForms `save_to` / `entities:entity` bindings to either create a new
 *    [GeospatialEntityItem], record a standalone [SubmissionPreviewItem], or advance an existing
 *    entity's 3-stage `simplestyle-spec` workflow (`○` → `◐` → `✓`).
 * 3. Queues the resulting [MutationLogItem]s in [MutationRepository].
 */
class CompleteFormSubmissionUseCase(
  private val surveyRepository: SurveyRepository,
  private val mutationRepository: MutationRepository,
  private val transactionRunner: TransactionRunner,
  /** Labels and values of answered questions as the form runner shows them. */
  private val answerDisplay: AnswerDisplay,
  /** The device clock, recorded as each submission's `clientUpdatedAt` when it is saved. */
  private val clock: Clock,
  private val resolveFormDefForLaunchUseCase: ResolveFormDefForLaunchUseCase =
    ResolveFormDefForLaunchUseCase(),
  /** IDs of new map features and submissions (random UUIDs). */
  private val newId: () -> String = RecordIds::newId,
) {
  /**
   * Records the submission and queues its upload in one atomic change to the local data store.
   * Returns null if the submission can't be completed.
   */
  suspend operator fun invoke(
    recordInstance: RecordInstance,
    entityStates: List<EntityState>,
    formRunner: FormRunner?,
    customFormDef: FormDef?,
    activeDataCollectionFormId: String?,
    activeDataCollectionEntityId: String?,
    selectedEntityId: String?,
    wasFormLaunchedWithoutEntity: Boolean,
    signedInUserName: String,
    signedInUserEmail: String,
    userGpsCoordinatesLabel: String,
    userGpsNormalizedX: Float,
    userGpsNormalizedY: Float,
    gnssAccuracy: Measurement,
  ): FormSubmissionCompletionResult? = transactionRunner {
    complete(
      recordInstance = recordInstance,
      entityStates = entityStates,
      formRunner = formRunner,
      customFormDef = customFormDef,
      activeDataCollectionFormId = activeDataCollectionFormId,
      activeDataCollectionEntityId = activeDataCollectionEntityId,
      selectedEntityId = selectedEntityId,
      wasFormLaunchedWithoutEntity = wasFormLaunchedWithoutEntity,
      signedInUserName = signedInUserName,
      signedInUserEmail = signedInUserEmail,
      userGpsCoordinatesLabel = userGpsCoordinatesLabel,
      userGpsNormalizedX = userGpsNormalizedX,
      userGpsNormalizedY = userGpsNormalizedY,
      gnssAccuracy = gnssAccuracy,
    )
  }

  private suspend fun complete(
    recordInstance: RecordInstance,
    entityStates: List<EntityState>,
    formRunner: FormRunner?,
    customFormDef: FormDef?,
    activeDataCollectionFormId: String?,
    activeDataCollectionEntityId: String?,
    selectedEntityId: String?,
    wasFormLaunchedWithoutEntity: Boolean,
    signedInUserName: String,
    signedInUserEmail: String,
    userGpsCoordinatesLabel: String,
    userGpsNormalizedX: Float,
    userGpsNormalizedY: Float,
    gnssAccuracy: Measurement,
  ): FormSubmissionCompletionResult? {
    val entities = surveyRepository.getEntities()
    val forms = surveyRepository.getForms()
    val mapLayers = surveyRepository.getMapLayers()
    val standaloneSubmissions = surveyRepository.getStandaloneSubmissions()
    val activeSurveyId = surveyRepository.getActiveSurveyId()
    val savedAt = clock.now()
    val opTime = formatMutationTime(savedAt)
    val userLocation =
      SurveyMapAnchor.forSurvey(activeSurveyId)
        .toLatLng(userGpsNormalizedX.toDouble(), userGpsNormalizedY.toDouble())

    val resolvedEntityStates =
      if (entityStates.isEmpty() && formRunner != null) {
        val fin = formRunner.finalizeForm()
        if (fin is FinalizationResult.Success) {
          fin.entityStates
        } else {
          formRunner.formState.entityStates
        }
      } else {
        entityStates
      }

    val entityRefFieldState =
      formRunner?.formState?.fieldStates?.get(ENTITY_REF_FIELD_PATH)
        ?: formRunner?.formState?.fieldStates?.get("/data/sample_plot_entity")
        ?: formRunner?.formState?.fieldStates?.get("/data/past_individual_id")
        ?: formRunner?.formState?.fieldStates?.get("/data/primary_respondent_id")
    val entityRefFromForm =
      entityRefFieldState
        ?.takeIf { !it.isEmpty }
        ?.let { answerDisplay.displayValue(it.value, it.dataType).trim() }
        ?.takeIf { it.isNotBlank() }

    val selectedEntity = selectedEntityId?.let { id -> entities.firstOrNull { it.id == id } }
    val formId =
      activeDataCollectionFormId
        ?: selectedEntity?.let { ent ->
          forms.firstOrNull { it.targetDatasetId == ent.datasetId }?.id
        }
        ?: forms.firstOrNull()?.id
        ?: return null
    val form = forms.firstOrNull { it.id == formId } ?: forms.first()
    val surveyConfig = surveyRepository.getSurveyConfig(activeSurveyId)

    val resolvedFormDef =
      formRunner?.formState?.formDef
        ?: resolveFormDefForLaunchUseCase(
          customFormDef = customFormDef,
          form = form,
          surveyConfig = surveyConfig,
        )

    val extractedFields =
      extractSubmissionFields(
        recordInstance = recordInstance,
        formRunner = formRunner,
        resolvedFormDef = resolvedFormDef,
        form = form,
        gnssAccuracy = gnssAccuracy,
      )

    val resolvedTitle = resolvedFormDef.title.ifBlank { form.title }
    val resolvedVersion = resolvedFormDef.version.ifBlank { form.version }

    val saveToProps = mutableMapOf<String, String>()
    resolvedEntityStates.forEach { es ->
      es.properties.forEach { (k, v) ->
        val formatted = formatSaveToValue(v)
        if (formatted.isNotBlank()) {
          saveToProps[k] = formatted
        }
      }
    }
    // Answers by question name, used only to label new features. A feature's properties are what
    // the Form's `save_to` bindings write (ODK Entities): copying every answer onto it duplicated
    // bound questions, e.g. a geopoint saved to `geometry` also stored under its question name.
    val answers = extractedFields.associate { it.questionName to it.answerValue } + saveToProps
    // Forms without `save_to` bindings keep their answers on the feature, as before.
    if (saveToProps.isEmpty()) saveToProps.putAll(answers)

    val createEntityState = resolvedEntityStates.firstOrNull { it.shouldCreate }

    val entityId =
      activeDataCollectionEntityId
        ?: entityRefFromForm
        ?: if (wasFormLaunchedWithoutEntity) {
          null
        } else {
          selectedEntityId ?: entities.firstOrNull()?.id
        }

    // 1. Check if an entity should be created via XForms save_to / entities:entity create="1"
    if (
      entityId == null &&
        (createEntityState != null || (form.targetDatasetId.isNotBlank() && !form.requiresEntity))
    ) {
      val targetDataset =
        createEntityState?.dataset?.takeIf { it.isNotBlank() }
          ?: form.targetDatasetId.ifBlank { "locations" }
      val targetDatasetName = form.targetDatasetName.ifBlank { targetDataset.replace('_', ' ') }
      val newEntId = createEntityState?.entityId?.takeIf { it.isNotBlank() } ?: newId()
      // Without an entity label expression, the label falls back to answers of the sample
      // commodity / land use Forms, then to "<dataset> #n".
      val labelCandidate =
        createEntityState?.label?.takeIf { it.isNotBlank() }
          ?: answers["farmer_parcel_code"]
          ?: answers["producer_farm_id"]
          ?: answers["land_use"]?.substringBefore(" (")
          ?: "$targetDatasetName #${entities.size + 1}"
      val finalEntLabel =
        if (labelCandidate.contains("•")) {
          labelCandidate
        } else {
          val subCategory =
            answers["commodity_type"]?.substringBefore(" (")
              ?: answers["land_use"]?.substringBefore(" (")
          if (subCategory != null) "$labelCandidate • $subCategory" else labelCandidate
        }

      val newSubId = newId()
      val newSubmission =
        SubmissionPreviewItem(
          id = newSubId,
          entityId = newEntId,
          entityLabel = finalEntLabel,
          formId = form.id,
          formTitle = resolvedTitle,
          formVersion = resolvedVersion,
          collectorName = signedInUserName,
          collectorEmail = signedInUserEmail,
          clientUpdatedAt = savedAt,
          fields = extractedFields,
          targetTypeNoun = form.targetTypeNoun,
          syncStatus = SyncStatus.UPLOADING,
          location = userLocation,
        )

      val targetDatasetDashed = targetDataset.replace('_', '-')
      val matchingLayer =
        mapLayers.firstOrNull {
          it.id == "layer-$targetDataset" || it.id == "layer-$targetDatasetDashed"
        }
          ?: mapLayers.firstOrNull { it.id == "layer-form-${form.id}" }
          ?: mapLayers.firstOrNull {
            it.id == "layer-form-commodity-perimeter" && targetDataset == "commodity_plots"
          }
          ?: mapLayers.firstOrNull {
            it.id == "layer-form-single-point-land-use" && targetDataset == "land_use_observations"
          }
      val layerId = matchingLayer?.id ?: "layer-$targetDatasetDashed"
      val layerColorHex = matchingLayer?.colorHex ?: 0xFF2E7D32

      val geomWkt = saveToProps["geometry"]
      val isPolygonGeom =
        geomWkt?.contains(";") == true ||
          geomWkt?.startsWith("POLYGON", ignoreCase = true) == true ||
          extractedFields.any { it.questionName.contains("perimeter") }
      // The geometry the form saved, else the shape drawn at the collector's position.
      val newGeometry =
        geomWkt?.let(::odkGeometry)
          ?: EntityShape.placeholder(
            type = if (isPolygonGeom) GeometryType.POLYGON else GeometryType.POINT,
            center = userLocation,
          )
      val newGeoId = newGeometry.computeAgStackGeoId()

      // Workflow status (`status`, `marker-symbol`, `marker-color`) comes only from the form's
      // `save_to` bindings; entities created without them show no status rather than a default.
      val entityProps = saveToProps.toMutableMap()

      val newEntity =
        GeospatialEntityItem(
          id = newEntId,
          label = finalEntLabel,
          datasetId = targetDataset,
          datasetName = targetDatasetName,
          layerId = layerId,
          geoId = newGeoId,
          geometry = newGeometry,
          areaHectares = if (isPolygonGeom) 1.2 else 0.05,
          perimeterMeters = if (isPolygonGeom) 480 else 25,
          colorHex = layerColorHex,
          properties = entityProps,
          submissions = listOf(newSubmission),
          typeNoun = form.targetTypeNoun,
          syncStatus = SyncStatus.UPLOADING,
          isFieldCreated = true,
        )

      val updatedEntities = listOf(newEntity) + entities
      surveyRepository.setEntities(updatedEntities)
      surveyRepository.updateSurvey(activeSurveyId) { item ->
        item.copy(
          entityCount = updatedEntities.size,
          thumbnail =
            SurveyThumbnailGenerator.updateFeatures(
              existing = item.thumbnail,
              surveyId = activeSurveyId,
              mapLayers = mapLayers,
              entities = updatedEntities,
            ),
        )
      }

      val newEntityMutation =
        MutationLogItem(
          id = "mut-create-ent-$newEntId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_ENTITY,
          title = "Created entity $finalEntLabel",
          targetLabel = finalEntLabel,
          entityId = newEntId,
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.QUEUED,
          stateDetail = "Waiting to upload new entity",
          operationTimestamp = opTime,
          startedTimestamp = opTime,
          completedTimestamp = null,
          payloadSummary = "Created new entity via save_to",
        )
      val newSubmissionMutation =
        MutationLogItem(
          id = "mut-sub-$newSubId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = resolvedTitle,
          targetLabel = finalEntLabel,
          entityId = newEntId,
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.UPLOADING,
          stateDetail = "Uploading",
          operationTimestamp = opTime,
          startedTimestamp = opTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} responses",
        )
      mutationRepository.prependMutations(listOf(newEntityMutation, newSubmissionMutation))
      return FormSubmissionCompletionResult(
        selectedEntityId = newEntId,
        selectedSubmissionId = newSubId,
        notice = SubmissionNotice.ENTITY_CREATED.message(finalEntLabel),
      )
    }

    // 2. Handle standalone ("Log Only") form submissions without an attached entity
    if (entityId == null && !form.requiresEntity) {
      val newSubId = newId()
      val newStandaloneSubmission =
        SubmissionPreviewItem(
          id = newSubId,
          entityId = "",
          entityLabel = "",
          formId = form.id,
          formTitle = resolvedTitle,
          formVersion = resolvedVersion,
          collectorName = signedInUserName,
          collectorEmail = signedInUserEmail,
          clientUpdatedAt = savedAt,
          fields = extractedFields,
          syncStatus = SyncStatus.UPLOADING,
          location = userLocation,
        )
      surveyRepository.setStandaloneSubmissions(
        listOf(newStandaloneSubmission) + standaloneSubmissions
      )
      val newSubmissionMutation =
        MutationLogItem(
          id = "mut-sub-$newSubId",
          surveyId = activeSurveyId,
          operationKind = MutationOperationKind.CREATE_SUBMISSION,
          title = resolvedTitle,
          targetLabel = "Standalone Field Log (${userGpsCoordinatesLabel.substringBefore(" (")})",
          entityId = "",
          submissionId = newSubId,
          actorName = signedInUserName,
          state = MutationSyncState.UPLOADING,
          stateDetail =
            "Uploading standalone submission (${extractedFields.size} fields) to Collect One Cloud",
          operationTimestamp = opTime,
          startedTimestamp = opTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} fields • Standalone Field Log",
        )
      mutationRepository.prependMutations(listOf(newSubmissionMutation))
      return FormSubmissionCompletionResult(
        selectedEntityId = null,
        selectedSubmissionId = newSubId,
        notice =
          SubmissionNotice.STANDALONE_SUBMITTED.message(
            resolvedTitle,
            userGpsCoordinatesLabel.substringBefore(" ("),
          ),
      )
    }

    // 3. Update existing entity with submission and merged save_to properties
    val resolvedEntityId = entityId ?: return null
    val entity = entities.firstOrNull { it.id == resolvedEntityId } ?: return null
    val newSubId = newId()
    val newSubmission =
      SubmissionPreviewItem(
        id = newSubId,
        entityId = entity.id,
        entityLabel = entity.label,
        formId = form.id,
        formTitle = resolvedTitle,
        formVersion = resolvedVersion,
        collectorName = signedInUserName,
        collectorEmail = signedInUserEmail,
        clientUpdatedAt = savedAt,
        fields = extractedFields,
        targetTypeNoun = entity.typeNoun,
        syncStatus = SyncStatus.UPLOADING,
      )

    val previousMarkerSymbol = entity.markerSymbol
    val (fallbackMarkerSymbol, fallbackMarkerColor, fallbackStatus) =
      when (previousMarkerSymbol) {
        "○" -> Triple("◐", "#F9AB00", "In progress")
        "◐" -> Triple("✓", "#1E8E3E", "Completed")
        else -> Triple("✓", "#1E8E3E", "Completed")
      }
    val nextStatus = saveToProps["status"] ?: fallbackStatus
    val nextMarkerSymbol = saveToProps["marker-symbol"] ?: fallbackMarkerSymbol
    val nextMarkerColor = saveToProps["marker-color"] ?: fallbackMarkerColor
    val updatedProperties =
      entity.properties.toMutableMap().apply {
        putAll(saveToProps)
        put("status", nextStatus)
        put("marker-symbol", nextMarkerSymbol)
        // Status color for the status chip only: map features keep their layer's color, so the
        // geometry `stroke` / `fill` styling isn't changed with the status.
        put("marker-color", nextMarkerColor)
      }

    val updatedGeometry = saveToProps["geometry"]?.let(::odkGeometry) ?: entity.geometry
    val updatedGeoId =
      if (
        updatedGeometry != entity.geometry ||
          (updatedGeometry != null && !updatedGeometry.matchesAgStackGeoId(entity.geoId))
      ) {
        updatedGeometry.computeAgStackGeoId()
      } else {
        entity.geoId
      }
    val updatedSubmissions = listOf(newSubmission) + entity.submissions
    surveyRepository.upsertEntities(
      listOf(
        entity.copy(
          geoId = updatedGeoId,
          geometry = updatedGeometry,
          properties = updatedProperties,
          submissions = updatedSubmissions,
          syncStatus = deriveEntitySyncStatus(updatedSubmissions, fallback = SyncStatus.UPLOADING),
        )
      )
    )

    val newSubmissionMutation =
      MutationLogItem(
        id = "mut-sub-$newSubId",
        surveyId = activeSurveyId,
        operationKind = MutationOperationKind.CREATE_SUBMISSION,
        title = resolvedTitle,
        targetLabel = entity.label,
        entityId = entity.id,
        submissionId = newSubId,
        actorName = signedInUserName,
        state = MutationSyncState.UPLOADING,
        stateDetail = "Uploading",
        operationTimestamp = opTime,
        startedTimestamp = opTime,
        completedTimestamp = null,
        payloadSummary = "${extractedFields.size} responses",
      )
    val newEntityUpdateMutation =
      MutationLogItem(
        id = "mut-ent-$newSubId",
        surveyId = activeSurveyId,
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title =
          "Marked ${entity.typeNoun.ifBlank { entity.datasetName }.lowercase()} as $nextStatus",
        targetLabel = entity.label,
        entityId = entity.id,
        submissionId = newSubId,
        actorName = signedInUserName,
        state = MutationSyncState.QUEUED,
        stateDetail = "Waiting to upload",
        operationTimestamp = opTime,
        startedTimestamp = opTime,
        completedTimestamp = null,
        payloadSummary = "${entity.typeNoun.ifBlank { entity.datasetName }} status updated",
      )
    mutationRepository.prependMutations(listOf(newSubmissionMutation, newEntityUpdateMutation))

    return FormSubmissionCompletionResult(
      selectedEntityId = entity.id,
      selectedSubmissionId = null,
      notice =
        SubmissionNotice.ENTITY_SUBMITTED.message(
          resolvedTitle,
          entity.label,
          previousMarkerSymbol,
          nextMarkerSymbol,
          nextStatus,
        ),
      updateSelectedSubmissionId = false,
    )
  }

  /**
   * The answered questions of [recordInstance] (evaluated with [formRunner]'s state when it holds
   * that record) in question order, labeled and formatted as the form runner shows them.
   */
  private fun extractSubmissionFields(
    recordInstance: RecordInstance,
    formRunner: FormRunner?,
    resolvedFormDef: FormDef,
    form: FormPreviewItem,
    gnssAccuracy: Measurement,
  ): List<SubmissionFieldEntry> {
    val evaluatedState: FormState =
      if (formRunner != null && formRunner.formState.recordInstance == recordInstance) {
        formRunner.formState
      } else {
        FormSession(
            formDef = formRunner?.formState?.formDef ?: resolvedFormDef,
            existingRecord = recordInstance,
            isFirstLoad = false,
            environment = deviceFormEnvironment(clock),
          )
          .state
      }
    val controlLabelsByPath = answerDisplay.questionLabels(evaluatedState)
    val orderedPaths = controlLabelsByPath.keys

    val seenCanonicalPaths = mutableSetOf<String>()
    val orderedFieldStates = buildList {
      for (path in orderedPaths) {
        val fs = evaluatedState.fieldStates[path] ?: continue
        if (seenCanonicalPaths.add(fs.canonicalPath)) {
          add(fs)
        }
      }
      for (fs in evaluatedState.fieldStates.values) {
        if (seenCanonicalPaths.add(fs.canonicalPath)) {
          add(fs)
        }
      }
    }

    val entries = mutableListOf<SubmissionFieldEntry>()
    orderedFieldStates.forEach { fs ->
      val isContainerNode = fs.dataType == openforis.collectone.v1.forms.DataType.TYPE_MESSAGE
      val isMetaOrEntityRefField =
        fs.relativePath == "meta/instanceID" ||
          fs.relativePath.startsWith("meta/") ||
          fs.relativePath.startsWith("__") ||
          fs.canonicalPath.endsWith("/meta/instanceID") ||
          fs.canonicalPath == ENTITY_REF_FIELD_PATH ||
          fs.relativePath == "target_entity"
      if (!isContainerNode && fs.isRelevant && !fs.isEmpty && !isMetaOrEntityRefField) {
        val canonicalWithoutRoot =
          fs.canonicalPath.trimStart('/').substringAfter('/', missingDelimiterValue = "")
        val questionName =
          if (canonicalWithoutRoot.contains('[')) {
            canonicalWithoutRoot
          } else {
            fs.relativePath.ifBlank { fs.canonicalPath.trimStart('/') }
          }
        val questionLabel =
          controlLabelsByPath[fs.canonicalPath]
            ?: fs.canonicalPath.substringAfterLast('/').replace('_', ' ')
        val formattedValue = answerDisplay.displayValue(fs.value, fs.dataType)
        entries.add(
          SubmissionFieldEntry(
            questionName = questionName,
            questionLabel = questionLabel,
            answerValue = formattedValue,
          )
        )
      }
    }

    if (entries.isEmpty()) {
      entries.add(
        SubmissionFieldEntry(
          questionName = "organizer_action_cta",
          questionLabel = "Completed Form Action (${form.ctaLabel})",
          // Stored in the record, so in English whatever the UI language.
          answerValue = "Verified in field (±${gnssAccuracy.value} ${gnssAccuracy.unit.symbol})",
        )
      )
    }

    return entries
  }

  companion object {
    const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"
  }
}

/**
 * Formats an ODK Entities `save_to` [TypedValue] into the plain string stored in entity properties
 * (and shown in Web data tables and entity detail cards), using ISO-8601 for temporal values.
 */
private fun formatSaveToValue(v: TypedValue): String =
  when {
    v.string_value != null -> v.string_value.orEmpty()
    v.double_value != null -> v.double_value.toString()
    v.int64_value != null -> v.int64_value.toString()
    v.int32_value != null -> v.int32_value.toString()
    v.bool_value != null -> v.bool_value.toString()
    v.date_value != null -> {
      val d = v.date_value!!
      if (d.year > 0) {
        "${d.year.toString().padStart(4, '0')}-${d.month.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
      } else {
        ""
      }
    }
    v.time_value != null -> formatIsoTime(v.time_value!!)
    v.timestamp_value != null -> formatIsoInstant(v.timestamp_value!!)
    v.geopoint_value != null -> "${v.geopoint_value!!.latitude} ${v.geopoint_value!!.longitude}"
    v.geoshape_value != null ->
      v.geoshape_value!!.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
    v.geotrace_value != null ->
      v.geotrace_value!!.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
    else -> ""
  }
