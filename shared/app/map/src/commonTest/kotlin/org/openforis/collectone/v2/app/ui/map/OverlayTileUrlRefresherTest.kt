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
package org.openforis.collectone.v2.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.error.DomainResult
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.app.domain.repository.OverlayTileUrl

/**
 * [OverlayTileUrlRefresher] on its own, in virtual time. The refresh loops never go idle, so the
 * tests step time with [runCurrent] and [advanceTimeBy], never `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayTileUrlRefresherTest {
  private class Harness(val scope: TestScope, ttlSeconds: Long, failures: Int) {
    val requests = mutableListOf<Pair<String, String>>()
    val data = MutableStateFlow(seededMapData())
    val session = MutableStateFlow(SurveyMapSession())

    init {
      OverlayTileUrlRefresher(
          scope = scope.backgroundScope,
          overlayTileUrls = { surveyId, sourceId ->
            requests += surveyId to sourceId
            if (requests.size <= failures) {
              DomainResult.Failure(DomainError.Network)
            } else {
              DomainResult.Success(OverlayTileUrl("url-${requests.size}", ttlSeconds))
            }
          },
          session = session,
        )
        .start(data)
      scope.runCurrent()
    }

    fun enable(sourceId: String) {
      session.value = session.value.copy(enabledImagerySourceIds = setOf(sourceId))
      scope.runCurrent()
    }

    val urls: Map<String, String>
      get() = session.value.resolvedOverlayTileUrls
  }

  private fun runHarness(
    ttlSeconds: Long = 7200,
    failures: Int = 0,
    body: suspend TestScope.(Harness) -> Unit,
  ) = runTest { body(Harness(this, ttlSeconds, failures)) }

  @Test
  fun nonEarthEngineSources_areNeverResolved() = runHarness { h ->
    h.enable("some-xyz-source")
    assertTrue(h.requests.isEmpty())
    assertTrue(h.urls.isEmpty())
  }

  @Test
  fun enabledSource_isResolvedForTheActiveSurvey() = runHarness { h ->
    h.enable(SEEDED_EE_SOURCE_ID)
    assertEquals(listOf(h.data.value.activeSurveyId to SEEDED_EE_SOURCE_ID), h.requests)
    assertEquals(mapOf(SEEDED_EE_SOURCE_ID to "url-1"), h.urls)
  }

  @Test
  fun shortTtls_areRefreshedNoMoreThanOnceAMinute() =
    runHarness(ttlSeconds = 5) { h ->
      h.enable(SEEDED_EE_SOURCE_ID)
      advanceTimeBy(59_000)
      runCurrent()
      assertEquals(1, h.requests.size)

      advanceTimeBy(2_000)
      runCurrent()
      assertEquals(2, h.requests.size)
      assertEquals("url-2", h.urls[SEEDED_EE_SOURCE_ID])
    }

  @Test
  fun failures_leaveTheLayerUnresolved_untilARetrySucceeds() =
    runHarness(failures = 1) { h ->
      h.enable(SEEDED_EE_SOURCE_ID)
      assertTrue(h.urls.isEmpty())

      advanceTimeBy(30_001)
      runCurrent()
      assertEquals("url-2", h.urls[SEEDED_EE_SOURCE_ID])
    }

  @Test
  fun switchingSurveys_dropsUrlsAndThePinnedScene_andStopsTheOldLoops() = runHarness { h ->
    h.enable(SEEDED_EE_SOURCE_ID)
    h.session.value =
      h.session.value.copy(pinnedSceneOverlay = ImagerySource(id = "scene", name = "Scene"))

    h.data.value = h.data.value.copy(activeSurveyId = "")
    runCurrent()
    assertTrue(h.urls.isEmpty())
    assertNull(h.session.value.pinnedSceneOverlay)

    // No survey: nothing is resolved, even after the old loop's TTL.
    advanceTimeBy(7_200_001)
    runCurrent()
    assertEquals(1, h.requests.size)
  }
}
