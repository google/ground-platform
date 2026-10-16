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
package org.groundplatform.v2.app.data.datasource.remote

import com.squareup.wire.Instant
import com.squareup.wire.ofEpochSecond
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.ImpactEvent as ImpactEventProto
import groundplatform.v2.data.ImpactEventType as ImpactEventTypeProto
import groundplatform.v2.data.MutationLogRecord
import groundplatform.v2.data.PlaceRecord
import groundplatform.v2.data.SubmissionFieldEntryDef
import groundplatform.v2.data.SubmissionGeometryPolygonDef
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.GeoShape
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.library.ConceptRef
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.CachedProfile as CachedProfileProto
import groundplatform.v2.survey.DataTableRow
import groundplatform.v2.survey.EntityDatasetDef
import groundplatform.v2.survey.EntityPropertyDefinition
import groundplatform.v2.survey.EntityType
import groundplatform.v2.survey.FormAvailability as FormAvailabilityProto
import groundplatform.v2.survey.FormGeometrySource
import groundplatform.v2.survey.FormLaunchConfig
import groundplatform.v2.survey.GeometryStyle
import groundplatform.v2.survey.ImagerySource as ImagerySourceProto
import groundplatform.v2.survey.ImagerySourceType as ImagerySourceTypeProto
import groundplatform.v2.survey.LayerDef
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.MembershipStatus as MembershipStatusProto
import groundplatform.v2.survey.OfflineTilePackageDef
import groundplatform.v2.survey.Organization as OrganizationProto
import groundplatform.v2.survey.OrganizationMember as OrganizationMemberProto
import groundplatform.v2.survey.OrganizationRole as OrganizationRoleProto
import groundplatform.v2.survey.OrganizationType as OrganizationTypeProto
import groundplatform.v2.survey.PeerDataVisibility as PeerDataVisibilityProto
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SharingPolicy as SharingPolicyProto
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyArea as SurveyAreaProto
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyOutcome as SurveyOutcomeProto
import groundplatform.v2.survey.SurveyOverview
import groundplatform.v2.survey.SurveyState
import org.groundplatform.v2.app.data.datasource.local.LibraryProtoMapper
import org.groundplatform.v2.app.domain.model.CachedProfile
import org.groundplatform.v2.app.domain.model.ConceptLink
import org.groundplatform.v2.app.domain.model.EffortComparison
import org.groundplatform.v2.app.domain.model.FormAvailability
import org.groundplatform.v2.app.domain.model.FormPreviewItem
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.domain.model.ImagerySource
import org.groundplatform.v2.app.domain.model.ImagerySourceType
import org.groundplatform.v2.app.domain.model.ImpactEvent
import org.groundplatform.v2.app.domain.model.ImpactEventType
import org.groundplatform.v2.app.domain.model.InvitationStatus
import org.groundplatform.v2.app.domain.model.LayerSourceType
import org.groundplatform.v2.app.domain.model.MapLayerItem
import org.groundplatform.v2.app.domain.model.MapThumbnailTheme
import org.groundplatform.v2.app.domain.model.MembershipStatus
import org.groundplatform.v2.app.domain.model.MutationLogItem
import org.groundplatform.v2.app.domain.model.MutationOperationKind
import org.groundplatform.v2.app.domain.model.MutationSyncState
import org.groundplatform.v2.app.domain.model.OfflineTilePackageItem
import org.groundplatform.v2.app.domain.model.Organization
import org.groundplatform.v2.app.domain.model.OrganizationLibrary
import org.groundplatform.v2.app.domain.model.OrganizationMember
import org.groundplatform.v2.app.domain.model.OrganizationRole
import org.groundplatform.v2.app.domain.model.OrganizationType
import org.groundplatform.v2.app.domain.model.SubmissionFieldEntry
import org.groundplatform.v2.app.domain.model.SubmissionGeometryPolygon
import org.groundplatform.v2.app.domain.model.SubmissionPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyConfig
import org.groundplatform.v2.app.domain.model.SurveyLifecycleState
import org.groundplatform.v2.app.domain.model.SurveyOutcome
import org.groundplatform.v2.app.domain.model.SurveyOutcomeKind
import org.groundplatform.v2.app.domain.model.SurveyPlaceItem
import org.groundplatform.v2.app.domain.model.SurveyPreviewItem
import org.groundplatform.v2.app.domain.model.SurveyThumbnailGenerator
import org.groundplatform.v2.app.domain.model.SyncStatus
import org.groundplatform.v2.app.domain.model.editor.Collaborator
import org.groundplatform.v2.app.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.app.domain.model.editor.DatasetKind
import org.groundplatform.v2.app.domain.model.editor.EntityDataset
import org.groundplatform.v2.app.domain.model.editor.EntityProperty
import org.groundplatform.v2.app.domain.model.editor.EntityRow
import org.groundplatform.v2.app.domain.model.editor.FormConceptLinkSync
import org.groundplatform.v2.app.domain.model.editor.GeometryKind
import org.groundplatform.v2.app.domain.model.editor.LatLng
import org.groundplatform.v2.app.domain.model.editor.LayerStyle
import org.groundplatform.v2.app.domain.model.editor.PeerDataVisibility
import org.groundplatform.v2.app.domain.model.editor.PropertyType
import org.groundplatform.v2.app.domain.model.editor.SharingPolicy
import org.groundplatform.v2.app.domain.model.editor.SharingSettings
import org.groundplatform.v2.app.domain.model.editor.SurveyArea
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDerivation
import org.groundplatform.v2.app.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.app.domain.model.editor.epochMillisOfIsoUtc
import org.groundplatform.v2.app.domain.model.editor.isoUtc
import org.groundplatform.v2.app.domain.model.formatHexColorCss
import org.groundplatform.v2.app.domain.model.parseHexColorOrDefault
import org.groundplatform.v2.core.forms.model.FormDefinition

/**
 * Bidirectional mapper between Wire-generated gRPC Protocol Buffer messages (`groundplatform.v2.*`)
 * and `shared/app` domain models stored in `LocalStore`.
 */
object GrpcProtoMapper {
  private const val PRIMARY_FORM_PROTO_ID = "__primary_form__"

  // ---------------------------------------------------------------------------
  // Organization ↔ OrganizationProto
  // ---------------------------------------------------------------------------

  fun organizationToProto(
    org: Organization,
    version: Long = 1L,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): OrganizationProto =
    OrganizationProto(
      organization_id = org.id,
      name = org.name,
      description = org.description,
      website_url = org.websiteUrl,
      logo_url = org.logoUrl.orEmpty(),
      listed = org.isListed,
      members = org.members.map(::memberToProto),
      created_at = parseIsoToInstant(org.createdOn),
      imagery_sources = org.imagerySources.map(::imagerySourceToProto),
      organization_type =
        when (org.organizationType) {
          OrganizationType.GOVERNMENT_AGENCY -> OrganizationTypeProto.GOVERNMENT_AGENCY
          OrganizationType.COOPERATIVE -> OrganizationTypeProto.COOPERATIVE
          OrganizationType.IPLC_ORGANIZATION -> OrganizationTypeProto.IPLC_ORGANIZATION
          OrganizationType.NGO -> OrganizationTypeProto.NGO
          OrganizationType.COMPANY -> OrganizationTypeProto.COMPANY
          OrganizationType.RESEARCH -> OrganizationTypeProto.RESEARCH
          OrganizationType.OTHER -> OrganizationTypeProto.OTHER
          null -> OrganizationTypeProto.ORGANIZATION_TYPE_UNSPECIFIED
        },
      country_code = org.countryCode.orEmpty(),
      exclude_from_platform_aggregates = org.excludeFromPlatformAggregates,
      version = version,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
    )

  fun organizationFromProto(proto: OrganizationProto): Organization {
    val isSynthetic = proto.organization_id == Organization.ALL_USERS_ID
    return Organization(
      id = proto.organization_id,
      name = proto.name,
      description = proto.description,
      websiteUrl = proto.website_url,
      logoUrl = proto.logo_url.takeIf { it.isNotBlank() },
      isListed = proto.listed,
      members = proto.members.map(::memberFromProto),
      createdOn = formatInstantDateOrIso(proto.created_at),
      imagerySources = proto.imagery_sources.map(::imagerySourceFromProto),
      organizationType =
        when (proto.organization_type) {
          OrganizationTypeProto.GOVERNMENT_AGENCY -> OrganizationType.GOVERNMENT_AGENCY
          OrganizationTypeProto.COOPERATIVE -> OrganizationType.COOPERATIVE
          OrganizationTypeProto.IPLC_ORGANIZATION -> OrganizationType.IPLC_ORGANIZATION
          OrganizationTypeProto.NGO -> OrganizationType.NGO
          OrganizationTypeProto.COMPANY -> OrganizationType.COMPANY
          OrganizationTypeProto.RESEARCH -> OrganizationType.RESEARCH
          OrganizationTypeProto.OTHER -> OrganizationType.OTHER
          OrganizationTypeProto.ORGANIZATION_TYPE_UNSPECIFIED -> null
        },
      countryCode = proto.country_code.takeIf { it.isNotBlank() },
      excludeFromPlatformAggregates = proto.exclude_from_platform_aggregates,
      isSynthetic = isSynthetic,
    )
  }

  fun memberToProto(member: OrganizationMember): OrganizationMemberProto =
    OrganizationMemberProto(
      user_id = member.userId.orEmpty(),
      email = member.email,
      role =
        when (member.role) {
          OrganizationRole.MEMBER -> OrganizationRoleProto.MEMBER
          OrganizationRole.MANAGER -> OrganizationRoleProto.MANAGER
        },
      status =
        when (member.status) {
          MembershipStatus.INVITED -> MembershipStatusProto.INVITED
          MembershipStatus.REQUESTED -> MembershipStatusProto.REQUESTED
          MembershipStatus.ACTIVE -> MembershipStatusProto.ACTIVE
        },
      invite_token = member.inviteToken.orEmpty(),
      profile = member.profile?.let(::cachedProfileToProto),
    )

  fun memberFromProto(proto: OrganizationMemberProto): OrganizationMember =
    OrganizationMember(
      email = proto.email,
      role =
        when (proto.role) {
          OrganizationRoleProto.MANAGER -> OrganizationRole.MANAGER
          else -> OrganizationRole.MEMBER
        },
      status =
        when (proto.status) {
          MembershipStatusProto.INVITED -> MembershipStatus.INVITED
          MembershipStatusProto.REQUESTED -> MembershipStatus.REQUESTED
          else -> MembershipStatus.ACTIVE
        },
      inviteToken = proto.invite_token.takeIf { it.isNotBlank() },
      userId = proto.user_id.takeIf { it.isNotBlank() },
      profile = proto.profile?.let(::cachedProfileFromProto),
    )

  fun imagerySourceToProto(source: ImagerySource): ImagerySourceProto =
    ImagerySourceProto(
      imagery_source_id = source.id,
      name = source.name,
      type =
        when (source.type) {
          ImagerySourceType.XYZ_TILES -> ImagerySourceTypeProto.XYZ_TILES
        },
      url_template = source.urlTemplate,
      allow_offline_download = source.allowOfflineDownload,
    )

  fun imagerySourceFromProto(proto: ImagerySourceProto): ImagerySource =
    ImagerySource(
      id = proto.imagery_source_id,
      name = proto.name,
      urlTemplate = proto.url_template,
      type = ImagerySourceType.XYZ_TILES,
      allowOfflineDownload = proto.allow_offline_download,
    )

  fun cachedProfileToProto(profile: CachedProfile): CachedProfileProto =
    CachedProfileProto(
      display_name = profile.displayName,
      avatar_url = profile.photoUrl.orEmpty(),
      cached_on = profile.cachedOn,
    )

  fun cachedProfileFromProto(proto: CachedProfileProto): CachedProfile =
    CachedProfile(
      displayName = proto.display_name,
      photoUrl = proto.avatar_url.takeIf { it.isNotBlank() },
      cachedOn = proto.cached_on,
    )

  // ---------------------------------------------------------------------------
  // OrganizationLibrary ↔ OrganizationLibraryRecord
  // ---------------------------------------------------------------------------

  fun libraryToProto(
    library: OrganizationLibrary,
    version: Long = 1L,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): OrganizationLibraryRecord =
    OrganizationLibraryRecord(
      organization_id = library.organizationId,
      bundle = LibraryProtoMapper.toBundle(library, includeOrganizationId = true),
      settings = LibraryProtoMapper.toProto(library.settings, library.organizationId),
      version = version,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
    )

  fun libraryFromProto(proto: OrganizationLibraryRecord): OrganizationLibrary {
    val parsed =
      LibraryProtoMapper.fromBundle(
        bundle = proto.bundle ?: groundplatform.v2.library.LibraryBundle(),
        organizationId = proto.organization_id,
        settings =
          proto.settings?.let(LibraryProtoMapper::fromProto)
            ?: org.groundplatform.v2.app.domain.model.OrganizationLibrarySettings(),
      )
    return parsed.copy(
      formTemplates =
        parsed.formTemplates.map { tpl ->
          tpl.copy(
            form = tpl.form.copy(questions = tpl.form.questions.map { q -> q.copy(key = q.name) })
          )
        }
    )
  }

  // ---------------------------------------------------------------------------
  // SurveyPreviewItem + Config + Layers + Draft ↔ SurveyDef & SurveyAcl
  // ---------------------------------------------------------------------------

  fun surveyToProto(
    survey: SurveyPreviewItem,
    forms: List<FormPreviewItem>,
    mapLayers: List<MapLayerItem>,
    config: SurveyConfig?,
    editorDraft: SurveyEditorDraft?,
    offlineTilePackages: List<OfflineTilePackageItem> = emptyList(),
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): Pair<SurveyDef, SurveyAcl> {
    val formDefs = mutableListOf<FormDef>()
    config?.primaryForm?.let { primary ->
      formDefs += primary.proto.copy(form_id = PRIMARY_FORM_PROTO_ID)
    }
    config?.formsById?.forEach { (formId, formDef) ->
      formDefs += formDef.proto.copy(form_id = formId)
    }

    val launchConfigs = forms.map { f ->
      FormLaunchConfig(
        form_id = f.id,
        cta_label = LabelDef(text = f.ctaLabel),
        target_entity_dataset_id = f.targetDatasetId,
        availability = formAvailabilityToProto(f.availability),
        title = f.title,
        description = f.description,
        version = f.version,
        target_dataset_name = f.targetDatasetName,
        question_count = f.questionCount,
        requires_entity = f.requiresEntity,
      )
    }

    val layerDefs = mapLayers.map(::mapLayerToProto)
    val surveyAreaProto = editorDraft?.details?.surveyArea?.let(::surveyAreaToProto)

    val extraDatasets =
      editorDraft
        ?.datasets
        ?.filter {
          it.kind == DatasetKind.DATA_TABLE || it.properties.any { p -> p.conceptLink != null }
        }
        ?.map(::entityDatasetToProto)
        .orEmpty()

    val purposeIds = editorDraft?.details?.purposeIds ?: config?.purposeIds.orEmpty()
    val programIds = editorDraft?.details?.programIds ?: config?.programIds.orEmpty()
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
        state = surveyStateToProto(survey.state),
        version = forms.firstOrNull()?.version ?: "1",
        forms = formDefs,
        entity_datasets = extraDatasets,
        map_config =
          MapConfig(
            initial_center = surveyAreaProto?.center,
            initial_zoom = surveyAreaProto?.zoom ?: 0.0,
            layers = layerDefs,
            survey_area = surveyAreaProto,
            offline_tile_packages = offlineTilePackages.map(::offlineTilePackageToProto),
          ),
        default_language = editorDraft?.details?.defaultLanguage ?: "en",
        supported_languages = editorDraft?.details?.supportedLanguages ?: listOf("en"),
        form_launch_configs = launchConfigs,
        purpose_ids = purposeIds,
        program_ids = programIds,
        form_concept_links = conceptLinks,
        outcome = survey.outcome?.let(::surveyOutcomeToProto),
        commit_timestamp = commitTimestamp,
        closed_at = survey.closedAt?.let(::parseIsoToInstant),
        closed_at_iso = survey.closedAt.orEmpty(),
        saved_revision_number = 1L,
        location_label = survey.location,
        coordinates_label = survey.coordinatesLabel,
        offline_size_label = survey.offlineSizeLabel,
        is_downloaded = survey.isDownloaded,
        thumbnail_theme = survey.thumbnailTheme.name,
        entity_count = survey.entityCount,
        other_purpose = editorDraft?.details?.otherPurpose.orEmpty(),
        primary_form_id = config?.primaryForm?.proto?.form_id.orEmpty(),
      )

    val sharing = editorDraft?.sharing
    val acl =
      SurveyAcl(
        survey_id = survey.id,
        entries = sharing?.collaborators?.map(::collaboratorToProto).orEmpty(),
        sharing_policy = sharingPolicyToProto(sharing?.policy ?: survey.sharingPolicy),
        peer_data_visibility =
          peerDataVisibilityToProto(sharing?.peerDataVisibility ?: survey.peerDataVisibility),
        owner_email = sharing?.ownerEmail ?: survey.ownerEmail,
        owner_profile = sharing?.ownerProfile?.let(::cachedProfileToProto),
        collector_consent_required =
          sharing?.collectorConsentRequired ?: survey.collectorConsentRequired,
        collector_consent_text = sharing?.collectorConsentText ?: survey.collectorConsentText,
        commit_timestamp = commitTimestamp,
      )

    return surveyDef to acl
  }

  /**
   * Builds the catalog overview of [surveyDef]. Its `commit_timestamp` is the later of the survey's
   * and [acl]'s, so access changes also reach `ListSurveyOverviews` delta polls, and its
   * `record_version` is the higher of their record versions.
   */
  fun surveyOverviewFromDef(
    surveyDef: SurveyDef,
    acl: SurveyAcl? = null,
    hasWorkingCopy: Boolean = false,
  ): SurveyOverview =
    SurveyOverview(
      survey_id = surveyDef.survey_id,
      title = surveyDef.title,
      description = surveyDef.description,
      organization_id = surveyDef.organization_id,
      owner_id = surveyDef.owner_id,
      state = surveyDef.state,
      version = surveyDef.version,
      saved_revision_number = surveyDef.saved_revision_number,
      location_label = surveyDef.location_label,
      coordinates_label = surveyDef.coordinates_label,
      offline_size_label = surveyDef.offline_size_label,
      is_downloaded = surveyDef.is_downloaded,
      thumbnail_theme = surveyDef.thumbnail_theme,
      entity_count = surveyDef.entity_count,
      survey_area = surveyDef.map_config?.survey_area,
      outcome = surveyDef.outcome,
      closed_at = surveyDef.closed_at,
      closed_at_iso = surveyDef.closed_at_iso,
      commit_timestamp = latestOf(surveyDef.commit_timestamp, acl?.commit_timestamp),
      record_version = maxOf(surveyDef.record_version, acl?.record_version ?: 0L),
      has_working_copy = hasWorkingCopy,
      sharing_policy = acl?.sharing_policy ?: SharingPolicyProto.SHARING_POLICY_UNSPECIFIED,
      peer_data_visibility =
        acl?.peer_data_visibility ?: PeerDataVisibilityProto.PEER_DATA_VISIBILITY_UNSPECIFIED,
      collector_consent_required = acl?.collector_consent_required ?: false,
      collector_consent_text = acl?.collector_consent_text.orEmpty(),
    )

  private fun latestOf(a: Instant?, b: Instant?): Instant? =
    when {
      a == null -> b
      b == null -> a
      a.getEpochSecond() != b.getEpochSecond() ->
        if (a.getEpochSecond() > b.getEpochSecond()) a else b
      else -> if (a.getNano() >= b.getNano()) a else b
    }

  fun surveyPreviewFromOverviewProto(
    overview: SurveyOverview,
    mapLayers: List<MapLayerItem> = emptyList(),
    entities: List<GeospatialEntityItem> = emptyList(),
  ): SurveyPreviewItem {
    val surveyArea =
      if (overview.survey_id == "survey-kenya-coffee") {
        overview.survey_area?.let(::surveyAreaFromProto)
      } else {
        null
      }
    val theme =
      MapThumbnailTheme.entries.firstOrNull { it.name == overview.thumbnail_theme }
        ?: MapThumbnailTheme.SAVANNA
    return SurveyPreviewItem(
      id = overview.survey_id,
      title = overview.title,
      description = overview.description,
      location = overview.location_label,
      coordinatesLabel = overview.coordinates_label,
      offlineSizeLabel = overview.offline_size_label,
      isDownloaded = overview.is_downloaded,
      thumbnailTheme = theme,
      entityCount = overview.entity_count,
      thumbnail =
        SurveyThumbnailGenerator.fromRuntime(
          surveyId = overview.survey_id,
          surveyArea = surveyArea,
          mapLayers = mapLayers,
          entities = entities,
        ),
      ownerEmail = overview.owner_id,
      organizationId = overview.organization_id.takeIf { it.isNotBlank() },
      state = surveyStateFromProto(overview.state),
      closedAt =
        overview.closed_at_iso.takeIf { it.isNotBlank() }
          ?: overview.closed_at?.let { isoUtc(it.epochMillis()) },
      outcome = overview.outcome?.let(::surveyOutcomeFromProto),
      sharingPolicy = sharingPolicyFromProto(overview.sharing_policy),
      peerDataVisibility = peerDataVisibilityFromProto(overview.peer_data_visibility),
      collectorConsentRequired = overview.collector_consent_required,
      collectorConsentText = overview.collector_consent_text,
    )
  }

  fun surveyPreviewFromProto(
    surveyDef: SurveyDef,
    acl: SurveyAcl?,
    mapLayers: List<MapLayerItem> = emptyList(),
    entities: List<GeospatialEntityItem> = emptyList(),
  ): SurveyPreviewItem =
    surveyPreviewFromOverviewProto(
      overview =
        surveyOverviewFromDef(surveyDef, acl).let { o ->
          o.copy(owner_id = o.owner_id.ifBlank { acl?.owner_email.orEmpty() })
        },
      mapLayers = mapLayers,
      entities = entities,
    )

  fun formsFromProto(surveyDef: SurveyDef): List<FormPreviewItem> =
    surveyDef.form_launch_configs.map { lc ->
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
        availability = formAvailabilityFromProto(lc.availability),
      )
    }

  fun mapLayersFromProto(surveyDef: SurveyDef): List<MapLayerItem> =
    surveyDef.map_config?.layers?.map(::mapLayerFromProto).orEmpty()

  fun offlineTilePackagesFromProto(surveyDef: SurveyDef): List<OfflineTilePackageItem> =
    surveyDef.map_config?.offline_tile_packages?.map(::offlineTilePackageFromProto).orEmpty()

  fun surveyConfigFromProto(surveyDef: SurveyDef): SurveyConfig {
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
    )
  }

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
      )
    val surveyArea = surveyDef.map_config?.survey_area?.let(::surveyAreaFromProto)
    val tabularDatasets =
      surveyDef.entity_datasets
        .filter { it.type == EntityType.TABULAR }
        .map(::entityDatasetFromProto)
    val conceptLinksByDatasetProperty =
      surveyDef.entity_datasets
        .filter { it.type == EntityType.GEOSPATIAL }
        .associate { ds ->
          ds.id to
            ds.properties
              .mapNotNull { p ->
                p.concept_ref
                  ?.takeIf { it.concept_id.isNotBlank() }
                  ?.let { p.name to ConceptLink(it.concept_id, it.version.coerceAtLeast(1)) }
              }
              .toMap()
        }
    val mergedMapDatasets =
      base.datasets.map { ds ->
        val propLinks = conceptLinksByDatasetProperty[ds.id]
        if (propLinks.isNullOrEmpty()) {
          ds
        } else {
          ds.copy(
            properties =
              ds.properties.map { prop ->
                val link = propLinks[prop.name]
                if (link != null) prop.copy(conceptLink = link) else prop
              }
          )
        }
      }
    val sharing =
      if (acl != null) {
        SharingSettings(
          ownerEmail = acl.owner_email.ifBlank { surveyPreview.ownerEmail },
          ownerProfile = acl.owner_profile?.let(::cachedProfileFromProto),
          policy = sharingPolicyFromProto(acl.sharing_policy),
          peerDataVisibility = peerDataVisibilityFromProto(acl.peer_data_visibility),
          collaborators = acl.entries.map(::collaboratorFromProto),
          collectorConsentRequired = acl.collector_consent_required,
          collectorConsentText = acl.collector_consent_text,
        )
      } else {
        base.sharing
      }

    return base.copy(
      details =
        base.details.copy(
          defaultLanguage = surveyDef.default_language.ifBlank { "en" },
          supportedLanguages = surveyDef.supported_languages.ifEmpty { listOf("en") },
          surveyArea = surveyArea ?: base.details.surveyArea,
          organizationId = surveyDef.organization_id.takeIf { it.isNotBlank() },
          purposeIds = surveyDef.purpose_ids,
          otherPurpose = surveyDef.other_purpose,
          programIds = surveyDef.program_ids,
        ),
      datasets = mergedMapDatasets + tabularDatasets,
      sharing = sharing,
    )
  }

  // ---------------------------------------------------------------------------
  // Entities & Submissions ↔ EntityRecord & SubmissionRecord
  // ---------------------------------------------------------------------------

  fun entityToProto(
    surveyId: String,
    entity: GeospatialEntityItem,
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): EntityRecord =
    EntityRecord(
      entity_id = entity.id,
      dataset_id = entity.datasetId,
      revision = 1L,
      label = entity.label,
      properties = entity.properties.mapValues { (_, v) -> TypedValue(string_value = v) },
      is_deleted = isDeleted,
      survey_id = surveyId,
      commit_timestamp = commitTimestamp,
      dataset_name = entity.datasetName,
      layer_id = entity.layerId,
      geo_id = entity.geoId,
      geometry_type_label = entity.geometryTypeLabel,
      area_hectares = entity.areaHectares,
      perimeter_meters = entity.perimeterMeters,
      coordinates_label = entity.coordinatesLabel,
      normalized_x = entity.normalizedX,
      normalized_y = entity.normalizedY,
      color_hex = entity.colorHex,
      singular_type_label = entity.singularTypeLabel,
      sync_status_key = entity.syncStatus.name,
    )

  fun entityFromProto(
    proto: EntityRecord,
    submissions: List<SubmissionPreviewItem>,
  ): GeospatialEntityItem {
    val fallbackSyncStatus =
      SyncStatus.entries.firstOrNull { it.name == proto.sync_status_key } ?: SyncStatus.SYNCED
    return GeospatialEntityItem(
      id = proto.entity_id,
      label = proto.label,
      datasetId = proto.dataset_id,
      datasetName = proto.dataset_name,
      layerId = proto.layer_id,
      geoId = proto.geo_id,
      geometryTypeLabel = proto.geometry_type_label,
      areaHectares = proto.area_hectares,
      perimeterMeters = proto.perimeter_meters,
      coordinatesLabel = proto.coordinates_label,
      normalizedX = proto.normalized_x,
      normalizedY = proto.normalized_y,
      colorHex = proto.color_hex,
      properties = proto.properties.mapValues { (_, v) -> v.string_value.orEmpty() },
      submissions = submissions,
      singularTypeLabel = proto.singular_type_label.ifBlank { "Location" },
      syncStatus =
        if (submissions.isNotEmpty()) {
          org.groundplatform.v2.app.domain.model.deriveEntitySyncStatus(
            submissions,
            fallbackSyncStatus,
          )
        } else {
          fallbackSyncStatus
        },
    )
  }

  fun submissionToProto(
    surveyId: String,
    submission: SubmissionPreviewItem,
    geometryPolygons: List<SubmissionGeometryPolygon> = emptyList(),
    isDeleted: Boolean = false,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): SubmissionRecord =
    SubmissionRecord(
      submission_id = submission.id,
      survey_id = surveyId,
      form_id = submission.formId,
      form_version = submission.formVersion,
      entity_id = submission.entityId,
      revision = 1L,
      is_deleted = isDeleted,
      commit_timestamp = commitTimestamp,
      entity_label = submission.entityLabel,
      form_title = submission.formTitle,
      collector_name = submission.collectorName,
      collector_email = submission.collectorEmail,
      timestamp_label = submission.timestamp,
      target_type_label = submission.targetTypeLabel,
      sync_status_key = submission.syncStatus.name,
      coordinates_label = submission.coordinatesLabel,
      normalized_x = submission.normalizedX,
      normalized_y = submission.normalizedY,
      field_entries =
        submission.fields.map { f ->
          SubmissionFieldEntryDef(
            question_name = f.questionName,
            question_label = f.questionLabel,
            answer_value = f.answerValue,
          )
        },
      geometry_polygons = geometryPolygons.map(::geometryPolygonToProto),
    )

  fun submissionFromProto(proto: SubmissionRecord): SubmissionPreviewItem =
    SubmissionPreviewItem(
      id = proto.submission_id,
      entityId = proto.entity_id,
      entityLabel = proto.entity_label,
      formId = proto.form_id,
      formTitle = proto.form_title,
      formVersion = proto.form_version,
      collectorName = proto.collector_name,
      collectorEmail = proto.collector_email,
      timestamp = proto.timestamp_label,
      fields =
        proto.field_entries.map { f ->
          SubmissionFieldEntry(
            questionName = f.question_name,
            questionLabel = f.question_label,
            answerValue = f.answer_value,
          )
        },
      targetTypeLabel = proto.target_type_label.ifBlank { "Location" },
      syncStatus =
        SyncStatus.entries.firstOrNull { it.name == proto.sync_status_key } ?: SyncStatus.SYNCED,
      coordinatesLabel = proto.coordinates_label,
      normalizedX = proto.normalized_x,
      normalizedY = proto.normalized_y,
    )

  fun geometryPolygonToProto(poly: SubmissionGeometryPolygon): SubmissionGeometryPolygonDef =
    SubmissionGeometryPolygonDef(
      id = poly.id,
      submission_id = poly.submissionId,
      entity_id = poly.entityId,
      layer_id = poly.layerId,
      form_id = poly.formId,
      form_title = poly.formTitle,
      field_path = poly.fieldPath,
      question_label = poly.questionLabel,
      short_map_badge = poly.shortMapBadge,
      collector_name = poly.collectorName,
      timestamp_label = poly.timestamp,
      area_hectares = poly.areaHectares,
      vertex_count = poly.vertexCount,
      normalized_x = poly.normalizedX,
      normalized_y = poly.normalizedY,
      width_fraction = poly.widthFraction,
      height_fraction = poly.heightFraction,
      color_hex = poly.colorHex,
    )

  fun geometryPolygonFromProto(proto: SubmissionGeometryPolygonDef): SubmissionGeometryPolygon =
    SubmissionGeometryPolygon(
      id = proto.id,
      submissionId = proto.submission_id,
      entityId = proto.entity_id,
      layerId = proto.layer_id,
      formId = proto.form_id,
      formTitle = proto.form_title,
      fieldPath = proto.field_path,
      questionLabel = proto.question_label,
      shortMapBadge = proto.short_map_badge,
      collectorName = proto.collector_name,
      timestamp = proto.timestamp_label,
      areaHectares = proto.area_hectares,
      vertexCount = proto.vertex_count,
      normalizedX = proto.normalized_x,
      normalizedY = proto.normalized_y,
      widthFraction = proto.width_fraction,
      heightFraction = proto.height_fraction,
      colorHex = proto.color_hex,
    )

  // ---------------------------------------------------------------------------
  // Places & Mutation Logs & Impact Events
  // ---------------------------------------------------------------------------

  fun placeToProto(place: SurveyPlaceItem): PlaceRecord =
    PlaceRecord(
      id = place.id,
      name = place.name,
      category_label = place.categoryLabel,
      region_subtitle = place.regionSubtitle,
      coordinates_label = place.coordinatesLabel,
      normalized_x = place.normalizedX,
      normalized_y = place.normalizedY,
      zoom_delta = place.zoomDelta,
      longitude = place.longitude,
      latitude = place.latitude,
      bbox_min_lng = place.bboxMinLng,
      bbox_min_lat = place.bboxMinLat,
      bbox_max_lng = place.bboxMaxLng,
      bbox_max_lat = place.bboxMaxLat,
      target_zoom = place.targetZoom,
      mapbox_place_id = place.mapboxPlaceId,
      source_label = place.sourceLabel,
    )

  fun placeFromProto(proto: PlaceRecord): SurveyPlaceItem =
    SurveyPlaceItem(
      id = proto.id,
      name = proto.name,
      categoryLabel = proto.category_label,
      regionSubtitle = proto.region_subtitle,
      coordinatesLabel = proto.coordinates_label,
      normalizedX = proto.normalized_x,
      normalizedY = proto.normalized_y,
      zoomDelta = proto.zoom_delta,
      longitude = proto.longitude,
      latitude = proto.latitude,
      bboxMinLng = proto.bbox_min_lng,
      bboxMinLat = proto.bbox_min_lat,
      bboxMaxLng = proto.bbox_max_lng,
      bboxMaxLat = proto.bbox_max_lat,
      targetZoom = proto.target_zoom,
      mapboxPlaceId = proto.mapbox_place_id,
      sourceLabel = proto.source_label,
    )

  fun mutationLogToProto(
    item: MutationLogItem,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): MutationLogRecord =
    MutationLogRecord(
      id = item.id,
      survey_id = item.surveyId,
      operation_kind_key = item.operationKind.name,
      title = item.title,
      target_label = item.targetLabel,
      entity_id = item.entityId,
      submission_id = item.submissionId,
      actor_name = item.actorName,
      state_key = item.state.name,
      state_detail = item.stateDetail,
      operation_timestamp = item.operationTimestamp,
      started_timestamp = item.startedTimestamp,
      completed_timestamp = item.completedTimestamp,
      payload_summary = item.payloadSummary,
      commit_timestamp = commitTimestamp,
    )

  fun mutationLogFromProto(proto: MutationLogRecord): MutationLogItem =
    MutationLogItem(
      id = proto.id,
      surveyId = proto.survey_id,
      operationKind =
        MutationOperationKind.entries.firstOrNull { it.name == proto.operation_kind_key }
          ?: MutationOperationKind.CREATE_SUBMISSION,
      title = proto.title,
      targetLabel = proto.target_label,
      entityId = proto.entity_id,
      submissionId = proto.submission_id,
      actorName = proto.actor_name,
      state =
        MutationSyncState.entries.firstOrNull { it.name == proto.state_key }
          ?: MutationSyncState.QUEUED,
      stateDetail = proto.state_detail,
      operationTimestamp = proto.operation_timestamp,
      startedTimestamp = proto.started_timestamp,
      completedTimestamp = proto.completed_timestamp,
      payloadSummary = proto.payload_summary,
    )

  fun impactEventToProto(
    event: ImpactEvent,
    commitTimestamp: Instant = ofEpochSecond(1_770_000_000L, 0L),
  ): ImpactEventProto =
    ImpactEventProto(
      event_id = event.id,
      type =
        when (event.type) {
          ImpactEventType.EXPORT -> ImpactEventTypeProto.EXPORT
          ImpactEventType.PARTNER_PUSH -> ImpactEventTypeProto.PARTNER_PUSH
          ImpactEventType.RECEIPT_GENERATED -> ImpactEventTypeProto.RECEIPT_GENERATED
          ImpactEventType.RECEIPT_SHARED -> ImpactEventTypeProto.RECEIPT_SHARED
          ImpactEventType.DOI_MINTED -> ImpactEventTypeProto.DOI_MINTED
          ImpactEventType.SURVEY_CLOSED -> ImpactEventTypeProto.SURVEY_CLOSED
        },
      survey_id = event.surveyId,
      organization_id = event.organizationId.orEmpty(),
      purpose_ids = event.purposeIds,
      export_profile_id = event.exportProfileId,
      feature_count = event.featureCount.toLong(),
      area_ha = event.areaHa,
      occurred_at = parseIsoToInstant(event.occurredAt),
      occurred_at_iso = event.occurredAt,
      actor_user_id = event.actorUserId,
      commit_timestamp = commitTimestamp,
    )

  fun impactEventFromProto(proto: ImpactEventProto): ImpactEvent =
    ImpactEvent(
      id = proto.event_id,
      type =
        when (proto.type) {
          ImpactEventTypeProto.PARTNER_PUSH -> ImpactEventType.PARTNER_PUSH
          ImpactEventTypeProto.RECEIPT_GENERATED -> ImpactEventType.RECEIPT_GENERATED
          ImpactEventTypeProto.RECEIPT_SHARED -> ImpactEventType.RECEIPT_SHARED
          ImpactEventTypeProto.DOI_MINTED -> ImpactEventType.DOI_MINTED
          ImpactEventTypeProto.SURVEY_CLOSED -> ImpactEventType.SURVEY_CLOSED
          else -> ImpactEventType.EXPORT
        },
      surveyId = proto.survey_id,
      organizationId = proto.organization_id.takeIf { it.isNotBlank() },
      purposeIds = proto.purpose_ids,
      exportProfileId = proto.export_profile_id,
      featureCount = proto.feature_count.toInt(),
      areaHa = proto.area_ha,
      occurredAt =
        proto.occurred_at_iso.takeIf { it.isNotBlank() }
          ?: proto.occurred_at?.let { isoUtc(it.epochMillis()) }.orEmpty(),
      actorUserId = proto.actor_user_id,
      isUploaded = true,
    )

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  private fun mapLayerToProto(layer: MapLayerItem): LayerDef =
    LayerDef(
      id = layer.id,
      label = layer.label,
      visible_by_default = layer.isVisible,
      default_style =
        GeometryStyle(
          color = formatHexColorCss(layer.colorHex),
          icon = layer.iconName.orEmpty(),
        ),
      entity_dataset_id = layer.datasetId,
      form_geometry =
        if (layer.formId != null && layer.fieldPath != null) {
          FormGeometrySource(form_id = layer.formId, field_path = layer.fieldPath)
        } else {
          null
        },
      source_description = layer.sourceDescription,
      geometry_type_label = layer.geometryTypeLabel,
      singular_item_label = layer.singularItemLabel,
      plural_item_label = layer.pluralItemLabel,
      form_title = layer.formTitle.orEmpty(),
    )

  private fun mapLayerFromProto(proto: LayerDef): MapLayerItem =
    MapLayerItem(
      id = proto.id,
      label = proto.label,
      sourceDescription = proto.source_description,
      colorHex = parseHexColorOrDefault(proto.default_style?.color, 0xFF2E7D32L),
      geometryTypeLabel = proto.geometry_type_label,
      isVisible = proto.visible_by_default,
      sourceType = LayerSourceType.ENTITY_DATASET,
      formId = proto.form_geometry?.form_id?.takeIf { it.isNotBlank() },
      formTitle = proto.form_title.takeIf { it.isNotBlank() },
      fieldPath = proto.form_geometry?.field_path?.takeIf { it.isNotBlank() },
      singularItemLabel = proto.singular_item_label.ifBlank { "location" },
      pluralItemLabel = proto.plural_item_label.ifBlank { "locations" },
      datasetId = proto.entity_dataset_id?.takeIf { it.isNotBlank() },
      iconName = proto.default_style?.icon?.takeIf { it.isNotBlank() },
    )

  private fun offlineTilePackageToProto(pkg: OfflineTilePackageItem): OfflineTilePackageDef =
    OfflineTilePackageDef(
      id = pkg.id,
      region_name = pkg.regionName,
      tile_type_label = pkg.tileTypeLabel,
      zoom_range_label = pkg.zoomRangeLabel,
      size_label = pkg.sizeLabel,
      is_downloaded = pkg.isDownloaded,
    )

  private fun offlineTilePackageFromProto(proto: OfflineTilePackageDef): OfflineTilePackageItem =
    OfflineTilePackageItem(
      id = proto.id,
      regionName = proto.region_name,
      tileTypeLabel = proto.tile_type_label,
      zoomRangeLabel = proto.zoom_range_label,
      sizeLabel = proto.size_label,
      isDownloaded = proto.is_downloaded,
    )

  private fun surveyAreaToProto(area: SurveyArea): SurveyAreaProto =
    SurveyAreaProto(
      name = area.name,
      parts =
        area.parts.map { ring ->
          GeoShape(points = ring.map { pt -> GeoPoint(latitude = pt.lat, longitude = pt.lng) })
        },
      source = area.sourceLabel,
      center = GeoPoint(latitude = area.center.lat, longitude = area.center.lng),
      zoom = area.zoom,
    )

  private fun surveyAreaFromProto(proto: SurveyAreaProto): SurveyArea {
    val parts =
      proto.parts.map { shape ->
        shape.points.map { pt -> LatLng(pt.latitude, pt.longitude) }
      }
    val center = proto.center?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(0.0, 0.0)
    return SurveyArea(
      name = proto.name,
      parts = parts,
      center = center,
      zoom = if (proto.zoom > 0.0) proto.zoom else 12.0,
      sourceLabel = proto.source.ifBlank { "Selected boundary" },
    )
  }

  private fun entityDatasetToProto(ds: EntityDataset): EntityDatasetDef =
    EntityDatasetDef(
      id = ds.id,
      display_name = ds.displayName,
      description = ds.description,
      type =
        when (ds.kind) {
          DatasetKind.DATA_TABLE -> EntityType.TABULAR
          DatasetKind.MAP_LAYER -> EntityType.GEOSPATIAL
        },
      key_property = ds.keyProperty,
      label_property = ds.labelProperty,
      field_creation_enabled = ds.fieldCreationEnabled,
      properties =
        ds.properties.map { p ->
          EntityPropertyDefinition(
            name = p.name,
            label = p.label,
            required = p.required,
            type =
              when (p.type) {
                PropertyType.TEXT -> DataType.TYPE_STRING
                PropertyType.INTEGER -> DataType.TYPE_INT32
                PropertyType.DECIMAL -> DataType.TYPE_DOUBLE
                PropertyType.BOOLEAN -> DataType.TYPE_BOOLEAN
                PropertyType.DATE -> DataType.TYPE_DATE
              },
            concept_ref =
              p.conceptLink?.let { link ->
                ConceptRef(concept_id = link.conceptId, version = link.version)
              },
          )
        },
      geometry_type =
        when (ds.geometryKind) {
          GeometryKind.POINT -> DataType.TYPE_GEOPOINT
          GeometryKind.LINE -> DataType.TYPE_GEOTRACE
          GeometryKind.POLYGON -> DataType.TYPE_GEOSHAPE
        },
      editor_key = ds.key,
      linked_form_key = ds.linkedFormKey.orEmpty(),
      default_style =
        GeometryStyle(
          color = ds.style.colorHex,
          icon = ds.style.iconName.orEmpty(),
          stroke_width = ds.style.strokeWidth,
          fill_opacity = ds.style.fillOpacity,
        ),
      visible_by_default = ds.style.visibleByDefault,
      rows =
        ds.rows.map { r ->
          DataTableRow(
            row_key = r.key,
            values = r.values,
            geometry = r.geometry.map { GeoPoint(latitude = it.lat, longitude = it.lng) },
          )
        },
    )

  private fun entityDatasetFromProto(proto: EntityDatasetDef): EntityDataset =
    EntityDataset(
      key = proto.editor_key.ifBlank { proto.id },
      kind =
        when (proto.type) {
          EntityType.TABULAR -> DatasetKind.DATA_TABLE
          else -> DatasetKind.MAP_LAYER
        },
      id = proto.id,
      displayName = proto.display_name,
      description = proto.description,
      geometryKind =
        when (proto.geometry_type) {
          DataType.TYPE_GEOTRACE -> GeometryKind.LINE
          DataType.TYPE_GEOSHAPE -> GeometryKind.POLYGON
          else -> GeometryKind.POINT
        },
      keyProperty = proto.key_property,
      labelProperty = proto.label_property,
      fieldCreationEnabled = proto.field_creation_enabled,
      linkedFormKey = proto.linked_form_key.takeIf { it.isNotBlank() },
      properties =
        proto.properties.map { p ->
          EntityProperty(
            name = p.name,
            label = p.label,
            type =
              when (p.type) {
                DataType.TYPE_INT32,
                DataType.TYPE_INT64 -> PropertyType.INTEGER
                DataType.TYPE_DOUBLE -> PropertyType.DECIMAL
                DataType.TYPE_BOOLEAN -> PropertyType.BOOLEAN
                DataType.TYPE_DATE -> PropertyType.DATE
                else -> PropertyType.TEXT
              },
            required = p.required,
            conceptLink =
              p.concept_ref
                ?.takeIf { it.concept_id.isNotBlank() }
                ?.let { ConceptLink(it.concept_id, it.version) },
          )
        },
      rows =
        proto.rows.map { r ->
          EntityRow(
            key = r.row_key,
            values = r.values,
            geometry = r.geometry.map { LatLng(it.latitude, it.longitude) },
          )
        },
      style =
        proto.default_style?.let { s ->
          LayerStyle(
            colorHex = s.color.ifBlank { "#2E7D32" },
            strokeWidth = if (s.stroke_width > 0.0) s.stroke_width else 2.0,
            fillOpacity = if (s.fill_opacity > 0.0) s.fill_opacity else 0.3,
            visibleByDefault = proto.visible_by_default,
            iconName = s.icon.takeIf { it.isNotBlank() },
          )
        } ?: LayerStyle(),
    )

  private fun collaboratorToProto(c: Collaborator): AclEntry =
    AclEntry(
      user_id = c.userId.orEmpty(),
      email = c.email,
      role =
        when (c.role) {
          CollaboratorRole.VIEWER -> Role.VIEWER
          CollaboratorRole.DATA_COLLECTOR -> Role.DATA_COLLECTOR
          CollaboratorRole.SURVEY_ORGANIZER -> Role.SURVEY_ORGANIZER
        },
      invitation_status =
        when (c.status) {
          InvitationStatus.PENDING -> AclEntry.InvitationStatus.PENDING
          InvitationStatus.ACCEPTED -> AclEntry.InvitationStatus.ACCEPTED
        },
      invite_token = c.inviteToken.orEmpty(),
      profile = c.profile?.let(::cachedProfileToProto),
    )

  private fun collaboratorFromProto(proto: AclEntry): Collaborator =
    Collaborator(
      email = proto.email,
      role =
        when (proto.role) {
          Role.VIEWER -> CollaboratorRole.VIEWER
          Role.SURVEY_ORGANIZER -> CollaboratorRole.SURVEY_ORGANIZER
          else -> CollaboratorRole.DATA_COLLECTOR
        },
      status =
        when (proto.invitation_status) {
          AclEntry.InvitationStatus.ACCEPTED -> InvitationStatus.ACCEPTED
          else -> InvitationStatus.PENDING
        },
      inviteToken = proto.invite_token.takeIf { it.isNotBlank() },
      userId = proto.user_id.takeIf { it.isNotBlank() },
      profile = proto.profile?.let(::cachedProfileFromProto),
    )

  private fun sharingPolicyToProto(policy: SharingPolicy): SharingPolicyProto =
    when (policy) {
      SharingPolicy.RESTRICTED -> SharingPolicyProto.RESTRICTED
      SharingPolicy.ORGANIZATION -> SharingPolicyProto.ORGANIZATION
      SharingPolicy.ANYONE_WITH_LINK -> SharingPolicyProto.ANYONE_WITH_LINK
      SharingPolicy.PUBLIC -> SharingPolicyProto.PUBLIC
    }

  private fun sharingPolicyFromProto(proto: SharingPolicyProto): SharingPolicy =
    when (proto) {
      SharingPolicyProto.ORGANIZATION -> SharingPolicy.ORGANIZATION
      SharingPolicyProto.ANYONE_WITH_LINK -> SharingPolicy.ANYONE_WITH_LINK
      SharingPolicyProto.PUBLIC -> SharingPolicy.PUBLIC
      else -> SharingPolicy.RESTRICTED
    }

  private fun peerDataVisibilityToProto(v: PeerDataVisibility): PeerDataVisibilityProto =
    when (v) {
      PeerDataVisibility.ALL -> PeerDataVisibilityProto.ALL
      PeerDataVisibility.OWN_ONLY -> PeerDataVisibilityProto.OWN_ONLY
    }

  private fun peerDataVisibilityFromProto(proto: PeerDataVisibilityProto): PeerDataVisibility =
    when (proto) {
      PeerDataVisibilityProto.OWN_ONLY -> PeerDataVisibility.OWN_ONLY
      else -> PeerDataVisibility.ALL
    }

  private fun surveyStateToProto(state: SurveyLifecycleState): SurveyState =
    when (state) {
      SurveyLifecycleState.DRAFT -> SurveyState.DRAFT
      SurveyLifecycleState.PUBLISHED -> SurveyState.PUBLISHED
      SurveyLifecycleState.CLOSED -> SurveyState.CLOSED
      SurveyLifecycleState.ARCHIVED -> SurveyState.ARCHIVED
    }

  private fun surveyStateFromProto(proto: SurveyState): SurveyLifecycleState =
    when (proto) {
      SurveyState.DRAFT -> SurveyLifecycleState.DRAFT
      SurveyState.CLOSED -> SurveyLifecycleState.CLOSED
      SurveyState.ARCHIVED -> SurveyLifecycleState.ARCHIVED
      else -> SurveyLifecycleState.PUBLISHED
    }

  /** Encodes [availability]; `null` (unset) means available on all platforms. */
  private fun formAvailabilityToProto(availability: FormAvailability): FormAvailabilityProto? =
    when (availability) {
      FormAvailability.BOTH -> null
      FormAvailability.MOBILE -> FormAvailabilityProto(web = false, mobile = true)
      FormAvailability.WEB -> FormAvailabilityProto(web = true, mobile = false)
      FormAvailability.NONE -> FormAvailabilityProto(web = false, mobile = false)
    }

  private fun formAvailabilityFromProto(proto: FormAvailabilityProto?): FormAvailability =
    when {
      proto == null -> FormAvailability.BOTH
      proto.web && proto.mobile -> FormAvailability.BOTH
      proto.mobile -> FormAvailability.MOBILE
      proto.web -> FormAvailability.WEB
      else -> FormAvailability.NONE
    }

  private fun surveyOutcomeToProto(outcome: SurveyOutcome): SurveyOutcomeProto =
    SurveyOutcomeProto(
      outcomes =
        outcome.outcomes.map { kind ->
          when (kind) {
            SurveyOutcomeKind.SUBMITTED_EUDR_DDS -> SurveyOutcomeProto.Outcome.SUBMITTED_EUDR_DDS
            SurveyOutcomeKind.REPORTED_FERM -> SurveyOutcomeProto.Outcome.REPORTED_FERM
            SurveyOutcomeKind.LAND_TITLING -> SurveyOutcomeProto.Outcome.LAND_TITLING
            SurveyOutcomeKind.SHARED_WITH_BUYERS -> SurveyOutcomeProto.Outcome.SHARED_WITH_BUYERS
            SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL ->
              SurveyOutcomeProto.Outcome.TRAINED_OR_VALIDATED_MODEL
            SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT ->
              SurveyOutcomeProto.Outcome.PROTECTED_AREA_MANAGEMENT
            SurveyOutcomeKind.NOT_YET -> SurveyOutcomeProto.Outcome.NOT_YET
          }
        },
      effort_comparison =
        when (outcome.effortComparison) {
          EffortComparison.LESS -> SurveyOutcomeProto.EffortComparison.LESS
          EffortComparison.SAME -> SurveyOutcomeProto.EffortComparison.SAME
          EffortComparison.MORE -> SurveyOutcomeProto.EffortComparison.MORE
          null -> SurveyOutcomeProto.EffortComparison.EFFORT_COMPARISON_UNSPECIFIED
        },
      answered_at = parseIsoToInstant(outcome.answeredAt),
      answered_at_iso = outcome.answeredAt,
      answered_by = outcome.answeredBy,
    )

  private fun surveyOutcomeFromProto(proto: SurveyOutcomeProto): SurveyOutcome =
    SurveyOutcome(
      outcomes =
        proto.outcomes
          .mapNotNull { o ->
            when (o) {
              SurveyOutcomeProto.Outcome.SUBMITTED_EUDR_DDS -> SurveyOutcomeKind.SUBMITTED_EUDR_DDS
              SurveyOutcomeProto.Outcome.REPORTED_FERM -> SurveyOutcomeKind.REPORTED_FERM
              SurveyOutcomeProto.Outcome.LAND_TITLING -> SurveyOutcomeKind.LAND_TITLING
              SurveyOutcomeProto.Outcome.SHARED_WITH_BUYERS -> SurveyOutcomeKind.SHARED_WITH_BUYERS
              SurveyOutcomeProto.Outcome.TRAINED_OR_VALIDATED_MODEL ->
                SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL
              SurveyOutcomeProto.Outcome.PROTECTED_AREA_MANAGEMENT ->
                SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT
              SurveyOutcomeProto.Outcome.NOT_YET -> SurveyOutcomeKind.NOT_YET
              SurveyOutcomeProto.Outcome.OUTCOME_UNSPECIFIED -> null
            }
          }
          .toSet(),
      effortComparison =
        when (proto.effort_comparison) {
          SurveyOutcomeProto.EffortComparison.LESS -> EffortComparison.LESS
          SurveyOutcomeProto.EffortComparison.SAME -> EffortComparison.SAME
          SurveyOutcomeProto.EffortComparison.MORE -> EffortComparison.MORE
          SurveyOutcomeProto.EffortComparison.EFFORT_COMPARISON_UNSPECIFIED -> null
        },
      answeredAt =
        proto.answered_at_iso.takeIf { it.isNotBlank() }
          ?: proto.answered_at?.let { isoUtc(it.epochMillis()) }.orEmpty(),
      answeredBy = proto.answered_by,
    )

  private fun parseIsoToInstant(iso: String): Instant? {
    if (iso.isBlank()) return null
    val normalized = if (iso.length == 10) "${iso}T00:00:00Z" else iso
    val millis = epochMillisOfIsoUtc(normalized) ?: return null
    return ofEpochSecond(millis / 1000L, (millis % 1000L) * 1_000_000L)
  }

  private fun formatInstantDateOrIso(instant: Instant?): String {
    if (instant == null) return ""
    val iso = isoUtc(instant.epochMillis())
    return if (iso.endsWith("T00:00:00Z")) iso.substring(0, 10) else iso
  }
}

/** Milliseconds since the epoch; Wire's multiplatform [Instant] has no `toEpochMilli()`. */
private fun Instant.epochMillis(): Long = getEpochSecond() * 1000L + getNano() / 1_000_000
