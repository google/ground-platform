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
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.editor.StatusMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_allocation_mode_custom
import org.openforis.collectone.v2.app.resources.domain_allocation_mode_equal
import org.openforis.collectone.v2.app.resources.domain_allocation_mode_proportional
import org.openforis.collectone.v2.app.resources.domain_choice_source_manual
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_data_collector
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_data_collector_description
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_survey_organizer
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_survey_organizer_description
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_viewer
import org.openforis.collectone.v2.app.resources.domain_collaborator_role_viewer_description
import org.openforis.collectone.v2.app.resources.domain_data_type_date
import org.openforis.collectone.v2.app.resources.domain_data_type_decimal
import org.openforis.collectone.v2.app.resources.domain_data_type_integer
import org.openforis.collectone.v2.app.resources.domain_data_type_select_multiple
import org.openforis.collectone.v2.app.resources.domain_data_type_select_one
import org.openforis.collectone.v2.app.resources.domain_data_type_text
import org.openforis.collectone.v2.app.resources.domain_dataset_kind_data_table
import org.openforis.collectone.v2.app.resources.domain_dataset_kind_data_table_plural
import org.openforis.collectone.v2.app.resources.domain_dataset_kind_map_layer
import org.openforis.collectone.v2.app.resources.domain_dataset_kind_map_layer_plural
import org.openforis.collectone.v2.app.resources.domain_date_rule_between
import org.openforis.collectone.v2.app.resources.domain_date_rule_not_in_future
import org.openforis.collectone.v2.app.resources.domain_date_rule_not_in_past
import org.openforis.collectone.v2.app.resources.domain_editor_property_kind_boolean
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_audio
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_long_text
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_note
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_number
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_photo
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_text
import org.openforis.collectone.v2.app.resources.domain_editor_question_type_video
import org.openforis.collectone.v2.app.resources.domain_form_preview_target_mobile
import org.openforis.collectone.v2.app.resources.domain_form_preview_target_web
import org.openforis.collectone.v2.app.resources.domain_geometry_capture_gps_only
import org.openforis.collectone.v2.app.resources.domain_geometry_capture_gps_only_description
import org.openforis.collectone.v2.app.resources.domain_geometry_capture_gps_or_map
import org.openforis.collectone.v2.app.resources.domain_geometry_capture_gps_or_map_description
import org.openforis.collectone.v2.app.resources.domain_geometry_kind_line
import org.openforis.collectone.v2.app.resources.domain_geometry_kind_point
import org.openforis.collectone.v2.app.resources.domain_geometry_kind_polygon
import org.openforis.collectone.v2.app.resources.domain_geometry_line
import org.openforis.collectone.v2.app.resources.domain_geometry_point
import org.openforis.collectone.v2.app.resources.domain_geometry_polygon
import org.openforis.collectone.v2.app.resources.domain_media_source_capture_only
import org.openforis.collectone.v2.app.resources.domain_media_source_capture_only_description
import org.openforis.collectone.v2.app.resources.domain_media_source_capture_or_upload
import org.openforis.collectone.v2.app.resources.domain_media_source_capture_or_upload_description
import org.openforis.collectone.v2.app.resources.domain_peer_data_visibility_all
import org.openforis.collectone.v2.app.resources.domain_peer_data_visibility_all_description
import org.openforis.collectone.v2.app.resources.domain_peer_data_visibility_own_only
import org.openforis.collectone.v2.app.resources.domain_peer_data_visibility_own_only_description
import org.openforis.collectone.v2.app.resources.domain_plot_shape_option_circle
import org.openforis.collectone.v2.app.resources.domain_plot_shape_option_point
import org.openforis.collectone.v2.app.resources.domain_plot_shape_option_square
import org.openforis.collectone.v2.app.resources.domain_property_type_boolean
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_equals
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_greater_than
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_includes
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_is_answered
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_less_than
import org.openforis.collectone.v2.app.resources.domain_relevance_operator_not_equals
import org.openforis.collectone.v2.app.resources.domain_sample_method_cluster
import org.openforis.collectone.v2.app.resources.domain_sample_method_cluster_description
import org.openforis.collectone.v2.app.resources.domain_sample_method_simple_random
import org.openforis.collectone.v2.app.resources.domain_sample_method_simple_random_description
import org.openforis.collectone.v2.app.resources.domain_sample_method_stratified_random
import org.openforis.collectone.v2.app.resources.domain_sample_method_stratified_random_description
import org.openforis.collectone.v2.app.resources.domain_sample_method_systematic_grid
import org.openforis.collectone.v2.app.resources.domain_sample_method_systematic_grid_description
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_anyone_with_link
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_anyone_with_link_description
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_organization
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_organization_description
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_public
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_public_description
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_restricted
import org.openforis.collectone.v2.app.resources.domain_sharing_policy_restricted_description
import org.openforis.collectone.v2.app.resources.domain_status_condition_subject_entity_property
import org.openforis.collectone.v2.app.resources.domain_status_condition_subject_question
import org.openforis.collectone.v2.app.resources.domain_status_condition_subject_submissions
import org.openforis.collectone.v2.app.resources.domain_status_marker_symbol_alert
import org.openforis.collectone.v2.app.resources.domain_status_marker_symbol_check
import org.openforis.collectone.v2.app.resources.domain_status_marker_symbol_cross
import org.openforis.collectone.v2.app.resources.domain_status_marker_symbol_half
import org.openforis.collectone.v2.app.resources.domain_status_marker_symbol_open
import org.openforis.collectone.v2.app.resources.domain_sub_plot_mode_center
import org.openforis.collectone.v2.app.resources.domain_sub_plot_mode_grid
import org.openforis.collectone.v2.app.resources.domain_sub_plot_mode_none
import org.openforis.collectone.v2.app.resources.domain_sub_plot_mode_random
import org.openforis.collectone.v2.app.resources.domain_text_pattern_custom
import org.openforis.collectone.v2.app.resources.domain_text_pattern_digits
import org.openforis.collectone.v2.app.resources.domain_text_pattern_email
import org.openforis.collectone.v2.app.resources.domain_text_pattern_letters
import org.openforis.collectone.v2.map.GeometryType

// Display labels of the Survey and Form editors' domain enums (sharing, datasets, sampling,
// questions, display logic, and status rules); see DomainLabels.kt for the rest.

/** Display label of [CollaboratorRole]; resolve with `stringResource`. */
val CollaboratorRole.labelRes: StringResource
  get() =
    when (this) {
      CollaboratorRole.VIEWER -> Res.string.domain_collaborator_role_viewer
      CollaboratorRole.DATA_COLLECTOR -> Res.string.domain_collaborator_role_data_collector
      CollaboratorRole.SURVEY_ORGANIZER -> Res.string.domain_collaborator_role_survey_organizer
    }

/** Explanation of [CollaboratorRole]; resolve with `stringResource`. */
val CollaboratorRole.descriptionRes: StringResource
  get() =
    when (this) {
      CollaboratorRole.VIEWER -> Res.string.domain_collaborator_role_viewer_description
      CollaboratorRole.DATA_COLLECTOR ->
        Res.string.domain_collaborator_role_data_collector_description
      CollaboratorRole.SURVEY_ORGANIZER ->
        Res.string.domain_collaborator_role_survey_organizer_description
    }

/** Display label of [SharingPolicy]; resolve with `stringResource`. */
val SharingPolicy.labelRes: StringResource
  get() =
    when (this) {
      SharingPolicy.RESTRICTED -> Res.string.domain_sharing_policy_restricted
      SharingPolicy.ORGANIZATION -> Res.string.domain_sharing_policy_organization
      SharingPolicy.ANYONE_WITH_LINK -> Res.string.domain_sharing_policy_anyone_with_link
      SharingPolicy.PUBLIC -> Res.string.domain_sharing_policy_public
    }

/** Explanation of [SharingPolicy]; resolve with `stringResource`. */
val SharingPolicy.descriptionRes: StringResource
  get() =
    when (this) {
      SharingPolicy.RESTRICTED -> Res.string.domain_sharing_policy_restricted_description
      SharingPolicy.ORGANIZATION -> Res.string.domain_sharing_policy_organization_description
      SharingPolicy.ANYONE_WITH_LINK ->
        Res.string.domain_sharing_policy_anyone_with_link_description
      SharingPolicy.PUBLIC -> Res.string.domain_sharing_policy_public_description
    }

/** Display label of [PeerDataVisibility]; resolve with `stringResource`. */
val PeerDataVisibility.labelRes: StringResource
  get() =
    when (this) {
      PeerDataVisibility.ALL -> Res.string.domain_peer_data_visibility_all
      PeerDataVisibility.OWN_ONLY -> Res.string.domain_peer_data_visibility_own_only
    }

/** Explanation of [PeerDataVisibility]; resolve with `stringResource`. */
val PeerDataVisibility.descriptionRes: StringResource
  get() =
    when (this) {
      PeerDataVisibility.ALL -> Res.string.domain_peer_data_visibility_all_description
      PeerDataVisibility.OWN_ONLY -> Res.string.domain_peer_data_visibility_own_only_description
    }

/** Singular name of [DatasetKind]; resolve with `stringResource`. */
val DatasetKind.singularRes: StringResource
  get() =
    when (this) {
      DatasetKind.MAP_LAYER -> Res.string.domain_dataset_kind_map_layer
      DatasetKind.DATA_TABLE -> Res.string.domain_dataset_kind_data_table
    }

/** Plural name of [DatasetKind]; resolve with `stringResource`. */
val DatasetKind.pluralRes: StringResource
  get() =
    when (this) {
      DatasetKind.MAP_LAYER -> Res.string.domain_dataset_kind_map_layer_plural
      DatasetKind.DATA_TABLE -> Res.string.domain_dataset_kind_data_table_plural
    }

/**
 * Plural display label of [GeometryType] (e.g. `Polygons`), naming the features of a Map layer;
 * resolve with `stringResource`.
 */
val GeometryType.pluralRes: StringResource
  get() =
    when (this) {
      GeometryType.POINT -> Res.string.domain_geometry_kind_point
      GeometryType.LINE_STRING -> Res.string.domain_geometry_kind_line
      GeometryType.POLYGON -> Res.string.domain_geometry_kind_polygon
    }

/** Display label of [PropertyType]; resolve with `stringResource`. */
val PropertyType.labelRes: StringResource
  get() =
    when (this) {
      PropertyType.TEXT -> Res.string.domain_data_type_text
      PropertyType.INTEGER -> Res.string.domain_data_type_integer
      PropertyType.DECIMAL -> Res.string.domain_data_type_decimal
      PropertyType.BOOLEAN -> Res.string.domain_property_type_boolean
      PropertyType.DATE -> Res.string.domain_data_type_date
    }

/** Display label of [SampleMethod]; resolve with `stringResource`. */
val SampleMethod.labelRes: StringResource
  get() =
    when (this) {
      SampleMethod.SYSTEMATIC_GRID -> Res.string.domain_sample_method_systematic_grid
      SampleMethod.SIMPLE_RANDOM -> Res.string.domain_sample_method_simple_random
      SampleMethod.STRATIFIED_RANDOM -> Res.string.domain_sample_method_stratified_random
      SampleMethod.CLUSTER -> Res.string.domain_sample_method_cluster
    }

/** Explanation of [SampleMethod]; resolve with `stringResource`. */
val SampleMethod.descriptionRes: StringResource
  get() =
    when (this) {
      SampleMethod.SYSTEMATIC_GRID -> Res.string.domain_sample_method_systematic_grid_description
      SampleMethod.SIMPLE_RANDOM -> Res.string.domain_sample_method_simple_random_description
      SampleMethod.STRATIFIED_RANDOM ->
        Res.string.domain_sample_method_stratified_random_description
      SampleMethod.CLUSTER -> Res.string.domain_sample_method_cluster_description
    }

/** Display label of [AllocationMode]; resolve with `stringResource`. */
val AllocationMode.labelRes: StringResource
  get() =
    when (this) {
      AllocationMode.EQUAL -> Res.string.domain_allocation_mode_equal
      AllocationMode.PROPORTIONAL -> Res.string.domain_allocation_mode_proportional
      AllocationMode.CUSTOM -> Res.string.domain_allocation_mode_custom
    }

/** Display label of [PlotShapeOption]; resolve with `stringResource`. */
val PlotShapeOption.labelRes: StringResource
  get() =
    when (this) {
      PlotShapeOption.SQUARE -> Res.string.domain_plot_shape_option_square
      PlotShapeOption.CIRCLE -> Res.string.domain_plot_shape_option_circle
      PlotShapeOption.POINT -> Res.string.domain_plot_shape_option_point
    }

/** Display label of [SubPlotMode]; resolve with `stringResource`. */
val SubPlotMode.labelRes: StringResource
  get() =
    when (this) {
      SubPlotMode.NONE -> Res.string.domain_sub_plot_mode_none
      SubPlotMode.CENTER -> Res.string.domain_sub_plot_mode_center
      SubPlotMode.GRID -> Res.string.domain_sub_plot_mode_grid
      SubPlotMode.RANDOM -> Res.string.domain_sub_plot_mode_random
    }

/** Localized name of this preview platform; resolve with `stringResource`. */
val FormPreviewTarget.labelRes: StringResource
  get() =
    when (this) {
      FormPreviewTarget.MOBILE -> Res.string.domain_form_preview_target_mobile
      FormPreviewTarget.WEB -> Res.string.domain_form_preview_target_web
    }

/** Display label of [EditorQuestionType]; resolve with `stringResource`. */
val EditorQuestionType.labelRes: StringResource
  get() =
    when (this) {
      EditorQuestionType.TEXT -> Res.string.domain_editor_question_type_text
      EditorQuestionType.LONG_TEXT -> Res.string.domain_editor_question_type_long_text
      EditorQuestionType.NUMBER -> Res.string.domain_editor_question_type_number
      EditorQuestionType.SELECT_ONE -> Res.string.domain_data_type_select_one
      EditorQuestionType.SELECT_MULTIPLE -> Res.string.domain_data_type_select_multiple
      EditorQuestionType.DATE -> Res.string.domain_data_type_date
      EditorQuestionType.LOCATION -> Res.string.domain_geometry_point
      EditorQuestionType.LINE -> Res.string.domain_geometry_line
      EditorQuestionType.POLYGON -> Res.string.domain_geometry_polygon
      EditorQuestionType.PHOTO -> Res.string.domain_editor_question_type_photo
      EditorQuestionType.VIDEO -> Res.string.domain_editor_question_type_video
      EditorQuestionType.AUDIO -> Res.string.domain_editor_question_type_audio
      EditorQuestionType.NOTE -> Res.string.domain_editor_question_type_note
    }

/** Localized name of this media source setting; resolve with `stringResource`. */
val MediaSource.labelRes: StringResource
  get() =
    when (this) {
      MediaSource.CAPTURE_OR_UPLOAD -> Res.string.domain_media_source_capture_or_upload
      MediaSource.CAPTURE_ONLY -> Res.string.domain_media_source_capture_only
    }

/** Localized explanation of this media source setting; resolve with `stringResource`. */
val MediaSource.descriptionRes: StringResource
  get() =
    when (this) {
      MediaSource.CAPTURE_OR_UPLOAD -> Res.string.domain_media_source_capture_or_upload_description
      MediaSource.CAPTURE_ONLY -> Res.string.domain_media_source_capture_only_description
    }

/** Display label of [GeometryCapture]; resolve with `stringResource`. */
val GeometryCapture.labelRes: StringResource
  get() =
    when (this) {
      GeometryCapture.GPS_ONLY -> Res.string.domain_geometry_capture_gps_only
      GeometryCapture.GPS_OR_MAP -> Res.string.domain_geometry_capture_gps_or_map
    }

/** Explanation of [GeometryCapture]; resolve with `stringResource`. */
val GeometryCapture.descriptionRes: StringResource
  get() =
    when (this) {
      GeometryCapture.GPS_ONLY -> Res.string.domain_geometry_capture_gps_only_description
      GeometryCapture.GPS_OR_MAP -> Res.string.domain_geometry_capture_gps_or_map_description
    }

/** Display label of [RelevanceOperator]; resolve with `stringResource`. */
val RelevanceOperator.labelRes: StringResource
  get() =
    when (this) {
      RelevanceOperator.EQUALS -> Res.string.domain_relevance_operator_equals
      RelevanceOperator.NOT_EQUALS -> Res.string.domain_relevance_operator_not_equals
      RelevanceOperator.GREATER_THAN -> Res.string.domain_relevance_operator_greater_than
      RelevanceOperator.LESS_THAN -> Res.string.domain_relevance_operator_less_than
      RelevanceOperator.INCLUDES -> Res.string.domain_relevance_operator_includes
      RelevanceOperator.IS_ANSWERED -> Res.string.domain_relevance_operator_is_answered
    }

/** Display label of [ChoiceSource]; resolve with `stringResource`. */
val ChoiceSource.labelRes: StringResource
  get() =
    when (this) {
      ChoiceSource.MANUAL -> Res.string.domain_choice_source_manual
      ChoiceSource.MAP_LAYER -> Res.string.domain_dataset_kind_map_layer
      ChoiceSource.DATA_TABLE -> Res.string.domain_dataset_kind_data_table
    }

/** Display label of [StatusMarkerSymbol]; resolve with `stringResource`. */
val StatusMarkerSymbol.labelRes: StringResource
  get() =
    when (this) {
      StatusMarkerSymbol.OPEN -> Res.string.domain_status_marker_symbol_open
      StatusMarkerSymbol.HALF -> Res.string.domain_status_marker_symbol_half
      StatusMarkerSymbol.CHECK -> Res.string.domain_status_marker_symbol_check
      StatusMarkerSymbol.ALERT -> Res.string.domain_status_marker_symbol_alert
      StatusMarkerSymbol.CROSS -> Res.string.domain_status_marker_symbol_cross
    }

/** Display label of [StatusConditionSubject]; resolve with `stringResource`. */
val StatusConditionSubject.labelRes: StringResource
  get() =
    when (this) {
      StatusConditionSubject.SUBMISSIONS -> Res.string.domain_status_condition_subject_submissions
      StatusConditionSubject.QUESTION -> Res.string.domain_status_condition_subject_question
      StatusConditionSubject.ENTITY_PROPERTY ->
        Res.string.domain_status_condition_subject_entity_property
    }

/** Display label of [EditorPropertyKind]; resolve with `stringResource`. */
val EditorPropertyKind.labelRes: StringResource
  get() =
    when (this) {
      EditorPropertyKind.TEXT -> Res.string.domain_data_type_text
      EditorPropertyKind.INTEGER -> Res.string.domain_data_type_integer
      EditorPropertyKind.DECIMAL -> Res.string.domain_data_type_decimal
      EditorPropertyKind.BOOLEAN -> Res.string.domain_editor_property_kind_boolean
      EditorPropertyKind.DATE -> Res.string.domain_data_type_date
    }

/** Display label of [DateRule]; resolve with `stringResource`. */
val DateRule.labelRes: StringResource
  get() =
    when (this) {
      DateRule.NOT_IN_FUTURE -> Res.string.domain_date_rule_not_in_future
      DateRule.NOT_IN_PAST -> Res.string.domain_date_rule_not_in_past
      DateRule.BETWEEN -> Res.string.domain_date_rule_between
    }

/** Display label of [TextPattern]; resolve with `stringResource`. */
val TextPattern.labelRes: StringResource
  get() =
    when (this) {
      TextPattern.EMAIL -> Res.string.domain_text_pattern_email
      TextPattern.DIGITS -> Res.string.domain_text_pattern_digits
      TextPattern.LETTERS -> Res.string.domain_text_pattern_letters
      TextPattern.CUSTOM -> Res.string.domain_text_pattern_custom
    }
