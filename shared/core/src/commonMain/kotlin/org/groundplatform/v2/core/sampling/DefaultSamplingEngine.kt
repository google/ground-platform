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

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.groundplatform.v2.core.geo.IndexedRing
import org.groundplatform.v2.core.geo.LambertAzimuthalEqualArea
import org.groundplatform.v2.core.geo.PointSpatialHash
import org.groundplatform.v2.core.geo.RingSetIndex
import org.groundplatform.v2.core.geo.Wgs84
import org.groundplatform.v2.core.geo.quantizeDegrees

/** Thrown by [SamplingEngine.generateChunks] when the design cannot be satisfied. */
class SamplingException(message: String) : RuntimeException(message)

/**
 * Default [SamplingEngine] (engine version [SAMPLING_ENGINE_VERSION]).
 *
 * Generation runs in two phases:
 * 1. **Placement**: plot centers are chosen in a Lambert azimuthal equal-area projection centered
 *    on the area (so a uniform density in projected meters is uniform on the ground), inverse-
 *    projected, quantized to 1e-7° and tested for containment in lat/lng. A plot belongs to the
 *    first stratum whose ring contains its center.
 * 2. **Materialization**: plot IDs, visiting order, inclusion weights, boundaries and sub-plot
 *    samples are derived per plot from the placement, so [generateChunks] can emit plots lazily.
 *
 * All randomness comes from [SplitMix64] streams derived from [SampleDesign.seed], one stream per
 * purpose (grid origin, random placement, each stratum keyed by its ID, shuffle, and each plot's
 * sub-plot points keyed by plot ID), so changing one part of a design doesn't reshuffle the rest.
 * Containment is decided for the plot center; boundaries of edge plots may extend past the area.
 */
object DefaultSamplingEngine : SamplingEngine {
  /** Upper bound on plots per design, to fail fast on accidental country-wide 1 m grids. */
  const val MAX_PLOTS: Int = 1_000_000

  /** Max sub-plot samples per plot (larger designs should use a separate point layer). */
  const val MAX_SAMPLES_PER_PLOT: Int = 100

  /** Rejection sampling gives up after this many attempts per requested plot. */
  const val MAX_ATTEMPTS_PER_PLOT: Int = 200

  /** Number of vertices of a circular plot boundary (plus the repeated closing vertex). */
  const val CIRCLE_VERTICES: Int = 24

  private const val STREAM_GRID = 1L
  private const val STREAM_RANDOM = 2L
  private const val STREAM_STRATIFIED = 3L
  private const val STREAM_CLUSTER = 4L
  private const val STREAM_SHUFFLE = 5L
  private const val STREAM_SUBPLOT = 6L
  private const val CHECK_INTERVAL = 256

  override fun estimate(area: SamplingArea, design: SampleDesign): SampleEstimate {
    val areaHa = area.strata.sumOf { ringAreaHa(it.ring) }
    val count =
      when (val layout = design.layout) {
        is PlotLayout.SystematicGrid ->
          if (layout.spacingM > 0) {
            (areaHa * 10_000 / (layout.spacingM * layout.spacingM))
              .coerceAtMost(Int.MAX_VALUE.toDouble())
              .toInt()
          } else 0
        is PlotLayout.SimpleRandom -> layout.count
        is PlotLayout.StratifiedRandom ->
          when (val allocation = layout.allocation) {
            is Allocation.Custom -> {
              val ids = area.strata.map { it.id }.toSet()
              allocation.countsByStratum.filterKeys { it in ids }.values.sum()
            }
            else -> layout.totalCount
          }
        is PlotLayout.Cluster -> layout.clusterCount * layout.plotsPerSide * layout.plotsPerSide
      }
    return SampleEstimate(areaHa = areaHa, estimatedPlotCount = max(0, count))
  }

  override fun generate(
    area: SamplingArea,
    design: SampleDesign,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): SamplingResult {
    val placement =
      when (val p = place(area, design, { onProgress(it * PLACEMENT_SHARE) }, isCancelled)) {
        is PlaceOutcome.Placed -> p.placement
        is PlaceOutcome.Failed -> return SamplingResult.Failure(p.message)
        PlaceOutcome.Cancelled -> return SamplingResult.Cancelled
      }
    val n = placement.size
    val plots = ArrayList<GeneratedPlot>(n)
    for (i in 0 until n) {
      if (i % CHECK_INTERVAL == 0) {
        if (isCancelled()) return SamplingResult.Cancelled
        onProgress(PLACEMENT_SHARE + (1 - PLACEMENT_SHARE) * i / n)
      }
      plots.add(placement.materialize(i))
    }
    onProgress(1.0)
    return SamplingResult.Success(plots)
  }

  override fun generateChunks(
    area: SamplingArea,
    design: SampleDesign,
    chunkSize: Int,
  ): Sequence<List<GeneratedPlot>> {
    require(chunkSize > 0) { "chunkSize must be positive" }
    return sequence {
      val placement =
        when (val p = place(area, design, {}, { false })) {
          is PlaceOutcome.Placed -> p.placement
          is PlaceOutcome.Failed -> throw SamplingException(p.message)
          PlaceOutcome.Cancelled -> return@sequence
        }
      var start = 0
      while (start < placement.size) {
        val end = min(placement.size, start + chunkSize)
        yield(List(end - start) { placement.materialize(start + it) })
        start = end
      }
    }
  }

  private const val PLACEMENT_SHARE = 0.6

  private fun ringAreaHa(ring: Ring): Double {
    val lats = DoubleArray(ring.size) { ring[it].lat }
    val lngs = DoubleArray(ring.size) { ring[it].lng }
    return Wgs84.ringAreaHa(lats, lngs)
  }

  private sealed interface PlaceOutcome {
    class Placed(val placement: Placement) : PlaceOutcome

    class Failed(val message: String) : PlaceOutcome

    data object Cancelled : PlaceOutcome
  }

  private fun validate(area: SamplingArea, design: SampleDesign): String? {
    if (area.strata.isEmpty()) return "The sampling area is empty"
    if (design.plotShape != PlotShape.POINT && !(design.plotSizeM > 0)) {
      return "Plot size must be greater than 0 m"
    }
    when (val sub = design.subPlot) {
      is SubPlotLayout.Grid -> {
        if (sub.n < 1) return "Sub-plot grid size must be at least 1"
        if (sub.n * sub.n > MAX_SAMPLES_PER_PLOT) {
          return "At most $MAX_SAMPLES_PER_PLOT samples per plot are supported"
        }
        if (sub.spacingM < 0) return "Sub-plot spacing must not be negative"
      }
      is SubPlotLayout.Random -> {
        if (sub.count < 0) return "Sub-plot sample count must not be negative"
        if (sub.count > MAX_SAMPLES_PER_PLOT) {
          return "At most $MAX_SAMPLES_PER_PLOT samples per plot are supported"
        }
      }
      SubPlotLayout.Center,
      SubPlotLayout.None -> Unit
    }
    when (val layout = design.layout) {
      is PlotLayout.SystematicGrid ->
        if (!(layout.spacingM > 0)) return "Grid spacing must be greater than 0 m"
      is PlotLayout.SimpleRandom -> {
        if (layout.count < 0) return "Plot count must not be negative"
        if (layout.minDistanceM < 0) return "Minimum distance must not be negative"
      }
      is PlotLayout.StratifiedRandom -> {
        if (layout.totalCount < 0) return "Plot count must not be negative"
        if (layout.minDistanceM < 0) return "Minimum distance must not be negative"
        val allocation = layout.allocation
        if (allocation is Allocation.Custom && allocation.countsByStratum.values.any { it < 0 }) {
          return "Plot counts per stratum must not be negative"
        }
      }
      is PlotLayout.Cluster -> {
        if (layout.clusterCount < 0) return "Cluster count must not be negative"
        if (layout.plotsPerSide < 1) return "Plots per cluster side must be at least 1"
        if (layout.plotSpacingM < 0) return "Plot spacing must not be negative"
        if (layout.minDistanceM < 0) return "Minimum distance must not be negative"
      }
    }
    val estimate = estimate(area, design).estimatedPlotCount
    if (estimate > MAX_PLOTS) {
      return "This design would produce about $estimate plots; the maximum is $MAX_PLOTS"
    }
    return null
  }

  private fun place(
    area: SamplingArea,
    design: SampleDesign,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): PlaceOutcome {
    validate(area, design)?.let {
      return PlaceOutcome.Failed(it)
    }
    val prepared = PreparedArea(area)
    if (prepared.totalAreaHa <= 0) return PlaceOutcome.Failed("The sampling area is empty")
    val builder = PlacementBuilder(prepared, design)
    val outcome =
      when (val layout = design.layout) {
        is PlotLayout.SystematicGrid ->
          placeGrid(prepared, layout, design.seed, builder, onProgress, isCancelled)
        is PlotLayout.SimpleRandom ->
          placeSimpleRandom(prepared, layout, design.seed, builder, onProgress, isCancelled)
        is PlotLayout.StratifiedRandom ->
          placeStratified(prepared, layout, design.seed, builder, onProgress, isCancelled)
        is PlotLayout.Cluster ->
          placeCluster(prepared, layout, design.seed, builder, onProgress, isCancelled)
      }
    return outcome ?: PlaceOutcome.Placed(builder.build())
  }

  private fun placeGrid(
    area: PreparedArea,
    layout: PlotLayout.SystematicGrid,
    seed: Long,
    out: PlacementBuilder,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): PlaceOutcome? {
    val s = layout.spacingM
    val rng = SplitMix64(SplitMix64.derive(seed, STREAM_GRID))
    val ox = rng.nextDouble() * s
    val oy = rng.nextDouble() * s
    val bb = area.xyBounds
    val i0 = ceil((bb.minX - ox) / s).toLong()
    val i1 = floor((bb.maxX - ox) / s).toLong()
    val j0 = ceil((bb.minY - oy) / s).toLong()
    val j1 = floor((bb.maxY - oy) / s).toLong()
    val ll = DoubleArray(2)
    val rows = max(1L, j1 - j0 + 1)
    for (j in j0..j1) {
      if (isCancelled()) return PlaceOutcome.Cancelled
      onProgress((j - j0).toDouble() / rows)
      val y = oy + j * s
      for (i in i0..i1) {
        area.projection.inverse(ox + i * s, y, ll)
        val lat = quantizeDegrees(ll[0])
        val lng = quantizeDegrees(ll[1])
        val stratum = area.stratumOf(lat, lng)
        if (stratum >= 0) {
          if (out.size >= MAX_PLOTS) {
            return PlaceOutcome.Failed("This design would produce more than $MAX_PLOTS plots")
          }
          out.add(lat, lng, stratum, -1)
        }
      }
    }
    onProgress(1.0)
    return null
  }

  /**
   * Rejection-samples up to [count] centers inside the projected box [bb], accepting points for
   * which [accept] returns a stratum index ≥ 0 and that respect [hash]. Returns null on success.
   */
  private inline fun rejectionSample(
    area: PreparedArea,
    bb: XyBounds,
    count: Int,
    rng: SplitMix64,
    hash: PointSpatialHash,
    minDistanceM: Double,
    isCancelled: () -> Boolean,
    onPlaced: (placed: Int) -> Unit,
    accept: (lat: Double, lng: Double) -> Int,
    emit: (lat: Double, lng: Double, stratum: Int) -> Unit,
  ): PlaceOutcome? {
    if (count == 0) return null
    val ll = DoubleArray(2)
    val maxAttempts = count.toLong() * MAX_ATTEMPTS_PER_PLOT
    var attempts = 0L
    var placed = 0
    while (placed < count) {
      if (attempts >= maxAttempts) {
        return PlaceOutcome.Failed(
          if (minDistanceM > 0) {
            "Cannot fit $count plots at least ${formatMeters(minDistanceM)} m apart in the " +
              "sampling area (placed $placed after $attempts attempts)"
          } else {
            "Cannot place $count plots in the sampling area (placed $placed after $attempts attempts)"
          }
        )
      }
      if (attempts % CHECK_INTERVAL == 0L && isCancelled()) return PlaceOutcome.Cancelled
      attempts++
      val x = bb.minX + rng.nextDouble() * (bb.maxX - bb.minX)
      val y = bb.minY + rng.nextDouble() * (bb.maxY - bb.minY)
      area.projection.inverse(x, y, ll)
      val lat = quantizeDegrees(ll[0])
      val lng = quantizeDegrees(ll[1])
      val stratum = accept(lat, lng)
      if (stratum < 0 || hash.hasNeighborWithin(lat, lng)) continue
      hash.insert(lat, lng)
      emit(lat, lng, stratum)
      placed++
      if (placed % CHECK_INTERVAL == 0) onPlaced(placed)
    }
    return null
  }

  private fun placeSimpleRandom(
    area: PreparedArea,
    layout: PlotLayout.SimpleRandom,
    seed: Long,
    out: PlacementBuilder,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): PlaceOutcome? {
    val rng = SplitMix64(SplitMix64.derive(seed, STREAM_RANDOM))
    val hash = PointSpatialHash(area.index.bounds, layout.minDistanceM)
    return rejectionSample(
      area,
      area.xyBounds,
      layout.count,
      rng,
      hash,
      layout.minDistanceM,
      isCancelled,
      onPlaced = { onProgress(it.toDouble() / layout.count) },
      accept = { lat, lng -> area.stratumOf(lat, lng) },
      emit = { lat, lng, stratum -> out.add(lat, lng, stratum, -1) },
    )
  }

  private fun placeStratified(
    area: PreparedArea,
    layout: PlotLayout.StratifiedRandom,
    seed: Long,
    out: PlacementBuilder,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): PlaceOutcome? {
    val counts = allocate(area, layout)
    val total = counts.sum()
    val hash = PointSpatialHash(area.index.bounds, layout.minDistanceM)
    val stratifiedSeed = SplitMix64.derive(seed, STREAM_STRATIFIED)
    var done = 0
    for (s in counts.indices) {
      val n = counts[s]
      if (n == 0) continue
      val bb = area.stratumXyBounds[s]
      if (area.stratumAreaHa[s] <= 0 || bb.isEmpty) {
        return PlaceOutcome.Failed(
          "Cannot place $n plots in stratum \"${area.strataIds[s]}\": it has no area"
        )
      }
      val rng = SplitMix64(SplitMix64.derive(stratifiedSeed, area.strataIds[s]))
      val base = done
      val failure =
        rejectionSample(
          area,
          bb,
          n,
          rng,
          hash,
          layout.minDistanceM,
          isCancelled,
          onPlaced = { onProgress((base + it).toDouble() / total) },
          accept = { lat, lng -> if (area.stratumOf(lat, lng) == s) s else -1 },
          emit = { lat, lng, stratum -> out.add(lat, lng, stratum, -1) },
        )
      if (failure != null) {
        return if (failure is PlaceOutcome.Failed) {
          PlaceOutcome.Failed("Stratum \"${area.strataIds[s]}\": ${failure.message}")
        } else failure
      }
      done += n
    }
    return null
  }

  private fun placeCluster(
    area: PreparedArea,
    layout: PlotLayout.Cluster,
    seed: Long,
    out: PlacementBuilder,
    onProgress: (Double) -> Unit,
    isCancelled: () -> Boolean,
  ): PlaceOutcome? {
    val rng = SplitMix64(SplitMix64.derive(seed, STREAM_CLUSTER))
    val hash = PointSpatialHash(area.index.bounds, layout.minDistanceM)
    val centerLats = DoubleArray(layout.clusterCount)
    val centerLngs = DoubleArray(layout.clusterCount)
    var k = 0
    val failure =
      rejectionSample(
        area,
        area.xyBounds,
        layout.clusterCount,
        rng,
        hash,
        layout.minDistanceM,
        isCancelled,
        onPlaced = { onProgress(0.5 * it / layout.clusterCount) },
        accept = { lat, lng -> area.stratumOf(lat, lng) },
        emit = { lat, lng, _ ->
          centerLats[k] = lat
          centerLngs[k] = lng
          k++
        },
      )
    if (failure != null) {
      return if (failure is PlaceOutcome.Failed) {
        PlaceOutcome.Failed(failure.message.replace(" plots", " clusters"))
      } else failure
    }
    val n = layout.plotsPerSide
    val half = (n - 1) / 2.0
    for (c in 0 until layout.clusterCount) {
      if (c % CHECK_INTERVAL == 0) {
        if (isCancelled()) return PlaceOutcome.Cancelled
        onProgress(0.5 + 0.5 * c / max(1, layout.clusterCount))
      }
      val frame = org.groundplatform.v2.core.geo.LocalMetricFrame(centerLats[c], centerLngs[c])
      // Reading order: rows from north to south, west to east within a row.
      for (r in 0 until n) for (col in 0 until n) {
        val lat = quantizeDegrees(frame.lat((half - r) * layout.plotSpacingM))
        val lng = quantizeDegrees(frame.lng((col - half) * layout.plotSpacingM))
        val stratum = area.stratumOf(lat, lng)
        if (stratum >= 0) out.add(lat, lng, stratum, c)
      }
    }
    onProgress(1.0)
    return null
  }

  /** Plot counts per stratum index for a stratified design (largest-remainder rounding). */
  private fun allocate(area: PreparedArea, layout: PlotLayout.StratifiedRandom): IntArray {
    val k = area.strataIds.size
    return when (val allocation = layout.allocation) {
      is Allocation.Custom -> IntArray(k) { allocation.countsByStratum[area.strataIds[it]] ?: 0 }
      Allocation.Equal -> largestRemainder(layout.totalCount, DoubleArray(k) { 1.0 })
      Allocation.Proportional -> largestRemainder(layout.totalCount, area.stratumAreaHa)
    }
  }

  /**
   * Splits [total] into integer parts proportional to [weights] using the largest-remainder
   * (Hamilton) method, so the parts always sum to [total]. Ties go to the earlier stratum.
   */
  internal fun largestRemainder(total: Int, weights: DoubleArray): IntArray {
    val k = weights.size
    val result = IntArray(k)
    val sum = weights.sum()
    if (k == 0 || total <= 0 || !(sum > 0)) return result
    val remainders = DoubleArray(k)
    var assigned = 0
    for (i in 0 until k) {
      val quota = total * (weights[i] / sum)
      result[i] = floor(quota).toInt()
      remainders[i] = quota - result[i]
      assigned += result[i]
    }
    val order = (0 until k).sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
    var i = 0
    while (assigned < total) {
      result[order[i % k]]++
      assigned++
      i++
    }
    return result
  }

  private fun formatMeters(m: Double): String =
    if (m == floor(m) && m < 1e15) m.toLong().toString() else m.toString()

  /** Projected bounds in meters. */
  internal class XyBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val isEmpty: Boolean
      get() = minX > maxX || minY > maxY
  }

  /** The sampling area with containment indexes, stratum areas and projected extents. */
  internal class PreparedArea(area: SamplingArea) {
    val strataIds: List<String>
    val stratumAreaHa: DoubleArray
    val stratumXyBounds: Array<XyBounds>
    val totalAreaHa: Double
    val index: RingSetIndex
    val projection: LambertAzimuthalEqualArea
    val xyBounds: XyBounds
    private val ringStratum: IntArray

    init {
      val ids = LinkedHashMap<String, Int>()
      val rings = ArrayList<IndexedRing>(area.strata.size)
      ringStratum = IntArray(area.strata.size)
      area.strata.forEachIndexed { i, stratum ->
        ringStratum[i] = ids.getOrPut(stratum.id) { ids.size }
        val ring = stratum.ring
        rings.add(
          IndexedRing(
            DoubleArray(ring.size) { ring[it].lat },
            DoubleArray(ring.size) { ring[it].lng },
          )
        )
      }
      strataIds = ids.keys.toList()
      index = RingSetIndex(rings)
      stratumAreaHa = DoubleArray(strataIds.size)
      rings.forEachIndexed { i, ring -> stratumAreaHa[ringStratum[i]] += ring.areaHa() }
      totalAreaHa = stratumAreaHa.sum()
      val b = index.bounds
      projection =
        if (b.isEmpty) LambertAzimuthalEqualArea(0.0, 0.0)
        else LambertAzimuthalEqualArea((b.minLat + b.maxLat) / 2, (b.minLng + b.maxLng) / 2)
      val minX = DoubleArray(strataIds.size) { Double.POSITIVE_INFINITY }
      val minY = DoubleArray(strataIds.size) { Double.POSITIVE_INFINITY }
      val maxX = DoubleArray(strataIds.size) { Double.NEGATIVE_INFINITY }
      val maxY = DoubleArray(strataIds.size) { Double.NEGATIVE_INFINITY }
      val xy = DoubleArray(2)
      rings.forEachIndexed { r, ring ->
        val s = ringStratum[r]
        for (v in 0 until ring.size) {
          projection.forward(ring.lats[v], ring.lngs[v], xy)
          minX[s] = min(minX[s], xy[0])
          minY[s] = min(minY[s], xy[1])
          maxX[s] = max(maxX[s], xy[0])
          maxY[s] = max(maxY[s], xy[1])
        }
      }
      // Pad slightly: edges are straight in lat/lng, so they can bulge past the projected vertices.
      stratumXyBounds =
        Array(strataIds.size) {
          val padX = (maxX[it] - minX[it]) * 0.005
          val padY = (maxY[it] - minY[it]) * 0.005
          XyBounds(minX[it] - padX, minY[it] - padY, maxX[it] + padX, maxY[it] + padY)
        }
      xyBounds =
        if (stratumXyBounds.isEmpty()) XyBounds(0.0, 0.0, -1.0, -1.0)
        else
          XyBounds(
            stratumXyBounds.minOf { it.minX },
            stratumXyBounds.minOf { it.minY },
            stratumXyBounds.maxOf { it.maxX },
            stratumXyBounds.maxOf { it.maxY },
          )
    }

    /** Stratum index of the first ring containing the point, or -1. */
    fun stratumOf(lat: Double, lng: Double): Int {
      val ring = index.find(lat, lng)
      return if (ring < 0) -1 else ringStratum[ring]
    }
  }

  private class PlacementBuilder(val area: PreparedArea, val design: SampleDesign) {
    private var lats = DoubleArray(1024)
    private var lngs = DoubleArray(1024)
    private var strata = IntArray(1024)
    private var clusters = IntArray(1024)
    var size = 0
      private set

    fun add(lat: Double, lng: Double, stratum: Int, cluster: Int) {
      if (size == lats.size) {
        val cap = size * 2
        lats = lats.copyOf(cap)
        lngs = lngs.copyOf(cap)
        strata = strata.copyOf(cap)
        clusters = clusters.copyOf(cap)
      }
      lats[size] = lat
      lngs[size] = lng
      strata[size] = stratum
      clusters[size] = cluster
      size++
    }

    fun build(): Placement =
      Placement(
        area,
        design,
        lats.copyOf(size),
        lngs.copyOf(size),
        strata.copyOf(size),
        clusters.copyOf(size),
      )
  }

  /** Placed plot centers plus everything needed to materialize any plot independently. */
  private class Placement(
    val area: PreparedArea,
    val design: SampleDesign,
    val lats: DoubleArray,
    val lngs: DoubleArray,
    val strata: IntArray,
    val clusters: IntArray,
  ) {
    val size: Int = lats.size
    private val idWidth = max(6, size.toString().length)
    private val weights: DoubleArray
    private val sampleOrder: IntArray
    private val clusterIdWidth: Int
    private val subPlotSeed = SplitMix64.derive(design.seed, STREAM_SUBPLOT)

    init {
      val perStratum = IntArray(area.strataIds.size)
      for (s in strata) perStratum[s]++
      weights =
        DoubleArray(area.strataIds.size) {
          if (perStratum[it] == 0) 0.0 else area.stratumAreaHa[it] / perStratum[it]
        }
      sampleOrder = IntArray(size) { it + 1 }
      if (design.shuffle && size > 1) {
        // Fisher–Yates (Durstenfeld) shuffle driven by a dedicated stream.
        val rng = SplitMix64(SplitMix64.derive(design.seed, STREAM_SHUFFLE))
        for (i in size - 1 downTo 1) {
          val j = rng.nextInt(i + 1)
          val t = sampleOrder[i]
          sampleOrder[i] = sampleOrder[j]
          sampleOrder[j] = t
        }
      }
      val maxCluster = clusters.maxOrNull() ?: -1
      clusterIdWidth = max(6, (maxCluster + 1).toString().length)
    }

    fun materialize(i: Int): GeneratedPlot {
      val plotId = "P" + (i + 1).toString().padStart(idWidth, '0')
      val center = GeoCoord(lats[i], lngs[i])
      val boundary = PlotGeometry.boundary(center, design.plotShape, design.plotSizeM)
      val samples =
        PlotGeometry.subPlotSamples(
          center,
          boundary,
          design.plotShape,
          design.plotSizeM,
          design.subPlot,
          SplitMix64.derive(subPlotSeed, plotId),
        )
      val cluster = clusters[i]
      return GeneratedPlot(
        plotId = plotId,
        center = center,
        boundary = boundary,
        stratum = area.strataIds[strata[i]],
        sampleOrder = sampleOrder[i],
        inclusionWeightHa = weights[strata[i]],
        clusterId =
          if (cluster < 0) "" else "C" + (cluster + 1).toString().padStart(clusterIdWidth, '0'),
        samples = samples,
      )
    }
  }
}
