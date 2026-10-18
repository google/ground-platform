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

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.groundplatform.v2.core.geo.IndexedRing
import org.groundplatform.v2.core.geo.Wgs84

class DefaultSamplingEngineTest {
  private val engine = SamplingEngine.Default

  /** A concave (L-shaped) area of roughly 20 × 20 km near the equator. */
  private val lShape: Ring =
    listOf(
      GeoCoord(-1.0, 37.0),
      GeoCoord(-1.0, 37.18),
      GeoCoord(-0.91, 37.18),
      GeoCoord(-0.91, 37.09),
      GeoCoord(-0.82, 37.09),
      GeoCoord(-0.82, 37.0),
      GeoCoord(-1.0, 37.0),
    )

  /** Two parts of a survey area (an island and a mainland block) at mid latitude. */
  private val twoParts: List<Ring> =
    listOf(
      listOf(GeoCoord(45.0, 7.0), GeoCoord(45.0, 7.2), GeoCoord(45.1, 7.2), GeoCoord(45.1, 7.0)),
      listOf(
        GeoCoord(45.2, 7.3),
        GeoCoord(45.2, 7.35),
        GeoCoord(45.25, 7.35),
        GeoCoord(45.25, 7.3),
      ),
    )

  private val strata =
    SamplingArea(
      listOf(
        Stratum(
          "forest",
          listOf(GeoCoord(0.0, 0.0), GeoCoord(0.0, 0.2), GeoCoord(0.1, 0.2), GeoCoord(0.1, 0.0)),
        ),
        Stratum(
          "crop",
          listOf(
            GeoCoord(0.1, 0.0),
            GeoCoord(0.1, 0.05),
            GeoCoord(0.15, 0.05),
            GeoCoord(0.15, 0.0),
          ),
        ),
        Stratum(
          "crop",
          listOf(
            GeoCoord(0.2, 0.0),
            GeoCoord(0.2, 0.05),
            GeoCoord(0.25, 0.05),
            GeoCoord(0.25, 0.0),
          ),
        ),
        Stratum(
          "water",
          listOf(
            GeoCoord(0.1, 0.1),
            GeoCoord(0.1, 0.12),
            GeoCoord(0.12, 0.12),
            GeoCoord(0.12, 0.1),
          ),
        ),
      )
    )

  private fun success(result: SamplingResult): List<GeneratedPlot> {
    assertIs<SamplingResult.Success>(result, "expected success but got $result")
    return result.plots
  }

  private fun ringIndex(ring: Ring) =
    IndexedRing(DoubleArray(ring.size) { ring[it].lat }, DoubleArray(ring.size) { ring[it].lng })

  private fun assertAllInside(area: SamplingArea, plots: List<GeneratedPlot>) {
    val rings = area.strata.map { it.id to ringIndex(it.ring) }
    for (p in plots) {
      val containing = rings.firstOrNull { it.second.contains(p.center.lat, p.center.lng) }
      assertTrue(containing != null, "${p.plotId} at ${p.center} is outside the area")
      assertEquals(containing.first, p.stratum, "${p.plotId} has the wrong stratum")
    }
  }

  private fun minPairDistance(plots: List<GeneratedPlot>): Double {
    var best = Double.MAX_VALUE
    for (i in plots.indices) for (j in i + 1 until plots.size) {
      val a = plots[i].center
      val b = plots[j].center
      best = minOf(best, Wgs84.localDistanceM(a.lat, a.lng, b.lat, b.lng))
    }
    return best
  }

  /** Platform-independent fingerprint of a plot set (integers only, no Double.toString). */
  private fun fingerprint(plots: List<GeneratedPlot>): Long {
    var h = 0L
    fun mix(v: Long) {
      h = SplitMix64.mix64(h xor v)
    }
    for (p in plots) {
      mix(SplitMix64.fnv1a64(p.plotId + "|" + p.stratum + "|" + p.clusterId))
      mix(p.sampleOrder.toLong())
      for (c in listOf(p.center) + p.boundary + p.samples) {
        mix((c.lat * 1e7).roundToLong())
        mix((c.lng * 1e7).roundToLong())
      }
      mix((p.inclusionWeightHa * 1000).roundToLong())
    }
    return h
  }

  @Test
  fun systematicGrid_placesPlotsInsideOnRegularSpacing() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val design = SampleDesign(PlotLayout.SystematicGrid(spacingM = 1000.0), seed = 3)
    val plots = success(engine.generate(area, design))
    val estimate = engine.estimate(area, design)
    // L-shape ≈ 3 × (10 km)² ⇒ ≈ 300 plots at 1 km.
    assertTrue(abs(plots.size - estimate.estimatedPlotCount) <= 30, "${plots.size} vs $estimate")
    assertAllInside(area, plots)
    assertEquals(1000.0, minPairDistance(plots), 5.0)
    assertEquals(estimate.areaHa / plots.size, plots[0].inclusionWeightHa, 1e-9)
    assertEquals("P000001", plots[0].plotId)
    assertEquals((1..plots.size).toSet(), plots.map { it.sampleOrder }.toSet())
  }

  @Test
  fun systematicGrid_originDependsOnSeed() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val a =
      success(engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(1000.0), seed = 1)))
    val b =
      success(engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(1000.0), seed = 1)))
    val c =
      success(engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(1000.0), seed = 2)))
    assertEquals(a, b)
    assertNotEquals(a[0].center, c[0].center)
  }

  @Test
  fun simpleRandom_respectsCountMinDistanceAndParts() {
    val area = SamplingArea.fromSurveyArea(twoParts)
    val design =
      SampleDesign(
        PlotLayout.SimpleRandom(count = 200, minDistanceM = 300.0),
        plotShape = PlotShape.CIRCLE,
        plotSizeM = 30.0,
      )
    val plots = success(engine.generate(area, design))
    assertEquals(200, plots.size)
    assertAllInside(area, plots)
    assertTrue(minPairDistance(plots) >= 300.0)
    val island = ringIndex(twoParts[1])
    assertTrue(plots.any { island.contains(it.center.lat, it.center.lng) }, "no plot on the island")
    assertEquals(engine.estimate(area, design).areaHa / 200, plots[0].inclusionWeightHa, 1e-9)
  }

  @Test
  fun simpleRandom_failsWithClearMessageWhenPlotsCannotFit() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val result =
      engine.generate(
        area,
        SampleDesign(PlotLayout.SimpleRandom(count = 500, minDistanceM = 5000.0)),
      )
    assertIs<SamplingResult.Failure>(result)
    assertTrue(
      result.message.startsWith("Cannot fit 500 plots at least 5000 m apart"),
      result.message,
    )
    val thrown =
      assertFailsWith<SamplingException> {
        engine.generateChunks(area, SampleDesign(PlotLayout.SimpleRandom(500, 5000.0))).toList()
      }
    assertEquals(result.message, thrown.message)
  }

  @Test
  fun stratifiedRandom_allocatesProportionallyWithLargestRemainder() {
    val design =
      SampleDesign(
        PlotLayout.StratifiedRandom(totalCount = 101, allocation = Allocation.Proportional)
      )
    val plots = success(engine.generate(strata, design))
    assertEquals(101, plots.size)
    assertAllInside(strata, plots)
    val counts = plots.groupingBy { it.stratum }.eachCount()
    // Areas: forest 0.02 deg², crop 2 × 0.0025, water 0.0004 ⇒ quotas 79.5 / 19.9 / 1.6.
    assertEquals(mapOf("forest" to 79, "crop" to 20, "water" to 2), counts)
    val estimate = engine.estimate(strata, design)
    assertEquals(101, estimate.estimatedPlotCount)
    // Inclusion weights sum to the total area.
    assertEquals(estimate.areaHa, plots.sumOf { it.inclusionWeightHa }, estimate.areaHa * 1e-9)
    val crop = plots.first { it.stratum == "crop" }
    val cropArea =
      strata.strata
        .filter { it.id == "crop" }
        .sumOf { s ->
          Wgs84.ringAreaHa(
            DoubleArray(s.ring.size) { s.ring[it].lat },
            DoubleArray(s.ring.size) { s.ring[it].lng },
          )
        }
    assertEquals(cropArea / 20, crop.inclusionWeightHa, 1e-6)
  }

  @Test
  fun stratifiedRandom_equalAndCustomAllocation() {
    val equal =
      success(
        engine.generate(strata, SampleDesign(PlotLayout.StratifiedRandom(10, Allocation.Equal)))
      )
    assertEquals(
      mapOf("forest" to 4, "crop" to 3, "water" to 3),
      equal.groupingBy { it.stratum }.eachCount(),
    )
    val custom =
      success(
        engine.generate(
          strata,
          SampleDesign(
            PlotLayout.StratifiedRandom(
              0,
              Allocation.Custom(mapOf("water" to 5, "crop" to 2, "unknown" to 9)),
              minDistanceM = 50.0,
            )
          ),
        )
      )
    assertEquals(mapOf("crop" to 2, "water" to 5), custom.groupingBy { it.stratum }.eachCount())
    assertAllInside(strata, custom)
    assertTrue(minPairDistance(custom) >= 50.0)
  }

  @Test
  fun largestRemainder_sumsToTotal() {
    val parts = DefaultSamplingEngine.largestRemainder(10, doubleArrayOf(1.0, 1.0, 1.0))
    assertEquals(listOf(4, 3, 3), parts.toList())
    val weights = DoubleArray(500) { 1.0 + (it % 7) }
    assertEquals(100_000, DefaultSamplingEngine.largestRemainder(100_000, weights).sum())
  }

  @Test
  fun cluster_buildsPatternsAndDropsOutsidePlots() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val design =
      SampleDesign(
        PlotLayout.Cluster(
          clusterCount = 20,
          plotsPerSide = 3,
          plotSpacingM = 200.0,
          minDistanceM = 1500.0,
        ),
        plotShape = PlotShape.POINT,
      )
    val plots = success(engine.generate(area, design))
    assertTrue(plots.size in 20..180)
    assertAllInside(area, plots)
    val byCluster = plots.groupBy { it.clusterId }
    assertEquals(20, byCluster.size)
    assertTrue(byCluster.keys.all { it.matches(Regex("C\\d{6}")) })
    assertTrue(byCluster.values.all { it.size <= 9 })
    assertTrue(plots.all { it.boundary.isEmpty() })
    val full = byCluster.values.first { it.size == 9 }
    assertEquals(200.0, minPairDistance(full), 1.0)
  }

  @Test
  fun plotShapes_haveExpectedRings() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    fun first(shape: PlotShape) =
      success(
        engine.generate(
          area,
          SampleDesign(PlotLayout.SimpleRandom(1), plotShape = shape, plotSizeM = 100.0),
        )
      )[0]
    val square = first(PlotShape.SQUARE)
    assertEquals(5, square.boundary.size)
    assertEquals(square.boundary.first(), square.boundary.last())
    val sw = square.boundary[0]
    val se = square.boundary[1]
    val ne = square.boundary[2]
    assertEquals(100.0, Wgs84.localDistanceM(sw.lat, sw.lng, se.lat, se.lng), 0.05)
    assertEquals(100.0, Wgs84.localDistanceM(se.lat, se.lng, ne.lat, ne.lng), 0.05)
    val circle = first(PlotShape.CIRCLE)
    assertEquals(25, circle.boundary.size)
    assertEquals(circle.boundary.first(), circle.boundary.last())
    for (v in circle.boundary) {
      assertEquals(
        50.0,
        Wgs84.localDistanceM(circle.center.lat, circle.center.lng, v.lat, v.lng),
        0.05,
      )
    }
    assertTrue(first(PlotShape.POINT).boundary.isEmpty())
  }

  @Test
  fun subPlotLayouts_areInsideTheirPlots() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val base =
      SampleDesign(PlotLayout.SimpleRandom(20), plotShape = PlotShape.CIRCLE, plotSizeM = 60.0)
    val center = success(engine.generate(area, base.copy(subPlot = SubPlotLayout.Center)))
    assertTrue(center.all { it.samples == listOf(it.center) })
    val grid =
      success(
        engine.generate(
          area,
          base.copy(plotShape = PlotShape.SQUARE, subPlot = SubPlotLayout.Grid(5, 10.0)),
        )
      )
    for (p in grid) {
      assertEquals(25, p.samples.size)
      // Reading order: first sample is north-west of the center, last is south-east.
      assertTrue(p.samples.first().lat > p.center.lat && p.samples.first().lng < p.center.lng)
      assertEquals(p.center, p.samples[12])
    }
    val random = success(engine.generate(area, base.copy(subPlot = SubPlotLayout.Random(25))))
    for (p in random) {
      assertEquals(25, p.samples.size)
      val ring = ringIndex(p.boundary)
      assertTrue(p.samples.all { ring.contains(it.lat, it.lng) })
    }
    val none = success(engine.generate(area, base))
    assertTrue(none.all { it.samples.isEmpty() })
    // Same plot ID and seed ⇒ same random samples, independent of other plots.
    val fewer =
      success(
        engine.generate(
          area,
          base.copy(layout = PlotLayout.SimpleRandom(5), subPlot = SubPlotLayout.Random(25)),
        )
      )
    assertEquals(random[0].samples, fewer[0].samples)
  }

  @Test
  fun shuffle_permutesSampleOrderDeterministically() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val unshuffled =
      success(
        engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(2000.0), shuffle = false))
      )
    assertEquals((1..unshuffled.size).toList(), unshuffled.map { it.sampleOrder })
    val shuffled =
      success(
        engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(2000.0), shuffle = true))
      )
    assertEquals((1..shuffled.size).toSet(), shuffled.map { it.sampleOrder }.toSet())
    assertNotEquals((1..shuffled.size).toList(), shuffled.map { it.sampleOrder })
  }

  @Test
  fun simpleRandom_isUniformChiSquare() {
    // 5,000 plots in a 0.1° square at the equator, binned into 10 × 10 equal cells. With 99
    // degrees of freedom the 0.1% critical value is ≈ 149; the seed is fixed, so this can't flake.
    val square =
      listOf(GeoCoord(0.0, 0.0), GeoCoord(0.0, 0.1), GeoCoord(0.1, 0.1), GeoCoord(0.1, 0.0))
    val plots =
      success(
        engine.generate(
          SamplingArea.fromSurveyArea(listOf(square)),
          SampleDesign(PlotLayout.SimpleRandom(5000), plotShape = PlotShape.POINT, seed = 99),
        )
      )
    val bins = IntArray(100)
    for (p in plots) {
      val r = (p.center.lat / 0.01).toInt().coerceIn(0, 9)
      val c = (p.center.lng / 0.01).toInt().coerceIn(0, 9)
      bins[r * 10 + c]++
    }
    val expected = plots.size / 100.0
    val chi2 = bins.sumOf { (it - expected) * (it - expected) / expected }
    assertTrue(chi2 < 149.0, "χ² = $chi2")
  }

  @Test
  fun generate_isDeterministicPerSeed() {
    val designs =
      listOf(
        SampleDesign(PlotLayout.SystematicGrid(1500.0), subPlot = SubPlotLayout.Random(5)),
        SampleDesign(PlotLayout.SimpleRandom(50, 200.0), plotShape = PlotShape.CIRCLE),
        SampleDesign(PlotLayout.StratifiedRandom(40), subPlot = SubPlotLayout.Grid(3, 20.0)),
        SampleDesign(PlotLayout.Cluster(5, 2, 100.0)),
      )
    for (design in designs) {
      val area =
        if (design.layout is PlotLayout.StratifiedRandom) strata
        else SamplingArea.fromSurveyArea(listOf(lShape))
      val a = success(engine.generate(area, design.copy(seed = 11)))
      val b = success(engine.generate(area, design.copy(seed = 11)))
      val c = success(engine.generate(area, design.copy(seed = 12)))
      assertEquals(a, b, "$design")
      assertNotEquals(fingerprint(a), fingerprint(c), "$design")
    }
  }

  @Test
  fun generateChunks_concatenationEqualsGenerate() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val design =
      SampleDesign(
        PlotLayout.SystematicGrid(700.0),
        plotShape = PlotShape.CIRCLE,
        subPlot = SubPlotLayout.Random(3),
      )
    val all = success(engine.generate(area, design))
    val chunks = engine.generateChunks(area, design, chunkSize = 97).toList()
    assertTrue(chunks.size > 1)
    assertTrue(chunks.dropLast(1).all { it.size == 97 })
    assertEquals(all, chunks.flatten())
  }

  @Test
  fun generate_reportsProgressAndCancels() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val design = SampleDesign(PlotLayout.SystematicGrid(200.0))
    val progress = mutableListOf<Double>()
    success(engine.generate(area, design, onProgress = { progress.add(it) }))
    assertTrue(progress.size > 2)
    assertEquals(progress.sorted(), progress)
    assertEquals(1.0, progress.last())
    var calls = 0
    val cancelled = engine.generate(area, design, isCancelled = { ++calls > 3 })
    assertEquals(SamplingResult.Cancelled, cancelled)
    assertEquals(
      SamplingResult.Cancelled,
      engine.generate(area, SampleDesign(PlotLayout.SimpleRandom(100)), isCancelled = { true }),
    )
  }

  @Test
  fun generate_rejectsInvalidDesigns() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    assertIs<SamplingResult.Failure>(
      engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(0.0)))
    )
    assertIs<SamplingResult.Failure>(
      engine.generate(SamplingArea(emptyList()), SampleDesign(PlotLayout.SimpleRandom(1)))
    )
    assertIs<SamplingResult.Failure>(
      engine.generate(
        area,
        SampleDesign(PlotLayout.SimpleRandom(1), subPlot = SubPlotLayout.Grid(11, 1.0)),
      )
    )
    val tooMany = engine.generate(area, SampleDesign(PlotLayout.SystematicGrid(1.0)))
    assertIs<SamplingResult.Failure>(tooMany)
    assertTrue(tooMany.message.contains("maximum"), tooMany.message)
  }

  /**
   * Golden vectors: identical on every platform (JVM, JS, WasmJS, native). If these change, the
   * engine output changed and [SAMPLING_ENGINE_VERSION] must be bumped.
   */
  @Test
  fun goldenVectors() {
    val area = SamplingArea.fromSurveyArea(listOf(lShape))
    val grid =
      success(
        engine.generate(
          area,
          SampleDesign(
            PlotLayout.SystematicGrid(1000.0),
            plotShape = PlotShape.CIRCLE,
            subPlot = SubPlotLayout.Random(4),
            seed = 2026,
          ),
        )
      )
    val random =
      success(
        engine.generate(
          area,
          SampleDesign(
            PlotLayout.SimpleRandom(100, 500.0),
            subPlot = SubPlotLayout.Grid(3, 15.0),
            seed = 2026,
          ),
        )
      )
    val stratified =
      success(
        engine.generate(
          strata,
          SampleDesign(PlotLayout.StratifiedRandom(60, minDistanceM = 100.0), seed = 2026),
        )
      )
    val cluster =
      success(engine.generate(area, SampleDesign(PlotLayout.Cluster(10, 3, 150.0), seed = 2026)))
    val actual =
      listOf(grid, random, stratified, cluster).joinToString { "${it.size}:${fingerprint(it)}" }
    println("Sampling golden vectors: $actual; first grid plot ${grid[0]}")
    assertEquals(GOLDEN, actual)
  }

  private companion object {
    const val GOLDEN =
      "300:8444757153970821526, 100:-3918561751431059117, 60:-2578603035579192089, " +
        "90:-3131709567840164918"
  }
}
