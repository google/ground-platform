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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ExportProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrarySettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType

class ResolveLibraryUseCaseTest {
  private val resolve = ResolveLibraryUseCase()
  private val g = Organization.ALL_USERS_ID
  private val org = "org-coop"

  private fun concept(id: String, owner: String, status: LibraryStatus = LibraryStatus.STABLE) =
    LibraryConcept(id, owner, LocalizedText.en(id), ConceptDataType.TEXT, status = status)

  private fun template(id: String, owner: String, concepts: Map<String, String> = emptyMap()) =
    FormTemplate(
      id,
      owner,
      LocalizedText.en(id),
      EditorForm(
        id,
        id,
        concepts.map { (key, conceptId) ->
          EditorQuestion(
            key,
            key,
            EditorQuestionType.TEXT,
            key,
            conceptLink = ConceptLink(conceptId),
          )
        },
      ),
    )

  private fun pack(
    id: String,
    owner: String,
    templates: List<String>,
    profiles: List<String> = emptyList(),
  ) =
    PurposePack(
      id,
      owner,
      LocalizedText.en(id),
      formTemplateIds = templates,
      exportProfileIds = profiles,
    )

  private val global =
    OrganizationLibrary(
      g,
      concepts = listOf(concept("eudr.commodity", g), concept("core.area_ha", g)),
      formTemplates =
        listOf(
          template("eudr_plots", g, mapOf("q1" to "eudr.commodity", "q2" to "org.org-coop.x")),
          template("ferm_wave", g),
          template("ferm_baseline", g),
          // Shadowing attempt in the global library: dropped.
          template("org.org-coop.sneaky", g),
        ),
      purposePacks =
        listOf(
          pack("eudr_dd", g, listOf("eudr_plots"), listOf("eudr_geojson", "org.org-coop.p")),
          pack("ferm", g, listOf("ferm_wave", "ferm_baseline")),
          pack("registry", g, emptyList()),
          // Global packs never reference organization templates.
          pack("mixed", g, listOf("eudr_plots", "org.org-coop.audit")),
        ),
      exportProfiles =
        listOf(ExportProfile("eudr_geojson", g, LocalizedText.en("GeoJSON"), "geojson")),
    )

  private val coop =
    OrganizationLibrary(
      org,
      concepts =
        listOf(
          concept("org.org-coop.cherry_kg", org),
          // No shadowing: organization entries must carry the org prefix.
          concept("eudr.commodity", org),
          concept("org.org-other.x", org),
        ),
      formTemplates = listOf(template("org.org-coop.audit", org)),
      purposePacks =
        listOf(pack("org.org-coop.cert", org, listOf("eudr_plots", "org.org-coop.audit"))),
    )

  @Test
  fun personalSurvey_getsTheGlobalLibraryOnly() {
    val resolved = resolve(global, organization = null)
    assertNull(resolved.organizationId)
    assertEquals(listOf("eudr.commodity", "core.area_ha"), resolved.concepts.map { it.id })
    assertEquals(
      listOf("eudr_plots", "ferm_wave", "ferm_baseline"),
      resolved.formTemplates.map { it.id },
    )
    assertEquals(
      listOf("eudr_dd", "ferm", "registry", "mixed"),
      resolved.purposePacks.map { it.id },
    )
    assertEquals(listOf("eudr_geojson"), resolved.exportProfiles.map { it.id })
  }

  @Test
  fun organizationSurvey_listsOrganizationEntriesFirst_andDropsShadowingIds() {
    val resolved = resolve(global, coop)
    assertEquals(org, resolved.organizationId)
    assertEquals(
      listOf("org.org-coop.cherry_kg", "eudr.commodity", "core.area_ha"),
      resolved.concepts.map { it.id },
    )
    // The resolved eudr.commodity is the global one.
    assertEquals(g, resolved.concept("eudr.commodity")!!.organizationId)
    assertEquals("org.org-coop.audit", resolved.formTemplates.first().id)
    assertEquals("org.org-coop.cert", resolved.purposePacks.first().id)
    assertTrue(resolved.isOrganizationEntry("org.org-coop.cherry_kg"))
    assertTrue(!resolved.isOrganizationEntry("eudr.commodity"))
  }

  @Test
  fun references_flowOneWay() {
    val resolved = resolve(global, coop)
    // Global templates keep only global concept links.
    assertEquals(
      mapOf("q1" to "eudr.commodity", "q2" to null),
      resolved.formTemplate("eudr_plots")!!.form.questions.associate {
        it.key to it.conceptLink?.conceptId
      },
    )
    // Global packs keep only global templates and export profiles.
    assertEquals(listOf("eudr_plots"), resolved.purposePack("mixed")!!.formTemplateIds)
    assertEquals(listOf("eudr_geojson"), resolved.purposePack("eudr_dd")!!.exportProfileIds)
    // Organization packs may reference global templates.
    assertEquals(
      listOf("eudr_plots", "org.org-coop.audit"),
      resolved.purposePack("org.org-coop.cert")!!.formTemplateIds,
    )
  }

  @Test
  fun hiddenGlobalTemplates_hidePacksBuiltOnlyOnThem() {
    val hiding =
      coop.copy(
        settings = OrganizationLibrarySettings(setOf("eudr_plots", "ferm_wave", "registry"))
      )
    val resolved = resolve(global, hiding)
    assertEquals(
      listOf("org.org-coop.audit", "ferm_baseline"),
      resolved.formTemplates.map { it.id },
    )
    // eudr_dd only uses the hidden eudr_plots → hidden. ferm keeps ferm_baseline. registry is
    // hidden directly. mixed only had eudr_plots left among global templates → hidden.
    assertEquals(listOf("org.org-coop.cert", "ferm"), resolved.purposePacks.map { it.id })
    assertEquals(listOf("ferm_baseline"), resolved.purposePack("ferm")!!.formTemplateIds)
    // Organization packs aren't rewritten, and concepts can't be hidden.
    assertEquals(
      listOf("eudr_plots", "org.org-coop.audit"),
      resolved.purposePack("org.org-coop.cert")!!.formTemplateIds,
    )
    assertEquals(3, resolved.concepts.size)
  }

  @Test
  fun hiddenIds_doNotApplyToPersonalSurveysOrTheGlobalLibrary() {
    val globalWithSettings =
      global.copy(settings = OrganizationLibrarySettings(setOf("eudr_plots")))
    assertEquals(3, resolve(globalWithSettings, null).formTemplates.size)
    val forAllUsers = resolve(globalWithSettings, globalWithSettings)
    assertEquals(g, forAllUsers.organizationId)
    assertEquals(3, forAllUsers.formTemplates.size)
    assertEquals(2, forAllUsers.concepts.size) // The global library is listed once.
  }

  @Test
  fun deprecatedEntries_resolveButAreNotPickable() {
    val withDeprecated =
      global.copy(concepts = global.concepts + concept("core.old", g, LibraryStatus.DEPRECATED))
    val resolved = resolve(withDeprecated, null)
    assertTrue(resolved.concept("core.old") != null)
    assertTrue(resolved.pickableConcepts.none { it.id == "core.old" })
  }

  @Test
  fun forOrganization_readsLibrariesByOrganizationId() {
    val libraries = mapOf(g to global, org to coop)
    assertEquals(resolve(global, coop), resolve.forOrganization(libraries, org))
    assertEquals(resolve(global, null), resolve.forOrganization(libraries, null))
    // An organization without a stored library gets the global entries.
    assertEquals(2, resolve.forOrganization(libraries, "org-new").concepts.size)
    assertTrue(resolve.forOrganization(emptyMap(), null).concepts.isEmpty())
  }
}
