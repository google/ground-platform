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

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LocalizedText
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.FormTemplateOrigin
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import org.openforis.collectone.v2.core.geo.GeometryProtos

/**
 * Semantics of sparse-proto mutations (structural `delete` mask + `upsert`), including the
 * idempotency property the client outbox relies on: applying a `(delete, upsert)` pair twice gives
 * the same record as applying it once, so pending mutations can be re-applied on top of a server
 * copy that may already include them.
 */
class RecordMutationsTest {
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
      val once = SurveyMutations.apply(survey, delete, upsert)
      assertEquals(once, SurveyMutations.apply(once, delete, upsert))
    }
  }

  @Test
  fun surveyPatch_upsertTitle_replacesTitle() {
    val result = SurveyMutations.apply(survey, delete = null, upsert = SurveyDef(title = "New"))

    assertEquals("New", result.title)
  }

  @Test
  fun surveyPatch_deleteDescription_clearsDescription() {
    val result = SurveyMutations.apply(survey, delete = SurveyDef(description = "x"), upsert = null)

    assertEquals("", result.description)
  }

  @Test
  fun surveyPatch_upsertSupportedLanguages_setsLanguages() {
    val result =
      SurveyMutations.apply(
        survey,
        delete = null,
        upsert = SurveyDef(supported_languages = listOf("en", "sw")),
      )

    assertEquals(listOf("en", "sw"), result.supported_languages)
  }

  @Test
  fun surveyPatch_upsertKeyedForm_mergesIntoFormWithSameId() {
    val result =
      SurveyMutations.apply(
        survey,
        delete = null,
        upsert = SurveyDef(forms = listOf(FormDef(form_id = "f1", title = "Uno"))),
      )

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
        ),
        FormDef(form_id = "f2", title = "Two"),
      ),
      result.forms,
    )
  }

  @Test
  fun surveyPatch_deleteKeyedForm_removesFormWithSameId() {
    val result =
      SurveyMutations.apply(
        survey,
        delete = SurveyDef(forms = listOf(FormDef(form_id = "f2"))),
        upsert = null,
      )

    assertEquals(listOf("f1"), result.forms.map { it.form_id })
  }

  @Test
  fun surveyPatch_upsertMapLayers_setsLayers() {
    val result =
      SurveyMutations.apply(
        survey,
        delete = null,
        upsert = SurveyDef(map_config = MapConfig(layers = listOf(LayerDef(id = "l1")))),
      )

    assertEquals(listOf("l1"), result.map_config?.layers?.map { it.id })
  }

  @Test
  fun surveyPatches_branchDeleteClearsNestedFieldsWithoutDroppingParentItem() {
    val deleteFormTitleOnly = SurveyDef(forms = listOf(FormDef(form_id = "f1", title = "x")))
    val result = SurveyMutations.apply(survey, delete = deleteFormTitleOnly, upsert = null)

    assertEquals(2, result.forms.size)
    assertEquals("", result.forms.first { it.form_id == "f1" }.title)
    assertEquals(
      listOf(ViewComponent(id = "c1", control = ControlDef(field_ref = "/data/a"))),
      result.forms.first { it.form_id == "f1" }.view?.components,
    )
  }

  @Test
  fun aclPatch_grant_addsEntryWithRole() {
    val granted = SurveyMutations.applyAcl(SurveyAcl(survey_id = "s1"), upsert = collectorGrant)

    assertEquals(Role.DATA_COLLECTOR, granted.entries.single().role)
  }

  @Test
  fun aclPatch_grantTwice_isIdempotent() {
    val granted = SurveyMutations.applyAcl(SurveyAcl(survey_id = "s1"), upsert = collectorGrant)

    assertEquals(granted, SurveyMutations.applyAcl(granted, upsert = collectorGrant))
  }

  @Test
  fun aclPatch_revoke_removesEntryByEmail() {
    val granted = SurveyMutations.applyAcl(SurveyAcl(survey_id = "s1"), upsert = collectorGrant)
    val revoke = SurveyAcl(entries = listOf(AclEntry(email = "a@example.org")))

    assertEquals(emptyList(), SurveyMutations.applyAcl(granted, delete = revoke).entries)
  }

  private val collectorGrant =
    SurveyAcl(entries = listOf(AclEntry(email = "a@example.org", role = Role.DATA_COLLECTOR)))

  @Test
  fun organizationPatches_areIdempotent() {
    val upserts =
      listOf(
        Organization(name = "Org"),
        Organization(listed = true),
        Organization(members = listOf(OrganizationMember(email = "a@example.org", user_id = "u1"))),
      )

    for (upsert in upserts) {
      val once = OrganizationMutations.apply(organization, upsert = upsert)
      assertEquals(once, OrganizationMutations.apply(once, upsert = upsert))
    }
  }

  @Test
  fun organizationPatch_memberUpsert_matchesExistingMemberEmailCaseInsensitively() {
    val upsert =
      Organization(members = listOf(OrganizationMember(email = "a@example.org", user_id = "u1")))

    val result = OrganizationMutations.apply(organization, upsert = upsert)

    assertEquals("u1", result.members.single().user_id)
  }

  private val organization =
    Organization(
      organization_id = "o1",
      members = listOf(OrganizationMember(email = "A@Example.org")),
    )

  @Test
  fun libraryPatch_upsertConcept_addsIt() {
    val added = LibraryMutations.apply(emptyLibrary, upsert = treeConceptUpsert)

    assertEquals(listOf("c1"), added.bundle?.concepts?.map { it.id })
  }

  @Test
  fun libraryPatch_upsertConceptTwice_isIdempotent() {
    val added = LibraryMutations.apply(emptyLibrary, upsert = treeConceptUpsert)

    assertEquals(added, LibraryMutations.apply(added, upsert = treeConceptUpsert))
  }

  @Test
  fun libraryPatch_deleteConcept_removesItById() {
    val added = LibraryMutations.apply(emptyLibrary, upsert = treeConceptUpsert)
    val remove =
      OrganizationLibraryRecord(bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1"))))

    assertEquals(emptyList(), LibraryMutations.apply(added, delete = remove).bundle?.concepts)
  }

  private val emptyLibrary = OrganizationLibraryRecord(organization_id = "o1")

  private val treeConceptUpsert =
    OrganizationLibraryRecord(
      bundle =
        LibraryBundle(
          concepts =
            listOf(ConceptDef(id = "c1", label = LocalizedText(values = mapOf("en" to "Tree"))))
        )
    )

  @Test
  fun entityPatch_appliedTwice_isIdempotent() {
    val once = DataMutations.applyEntity(plotEntity, delete = clearOldProperty, upsert = labelCrop)

    assertEquals(
      once,
      DataMutations.applyEntity(once, delete = clearOldProperty, upsert = labelCrop),
    )
  }

  @Test
  fun entityPatch_upsertLabel_setsLabel() {
    val once = DataMutations.applyEntity(plotEntity, delete = clearOldProperty, upsert = labelCrop)

    assertEquals("Plot 1", once.label)
  }

  @Test
  fun entityPatch_deleteOneAndUpsertAnotherProperty_replacesProperties() {
    val once = DataMutations.applyEntity(plotEntity, delete = clearOldProperty, upsert = labelCrop)

    assertEquals(mapOf("crop" to TypedValue(string_value = "coffee")), once.properties)
  }

  private val plotEntity =
    EntityRecord(entity_id = "e1", properties = mapOf("old" to TypedValue(string_value = "x")))

  private val clearOldProperty = EntityRecord(properties = mapOf("old" to TypedValue()))

  private val labelCrop =
    EntityRecord(
      label = "Plot 1",
      properties = mapOf("crop" to TypedValue(string_value = "coffee")),
    )

  @Test
  fun submissionPatch_isIdempotent() {
    val upsert = SubmissionRecord(entity_id = "e1", is_quarantined = true)
    val once =
      DataMutations.applySubmission(SubmissionRecord(submission_id = "sub1"), upsert = upsert)

    assertEquals(once, DataMutations.applySubmission(once, upsert = upsert))
  }

  @Test
  fun submissionUpsert_setsScalarFields() {
    val upsert = SubmissionRecord(entity_id = "e1", is_quarantined = true)

    val once =
      DataMutations.applySubmission(SubmissionRecord(submission_id = "sub1"), upsert = upsert)

    assertEquals("e1", once.entity_id)
    assertEquals(true, once.is_quarantined)
  }

  @Test
  fun submissionUpsert_setsMetadata() {
    val metadata = RecordMetadata(instance_id = "uuid:1", device_id = "device-1")

    val merged =
      DataMutations.applySubmission(
        SubmissionRecord(submission_id = "sub1"),
        upsert = SubmissionRecord(metadata = metadata),
      )

    assertEquals(metadata, merged.metadata)
  }

  @Test
  fun submissionDelete_clearsMetadata() {
    val record =
      SubmissionRecord(submission_id = "sub1", metadata = RecordMetadata(instance_id = "uuid:1"))

    val cleared =
      DataMutations.applySubmission(
        record,
        delete = SubmissionRecord(metadata = RecordMetadata()),
      )

    assertEquals(null, cleared.metadata)
  }

  @Test
  fun entityUpsert_canonicalGeometry_replacesALegacyPoint() {
    val legacy = EntityRecord(entity_id = "e1", point = GeoPoint(latitude = 1.0, longitude = 2.0))
    val moved = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491)

    val merged = DataMutations.applyEntity(legacy, upsert = EntityRecord(geometry = moved))

    assertEquals(moved, merged.geometry)
    assertEquals(null, merged.point)
  }

  @Test
  fun entityDelete_geometry_clearsBothForms() {
    val record =
      EntityRecord(
        entity_id = "e1",
        geometry = GeometryProtos.point(latitude = -0.4182, longitude = 36.9491),
      )

    val cleared = DataMutations.applyEntity(record, delete = EntityRecord(geometry = Geometry()))

    assertEquals(null, cleared.geometry)
  }

  @Test
  fun submissionUpsert_setsClientUpdatedAt() {
    val savedAt = ofEpochSecond(1_790_000_000L, 0L)

    val merged =
      DataMutations.applySubmission(
        SubmissionRecord(submission_id = "sub1"),
        upsert = SubmissionRecord(client_updated_at = savedAt),
      )

    assertEquals(savedAt, merged.client_updated_at)
  }

  @Test
  fun submissionUpsert_ignoresServerUpdatedAt() {
    val stamped = ofEpochSecond(1_790_000_000L, 0L)
    val record = SubmissionRecord(submission_id = "sub1", server_updated_at = stamped)

    val merged =
      DataMutations.applySubmission(
        record,
        upsert = SubmissionRecord(server_updated_at = ofEpochSecond(1L, 0L)),
      )

    assertEquals(stamped, merged.server_updated_at)
  }

  @Test
  fun submissionUpsert_rawPayloadReplacesRecord() {
    val record = SubmissionRecord(submission_id = "sub1", record = RecordInstance(form_id = "f1"))

    val merged =
      DataMutations.applySubmission(
        record,
        upsert = SubmissionRecord(raw_payload = "payload".encodeUtf8()),
      )

    assertEquals(null, merged.record)
    assertEquals("payload".encodeUtf8(), merged.raw_payload)
  }

  @Test
  fun submissionUpsert_recordReplacesRawPayload() {
    val record = SubmissionRecord(submission_id = "sub1", raw_payload = "payload".encodeUtf8())

    val merged =
      DataMutations.applySubmission(
        record,
        upsert = SubmissionRecord(record = RecordInstance(form_id = "f1")),
      )

    assertEquals(RecordInstance(form_id = "f1"), merged.record)
    assertEquals(null, merged.raw_payload)
  }

  @Test
  fun submissionDelete_clearsRawPayload() {
    val record = SubmissionRecord(submission_id = "sub1", raw_payload = "payload".encodeUtf8())

    val cleared =
      DataMutations.applySubmission(
        record,
        delete = SubmissionRecord(raw_payload = "x".encodeUtf8()),
      )

    assertEquals(null, cleared.raw_payload)
  }

  @Test
  fun entityUpsert_isDeleted_softDeletesEntity() {
    val entity = EntityRecord(entity_id = "e1", label = "Plot 1")

    val deleted = DataMutations.applyEntity(entity, upsert = EntityRecord(is_deleted = true))

    assertEquals(true, deleted.is_deleted)
  }

  @Test
  fun entityDelete_isDeleted_restoresEntityWithItsFields() {
    val deleted = EntityRecord(entity_id = "e1", label = "Plot 1", is_deleted = true)

    val restored = DataMutations.applyEntity(deleted, delete = EntityRecord(is_deleted = true))

    assertEquals(false, restored.is_deleted)
    assertEquals("Plot 1", restored.label)
  }

  @Test
  fun organizationUpsert_isDeleted_softDeletesOrganization() {
    val org = Organization(organization_id = "o1", name = "Org")

    val deleted = OrganizationMutations.apply(org, upsert = Organization(is_deleted = true))

    assertEquals(true, deleted.is_deleted)
  }

  @Test
  fun organizationDelete_isDeleted_restoresOrganizationWithItsFields() {
    val deleted = Organization(organization_id = "o1", name = "Org", is_deleted = true)

    val restored = OrganizationMutations.apply(deleted, delete = Organization(is_deleted = true))

    assertEquals(false, restored.is_deleted)
    assertEquals("Org", restored.name)
  }

  @Test
  fun surveyPatch_upsertFormTemplateOrigins_addsThem() {
    val result =
      SurveyMutations.apply(
        SurveyDef(survey_id = "s"),
        upsert = SurveyDef(form_template_origins = listOf(eudrOrigin, ukOrigin)),
      )

    assertEquals(listOf(eudrOrigin, ukOrigin), result.form_template_origins)
  }

  @Test
  fun surveyPatch_upsertExistingFormTemplateOrigin_replacesItByFormId() {
    val edited =
      SurveyMutations.apply(
        surveyWithBothOrigins,
        upsert = SurveyDef(form_template_origins = listOf(eudrOrigin.copy(modified = true))),
      )

    assertEquals(listOf(ukOrigin, eudrOrigin.copy(modified = true)), edited.form_template_origins)
  }

  @Test
  fun surveyPatch_deleteFormTemplateOrigin_removesItByFormId() {
    val removed =
      SurveyMutations.apply(
        surveyWithBothOrigins,
        delete = SurveyDef(form_template_origins = listOf(FormTemplateOrigin(form_id = "f2"))),
      )

    assertEquals(listOf(eudrOrigin), removed.form_template_origins)
  }

  private val eudrOrigin =
    FormTemplateOrigin(form_id = "f1", template_id = "eudr_plot_registration", template_version = 1)

  private val ukOrigin =
    FormTemplateOrigin(
      form_id = "f2",
      template_id = "uk_frc_plot_registration",
      template_version = 2,
    )

  private val surveyWithBothOrigins =
    SurveyDef(survey_id = "s", form_template_origins = listOf(eudrOrigin, ukOrigin))
}
