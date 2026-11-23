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
package org.openforis.collectone.v2.web.ui.surveyeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.app.domain.model.editor.SurveyDetails
import org.openforis.collectone.v2.app.domain.model.hasPlatformAdmin
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.web.domain.usecase.ManageLibraryUseCase
import org.openforis.collectone.v2.web.ui.formeditor.FormLibraryContext

/** [SurveyLibraryHandler]: who may add to which library, and the writes it validates. */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyLibraryHandlerTest {
  // Library writes need the organization in the store.
  private val store = seededStore()
  private val libraries = LibraryRepositoryImpl(store)
  private val manage = ManageLibraryUseCase(libraries)
  private val scope = TestScope()

  private val forestService =
    Organization(
      id = SampleOrganizationsData.KENYA_FOREST_SERVICE,
      name = "Forest Service",
      members =
        listOf(
          OrganizationMember("manager@forest.example", role = OrganizationRole.MANAGER),
          OrganizationMember("member@forest.example"),
        ),
    )
  private val allUsers =
    Organization(
      id = Organization.ALL_USERS_ID,
      name = "All users",
      isSynthetic = true,
      members = listOf(OrganizationMember("admin@app.example", role = OrganizationRole.MANAGER)),
    )
  private val organizations = listOf(forestService, allUsers)
  private val details =
    SurveyDetails(
      surveyId = "s1",
      title = "Forest plots",
      description = "",
      organizationId = forestService.id,
    )

  private fun handler(
    manage: ManageLibraryUseCase? = this.manage,
    context: FormLibraryContext = FormLibraryContext(),
  ) =
    SurveyLibraryHandler(
      manage,
      ResolveLibraryUseCase(),
      scope,
      libraries = { emptyMap() },
      libraryContext = { context },
    )

  private fun concept(label: String = "Canopy gap") =
    LibraryConcept(
      id = manage.conceptId(forestService.id, "canopy_gap_test"),
      organizationId = forestService.id,
      label = LocalizedText(mapOf("en" to label)),
      dataType = ConceptDataType.DECIMAL,
    )

  private fun library() = runNow { libraries.getLibrary(forestService.id) }

  private fun contextFor(
    email: String,
    details: SurveyDetails = this.details,
    manage: ManageLibraryUseCase? = this.manage,
  ) = handler(manage).contextFor(details, organizations, email, emptyMap())

  @Test
  fun hasPlatformAdmin_isAManagerOfAllUsers() {
    assertTrue(organizations.hasPlatformAdmin("admin@app.example"))
    assertFalse(organizations.hasPlatformAdmin("manager@forest.example"))
    assertFalse(emptyList<Organization>().hasPlatformAdmin("admin@app.example"))
  }

  @Test
  fun contextFor_organizationManager_mayAddToItsDictionaryOnly() {
    val context = contextFor("manager@forest.example")

    assertEquals("Forest Service", context.organizationName)
    assertEquals(forestService.id, context.library.organizationId)
    assertTrue(context.canAddToDictionary)
    assertFalse(context.canSaveToGlobalLibrary)
  }

  @Test
  fun contextFor_member_mayAddToNoLibrary() {
    val context = contextFor("member@forest.example")

    assertFalse(context.canAddToDictionary)
    assertFalse(context.canSaveToGlobalLibrary)
  }

  @Test
  fun contextFor_platformAdmin_mayAddToBothLibraries() {
    val context = contextFor("admin@app.example")

    assertTrue(context.canAddToDictionary)
    assertTrue(context.canSaveToGlobalLibrary)
  }

  @Test
  fun contextFor_withoutLibraryManagement_mayAddToNoLibrary() {
    val context = contextFor("admin@app.example", manage = null)

    assertFalse(context.canAddToDictionary)
    assertFalse(context.canSaveToGlobalLibrary)
  }

  @Test
  fun contextFor_personalSurvey_hasNoDictionary() {
    val context = contextFor("admin@app.example", details.copy(organizationId = null))

    assertNull(context.organizationName)
    assertFalse(context.canAddToDictionary)
  }

  @Test
  fun contextFor_languages_startWithTheDefault_trimmedAndDeduplicated() {
    val context =
      contextFor(
        "member@forest.example",
        details.copy(defaultLanguage = "fr", supportedLanguages = listOf(" en ", "FR", "", "sw")),
      )

    assertEquals("fr", context.language)
    assertEquals(listOf("fr", "en", "sw"), context.supportedLanguages)
    assertEquals(
      LocalizedText.DEFAULT_LANGUAGE,
      contextFor("member@forest.example", details.copy(defaultLanguage = "")).language,
    )
  }

  @Test
  fun writes_withoutLibraryManagement_areRefused() {
    val handler = handler(manage = null)
    val form = EditorFormTemplates.blank(formId = "f1", title = "Plot")

    assertEquals(
      "Templates can't be saved here.",
      handler.saveFormAsTemplate(form, "Plot", "", false),
    )
    assertNotNull(handler.addOrganizationConcept(concept()))
  }

  @Test
  fun saveFormAsTemplate_refusesLibrariesTheUserCantAddTo() {
    val form = EditorFormTemplates.blank(formId = "f1", title = "Plot")
    val organizationLibrary = ResolvedLibrary(organizationId = forestService.id)

    assertEquals(
      "Only Managers of All users can add to the global library.",
      handler(
          context = FormLibraryContext(library = organizationLibrary, canAddToDictionary = true)
        )
        .saveFormAsTemplate(form, "Plot", "", toGlobal = true),
    )
    assertEquals(
      "Only Managers of this survey's organization can save templates.",
      handler(context = FormLibraryContext(library = organizationLibrary))
        .saveFormAsTemplate(form, "Plot", "", toGlobal = false),
    )
    assertEquals(
      "Only Managers of this survey's organization can save templates.",
      handler(context = FormLibraryContext(canAddToDictionary = true))
        .saveFormAsTemplate(form, "Plot", "", toGlobal = false),
      "a personal survey has no organization library",
    )
  }

  @Test
  fun saveFormAsTemplate_checksTheTitleAfterThePermission() {
    val form = EditorFormTemplates.blank(formId = "f1", title = "Plot")
    val context =
      FormLibraryContext(
        library = ResolvedLibrary(organizationId = forestService.id),
        canAddToDictionary = true,
      )

    val before = library()

    assertEquals(
      "Enter a title.",
      handler(context = context).saveFormAsTemplate(form, " ", "", false),
    )
    scope.runCurrent()
    assertEquals(before, library())
  }

  @Test
  fun saveFormAsTemplate_savesACopyToTheOrganizationLibrary() {
    val form = EditorFormTemplates.blank(formId = "f1", title = "Plot")
    val context =
      FormLibraryContext(
        library = ResolvedLibrary(organizationId = forestService.id),
        canAddToDictionary = true,
      )

    val before = library().formTemplates

    assertNull(handler(context = context).saveFormAsTemplate(form, "Plot survey", "Trees", false))
    scope.runCurrent()

    val template = (library().formTemplates - before.toSet()).single()
    assertTrue("Plot survey" in template.title.values.values)
    assertEquals(forestService.id, template.organizationId)
  }

  @Test
  fun addOrganizationConcept_refusesAnInvalidConcept() {
    val before = library()

    assertEquals("Enter a label.", handler().addOrganizationConcept(concept(label = "")))
    scope.runCurrent()
    assertEquals(before, library())
  }

  @Test
  fun addOrganizationConcept_savesItToItsOrganizationsDictionary() {
    val concept = concept()

    assertNull(handler().addOrganizationConcept(concept))
    scope.runCurrent()

    assertEquals("Canopy gap", library().concept(concept.id)?.label?.values?.get("en"))
  }
}
