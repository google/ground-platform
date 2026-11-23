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
package org.openforis.collectone.v2.app.data.mapper

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.EntityType
import openforis.collectone.v1.survey.FormLaunchConfig
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.PeerDataVisibility as PeerDataVisibilityProto
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SharingPolicy as SharingPolicyProto
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyOverview
import openforis.collectone.v1.survey.SurveyState
import openforis.collectone.v1.survey.SurveyThumbnailFeature as SurveyThumbnailFeatureProto
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.MapLayerItem
import org.openforis.collectone.v2.app.domain.model.MapThumbnailTheme
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.model.SurveyPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailFeature
import org.openforis.collectone.v2.app.domain.model.SurveyThumbnailGenerator
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.FormConceptLinkSync
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDerivation
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin
import org.openforis.collectone.v2.app.domain.model.editor.isoUtc
import org.openforis.collectone.v2.app.domain.model.outline
import org.openforis.collectone.v2.app.domain.model.parseHexColorOrDefault
import org.openforis.collectone.v2.app.domain.model.sampleEvenly
import org.openforis.collectone.v2.core.forms.model.FormDefinition
import org.openforis.collectone.v2.core.mutation.SurveyMutations
import org.openforis.collectone.v2.core.sampling.SamplingDesignProtos
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.GeometryType

/**
 * Bidirectional mapper between Wire-generated survey Protocol Buffer messages (`SurveyDef`,
 * `SurveyAcl`, `SurveyOverview`) and `shared/app` domain models stored in `LocalStore`.
 *
 * The domain splits one survey across several stored projections (list entry, forms, map layers,
 * config, editor draft); this object assembles a `SurveyDef` from them and derives each of them
 * back. Sharing, lifecycle, and dataset details are delegated to [SurveyAclMapper],
 * [SurveyLifecycleMapper], and [SurveyDatasetMapper]. Stateless and pure.
 */
object ProtoSurveyMapper {
  /**
   * `FormDef.form_id` under which the survey's primary form is stored in `SurveyDef.forms`; its
   * real ID travels in `SurveyDef.primary_form_id` (see [surveyConfigFromProto]).
   */
  private const val PRIMARY_FORM_PROTO_ID = "__primary_form__"
  private val LEGACY_SEED_SURVEY_IDS =
    setOf(
      "00000032-8d62-5717-8074-f4917e0cd76d",
      "00000033-6b50-51cd-ac94-baea2ea06d65",
    )

  /**
   * Builds the survey's definition and access list from its stored projections. When an
   * [editorDraft] is given, its details (area, languages, purposes, sharing, extra datasets) win
   * over the list entry's and [config]'s, since the draft is what the organizer last edited.
   * [commitTimestamp] defaults to a fixed instant so the output is deterministic (seeds, diffs);
   * [org.openforis.collectone.v2.app.data.sync.MutationDiffs] ignores it and the server stamps its
   * own on write.
   */
  fun surveyToProto(
    survey: SurveyPreviewItem,
    forms: List<FormPreviewItem>,
    mapLayers: List<MapLayerItem>,
    config: SurveyConfig?,
    editorDraft: SurveyEditorDraft?,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): Pair<SurveyDef, SurveyAcl> {
    val layerDefs = mapLayers.map(SurveyDatasetMapper::mapLayerToProto)
    // Without an editor draft, the survey list's copy of the area is the survey's area.
    val surveyArea = if (editorDraft != null) editorDraft.details.surveyArea else survey.surveyArea
    val surveyAreaProto = surveyArea?.let(SurveyDatasetMapper::surveyAreaToProto)

    val purposeIds = editorDraft?.details?.purposeIds ?: config?.purposeIds.orEmpty()
    val programIds = editorDraft?.details?.programIds ?: config?.programIds.orEmpty()
    val templateOrigins =
      editorDraft?.forms?.map { it.form }?.let(TemplateOrigin::toProto)
        ?: config?.formTemplateOrigins.orEmpty()
    val conceptLinks =
      editorDraft?.forms?.map { it.form }?.let(FormConceptLinkSync::toFormConceptLinks)
        ?: config?.formConceptLinks.orEmpty()

    val surveyDef =
      SurveyDef(
        survey_id = survey.id,
        title = survey.title,
        description = survey.description,
        organization_id = survey.organizationId.orEmpty(),
        owner_id = survey.ownerEmail,
        state = SurveyLifecycleMapper.surveyStateToProto(survey.state),
        version = forms.firstOrNull()?.version ?: "1",
        forms = surveyFormDefs(config),
        entity_datasets = extraDatasetsToProto(editorDraft),
        map_config =
          MapConfig(
            initial_center = surveyAreaProto?.center,
            initial_zoom = surveyAreaProto?.zoom ?: 0.0,
            layers = layerDefs,
            survey_area = surveyAreaProto,
          ),
        default_language = editorDraft?.details?.defaultLanguage ?: "en",
        supported_languages = editorDraft?.details?.supportedLanguages ?: listOf("en"),
        form_launch_configs = forms.map(::formLaunchConfigToProto),
        purpose_ids = purposeIds,
        program_ids = programIds,
        form_template_origins = templateOrigins,
        form_concept_links = conceptLinks,
        outcome = survey.outcome?.let(SurveyLifecycleMapper::surveyOutcomeToProto),
        commit_timestamp = commitTimestamp,
        closed_at = survey.closedAt?.let(::parseIsoToInstant),
        saved_revision_number = 1L,
        location_label = survey.location,
        is_downloaded = survey.isDownloaded,
        thumbnail_theme = survey.thumbnailTheme.name,
        entity_count = survey.entityCount,
        other_purpose = editorDraft?.details?.otherPurpose.orEmpty(),
        primary_form_id = config?.primaryForm?.proto?.form_id.orEmpty(),
        quota_limits =
          ProtoOrganizationMapper.quotaLimitsToProto(
            editorDraft?.details?.quotaLimits ?: survey.quotaLimits
          ),
      )

    return surveyDef to surveyAclToProto(survey, config, editorDraft, commitTimestamp)
  }

  /** The survey's forms: the primary form, then the others by ID. */
  private fun surveyFormDefs(config: SurveyConfig?): List<FormDef> =
    listOfNotNull(config?.primaryForm?.proto?.copy(form_id = PRIMARY_FORM_PROTO_ID)) +
      config?.formsById.orEmpty().map { (formId, formDef) -> formDef.proto.copy(form_id = formId) }

  private fun formLaunchConfigToProto(f: FormPreviewItem): FormLaunchConfig =
    FormLaunchConfig(
      form_id = f.id,
      cta_label = LabelDef(text = f.ctaLabel),
      target_entity_dataset_id = f.targetDatasetId,
      availability = SurveyLifecycleMapper.formAvailabilityToProto(f.availability),
      title = f.title,
      description = f.description,
      version = f.version,
      target_dataset_name = f.targetDatasetName,
      target_type_noun = f.targetTypeNoun,
      question_count = f.questionCount,
      requires_entity = f.requiresEntity,
      open_in_google_earth =
        f.openInGoogleEarth &&
          f.availability.includesWeb &&
          f.requiresEntity &&
          f.targetDatasetId.isNotBlank(),
    )

  /**
   * The editor draft's datasets the survey keeps besides its map layers: data tables, generated
   * datasets, and datasets with properties linked to standard fields.
   */
  private fun extraDatasetsToProto(editorDraft: SurveyEditorDraft?): List<EntityDatasetDef> =
    editorDraft
      ?.datasets
      ?.filter {
        it.kind == DatasetKind.DATA_TABLE ||
          it.isGenerated ||
          it.properties.any { p -> p.conceptLink != null }
      }
      ?.map(SurveyDatasetMapper::entityDatasetToProto)
      .orEmpty()

  /** The survey's access list: the editor draft's sharing, else the survey's and [config]'s. */
  private fun surveyAclToProto(
    survey: SurveyPreviewItem,
    config: SurveyConfig?,
    editorDraft: SurveyEditorDraft?,
    commitTimestamp: Instant,
  ): SurveyAcl {
    val sharing = editorDraft?.sharing
    return SurveyAcl(
      survey_id = survey.id,
      entries =
        (sharing?.collaborators ?: config?.collaborators)
          .orEmpty()
          .map(SurveyAclMapper::collaboratorToProto),
      sharing_policy =
        SurveyAclMapper.sharingPolicyToProto(sharing?.policy ?: survey.sharingPolicy),
      peer_data_visibility =
        SurveyAclMapper.peerDataVisibilityToProto(
          sharing?.peerDataVisibility ?: survey.peerDataVisibility
        ),
      owner_id = sharing?.ownerEmail ?: survey.ownerEmail,
      owner_profile =
        (sharing?.ownerProfile ?: config?.ownerProfile)?.let(
          ProtoOrganizationMapper::cachedProfileToProto
        ),
      collector_consent_required =
        sharing?.collectorConsentRequired ?: survey.collectorConsentRequired,
      collector_consent_text = sharing?.collectorConsentText ?: survey.collectorConsentText,
      default_role =
        SurveyAclMapper.collaboratorRoleToProto(sharing?.defaultRole ?: survey.defaultRole),
      commit_timestamp = commitTimestamp,
    )
  }

  private const val DEFAULT_THUMBNAIL_COLOR_HEX: Long = 0xFF2E7D32L

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `ListSurveyOverviews` delta polls, and its
   * `record_version` is the higher of their record versions.
   */
  fun surveyOverviewFromDef(
    surveyDef: SurveyDef,
    acl: SurveyAcl? = null,
    hasWorkingCopy: Boolean = false,
    mapLayers: List<MapLayerItem> = emptyList(),
    entities: List<GeospatialEntityItem> = emptyList(),
  ): SurveyOverview {
    val geoEntitiesCount = entities.count { it.geometry != null }
    val geospatialRowsCount =
      surveyDef.entity_datasets.filter { it.type == EntityType.GEOSPATIAL }.sumOf { it.rows.size }
    val resolvedEntityCount =
      when {
        surveyDef.entity_count > 0 -> surveyDef.entity_count
        geoEntitiesCount > 0 -> geoEntitiesCount
        else -> geospatialRowsCount
      }
    return SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      // A trashed survey's overview is a tombstone, matching the server's survey list.
      state = if (surveyDef.trash_time != null) SurveyState.DELETED else surveyDef.state,
      trash_time = surveyDef.trash_time,
      version = surveyDef.version,
      saved_revision_number = surveyDef.saved_revision_number,
      location_label = surveyDef.location_label,
      is_downloaded = surveyDef.is_downloaded,
      thumbnail_theme = surveyDef.thumbnail_theme,
      entity_count = resolvedEntityCount,
      survey_area = surveyDef.map_config?.survey_area,
      outcome = surveyDef.outcome,
      closed_at = surveyDef.closed_at,
      commit_timestamp = latestOf(surveyDef.commit_timestamp, acl?.commit_timestamp),
      record_version = maxOf(surveyDef.record_version, acl?.record_version ?: 0L),
      has_working_copy = hasWorkingCopy,
      sharing_policy = acl?.sharing_policy ?: SharingPolicyProto.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibilityProto.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
      default_role =
        acl?.default_role?.takeIf { it in SurveyMutations.SHARING_ROLES } ?: Role.DATA_COLLECTOR,
      quota_limits = surveyDef.quota_limits,
      thumbnail_features =
        if (surveyDef.trash_time != null) {
          emptyList()
        } else {
          thumbnailFeaturesToProto(surveyDef, mapLayers, entities)
        },
    )
  }

  private fun thumbnailFeaturesToProto(
    surveyDef: SurveyDef,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): List<SurveyThumbnailFeatureProto> {
    val effectiveLayers = mapLayers.ifEmpty { mapLayersFromProto(surveyDef) }
    val layersById = effectiveLayers.associateBy { it.id }
    val layersByDatasetId =
      effectiveLayers
        .mapNotNull { layer ->
          layer.datasetId?.takeIf { it.isNotBlank() }?.let { it to layer }
        }
        .toMap()

    if (entities.isEmpty()) {
      return datasetThumbnailFeaturesToProto(surveyDef, layersByDatasetId)
    }

    val sourceEntities = selectThumbnailSourceEntities(entities, effectiveLayers)
    return sourceEntities
      .mapNotNull { entity ->
        val geometry = entity.geometry ?: return@mapNotNull null
        val layer = layersById[entity.layerId] ?: layersByDatasetId[entity.datasetId]
        val rawColor =
          layer?.colorHex ?: entity.colorHex.takeIf { it != 0L } ?: DEFAULT_THUMBNAIL_COLOR_HEX
        SurveyThumbnailFeatureProto(
          entity_id = entity.id,
          geometry = GeometryMapper.toProto(geometry),
          color_hex = 0xFF000000L or (rawColor and 0xFFFFFFL),
        )
      }
      .sampleEvenly(SurveyThumbnailGenerator.MAX_THUMBNAIL_FEATURES)
  }

  private fun datasetThumbnailFeaturesToProto(
    surveyDef: SurveyDef,
    layersByDatasetId: Map<String, MapLayerItem>,
  ): List<SurveyThumbnailFeatureProto> =
    surveyDef.entity_datasets
      .filter { it.type == EntityType.GEOSPATIAL }
      .flatMap { ds ->
        val mappedDs = SurveyDatasetMapper.entityDatasetFromProto(ds)
        val layer = layersByDatasetId[ds.id]
        val rawColor =
          layer?.colorHex
            ?: parseHexColorOrDefault(ds.default_style?.color, DEFAULT_THUMBNAIL_COLOR_HEX)
        val colorHex = 0xFF000000L or (rawColor and 0xFFFFFFL)
        val geomType = mappedDs.geometryType
        mappedDs.rows.mapNotNull { row ->
          val geom =
            when (geomType) {
              GeometryType.POLYGON ->
                row.geometry.takeIf { it.size >= 3 }?.let { Geometry.Polygon(listOf(it)) }
              GeometryType.LINE_STRING ->
                row.geometry.takeIf { it.size >= 2 }?.let { Geometry.LineString(it) }
              GeometryType.POINT -> row.geometry.firstOrNull()?.let { Geometry.Point(it) }
            } ?: return@mapNotNull null
          SurveyThumbnailFeatureProto(
            entity_id = row.key,
            geometry = GeometryMapper.toProto(geom),
            color_hex = colorHex,
          )
        }
      }
      .sampleEvenly(SurveyThumbnailGenerator.MAX_THUMBNAIL_FEATURES)

  private fun selectThumbnailSourceEntities(
    entities: List<GeospatialEntityItem>,
    effectiveLayers: List<MapLayerItem>,
  ): List<GeospatialEntityItem> {
    val visibleLayerIds = effectiveLayers.filter { it.isVisible }.map { it.id }.toSet()
    val visibleDatasetIds =
      effectiveLayers
        .filter { it.isVisible }
        .mapNotNull { it.datasetId?.takeIf(String::isNotBlank) }
        .toSet()
    return if (visibleLayerIds.isEmpty() && visibleDatasetIds.isEmpty()) {
      entities
    } else {
      entities
        .filter { entity ->
          entity.layerId in visibleLayerIds ||
            (entity.datasetId.isNotBlank() && entity.datasetId in visibleDatasetIds)
        }
        .ifEmpty { entities }
    }
  }

  private fun thumbnailFeaturesFromOverviewProto(
    features: List<SurveyThumbnailFeatureProto>
  ): List<SurveyThumbnailFeature> = features.flatMap { proto ->
    val geometry = proto.geometry?.let(GeometryMapper::fromProto) ?: return@flatMap emptyList()
    val colorHex =
      0xFF000000L or
        ((proto.color_hex.takeIf { it != 0L } ?: DEFAULT_THUMBNAIL_COLOR_HEX) and 0xFFFFFFL)
    geometry.parts.map { part ->
      SurveyThumbnailFeature(
        id = proto.entity_id,
        type = geometry.type,
        vertices = part.outline,
        colorHex = colorHex,
      )
    }
  }

  /** The later of two optional timestamps, comparing nanoseconds when the seconds tie. */
  private fun latestOf(a: Instant?, b: Instant?): Instant? =
    when {
      a == null -> b
      b == null -> a
      a.getEpochSecond() != b.getEpochSecond() ->
        if (a.getEpochSecond() > b.getEpochSecond()) a else b
      else -> if (a.getNano() >= b.getNano()) a else b
    }

  /**
   * The survey list entry of [overview], for surveys known only from the catalog as well as stored
   * ones. Uses [entities] when downloaded locally, falling back to [overview]'s pre-extracted
   * `thumbnail_features` for non-downloaded catalog previews. Unknown thumbnail themes fall back to
   * `SAVANNA`.
   */
  fun surveyPreviewFromOverviewProto(
    overview: SurveyOverview,
    mapLayers: List<MapLayerItem> = emptyList(),
    entities: List<GeospatialEntityItem> = emptyList(),
  ): SurveyPreviewItem {
    val surveyArea = overview.survey_area?.let(SurveyDatasetMapper::surveyAreaFromProto)
    val theme =
      MapThumbnailTheme.entries.firstOrNull { it.name == overview.thumbnail_theme }
        ?: MapThumbnailTheme.SAVANNA
    val thumbnail =
      if (entities.isEmpty() && overview.thumbnail_features.isNotEmpty()) {
        SurveyThumbnailGenerator.fromOverviewFeatures(
          surveyId = overview.survey_id,
          surveyArea = surveyArea,
          features = thumbnailFeaturesFromOverviewProto(overview.thumbnail_features),
        )
      } else {
        SurveyThumbnailGenerator.fromRuntime(
          surveyId = overview.survey_id,
          surveyArea = surveyArea,
          mapLayers = mapLayers,
          entities = entities,
        )
      }
    return SurveyPreviewItem(
      id = overview.survey_id,
      title = overview.title,
      description = overview.description,
      location = overview.location_label,
      isDownloaded = overview.is_downloaded,
      thumbnailTheme = theme,
      entityCount =
        if (overview.entity_count > 0) overview.entity_count
        else entities.count { it.geometry != null },
      surveyArea = surveyArea,
      thumbnail = thumbnail,
      ownerEmail = overview.owner_id,
      organizationId = overview.organization_id.takeIf { it.isNotBlank() },
      state = SurveyLifecycleMapper.surveyStateFromProto(overview.state),
      closedAt = overview.closed_at?.let { isoUtc(it.toKotlinInstant()) },
      outcome = overview.outcome?.let(SurveyLifecycleMapper::surveyOutcomeFromProto),
      sharingPolicy = SurveyAclMapper.sharingPolicyFromProto(overview.sharing_policy),
      peerDataVisibility =
        SurveyAclMapper.peerDataVisibilityFromProto(overview.peer_data_visibility),
      collectorConsentRequired = overview.collector_consent_required,
      collectorConsentText = overview.collector_consent_text,
      defaultRole = SurveyAclMapper.collaboratorRoleFromProto(overview.default_role),
      quotaLimits = ProtoOrganizationMapper.quotaLimitsFromProto(overview.quota_limits),
    )
  }

  /**
   * The survey list entry of a stored survey, built through its overview so both paths agree. The
   * owner falls back to [acl]'s when the definition doesn't name one.
   */
  fun surveyPreviewFromProto(
    surveyDef: SurveyDef,
    acl: SurveyAcl?,
    mapLayers: List<MapLayerItem> = emptyList(),
    entities: List<GeospatialEntityItem> = emptyList(),
  ): SurveyPreviewItem =
    surveyPreviewFromOverviewProto(
      overview =
        surveyOverviewFromDef(
            surveyDef = surveyDef,
            acl = acl,
            mapLayers = mapLayers,
            entities = entities,
          )
          .let { o -> o.copy(owner_id = o.owner_id.ifBlank { acl?.owner_id.orEmpty() }) },
      mapLayers = mapLayers,
      entities = entities,
    )

  /**
   * The survey's launchable forms, one per `FormLaunchConfig`, in launch-config order. A form
   * selects a map feature when its definition has an entity reference question (see
   * [hasEntitySelectQuestionInFormDef]) or its launch config requires a target dataset.
   */
  fun formsFromProto(surveyDef: SurveyDef): List<FormPreviewItem> {
    val formsById = surveyDef.forms.associateBy { it.form_id }
    val unmigratedSeed =
      surveyDef.record_version <= 5L &&
        surveyDef.survey_id in LEGACY_SEED_SURVEY_IDS &&
        surveyDef.form_launch_configs.none { it.open_in_google_earth }
    return surveyDef.form_launch_configs.map { lc ->
      val formDef = formsById[lc.form_id]
      val availability = SurveyLifecycleMapper.formAvailabilityFromProto(lc.availability)
      val hasEntitySelect =
        (formDef != null && hasEntitySelectQuestionInFormDef(formDef)) ||
          (lc.requires_entity && lc.target_entity_dataset_id.isNotBlank())
      val openInEarth =
        (lc.open_in_google_earth ||
          (unmigratedSeed && lc.requires_entity && lc.target_entity_dataset_id.isNotBlank())) &&
          availability.includesWeb &&
          lc.requires_entity &&
          lc.target_entity_dataset_id.isNotBlank()
      FormPreviewItem(
        id = lc.form_id,
        title = lc.title,
        description = lc.description,
        version = lc.version,
        targetDatasetId = lc.target_entity_dataset_id,
        targetDatasetName = lc.target_dataset_name,
        questionCount = lc.question_count,
        ctaLabel = lc.cta_label?.text.orEmpty(),
        requiresEntity = lc.requires_entity,
        availability = availability,
        hasEntitySelectQuestion = hasEntitySelect,
        targetTypeNoun = lc.target_type_noun,
        openInGoogleEarth = openInEarth,
      )
    }
  }

  /**
   * Whether [formDef] asks the collector to pick a map feature: it binds one of the known entity
   * reference field paths (`target_entity`, `sample_plot_entity`, ...), or has a control with the
   * ODK `map-select` appearance (searched through groups and repeats).
   */
  private fun hasEntitySelectQuestionInFormDef(formDef: FormDef): Boolean {
    val entityRefPaths =
      setOf(
        "/data/target_entity",
        "target_entity",
        "/data/sample_plot_entity",
        "sample_plot_entity",
        "/data/past_individual_id",
        "past_individual_id",
        "/data/primary_respondent_id",
        "primary_respondent_id",
      )
    if (formDef.model?.bindings.orEmpty().any { it.field_path in entityRefPaths }) return true
    fun checkComponents(components: List<ViewComponent>): Boolean = components.any { comp ->
      val ctrl = comp.control
      val grp = comp.group
      val rep = comp.repeat
      when {
        ctrl != null ->
          ctrl.field_ref in entityRefPaths ||
            ctrl.appearance.split(Regex("\\s+")).contains("map-select")
        grp != null -> checkComponents(grp.components)
        rep != null -> checkComponents(rep.components)
        else -> false
      }
    }
    return checkComponents(formDef.view?.components.orEmpty())
  }

  fun mapLayersFromProto(surveyDef: SurveyDef): List<MapLayerItem> {
    val designsByDatasetId =
      surveyDef.entity_datasets
        .mapNotNull { ds ->
          ds.sampling?.let(SamplingDesignProtos::fromProto)?.let { ds.id to it }
        }
        .toMap()
    return surveyDef.map_config
      ?.layers
      ?.map { proto ->
        val layer = SurveyDatasetMapper.mapLayerFromProto(proto)
        val design = layer.datasetId?.let { designsByDatasetId[it] }
        if (design != null) layer.copy(sampleDesign = design) else layer
      }
      .orEmpty()
  }

  /**
   * The survey's form definitions and settings. The primary form is the one stored under
   * [PRIMARY_FORM_PROTO_ID], renamed back to `primary_form_id` (or kept under the placeholder when
   * that's blank); without a placeholder form, the first form is the primary one. Other forms are
   * keyed by the launch config that names them; forms no launch config names are left out.
   */
  fun surveyConfigFromProto(surveyDef: SurveyDef, acl: SurveyAcl? = null): SurveyConfig {
    val primaryProto =
      surveyDef.forms
        .firstOrNull { it.form_id == PRIMARY_FORM_PROTO_ID }
        ?.copy(form_id = surveyDef.primary_form_id.ifBlank { PRIMARY_FORM_PROTO_ID })
        ?: surveyDef.forms.firstOrNull { it.form_id != PRIMARY_FORM_PROTO_ID }
    val primaryForm = primaryProto?.let(::FormDefinition)

    val formsById = mutableMapOf<String, FormDefinition>()
    surveyDef.form_launch_configs.forEach { lc ->
      surveyDef.forms
        .firstOrNull { it.form_id == lc.form_id }
        ?.let { formsById[lc.form_id] = FormDefinition(it) }
    }
    return SurveyConfig(
      primaryForm = primaryForm,
      formsById = formsById,
      formConceptLinks = surveyDef.form_concept_links,
      purposeIds = surveyDef.purpose_ids,
      programIds = surveyDef.program_ids,
      formTemplateOrigins = surveyDef.form_template_origins,
      collaborators = acl?.entries?.map(SurveyAclMapper::collaboratorFromProto).orEmpty(),
      ownerProfile = acl?.owner_profile?.let(ProtoOrganizationMapper::cachedProfileFromProto),
    )
  }

  /**
   * The survey editor's draft of a stored survey: derived from its projections, then completed with
   * what only the definition holds (languages, area, purposes, data tables, concept links on map
   * layer properties) and [acl]'s sharing. A draft survey without a stored area keeps none rather
   * than the derived default.
   */
  fun surveyEditorDraftFromProto(
    surveyDef: SurveyDef,
    acl: SurveyAcl?,
    surveyPreview: SurveyPreviewItem,
    forms: List<FormPreviewItem>,
    config: SurveyConfig,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft {
    val base =
      SurveyEditorDerivation.derive(
        surveyId = surveyDef.survey_id,
        survey = surveyPreview,
        forms = forms,
        formDefinition = { form ->
          config.formsById[form.id] ?: config.primaryForm
        },
        mapLayers = mapLayers,
        entities = entities,
        conceptLinks = surveyDef.form_concept_links,
        purposeIds = surveyDef.purpose_ids,
        programIds = surveyDef.program_ids,
        templateOrigins = surveyDef.form_template_origins,
      )
    val surveyArea =
      surveyDef.map_config?.survey_area?.let(SurveyDatasetMapper::surveyAreaFromProto)
    val tabularDatasets =
      surveyDef.entity_datasets
        .filter { it.type == EntityType.TABULAR }
        .map(SurveyDatasetMapper::entityDatasetFromProto)
    val geospatialDatasetsById =
      surveyDef.entity_datasets
        .filter { it.type == EntityType.GEOSPATIAL }
        .associate { it.id to SurveyDatasetMapper.entityDatasetFromProto(it) }
    val mergedMapDatasets =
      base.datasets.map { ds ->
        val storedDs = geospatialDatasetsById[ds.id] ?: return@map ds
        mergeStoredGeospatialDataset(
          derived = ds,
          stored = storedDs,
          surveyId = surveyDef.survey_id,
          mapLayers = mapLayers,
          entities = entities,
        )
      }
    val baseDatasetIds = base.datasets.map { it.id }.toSet()
    val unprojectedGeneratedDatasets =
      geospatialDatasetsById.values.filter { it.id !in baseDatasetIds && it.isGenerated }
    val sharing =
      if (acl != null) {
        SharingSettings(
          ownerEmail = acl.owner_id.ifBlank { surveyPreview.ownerEmail },
          ownerProfile = acl.owner_profile?.let(ProtoOrganizationMapper::cachedProfileFromProto),
          policy = SurveyAclMapper.sharingPolicyFromProto(acl.sharing_policy),
          peerDataVisibility =
            SurveyAclMapper.peerDataVisibilityFromProto(acl.peer_data_visibility),
          collaborators = acl.entries.map(SurveyAclMapper::collaboratorFromProto),
          collectorConsentRequired = acl.collector_consent_required,
          collectorConsentText = acl.collector_consent_text,
          defaultRole = SurveyAclMapper.collaboratorRoleFromProto(acl.default_role),
        )
      } else {
        base.sharing
      }

    return base.copy(
      details =
        base.details.copy(
          defaultLanguage = surveyDef.default_language.ifBlank { "en" },
          supportedLanguages = surveyDef.supported_languages.ifEmpty { listOf("en") },
          surveyArea = surveyArea ?: base.details.surveyArea.takeIf { !surveyPreview.isDraft },
          organizationId = surveyDef.organization_id.takeIf { it.isNotBlank() },
          purposeIds = surveyDef.purpose_ids,
          otherPurpose = surveyDef.other_purpose,
          programIds = surveyDef.program_ids,
          quotaLimits = ProtoOrganizationMapper.quotaLimitsFromProto(surveyDef.quota_limits),
        ),
      datasets = mergedMapDatasets + unprojectedGeneratedDatasets + tabularDatasets,
      sharing = sharing,
    )
  }

  private fun mergeStoredGeospatialDataset(
    derived: EntityDataset,
    stored: EntityDataset,
    surveyId: String,
    mapLayers: List<MapLayerItem>,
    entities: List<GeospatialEntityItem>,
  ): EntityDataset {
    val propLinks =
      stored.properties
        .mapNotNull { p ->
          p.conceptLink?.let { p.name to it.copy(version = it.version.coerceAtLeast(1)) }
        }
        .toMap()
    if (
      !stored.isGenerated &&
        (stored.keyProperty.isBlank() || stored.keyProperty == SurveyEditorDerivation.ID_PROPERTY)
    ) {
      return if (propLinks.isEmpty()) {
        derived
      } else {
        derived.copy(
          properties =
            derived.properties.map { prop ->
              val link = propLinks[prop.name]
              if (link != null) prop.copy(conceptLink = link) else prop
            }
        )
      }
    }
    val storedNames = stored.properties.map { it.name }.toSet()
    val extraDerivedProps =
      derived.properties.filterNot {
        it.name in storedNames ||
          it.name == SurveyEditorDerivation.ID_PROPERTY ||
          it.name == SurveyEditorDerivation.LABEL_PROPERTY
      }
    val mergedProps =
      (stored.properties + extraDerivedProps).map { prop ->
        val link = propLinks[prop.name]
        if (link != null) prop.copy(conceptLink = link) else prop
      }
    val baseMerged =
      stored.copy(
        key = stored.key.ifBlank { derived.key },
        displayName = derived.displayName.ifBlank { stored.displayName },
        linkedFormKey = derived.linkedFormKey ?: stored.linkedFormKey,
        properties = mergedProps,
      )
    val layer = SurveyEditorDerivation.runtimeLayerFor(baseMerged, mapLayers)
    val layerEntities =
      if (layer != null) entities.filter { it.layerId == layer.id } else emptyList()
    return if (layerEntities.isEmpty()) {
      baseMerged
    } else {
      SurveyEditorDerivation.refreshDataset(baseMerged, surveyId, layerEntities)
    }
  }

  /**
   * Whether [workingCopy] holds edits beyond the [saved] revision, ignoring the versioning fields
   * the service stamps on every write.
   */
  fun differsFromSaved(workingCopy: SurveyDef, saved: SurveyDef): Boolean =
    workingCopy.copy(record_version = 0L, commit_timestamp = null) !=
      saved.copy(record_version = 0L, commit_timestamp = null)

  /**
   * IDs of [surveyDef]'s datasets whose features are generated sample plots
   * (`EntityDatasetDef.sampling`), matching [EntityDataset.isGenerated].
   */
  fun generatedDatasetIds(surveyDef: SurveyDef?): Set<String> =
    surveyDef?.entity_datasets.orEmpty().filter { it.sampling != null }.map { it.id }.toSet()

  /**
   * The editor draft of an organizer's unsaved [workingCopy], deriving every projection from the
   * working copy itself rather than from the stored (saved) ones.
   */
  fun surveyEditorDraftFromWorkingCopyProto(
    workingCopy: SurveyDef,
    acl: SurveyAcl?,
    entities: List<GeospatialEntityItem>,
  ): SurveyEditorDraft {
    val forms = formsFromProto(workingCopy)
    val mapLayers = mapLayersFromProto(workingCopy)
    val config = surveyConfigFromProto(workingCopy, acl)
    val preview = surveyPreviewFromProto(workingCopy, acl, mapLayers, entities)
    return surveyEditorDraftFromProto(
      surveyDef = workingCopy,
      acl = acl,
      surveyPreview = preview,
      forms = forms,
      config = config,
      mapLayers = mapLayers,
      entities = entities,
    )
  }
}
