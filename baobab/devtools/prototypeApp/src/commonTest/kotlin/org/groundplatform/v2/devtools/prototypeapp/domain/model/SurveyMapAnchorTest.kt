/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import org.groundplatform.v2.map.LatLng

class SurveyMapAnchorTest {
  private val anchor = SurveyMapAnchor.forSurvey(SurveyMapAnchor.DEFAULT_SURVEY_ID)

  @Test
  fun toLatLng_centerOfSurveyIsAnchor() {
    assertEquals(anchor.center, anchor.toLatLng(0.5, 0.5))
  }

  @Test
  fun toLatLng_yGrowsSouthward() {
    assertEquals(true, anchor.toLatLng(0.5, 1.0).latitude < anchor.center.latitude)
    assertEquals(true, anchor.toLatLng(1.0, 0.5).longitude > anchor.center.longitude)
  }

  @Test
  fun toNormalized_roundTripsToLatLng() {
    val point = LatLng(-0.4150, 36.9480)

    val (nx, ny) = anchor.toNormalized(point)
    val back = anchor.toLatLng(nx, ny)

    assertEquals(point.latitude, back.latitude, 1e-9)
    assertEquals(point.longitude, back.longitude, 1e-9)
  }

  @Test
  fun forSurvey_fallsBackToDefault() {
    assertEquals(anchor, SurveyMapAnchor.forSurvey("unknown-survey"))
  }
}
