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
package org.groundplatform.v2.core.sampling

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Coarse JVM timings for the plan's performance budgets (100k grid plots ≤ 500 ms, 100k stratified
 * plots over 500 strata ≤ 1.5 s). Timings are printed; assertions are deliberately generous so
 * shared CI machines don't make this flaky.
 */
class SamplingBenchmarkTest {
  private val engine = SamplingEngine.Default

  /** A wobbly, roughly 340 km wide country-like outline with 5,000 vertices around (0°, 20°). */
  private val country: Ring =
    List(5000) { i ->
      val t = 2 * PI * i / 5000
      val r = 1.6 + 0.12 * sin(23 * t) + 0.05 * sin(131 * t)
      GeoCoord(r * sin(t), 20 + r * cos(t))
    }

  private fun <T> bench(label: String, block: () -> T): T {
    repeat(2) { block() } // JIT warm-up.
    val (value, duration) = measureTimedValue(block)
    println("Sampling benchmark — $label: ${duration.inWholeMilliseconds} ms")
    assertTrue(duration.inWholeSeconds < 30, "$label took $duration")
    return value
  }

  @Test
  fun grid100k() {
    val area = SamplingArea.fromSurveyArea(listOf(country))
    val areaHa = engine.estimate(area, SampleDesign(PlotLayout.SimpleRandom(0))).areaHa
    val spacing = kotlin.math.sqrt(areaHa * 10_000 / 100_000)
    val design =
      SampleDesign(
        PlotLayout.SystematicGrid(spacing),
        plotShape = PlotShape.SQUARE,
        plotSizeM = 30.0,
      )
    val result = bench("100k grid plots (square)") { engine.generate(area, design) }
    assertIs<SamplingResult.Success>(result)
    println("Sampling benchmark — grid plots: ${result.plots.size}")
    assertTrue(result.plots.size in 95_000..105_000)
  }

  @Test
  fun stratified100kOver500Strata() {
    // 25 × 20 strata of 0.12° × 0.12°, each with a slightly irregular 40-vertex outline.
    val strata = ArrayList<Stratum>()
    for (r in 0 until 20) for (c in 0 until 25) {
      val lat0 = r * 0.12
      val lng0 = 10 + c * 0.12
      val ring =
        List(40) { k ->
          val t = 2 * PI * k / 40
          GeoCoord(lat0 + 0.06 + 0.055 * sin(t), lng0 + 0.06 + 0.055 * cos(t))
        }
      strata.add(Stratum("S${r * 25 + c}", ring))
    }
    val area = SamplingArea(strata)
    val design =
      SampleDesign(
        PlotLayout.StratifiedRandom(100_000, Allocation.Proportional),
        plotShape = PlotShape.CIRCLE,
        plotSizeM = 20.0,
      )
    val result =
      bench("100k stratified plots over 500 strata (circle)") { engine.generate(area, design) }
    assertIs<SamplingResult.Success>(result)
    assertTrue(result.plots.size == 100_000)
  }

  @Test
  fun random25kWithMinDistanceAndSubPlots() {
    val area = SamplingArea.fromSurveyArea(listOf(country))
    val design =
      SampleDesign(
        PlotLayout.SimpleRandom(25_000, minDistanceM = 500.0),
        plotShape = PlotShape.SQUARE,
        plotSizeM = 100.0,
        subPlot = SubPlotLayout.Grid(5, 20.0),
      )
    val result =
      bench("25k random plots, 500 m apart, 25 samples each") { engine.generate(area, design) }
    assertIs<SamplingResult.Success>(result)
  }
}
