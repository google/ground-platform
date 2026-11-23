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

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.ui.text.OrganizationMessage

/** Editing library templates in the shared Form editor ([TemplateEditorSession]). */
class TemplateEditorSessionTest {
  private val kfs = "00000013-31cc-5c95-90ea-379f624a5c72"

  private val template =
    FormTemplate(
      id = "org.$kfs.plot_audit",
      organizationId = kfs,
      title = LocalizedText.of("en" to "Plot audit", "fr" to "Audit de parcelle"),
      description = LocalizedText.en("Yearly"),
      form =
        EditorForm(
          formId = "form-plot_audit",
          title = "Old form title",
          questions =
            listOf(
              EditorQuestion("q1", "plot", EditorQuestionType.POLYGON, "Plot"),
              EditorQuestion("q2", "farmer_name", EditorQuestionType.TEXT, "Farmer"),
            ),
        ),
      status = LibraryStatus.STABLE,
    )

  private val ownPack =
    PurposePack(
      id = "org.$kfs.shade",
      organizationId = kfs,
      title = LocalizedText.en("Shade"),
      formTemplateIds = listOf(template.id),
    )

  // Global packs can't list organization templates, even if their data says so.
  private val globalPack =
    PurposePack(
      id = "eudr_due_diligence",
      organizationId = "",
      title = LocalizedText.en("EUDR"),
      formTemplateIds = listOf(template.id),
    )

  private val concept =
    LibraryConcept(
      id = "org.$kfs.shade_tree_count",
      organizationId = kfs,
      label = LocalizedText.en("Shade trees"),
      dataType = ConceptDataType.INTEGER,
    )

  private val library =
    OrganizationLibraryUiState(
      organizationId = kfs,
      canEdit = true,
      formTemplates = listOf(LibraryEntryRow(template, isGlobal = false, canEdit = true)),
      purposePacks =
        listOf(
          LibraryEntryRow(ownPack, isGlobal = false, canEdit = true),
          LibraryEntryRow(globalPack, isGlobal = true, canEdit = false),
        ),
      // The template itself is in the library its Form editor resolves against.
      resolvedLibrary =
        ResolvedLibrary(kfs, concepts = listOf(concept), formTemplates = listOf(template)),
    )

  private val sessions = mutableListOf<TemplateEditorSession>()

  private fun session(template: FormTemplate? = this.template) =
    TemplateEditorSession.of(template, library, organizationName = "Kenya Forest Service").also {
      sessions += it
    }

  @AfterTest fun closeSessions() = sessions.forEach { it.close() }

  @Test
  fun of_anExistingTemplate_opensItsFormInTemplateMode_withItsMetadata() {
    val session = session()
    val ui = session.formEditor.uiState.value

    assertEquals(template.id, session.templateId)
    assertTrue(ui.isTemplate)
    // The Form's title is the template's.
    assertEquals("Plot audit", ui.form.title)
    assertEquals(template.form.questions, ui.form.questions)
    assertEquals("Yearly", session.description.value)
    assertEquals(setOf(ownPack.id), session.purposePackIds.value)
    assertEquals(listOf(ownPack), library.ownPurposePacks)
    assertEquals(library.resolvedLibrary, ui.library.library)
    assertEquals("Kenya Forest Service", ui.library.organizationName)
    assertFalse(ui.library.canAddToDictionary)
    assertFalse(session.isDirty)
  }

  @Test
  fun of_noTemplate_startsANewOneOnItsFormProperties() {
    val session = session(template = null)
    val ui = session.formEditor.uiState.value

    assertNull(session.templateId)
    assertTrue(ui.isTemplate)
    assertEquals("", ui.form.title)
    assertEquals(listOf(EditorQuestionType.TEXT), ui.form.questions.map { it.type })
    assertNull(ui.selectedKey)
    assertEquals("", session.description.value)
    assertEquals(emptySet(), session.purposePackIds.value)
    assertFalse(session.isDirty)
  }

  @Test
  fun templateMode_hidesSurveyOnlyState_andSaysWhichDatasetSurveysGet() {
    val session = session()
    // Even edited, the template isn't offered updates from itself.
    session.formEditor.deleteQuestion("q2")
    val ui = session.formEditor.uiState.value

    assertNull(ui.templateUpdateDiff)
    assertTrue(ui.datasets.isEmpty())
    assertTrue(ui.issues.isEmpty(), ui.issues.toString())
    assertEquals(DatasetKind.MAP_LAYER to GeometryType.POLYGON, ui.templateDataset)

    session.formEditor.deleteQuestion("q1")
    assertEquals(
      DatasetKind.DATA_TABLE,
      session.formEditor.uiState.value.templateDataset.first,
    )
  }

  @Test
  fun edits_makeTheSessionDirty_andSaveSendsThemAll() {
    val session = session()
    session.formEditor.updateTitle("Plot audit v2")
    session.formEditor.addQuestion(EditorQuestionType.NUMBER, null)
    session.updateDescription("Every season")
    session.setPurposePack(ownPack.id, listed = false)
    assertTrue(session.isDirty)

    val actions = RecordingActions()
    assertNull(session.save(actions, kfs))

    val saved = actions.saved.single()
    assertEquals(kfs, saved.organizationId)
    assertEquals(template.id, saved.templateId)
    assertEquals("Plot audit v2", saved.form.title)
    assertEquals(3, saved.form.questions.size)
    assertEquals("Every season", saved.description)
    assertEquals(emptyList(), saved.purposePackIds)
  }

  @Test
  fun save_returnsWhyNothingWasSaved() {
    val session = session(template = null)
    val error = OrganizationMessage.Text("Enter a title.")
    assertEquals(error, session.save(RecordingActions(error), kfs))
  }

  @Test
  fun undoingEveryEdit_leavesTheSessionClean() {
    val session = session()
    session.formEditor.updateTitle("Changed")
    session.formEditor.undo()
    assertFalse(session.isDirty)
    // Survey-only settings never reach a template.
    assertEquals(EditorSaveTo(), session.form.saveTo)
    assertEquals(SaveToMode.CREATE, session.form.saveTo.mode)
  }

  private data class SavedTemplate(
    val organizationId: String,
    val templateId: String?,
    val form: EditorForm,
    val description: String,
    val purposePackIds: List<String>,
  )

  /** Records [saveTemplateForm] calls and answers them with [result]. */
  private class RecordingActions(private val result: OrganizationMessage? = null) :
    OrganizationLibraryActions {
    val saved = mutableListOf<SavedTemplate>()

    override fun saveTemplateForm(
      organizationId: String,
      templateId: String?,
      form: EditorForm,
      description: String,
      purposePackIds: List<String>,
    ): OrganizationMessage? {
      saved += SavedTemplate(organizationId, templateId, form, description, purposePackIds)
      return result
    }

    override fun setDictionaryQuery(query: String) = Unit

    override fun saveConcept(concept: LibraryConcept, isNew: Boolean): OrganizationMessage? = null

    override fun deleteConcept(organizationId: String, conceptId: String): OrganizationMessage? =
      null

    override fun renameTemplate(
      organizationId: String,
      templateId: String,
      title: String,
      description: String,
    ): OrganizationMessage? = null

    override fun deleteTemplate(organizationId: String, templateId: String) = Unit

    override fun renamePurposePack(
      organizationId: String,
      packId: String,
      title: String,
      description: String,
    ): OrganizationMessage? = null

    override fun deletePurposePack(organizationId: String, packId: String) = Unit

    override fun setGlobalEntryHidden(
      organizationId: String,
      globalEntryId: String,
      hidden: Boolean,
    ) = Unit
  }
}
