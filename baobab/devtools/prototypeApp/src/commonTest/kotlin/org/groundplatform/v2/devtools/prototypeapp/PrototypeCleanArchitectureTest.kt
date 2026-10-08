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
package org.groundplatform.v2.devtools.prototypeapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.client.auth.PrototypeAuthClient
import org.groundplatform.v2.devtools.prototypeapp.client.location.LocationClient
import org.groundplatform.v2.devtools.prototypeapp.client.places.PlacesResponseMapper
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.runNow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.store.seededStore
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocalStoreTransactionRunner
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.OrganizationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SampleDataRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.seed.PrototypeFakeOrganizationsData
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SurveyMapAnchor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.SyncStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ClusterMapFeaturesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ComputeWayfindingNavigationUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.GeneratePrototypeRandomSitesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.ResolveFormDefForLaunchUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SearchPlacesUseCase
import org.groundplatform.v2.devtools.prototypeapp.domain.usecase.SyncMutationsUseCase
import org.groundplatform.v2.devtools.prototypeapp.ui.state.AppUiState
import org.groundplatform.v2.devtools.prototypeapp.ui.viewmodel.SurveyAppViewModel

/**
 * Unit tests verifying the Clean Architecture & MVVM layers of `devtools/prototypeApp` per
 * `docs/technical/client/architecture.md`:
 * - Local data store seeded with sample data, read and written through repositories
 * - Client & Mapper layers ([PrototypeAuthClient], [LocationClient], [PlacesResponseMapper])
 * - Repository implementations ([SurveyRepositoryImpl], [MutationRepositoryImpl],
 *   [SettingsRepositoryImpl], [PlaceRepositoryImpl], [LocationRepositoryImpl],
 *   [OrganizationRepositoryImpl])
 * - Domain Use Cases ([ClusterMapFeaturesUseCase], [ComputeWayfindingNavigationUseCase],
 *   [SearchPlacesUseCase], [GeneratePrototypeRandomSitesUseCase], [SyncMutationsUseCase],
 *   [ResolveFormDefForLaunchUseCase])
 * - Presentation ViewModel ([SurveyAppViewModel] & `uiState: StateFlow<AppUiState>`)
 */
class PrototypeCleanArchitectureTest {

  @Test
  fun localStore_seedsSampleDataAndKeepsEditsAcrossSurveySwitches() = runNow {
    val store = seededStore()
    val surveyRepo = SurveyRepositoryImpl(store)
    val mutationRepo = MutationRepositoryImpl(store)
    val placeRepo = PlaceRepositoryImpl(store)
    val organizationRepo = OrganizationRepositoryImpl(store)
    assertTrue(surveyRepo.getSurveys().size >= 5)
    assertTrue(organizationRepo.getOrganizations().size >= 3)
    val memberships =
      organizationRepo.getOrganizationsFor(PrototypeFakeOrganizationsData.SIGNED_IN_EMAIL)
    assertTrue(memberships.any { it.id == PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE })
    assertTrue(surveyRepo.getSurveys().any { it.organizationId != null })
    assertTrue(surveyRepo.getSurveys().any { it.organizationId == null })
    assertTrue(placeRepo.getLocalPlaces().isNotEmpty())
    assertTrue(surveyRepo.getEntities().isNotEmpty())
    assertTrue(surveyRepo.getForms().isNotEmpty())
    assertTrue(mutationRepo.getMutations().isNotEmpty())
    assertTrue(surveyRepo.getOfflineTilePackages().isNotEmpty())
    assertEquals("survey-kenya-coffee", surveyRepo.getActiveSurveyId())

    // Edit the active survey, switch away, and switch back: the edit is still there.
    val kenyaEntities = surveyRepo.getEntities()
    surveyRepo.setEntities(kenyaEntities.drop(1))
    surveyRepo.setActiveSurveyId("survey-sample-plots-forest")
    assertEquals("survey-sample-plots-forest", surveyRepo.getActiveSurveyId())
    assertTrue(surveyRepo.getEntities().none { it.id == kenyaEntities.first().id })
    surveyRepo.setActiveSurveyId("survey-kenya-coffee")
    assertEquals(kenyaEntities.drop(1), surveyRepo.getEntities())

    // Resetting reseeds the sample data.
    SampleDataRepositoryImpl(store).resetToSampleData()
    assertEquals("survey-kenya-coffee", surveyRepo.getActiveSurveyId())
    assertEquals(kenyaEntities, surveyRepo.getEntities())
  }

  @Test
  fun clientAndMapperLayers_encapsulateAuthLocationStorageAndPlaceJsonMapping() {
    val authClient = PrototypeAuthClient()
    assertFalse(authClient.isSignedIn())
    val profile = authClient.signInWithGoogle()
    assertTrue(authClient.isSignedIn())
    assertEquals("Maya Lin", profile.displayName)
    authClient.signOut()
    assertFalse(authClient.isSignedIn())

    val locationClient = LocationClient()
    assertEquals(0.50f, locationClient.readCurrentLocation().normalizedX)
    locationClient.updateSimulatedGpsLocation(0.65f, 0.35f, "-0.4200°, 36.9500°")
    assertEquals(0.65f, locationClient.readCurrentLocation().normalizedX)
    locationClient.reset()
    assertEquals(0.50f, locationClient.readCurrentLocation().normalizedX)

    val mapped =
      PlacesResponseMapper.map(
        body =
          """{"type":"FeatureCollection","features":[{"id":"place-api-1","text":"Othaya Market","place_name":"Othaya Market, Nyeri County, Kenya","place_type":["locality"],"center":[36.9421,-0.5512]}]}""",
        query = "Othaya",
        near = SurveyMapAnchor.forSurvey(SurveyMapAnchor.DEFAULT_SURVEY_ID),
        defaultSubtitle = "Nyeri County, Kenya",
      )
    assertEquals(1, mapped.size)
    assertEquals("Othaya Market", mapped.first().name)
    assertEquals("Places API", mapped.first().sourceLabel)
  }

  @Test
  fun repositories_readAndMutateLocalStoreCleanly() = runNow {
    val store = seededStore()
    val surveyRepo = SurveyRepositoryImpl(store)
    val mutationRepo = MutationRepositoryImpl(store)
    val settingsRepo = SettingsRepositoryImpl(store)
    val placeRepo = PlaceRepositoryImpl(store)
    val locationRepo = LocationRepositoryImpl()

    // SurveyRepository CRUD
    val firstLayerId = surveyRepo.getMapLayers().first().id
    val initialVisibility = surveyRepo.getMapLayers().first().isVisible
    surveyRepo.toggleLayerVisibility(firstLayerId)
    assertEquals(!initialVisibility, surveyRepo.getMapLayers().first().isVisible)

    // SettingsRepository CRUD
    settingsRepo.updateMeasurementUnits(MeasurementUnitSystem.IMPERIAL)
    assertEquals(MeasurementUnitSystem.IMPERIAL, settingsRepo.getUserSettings().measurementUnits)
    settingsRepo.updateLanguage("fr")
    assertEquals("fr", settingsRepo.getUserSettings().language)
    val evicted = settingsRepo.evictUploadedMediaCache()
    assertTrue(evicted.fileCount > 0)
    assertEquals(0, settingsRepo.evictUploadedMediaCache().fileCount)

    // MutationRepository CRUD
    assertTrue(
      mutationRepo.getMutations().any { it.surveyId == "survey-kenya-coffee" && it.isOutbox }
    )
    assertTrue(
      mutationRepo.getMutations().any { it.surveyId == "survey-kenya-coffee" && !it.isOutbox }
    )

    // PlaceRepository CRUD
    assertTrue(placeRepo.getLocalPlaces().isNotEmpty())

    // LocationRepository CRUD
    locationRepo.updateGpsLocation(0.42f, 0.58f, "-0.4210°, 36.9490°")
    assertEquals(0.42f, locationRepo.getLocationSnapshot().normalizedX)
  }

  @Test
  fun domainUseCases_executeClusteringWayfindingPlaceSearchSyncAndRandomSiteGeneration() = runNow {
    val store = seededStore()
    val surveyRepo = SurveyRepositoryImpl(store)
    val mutationRepo = MutationRepositoryImpl(store)
    val placeRepo = PlaceRepositoryImpl(store)
    val transactionRunner = LocalStoreTransactionRunner(store)

    // 1. ClusterMapFeaturesUseCase
    val clusterUseCase = ClusterMapFeaturesUseCase()
    val features =
      listOf(
        MapClusterFeatureItem(
          id = "f1",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 1",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.30f,
          normalizedY = 0.30f,
        ),
        MapClusterFeatureItem(
          id = "f2",
          kind = MapFeatureKind.ENTITY,
          label = "Plot 2",
          colorHex = 0xFF1E8E3EL,
          colorCss = "#1E8E3E",
          markerSymbol = "✓",
          normalizedX = 0.31f,
          normalizedY = 0.31f,
        ),
      )
    val clusters = clusterUseCase(features, radiusNormalized = 0.10f)
    assertEquals(1, clusters.size)
    assertEquals(2, clusters.first().siteCount)

    val scaleBar = clusterUseCase.computeScaleBarSpec("survey-kenya-coffee", 0f)
    assertTrue(scaleBar.label.endsWith("m"))

    // 2. ComputeWayfindingNavigationUseCase
    val wayfindingUseCase = ComputeWayfindingNavigationUseCase()
    val vectorImperial =
      wayfindingUseCase(
        fromNormalizedX = 0.20f,
        fromNormalizedY = 0.20f,
        targetNormalizedX = 0.50f,
        targetNormalizedY = 0.50f,
        unitSystem = MeasurementUnitSystem.IMPERIAL,
      )
    assertTrue(
      vectorImperial.formattedDistance.endsWith("ft") ||
        vectorImperial.formattedDistance.endsWith("mi")
    )

    val navState =
      wayfindingUseCase.resolveActiveNavigationState(
        navigationTargetKind = NavigationTargetKind.ENTITY,
        navigationTargetId = surveyRepo.getEntities().first().id,
        fromNormalizedX = 0.50f,
        fromNormalizedY = 0.50f,
        unitSystem = MeasurementUnitSystem.METRIC,
        userGpsCoordinatesLabel = "-0.4198°, 36.9512°",
        entities = surveyRepo.getEntities(),
        allSubmissions = emptyList(),
        submissionGeometries = surveyRepo.getSubmissionGeometries(),
        findPlaceById = { null },
      )
    assertNotNull(navState)
    assertEquals(NavigationTargetKind.ENTITY, navState.targetKind)

    // 3. SearchPlacesUseCase
    val searchPlacesUseCase = SearchPlacesUseCase()
    val coordMatches =
      searchPlacesUseCase(
        query = "-0.4210, 36.9505",
        isAirplaneMode = false,
        listFilterTab = ListFilterTab.ALL,
        localPlaces = placeRepo.getLocalPlaces(),
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )
    assertTrue(coordMatches.isNotEmpty())
    assertEquals("place-coord-query", coordMatches.first().id)

    // 4. SyncMutationsUseCase
    val syncUseCase = SyncMutationsUseCase(mutationRepo, surveyRepo, transactionRunner)
    val notice = syncUseCase.syncAllOutboxMutations("survey-kenya-coffee")
    assertNotNull(notice)
    assertTrue(
      mutationRepo
        .getMutations()
        .filter { it.surveyId == "survey-kenya-coffee" }
        .all { it.state == MutationSyncState.UPLOADED }
    )
    assertTrue(surveyRepo.getEntities().all { it.syncStatus == SyncStatus.SYNCED })

    // 5. GeneratePrototypeRandomSitesUseCase
    val randomSitesUseCase = GeneratePrototypeRandomSitesUseCase(surveyRepo)
    val beforeCount = surveyRepo.getEntities().size
    val randomResult = assertNotNull(randomSitesUseCase(count = 25))
    assertEquals(beforeCount + 25, randomResult.totalEntityCount)

    // 6. ResolveFormDefForLaunchUseCase
    val resolveFormUseCase = ResolveFormDefForLaunchUseCase()
    val formDef =
      resolveFormUseCase(
        customFormDef = XFormsParseCache.formDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
        form = surveyRepo.getForms().first(),
        includeEntityRefStep = true,
      )
    assertTrue(XFormsXmlSerializer.serializeFormDef(formDef).contains("/data/target_entity"))
  }

  @Test
  fun surveyAppViewModel_exposesImmutableStateFlowAndOrchestratesMvvmStateTransitions() {
    val viewModel = SurveyAppViewModel()
    assertEquals(AppScreen.SIGN_IN, viewModel.uiState.value.currentScreen)
    assertFalse(viewModel.uiState.value.isSignedIn)

    // Onboarding flow via ViewModel
    viewModel.signInWithGoogle()
    assertTrue(viewModel.uiState.value.isSignedIn)
    assertEquals(AppScreen.TERMS_OF_SERVICE, viewModel.uiState.value.currentScreen)

    viewModel.acceptTermsOfService()
    assertTrue(viewModel.uiState.value.hasAcceptedTerms)
    assertEquals(AppScreen.DOWNLOAD_SURVEY, viewModel.uiState.value.currentScreen)

    viewModel.selectSurvey("survey-sample-plots-forest")
    assertEquals(AppScreen.MAIN_SURVEY, viewModel.uiState.value.currentScreen)
    assertEquals("survey-sample-plots-forest", viewModel.uiState.value.activeSurveyId)

    // Add random sites via ViewModel -> UseCase -> Repository -> StateFlow
    val initialEntityCount = viewModel.uiState.value.entities.size
    viewModel.addRandomSites(10)
    assertEquals(initialEntityCount + 10, viewModel.uiState.value.entities.size)

    // Sync all outbox mutations for survey-kenya-coffee via ViewModel -> UseCase -> Repository ->
    // StateFlow
    viewModel.selectSurvey("survey-kenya-coffee")
    viewModel.syncAllOutboxMutations()
    assertTrue(
      viewModel.uiState.value.mutations
        .filter { it.surveyId == "survey-kenya-coffee" }
        .all { it.state == MutationSyncState.UPLOADED }
    )

    // Update settings via ViewModel -> Repository -> StateFlow
    viewModel.updateUnitSystem(MeasurementUnitSystem.IMPERIAL)
    assertEquals(
      MeasurementUnitSystem.IMPERIAL,
      viewModel.uiState.value.userSettings.measurementUnits,
    )

    // Reset flow
    viewModel.resetPrototypeFlow()
    assertEquals(AppScreen.SIGN_IN, viewModel.uiState.value.currentScreen)
    assertNull(viewModel.uiState.value.activeSurveyNotice)
  }

  @Test
  fun prototypeAppState_exposesSynchronizedViewModelUiStateFlow() {
    val state = PrototypeAppState()
    state.signInWithGoogle()
    state.acceptTermsOfService()
    state.openSurvey("survey-single-point-land-use")

    val snapshot: AppUiState = state.uiState.value
    assertEquals(AppScreen.MAIN_SURVEY, snapshot.currentScreen)
    assertEquals("survey-single-point-land-use", snapshot.activeSurveyId)
    assertTrue(snapshot.entities.isNotEmpty())
    assertEquals(state.entities.size, snapshot.entities.size)
  }
}
