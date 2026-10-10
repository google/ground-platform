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
package org.groundplatform.v2.devtools.prototypeapp.data.datasource.local

import groundplatform.v2.forms.DataType
import groundplatform.v2.library.LibraryStatus as LibraryStatusProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.devtools.prototypeapp.data.seed.GlobalLibrarySeedData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrarySettings

class LibraryProtoMapperTest {
  private val global = GlobalLibrarySeedData.library()

  @Test
  fun bundle_roundTripsConceptsPacksAndProfiles() {
    val bundle = LibraryProtoMapper.toBundle(global)
    val back = LibraryProtoMapper.fromBundle(bundle, Organization.ALL_USERS_ID)
    assertEquals(global.concepts, back.concepts)
    assertEquals(global.purposePacks, back.purposePacks)
    assertEquals(global.exportProfiles, back.exportProfiles)
    assertTrue(bundle.concepts.all { it.organization_id == Organization.ALL_USERS_ID })
  }

  @Test
  fun templates_roundTripTheirForms_butNotConceptLinksYet() {
    val back =
      LibraryProtoMapper.fromBundle(LibraryProtoMapper.toBundle(global), global.organizationId)
    for ((seed, parsed) in global.formTemplates.zip(back.formTemplates)) {
      assertEquals(seed.id, parsed.id)
      assertEquals(seed.title, parsed.title)
      assertEquals(seed.form.questions.map { it.name }, parsed.form.questions.map { it.name })
      assertEquals(seed.form.questions.map { it.type }, parsed.form.questions.map { it.type })
      assertEquals(seed.form.questions.map { it.choices }, parsed.form.questions.map { it.choices })
      assertTrue(seed.questionConcepts.isNotEmpty())
      assertTrue(parsed.questionConcepts.isEmpty())
    }
  }

  @Test
  fun seedBundles_omitTheOrganizationId_whichTheLoaderAssigns() {
    val bundle = LibraryProtoMapper.toBundle(global, includeOrganizationId = false)
    assertTrue(bundle.concepts.all { it.organization_id.isEmpty() })
    assertTrue(bundle.purpose_packs.all { it.organization_id.isEmpty() })
    val loaded = LibraryProtoMapper.fromBundle(bundle, "org-x")
    assertTrue(loaded.concepts.all { it.organizationId == "org-x" })
  }

  @Test
  fun enumsAndTypes_mapByName() {
    val commodity = LibraryProtoMapper.toProto(global.concept("eudr.commodity")!!)
    assertEquals(DataType.TYPE_SELECT_ONE, commodity.data_type)
    assertEquals(LibraryStatusProto.STABLE, commodity.status)
    assertEquals(
      "0901",
      commodity.code_list!!.items.first { it.code == "coffee" }.external_ids["hs"],
    )
    ConceptDataType.entries.forEach {
      assertEquals(it, LibraryProtoMapper.fromProto(LibraryProtoMapper.toProto(it)))
    }
    assertEquals(ConceptDataType.INTEGER, LibraryProtoMapper.fromProto(DataType.TYPE_INT64))
    assertEquals(ConceptDataType.TEXT, LibraryProtoMapper.fromProto(DataType.TYPE_BOOLEAN))
  }

  @Test
  fun settings_roundTrip() {
    val settings = OrganizationLibrarySettings(setOf("ferm_restoration", "eudr_plot_registration"))
    val proto = LibraryProtoMapper.toProto(settings, "org-x")
    assertEquals("org-x", proto.organization_id)
    assertEquals(
      listOf("eudr_plot_registration", "ferm_restoration"),
      proto.hidden_global_entry_ids,
    )
    assertEquals(settings, LibraryProtoMapper.fromProto(proto))
  }
}
