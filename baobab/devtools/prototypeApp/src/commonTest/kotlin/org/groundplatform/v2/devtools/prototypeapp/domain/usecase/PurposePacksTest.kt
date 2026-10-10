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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryPrograms
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind

/** Purpose Packs seeding new surveys, and template IDs for Save as template. */
class PurposePacksTest {
  private val kfs = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  private val mekong = PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE

  private class Fixture {
    val store = seededStore()
    val surveys = SurveyRepositoryImpl(store)
    val editor = SurveyEditorRepositoryImpl(store)
    val libraries = LibraryRepositoryImpl(store)
    var formIds = 0
    val useCase =
      CreateSurveyUseCase(
        surveys,
        editor,
        LocalStoreTransactionRunner(store),
        libraryRepository = libraries,
        newFormId = { "form_${++formIds}" },
      )

    fun organization(id: String?): Organization? = id?.let {
      runNow { OrganizationRepositoryImpl(store).getOrganization(it) }
    }

    fun create(
      organizationId: String?,
      purposeIds: List<String>,
      programIds: List<String> = emptyList(),
    ) =
      organization(organizationId).let { organization ->
        runNow {
          val survey =
            useCase.newSurvey("Plots 2026", surveys.getSurveys(), organization, "maya@example.org")
          useCase(survey, "Maya Lin", purposeIds, programIds)
          editor.getDraft(survey.id)
        }
      }

    fun resolved(organizationId: String?) =
      ResolveLibraryUseCase()
        .forOrganization(runNow { libraries.observeLibraries().first() }, organizationId)
  }

  @Test
  fun createSurvey_withEudrPurpose_seedsTheTemplateWithLinksCodesAndALinkedMapLayer() {
    val f = Fixture()
    val draft = f.create(kfs, listOf("eudr_due_diligence"), listOf("eudr"))
    assertEquals(listOf("eudr_due_diligence"), draft.details.purposeIds)
    assertEquals(listOf("eudr"), draft.details.programIds)

    val entry = draft.forms.single()
    val form = entry.form
    assertEquals("form_1", form.formId)
    assertEquals("EUDR plot registration", form.title)
    val commodity = form.questions.first { it.name == "commodity" }
    assertEquals(ConceptLink("eudr.commodity", 1), commodity.conceptLink)
    assertTrue(commodity.choices.isNotEmpty() && commodity.choices.all { it.code != null })

    val dataset = draft.datasets.single { it.linkedFormKey == entry.key }
    assertEquals(DatasetKind.MAP_LAYER, dataset.kind)
    assertEquals(GeometryKind.POLYGON, dataset.geometryKind)
    assertEquals(
      ConceptLink("eudr.commodity", 1),
      dataset.properties.first { it.name == "commodity" }.conceptLink,
    )
  }

  @Test
  fun createSurvey_withoutPurposes_startsEmpty() {
    val draft = Fixture().create(kfs, emptyList())
    assertTrue(draft.forms.isEmpty())
    assertTrue(draft.details.purposeIds.isEmpty())
  }

  @Test
  fun createSurvey_withSeveralPacks_addsEachTemplateOnce_inPackOrder() {
    val f = Fixture()
    // The KFS pack lists the global EUDR template and the cooperative's own audit.
    val coop = "org.$kfs.coop_certification_audit"
    val draft = f.create(kfs, listOf(coop, "eudr_due_diligence", "producer_registration"))
    assertEquals(
      listOf("EUDR plot registration", "Coop member plot audit", "Producer profile"),
      draft.forms.map { it.form.title },
    )
    assertEquals(3, draft.forms.map { it.form.formId }.toSet().size)
    assertEquals(3, draft.datasets.count { it.linkedFormKey != null })
  }

  @Test
  fun createSurvey_skipsHiddenPacksAndPacksOfOtherOrganizations() {
    val f = Fixture()
    runNow {
      ManageLibraryUseCase(f.libraries).setGlobalEntryHidden(mekong, "eudr_due_diligence", true)
    }
    assertTrue(f.resolved(mekong).pickablePurposePacks.none { it.id == "eudr_due_diligence" })
    assertTrue(f.resolved(kfs).pickablePurposePacks.any { it.id == "eudr_due_diligence" })
    val draft = f.create(mekong, listOf("eudr_due_diligence", "org.$kfs.coop_certification_audit"))
    assertTrue(draft.forms.isEmpty())
  }

  @Test
  fun resolvedPurposes_listOrganizationPacksFirst_andPersonalSurveysGetGlobalOnly() {
    val f = Fixture()
    val kenya = f.resolved(kfs).pickablePurposePacks.map { it.id }
    assertEquals("org.$kfs.coop_certification_audit", kenya.first())
    assertTrue("eudr_due_diligence" in kenya)
    assertTrue(f.resolved(null).pickablePurposePacks.all { it.isGlobal })
    assertEquals(
      setOf("eudr.commodity", "core.area_ha", "core.producer_id"),
      f.resolved(kfs)
        .conceptIdsForPurposes(listOf("eudr_due_diligence"))
        .intersect(
          setOf("eudr.commodity", "core.area_ha", "core.producer_id", "org.$kfs.shade_tree_count")
        ),
    )
  }

  @Test
  fun programs_areOfferedByTheSelectedPacks() {
    val eudr =
      PurposePack(
        "eudr_due_diligence",
        Organization.ALL_USERS_ID,
        LocalizedText.en("EUDR"),
        programIds = listOf("eudr", "uk_frc"),
      )
    val ferm =
      PurposePack(
        "ferm_restoration",
        Organization.ALL_USERS_ID,
        LocalizedText.en("FERM"),
        programIds = listOf("ferm", "eudr"),
      )
    assertEquals(listOf("eudr", "uk_frc", "ferm"), LibraryPrograms.offeredFor(listOf(eudr, ferm)))
    assertEquals(
      listOf("ferm", "eudr", "redd_plus"),
      LibraryPrograms.offeredFor(listOf(ferm), listOf("redd_plus")),
    )
    assertEquals("UK Forest Risk Commodities", LibraryPrograms.label("uk_frc"))
    assertEquals("custom_program", LibraryPrograms.label("custom_program"))
  }

  @Test
  fun newTemplateId_isPrefixedAndUnique_amongTemplatesAndPacks() {
    val useCase = ManageLibraryUseCase(LibraryRepositoryImpl(seededStore()))
    val template = { id: String ->
      FormTemplate(id, kfs, LocalizedText.en("T"), form = EditorForm("f", "T", emptyList()))
    }
    val library =
      OrganizationLibrary(
        organizationId = kfs,
        formTemplates = listOf(template("org.$kfs.plot_check")),
        purposePacks = listOf(PurposePack("org.$kfs.plot_check_2", kfs, LocalizedText.en("P"))),
      )
    assertEquals("org.$kfs.nursery_visit", useCase.newTemplateId(library, "Nursery visit"))
    assertEquals("org.$kfs.plot_check_3", useCase.newTemplateId(library, "Plot check"))
    val global = OrganizationLibrary(Organization.ALL_USERS_ID)
    assertEquals("nursery_visit", useCase.newTemplateId(global, "Nursery visit"))
    // "org" is reserved for organization IDs.
    assertEquals("org_2", useCase.newTemplateId(global, "Org"))
  }

  @Test
  fun saveTemplate_addsItToTheOrganizationLibrary() {
    val libraries = LibraryRepositoryImpl(seededStore())
    val useCase = ManageLibraryUseCase(libraries)
    val template =
      FormTemplate(
        "org.$kfs.nursery_visit",
        kfs,
        LocalizedText.en("Nursery visit"),
        form = EditorForm("nursery_visit", "Nursery visit", emptyList()),
      )
    assertNotNull(runNow { useCase.saveTemplate(template) })
    assertEquals(template, runNow { libraries.getLibrary(kfs) }.formTemplate(template.id))
    // A template whose ID breaks the ownership rules isn't stored.
    assertNull(runNow { useCase.saveTemplate(template.copy(id = "org.other.nursery_visit")) })
  }
}
