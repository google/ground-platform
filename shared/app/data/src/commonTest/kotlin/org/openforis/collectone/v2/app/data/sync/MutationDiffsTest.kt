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
package org.openforis.collectone.v2.app.data.sync

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GeoPoint
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.TypedValue
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.ImagerySource
import openforis.collectone.v1.survey.LayerDef
import openforis.collectone.v1.survey.MapConfig
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationType
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SharingPolicy
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyArea
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyOutcome
import openforis.collectone.v1.survey.SurveyState
import org.openforis.collectone.v2.core.mutation.DataMutations
import org.openforis.collectone.v2.core.mutation.LibraryMutations
import org.openforis.collectone.v2.core.mutation.OrganizationMutations
import org.openforis.collectone.v2.core.mutation.SurveyMutations

/**
 * The sparse diffs [MutationDiffs] builds must be minimal (one mutation per changed field or keyed
 * item) and, applied to the old value with the core `*Mutations`, reproduce the new one.
 */
class MutationDiffsTest {
  private fun text(value: String) = TypedValue(string_value = value)

  // Entities

  private val entity =
    EntityRecord(
      entity_id = "e1",
      dataset_id = "plots",
      label = "Plot 1",
      point = GeoPoint(latitude = 1.0, longitude = 2.0),
      properties = mapOf("owner" to text("Ana"), "crop" to text("maize")),
    )

  @Test
  fun entity_withOnlyServerBookkeepingChanged_isEmpty() {
    val bumped =
      entity.copy(
        revision = 7L,
        commit_timestamp = ofEpochSecond(10L, 0L),
        sync_status_key = "synced",
        is_deleted = true,
      )

    assertTrue(MutationDiffs.entity(entity, bumped).isEmpty)
  }

  @Test
  fun entity_labelAndPropertyEdits_becomeSparseUpsertAndDelete() {
    val edited =
      entity.copy(label = "Plot 1b", properties = mapOf("owner" to text("Bo"), "soil" to text("x")))

    val diff = MutationDiffs.entity(entity, edited)

    assertFalse(diff.requiresUpsert)
    assertEquals(
      EntityRecord(
        label = "Plot 1b",
        properties = mapOf("owner" to text("Bo"), "soil" to text("x")),
      ),
      diff.diff.upsert,
    )
    assertEquals(EntityRecord(properties = mapOf("crop" to TypedValue())), diff.diff.delete)
    assertEquals(edited, DataMutations.applyEntity(entity, diff.diff.delete, diff.diff.upsert))
  }

  @Test
  fun entity_clearedLabelAndGeometry_becomeDeletes() {
    val cleared = entity.copy(label = "", point = null)

    val diff = MutationDiffs.entity(entity, cleared)

    assertNull(diff.diff.upsert)
    assertEquals(EntityRecord(label = "Plot 1", geometry = Geometry()), diff.diff.delete)
    assertEquals(cleared, DataMutations.applyEntity(entity, diff.diff.delete, diff.diff.upsert))
  }

  @Test
  fun entity_movedPoint_upsertsTheNewGeometryOnly() {
    val moved = entity.copy(point = GeoPoint(latitude = 3.0, longitude = 4.0))

    val diff = MutationDiffs.entity(entity, moved)

    assertEquals(EntityRecord(point = moved.point), diff.diff.upsert)
    assertNull(diff.diff.delete)
  }

  @Test
  fun entity_changedFieldOutsideTheMasks_requiresUpsert() {
    val diff = MutationDiffs.entity(entity, entity.copy(dataset_id = "other"))

    assertTrue(diff.diff.isEmpty)
    assertTrue(diff.requiresUpsert)
    assertFalse(diff.isEmpty)
  }

  @Test
  fun entity_metadataEdits_emitSparsePatch_withoutOverwritingUnloadedProperties() {
    val edited =
      entity.copy(
        dataset_name = "Sample plots",
        layer_id = "layer-plots",
        geo_id = "geo-123",
        area_hectares = 1.25,
        perimeter_meters = 400,
        color_hex = 0xFF2E7D32L,
        singular_type_label = "Sample plot",
      )

    val diff = MutationDiffs.entity(entity, edited)

    assertFalse(diff.requiresUpsert)
    assertNull(diff.diff.delete)
    assertEquals(
      EntityRecord(
        dataset_name = "Sample plots",
        layer_id = "layer-plots",
        geo_id = "geo-123",
        area_hectares = 1.25,
        perimeter_meters = 400,
        color_hex = 0xFF2E7D32L,
        singular_type_label = "Sample plot",
      ),
      diff.diff.upsert,
    )
    // Applying the sparse patch to a server entity that holds unloaded whisp_* enrichment keeps it.
    val serverEntity =
      entity.copy(
        properties = entity.properties + ("whisp_Area" to TypedValue(string_value = "0.09"))
      )
    val merged = DataMutations.applyEntity(serverEntity, diff.diff.delete, diff.diff.upsert)
    assertEquals("0.09", merged.properties["whisp_Area"]?.string_value)
    assertEquals("Sample plots", merged.dataset_name)
    assertEquals("layer-plots", merged.layer_id)
    assertEquals("geo-123", merged.geo_id)
    assertEquals(0xFF2E7D32L, merged.color_hex)
  }

  // Submissions

  private val submission =
    SubmissionRecord(
      submission_id = "s1",
      survey_id = "survey",
      form_id = "form",
      form_version = "3",
      entity_id = "e1",
    )

  @Test
  fun submission_linkQuarantineAndReview_roundTrip() {
    val edited =
      submission.copy(
        entity_id = "e2",
        is_quarantined = true,
        review_status = ReviewStatus.REVIEW_STATUS_APPROVED,
      )

    val diff = MutationDiffs.submission(submission, edited)

    assertFalse(diff.requiresUpsert)
    assertNull(diff.diff.delete)
    assertEquals(
      SubmissionRecord(
        entity_id = "e2",
        is_quarantined = true,
        review_status = ReviewStatus.REVIEW_STATUS_APPROVED,
      ),
      diff.diff.upsert,
    )
    assertEquals(
      edited,
      DataMutations.applySubmission(submission, diff.diff.delete, diff.diff.upsert),
    )
  }

  @Test
  fun submission_unlinkAndClearedFields_becomeDeletes() {
    val reviewed =
      submission.copy(is_quarantined = true, review_status = ReviewStatus.REVIEW_STATUS_FLAGGED)
    val cleared =
      reviewed.copy(
        entity_id = "",
        form_version = "",
        is_quarantined = false,
        review_status = ReviewStatus.REVIEW_STATUS_UNSPECIFIED,
      )

    val diff = MutationDiffs.submission(reviewed, cleared)

    assertNull(diff.diff.upsert)
    assertEquals(
      SubmissionRecord(
        entity_id = "e1",
        form_version = "3",
        is_quarantined = true,
        review_status = ReviewStatus.REVIEW_STATUS_FLAGGED,
      ),
      diff.diff.delete,
    )
    assertEquals(
      cleared,
      DataMutations.applySubmission(reviewed, diff.diff.delete, diff.diff.upsert),
    )
  }

  @Test
  fun submission_serverStampsAreIgnored_otherFieldsRequireUpsert() {
    val stamped =
      submission.copy(
        revision = 2L,
        server_updated_at = ofEpochSecond(5L, 0L),
        commit_timestamp = ofEpochSecond(5L, 0L),
      )

    assertTrue(MutationDiffs.submission(submission, stamped).isEmpty)
    assertTrue(MutationDiffs.submission(submission, submission.copy(form_id = "f2")).requiresUpsert)
  }

  @Test
  fun upsertOf_dropsTheFieldsTheServerNumbersItself() {
    assertEquals(
      SurveyDef(survey_id = "a"),
      MutationDiffs.upsertOf(SurveyDef(survey_id = "a", saved_revision_number = 4L)),
    )
    assertEquals(
      submission,
      MutationDiffs.upsertOf(submission.copy(server_updated_at = ofEpochSecond(1L, 0L))),
    )
  }

  // Organizations

  private val organization =
    Organization(
      organization_id = "org",
      name = "Forest Watch",
      description = "Monitors forests",
      listed = true,
      organization_type = OrganizationType.GOVERNMENT_AGENCY,
      imagery_sources = listOf(ImagerySource(imagery_source_id = "img1", name = "Sat")),
      members =
        listOf(
          OrganizationMember(email = "ana@example.org"),
          OrganizationMember(email = "bo@x.org"),
        ),
    )

  private fun Organization.applying(
    mutations: List<openforis.collectone.v1.survey.OrganizationMutation>
  ) = mutations.fold(this) { org, m -> OrganizationMutations.apply(org, m.delete, m.upsert) }

  @Test
  fun organization_emitsOneMutationPerChangedFieldOrItem() {
    val edited =
      organization.copy(
        name = "Forest Watch Intl",
        description = "",
        listed = false,
        organization_type = OrganizationType.ORGANIZATION_TYPE_UNSPECIFIED,
        imagery_sources = emptyList(),
        members = listOf(OrganizationMember(email = "ana@example.org")),
      )

    val diff = MutationDiffs.organization(organization, edited)

    assertFalse(diff.requiresUpsert)
    assertEquals(6, diff.mutations.size)
    assertEquals(Organization(name = "Forest Watch Intl"), diff.mutations.first().upsert)
    assertEquals(
      Organization(members = listOf(OrganizationMember(email = "bo@x.org"))),
      diff.mutations.last().delete,
    )
    assertEquals(edited, organization.applying(diff.mutations))
  }

  @Test
  fun organization_versionChangesAreIgnored_otherFieldsRequireUpsert() {
    val bumped = organization.copy(version = 9L, commit_timestamp = ofEpochSecond(3L, 0L))

    assertEquals(
      OrganizationDiffShape(0, false),
      MutationDiffs.organization(organization, bumped).shape(),
    )
    assertTrue(
      MutationDiffs.organization(organization, organization.copy(organization_id = "o2"))
        .requiresUpsert
    )
  }

  private data class OrganizationDiffShape(val mutations: Int, val requiresUpsert: Boolean)

  private fun MutationDiffs.OrganizationDiff.shape() =
    OrganizationDiffShape(mutations.size, requiresUpsert)

  // Libraries

  @Test
  fun library_settingsAndConcepts_roundTrip() {
    val old =
      OrganizationLibraryRecord(
        organization_id = "org",
        settings = OrganizationLibrarySettings(hidden_global_entry_ids = listOf("g1")),
        bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c1"), ConceptDef(id = "c2"))),
      )
    val new =
      old.copy(
        settings = OrganizationLibrarySettings(),
        bundle = LibraryBundle(concepts = listOf(ConceptDef(id = "c2"), ConceptDef(id = "c3"))),
      )

    val mutations = MutationDiffs.library(old, new)

    assertEquals(3, mutations.size)
    assertEquals(
      OrganizationLibraryRecord(settings = OrganizationLibrarySettings()),
      mutations.first().delete,
    )
    val applied =
      mutations.fold(old) { record, m -> LibraryMutations.apply(record, m.delete, m.upsert) }
    assertEquals(new.bundle, applied.bundle)
    // Clearing the settings keeps the library's organization ID on them.
    assertEquals(emptyList(), applied.settings?.hidden_global_entry_ids)
  }

  @Test
  fun library_unchanged_hasNoMutations() {
    val record = OrganizationLibraryRecord(organization_id = "org")

    assertEquals(emptyList(), MutationDiffs.library(record, record.copy()))
  }

  // Surveys

  private val survey =
    SurveyDef(
      survey_id = "s",
      title = "Baseline",
      state = SurveyState.PUBLISHED,
      closed_at = ofEpochSecond(100L, 0L),
      outcome = SurveyOutcome(outcomes = listOf(SurveyOutcome.Outcome.NOT_YET)),
      purpose_ids = listOf("p1"),
      forms = listOf(FormDef(form_id = "f1"), FormDef(form_id = "f2")),
      map_config =
        MapConfig(
          initial_zoom = 5.0,
          layers = listOf(LayerDef(id = "l1"), LayerDef(id = "l2")),
          survey_area = SurveyArea(name = "Area"),
        ),
    )

  private fun SurveyDef.applying(mutations: List<SurveyMutation>) =
    mutations.fold(this) { def, m -> SurveyMutations.apply(def, m.delete, m.upsert) }

  @Test
  fun survey_scalarAndListEdits_roundTrip() {
    val edited =
      survey.copy(
        title = "Baseline 2026",
        state = SurveyState.SURVEY_STATE_UNSPECIFIED,
        closed_at = null,
        outcome = null,
        purpose_ids = emptyList(),
        forms = listOf(FormDef(form_id = "f2"), FormDef(form_id = "f3")),
      )

    val mutations = MutationDiffs.survey(survey, edited)

    assertEquals(SurveyDef(title = "Baseline 2026"), mutations.first().upsert)
    assertTrue(SurveyMutation(delete = SurveyDef(state = SurveyState.PUBLISHED)) in mutations)
    assertTrue(SurveyMutation(delete = SurveyDef(closed_at = survey.closed_at)) in mutations)
    assertTrue(SurveyMutation(delete = SurveyDef(outcome = SurveyOutcome())) in mutations)
    assertTrue(
      SurveyMutation(delete = SurveyDef(forms = listOf(FormDef(form_id = "f1")))) in mutations
    )
    assertEquals(edited, survey.applying(mutations))
  }

  @Test
  fun survey_areaAndLayerEdits_staySparseWithinTheMapConfig() {
    val edited =
      survey.copy(
        map_config =
          survey.map_config!!.copy(
            layers = listOf(LayerDef(id = "l1")),
            survey_area = SurveyArea(name = "Area 2"),
          )
      )

    val mutations = MutationDiffs.survey(survey, edited)

    assertEquals(
      listOf(
        SurveyMutation(
          upsert = SurveyDef(map_config = MapConfig(survey_area = SurveyArea(name = "Area 2")))
        ),
        SurveyMutation(
          delete = SurveyDef(map_config = MapConfig(layers = listOf(LayerDef(id = "l2"))))
        ),
      ),
      mutations,
    )
    assertEquals(edited, survey.applying(mutations))
  }

  @Test
  fun survey_otherMapConfigEdit_upsertsTheWholeMapConfig() {
    val edited = survey.copy(map_config = survey.map_config!!.copy(initial_zoom = 9.0))

    assertEquals(
      listOf(SurveyMutation(upsert = SurveyDef(map_config = edited.map_config))),
      MutationDiffs.survey(survey, edited),
    )
  }

  @Test
  fun survey_updatedForm_preservesMidFormInsertOrderAndRemovesDeletedItems() {
    fun control(ref: String) =
      ViewComponent(control = ControlDef(field_ref = ref, type = ControlType.CONTROL_INPUT))
    val initialForm =
      FormDef(
        form_id = "f1",
        title = "Plot Visit",
        version = "1",
        default_language = "en",
        model =
          ModelDef(
            bindings =
              listOf(
                FieldBinding(field_path = "/data/q1"),
                FieldBinding(field_path = "/data/q2"),
                FieldBinding(field_path = "/data/q3"),
              )
          ),
        view = ViewDef(components = listOf(control("q1"), control("q2"), control("q3"))),
      )
    val updatedForm =
      FormDef(
        form_id = "f1",
        title = "Plot Visit",
        version = "1",
        default_language = "",
        model =
          ModelDef(
            bindings =
              listOf(
                FieldBinding(field_path = "/data/q0"),
                FieldBinding(field_path = "/data/q1"),
                FieldBinding(field_path = "/data/q_mid"),
                FieldBinding(field_path = "/data/q3"),
              )
          ),
        view =
          ViewDef(
            components = listOf(control("q0"), control("q1"), control("q_mid"), control("q3"))
          ),
      )
    val before = survey.copy(forms = listOf(initialForm))
    val after = survey.copy(forms = listOf(updatedForm))

    val mutations = MutationDiffs.survey(before, after)
    val merged = before.applying(mutations)

    assertEquals(updatedForm, merged.forms.single())
    assertEquals(
      listOf("q0", "q1", "q_mid", "q3"),
      merged.forms.single().view!!.components.mapNotNull { it.control?.field_ref },
    )
  }

  @Test
  fun survey_removedMapConfigAndArea_becomeDeletes() {
    assertEquals(
      listOf(SurveyMutation(delete = SurveyDef(map_config = MapConfig()))),
      MutationDiffs.survey(survey, survey.copy(map_config = null)),
    )
    assertEquals(
      listOf(
        SurveyMutation(delete = SurveyDef(map_config = MapConfig(survey_area = SurveyArea())))
      ),
      MutationDiffs.survey(
        survey,
        survey.copy(map_config = survey.map_config!!.copy(survey_area = null)),
      ),
    )
  }

  @Test
  fun survey_entityCountAndLocationLabel_roundTripThroughApply() {
    val withFeatures =
      survey.copy(entity_count = 414, location_label = "Arusha, Tanzania", version = "2026111401")
    val mutations = MutationDiffs.survey(survey, withFeatures)
    assertEquals(withFeatures, survey.applying(mutations))

    val cleared = withFeatures.copy(entity_count = 0, location_label = "")
    val clearMutations = MutationDiffs.survey(withFeatures, cleared)
    assertEquals(cleared, withFeatures.applying(clearMutations))
  }

  // ACLs

  private val acl =
    SurveyAcl(
      survey_id = "s",
      entries =
        listOf(
          AclEntry(id = "a1", email = "ana@example.org", role = Role.SURVEY_ORGANIZER),
          AclEntry(email = "bo@example.org", user_id = "u-bo", role = Role.DATA_COLLECTOR),
        ),
      sharing_policy = SharingPolicy.RESTRICTED,
      collector_consent_required = true,
      collector_consent_text = "I agree",
      default_role = Role.VIEWER,
    )

  @Test
  fun acl_removedCollaborator_isDeletedByItsIdentity() {
    val edited = acl.copy(entries = acl.entries.take(1))

    val mutations = MutationDiffs.acl(acl, edited)

    assertEquals(
      listOf(
        SurveyMutation(
          acl_delete =
            SurveyAcl(entries = listOf(AclEntry(email = "bo@example.org", user_id = "u-bo")))
        )
      ),
      mutations,
    )
  }

  @Test
  fun acl_accessRuleEdits_combineIntoOneMutation() {
    val edited =
      acl.copy(
        sharing_policy = SharingPolicy.PUBLIC,
        collector_consent_required = false,
        collector_consent_text = "",
        default_role = Role.ROLE_UNSPECIFIED,
        owner_id = "owner@example.org",
      )

    val mutations = MutationDiffs.acl(acl, edited)

    assertEquals(
      listOf(
        SurveyMutation(
          acl_delete =
            SurveyAcl(
              collector_consent_required = true,
              collector_consent_text = "I agree",
              default_role = Role.VIEWER,
            ),
          acl_upsert =
            SurveyAcl(sharing_policy = SharingPolicy.PUBLIC, owner_id = "owner@example.org"),
        )
      ),
      mutations,
    )
  }

  @Test
  fun acl_addedAndEditedCollaborators_roundTrip() {
    val edited =
      acl.copy(
        entries =
          listOf(
            acl.entries[0],
            acl.entries[1].copy(role = Role.VIEWER),
            AclEntry(email = "cy@example.org", role = Role.DATA_COLLECTOR),
          )
      )

    val mutations = MutationDiffs.acl(acl, edited)

    assertEquals(2, mutations.size)
    assertEquals(
      edited,
      mutations.fold(acl) { a, m -> SurveyMutations.applyAcl(a, m.acl_delete, m.acl_upsert) },
    )
  }
}
