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

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.CountedMessageKey
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.MessageKey
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.QuotaCountedMessage
import org.openforis.collectone.v2.app.domain.model.QuotaMessage
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.SubmissionNotice
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.ComparisonOperator
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.DisplayLogicMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueCountedMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
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
import org.openforis.collectone.v2.app.resources.domain_display_logic_shown_if
import org.openforis.collectone.v2.app.resources.domain_display_logic_shown_if_answered
import org.openforis.collectone.v2.app.resources.domain_display_logic_source_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_add_choice
import org.openforis.collectone.v2.app.resources.domain_issue_cascade_choice_parent_value_unknown
import org.openforis.collectone.v2.app.resources.domain_issue_cascade_parent_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_cascade_parent_later
import org.openforis.collectone.v2.app.resources.domain_issue_cascade_parent_not_choice
import org.openforis.collectone.v2.app.resources.domain_issue_choice_dataset_generated
import org.openforis.collectone.v2.app.resources.domain_issue_choice_dataset_missing
import org.openforis.collectone.v2.app.resources.domain_issue_choice_dataset_no_creation_form_data_table
import org.openforis.collectone.v2.app.resources.domain_issue_choice_dataset_no_creation_form_map_layer
import org.openforis.collectone.v2.app.resources.domain_issue_choice_value_duplicate
import org.openforis.collectone.v2.app.resources.domain_issue_choice_value_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_choose_choice_data_table
import org.openforis.collectone.v2.app.resources.domain_issue_choose_choice_map_layer
import org.openforis.collectone.v2.app.resources.domain_issue_comparison_operator_not_applicable
import org.openforis.collectone.v2.app.resources.domain_issue_comparison_target_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_comparison_target_in_repeat
import org.openforis.collectone.v2.app.resources.domain_issue_comparison_target_missing
import org.openforis.collectone.v2.app.resources.domain_issue_comparison_target_wrong_type
import org.openforis.collectone.v2.app.resources.domain_issue_custom_expression_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_custom_pattern_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_custom_pattern_missing
import org.openforis.collectone.v2.app.resources.domain_issue_custom_pattern_quotes
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_id_duplicate
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_id_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_key_property_missing
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_label_property_missing
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_property_name_duplicate
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_property_name_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_row_geometry_too_short
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_row_key_duplicate
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_row_key_missing
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_row_value_missing
import org.openforis.collectone.v2.app.resources.domain_issue_dataset_row_value_wrong_type
import org.openforis.collectone.v2.app.resources.domain_issue_date_range_missing
import org.openforis.collectone.v2.app.resources.domain_issue_date_range_reversed
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_operator_not_applicable
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_source_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_source_in_repeat
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_source_later
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_value_missing
import org.openforis.collectone.v2.app.resources.domain_issue_display_logic_value_not_number
import org.openforis.collectone.v2.app.resources.domain_issue_distance_max_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_distance_min_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_distance_min_more_than_max
import org.openforis.collectone.v2.app.resources.domain_issue_distance_reference_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_distance_reference_in_repeat
import org.openforis.collectone.v2.app.resources.domain_issue_distance_reference_missing
import org.openforis.collectone.v2.app.resources.domain_issue_distance_reference_not_location
import org.openforis.collectone.v2.app.resources.domain_issue_form_missing_id
import org.openforis.collectone.v2.app.resources.domain_issue_google_earth_incompatible_question
import org.openforis.collectone.v2.app.resources.domain_issue_label_empty
import org.openforis.collectone.v2.app.resources.domain_issue_max_length_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_max_not_number
import org.openforis.collectone.v2.app.resources.domain_issue_max_selections_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_min_length_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_min_length_more_than_max
import org.openforis.collectone.v2.app.resources.domain_issue_min_more_than_max
import org.openforis.collectone.v2.app.resources.domain_issue_min_not_number
import org.openforis.collectone.v2.app.resources.domain_issue_min_selections_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_min_selections_more_than_choices
import org.openforis.collectone.v2.app.resources.domain_issue_min_selections_more_than_max
import org.openforis.collectone.v2.app.resources.domain_issue_name_duplicate
import org.openforis.collectone.v2.app.resources.domain_issue_name_invalid
import org.openforis.collectone.v2.app.resources.domain_issue_name_required
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_geometry_question_required
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_id_question_missing_data_table
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_id_question_missing_map_layer
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_match_property_missing
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_no_fields
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_property_already_updated
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_property_not_updatable
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_reserved_name_data_table
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_reserved_name_map_layer
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_target_deleted
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_target_missing
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_type_mismatch
import org.openforis.collectone.v2.app.resources.domain_issue_save_to_unknown_property
import org.openforis.collectone.v2.app.resources.domain_issue_section_display_logic_source_inside
import org.openforis.collectone.v2.app.resources.domain_issue_sharing_organization_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_default_label_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_label_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_min_submissions
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_operator_not_applicable
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_missing_data_table
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_missing_map_layer
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_operator_not_applicable
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_unwritten
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_value_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_property_value_not_number
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_question_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_value_missing
import org.openforis.collectone.v2.app.resources.domain_issue_status_rule_value_not_number
import org.openforis.collectone.v2.app.resources.domain_issue_web_incompatible_geometry
import org.openforis.collectone.v2.app.resources.domain_notice_entity_created
import org.openforis.collectone.v2.app.resources.domain_notice_entity_submitted
import org.openforis.collectone.v2.app.resources.domain_notice_standalone_submitted
import org.openforis.collectone.v2.app.resources.domain_quota_max_adhoc_entities
import org.openforis.collectone.v2.app.resources.domain_quota_max_area_vertices
import org.openforis.collectone.v2.app.resources.domain_quota_max_collaborators
import org.openforis.collectone.v2.app.resources.domain_quota_max_dataset_polygon_vertices
import org.openforis.collectone.v2.app.resources.domain_quota_max_forms
import org.openforis.collectone.v2.app.resources.domain_quota_max_generated_entities
import org.openforis.collectone.v2.app.resources.domain_quota_max_photo_questions
import org.openforis.collectone.v2.app.resources.domain_quota_max_predefined_entities
import org.openforis.collectone.v2.app.resources.domain_quota_max_questions
import org.openforis.collectone.v2.app.resources.domain_quota_max_submissions_per_entity
import org.openforis.collectone.v2.app.resources.domain_quota_max_surveys_per_owner
import org.openforis.collectone.v2.app.resources.domain_quota_max_surveys_per_user
import org.openforis.collectone.v2.app.resources.domain_warning_concept_deprecated
import org.openforis.collectone.v2.app.resources.domain_warning_concept_linked_twice
import org.openforis.collectone.v2.app.resources.domain_warning_concept_not_in_dictionary
import org.openforis.collectone.v2.app.resources.domain_warning_concept_type_mismatch
import org.openforis.collectone.v2.app.resources.domain_warning_concept_unmapped_choices
import org.openforis.collectone.v2.map.GeometryType

/** This domain message as [UiText] in the user's language. */
fun DomainMessage.toUiText(): UiText =
  when (this) {
    is DomainMessage.Text -> UiText.Resource(key.resource(), args.map { it.toUiTextArg() })
    is DomainMessage.Counted ->
      UiText.Plural(key.resource(), quantity, args.map { it.toUiTextArg() })
    is DomainMessage.Label -> UiText.Resource(labelResource(value))
    is DomainMessage.Lowercase -> UiText.Lowercase(message.toUiText())
  }

private fun Any.toUiTextArg(): Any = if (this is DomainMessage) toUiText() else this

private fun MessageKey.resource(): StringResource =
  when (this) {
    is EditorIssueMessage -> resource
    is DisplayLogicMessage -> resource
    is QuotaMessage -> resource
    is SubmissionNotice -> resource
    else -> throw IllegalArgumentException("No string resource for the message $this")
  }

private fun CountedMessageKey.resource(): PluralStringResource =
  when (this) {
    is EditorIssueCountedMessage -> resource
    is QuotaCountedMessage -> resource
    else -> throw IllegalArgumentException("No plural resource for the message $this")
  }

/** The display label of a domain enum value carried by a [DomainMessage.Label]. */
private fun labelResource(value: Enum<*>): StringResource =
  when (value) {
    is ImpactEventType -> value.labelRes
    is SurveyLifecycleState -> value.labelRes
    is SurveyOutcomeKind -> value.labelRes
    is EffortComparison -> value.labelRes
    is SyncStatus -> value.labelRes
    is QuotaTier -> value.labelRes
    is MeasurementUnitSystem -> value.labelRes
    is LocationLockState -> value.labelRes
    is BasemapType -> value.labelRes
    is OrganizationRole -> value.labelRes
    is MembershipStatus -> value.labelRes
    is ImagerySourceType -> value.labelRes
    is OrganizationType -> value.labelRes
    is UploadStatusFilter -> value.labelRes
    is MutationSyncState -> value.labelRes
    is MutationOperationKind -> value.labelRes
    is InvitationStatus -> value.labelRes
    is GeometryType -> value.labelRes
    is LibraryStatus -> value.labelRes
    is ConceptAggregation -> value.labelRes
    is PrivacyClass -> value.labelRes
    is ImpactPillar -> value.labelRes
    is ConceptDataType -> value.labelRes
    is CollaboratorRole -> value.labelRes
    is SharingPolicy -> value.labelRes
    is PeerDataVisibility -> value.labelRes
    is PropertyType -> value.labelRes
    is SampleMethod -> value.labelRes
    is AllocationMode -> value.labelRes
    is PlotShapeOption -> value.labelRes
    is SubPlotMode -> value.labelRes
    is FormPreviewTarget -> value.labelRes
    is EditorQuestionType -> value.labelRes
    is MediaSource -> value.labelRes
    is GeometryCapture -> value.labelRes
    is RelevanceOperator -> value.labelRes
    is ChoiceSource -> value.labelRes
    is StatusMarkerSymbol -> value.labelRes
    is StatusConditionSubject -> value.labelRes
    is EditorPropertyKind -> value.labelRes
    is DateRule -> value.labelRes
    is TextPattern -> value.labelRes
    is ComparisonOperator -> value.labelRes
    else -> throw IllegalArgumentException("No display label for $value")
  }

private val EditorIssueMessage.resource: StringResource
  get() =
    when (this) {
      EditorIssueMessage.FORM_MISSING_ID -> Res.string.domain_issue_form_missing_id
      EditorIssueMessage.NAME_REQUIRED -> Res.string.domain_issue_name_required
      EditorIssueMessage.NAME_INVALID -> Res.string.domain_issue_name_invalid
      EditorIssueMessage.NAME_DUPLICATE -> Res.string.domain_issue_name_duplicate
      EditorIssueMessage.LABEL_EMPTY -> Res.string.domain_issue_label_empty
      EditorIssueMessage.CHOOSE_CHOICE_DATA_TABLE ->
        Res.string.domain_issue_choose_choice_data_table
      EditorIssueMessage.CHOOSE_CHOICE_MAP_LAYER -> Res.string.domain_issue_choose_choice_map_layer
      EditorIssueMessage.CHOICE_DATASET_MISSING -> Res.string.domain_issue_choice_dataset_missing
      EditorIssueMessage.CHOICE_DATASET_GENERATED ->
        Res.string.domain_issue_choice_dataset_generated
      EditorIssueMessage.CHOICE_DATASET_NO_CREATION_FORM_MAP_LAYER ->
        Res.string.domain_issue_choice_dataset_no_creation_form_map_layer
      EditorIssueMessage.CHOICE_DATASET_NO_CREATION_FORM_DATA_TABLE ->
        Res.string.domain_issue_choice_dataset_no_creation_form_data_table
      EditorIssueMessage.ADD_CHOICE -> Res.string.domain_issue_add_choice
      EditorIssueMessage.CHOICE_VALUE_INVALID -> Res.string.domain_issue_choice_value_invalid
      EditorIssueMessage.CHOICE_VALUE_DUPLICATE -> Res.string.domain_issue_choice_value_duplicate
      EditorIssueMessage.DISPLAY_LOGIC_SOURCE_DELETED ->
        Res.string.domain_issue_display_logic_source_deleted
      EditorIssueMessage.DISPLAY_LOGIC_SOURCE_LATER ->
        Res.string.domain_issue_display_logic_source_later
      EditorIssueMessage.DISPLAY_LOGIC_SOURCE_IN_REPEAT ->
        Res.string.domain_issue_display_logic_source_in_repeat
      EditorIssueMessage.SECTION_DISPLAY_LOGIC_SOURCE_INSIDE ->
        Res.string.domain_issue_section_display_logic_source_inside
      EditorIssueMessage.DISPLAY_LOGIC_OPERATOR_NOT_APPLICABLE ->
        Res.string.domain_issue_display_logic_operator_not_applicable
      EditorIssueMessage.DISPLAY_LOGIC_VALUE_MISSING ->
        Res.string.domain_issue_display_logic_value_missing
      EditorIssueMessage.DISPLAY_LOGIC_VALUE_NOT_NUMBER ->
        Res.string.domain_issue_display_logic_value_not_number
      EditorIssueMessage.CASCADE_PARENT_DELETED -> Res.string.domain_issue_cascade_parent_deleted
      EditorIssueMessage.CASCADE_PARENT_LATER -> Res.string.domain_issue_cascade_parent_later
      EditorIssueMessage.CASCADE_PARENT_NOT_CHOICE ->
        Res.string.domain_issue_cascade_parent_not_choice
      EditorIssueMessage.CASCADE_CHOICE_PARENT_VALUE_UNKNOWN ->
        Res.string.domain_issue_cascade_choice_parent_value_unknown
      EditorIssueMessage.WEB_INCOMPATIBLE_GEOMETRY ->
        Res.string.domain_issue_web_incompatible_geometry
      EditorIssueMessage.GOOGLE_EARTH_INCOMPATIBLE_QUESTION ->
        Res.string.domain_issue_google_earth_incompatible_question
      EditorIssueMessage.MIN_NOT_NUMBER -> Res.string.domain_issue_min_not_number
      EditorIssueMessage.MAX_NOT_NUMBER -> Res.string.domain_issue_max_not_number
      EditorIssueMessage.MIN_MORE_THAN_MAX -> Res.string.domain_issue_min_more_than_max
      EditorIssueMessage.MIN_LENGTH_INVALID -> Res.string.domain_issue_min_length_invalid
      EditorIssueMessage.MAX_LENGTH_INVALID -> Res.string.domain_issue_max_length_invalid
      EditorIssueMessage.MIN_LENGTH_MORE_THAN_MAX ->
        Res.string.domain_issue_min_length_more_than_max
      EditorIssueMessage.MIN_SELECTIONS_INVALID -> Res.string.domain_issue_min_selections_invalid
      EditorIssueMessage.MAX_SELECTIONS_INVALID -> Res.string.domain_issue_max_selections_invalid
      EditorIssueMessage.MIN_SELECTIONS_MORE_THAN_MAX ->
        Res.string.domain_issue_min_selections_more_than_max
      EditorIssueMessage.MIN_SELECTIONS_MORE_THAN_CHOICES ->
        Res.string.domain_issue_min_selections_more_than_choices
      EditorIssueMessage.CUSTOM_PATTERN_MISSING -> Res.string.domain_issue_custom_pattern_missing
      EditorIssueMessage.CUSTOM_PATTERN_INVALID -> Res.string.domain_issue_custom_pattern_invalid
      EditorIssueMessage.CUSTOM_PATTERN_QUOTES -> Res.string.domain_issue_custom_pattern_quotes
      EditorIssueMessage.DATE_RANGE_MISSING -> Res.string.domain_issue_date_range_missing
      EditorIssueMessage.DATE_RANGE_REVERSED -> Res.string.domain_issue_date_range_reversed
      EditorIssueMessage.COMPARISON_TARGET_MISSING ->
        Res.string.domain_issue_comparison_target_missing
      EditorIssueMessage.COMPARISON_TARGET_DELETED ->
        Res.string.domain_issue_comparison_target_deleted
      EditorIssueMessage.COMPARISON_TARGET_IN_REPEAT ->
        Res.string.domain_issue_comparison_target_in_repeat
      EditorIssueMessage.COMPARISON_TARGET_WRONG_TYPE ->
        Res.string.domain_issue_comparison_target_wrong_type
      EditorIssueMessage.COMPARISON_OPERATOR_NOT_APPLICABLE ->
        Res.string.domain_issue_comparison_operator_not_applicable
      EditorIssueMessage.DISTANCE_REFERENCE_MISSING ->
        Res.string.domain_issue_distance_reference_missing
      EditorIssueMessage.DISTANCE_REFERENCE_DELETED ->
        Res.string.domain_issue_distance_reference_deleted
      EditorIssueMessage.DISTANCE_REFERENCE_IN_REPEAT ->
        Res.string.domain_issue_distance_reference_in_repeat
      EditorIssueMessage.DISTANCE_REFERENCE_NOT_LOCATION ->
        Res.string.domain_issue_distance_reference_not_location
      EditorIssueMessage.DISTANCE_MIN_INVALID -> Res.string.domain_issue_distance_min_invalid
      EditorIssueMessage.DISTANCE_MAX_INVALID -> Res.string.domain_issue_distance_max_invalid
      EditorIssueMessage.DISTANCE_MIN_MORE_THAN_MAX ->
        Res.string.domain_issue_distance_min_more_than_max
      EditorIssueMessage.CUSTOM_EXPRESSION_INVALID ->
        Res.string.domain_issue_custom_expression_invalid
      EditorIssueMessage.SAVE_TO_TARGET_MISSING -> Res.string.domain_issue_save_to_target_missing
      EditorIssueMessage.SAVE_TO_TARGET_DELETED -> Res.string.domain_issue_save_to_target_deleted
      EditorIssueMessage.SAVE_TO_RESERVED_NAME_MAP_LAYER ->
        Res.string.domain_issue_save_to_reserved_name_map_layer
      EditorIssueMessage.SAVE_TO_RESERVED_NAME_DATA_TABLE ->
        Res.string.domain_issue_save_to_reserved_name_data_table
      EditorIssueMessage.SAVE_TO_ID_QUESTION_MISSING_MAP_LAYER ->
        Res.string.domain_issue_save_to_id_question_missing_map_layer
      EditorIssueMessage.SAVE_TO_ID_QUESTION_MISSING_DATA_TABLE ->
        Res.string.domain_issue_save_to_id_question_missing_data_table
      EditorIssueMessage.SAVE_TO_MATCH_PROPERTY_MISSING ->
        Res.string.domain_issue_save_to_match_property_missing
      EditorIssueMessage.SAVE_TO_GEOMETRY_QUESTION_REQUIRED ->
        Res.string.domain_issue_save_to_geometry_question_required
      EditorIssueMessage.SAVE_TO_UNKNOWN_PROPERTY ->
        Res.string.domain_issue_save_to_unknown_property
      EditorIssueMessage.SAVE_TO_PROPERTY_NOT_UPDATABLE ->
        Res.string.domain_issue_save_to_property_not_updatable
      EditorIssueMessage.SAVE_TO_PROPERTY_ALREADY_UPDATED ->
        Res.string.domain_issue_save_to_property_already_updated
      EditorIssueMessage.SAVE_TO_NO_FIELDS -> Res.string.domain_issue_save_to_no_fields
      EditorIssueMessage.SAVE_TO_TYPE_MISMATCH -> Res.string.domain_issue_save_to_type_mismatch
      EditorIssueMessage.STATUS_DEFAULT_LABEL_MISSING ->
        Res.string.domain_issue_status_default_label_missing
      EditorIssueMessage.STATUS_RULE_LABEL_MISSING ->
        Res.string.domain_issue_status_rule_label_missing
      EditorIssueMessage.STATUS_RULE_MIN_SUBMISSIONS ->
        Res.string.domain_issue_status_rule_min_submissions
      EditorIssueMessage.STATUS_RULE_QUESTION_MISSING ->
        Res.string.domain_issue_status_rule_question_missing
      EditorIssueMessage.STATUS_RULE_OPERATOR_NOT_APPLICABLE ->
        Res.string.domain_issue_status_rule_operator_not_applicable
      EditorIssueMessage.STATUS_RULE_VALUE_MISSING ->
        Res.string.domain_issue_status_rule_value_missing
      EditorIssueMessage.STATUS_RULE_VALUE_NOT_NUMBER ->
        Res.string.domain_issue_status_rule_value_not_number
      EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING_MAP_LAYER ->
        Res.string.domain_issue_status_rule_property_missing_map_layer
      EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING_DATA_TABLE ->
        Res.string.domain_issue_status_rule_property_missing_data_table
      EditorIssueMessage.STATUS_RULE_PROPERTY_MISSING ->
        Res.string.domain_issue_status_rule_property_missing
      EditorIssueMessage.STATUS_RULE_PROPERTY_UNWRITTEN ->
        Res.string.domain_issue_status_rule_property_unwritten
      EditorIssueMessage.STATUS_RULE_PROPERTY_OPERATOR_NOT_APPLICABLE ->
        Res.string.domain_issue_status_rule_property_operator_not_applicable
      EditorIssueMessage.STATUS_RULE_PROPERTY_VALUE_MISSING ->
        Res.string.domain_issue_status_rule_property_value_missing
      EditorIssueMessage.STATUS_RULE_PROPERTY_VALUE_NOT_NUMBER ->
        Res.string.domain_issue_status_rule_property_value_not_number
      EditorIssueMessage.CONCEPT_NOT_IN_DICTIONARY ->
        Res.string.domain_warning_concept_not_in_dictionary
      EditorIssueMessage.CONCEPT_TYPE_MISMATCH -> Res.string.domain_warning_concept_type_mismatch
      EditorIssueMessage.CONCEPT_DEPRECATED -> Res.string.domain_warning_concept_deprecated
      EditorIssueMessage.CONCEPT_LINKED_TWICE -> Res.string.domain_warning_concept_linked_twice
      EditorIssueMessage.DATASET_ID_INVALID -> Res.string.domain_issue_dataset_id_invalid
      EditorIssueMessage.DATASET_ID_DUPLICATE -> Res.string.domain_issue_dataset_id_duplicate
      EditorIssueMessage.DATASET_PROPERTY_NAME_INVALID ->
        Res.string.domain_issue_dataset_property_name_invalid
      EditorIssueMessage.DATASET_PROPERTY_NAME_DUPLICATE ->
        Res.string.domain_issue_dataset_property_name_duplicate
      EditorIssueMessage.DATASET_KEY_PROPERTY_MISSING ->
        Res.string.domain_issue_dataset_key_property_missing
      EditorIssueMessage.DATASET_LABEL_PROPERTY_MISSING ->
        Res.string.domain_issue_dataset_label_property_missing
      EditorIssueMessage.DATASET_ROW_KEY_MISSING -> Res.string.domain_issue_dataset_row_key_missing
      EditorIssueMessage.DATASET_ROW_KEY_DUPLICATE ->
        Res.string.domain_issue_dataset_row_key_duplicate
      EditorIssueMessage.DATASET_ROW_VALUE_MISSING ->
        Res.string.domain_issue_dataset_row_value_missing
      EditorIssueMessage.DATASET_ROW_VALUE_WRONG_TYPE ->
        Res.string.domain_issue_dataset_row_value_wrong_type
      EditorIssueMessage.SHARING_ORGANIZATION_MISSING ->
        Res.string.domain_issue_sharing_organization_missing
    }

private val EditorIssueCountedMessage.resource: PluralStringResource
  get() =
    when (this) {
      EditorIssueCountedMessage.CONCEPT_UNMAPPED_CHOICES ->
        Res.plurals.domain_warning_concept_unmapped_choices
      EditorIssueCountedMessage.DATASET_ROW_GEOMETRY_TOO_SHORT ->
        Res.plurals.domain_issue_dataset_row_geometry_too_short
    }

private val DisplayLogicMessage.resource: StringResource
  get() =
    when (this) {
      DisplayLogicMessage.SOURCE_DELETED -> Res.string.domain_display_logic_source_deleted
      DisplayLogicMessage.SHOWN_IF_ANSWERED -> Res.string.domain_display_logic_shown_if_answered
      DisplayLogicMessage.SHOWN_IF -> Res.string.domain_display_logic_shown_if
    }

private val QuotaMessage.resource: StringResource
  get() =
    when (this) {
      QuotaMessage.MAX_SURVEYS_PER_OWNER -> Res.string.domain_quota_max_surveys_per_owner
    }

private val QuotaCountedMessage.resource: PluralStringResource
  get() =
    when (this) {
      QuotaCountedMessage.MAX_FORMS -> Res.plurals.domain_quota_max_forms
      QuotaCountedMessage.MAX_QUESTIONS -> Res.plurals.domain_quota_max_questions
      QuotaCountedMessage.MAX_PHOTO_QUESTIONS -> Res.plurals.domain_quota_max_photo_questions
      QuotaCountedMessage.MAX_PREDEFINED_ENTITIES ->
        Res.plurals.domain_quota_max_predefined_entities
      QuotaCountedMessage.MAX_GENERATED_ENTITIES -> Res.plurals.domain_quota_max_generated_entities
      QuotaCountedMessage.MAX_AREA_VERTICES -> Res.plurals.domain_quota_max_area_vertices
      QuotaCountedMessage.MAX_DATASET_POLYGON_VERTICES ->
        Res.plurals.domain_quota_max_dataset_polygon_vertices
      QuotaCountedMessage.MAX_COLLABORATORS -> Res.plurals.domain_quota_max_collaborators
      QuotaCountedMessage.MAX_SURVEYS_PER_USER -> Res.plurals.domain_quota_max_surveys_per_user
      QuotaCountedMessage.MAX_ADHOC_ENTITIES -> Res.plurals.domain_quota_max_adhoc_entities
      QuotaCountedMessage.MAX_SUBMISSIONS_PER_ENTITY ->
        Res.plurals.domain_quota_max_submissions_per_entity
    }

private val SubmissionNotice.resource: StringResource
  get() =
    when (this) {
      SubmissionNotice.ENTITY_CREATED -> Res.string.domain_notice_entity_created
      SubmissionNotice.STANDALONE_SUBMITTED -> Res.string.domain_notice_standalone_submitted
      SubmissionNotice.ENTITY_SUBMITTED -> Res.string.domain_notice_entity_submitted
    }
