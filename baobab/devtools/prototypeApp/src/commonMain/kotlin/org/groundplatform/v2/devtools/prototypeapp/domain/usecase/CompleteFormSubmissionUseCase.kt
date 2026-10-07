/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import org.groundplatform.v2.core.forms.engine.FormSession
import org.groundplatform.v2.core.forms.model.EntityState
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState
import org.groundplatform.v2.core.forms.ui.FormWizardController
import org.groundplatform.v2.core.forms.ui.FormWizardStep
import org.groundplatform.v2.core.forms.ui.formatFieldValueForDisplay
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationLogItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationOperationKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.deriveEntitySyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.MutationRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.SurveyRepository
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.TransactionRunner

/** Outcome of finalizing an active form submission via [CompleteFormSubmissionUseCase]. */
data class FormSubmissionCompletionResult(
  val selectedEntityId: String?,
  val selectedSubmissionId: String?,
  val noticeMessage: String,
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
  private val resolveFormDefForLaunchUseCase: ResolveFormDefForLaunchUseCase =
    ResolveFormDefForLaunchUseCase(),
) {
  /**
   * Records the submission and queues its upload in one atomic change to the local data store.
   * Returns null if the submission can't be completed.
   */
  suspend operator fun invoke(
    recordInstance: RecordInstance,
    entityStates: List<EntityState>,
    controller: FormWizardController?,
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
    gnssStatusChipLabel: String,
  ): FormSubmissionCompletionResult? = transactionRunner {
    complete(
      recordInstance = recordInstance,
      entityStates = entityStates,
      controller = controller,
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
      gnssStatusChipLabel = gnssStatusChipLabel,
    )
  }

  private suspend fun complete(
    recordInstance: RecordInstance,
    entityStates: List<EntityState>,
    controller: FormWizardController?,
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
    gnssStatusChipLabel: String,
  ): FormSubmissionCompletionResult? {
    val entities = surveyRepository.getEntities()
    val forms = surveyRepository.getForms()
    val mapLayers = surveyRepository.getMapLayers()
    val standaloneSubmissions = surveyRepository.getStandaloneSubmissions()
    val mutations = mutationRepository.getMutations()
    val activeSurveyId = surveyRepository.getActiveSurveyId()

    val resolvedEntityStates =
      if (entityStates.isEmpty() && controller != null) {
        val fin = controller.finalizeForm()
        if (fin is FinalizationResult.Success) {
          fin.entityStates
        } else {
          controller.formState.entityStates
        }
      } else {
        entityStates
      }

    val entityRefFieldState =
      controller?.formState?.fieldStates?.get(ENTITY_REF_FIELD_PATH)
        ?: controller?.formState?.fieldStates?.get("/data/sample_plot_entity")
        ?: controller?.formState?.fieldStates?.get("/data/past_individual_id")
        ?: controller?.formState?.fieldStates?.get("/data/primary_respondent_id")
    val entityRefFromForm =
      entityRefFieldState
        ?.takeIf { !it.isEmpty }
        ?.let { formatFieldValueForDisplay(it.value, it.dataType).trim() }
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

    val resolvedFormDef =
      controller?.formState?.formDef ?: resolveFormDefForLaunchUseCase(customFormDef, form)

    val extractedFields =
      extractSubmissionFieldsFromRecord(
        recordInstance = recordInstance,
        controller = controller,
        resolvedFormDef = resolvedFormDef,
        form = form,
        gnssBadge = gnssStatusChipLabel,
      )

    val resolvedTitle = resolvedFormDef.title.ifBlank { form.title }
    val resolvedVersion = resolvedFormDef.version.ifBlank { form.version }

    val saveToProps = mutableMapOf<String, String>()
    resolvedEntityStates.forEach { es ->
      es.properties.forEach { (k, v) ->
        val strVal = v.string_value
        val dblVal = v.double_value
        val i64Val = v.int64_value
        val i32Val = v.int32_value
        val boolVal = v.bool_value
        val gpVal = v.geopoint_value
        val shapeVal = v.geoshape_value
        val traceVal = v.geotrace_value
        val formatted: String =
          when {
            strVal != null -> strVal
            dblVal != null -> dblVal.toString()
            i64Val != null -> i64Val.toString()
            i32Val != null -> i32Val.toString()
            boolVal != null -> boolVal.toString()
            gpVal != null -> "${gpVal.latitude} ${gpVal.longitude}"
            shapeVal != null ->
              shapeVal.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
            traceVal != null ->
              traceVal.points.joinToString("; ") { "${it.latitude} ${it.longitude}" }
            else -> v.toString()
          }
        if (formatted.isNotBlank()) {
          saveToProps[k] = formatted
        }
      }
    }
    extractedFields.forEach { f -> saveToProps.getOrPut(f.questionName) { f.answerValue } }

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
      val newEntId =
        createEntityState?.entityId?.takeIf { it.isNotBlank() }
          ?: "ent-${targetDataset.take(4)}-${(entities.size + 1).toString().padStart(2, '0')}"
      val labelCandidate =
        createEntityState?.label?.takeIf { it.isNotBlank() }
          ?: saveToProps["farmer_parcel_code"]
          ?: saveToProps["producer_farm_id"]
          ?: saveToProps["land_use"]?.substringBefore(" (")
          ?: "$targetDatasetName #${entities.size + 1}"
      val finalEntLabel =
        if (labelCandidate.contains("•")) {
          labelCandidate
        } else {
          val subCategory =
            saveToProps["commodity_type"]?.substringBefore(" (")
              ?: saveToProps["land_use"]?.substringBefore(" (")
          if (subCategory != null) "$labelCandidate • $subCategory" else labelCandidate
        }

      val newSubId = "sub-$newEntId-${form.id}-${entities.size + 1}"
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
          timestamp = "2026-09-19 18:30 UTC",
          fields = extractedFields,
          targetTypeLabel = form.targetSingularTypeLabel,
          syncStatus = SyncStatus.UPLOADING,
          coordinatesLabel = userGpsCoordinatesLabel,
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
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
      val geomTypeLabel = if (isPolygonGeom) "Polygon" else "Point"

      val createdStatus = saveToProps["status"] ?: "Completed"
      val createdSymbol = saveToProps["marker-symbol"] ?: "✓"
      val createdColor = saveToProps["marker-color"] ?: "#1E8E3E"
      val entityProps =
        saveToProps.toMutableMap().apply {
          put("status", createdStatus)
          put("marker-symbol", createdSymbol)
          // Status color for the status chip; the map draws features in their layer's color.
          put("marker-color", createdColor)
        }

      val newEntity =
        GeospatialEntityItem(
          id = newEntId,
          label = finalEntLabel,
          datasetId = targetDataset,
          datasetName = targetDatasetName,
          layerId = layerId,
          geoId = newEntId.uppercase(),
          geometryTypeLabel = geomTypeLabel,
          areaHectares = if (isPolygonGeom) 1.2 else 0.05,
          perimeterMeters = if (isPolygonGeom) 480 else 25,
          coordinatesLabel = userGpsCoordinatesLabel.substringBefore(" ("),
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
          colorHex = layerColorHex,
          properties = entityProps,
          submissions = listOf(newSubmission),
          singularTypeLabel = form.targetSingularTypeLabel,
          syncStatus = SyncStatus.UPLOADING,
        )

      surveyRepository.setEntities(listOf(newEntity) + entities)

      val nextSeq = mutations.size + 1
      val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
      val opTime = "2026-09-19 18:30:$seqSuffix UTC"
      val startTime = "2026-09-19 18:31:$seqSuffix UTC"
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
          startedTimestamp = startTime,
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
          startedTimestamp = startTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} responses",
        )
      mutationRepository.prependMutations(listOf(newEntityMutation, newSubmissionMutation))
      return FormSubmissionCompletionResult(
        selectedEntityId = newEntId,
        selectedSubmissionId = newSubId,
        noticeMessage = "Created new entity \"$finalEntLabel\" via save_to (✓ Completed)",
      )
    }

    // 2. Handle standalone ("Log Only") form submissions without an attached entity
    if (entityId == null && !form.requiresEntity) {
      val newSubId = "sub-standalone-${form.id}-${standaloneSubmissions.size + 1}"
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
          timestamp = "2026-09-19 18:30 UTC",
          fields = extractedFields,
          targetTypeLabel = "Standalone Field Log",
          syncStatus = SyncStatus.UPLOADING,
          coordinatesLabel = userGpsCoordinatesLabel,
          normalizedX = userGpsNormalizedX,
          normalizedY = userGpsNormalizedY,
        )
      surveyRepository.setStandaloneSubmissions(
        listOf(newStandaloneSubmission) + standaloneSubmissions
      )
      val nextSeq = mutations.size + 1
      val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
      val opTime = "2026-09-19 18:30:$seqSuffix UTC"
      val startTime = "2026-09-19 18:31:$seqSuffix UTC"
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
            "Uploading standalone submission (${extractedFields.size} fields) to Ground Cloud",
          operationTimestamp = opTime,
          startedTimestamp = startTime,
          completedTimestamp = null,
          payloadSummary = "${extractedFields.size} fields • Standalone Field Log",
        )
      mutationRepository.prependMutations(listOf(newSubmissionMutation))
      return FormSubmissionCompletionResult(
        selectedEntityId = null,
        selectedSubmissionId = newSubId,
        noticeMessage =
          "Submitted standalone field log \"$resolvedTitle\" (${userGpsCoordinatesLabel.substringBefore(" (")})",
      )
    }

    // 3. Update existing entity with submission and merged save_to properties
    val resolvedEntityId = entityId ?: return null
    val entity = entities.firstOrNull { it.id == resolvedEntityId } ?: return null
    val newSubId = "sub-${entity.id}-${form.id}-${entity.submissions.size + 1}"
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
        timestamp = "2026-09-19 18:30 UTC",
        fields = extractedFields,
        targetTypeLabel = entity.singularTypeLabel,
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

    val updatedSubmissions = listOf(newSubmission) + entity.submissions
    surveyRepository.upsertEntities(
      listOf(
        entity.copy(
          properties = updatedProperties,
          submissions = updatedSubmissions,
          syncStatus = deriveEntitySyncStatus(updatedSubmissions, fallback = SyncStatus.UPLOADING),
        )
      )
    )

    val nextSeq = mutations.size + 1
    val seqSuffix = (nextSeq % 60).toString().padStart(2, '0')
    val opTime = "2026-09-19 18:30:$seqSuffix UTC"
    val startTime = "2026-09-19 18:31:$seqSuffix UTC"
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
        startedTimestamp = startTime,
        completedTimestamp = null,
        payloadSummary = "${extractedFields.size} responses",
      )
    val newEntityUpdateMutation =
      MutationLogItem(
        id = "mut-ent-$newSubId",
        surveyId = activeSurveyId,
        operationKind = MutationOperationKind.UPDATE_ENTITY,
        title = "Marked ${entity.singularTypeLabel.lowercase()} as $nextStatus",
        targetLabel = entity.label,
        entityId = entity.id,
        submissionId = newSubId,
        actorName = signedInUserName,
        state = MutationSyncState.QUEUED,
        stateDetail = "Waiting to upload",
        operationTimestamp = opTime,
        startedTimestamp = startTime,
        completedTimestamp = null,
        payloadSummary = "${entity.singularTypeLabel} status updated",
      )
    mutationRepository.prependMutations(listOf(newSubmissionMutation, newEntityUpdateMutation))

    return FormSubmissionCompletionResult(
      selectedEntityId = entity.id,
      selectedSubmissionId = null,
      noticeMessage =
        "Submitted \"$resolvedTitle\" for ${entity.label} (Marker: $previousMarkerSymbol → $nextMarkerSymbol $nextStatus)",
      updateSelectedSubmissionId = false,
    )
  }

  companion object {
    const val ENTITY_REF_FIELD_PATH: String = "/data/target_entity"

    fun extractSubmissionFieldsFromRecord(
      recordInstance: RecordInstance,
      controller: FormWizardController?,
      resolvedFormDef: FormDef,
      form: FormPreviewItem,
      gnssBadge: String,
    ): List<SubmissionFieldEntry> {
      val evaluatedState: FormState =
        if (controller != null && controller.formState.recordInstance == recordInstance) {
          controller.formState
        } else {
          FormSession(
              formDef = controller?.formState?.formDef ?: resolvedFormDef,
              existingRecord = recordInstance,
              isFirstLoad = false,
            )
            .state
        }

      val steps = controller?.steps ?: FormWizardController.buildSteps(evaluatedState)
      val controlLabelsByPath = mutableMapOf<String, String>()
      steps.forEach { step ->
        when (step) {
          is FormWizardStep.QuestionStep -> {
            controlLabelsByPath[step.control.canonicalPath] = step.title
          }
          is FormWizardStep.FieldListGroupStep -> {
            step.controls.forEach { ctrl ->
              val label =
                ctrl.label?.text?.takeIf { it.isNotBlank() }
                  ?: ctrl.canonicalPath.substringAfterLast('/')
              controlLabelsByPath[ctrl.canonicalPath] = label
            }
          }
          else -> {}
        }
      }

      val entries = mutableListOf<SubmissionFieldEntry>()
      evaluatedState.fieldStates.values.forEach { fs ->
        val isMetaOrEntityRefField =
          fs.relativePath == "meta/instanceID" ||
            fs.relativePath.startsWith("meta/") ||
            fs.relativePath.startsWith("__") ||
            fs.canonicalPath.endsWith("/meta/instanceID") ||
            fs.canonicalPath == ENTITY_REF_FIELD_PATH ||
            fs.relativePath == "target_entity"
        if (fs.isRelevant && !fs.isEmpty && !isMetaOrEntityRefField) {
          val questionName = fs.relativePath.ifBlank { fs.canonicalPath.trimStart('/') }
          val questionLabel =
            controlLabelsByPath[fs.canonicalPath]
              ?: fs.canonicalPath.substringAfterLast('/').replace('_', ' ')
          val formattedValue = formatFieldValueForDisplay(fs.value, fs.dataType)
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
            answerValue = "Verified in field ($gnssBadge)",
          )
        )
      }

      return entries
    }
  }
}
