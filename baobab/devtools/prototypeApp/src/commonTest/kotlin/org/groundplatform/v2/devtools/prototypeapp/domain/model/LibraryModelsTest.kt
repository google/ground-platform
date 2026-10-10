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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType

class LibraryModelsTest {
  private val global = Organization.ALL_USERS_ID

  private fun concept(id: String, owner: String) =
    LibraryConcept(id, owner, LocalizedText.en(id), ConceptDataType.TEXT)

  @Test
  fun localizedText_fallsBackToBaseLanguageThenEnglishThenAny() {
    val text = LocalizedText.of("en" to "Coffee", "es" to "Café", "vi" to "")
    assertEquals("Café", text.get("es"))
    assertEquals("Café", text.get("es-MX"))
    assertEquals("Coffee", text.get("sw"))
    assertEquals("Coffee", text.get("vi")) // Blank translations are skipped.
    assertEquals("Kahawa", LocalizedText(mapOf("sw" to "Kahawa")).get("fr"))
    assertEquals("", LocalizedText().text)
    assertTrue(LocalizedText().isBlank)
    assertEquals(mapOf("en" to "Coffee", "es" to "Café"), text.values)
  }

  @Test
  fun localizedText_with_setsOrRemovesALanguage() {
    val text = LocalizedText.en("Coffee").with("fr", "Café")
    assertEquals(mapOf("en" to "Coffee", "fr" to "Café"), text.values)
    assertEquals(mapOf("en" to "Coffee"), text.with("fr", " ").values)
  }

  @Test
  fun ids_globalAndOrganizationFormats() {
    assertTrue(LibraryIds.isValidGlobalConceptId("eudr.commodity"))
    assertTrue(LibraryIds.isValidGlobalConceptId("core.area_ha"))
    assertFalse(LibraryIds.isValidGlobalConceptId("commodity"))
    assertFalse(LibraryIds.isValidGlobalConceptId("eudr.commodity.extra"))
    assertFalse(LibraryIds.isValidGlobalConceptId("org.commodity"))
    assertFalse(LibraryIds.isValidGlobalConceptId("EUDR.commodity"))
    assertFalse(LibraryIds.isValidGlobalConceptId("eudr.1commodity"))

    assertTrue(LibraryIds.isValidGlobalEntryId("eudr_due_diligence"))
    assertFalse(LibraryIds.isValidGlobalEntryId("eudr.due_diligence"))
    assertFalse(LibraryIds.isValidGlobalEntryId("org"))

    val id = LibraryIds.organizationEntryId("org-kenya", "cherry_kg")
    assertEquals("org.org-kenya.cherry_kg", id)
    assertTrue(LibraryIds.isValidOrganizationEntryId(id, "org-kenya"))
    assertFalse(LibraryIds.isValidOrganizationEntryId(id, "org-other"))
    assertFalse(LibraryIds.isValidOrganizationEntryId("org.org-kenya.Bad-Name", "org-kenya"))
    assertFalse(LibraryIds.isValidOrganizationEntryId("org.org-kenya.", "org-kenya"))

    assertEquals("org-kenya", LibraryIds.organizationIdOf(id))
    assertNull(LibraryIds.organizationIdOf("eudr.commodity"))
    assertEquals("cherry_kg", LibraryIds.nameOf(id))
    assertEquals("eudr", LibraryIds.vocabularyOf("eudr.commodity"))
    assertNull(LibraryIds.vocabularyOf(id))
    assertTrue(LibraryIds.isGlobalId("eudr.commodity"))
    assertFalse(LibraryIds.isGlobalId(id))
    assertTrue(LibraryIds.isGlobalLibrary(global))
    assertFalse(LibraryIds.isGlobalLibrary("org-kenya"))
  }

  @Test
  fun ids_errorsDependOnTheOwningLibrary() {
    assertNull(LibraryIds.conceptIdError("eudr.commodity", global))
    assertNotNull(LibraryIds.conceptIdError("org.org-kenya.x", global))
    assertNull(LibraryIds.conceptIdError("org.org-kenya.x", "org-kenya"))
    // No shadowing: an organization can't define a global-looking ID.
    assertNotNull(LibraryIds.conceptIdError("eudr.commodity", "org-kenya"))
    assertNull(LibraryIds.entryIdError("eudr_plots", global))
    assertNotNull(LibraryIds.entryIdError("eudr_plots", "org-kenya"))
    assertNull(LibraryIds.entryIdError("org.org-kenya.plots", "org-kenya"))
  }

  @Test
  fun nameFrom_slugifiesFoldedText() {
    assertEquals("cherry_delivered_kg", LibraryIds.nameFrom("Cherry delivered (kg)"))
    assertEquals("cay_trong", LibraryIds.nameFrom("Cây trồng"))
    assertEquals("n_4ha_plots", LibraryIds.nameFrom("4ha plots"))
    assertEquals("entry", LibraryIds.nameFrom("  ¿?  "))
    assertEquals("", LibraryIds.nameFrom("", fallback = ""))
  }

  @Test
  fun integrityError_enforcesOwnershipPrefixesAndUniqueness() {
    val org = "org-kenya"
    val ok = OrganizationLibrary(org, concepts = listOf(concept("org.org-kenya.a", org)))
    assertNull(ok.integrityError())
    assertNotNull(
      OrganizationLibrary(org, concepts = listOf(concept("org.org-kenya.a", "org-other")))
        .integrityError()
    )
    assertNotNull(
      OrganizationLibrary(org, concepts = listOf(concept("eudr.commodity", org))).integrityError()
    )
    assertNotNull(
      OrganizationLibrary(
          org,
          concepts = listOf(concept("org.org-kenya.a", org), concept("org.org-kenya.a", org)),
        )
        .integrityError()
    )
    // Templates and packs share the hidden-ID namespace, so their IDs can't collide.
    val template =
      FormTemplate("same", global, LocalizedText.en("T"), EditorForm("same", "T", emptyList()))
    val pack = PurposePack("same", global, LocalizedText.en("P"))
    assertNotNull(
      OrganizationLibrary(global, formTemplates = listOf(template), purposePacks = listOf(pack))
        .integrityError()
    )
    assertNull(OrganizationLibrary(global, formTemplates = listOf(template)).integrityError())
  }

  @Test
  fun conceptDataType_compatibility() {
    assertTrue(ConceptDataType.DECIMAL.isCompatibleWith(EditorQuestionType.INTEGER))
    assertFalse(ConceptDataType.INTEGER.isCompatibleWith(EditorQuestionType.DECIMAL))
    assertTrue(ConceptDataType.TEXT.isCompatibleWith(EditorQuestionType.LONG_TEXT))
    assertTrue(ConceptDataType.MEDIA.isCompatibleWith(EditorQuestionType.PHOTO))
    assertFalse(ConceptDataType.SELECT_ONE.isCompatibleWith(EditorQuestionType.TEXT))
    assertTrue(ConceptDataType.SELECT_MULTIPLE.hasCodeList)
    assertFalse(ConceptDataType.TEXT.hasCodeList)
    // Notes never link to concepts.
    assertTrue(ConceptDataType.entries.none { it.isCompatibleWith(EditorQuestionType.NOTE) })
  }

  @Test
  fun libraryText_foldsAccentsAndTokenizes() {
    assertEquals("cay trong", LibraryText.fold("Cây trồng"))
    assertEquals("cafe", LibraryText.fold("CAFÉ"))
    assertEquals("dau tuong", LibraryText.fold("Đậu tương"))
    assertEquals("strasse oeuvre", LibraryText.fold("Straße Œuvre"))
    assertEquals("cafe", LibraryText.fold("cafe\u0301")) // Decomposed accent.
    assertEquals(listOf("area", "ha"), LibraryText.tokens("Area (ha)"))
    assertEquals(listOf("ng", "ombe"), LibraryText.tokens("Ng'ombe"))
    assertEquals(emptyList(), LibraryText.tokens("  -  "))
  }

  @Test
  fun libraryText_editDistance() {
    assertEquals(0, LibraryText.editDistance("coffee", "coffee"))
    assertEquals(1, LibraryText.editDistance("cofee", "coffee"))
    assertEquals(1, LibraryText.editDistance("ocffee", "coffee")) // Transposition.
    assertEquals(2, LibraryText.editDistance("comodty", "commodity"))
    assertEquals(3, LibraryText.editDistance("abc", "xyz"))
    assertEquals(3, LibraryText.editDistance("abcdef", "uvwxyz", max = 2)) // Capped at max + 1.
    assertEquals(3, LibraryText.editDistance("", "abc"))
  }
}
