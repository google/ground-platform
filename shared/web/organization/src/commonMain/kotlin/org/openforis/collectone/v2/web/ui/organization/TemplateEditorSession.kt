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
package org.openforis.collectone.v2.web.ui.organization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.ui.viewmodel.ViewModelDispatcher
import org.openforis.collectone.v2.app.ui.viewmodel.viewModelScopeOn
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorViewModel
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/**
 * One editing session of a library template on an organization's **Templates** tab: its Form, in
 * the same [FormEditorViewModel] that edits survey Forms (in template mode), plus the template's
 * own metadata (English description and the Purpose Packs listing it). Nothing is stored until
 * [save]; [close] stops the Form editor.
 *
 * @property templateId the template being edited, or `null` for a new one.
 */
internal class TemplateEditorSession(
  val templateId: String?,
  private val initialForm: EditorForm,
  private val initialDescription: String,
  private val initialPurposePackIds: Set<String>,
  library: FormLibraryContext,
  private val scope: CoroutineScope = viewModelScopeOn(ViewModelDispatcher),
) {
  /** The template's Form editor; the Form's title is the template's. */
  val formEditor: FormEditorViewModel = FormEditorViewModel.forTemplate(initialForm, library, scope)

  private val _description = MutableStateFlow(initialDescription)

  /** The template's English description. */
  val description: StateFlow<String> = _description.asStateFlow()

  private val _purposePackIds = MutableStateFlow(initialPurposePackIds)

  /** IDs of the organization's Purpose Packs that list the template. */
  val purposePackIds: StateFlow<Set<String>> = _purposePackIds.asStateFlow()

  init {
    // A new template starts on its Form properties, where its title and description go.
    if (templateId == null) formEditor.selectForm()
  }

  /** The Form as edited so far. */
  val form: EditorForm
    get() = formEditor.uiState.value.form

  /** Whether anything differs from when the session started (closing would lose it). */
  val isDirty: Boolean
    get() =
      form != initialForm ||
        _description.value != initialDescription ||
        _purposePackIds.value != initialPurposePackIds

  fun updateDescription(description: String) {
    _description.value = description
  }

  /** Lists the template in Purpose Pack [packId], or stops listing it there. */
  fun setPurposePack(packId: String, listed: Boolean) {
    _purposePackIds.update { if (listed) it + packId else it - packId }
  }

  /**
   * Saves the edits to [organizationId]'s library through [actions]: the template's Form, title,
   * description, and Purpose Packs. Returns why nothing was saved, or `null` once saving started.
   */
  fun save(actions: OrganizationLibraryActions, organizationId: String): OrganizationMessage? =
    actions.saveTemplateForm(
      organizationId = organizationId,
      templateId = templateId,
      form = form,
      description = _description.value,
      purposePackIds = _purposePackIds.value.toList(),
    )

  /** Stops the Form editor; the session can't be used afterwards. */
  fun close() = scope.cancel()

  companion object {
    /** Form ID of a new template's Form until it's saved (saving gives it the template's). */
    private const val NEW_TEMPLATE_FORM_ID = "template"

    /**
     * A session editing [template] of [library]'s organization, or a new template when it's `null`
     * (starting, like **Blank form** in a survey, with one text question). Questions link to the
     * concepts the organization's templates may use: its own and global ones, or only global ones
     * in the global library.
     */
    fun of(
      template: FormTemplate?,
      library: OrganizationLibraryUiState,
      organizationName: String?,
    ): TemplateEditorSession =
      TemplateEditorSession(
        templateId = template?.id,
        initialForm =
          template?.form?.let { it.copy(title = template.title.text.ifBlank { it.title }) }
            ?: EditorFormTemplates.blank(formId = NEW_TEMPLATE_FORM_ID, title = ""),
        initialDescription = template?.description?.get(LocalizedText.DEFAULT_LANGUAGE).orEmpty(),
        initialPurposePackIds =
          template
            ?.let { t -> library.ownPurposePacks.filter { t.id in it.formTemplateIds } }
            .orEmpty()
            .map { it.id }
            .toSet(),
        library =
          FormLibraryContext(
            library = library.resolvedLibrary,
            organizationName = organizationName.takeUnless { library.isGlobalLibrary },
          ),
      )
  }
}
