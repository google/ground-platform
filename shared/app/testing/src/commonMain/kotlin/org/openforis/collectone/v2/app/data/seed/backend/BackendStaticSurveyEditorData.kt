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
package org.openforis.collectone.v2.app.data.seed.backend

import org.openforis.collectone.v2.app.data.seed.SampleDataSeeder
import org.openforis.collectone.v2.app.domain.model.CachedProfile
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.LibraryIds
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.SharingSettings
import org.openforis.collectone.v2.app.domain.model.editor.SurveyArea
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.map.LatLng

/**
 * Survey editor fixtures that cannot be derived from the runtime survey data (fictional sample
 * data): sharing people, the survey area and languages, and Data tables.
 *
 * Forms and Map layers are derived from the seeded Forms, Map layers, and map features by
 * `SurveyEditorDerivation`; these extras are merged on top of that derived draft by
 * [SampleDataSeeder].
 */
object BackendStaticSurveyEditorData {
  /** ID of the sample survey shared with everyone in its organization. */
  const val ORGANIZATION_SHARED_SURVEY_ID: String = "00000035-caf4-571e-b907-f506f92666ab"

  /** The seeded draft for the Kenya coffee survey: [derived] plus the extras below. */
  fun kenyaCoffeeDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    derived.copy(
      details =
        derived.details.copy(
          supportedLanguages = listOf("en", "sw"),
          surveyArea = kenyaSurveyArea(),
          purposeIds = listOf("eudr_due_diligence"),
          programIds = listOf("eudr"),
        ),
      sharing = kenyaSharing(),
      datasets = derived.datasets.map(::withKenyaConceptLinks) + listOf(farmers(), treeSpecies()),
    )

  /**
   * Coffee parcels' columns linked to standard fields, so the EUDR GeoJSON export finds them (the
   * country isn't collected, which the export warns about) and the Impact views add them up: the
   * owner (global producer name), commodity and deforestation-free status (global EUDR concepts),
   * and shade trees and cherry delivered (Kenya Forest Service concepts; shade trees suggests the
   * ecosystem restoration goal).
   */
  private fun withKenyaConceptLinks(dataset: EntityDataset): EntityDataset =
    if (dataset.id != "coffee_parcels") {
      dataset
    } else {
      dataset.copy(
        properties =
          dataset.properties.map {
            val conceptId =
              when {
                it.name == "farmer_owner" -> "core.producer_name"
                it.label == "Commodity" -> "eudr.commodity"
                it.label == "Deforestation-free" -> "eudr.deforestation_free"
                it.label == "Shade trees" -> kfsConceptId("shade_tree_count")
                it.label == "Cherry delivered (kg)" -> kfsConceptId("cherry_delivery_kg")
                else -> null
              }
            if (conceptId != null) it.copy(conceptLink = ConceptLink(conceptId)) else it
          }
      )
    }

  private fun kfsConceptId(name: String) =
    LibraryIds.organizationEntryId(BackendStaticOrganizationsData.KENYA_FOREST_SERVICE, name)

  /**
   * The seeded draft for [ORGANIZATION_SHARED_SURVEY_ID]: [derived], owned by a Mekong Mangrove
   * Alliance member and open to everyone in that organization ([SharingPolicy.ORGANIZATION]).
   */
  fun organizationSharedDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    derived.copy(
      details =
        derived.details.copy(
          organizationId = BackendStaticOrganizationsData.MEKONG_MANGROVE_ALLIANCE,
          surveyArea = derived.details.surveyArea ?: mekongDeltaSurveyArea(),
        ),
      sharing = organizationSharing(),
    )

  /** Survey area of the Arusha land use survey: the city of Arusha, Tanzania. */
  fun arushaSurveyArea() =
    SurveyArea(
      name = "Arusha City, Arusha",
      center = LatLng(-3.3800, 36.6800),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(-3.3550, 36.6500),
            LatLng(-3.3550, 36.7100),
            LatLng(-3.4050, 36.7100),
            LatLng(-3.4050, 36.6500),
          )
        ),
    )

  /** Survey area of the Pará forest assessment: a block of forest sample plots. */
  fun paraSurveyArea() =
    SurveyArea(
      name = "Forest sample plots, Pará",
      center = LatLng(-3.4600, -62.2100),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(-3.4300, -62.2450),
            LatLng(-3.4300, -62.1750),
            LatLng(-3.4900, -62.1750),
            LatLng(-3.4900, -62.2450),
          )
        ),
    )

  /** Survey area of the Ashanti commodity plot survey: around Kumasi, Ghana. */
  fun ashantiSurveyArea() =
    SurveyArea(
      name = "Kumasi, Ashanti Region",
      center = LatLng(6.6900, -1.6200),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(6.7150, -1.6500),
            LatLng(6.7150, -1.5900),
            LatLng(6.6650, -1.5900),
            LatLng(6.6650, -1.6500),
          )
        ),
    )

  /** Survey area of the Kakamega household panel: Kakamega town, Kenya. */
  fun kakamegaSurveyArea() =
    SurveyArea(
      name = "Kakamega Central, Kakamega",
      center = LatLng(0.2800, 34.7500),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(0.3050, 34.7200),
            LatLng(0.3050, 34.7800),
            LatLng(0.2550, 34.7800),
            LatLng(0.2550, 34.7200),
          )
        ),
    )

  /** Survey area of the Kalimantan Google Earth sample-plot interpretation survey. */
  fun kalimantanSurveyArea() =
    SurveyArea(
      name = "Sebangau Peat Dome, Central Kalimantan",
      center = LatLng(-2.2136, 113.9213),
      zoom = 13.0,
      parts =
        listOf(
          listOf(
            LatLng(-2.1950, 113.9000),
            LatLng(-2.1950, 113.9430),
            LatLng(-2.2320, 113.9430),
            LatLng(-2.2320, 113.9000),
          )
        ),
    )

  /** Survey area of the Mekong Delta mangrove restoration: its coastal mangrove belt. */
  fun mekongDeltaSurveyArea() =
    SurveyArea(
      name = "Coastal mangroves, Mekong Delta",
      center = LatLng(9.8200, 106.3400),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(9.8500, 106.3000),
            LatLng(9.8500, 106.3800),
            LatLng(9.7900, 106.3800),
            LatLng(9.7900, 106.3000),
          )
        ),
    )

  /** Survey area of the Kenya coffee survey: Othaya Sub-County, Nyeri. */
  fun kenyaSurveyArea() =
    SurveyArea(
      name = "Othaya Sub-County, Nyeri",
      center = LatLng(-0.4192, 36.9498),
      zoom = 12.5,
      parts =
        listOf(
          listOf(
            LatLng(-0.3960, 36.9220),
            LatLng(-0.3960, 36.9780),
            LatLng(-0.4420, 36.9780),
            LatLng(-0.4420, 36.9220),
          )
        ),
    )

  fun kenyaSharing() =
    SharingSettings(
      ownerEmail = "organizer@example.org",
      ownerProfile = CachedProfile("Amina Wanjiru", "avatar:3", "2026-03-02"),
      policy = SharingPolicy.RESTRICTED,
      collaborators =
        listOf(
          Collaborator(
            "field.lead@example.org",
            CollaboratorRole.SURVEY_ORGANIZER,
            InvitationStatus.ACCEPTED,
            userId = "uid-field-lead",
            profile = CachedProfile("Daniel Kiprop", "avatar:0", "2026-03-04"),
          ),
          Collaborator(
            "collector.one@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            InvitationStatus.ACCEPTED,
            userId = "uid-collector-one",
            profile = CachedProfile("Grace Njeri", "avatar:5", "2026-03-06"),
          ),
          // The regular user of the web apps' development sign-in with fake accounts.
          Collaborator(
            "ima.user@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            InvitationStatus.ACCEPTED,
            userId = "uid-ima-user",
            profile = CachedProfile("Ima User", "avatar:3", "2026-03-08"),
          ),
          Collaborator(
            "collector.two@example.org",
            CollaboratorRole.DATA_COLLECTOR,
            inviteToken = "k7q2-mx4p",
          ),
          Collaborator("reviewer@example.org", CollaboratorRole.VIEWER, inviteToken = "r9w3-bt6d"),
        ),
    )

  fun organizationSharing() =
    SharingSettings(
      ownerEmail =
        BackendStaticOrganizationsData.ownerEmailForSurvey(ORGANIZATION_SHARED_SURVEY_ID),
      ownerProfile = CachedProfile("Linh Tran", "avatar:6", "2026-02-11"),
      policy = SharingPolicy.ORGANIZATION,
    )

  fun farmers() =
    EntityDataset(
      key = "d3",
      kind = DatasetKind.DATA_TABLE,
      id = "farmers",
      displayName = "Farmers",
      description = "Cooperative member roster (fictional sample data).",
      keyProperty = "farmer_id",
      labelProperty = "name",
      fieldCreationEnabled = true,
      properties =
        listOf(
          EntityProperty("farmer_id", "Farmer ID", PropertyType.TEXT, required = true),
          EntityProperty("name", "Name", PropertyType.TEXT, required = true),
          EntityProperty("cooperative", "Cooperative", PropertyType.TEXT),
          EntityProperty("member_since", "Member since", PropertyType.DATE),
          EntityProperty("certified", "Certified", PropertyType.BOOLEAN),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "farmer_id" to "F-001",
              "name" to "Farmer A",
              "cooperative" to "Gatura",
              "member_since" to "2019-06-01",
              "certified" to "yes",
            ),
          ),
          EntityRow(
            "r2",
            mapOf(
              "farmer_id" to "F-002",
              "name" to "Farmer B",
              "cooperative" to "Gatura",
              "member_since" to "2021-02-15",
              "certified" to "no",
            ),
          ),
          EntityRow(
            "r3",
            mapOf(
              "farmer_id" to "F-003",
              "name" to "Farmer C",
              "cooperative" to "Mathira",
              "member_since" to "2018-09-30",
              "certified" to "yes",
            ),
          ),
        ),
    )

  fun treeSpecies() =
    EntityDataset(
      key = "d4",
      kind = DatasetKind.DATA_TABLE,
      id = "tree_species",
      displayName = "Tree species",
      description = "Shade tree species lookup list.",
      keyProperty = "code",
      labelProperty = "common_name",
      properties =
        listOf(
          EntityProperty("code", "Code", PropertyType.TEXT, required = true),
          EntityProperty("scientific_name", "Scientific name", PropertyType.TEXT, required = true),
          EntityProperty("common_name", "Common name", PropertyType.TEXT),
          EntityProperty("native", "Native", PropertyType.BOOLEAN),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "code" to "GRRO",
              "scientific_name" to "Grevillea robusta",
              "common_name" to "Silky oak",
              "native" to "no",
            ),
          ),
          EntityRow(
            "r2",
            mapOf(
              "code" to "COAF",
              "scientific_name" to "Cordia africana",
              "common_name" to "Large-leaved cordia",
              "native" to "yes",
            ),
          ),
          EntityRow(
            "r3",
            mapOf(
              "code" to "CRME",
              "scientific_name" to "Croton megalocarpus",
              "common_name" to "Croton",
              "native" to "yes",
            ),
          ),
          EntityRow(
            "r4",
            mapOf(
              "code" to "MAIN",
              "scientific_name" to "Macadamia integrifolia",
              "common_name" to "Macadamia",
              "native" to "no",
            ),
          ),
        ),
    )
}
