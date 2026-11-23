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
package org.openforis.collectone.v2.app.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_basemap_type_normal
import org.openforis.collectone.v2.app.resources.domain_basemap_type_normal_description
import org.openforis.collectone.v2.app.resources.domain_basemap_type_satellite
import org.openforis.collectone.v2.app.resources.domain_basemap_type_satellite_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_count_by_code
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_count_by_code_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_count_distinct_features
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_count_distinct_features_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_mean
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_mean_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_none
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_none_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_share_by_code
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_share_by_code_description
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_sum
import org.openforis.collectone.v2.app.resources.domain_concept_aggregation_sum_description
import org.openforis.collectone.v2.app.resources.domain_concept_data_type_media
import org.openforis.collectone.v2.app.resources.domain_data_type_date
import org.openforis.collectone.v2.app.resources.domain_data_type_decimal
import org.openforis.collectone.v2.app.resources.domain_data_type_integer
import org.openforis.collectone.v2.app.resources.domain_data_type_select_multiple
import org.openforis.collectone.v2.app.resources.domain_data_type_select_one
import org.openforis.collectone.v2.app.resources.domain_data_type_text
import org.openforis.collectone.v2.app.resources.domain_effort_comparison_less
import org.openforis.collectone.v2.app.resources.domain_effort_comparison_more
import org.openforis.collectone.v2.app.resources.domain_effort_comparison_same
import org.openforis.collectone.v2.app.resources.domain_geometry_line
import org.openforis.collectone.v2.app.resources.domain_geometry_point
import org.openforis.collectone.v2.app.resources.domain_geometry_polygon
import org.openforis.collectone.v2.app.resources.domain_imagery_source_type_earth_engine
import org.openforis.collectone.v2.app.resources.domain_imagery_source_type_xyz_tiles
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_doi_minted
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_export
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_partner_push
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_receipt_generated
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_receipt_shared
import org.openforis.collectone.v2.app.resources.domain_impact_event_type_survey_closed
import org.openforis.collectone.v2.app.resources.domain_impact_pillar_adaptation
import org.openforis.collectone.v2.app.resources.domain_impact_pillar_mitigation
import org.openforis.collectone.v2.app.resources.domain_impact_pillar_protection
import org.openforis.collectone.v2.app.resources.domain_invitation_status_accepted
import org.openforis.collectone.v2.app.resources.domain_invitation_status_pending
import org.openforis.collectone.v2.app.resources.domain_layer_source_type_entity_dataset
import org.openforis.collectone.v2.app.resources.domain_library_status_deprecated
import org.openforis.collectone.v2.app.resources.domain_library_status_draft
import org.openforis.collectone.v2.app.resources.domain_library_status_stable
import org.openforis.collectone.v2.app.resources.domain_location_lock_state_locked
import org.openforis.collectone.v2.app.resources.domain_location_lock_state_locked_3d
import org.openforis.collectone.v2.app.resources.domain_location_lock_state_panned
import org.openforis.collectone.v2.app.resources.domain_measurement_unit_system_imperial
import org.openforis.collectone.v2.app.resources.domain_measurement_unit_system_imperial_short
import org.openforis.collectone.v2.app.resources.domain_measurement_unit_system_metric
import org.openforis.collectone.v2.app.resources.domain_measurement_unit_system_metric_short
import org.openforis.collectone.v2.app.resources.domain_membership_status_active
import org.openforis.collectone.v2.app.resources.domain_membership_status_invited
import org.openforis.collectone.v2.app.resources.domain_membership_status_requested
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_create_entity
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_create_submission
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_delete_entity
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_delete_submission
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_update_entity
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_update_submission
import org.openforis.collectone.v2.app.resources.domain_mutation_operation_kind_upload_media
import org.openforis.collectone.v2.app.resources.domain_offline_imagery_type_satellite
import org.openforis.collectone.v2.app.resources.domain_offline_imagery_type_terrain
import org.openforis.collectone.v2.app.resources.domain_organization_role_manager
import org.openforis.collectone.v2.app.resources.domain_organization_role_manager_description
import org.openforis.collectone.v2.app.resources.domain_organization_role_member
import org.openforis.collectone.v2.app.resources.domain_organization_role_member_description
import org.openforis.collectone.v2.app.resources.domain_organization_type_company
import org.openforis.collectone.v2.app.resources.domain_organization_type_cooperative
import org.openforis.collectone.v2.app.resources.domain_organization_type_government_agency
import org.openforis.collectone.v2.app.resources.domain_organization_type_iplc_organization
import org.openforis.collectone.v2.app.resources.domain_organization_type_ngo
import org.openforis.collectone.v2.app.resources.domain_organization_type_other
import org.openforis.collectone.v2.app.resources.domain_organization_type_research
import org.openforis.collectone.v2.app.resources.domain_privacy_class_aggregate_public
import org.openforis.collectone.v2.app.resources.domain_privacy_class_aggregate_public_description
import org.openforis.collectone.v2.app.resources.domain_privacy_class_org_only
import org.openforis.collectone.v2.app.resources.domain_privacy_class_org_only_description
import org.openforis.collectone.v2.app.resources.domain_privacy_class_sensitive
import org.openforis.collectone.v2.app.resources.domain_privacy_class_sensitive_description
import org.openforis.collectone.v2.app.resources.domain_quota_tier_basic
import org.openforis.collectone.v2.app.resources.domain_quota_tier_sponsored
import org.openforis.collectone.v2.app.resources.domain_quota_tier_unspecified
import org.openforis.collectone.v2.app.resources.domain_survey_lifecycle_state_archived
import org.openforis.collectone.v2.app.resources.domain_survey_lifecycle_state_closed
import org.openforis.collectone.v2.app.resources.domain_survey_lifecycle_state_draft
import org.openforis.collectone.v2.app.resources.domain_survey_lifecycle_state_published
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_land_titling
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_not_yet
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_protected_area_management
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_reported_ferm
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_shared_with_buyers
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_submitted_eudr_dds
import org.openforis.collectone.v2.app.resources.domain_survey_outcome_kind_trained_or_validated_model
import org.openforis.collectone.v2.app.resources.domain_sync_status_failed
import org.openforis.collectone.v2.app.resources.domain_sync_status_failed_description
import org.openforis.collectone.v2.app.resources.domain_sync_status_synced
import org.openforis.collectone.v2.app.resources.domain_sync_status_synced_description
import org.openforis.collectone.v2.app.resources.domain_sync_status_uploading
import org.openforis.collectone.v2.app.resources.domain_sync_status_uploading_description
import org.openforis.collectone.v2.app.resources.domain_upload_status_filter_failed
import org.openforis.collectone.v2.app.resources.domain_upload_status_filter_in_progress
import org.openforis.collectone.v2.app.resources.domain_upload_status_filter_pending
import org.openforis.collectone.v2.app.resources.domain_upload_status_filter_uploaded

// Display labels of domain enums. The domain holds no display text: every user-visible enum label
// is a string resource mapped here (or next to the feature that shows it).

/** Display label of [ImpactEventType]; resolve with `stringResource`. */
val ImpactEventType.labelRes: StringResource
  get() =
    when (this) {
      ImpactEventType.EXPORT -> Res.string.domain_impact_event_type_export
      ImpactEventType.PARTNER_PUSH -> Res.string.domain_impact_event_type_partner_push
      ImpactEventType.RECEIPT_GENERATED -> Res.string.domain_impact_event_type_receipt_generated
      ImpactEventType.RECEIPT_SHARED -> Res.string.domain_impact_event_type_receipt_shared
      ImpactEventType.DOI_MINTED -> Res.string.domain_impact_event_type_doi_minted
      ImpactEventType.SURVEY_CLOSED -> Res.string.domain_impact_event_type_survey_closed
    }

/** Display label of [SurveyLifecycleState]; resolve with `stringResource`. */
val SurveyLifecycleState.labelRes: StringResource
  get() =
    when (this) {
      SurveyLifecycleState.DRAFT -> Res.string.domain_survey_lifecycle_state_draft
      SurveyLifecycleState.PUBLISHED -> Res.string.domain_survey_lifecycle_state_published
      SurveyLifecycleState.CLOSED -> Res.string.domain_survey_lifecycle_state_closed
      SurveyLifecycleState.ARCHIVED -> Res.string.domain_survey_lifecycle_state_archived
    }

/** Display label of [SurveyOutcomeKind]; resolve with `stringResource`. */
val SurveyOutcomeKind.labelRes: StringResource
  get() =
    when (this) {
      SurveyOutcomeKind.SUBMITTED_EUDR_DDS ->
        Res.string.domain_survey_outcome_kind_submitted_eudr_dds
      SurveyOutcomeKind.REPORTED_FERM -> Res.string.domain_survey_outcome_kind_reported_ferm
      SurveyOutcomeKind.LAND_TITLING -> Res.string.domain_survey_outcome_kind_land_titling
      SurveyOutcomeKind.SHARED_WITH_BUYERS ->
        Res.string.domain_survey_outcome_kind_shared_with_buyers
      SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL ->
        Res.string.domain_survey_outcome_kind_trained_or_validated_model
      SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT ->
        Res.string.domain_survey_outcome_kind_protected_area_management
      SurveyOutcomeKind.NOT_YET -> Res.string.domain_survey_outcome_kind_not_yet
    }

/** Display label of [EffortComparison]; resolve with `stringResource`. */
val EffortComparison.labelRes: StringResource
  get() =
    when (this) {
      EffortComparison.LESS -> Res.string.domain_effort_comparison_less
      EffortComparison.SAME -> Res.string.domain_effort_comparison_same
      EffortComparison.MORE -> Res.string.domain_effort_comparison_more
    }

/** Display label of [SyncStatus]; resolve with `stringResource`. */
val SyncStatus.labelRes: StringResource
  get() =
    when (this) {
      SyncStatus.UPLOADING -> Res.string.domain_sync_status_uploading
      SyncStatus.SYNCED -> Res.string.domain_sync_status_synced
      SyncStatus.FAILED -> Res.string.domain_sync_status_failed
    }

/** Explanation of [SyncStatus]; resolve with `stringResource`. */
val SyncStatus.descriptionRes: StringResource
  get() =
    when (this) {
      SyncStatus.UPLOADING -> Res.string.domain_sync_status_uploading_description
      SyncStatus.SYNCED -> Res.string.domain_sync_status_synced_description
      SyncStatus.FAILED -> Res.string.domain_sync_status_failed_description
    }

/** Display label of [QuotaTier]; resolve with `stringResource`. */
val QuotaTier.labelRes: StringResource
  get() =
    when (this) {
      QuotaTier.UNSPECIFIED -> Res.string.domain_quota_tier_unspecified
      QuotaTier.BASIC -> Res.string.domain_quota_tier_basic
      QuotaTier.SPONSORED -> Res.string.domain_quota_tier_sponsored
    }

/** Display label of [MeasurementUnitSystem]; resolve with `stringResource`. */
val MeasurementUnitSystem.labelRes: StringResource
  get() =
    when (this) {
      MeasurementUnitSystem.METRIC -> Res.string.domain_measurement_unit_system_metric
      MeasurementUnitSystem.IMPERIAL -> Res.string.domain_measurement_unit_system_imperial
    }

/** Short display label of [MeasurementUnitSystem]; resolve with `stringResource`. */
val MeasurementUnitSystem.shortLabelRes: StringResource
  get() =
    when (this) {
      MeasurementUnitSystem.METRIC -> Res.string.domain_measurement_unit_system_metric_short
      MeasurementUnitSystem.IMPERIAL -> Res.string.domain_measurement_unit_system_imperial_short
    }

/** Display label of [LocationLockState]; resolve with `stringResource`. */
val LocationLockState.labelRes: StringResource
  get() =
    when (this) {
      LocationLockState.LOCKED -> Res.string.domain_location_lock_state_locked
      LocationLockState.LOCKED_3D -> Res.string.domain_location_lock_state_locked_3d
      LocationLockState.PANNED -> Res.string.domain_location_lock_state_panned
    }

/** Badge label of [LayerSourceType]; resolve with `stringResource`. */
val LayerSourceType.badgeLabelRes: StringResource
  get() =
    when (this) {
      LayerSourceType.ENTITY_DATASET -> Res.string.domain_layer_source_type_entity_dataset
    }

/** Display label of [BasemapType]; resolve with `stringResource`. */
val BasemapType.labelRes: StringResource
  get() =
    when (this) {
      BasemapType.NORMAL -> Res.string.domain_basemap_type_normal
      BasemapType.SATELLITE -> Res.string.domain_basemap_type_satellite
    }

/** Explanation of [BasemapType]; resolve with `stringResource`. */
val BasemapType.descriptionRes: StringResource
  get() =
    when (this) {
      BasemapType.NORMAL -> Res.string.domain_basemap_type_normal_description
      BasemapType.SATELLITE -> Res.string.domain_basemap_type_satellite_description
    }

/** Name of an [OfflineImageryType] layer of a saved map area; resolve with `stringResource`. */
val OfflineImageryType.labelRes: StringResource
  get() =
    when (this) {
      OfflineImageryType.SATELLITE -> Res.string.domain_offline_imagery_type_satellite
      OfflineImageryType.TERRAIN -> Res.string.domain_offline_imagery_type_terrain
    }

/** Display label of [OrganizationRole]; resolve with `stringResource`. */
val OrganizationRole.labelRes: StringResource
  get() =
    when (this) {
      OrganizationRole.MEMBER -> Res.string.domain_organization_role_member
      OrganizationRole.MANAGER -> Res.string.domain_organization_role_manager
    }

/** Explanation of [OrganizationRole]; resolve with `stringResource`. */
val OrganizationRole.descriptionRes: StringResource
  get() =
    when (this) {
      OrganizationRole.MEMBER -> Res.string.domain_organization_role_member_description
      OrganizationRole.MANAGER -> Res.string.domain_organization_role_manager_description
    }

/** Display label of [MembershipStatus]; resolve with `stringResource`. */
val MembershipStatus.labelRes: StringResource
  get() =
    when (this) {
      MembershipStatus.INVITED -> Res.string.domain_membership_status_invited
      MembershipStatus.REQUESTED -> Res.string.domain_membership_status_requested
      MembershipStatus.ACTIVE -> Res.string.domain_membership_status_active
    }

/** Display label of [ImagerySourceType]; resolve with `stringResource`. */
val ImagerySourceType.labelRes: StringResource
  get() =
    when (this) {
      ImagerySourceType.XYZ_TILES -> Res.string.domain_imagery_source_type_xyz_tiles
      ImagerySourceType.EARTH_ENGINE -> Res.string.domain_imagery_source_type_earth_engine
    }

/** Display label of [OrganizationType]; resolve with `stringResource`. */
val OrganizationType.labelRes: StringResource
  get() =
    when (this) {
      OrganizationType.GOVERNMENT_AGENCY -> Res.string.domain_organization_type_government_agency
      OrganizationType.COOPERATIVE -> Res.string.domain_organization_type_cooperative
      OrganizationType.IPLC_ORGANIZATION -> Res.string.domain_organization_type_iplc_organization
      OrganizationType.NGO -> Res.string.domain_organization_type_ngo
      OrganizationType.COMPANY -> Res.string.domain_organization_type_company
      OrganizationType.RESEARCH -> Res.string.domain_organization_type_research
      OrganizationType.OTHER -> Res.string.domain_organization_type_other
    }

/** Display label of [UploadStatusFilter]; resolve with `stringResource`. */
val UploadStatusFilter.labelRes: StringResource
  get() =
    when (this) {
      UploadStatusFilter.PENDING -> Res.string.domain_upload_status_filter_pending
      UploadStatusFilter.IN_PROGRESS -> Res.string.domain_upload_status_filter_in_progress
      UploadStatusFilter.UPLOADED -> Res.string.domain_upload_status_filter_uploaded
      UploadStatusFilter.FAILED -> Res.string.domain_upload_status_filter_failed
    }

/** Display label of [MutationSyncState]; resolve with `stringResource`. */
val MutationSyncState.labelRes: StringResource
  get() =
    when (this) {
      MutationSyncState.UPLOADING -> Res.string.domain_upload_status_filter_in_progress
      MutationSyncState.QUEUED -> Res.string.domain_upload_status_filter_pending
      MutationSyncState.RETRYING -> Res.string.domain_upload_status_filter_in_progress
      MutationSyncState.FAILED -> Res.string.domain_upload_status_filter_failed
      MutationSyncState.UPLOADED -> Res.string.domain_upload_status_filter_uploaded
    }

/** Display label of [MutationOperationKind]; resolve with `stringResource`. */
val MutationOperationKind.labelRes: StringResource
  get() =
    when (this) {
      MutationOperationKind.CREATE_SUBMISSION ->
        Res.string.domain_mutation_operation_kind_create_submission
      MutationOperationKind.UPDATE_SUBMISSION ->
        Res.string.domain_mutation_operation_kind_update_submission
      MutationOperationKind.DELETE_SUBMISSION ->
        Res.string.domain_mutation_operation_kind_delete_submission
      MutationOperationKind.CREATE_ENTITY -> Res.string.domain_mutation_operation_kind_create_entity
      MutationOperationKind.UPDATE_ENTITY -> Res.string.domain_mutation_operation_kind_update_entity
      MutationOperationKind.DELETE_ENTITY -> Res.string.domain_mutation_operation_kind_delete_entity
      MutationOperationKind.UPLOAD_MEDIA -> Res.string.domain_mutation_operation_kind_upload_media
    }

/** Display label of [InvitationStatus]; resolve with `stringResource`. */
val InvitationStatus.labelRes: StringResource
  get() =
    when (this) {
      InvitationStatus.PENDING -> Res.string.domain_invitation_status_pending
      InvitationStatus.ACCEPTED -> Res.string.domain_invitation_status_accepted
    }

/** Display label of [LibraryStatus]; resolve with `stringResource`. */
val LibraryStatus.labelRes: StringResource
  get() =
    when (this) {
      LibraryStatus.DRAFT -> Res.string.domain_library_status_draft
      LibraryStatus.STABLE -> Res.string.domain_library_status_stable
      LibraryStatus.DEPRECATED -> Res.string.domain_library_status_deprecated
    }

/** Display label of [ConceptAggregation]; resolve with `stringResource`. */
val ConceptAggregation.labelRes: StringResource
  get() =
    when (this) {
      ConceptAggregation.COUNT_DISTINCT_FEATURES ->
        Res.string.domain_concept_aggregation_count_distinct_features
      ConceptAggregation.COUNT_BY_CODE -> Res.string.domain_concept_aggregation_count_by_code
      ConceptAggregation.SUM -> Res.string.domain_concept_aggregation_sum
      ConceptAggregation.MEAN -> Res.string.domain_concept_aggregation_mean
      ConceptAggregation.SHARE_BY_CODE -> Res.string.domain_concept_aggregation_share_by_code
      ConceptAggregation.NONE -> Res.string.domain_concept_aggregation_none
    }

/** Explanation of [ConceptAggregation]; resolve with `stringResource`. */
val ConceptAggregation.descriptionRes: StringResource
  get() =
    when (this) {
      ConceptAggregation.COUNT_DISTINCT_FEATURES ->
        Res.string.domain_concept_aggregation_count_distinct_features_description
      ConceptAggregation.COUNT_BY_CODE ->
        Res.string.domain_concept_aggregation_count_by_code_description
      ConceptAggregation.SUM -> Res.string.domain_concept_aggregation_sum_description
      ConceptAggregation.MEAN -> Res.string.domain_concept_aggregation_mean_description
      ConceptAggregation.SHARE_BY_CODE ->
        Res.string.domain_concept_aggregation_share_by_code_description
      ConceptAggregation.NONE -> Res.string.domain_concept_aggregation_none_description
    }

/** Display label of [PrivacyClass]; resolve with `stringResource`. */
val PrivacyClass.labelRes: StringResource
  get() =
    when (this) {
      PrivacyClass.AGGREGATE_PUBLIC -> Res.string.domain_privacy_class_aggregate_public
      PrivacyClass.ORG_ONLY -> Res.string.domain_privacy_class_org_only
      PrivacyClass.SENSITIVE -> Res.string.domain_privacy_class_sensitive
    }

/** Explanation of [PrivacyClass]; resolve with `stringResource`. */
val PrivacyClass.descriptionRes: StringResource
  get() =
    when (this) {
      PrivacyClass.AGGREGATE_PUBLIC -> Res.string.domain_privacy_class_aggregate_public_description
      PrivacyClass.ORG_ONLY -> Res.string.domain_privacy_class_org_only_description
      PrivacyClass.SENSITIVE -> Res.string.domain_privacy_class_sensitive_description
    }

/** Display label of [ImpactPillar]; resolve with `stringResource`. */
val ImpactPillar.labelRes: StringResource
  get() =
    when (this) {
      ImpactPillar.MITIGATION -> Res.string.domain_impact_pillar_mitigation
      ImpactPillar.ADAPTATION -> Res.string.domain_impact_pillar_adaptation
      ImpactPillar.PROTECTION -> Res.string.domain_impact_pillar_protection
    }

/** Display label of [ConceptDataType]; resolve with `stringResource`. */
val ConceptDataType.labelRes: StringResource
  get() =
    when (this) {
      ConceptDataType.TEXT -> Res.string.domain_data_type_text
      ConceptDataType.INTEGER -> Res.string.domain_data_type_integer
      ConceptDataType.DECIMAL -> Res.string.domain_data_type_decimal
      ConceptDataType.DATE -> Res.string.domain_data_type_date
      ConceptDataType.SELECT_ONE -> Res.string.domain_data_type_select_one
      ConceptDataType.SELECT_MULTIPLE -> Res.string.domain_data_type_select_multiple
      ConceptDataType.POINT -> Res.string.domain_geometry_point
      ConceptDataType.LINE -> Res.string.domain_geometry_line
      ConceptDataType.POLYGON -> Res.string.domain_geometry_polygon
      ConceptDataType.MEDIA -> Res.string.domain_concept_data_type_media
    }
