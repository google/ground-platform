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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.repository.OrganizationRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.EditorSaveTo
import org.openforis.collectone.v2.app.domain.model.editor.SaveToMode

class ManageLibraryUseCaseTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE
  private val global = Organization.ALL_USERS_ID
  private val maya = SampleOrganizationsData.SIGNED_IN_EMAIL

  private class Fixture {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store)
    val organizations = OrganizationRepositoryImpl(store)
    val useCase = ManageLibraryUseCase(libraries)
  }

  private fun newConcept(id: String = "org.00000013-31cc-5c95-90ea-379f624a5c72.drying_days") =
    LibraryConcept(
      id = id,
      organizationId = kfs,
      label = LocalizedText.en("Drying days"),
      dataType = ConceptDataType.INTEGER,
      aggregation = ConceptAggregation.MEAN,
    )

  @Test
  fun canEdit_isLimitedToActiveManagers_includingAllUsersPlatformAdmins() = runNow {
    val f = Fixture()
    val allUsers = f.organizations.getOrganization(global)!!
    val kenya = f.organizations.getOrganization(kfs)!!
    assertTrue(f.useCase.canEdit(allUsers, maya))
    assertFalse(f.useCase.canEdit(allUsers, "field.lead@example.org"))
    assertTrue(f.useCase.canEdit(kenya, maya))
    assertFalse(f.useCase.canEdit(kenya, "field.lead@example.org"))
  }

  @Test
  fun conceptId_usesTheLibrarysIdFormat() {
    val useCase = Fixture().useCase
    assertEquals("org.00000013-31cc-5c95-90ea-379f624a5c72.x", useCase.conceptId(kfs, " x "))
    assertEquals("eudr.x", useCase.conceptId(global, "x", vocabulary = "eudr"))
  }

  @Test
  fun saveConcept_createsThenVersionsContentChanges() = runNow {
    val f = Fixture()
    val library = f.libraries.getLibrary(kfs)
    val concept = newConcept()
    assertNull(f.useCase.conceptError(concept, library, isNew = true))
    f.useCase.saveConcept(concept)
    val saved = f.libraries.getLibrary(kfs).concept(concept.id)!!
    assertEquals(1, saved.version)

    // Creating it again is refused.
    assertNotNull(f.useCase.conceptError(concept, f.libraries.getLibrary(kfs), isNew = true))

    // A new translation increments the version; a status-only change doesn't.
    f.useCase.saveConcept(saved.copy(label = saved.label.with("sw", "Siku za kukausha")))
    assertEquals(2, f.libraries.getLibrary(kfs).concept(concept.id)!!.version)
    f.useCase.setConceptStatus(kfs, concept.id, LibraryStatus.STABLE)
    val stable = f.libraries.getLibrary(kfs).concept(concept.id)!!
    assertEquals(LibraryStatus.STABLE, stable.status)
    assertEquals(2, stable.version)
    f.useCase.saveConcept(stable.copy(status = LibraryStatus.DEPRECATED))
    assertEquals(2, f.libraries.getLibrary(kfs).concept(concept.id)!!.version)
  }

  @Test
  fun conceptError_validatesIdsLabelsAndCodeLists() = runNow {
    val f = Fixture()
    val library = f.libraries.getLibrary(kfs)
    // No shadowing of global IDs.
    assertNotNull(f.useCase.conceptError(newConcept("eudr.commodity"), library, isNew = true))
    assertNotNull(
      f.useCase.conceptError(newConcept().copy(label = LocalizedText()), library, isNew = true)
    )
    val select = newConcept().copy(dataType = ConceptDataType.SELECT_ONE)
    assertEquals(
      "Add at least one value to the list.",
      f.useCase.conceptError(select, library, isNew = true),
    )
    val badCode = select.copy(codeList = listOf(CodeListItem("Bad Code", LocalizedText.en("Bad"))))
    assertNotNull(f.useCase.conceptError(badCode, library, isNew = true))
    val dupes =
      select.copy(
        codeList =
          listOf(CodeListItem("a", LocalizedText.en("A")), CodeListItem("a", LocalizedText.en("B")))
      )
    assertEquals(
      "List values must be unique.",
      f.useCase.conceptError(dupes, library, isNew = true),
    )
    val noLabel = select.copy(codeList = listOf(CodeListItem("a", LocalizedText())))
    assertNotNull(f.useCase.conceptError(noLabel, library, isNew = true))
    // Updating a concept that doesn't exist.
    assertNotNull(f.useCase.conceptError(newConcept(), library, isNew = false))
  }

  @Test
  fun conceptError_enforcesVersioningRulesOnStableConcepts() = runNow {
    val f = Fixture()
    val library = f.libraries.getLibrary(global)
    val commodity = library.concept("eudr.commodity")!!
    // Removing a list value isn't allowed.
    val removed = commodity.copy(codeList = commodity.codeList.drop(1))
    assertTrue(
      f.useCase.conceptError(removed, library, isNew = false)!!.contains("can't be removed")
    )
    // Adding one is.
    val added =
      commodity.copy(codeList = commodity.codeList + CodeListItem("tea", LocalizedText.en("Tea")))
    assertNull(f.useCase.conceptError(added, library, isNew = false))
    // Changing meaning (type, unit, aggregation) of a stable concept needs a new ID.
    val area = library.concept("core.area_ha")!!
    assertNotNull(f.useCase.conceptError(area.copy(unit = "m2"), library, isNew = false))
    assertNotNull(
      f.useCase.conceptError(
        area.copy(aggregation = ConceptAggregation.MEAN),
        library,
        isNew = false,
      )
    )
    // Drafts can still change.
    val draft =
      f.libraries
        .getLibrary(kfs)
        .concept("org.00000013-31cc-5c95-90ea-379f624a5c72.shade_tree_count")!!
    assertEquals(LibraryStatus.DRAFT, draft.status)
    assertNull(
      f.useCase.conceptError(
        draft.copy(dataType = ConceptDataType.DECIMAL),
        f.libraries.getLibrary(kfs),
        isNew = false,
      )
    )
  }

  @Test
  fun deleteConcept_onlyDeletesDrafts() = runNow {
    val f = Fixture()
    val library = f.libraries.getLibrary(kfs)
    val stableId = "org.00000013-31cc-5c95-90ea-379f624a5c72.cherry_delivery_kg"
    val draftId = "org.00000013-31cc-5c95-90ea-379f624a5c72.shade_tree_count"
    assertNotNull(f.useCase.deleteConceptError(library, stableId))
    assertNull(f.useCase.deleteConceptError(library, draftId))
    f.useCase.deleteConcept(kfs, stableId)
    f.useCase.deleteConcept(kfs, draftId)
    val after = f.libraries.getLibrary(kfs)
    assertNotNull(after.concept(stableId))
    assertNull(after.concept(draftId))
  }

  @Test
  fun templatesAndPacks_canBeRenamedAndDeleted() = runNow {
    val f = Fixture()
    val templateId = "org.00000013-31cc-5c95-90ea-379f624a5c72.coop_member_plot_audit"
    val packId = "org.00000013-31cc-5c95-90ea-379f624a5c72.coop_certification_audit"
    assertEquals("Enter a title.", f.useCase.titleError(" "))
    f.useCase.renameTemplate(kfs, templateId, " Plot audit ", "New description")
    val renamed = f.libraries.getLibrary(kfs).formTemplate(templateId)!!
    assertEquals("Plot audit", renamed.title.text)
    assertEquals("New description", renamed.description.text)

    f.useCase.renamePurposePack(kfs, packId, "Certification", "")
    assertEquals("Certification", f.libraries.getLibrary(kfs).purposePack(packId)!!.title.text)

    // Deleting a template removes it from the organization's packs.
    f.useCase.deleteTemplate(kfs, templateId)
    val afterTemplate = f.libraries.getLibrary(kfs)
    assertNull(afterTemplate.formTemplate(templateId))
    assertEquals(
      listOf("eudr_plot_registration"),
      afterTemplate.purposePack(packId)!!.formTemplateIds,
    )

    f.useCase.deletePurposePack(kfs, packId)
    assertNull(f.libraries.getLibrary(kfs).purposePack(packId))
  }

  @Test
  fun setGlobalEntryHidden_onlyHidesGlobalEntriesInOtherOrganizations() = runNow {
    val f = Fixture()
    assertNotNull(f.useCase.setGlobalEntryHidden(kfs, "ferm_restoration", hidden = true))
    assertEquals(
      setOf("ferm_restoration"),
      f.libraries.getLibrary(kfs).settings.hiddenGlobalEntryIds,
    )
    f.useCase.setGlobalEntryHidden(kfs, "ferm_restoration", hidden = false)
    assertTrue(f.libraries.getLibrary(kfs).settings.hiddenGlobalEntryIds.isEmpty())
    // "All users" can't hide its own entries, and organization IDs can't be hidden.
    assertNull(f.useCase.setGlobalEntryHidden(global, "ferm_restoration", hidden = true))
    assertNull(
      f.useCase.setGlobalEntryHidden(
        kfs,
        "org.00000013-31cc-5c95-90ea-379f624a5c72.coop_certification_audit",
        true,
      )
    )
  }

  @Test
  fun builders_createAndEditFormTemplatesAndPurposePacks() = runNow {
    val f = Fixture()
    val library = f.libraries.getLibrary(kfs)

    val builtTemplate =
      f.useCase.newTemplate(
        library = library,
        organizationId = kfs,
        title = " Shade Audit ",
        description = " Canopy check ",
        questions = emptyList(),
      )
    assertEquals("org.$kfs.shade_audit", builtTemplate.id)
    assertEquals("Shade Audit", builtTemplate.title.text)

    val builtPack =
      f.useCase.newPurposePack(
        library = library,
        organizationId = kfs,
        title = " Agroforestry Pack ",
        description = " Pack description ",
        icon = "forest",
        pillar = null,
        formTemplateIds = listOf(builtTemplate.id),
      )
    assertEquals("org.$kfs.agroforestry_pack", builtPack.id)
    assertEquals(listOf(builtTemplate.id), builtPack.formTemplateIds)

    val editedPack =
      f.useCase.editedPurposePack(
        pack = builtPack,
        title = "Updated Pack",
        description = "   ",
        icon = "park",
        pillar = null,
        status = LibraryStatus.DEPRECATED,
        formTemplateIds = listOf("eudr_plot_registration"),
      )
    assertEquals("Updated Pack", editedPack.title.text)
    assertEquals("", editedPack.description.text)
    assertEquals(LibraryStatus.DEPRECATED, editedPack.status)
    assertEquals(listOf("eudr_plot_registration"), editedPack.formTemplateIds)
  }

  @Test
  fun editedTemplate_createsOrUpdatesFromTheFormEditor_andSavesItsPurposePacks() = runNow {
    val f = Fixture()
    val pack =
      f.useCase.newPurposePack(
        f.libraries.getLibrary(kfs),
        kfs,
        "Shade",
        description = "",
        icon = "",
        pillar = null,
        formTemplateIds = emptyList(),
      )
    f.useCase.savePurposePack(pack)
    val form =
      EditorForm(
        formId = "template",
        title = " Shade audit ",
        questions = listOf(EditorQuestion("q1", "shade_trees", EditorQuestionType.NUMBER, "Shade")),
        saveTo = EditorSaveTo(mode = SaveToMode.UPDATE, targetDatasetId = "plots"),
      )

    // A new template gets an ID from its title, and the Form a portable copy.
    val created = f.useCase.editedTemplate(f.libraries.getLibrary(kfs), kfs, null, form, "Yearly")
    assertEquals("org.$kfs.shade_audit", created.id)
    assertEquals("Shade audit", created.title.text)
    assertEquals("Yearly", created.description.text)
    assertEquals("form-shade_audit", created.form.formId)
    assertEquals(EditorSaveTo(), created.form.saveTo)
    assertEquals(LibraryStatus.DRAFT, created.status)
    f.useCase.saveTemplate(created, listOf(pack.id))
    var library = f.libraries.getLibrary(kfs)
    assertEquals(created, library.formTemplate(created.id))
    assertEquals(listOf(created.id), library.purposePack(pack.id)?.formTemplateIds)

    // Editing it keeps its ID and Form ID, and can unlist it from the pack.
    val renamed = form.copy(title = "Canopy audit", questions = emptyList())
    val updated = f.useCase.editedTemplate(library, kfs, created.id, renamed, "")
    assertEquals(created.id, updated.id)
    assertEquals("form-shade_audit", updated.form.formId)
    assertEquals("Canopy audit", updated.title.text)
    assertEquals(LocalizedText(), updated.description)
    f.useCase.saveTemplate(updated, emptyList())
    library = f.libraries.getLibrary(kfs)
    assertEquals(1, library.formTemplates.count { it.id == created.id })
    assertEquals(emptyList(), library.formTemplate(created.id)?.form?.questions)
    assertEquals(emptyList(), library.purposePack(pack.id)?.formTemplateIds)
  }

  @Test
  fun templateFormError_needsATitleAndQuestionLabels() {
    val useCase = Fixture().useCase
    val form = EditorForm(formId = "t", title = "Audit", questions = emptyList())
    assertNull(useCase.templateFormError(form))
    assertEquals(
      ManageLibraryUseCase.TemplateFormError.MISSING_TITLE,
      useCase.templateFormError(form.copy(title = " ")),
    )
    assertEquals(
      ManageLibraryUseCase.TemplateFormError.UNLABELED_QUESTION,
      useCase.templateFormError(
        form.copy(questions = listOf(EditorQuestion("q1", "a", EditorQuestionType.TEXT, " ")))
      ),
    )
  }
}
