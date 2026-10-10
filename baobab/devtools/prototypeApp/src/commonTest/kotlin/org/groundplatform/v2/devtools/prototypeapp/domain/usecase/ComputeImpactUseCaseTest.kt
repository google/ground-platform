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
package org.groundplatform.v2.devtools.prototypeapp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.groundplatform.v2.core.impact.ALL_USERS_ORGANIZATION_ID
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.LocalStore
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.ImpactEventRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LibraryRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyEditorRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactAttributionLevel
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEvent
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactEventType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactFormat
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactGoals
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactHint
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcome
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyOutcomeKind

/**
 * [ComputeImpactUseCase] over the seeded sample data: the Kenya coffee parcels' linked columns,
 * plots shared between the Kenya Forest Service and Mekong surveys, the Kenya Forest Service's own
 * concepts (one suggesting a goal), and the opted-out Open Foris Community.
 */
class ComputeImpactUseCaseTest {
  private val compute = ComputeImpactUseCase()

  private fun snapshot(store: LocalStore = seededStore()): ImpactSnapshot = runNow {
    val surveys = SurveyRepositoryImpl(store)
    val editor = SurveyEditorRepositoryImpl(store)
    compute(
      sources =
        surveys.getSurveys().map {
          ImpactSurveySource(
            it,
            surveys.observeSurveyContent(it.id).first(),
            editor.getDraft(it.id),
          )
        },
      libraries = LibraryRepositoryImpl(store).observeLibraries().first(),
      organizations = OrganizationRepositoryImpl(store).getOrganizations(),
      events = ImpactEventRepositoryImpl(store).getEvents(),
    )
  }

  private fun event(
    type: ImpactEventType,
    surveyId: String = KENYA,
    profile: String? = null,
    id: String = "evt-${type.name}-${profile.orEmpty()}",
  ) =
    ImpactEvent(
      id = id,
      type = type,
      surveyId = surveyId,
      organizationId = KFS,
      exportProfileId = profile,
      featureCount = 3,
      areaHa = 5.85,
      occurredAt = "2026-10-01T00:00:00Z",
      actorUserId = "uid",
      isUploaded = true,
    )

  @Test
  fun allUsersIds_matchBetweenThePrototypeAndTheSharedJob() {
    assertEquals(Organization.ALL_USERS_ID, ALL_USERS_ORGANIZATION_ID)
  }

  @Test
  fun kenyaCoffee_addsUpItsLinkedColumnsByGoal() {
    val summary = assertNotNull(compute.surveySummary(snapshot(), KENYA))

    assertEquals(5, summary.features.uniqueCount)
    assertEquals(5, summary.features.mappedCount)
    assertEquals(9.12, summary.features.areaHa, 1e-6)
    assertEquals(3L, summary.producerCount)

    val goal = summary.goals.single()
    assertEquals(ImpactGoals.label("deforestation_free_supply_chains"), goal.title)
    val commodity = goal.indicators.first { it.conceptId == "eudr.commodity" }
    assertEquals("3 map features", commodity.valueText)
    assertEquals("Coffee", commodity.breakdown.single().label)
    assertEquals(1f, commodity.breakdown.single().fraction)
    val deforestationFree = goal.indicators.first { it.conceptId == "eudr.deforestation_free" }
    assertEquals("67% Yes", deforestationFree.valueText)
    assertEquals(listOf("Yes", "Unknown"), deforestationFree.breakdown.map { it.label })

    val own = summary.ownIndicators.associateBy { it.label }
    assertEquals("117", own.getValue("Shade trees").valueText)
    assertEquals("1,550 kg", own.getValue("Cherry delivered (kg)").valueText)
    assertTrue(
      "Not weighed" in summary.diagnostics.single().message,
      summary.diagnostics.toString(),
    )

    assertEquals(ImpactAttributionLevel.DATA_COLLECTED, summary.attribution.level)
    assertEquals(emptyList(), summary.hints)
  }

  @Test
  fun surveyWithoutPurposesOrLinks_explainsHowToGetNumbers() {
    val summary = assertNotNull(compute.surveySummary(snapshot(), "survey-sample-plots-forest"))

    assertEquals(listOf(ImpactHint.PICK_PURPOSE, ImpactHint.LINK_FIELDS), summary.hints)
    assertEquals(ImpactAttributionLevel.DATA_COLLECTED, summary.attribution.level)
  }

  @Test
  fun organization_countsItsSurveysTogether_withOutcomesAndAttribution() {
    val state = assertNotNull(compute.organizationSummary(snapshot(), KFS))

    assertEquals(14, state.features.uniqueCount)
    assertEquals(1, state.outcomes.unansweredCount)
    assertEquals(
      mapOf(ImpactAttributionLevel.DATA_COLLECTED to 2L, ImpactAttributionLevel.EXPLORATORY to 1L),
      state.attribution.distribution,
    )
    assertTrue(state.ownIndicators.any { it.label == "Shade trees" })
    assertEquals(3L, state.producerCount)
  }

  @Test
  fun organization_filtersBySurveyAndCountry() {
    val snapshot = snapshot()

    val coffee = assertNotNull(compute.organizationSummary(snapshot, KFS, surveyId = KENYA))
    assertEquals(5, coffee.features.uniqueCount)
    assertEquals(listOf("KE"), compute.organizationCountries(snapshot, KFS))
    val elsewhere = assertNotNull(compute.organizationSummary(snapshot, KFS, countryCode = "VN"))
    assertEquals(0, elsewhere.features.uniqueCount)
    assertTrue(ImpactHint.COLLECT_DATA in elsewhere.hints)
  }

  @Test
  fun global_countsSharedPlotsOnce_andKeepsOrganizationConceptsSeparate() {
    val global = compute.globalSummary(snapshot())

    // The Mekong survey maps the same five plots as the Kenya coffee survey.
    assertEquals(27, global.features.mappedCount)
    assertEquals(22, global.features.uniqueCount)
    assertEquals(5, global.features.duplicateCount)
    // Producer names are sensitive, so they never leave the organization.
    assertNull(global.producerCount)
    assertTrue(global.ownIndicators.isEmpty())
    assertTrue(global.goals.flatMap { it.indicators }.none { it.conceptId.startsWith("org.") })
    val suggested = global.suggestedIndicators.single()
    assertEquals("Ecosystem restoration", suggested.goalTitle)
    assertEquals("Kenya Forest Service", suggested.organizationName)
    assertEquals(listOf("Shade trees"), suggested.indicators.map { it.label })
    assertEquals(1, global.excludedOrganizationCount)
    // Shared plots count in the country of the copy that counts (Kenya), so Vietnam has none.
    assertEquals(listOf("KE"), global.countries.map { it.countryCode })
    assertEquals(14, global.countries.single().featureCount)
    assertNotNull(global.cells)
    // Only Kenya coffee has a global purpose.
    assertEquals(
      mapOf(ImpactAttributionLevel.DATA_COLLECTED to 1L),
      global.attribution.distribution,
    )
  }

  @Test
  fun optingOut_removesTheOrganizationFromGlobalNumbers_butNotItsOwn() {
    val store = seededStore()
    val before = snapshot(store)
    runNow {
      OrganizationRepositoryImpl(store).updateOrganization(KFS) {
        it.copy(excludeFromPlatformAggregates = true)
      }
    }
    val after = snapshot(store)

    val global = compute.globalSummary(after)
    assertEquals(2, global.excludedOrganizationCount)
    assertTrue(global.suggestedIndicators.isEmpty())
    assertTrue(global.countries.none { it.countryCode == "KE" })
    // The Mekong survey's five plots are too few to show Vietnam's numbers.
    val vietnam = global.countries.single { it.countryCode == "VN" }
    assertTrue(vietnam.isSuppressed)
    assertEquals(0, vietnam.featureCount)
    // Without Kenya Forest Service, the Mekong survey's copies of the shared plots count instead.
    assertEquals(13, global.features.uniqueCount)
    assertEquals(
      compute.organizationSummary(before, KFS),
      compute.organizationSummary(after, KFS),
    )
  }

  @Test
  fun purposeExport_andOfficialOutcome_raiseTheAttributionLevel() {
    val store = seededStore()
    val events = ImpactEventRepositoryImpl(store)
    runNow {
      events.append(event(ImpactEventType.EXPORT))
      events.append(event(ImpactEventType.RECEIPT_GENERATED))
    }
    val exported = assertNotNull(compute.surveySummary(snapshot(store), KENYA))
    assertEquals(ImpactAttributionLevel.DATA_COLLECTED, exported.attribution.level)
    assertEquals(1, exported.activity.first { it.type == ImpactEventType.EXPORT }.count)
    assertEquals(1, exported.activity.first { it.type == ImpactEventType.RECEIPT_GENERATED }.count)

    runNow { events.append(event(ImpactEventType.EXPORT, profile = "eudr_geojson")) }
    val viaProfile = assertNotNull(compute.surveySummary(snapshot(store), KENYA))
    assertEquals(ImpactAttributionLevel.PURPOSE_EXPORT, viaProfile.attribution.level)
    assertEquals(2, viaProfile.activity.first { it.type == ImpactEventType.EXPORT }.count)

    runNow {
      SurveyRepositoryImpl(store)
        .setSurveyOutcome(
          KENYA,
          SurveyOutcome(
            outcomes = setOf(SurveyOutcomeKind.SUBMITTED_EUDR_DDS),
            answeredAt = "2026-10-02T00:00:00Z",
            answeredBy = PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL,
          ),
        )
    }
    val submitted = assertNotNull(compute.surveySummary(snapshot(store), KENYA))
    assertEquals(ImpactAttributionLevel.OFFICIAL_SUBMISSION, submitted.attribution.level)
    assertEquals(listOf(SurveyOutcomeKind.SUBMITTED_EUDR_DDS to 1L), submitted.outcomes.uses)
  }

  @Test
  fun timestamps_parseSubmissionAndIsoFormats() {
    assertEquals(
      ComputeImpactUseCase.timestampMillis("2026-09-18T10:14:00Z"),
      ComputeImpactUseCase.timestampMillis("2026-09-18 10:14 UTC"),
    )
    assertTrue(ComputeImpactUseCase.timestampMillis("2026-09-18 10:14 UTC") > 0)
    assertEquals(0, ComputeImpactUseCase.timestampMillis("yesterday"))
  }

  @Test
  fun numbers_areFormattedWithoutLocaleApis() {
    assertEquals("1,550", ImpactFormat.number(1550.0))
    assertEquals("1,234,567.9", ImpactFormat.number(1_234_567.89))
    assertEquals("9.12 ha", ImpactFormat.hectares(9.12))
    assertEquals("12.5 ha", ImpactFormat.hectares(12.46))
    assertEquals("67%", ImpactFormat.percent(2.0 / 3))
    assertEquals("1 survey", ImpactFormat.count(1, "survey"))
    assertEquals("ha", ImpactFormat.unit("har"))
    assertEquals("Ecosystem restoration", ImpactGoals.label("ecosystem_restoration"))
    assertEquals("Soil health", ImpactGoals.label("soil_health"))
  }

  private companion object {
    const val KENYA = "survey-kenya-coffee"
    const val KFS = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE
  }
}
