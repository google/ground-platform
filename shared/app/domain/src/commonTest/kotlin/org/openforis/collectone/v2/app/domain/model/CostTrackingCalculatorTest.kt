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
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openforis.collectone.v2.app.domain.model.editor.Collaborator
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole

class CostTrackingCalculatorTest {
  @Test
  fun countPhotos_countsImageFileNamesAndMediaReferences() {
    val submission =
      submission(
        "a.JPG",
        "b.jpeg",
        " c.png ",
        "d.webp",
        "photo:123",
        "media:abc",
        "notes.txt",
        "Forest",
      )

    assertEquals(6, CostTrackingCalculator.countPhotos(submission))
  }

  @Test
  fun surveySummary_countsEntitiesByOriginAndSubmissions() {
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = emptyList(),
        surveys = listOf(survey("s1")),
        entitiesBySurvey =
          mapOf(
            "s1" to
              listOf(
                entity("e1"),
                entity("e2"),
                entity("e3", isGenerated = true),
                entity("e4", isFieldCreated = true),
              )
          ),
        submissionsBySurvey = mapOf("s1" to listOf(submission("x.jpg"), submission("text"))),
      )

    val summary = report.surveys.single()
    assertEquals(2, summary.predefinedEntityCount)
    assertEquals(1, summary.generatedEntityCount)
    assertEquals(1, summary.adhocEntityCount)
    assertEquals(2, summary.submissionCount)
    assertEquals(1, summary.photoCount)
    assertEquals("Personal Survey", summary.organizationName)
    assertEquals(QuotaTier.BASIC, summary.effectiveQuotaLimits.tier)
    assertEquals(4, report.totalEntitiesCount)
    assertEquals(2, report.totalSubmissionsCount)
    assertEquals(1, report.totalPhotosCount)
  }

  @Test
  fun surveyOverItsLimits_reportsEachViolation() {
    val limits =
      QuotaLimitsConfig(
        maxFormsPerSurvey = 1,
        maxQuestionsPerForm = 3,
        maxPredefinedEntities = 1,
        maxGeneratedEntities = 1,
        maxAclUsers = 1,
      )
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = emptyList(),
        surveys = listOf(survey("s1", quotaLimits = limits)),
        formsBySurvey = mapOf("s1" to listOf(form("f1", questions = 4), form("f2", questions = 1))),
        entitiesBySurvey =
          mapOf(
            "s1" to
              listOf(
                entity("e1"),
                entity("e2"),
                entity("g1", isGenerated = true),
                entity("g2", isGenerated = true),
              )
          ),
        aclsBySurvey = mapOf("s1" to listOf(collaborator("a@x.org"), collaborator("b@x.org"))),
      )

    assertEquals(
      listOf(
        "Forms limit exceeded (2 > 1)",
        "Questions per form limit exceeded (4 > 3)",
        "Predefined entities limit exceeded (2 > 1)",
        "Generated entities limit exceeded (2 > 1)",
        "ACL users limit exceeded (2 > 1)",
      ),
      report.surveys.single().violations,
    )
  }

  @Test
  fun zeroOverrideKeepsTheTierLimit() {
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = emptyList(),
        surveys = listOf(survey("s1")),
        formsBySurvey = mapOf("s1" to List(50) { form("f$it", questions = 1) }),
        globalQuotaLimits = QuotaLimitsConfig(tier = QuotaTier.BASIC, maxFormsPerSurvey = 0),
      )

    // The survey's BASIC tier allows 5 forms; the global override of 0 keeps that limit.
    assertEquals(listOf("Forms limit exceeded (50 > 5)"), report.surveys.single().violations)
  }

  @Test
  fun surveysOfAnOrganizationUseTheSponsoredTierAndRollUp() {
    val org = Organization(id = "org-1", name = "Forest Watch")
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations =
          listOf(org, Organization(id = "synthetic", name = "All", isSynthetic = true)),
        surveys =
          listOf(survey("s1", organizationId = "org-1"), survey("s2", organizationId = "org-1")),
        submissionsBySurvey =
          mapOf("s1" to listOf(submission("a.png")), "s2" to listOf(submission("b"))),
      )

    assertTrue(report.surveys.all { it.effectiveQuotaLimits.tier == QuotaTier.SPONSORED })
    assertTrue(report.surveys.all { it.organizationName == "Forest Watch" })
    // Synthetic organizations are left out of the organization summaries.
    val orgSummary = report.organizations.single()
    assertEquals(1, report.totalOrganizationsCount)
    assertEquals(QuotaTier.SPONSORED, orgSummary.tier)
    assertEquals(2, orgSummary.surveyCount)
    assertEquals(2, orgSummary.submissionCount)
    assertEquals(1, orgSummary.photoCount)
  }

  @Test
  fun organizationViolationsArePrefixedWithTheSurveyTitle() {
    val org = Organization(id = "org-1", name = "Forest Watch")
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = listOf(org),
        surveys =
          listOf(
            survey(
              "s1",
              organizationId = "org-1",
              quotaLimits = QuotaLimitsConfig(maxFormsPerSurvey = 1),
            )
          ),
        formsBySurvey = mapOf("s1" to listOf(form("f1"), form("f2"))),
      )

    assertEquals(
      listOf("Survey s1: Forms limit exceeded (2 > 1)"),
      report.organizations.single().violations,
    )
  }

  @Test
  fun userSummaries_collectOwnersCollaboratorsCollectorsAndMembers() {
    val org =
      Organization(
        id = "org-1",
        name = "Forest Watch",
        members = listOf(OrganizationMember(email = "member@x.org")),
      )
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = listOf(org),
        surveys = listOf(survey("s1", ownerEmail = "jane.doe@x.org")),
        submissionsBySurvey = mapOf("s1" to listOf(submission("a.jpg", collector = "COL@x.org"))),
        aclsBySurvey = mapOf("s1" to listOf(collaborator("editor@x.org"))),
      )

    assertEquals(
      setOf("jane.doe@x.org", "editor@x.org", "COL@x.org", "member@x.org"),
      report.users.map { it.userEmail }.toSet(),
    )
    val owner = report.users.single { it.userEmail == "jane.doe@x.org" }
    assertEquals("Jane Doe", owner.displayName)
    assertEquals(1, owner.ownedSurveyCount)
    assertEquals(1, owner.activeSurveyCount)
    val collector = report.users.single { it.userEmail == "COL@x.org" }
    assertEquals(1, collector.submissionCount)
    assertEquals(1, collector.photoCount)
    assertEquals(1, collector.activeSurveyCount)
    // Users are ranked by their submissions and ad hoc features.
    assertEquals("COL@x.org", report.users.first().userEmail)
    val member = report.users.single { it.userEmail == "member@x.org" }
    assertEquals(0, member.activeSurveyCount)
  }

  @Test
  fun userOverTheirQuotas_reportsViolationsAgainstTheGlobalLimits() {
    val limits =
      QuotaLimitsConfig(maxSurveysPerOwner = 1, maxSurveysPerUser = 1, maxAdhocEntitiesPerUser = 1)
    val owner = "owner@x.org"
    val adhoc =
      listOf("a1", "a2").map {
        entity(it, isFieldCreated = true, submissions = listOf(submission(collector = owner)))
      }
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = emptyList(),
        surveys = listOf(survey("s1", ownerEmail = owner), survey("s2", ownerEmail = owner)),
        entitiesBySurvey = mapOf("s1" to adhoc),
        globalQuotaLimits = limits,
      )

    assertEquals(
      listOf(
        "Owned surveys quota exceeded (2 > 1)",
        "Joined surveys quota exceeded (2 > 1)",
        "Ad-hoc entities quota exceeded (2 > 1)",
      ),
      report.users.single().violations,
    )
  }

  @Test
  fun surveysAreRankedByActivity() {
    val report =
      CostTrackingCalculator.calculatePlatformReport(
        organizations = emptyList(),
        surveys = listOf(survey("quiet"), survey("busy")),
        submissionsBySurvey = mapOf("busy" to listOf(submission(), submission())),
      )

    assertEquals(listOf("busy", "quiet"), report.surveys.map { it.surveyId })
  }

  private fun survey(
    id: String,
    organizationId: String? = null,
    ownerEmail: String = "",
    quotaLimits: QuotaLimitsConfig = QuotaLimitsConfig(),
  ) =
    SurveyPreviewItem(
      id = id,
      title = "Survey $id",
      description = "",
      location = "",
      isDownloaded = false,
      entityCount = 0,
      ownerEmail = ownerEmail,
      organizationId = organizationId,
      quotaLimits = quotaLimits,
    )

  private fun form(id: String, questions: Int = 1) =
    FormPreviewItem(
      id = id,
      title = id,
      description = "",
      version = "1",
      targetDatasetId = "",
      targetDatasetName = "",
      questionCount = questions,
      ctaLabel = "",
    )

  private fun entity(
    id: String,
    isGenerated: Boolean = false,
    isFieldCreated: Boolean = false,
    submissions: List<SubmissionPreviewItem> = emptyList(),
  ) =
    GeospatialEntityItem(
      id = id,
      label = id,
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "layer-plots",
      geoId = id,
      geometry = null,
      areaHectares = 0.0,
      perimeterMeters = 0,
      colorHex = 0,
      properties = emptyMap(),
      submissions = submissions,
      isGenerated = isGenerated,
      isFieldCreated = isFieldCreated,
    )

  private fun submission(vararg answers: String, collector: String = "") =
    SubmissionPreviewItem(
      id = "sub",
      formId = "f",
      formTitle = "F",
      formVersion = "1",
      collectorName = "",
      collectorEmail = collector,
      fields = answers.map { SubmissionFieldEntry("q", "Q", it) },
    )

  private fun collaborator(email: String) =
    Collaborator(email = email, role = CollaboratorRole.DATA_COLLECTOR)
}
