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

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionMutation
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryMutation
import openforis.collectone.v1.library.OrganizationLibraryRecord
import openforis.collectone.v1.library.OrganizationLibrarySettings
import openforis.collectone.v1.survey.Organization
import openforis.collectone.v1.survey.OrganizationMember
import openforis.collectone.v1.survey.OrganizationMutation
import openforis.collectone.v1.survey.OrganizationType
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation

/**
 * [OutboxCoalescer] keys organization, library, and submission patches per field or keyed item, so
 * the outbox keeps one pending value for each.
 */
class OutboxCoalescerFieldKeysTest {
  private var seq = 0L

  private fun pending(target: MutationTarget, key: String, payload: MutationPayload) =
    PendingMutation(
      mutationId = "m${++seq}",
      clientSeq = seq,
      target = target,
      recordKey = key,
      payload = payload,
    )

  private fun org(delete: Organization? = null, upsert: Organization? = null) =
    pending(
      MutationTarget.Organization("org"),
      Rebaser.organizationKey("org"),
      MutationPayload.Organization(OrganizationMutation(delete = delete, upsert = upsert)),
    )

  private fun library(upsert: OrganizationLibraryRecord) =
    pending(
      MutationTarget.Library("org"),
      Rebaser.libraryKey("org"),
      MutationPayload.Library(LibraryMutation(upsert = upsert)),
    )

  private fun submission(upsert: SubmissionRecord) =
    pending(
      MutationTarget.Data("s"),
      Rebaser.submissionKey("sub"),
      MutationPayload.Data(
        DataMutation(
          submission_mutation = SubmissionMutation(submission_id = "sub", upsert = upsert)
        )
      ),
    )

  private fun entity(upsert: EntityRecord) =
    pending(
      MutationTarget.Data("s"),
      Rebaser.entityKey("e"),
      MutationPayload.Data(
        DataMutation(entity_mutation = EntityMutation(entity_id = "e", upsert = upsert))
      ),
    )

  private fun enqueue(vararg mutations: PendingMutation) =
    mutations.fold(emptyList<PendingMutation>()) { outbox, m ->
      OutboxCoalescer.coalesce(outbox, m)
    }

  @Test
  fun organizationKeys_nameEachFieldAndKeyedItem() {
    val keys =
      OutboxCoalescer.fieldKeys(
        org(
            upsert =
              Organization(
                name = "n",
                description = "d",
                website_url = "w",
                logo_url = "l",
                country_code = "KE",
                listed = true,
                exclude_from_platform_aggregates = true,
                organization_type = OrganizationType.GOVERNMENT_AGENCY,
                members = listOf(OrganizationMember(email = "Ana@Example.org")),
              )
          )
          .payload
      )

    assertEquals(
      setOf(
        "name",
        "description",
        "website_url",
        "logo_url",
        "country_code",
        "listed",
        "exclude_from_platform_aggregates",
        "organization_type",
        "members/ana@example.org",
      ),
      keys,
    )
  }

  @Test
  fun wholeOrganizationUpsert_hasNoFieldKeys() {
    assertEquals(
      emptySet(),
      OutboxCoalescer.fieldKeys(org(upsert = Organization(organization_id = "org")).payload),
    )
  }

  @Test
  fun organizationPatches_keepTheLatestValuePerField() {
    val first = org(upsert = Organization(name = "A"))
    val other = org(upsert = Organization(description = "D"))
    val latest = org(upsert = Organization(name = "B"))

    assertEquals(listOf(other, latest), enqueue(first, other, latest))
  }

  @Test
  fun libraryPatches_keepTheLatestValuePerConceptAndSettings() {
    fun concept(id: String, version: Int) =
      library(
        OrganizationLibraryRecord(
          bundle = LibraryBundle(concepts = listOf(ConceptDef(id = id, version = version)))
        )
      )
    val settings = library(OrganizationLibraryRecord(settings = OrganizationLibrarySettings()))
    val c1 = concept("c1", 1)
    val c2 = concept("c2", 1)
    val c1Again = concept("c1", 2)

    assertEquals(listOf(settings, c2, c1Again), enqueue(settings, c1, c2, c1Again))
  }

  @Test
  fun submissionPatches_dropOnlyTheSupersededFields() {
    val linkAndReview =
      submission(
        SubmissionRecord(entity_id = "e1", review_status = ReviewStatus.REVIEW_STATUS_FLAGGED)
      )
    val review = submission(SubmissionRecord(review_status = ReviewStatus.REVIEW_STATUS_APPROVED))

    val outbox = enqueue(linkAndReview, review)

    assertEquals(2, outbox.size)
    val remaining = (outbox.first().payload as MutationPayload.Data).mutation.submission_mutation
    assertEquals(SubmissionRecord(entity_id = "e1"), remaining?.upsert)
    assertEquals(review, outbox.last())
  }

  @Test
  fun submissionPatchFullySuperseded_isDropped() {
    val quarantine = submission(SubmissionRecord(is_quarantined = true, form_version = "2"))
    val again = submission(SubmissionRecord(is_quarantined = true, form_version = "3"))

    assertEquals(listOf(again), enqueue(quarantine, again))
  }

  @Test
  fun entityFlagPatches_areKeyedSeparately() {
    val fieldCreated = entity(EntityRecord(is_field_created = true, label = "Plot"))
    val relabel = entity(EntityRecord(label = "Plot 2"))

    val outbox = enqueue(fieldCreated, relabel)

    val remaining = (outbox.first().payload as MutationPayload.Data).mutation.entity_mutation
    assertEquals(EntityRecord(is_field_created = true), remaining?.upsert)
    assertEquals(relabel, outbox.last())
  }
}
