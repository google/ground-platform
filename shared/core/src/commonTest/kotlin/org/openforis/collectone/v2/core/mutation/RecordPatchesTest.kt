/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.mutation

import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LocalizedText
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.AclEntry
import groundplatform.v2.survey.LayerDef
import groundplatform.v2.survey.MapConfig
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Semantics of sparse-proto mutations (structural `delete` mask + `upsert`), including the
 * idempotency property the client outbox relies on: applying a `(delete, upsert)` pair twice gives
 * the same record as applying it once, so pending mutations can be re-applied on top of a server
 * copy that may already include them.
 */
class RecordPatchesTest {
  private val survey =
    SurveyDef(
      survey_id = "s1",
      title = "Old",
      description = "Keep?",
      forms =
        listOf(
          FormDef(
            form_id = "f1",
            title = "One",
            view =
              ViewDef(
                components =
                  listOf(ViewComponent(id = "c1", control = ControlDef(field_ref = "/data/a")))
              ),
          ),
          FormDef(form_id = "f2", title = "Two"),
        ),
    )

  private val surveyMutations: List<Pair<SurveyDef?, SurveyDef?>> =
    listOf(
      null to SurveyDef(title = "New"),
      SurveyDef(description = "x") to null,
      null to SurveyDef(supported_languages = listOf("en", "sw")),
      null to SurveyDef(forms = listOf(FormDef(form_id = "f1", title = "Uno"))),
      SurveyDef(forms = listOf(FormDef(form_id = "f2"))) to null,
      null to SurveyDef(map_config = MapConfig(layers = listOf(LayerDef(id = "l1")))),
    )

  @Test
  fun surveyPatches_areIdempotent() {
    for ((delete, upsert) in surveyMutations) {
      val once = SurveyPatches.apply(survey, delete, upsert)
      assertEquals(once, SurveyPatches.apply(once, delete, upsert))
    }
  }

  @Test
  fun surveyPatches_setClearAndUpsertKeyedItems() {
    val result =
      surveyMutations.fold(survey) { acc, (delete, upsert) ->
        SurveyPatches.apply(acc, delete, upsert)
      }

    assertEquals("New", result.title)
    assertEquals("", result.description)
    assertEquals(listOf("en", "sw"), result.supported_languages)
    assertEquals(
      listOf(
        FormDef(
          form_id = "f1",
          title = "Uno",
          view =
            ViewDef(
              components =
                listOf(ViewComponent(id = "c1", control = ControlDef(field_ref = "/data/a")))
            ),
        )
      ),
      result.forms,
    )
    assertEquals(listOf("l1"), result.map_config?.layers?.map { it.id })
  }

  @Test
  fun surveyPatches_branchDeleteClearsNestedFieldsWithoutDroppingParentItem() {
    val deleteFormTitleOnly = SurveyDef(forms = listOf(FormDef(form_id = "f1", title = "x")))
    val result = SurveyPatches.apply(survey, delete = deleteFormTitleOnly, upsert = null)

    assertEquals(2, result.forms.size)
    assertEquals("", result.forms.first { it.form_id == "f1" }.title)
    assertEquals(
      listOf(ViewComponent(id = "c1", control = ControlDef(field_ref = "/data/a"))),
      result.forms.first { it.form_id == "f1" }.view?.components,
    )
  }

  @Test
  fun aclPatch_grantsAndRevokes_idempotently() {
    val grant =
      SurveyAcl(entries = listOf(AclEntry(email = "a@example.org", role = Role.DATA_COLLECTOR)))
    val revoke = SurveyAcl(entries = listOf(AclEntry(email = "a@example.org")))
    val granted = SurveyPatches.applyAcl(SurveyAcl(survey_id = "s1"), upsert = grant)

    assertEquals(granted, SurveyPatches.applyAcl(granted, upsert = grant))
    assertEquals(Role.DATA_COLLECTOR, granted.entries.single().role)
    assertEquals(emptyList(), SurveyPatches.applyAcl(granted, delete = revoke).entries)
  }

  @Test
  fun organizationPatches_areIdempotent_andMatchMembersCaseInsensitively() {
    val org =
      Organization(
        organization_id = "o1",
        members = listOf(OrganizationMember(email = "A@Example.org")),
      )
    val upserts =
      listOf(
        Organization(name = "Org"),
        Organization(listed = true),
        Organization(members = listOf(OrganizationMember(email = "a@example.org", user_id = "u1"))),
      )
    for (upsert in upserts) {
      val once = OrganizationPatches.apply(org, upsert = upsert)
      assertEquals(once, OrganizationPatches.apply(once, upsert = upsert))
    }
    val result = upserts.fold(org) { acc, u -> OrganizationPatches.apply(acc, upsert = u) }
    assertEquals("u1", result.members.single().user_id)
  }

  @Test
  fun libraryPatches_upsertAndRemoveConcepts_idempotently() {
    val upsert =
      OrganizationLibraryRecord(
        bundle =
          LibraryBundle(
            concepts =
              listOf(ConceptDef(id = "c1", label = LocalizedText(values = mapOf("en" to "Tree"))))
          )
      )
    val remove =
      OrganizationLibraryRecord(bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1"))))
    val added =
      LibraryPatches.apply(OrganizationLibraryRecord(organization_id = "o1"), upsert = upsert)

    assertEquals(added, LibraryPatches.apply(added, upsert = upsert))
    assertEquals(listOf("c1"), added.bundle?.concepts?.map { it.id })
    assertEquals(emptyList(), LibraryPatches.apply(added, delete = remove).bundle?.concepts)
  }

  @Test
  fun entityPatch_setsAndClearsProperties_idempotently() {
    val record =
      EntityRecord(entity_id = "e1", properties = mapOf("old" to TypedValue(string_value = "x")))
    val delete = EntityRecord(properties = mapOf("old" to TypedValue()))
    val upsert =
      EntityRecord(
        label = "Plot 1",
        properties = mapOf("crop" to TypedValue(string_value = "coffee")),
      )

    val once = DataPatches.applyEntity(record, delete = delete, upsert = upsert)

    assertEquals(once, DataPatches.applyEntity(once, delete = delete, upsert = upsert))
    assertEquals("Plot 1", once.label)
    assertEquals(mapOf("crop" to TypedValue(string_value = "coffee")), once.properties)
  }

  @Test
  fun submissionPatch_isIdempotent() {
    val upsert = SubmissionRecord(entity_id = "e1", is_quarantined = true)
    val once =
      DataPatches.applySubmission(SubmissionRecord(submission_id = "sub1"), upsert = upsert)

    assertEquals(once, DataPatches.applySubmission(once, upsert = upsert))
    assertEquals("e1", once.entity_id)
    assertEquals(true, once.is_quarantined)
  }
}
