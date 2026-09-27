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
import org.groundplatform.v2.devtools.prototypeapp.client.storage.OfflineTileStorageClient
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.local.PrototypeAppDataStore
import org.groundplatform.v2.devtools.prototypeapp.data.mapper.PlaceJsonMapper
import org.groundplatform.v2.devtools.prototypeapp.data.repository.LocationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.MutationRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.PlaceRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SettingsRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AppScreen
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ListFilterTab
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapClusterFeatureItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MapFeatureKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MeasurementUnitSystem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.MutationSyncState
import org.groundplatform.v2.devtools.prototypeapp.domain.model.NavigationTargetKind
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
 * - Static / hardcoded [PrototypeAppDataStore]
 * - Client & Mapper layers ([PrototypeAuthClient], [LocationClient], [OfflineTileStorageClient],
 *   [PlaceJsonMapper])
 * - Repository implementations ([SurveyRepositoryImpl], [MutationRepositoryImpl],
 *   [SettingsRepositoryImpl], [PlaceRepositoryImpl], [LocationRepositoryImpl])
 * - Domain Use Cases ([ClusterMapFeaturesUseCase], [ComputeWayfindingNavigationUseCase],
 *   [SearchPlacesUseCase], [GeneratePrototypeRandomSitesUseCase], [SyncMutationsUseCase],
 *   [ResolveFormDefForLaunchUseCase])
 * - Presentation ViewModel ([SurveyAppViewModel] & `uiState: StateFlow<AppUiState>`)
 */
class PrototypeCleanArchitectureTest {

  @Test
  fun prototypeAppDataStore_providesAllStaticSampleDataAndSwitchesSurveyDatasets() {
    val dataStore = PrototypeAppDataStore()
    assertTrue(PrototypeAppDataStore.defaultSampleSurveys().size >= 5)
    assertTrue(PrototypeAppDataStore.defaultSurveyPlaces().isNotEmpty())
    assertTrue(PrototypeAppDataStore.defaultGeospatialEntities().isNotEmpty())
    assertTrue(PrototypeAppDataStore.defaultForms().isNotEmpty())
    assertTrue(PrototypeAppDataStore.defaultMutations().isNotEmpty())
    assertTrue(PrototypeAppDataStore.defaultOfflineTilePackages().isNotEmpty())

    assertEquals("survey-kenya-coffee", dataStore.activeSurveyId)
    assertEquals(
      PrototypeAppDataStore.entitiesForSurvey("survey-kenya-coffee").size,
      dataStore.entities.size,
    )

    // Switch dataset to Tanzania mangrove survey
    dataStore.loadSurveyDatasets("survey-tanzania-mangrove")
    assertEquals("survey-tanzania-mangrove", dataStore.activeSurveyId)
    assertEquals(
      PrototypeAppDataStore.entitiesForSurvey("survey-tanzania-mangrove").size,
      dataStore.entities.size,
    )

    // Reset back to defaults
    dataStore.resetToDefaults()
    assertEquals("survey-kenya-coffee", dataStore.activeSurveyId)
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

    val storageClient = OfflineTileStorageClient()
    val pkg = storageClient.buildOfflineTilePackage("pkg-1", "Nyeri North", "12-16z", "24 MB")
    assertEquals("pkg-1", pkg.id)
    assertTrue(pkg.isDownloaded)
    assertEquals(1, storageClient.evictUploadedMediaCache())

    val mapper = PlaceJsonMapper()
    val mapped =
      mapper.mapJsonToPlaces(
        json =
          """[{"id":"place-api-1","name":"Othaya Market","categoryLabel":"Town","coordinatesLabel":"0.5512° S, 36.9421° E"}]""",
        defaultRegionSubtitle = "Nyeri County, Kenya",
        surveyLng = 36.9512,
        surveyLat = -0.4198,
      )
    assertEquals(1, mapped.size)
    assertEquals("Othaya Market", mapped.first().name)
    assertEquals("Places API", mapped.first().sourceLabel)
  }

  @Test
  fun repositories_readAndMutateDataStoreCleanly() {
    val dataStore = PrototypeAppDataStore()
    val surveyRepo = SurveyRepositoryImpl(dataStore)
    val mutationRepo = MutationRepositoryImpl(dataStore)
    val settingsRepo = SettingsRepositoryImpl(dataStore)
    val placeRepo = PlaceRepositoryImpl(dataStore)
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
    settingsRepo.evictUploadedMediaCache()
    assertEquals(0, settingsRepo.getUploadedMediaFileCount())

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
  fun domainUseCases_executeClusteringWayfindingPlaceSearchSyncAndRandomSiteGeneration() {
    val dataStore = PrototypeAppDataStore()
    val surveyRepo = SurveyRepositoryImpl(dataStore)
    val mutationRepo = MutationRepositoryImpl(dataStore)

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
        localPlaces = dataStore.places,
        remoteApiPlaces = emptyList(),
        surveyLocationLabel = "Nyeri County, Kenya",
        surveyBaseLng = 36.9512,
        surveyBaseLat = -0.4198,
      )
    assertTrue(coordMatches.isNotEmpty())
    assertEquals("place-coord-query", coordMatches.first().id)

    // 4. SyncMutationsUseCase
    val syncUseCase = SyncMutationsUseCase(mutationRepo, surveyRepo)
    val notice = syncUseCase.syncAllOutboxMutations("survey-kenya-coffee")
    assertNotNull(notice)
    assertTrue(mutationRepo.getMutations().all { it.state == MutationSyncState.UPLOADED })
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
        customFormDef =
          PrototypeAppDataStore.cachedExampleFormDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
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

    viewModel.selectSurvey("survey-tanzania-mangrove")
    assertEquals(AppScreen.MAIN_SURVEY, viewModel.uiState.value.currentScreen)
    assertEquals("survey-tanzania-mangrove", viewModel.uiState.value.activeSurveyId)

    // Add random sites via ViewModel -> UseCase -> Repository -> StateFlow
    val initialEntityCount = viewModel.uiState.value.entities.size
    viewModel.addRandomSites(10)
    assertEquals(initialEntityCount + 10, viewModel.uiState.value.entities.size)

    // Sync all outbox mutations for survey-kenya-coffee via ViewModel -> UseCase -> Repository -> StateFlow
    viewModel.selectSurvey("survey-kenya-coffee")
    viewModel.syncAllOutboxMutations()
    assertTrue(viewModel.uiState.value.mutations.all { it.state == MutationSyncState.UPLOADED })

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
    state.openSurvey("survey-brazil-pasture")

    val snapshot: AppUiState = state.uiState.value
    assertEquals(AppScreen.MAIN_SURVEY, snapshot.currentScreen)
    assertEquals("survey-brazil-pasture", snapshot.activeSurveyId)
    assertEquals(state.entities.size, snapshot.entities.size)
  }
}
