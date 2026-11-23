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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.app.domain.model.CountedMessageKey
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.MessageKey
import org.openforis.collectone.v2.web.domain.model.OrganizationError
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportCountedNote
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportNote
import org.openforis.collectone.v2.web.domain.model.editor.SamplingMessage
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_error_already_invited
import org.openforis.collectone.v2.web.resources.org_error_already_member
import org.openforis.collectone.v2.web.resources.org_error_already_requested
import org.openforis.collectone.v2.web.resources.org_error_concept_missing
import org.openforis.collectone.v2.web.resources.org_error_concept_not_draft
import org.openforis.collectone.v2.web.resources.org_error_enter_name
import org.openforis.collectone.v2.web.resources.org_error_invalid_email
import org.openforis.collectone.v2.web.resources.org_error_invite_all_users
import org.openforis.collectone.v2.web.resources.org_error_not_found
import org.openforis.collectone.v2.web.resources.surveyeditor_import_ambiguous_dates
import org.openforis.collectone.v2.web.resources.surveyeditor_import_file_empty
import org.openforis.collectone.v2.web.resources.surveyeditor_import_id_column_not_unique
import org.openforis.collectone.v2.web.resources.surveyeditor_import_no_data_rows
import org.openforis.collectone.v2.web.resources.surveyeditor_import_ragged_rows
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_area_missing
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_cant_generate_design
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_generate_failed
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_generate_failed_reason
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_no_design
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_no_plots_fit
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_deleted
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_no_polygons
import org.openforis.collectone.v2.web.resources.surveyeditor_sampling_strata_not_polygon

// Localizes the DomainMessages of web-only domain code, whose keys shared/app's
// DomainMessage.toUiText() doesn't know, into the web console's presentation text types.

/** This organization refusal as an [OrganizationMessage]. */
fun DomainMessage.toOrganizationMessage(): OrganizationMessage =
  when (this) {
    is DomainMessage.Text -> OrganizationMessage.Resource(key.resource(), args)
    else -> throw IllegalArgumentException("Organization messages are plain text: $this")
  }

/** This Survey editor message as [EditorText]. */
fun DomainMessage.toEditorText(): EditorText =
  when (this) {
    is DomainMessage.Text -> EditorText.Resource(key.resource(), args)
    is DomainMessage.Counted -> EditorText.Plural(key.resource(), quantity, args)
    else -> throw IllegalArgumentException("No editor text for the message $this")
  }

/** Why sample plots couldn't be generated, as [EditorText]. */
fun GenerateSamplePlotsUseCase.Result.Failure.toEditorText(): EditorText =
  when (this) {
    is GenerateSamplePlotsUseCase.Result.Failed -> message.toEditorText()
    is GenerateSamplePlotsUseCase.Result.DesignRejected -> EditorText.Verbatim(explanation)
  }

private fun MessageKey.resource(): StringResource =
  when (this) {
    is OrganizationError -> resource
    is SamplingMessage -> resource
    is DataTableImportNote -> resource
    else -> throw IllegalArgumentException("No string resource for the message $this")
  }

private fun CountedMessageKey.resource(): PluralStringResource =
  when (this) {
    is DataTableImportCountedNote -> resource
    else -> throw IllegalArgumentException("No plural resource for the message $this")
  }

private val OrganizationError.resource: StringResource
  get() =
    when (this) {
      OrganizationError.INVALID_EMAIL -> Res.string.org_error_invalid_email
      OrganizationError.ORGANIZATION_NOT_FOUND -> Res.string.org_error_not_found
      OrganizationError.EVERYONE_ALREADY_MEMBER -> Res.string.org_error_invite_all_users
      OrganizationError.ALREADY_MEMBER -> Res.string.org_error_already_member
      OrganizationError.ALREADY_INVITED -> Res.string.org_error_already_invited
      OrganizationError.ALREADY_REQUESTED -> Res.string.org_error_already_requested
      OrganizationError.NAME_REQUIRED -> Res.string.org_error_enter_name
      OrganizationError.CONCEPT_MISSING -> Res.string.org_error_concept_missing
      OrganizationError.CONCEPT_NOT_DRAFT -> Res.string.org_error_concept_not_draft
    }

private val SamplingMessage.resource: StringResource
  get() =
    when (this) {
      SamplingMessage.AREA_MISSING -> Res.string.surveyeditor_sampling_area_missing
      SamplingMessage.STRATA_DELETED -> Res.string.surveyeditor_sampling_strata_deleted
      SamplingMessage.STRATA_NOT_POLYGON -> Res.string.surveyeditor_sampling_strata_not_polygon
      SamplingMessage.STRATA_NO_POLYGONS -> Res.string.surveyeditor_sampling_strata_no_polygons
      SamplingMessage.NO_DESIGN -> Res.string.surveyeditor_sampling_no_design
      SamplingMessage.CANT_GENERATE_DESIGN -> Res.string.surveyeditor_sampling_cant_generate_design
      SamplingMessage.GENERATE_FAILED -> Res.string.surveyeditor_sampling_generate_failed
      SamplingMessage.GENERATE_FAILED_WITH_REASON ->
        Res.string.surveyeditor_sampling_generate_failed_reason
      SamplingMessage.NO_PLOTS_FIT -> Res.string.surveyeditor_sampling_no_plots_fit
    }

private val DataTableImportNote.resource: StringResource
  get() =
    when (this) {
      DataTableImportNote.FILE_EMPTY -> Res.string.surveyeditor_import_file_empty
      DataTableImportNote.NO_DATA_ROWS -> Res.string.surveyeditor_import_no_data_rows
      DataTableImportNote.ID_COLUMN_NOT_UNIQUE ->
        Res.string.surveyeditor_import_id_column_not_unique
      DataTableImportNote.AMBIGUOUS_DATES -> Res.string.surveyeditor_import_ambiguous_dates
    }

private val DataTableImportCountedNote.resource: PluralStringResource
  get() =
    when (this) {
      DataTableImportCountedNote.RAGGED_ROWS -> Res.plurals.surveyeditor_import_ragged_rows
    }
