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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CachedProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.InvitationStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.Collaborator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.CollaboratorRole
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingSettings
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyArea
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft

/**
 * Survey editor fixtures that cannot be derived from the runtime survey data (fictional sample
 * data): sharing people, the survey area and languages, and Data tables.
 *
 * Forms and Map layers are derived from the seeded Forms, Map layers, and map features by
 * `SurveyEditorDerivation`; these extras are merged on top of that derived draft by
 * [SampleDataSeeder].
 */
internal object PrototypeFakeSurveyEditorData {
  /** ID of the sample survey shared with everyone in its organization. */
  const val ORGANIZATION_SHARED_SURVEY_ID: String = "survey-serengeti-corridor"

  /** The seeded draft for the Kenya coffee survey: [derived] plus the extras below. */
  fun kenyaCoffeeDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    derived.copy(
      details =
        derived.details.copy(
          supportedLanguages = listOf("en", "sw"),
          surveyArea = kenyaSurveyArea(),
        ),
      sharing = kenyaSharing(),
      datasets = derived.datasets + listOf(farmers(), treeSpecies()),
    )

  /**
   * The seeded draft for [ORGANIZATION_SHARED_SURVEY_ID]: [derived], owned by a Mekong Mangrove
   * Alliance member and open to everyone in that organization ([SharingPolicy.ORGANIZATION]).
   */
  fun organizationSharedDraft(derived: SurveyEditorDraft): SurveyEditorDraft =
    derived.copy(
      details =
        derived.details.copy(
          organizationId = PrototypeFakeOrganizationsData.MEKONG_MANGROVE_ALLIANCE
        ),
      sharing = organizationSharing(),
    )

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
        PrototypeFakeOrganizationsData.ownerEmailForSurvey(ORGANIZATION_SHARED_SURVEY_ID),
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
