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
package org.openforis.collectone.v2.mobile

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.openforis.collectone.v2.app.di.AppDataHolder
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.OfflineAreaLayerStatus
import org.openforis.collectone.v2.app.domain.model.OfflineAreaName
import org.openforis.collectone.v2.app.domain.model.OfflineImageryType
import org.openforis.collectone.v2.app.domain.model.OfflineRegionMode
import org.openforis.collectone.v2.app.domain.repository.OfflineMapRepository
import org.openforis.collectone.v2.app.testing.FakeOfflineMapRepository
import org.openforis.collectone.v2.app.ui.navigation.MainSurveyViewMode
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.map.LngLatBounds
import org.openforis.collectone.v2.mobile.ui.MobileAppState
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute
import org.openforis.collectone.v2.mobile.ui.offlinemaps.OfflineAreaSelectionViewModel

/**
 * Golden-screenshot tests that render Collect One mobile app screens in both light and dark themes
 * and verify them against checked-in golden images in `src/jvmTest/resources/goldens/`.
 *
 * Set `UPDATE_GOLDENS=true` to regenerate the golden images. Set `THEME_SCREENSHOT_DIR=<path>` to
 * also export copies of the rendered PNGs to that directory.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeScreenshotTest {
  private val exportDir: File? = System.getenv("THEME_SCREENSHOT_DIR")?.let(::File)
  private val updateGoldens: Boolean = System.getenv("UPDATE_GOLDENS")?.toBoolean() == true

  /**
   * Scenes that don't match their golden. The test reports them all at the end, not just the first.
   */
  private val mismatches = mutableListOf<String>()

  private data class MobileScene(
    val id: String,
    val initialRoute: MobileRoute,
    val setUp: MobileAppState.() -> Unit = {},
    val holder: () -> AppDataHolder = ::mobileSampleDataHolder,
    /** Waits until this holds before capturing (e.g. for the area estimate). */
    val isReady: MobileAppState.() -> Boolean = { true },
  )

  private val scenes: List<MobileScene> =
    listOf(
      MobileScene(id = "01_mobile_sign_in", initialRoute = MobileRoute.SignIn),
      MobileScene(id = "02_mobile_terms_of_service", initialRoute = MobileRoute.TermsOfService),
      MobileScene(id = "03_mobile_download_survey", initialRoute = MobileRoute.DownloadSurvey),
      MobileScene(id = "04_mobile_main_map_peek", initialRoute = MobileRoute.MainSurvey),
      MobileScene(
        id = "05_mobile_entity_details",
        initialRoute = MobileRoute.MainSurvey,
        setUp = {
          viewModels.surveyMap.selectEntity(
            viewModels.dataCollection.uiState.value.entities.first().id
          )
        },
      ),
      MobileScene(
        id = "06_mobile_searchable_list",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { setMainSurveyViewMode(MainSurveyViewMode.LIST) },
      ),
      MobileScene(
        id = "07_mobile_navigation_drawer",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { updateDrawerOpen(true) },
      ),
      MobileScene(
        id = "08_mobile_user_profile_panel",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { updateUserProfileOpen(true) },
      ),
      MobileScene(
        id = "09_mobile_data_collection_form",
        initialRoute = MobileRoute.MainSurvey,
        setUp = {
          viewModels.dataCollection.launchFormForEntity(
            entityId = "0000000a-56d7-5c1d-9457-7457267120a3",
            formId = "form-shade-canopy-audit",
          )
        },
      ),
      MobileScene(
        id = "10_mobile_downloaded_surveys",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { drawerSwitchSurveys() },
      ),
      MobileScene(
        id = "11_mobile_uploads",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { drawerOpenUploads() },
      ),
      MobileScene(
        id = "12_mobile_offline_maps",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { drawerManageOfflineMaps() },
      ),
      MobileScene(
        id = "13_mobile_settings",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { drawerOpenSettings() },
      ),
      MobileScene(
        id = "14_mobile_offline_maps_saved",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { drawerManageOfflineMaps() },
        holder = { offlineMapsHolder(savedAreas = true) },
      ),
      MobileScene(
        id = "15_mobile_save_area",
        initialRoute = MobileRoute.MainSurvey,
        setUp = { startOfflineAreaSelection() },
        holder = { offlineMapsHolder(savedAreas = false) },
        isReady = {
          val selection = offlineAreaSelection.uiState.value
          selection.estimate != null && selection.name != null
        },
      ),
    )

  /**
   * A sample holder whose device saves map areas in simulated mode, from a fake repository that
   * holds, when [savedAreas], a saved area and one still saving.
   */
  private fun offlineMapsHolder(savedAreas: Boolean): AppDataHolder {
    val repository = FakeOfflineMapRepository(regionMode = OfflineRegionMode.SIMULATED)
    if (savedAreas) {
      runBlocking {
        val both = OfflineImageryType.entries.toSet()
        val saving =
          (repository.save(
              LngLatBounds(west = 37.00, south = -0.45, east = 37.05, north = -0.40),
              OfflineAreaName.NearCoordinates(LatLng(-0.425, 37.025)),
              both,
            ) as DomainResult.Success)
            .value
        repository.complete(saving)
        repository.setStatus(saving, OfflineImageryType.TERRAIN, OfflineAreaLayerStatus.SAVING)
        repository.setProgress(saving, 0.62f)
        val saved =
          (repository.save(
              LngLatBounds(west = 36.90, south = -0.45, east = 36.95, north = -0.40),
              OfflineAreaName.Named("Othaya, Nyeri"),
              both,
            ) as DomainResult.Success)
            .value
        repository.complete(saved)
      }
    }
    return mobileSampleDataHolder().also {
      it.koin.loadModules(
        listOf(
          module {
            single<OfflineMapRepository> { repository }
            // Estimates at once: the compose test clock doesn't run the debounce delay.
            viewModel {
              OfflineAreaSelectionViewModel(
                offlineMapRepository = get(),
                estimateOfflineArea = get(),
                saveOfflineArea = get(),
                debounceMillis = 0,
              )
            }
          }
        ),
        allowOverride = true,
      )
    }
  }

  @Test
  fun capturesKeyScreensInBothThemes() {
    exportDir?.mkdirs()
    for (scene in scenes) {
      for (dark in listOf(false, true)) {
        val suffix = if (dark) "dark" else "light"
        val goldenName = "${scene.id}_$suffix.png"
        runDesktopComposeUiTest(width = 412, height = 860) {
          var session: MobileAppState? = null
          setContent {
            MobileAppSession(scene.holder(), scene.initialRoute) { state ->
              session = state
              MobileApp(state)
            }
          }
          runOnIdle {
            val state = checkNotNull(session)
            if (state.viewModels.settings.uiState.value.isDarkTheme != dark)
              state.viewModels.settings.toggleDarkTheme()
            scene.setUp(state)
          }
          // Settle the map (its camera reports the frame again as it lays out) until the scene is
          // ready and stays so once idle.
          val state = checkNotNull(session)
          var attempts = 0
          do {
            waitForIdle()
            waitUntil(timeoutMillis = 5_000) { scene.isReady(state) }
            waitForIdle()
          } while (!scene.isReady(state) && ++attempts < 5)
          val bitmap = onAllNodes(isRoot()).onFirst().captureToImage().asSkiaBitmap()
          assertMatchesGolden(bitmap, goldenName)
        }
      }
    }
    if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
  }

  private fun assertMatchesGolden(
    actual: Bitmap,
    goldenName: String,
    maxDiffPixelRatio: Double = 0.001,
  ) {
    if (exportDir != null) {
      val exportPng = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      File(exportDir, goldenName).writeBytes(exportPng.bytes)
    }

    val candidates =
      listOf(
        File("shared/mobile/src/jvmTest/resources/goldens/$goldenName"),
        File("src/jvmTest/resources/goldens/$goldenName"),
      )
    val targetGoldenFile = candidates.firstOrNull { it.parentFile.exists() } ?: candidates.first()

    val resourceStream = javaClass.classLoader.getResourceAsStream("goldens/$goldenName")
    val goldenBytes =
      when {
        targetGoldenFile.exists() -> targetGoldenFile.readBytes()
        resourceStream != null -> resourceStream.readBytes()
        else -> null
      }

    if (goldenBytes == null || updateGoldens) {
      targetGoldenFile.parentFile.mkdirs()
      val png = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      targetGoldenFile.writeBytes(png.bytes)
      return
    }

    val expectedImage = Image.makeFromEncoded(goldenBytes)
    val expected = Bitmap.makeFromImage(expectedImage)

    assertEquals(expected.width, actual.width, "Width mismatch for $goldenName")
    assertEquals(expected.height, actual.height, "Height mismatch for $goldenName")

    var diffPixels = 0
    val totalPixels = actual.width * actual.height
    for (y in 0 until actual.height) {
      for (x in 0 until actual.width) {
        if (actual.getColor(x, y) != expected.getColor(x, y)) {
          diffPixels++
        }
      }
    }

    val diffRatio = diffPixels.toDouble() / totalPixels.toDouble()
    if (diffRatio > maxDiffPixelRatio) {
      val reportDir = File("build/reports/goldens/mobile").apply { mkdirs() }
      val actualPng = Image.makeFromBitmap(actual).encodeToData(EncodedImageFormat.PNG)!!
      File(reportDir, "actual_$goldenName").writeBytes(actualPng.bytes)
      mismatches +=
        "Golden mismatch for $goldenName: diff ratio $diffRatio exceeds threshold $maxDiffPixelRatio " +
          "($diffPixels / $totalPixels pixels differed). Actual screenshot: ${reportDir.resolve("actual_$goldenName").absolutePath}"
    }
  }
}
