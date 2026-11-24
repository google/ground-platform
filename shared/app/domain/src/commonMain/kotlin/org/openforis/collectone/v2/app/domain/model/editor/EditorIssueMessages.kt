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
package org.openforis.collectone.v2.app.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.CountedMessageKey
import org.openforis.collectone.v2.app.domain.model.MessageKey

/**
 * A problem or advice the Form and Survey editors report (an [EditorIssue] or a [DatasetIssue]).
 */
enum class EditorIssueMessage : MessageKey {
  FORM_MISSING_ID,
  NAME_REQUIRED,
  NAME_INVALID,
  NAME_DUPLICATE,
  LABEL_EMPTY,
  CHOOSE_CHOICE_DATA_TABLE,
  CHOOSE_CHOICE_MAP_LAYER,
  CHOICE_DATASET_MISSING,
  CHOICE_DATASET_GENERATED,
  CHOICE_DATASET_NO_CREATION_FORM_MAP_LAYER,
  CHOICE_DATASET_NO_CREATION_FORM_DATA_TABLE,
  ADD_CHOICE,
  CHOICE_VALUE_INVALID,
  CHOICE_VALUE_DUPLICATE,
  DISPLAY_LOGIC_SOURCE_DELETED,
  DISPLAY_LOGIC_SOURCE_LATER,
  /** `%1$s`: name of a question inside a repeated section that display logic outside it uses. */
  DISPLAY_LOGIC_SOURCE_IN_REPEAT,
  /** `%1$s`: name of a question inside the section whose display logic depends on it. */
  SECTION_DISPLAY_LOGIC_SOURCE_INSIDE,
  DISPLAY_LOGIC_OPERATOR_NOT_APPLICABLE,
  DISPLAY_LOGIC_VALUE_MISSING,
  DISPLAY_LOGIC_VALUE_NOT_NUMBER,
  CASCADE_PARENT_DELETED,
  CASCADE_PARENT_LATER,
  CASCADE_PARENT_NOT_CHOICE,
  CASCADE_CHOICE_PARENT_VALUE_UNKNOWN,
  WEB_INCOMPATIBLE_GEOMETRY,
  GOOGLE_EARTH_INCOMPATIBLE_QUESTION,
  MIN_NOT_NUMBER,
  MAX_NOT_NUMBER,
  MIN_MORE_THAN_MAX,
  MIN_LENGTH_INVALID,
  MAX_LENGTH_INVALID,
  MIN_LENGTH_MORE_THAN_MAX,
  MIN_SELECTIONS_INVALID,
  MAX_SELECTIONS_INVALID,
  MIN_SELECTIONS_MORE_THAN_MAX,
  MIN_SELECTIONS_MORE_THAN_CHOICES,
  CUSTOM_PATTERN_MISSING,
  CUSTOM_PATTERN_INVALID,
  CUSTOM_PATTERN_QUOTES,
  DATE_RANGE_MISSING,
  DATE_RANGE_REVERSED,
  COMPARISON_TARGET_MISSING,
  COMPARISON_TARGET_DELETED,
  /** `%1$s`: name of a question inside a repeated section that a comparison outside it uses. */
  COMPARISON_TARGET_IN_REPEAT,
  /** `%1$s`: name of a comparison target whose type doesn't match the question. */
  COMPARISON_TARGET_WRONG_TYPE,
  COMPARISON_OPERATOR_NOT_APPLICABLE,
  DISTANCE_REFERENCE_MISSING,
  DISTANCE_REFERENCE_DELETED,
  /** `%1$s`: name of a location question inside a repeated section. */
  DISTANCE_REFERENCE_IN_REPEAT,
  /** `%1$s`: name of a distance reference that isn't a Location question. */
  DISTANCE_REFERENCE_NOT_LOCATION,
  DISTANCE_MIN_INVALID,
  DISTANCE_MAX_INVALID,
  DISTANCE_MIN_MORE_THAN_MAX,
  CUSTOM_EXPRESSION_INVALID,
  SAVE_TO_TARGET_MISSING,
  SAVE_TO_TARGET_DELETED,
  SAVE_TO_RESERVED_NAME_MAP_LAYER,
  SAVE_TO_RESERVED_NAME_DATA_TABLE,
  SAVE_TO_ID_QUESTION_MISSING_MAP_LAYER,
  SAVE_TO_ID_QUESTION_MISSING_DATA_TABLE,
  SAVE_TO_MATCH_PROPERTY_MISSING,
  SAVE_TO_GEOMETRY_QUESTION_REQUIRED,
  SAVE_TO_UNKNOWN_PROPERTY,
  SAVE_TO_PROPERTY_NOT_UPDATABLE,
  SAVE_TO_PROPERTY_ALREADY_UPDATED,
  SAVE_TO_NO_FIELDS,
  SAVE_TO_TYPE_MISMATCH,
  STATUS_DEFAULT_LABEL_MISSING,
  STATUS_RULE_LABEL_MISSING,
  STATUS_RULE_MIN_SUBMISSIONS,
  STATUS_RULE_QUESTION_MISSING,
  STATUS_RULE_OPERATOR_NOT_APPLICABLE,
  STATUS_RULE_VALUE_MISSING,
  STATUS_RULE_VALUE_NOT_NUMBER,
  STATUS_RULE_PROPERTY_MISSING_MAP_LAYER,
  STATUS_RULE_PROPERTY_MISSING_DATA_TABLE,
  STATUS_RULE_PROPERTY_MISSING,
  STATUS_RULE_PROPERTY_UNWRITTEN,
  STATUS_RULE_PROPERTY_OPERATOR_NOT_APPLICABLE,
  STATUS_RULE_PROPERTY_VALUE_MISSING,
  STATUS_RULE_PROPERTY_VALUE_NOT_NUMBER,
  CONCEPT_NOT_IN_DICTIONARY,
  CONCEPT_TYPE_MISMATCH,
  CONCEPT_DEPRECATED,
  CONCEPT_LINKED_TWICE,
  DATASET_ID_INVALID,
  DATASET_ID_DUPLICATE,
  DATASET_PROPERTY_NAME_INVALID,
  DATASET_PROPERTY_NAME_DUPLICATE,
  DATASET_KEY_PROPERTY_MISSING,
  DATASET_LABEL_PROPERTY_MISSING,
  DATASET_ROW_KEY_MISSING,
  DATASET_ROW_KEY_DUPLICATE,
  DATASET_ROW_VALUE_MISSING,
  DATASET_ROW_VALUE_WRONG_TYPE,
  SHARING_ORGANIZATION_MISSING,
}

/** An [EditorIssueMessage] whose wording depends on a count. */
enum class EditorIssueCountedMessage : CountedMessageKey {
  CONCEPT_UNMAPPED_CHOICES,
  DATASET_ROW_GEOMETRY_TOO_SHORT,
}

/** The plain-language summary of a question's display logic ([EditorForm.relevanceSummary]). */
enum class DisplayLogicMessage : MessageKey {
  /** The question it depends on was deleted. */
  SOURCE_DELETED,
  /** `%1$s`: the source question's label. */
  SHOWN_IF_ANSWERED,
  /** `%1$s`: the source question's label; `%2$s`: the comparison; `%3$s`: the value. */
  SHOWN_IF,
}
