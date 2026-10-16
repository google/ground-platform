/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.mutation

import groundplatform.v2.data.EntityFieldPatch
import groundplatform.v2.data.EntityPatch
import groundplatform.v2.data.EntityRecord
import groundplatform.v2.data.SubmissionFieldPatch
import groundplatform.v2.data.SubmissionPatch
import groundplatform.v2.data.SubmissionRecord
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.LibraryFieldPatch
import groundplatform.v2.library.OrganizationLibraryRecord
import groundplatform.v2.survey.AclRolePatch
import groundplatform.v2.survey.LayerDef
import groundplatform.v2.survey.Organization
import groundplatform.v2.survey.OrganizationFieldPatch
import groundplatform.v2.survey.OrganizationMember
import groundplatform.v2.survey.Role
import groundplatform.v2.survey.StringListValue
import groundplatform.v2.survey.SurveyAcl
import groundplatform.v2.survey.SurveyDef
import groundplatform.v2.survey.SurveyFieldPatch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Semantics of the shared field patches, including the property the client outbox relies on:
 * applying a patch twice gives the same record as applying it once, so pending mutations can be
 * re-applied on top of a server copy that may already include them.
 *
 * Wire maps `google.protobuf.NullValue` to `Nothing?`, so Kotlin clients clear a key by leaving the
 * `value` oneof unset; the patches treat that the same as `null_value`.
 */
class RecordPatchesTest {
  private val survey =
    SurveyDef(
      survey_id = "s1",
      title = "Old",
      forms =
        listOf(FormDef(form_id = "f1", title = "One"), FormDef(form_id = "f2", title = "Two")),
    )

  private val surveyPatches =
    listOf(
      SurveyFieldPatch(key = "title", string_value = "New"),
      SurveyFieldPatch(key = "description"),
      SurveyFieldPatch(
        key = "supported_languages",
        string_list_value = StringListValue(values = listOf("en", "sw")),
      ),
      SurveyFieldPatch(key = "forms/f1", form_value = FormDef(form_id = "f1", title = "Uno")),
      SurveyFieldPatch(key = "forms/f2"),
      SurveyFieldPatch(key = "map_config/layers/l1", layer_value = LayerDef(id = "l1")),
      SurveyFieldPatch(key = "unknown_key", string_value = "ignored"),
    )

  @Test
  fun surveyPatches_areIdempotent() {
    for (patch in surveyPatches) {
      val once = SurveyPatches.applyField(survey, patch)
      assertEquals(once, SurveyPatches.applyField(once, patch), patch.key)
    }
  }

  @Test
  fun surveyPatches_setClearAndUpsertKeyedItems() {
    val result = surveyPatches.fold(survey, SurveyPatches::applyField)

    assertEquals("New", result.title)
    assertEquals("", result.description)
    assertEquals(listOf("en", "sw"), result.supported_languages)
    assertEquals(listOf(FormDef(form_id = "f1", title = "Uno")), result.forms)
    assertEquals(listOf("l1"), result.map_config?.layers?.map { it.id })
  }

  @Test
  fun aclRolePatch_grantsAndRevokes_idempotently() {
    val grant = AclRolePatch(user_id = "a@example.org", role = Role.DATA_COLLECTOR)
    val revoke = AclRolePatch(user_id = "a@example.org")
    val granted = SurveyPatches.applyAclRole(SurveyAcl(survey_id = "s1"), grant)

    assertEquals(granted, SurveyPatches.applyAclRole(granted, grant))
    assertEquals(Role.DATA_COLLECTOR, granted.entries.single().role)
    assertEquals(emptyList(), SurveyPatches.applyAclRole(granted, revoke).entries)
  }

  @Test
  fun organizationPatches_areIdempotent_andMatchMembersCaseInsensitively() {
    val org =
      Organization(
        organization_id = "o1",
        members = listOf(OrganizationMember(email = "A@Example.org")),
      )
    val patches =
      listOf(
        OrganizationFieldPatch(key = "name", string_value = "Org"),
        OrganizationFieldPatch(key = "listed", bool_value = true),
        OrganizationFieldPatch(
          key = "members/a@example.org",
          member_value = OrganizationMember(email = "a@example.org", user_id = "u1"),
        ),
      )
    for (patch in patches) {
      val once = OrganizationPatches.applyField(org, patch)
      assertEquals(once, OrganizationPatches.applyField(once, patch), patch.key)
    }
    val result = patches.fold(org, OrganizationPatches::applyField)
    assertEquals("u1", result.members.single().user_id)
  }

  @Test
  fun libraryPatches_upsertAndRemoveConcepts_idempotently() {
    val upsert = LibraryFieldPatch(key = "concepts/c1", concept_value = ConceptDef(id = "c1"))
    val remove = LibraryFieldPatch(key = "concepts/c1")
    val added = LibraryPatches.applyField(OrganizationLibraryRecord(organization_id = "o1"), upsert)

    assertEquals(added, LibraryPatches.applyField(added, upsert))
    assertEquals(listOf("c1"), added.bundle?.concepts?.map { it.id })
    assertEquals(emptyList(), LibraryPatches.applyField(added, remove).bundle?.concepts)
  }

  @Test
  fun entityPatch_setsAndClearsProperties_idempotently() {
    val record =
      EntityRecord(entity_id = "e1", properties = mapOf("old" to TypedValue(string_value = "x")))
    val patch =
      EntityPatch(
        patches =
          listOf(
            EntityFieldPatch(key = "label", label_value = "Plot 1"),
            EntityFieldPatch(
              key = "properties/crop",
              property_value = TypedValue(string_value = "coffee"),
            ),
            EntityFieldPatch(key = "properties/old"),
          )
      )

    val once = DataPatches.applyEntity(record, patch)

    assertEquals(once, DataPatches.applyEntity(once, patch))
    assertEquals("Plot 1", once.label)
    assertEquals(mapOf("crop" to TypedValue(string_value = "coffee")), once.properties)
  }

  @Test
  fun submissionPatch_isIdempotent() {
    val patch =
      SubmissionPatch(
        patches =
          listOf(
            SubmissionFieldPatch(key = "entity_id", string_value = "e1"),
            SubmissionFieldPatch(key = "is_quarantined", bool_value = true),
          )
      )
    val once = DataPatches.applySubmission(SubmissionRecord(submission_id = "sub1"), patch)

    assertEquals(once, DataPatches.applySubmission(once, patch))
    assertEquals("e1", once.entity_id)
    assertEquals(true, once.is_quarantined)
  }
}
