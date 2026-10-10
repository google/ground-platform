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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.CodeListItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptAggregation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization

class ManageLibraryUseCaseTest {
  private val kfs = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  private val global = Organization.ALL_USERS_ID
  private val maya = PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL

  private class Fixture {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store)
    val organizations = OrganizationRepositoryImpl(store)
    val useCase = ManageLibraryUseCase(libraries)
  }

  private fun newConcept(id: String = "org.org-kenya-forest-service.drying_days") =
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
    assertEquals("org.org-kenya-forest-service.x", useCase.conceptId(kfs, " x "))
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
      f.libraries.getLibrary(kfs).concept("org.org-kenya-forest-service.shade_tree_count")!!
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
    val stableId = "org.org-kenya-forest-service.cherry_delivery_kg"
    val draftId = "org.org-kenya-forest-service.shade_tree_count"
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
    val templateId = "org.org-kenya-forest-service.coop_member_plot_audit"
    val packId = "org.org-kenya-forest-service.coop_certification_audit"
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
        "org.org-kenya-forest-service.coop_certification_audit",
        true,
      )
    )
  }
}
