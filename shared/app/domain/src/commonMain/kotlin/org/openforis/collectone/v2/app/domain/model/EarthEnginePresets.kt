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

import google.earthengine.v1main.Expression
import google.earthengine.v1main.FunctionInvocation
import google.earthengine.v1main.ValueNode

/**
 * Built-in Earth Engine imagery layers, replicating the default Google Earth Engine imagery of Open
 * Foris Collect Earth Online (Sentinel-2 band combinations and NDVI, Sentinel-1 SAR, and Dynamic
 * World land cover).
 *
 * Expressions call Earth Engine's server-side algorithms directly (`Collection.filter`,
 * `reduce.median`, ...; see the Earth Engine algorithms reference), not the client library's
 * conveniences such as `filterDate`. Numbers in `constant_value` are always [Double]s: proto JSON
 * reads `google.protobuf.Value` numbers back as doubles, so this keeps seed round trips exact.
 */
object EarthEnginePresets {
  private const val START_DATE = "2025-01-01"
  private const val END_DATE = "2026-01-01"

  private const val SENTINEL_2 = "COPERNICUS/S2_SR_HARMONIZED"
  private const val SENTINEL_1 = "COPERNICUS/S1_GRD"
  private const val DYNAMIC_WORLD = "GOOGLE/DYNAMICWORLD/V1"

  /** Collect Earth Online's NDVI palette. */
  private val NDVI_PALETTE = listOf("c9c0bf", "435ebf", "eee8aa", "006400")

  /** Dynamic World's 9-class land cover palette (water … snow and ice). */
  private val DYNAMIC_WORLD_PALETTE =
    listOf(
      "419BDF",
      "397D49",
      "88B053",
      "7A87C6",
      "E49635",
      "DFC35A",
      "C4281B",
      "A59B8F",
      "B39FE1",
    )

  val presets: List<ImagerySource> =
    listOf(
      source(
        id = "imagery-all-users-ee-s2-true-color",
        name = "Sentinel-2 True Color",
        image = visualize(sentinel2Composite(), bands = listOf("B4", "B3", "B2"), max = 3000.0),
      ),
      source(
        id = "imagery-all-users-ee-s2-false-color-ir",
        name = "Sentinel-2 False Color Infrared",
        image = visualize(sentinel2Composite(), bands = listOf("B8", "B4", "B3"), max = 4000.0),
      ),
      source(
        id = "imagery-all-users-ee-s2-agriculture",
        name = "Sentinel-2 Agriculture",
        image = visualize(sentinel2Composite(), bands = listOf("B11", "B8", "B2"), max = 4000.0),
      ),
      source(
        id = "imagery-all-users-ee-s2-ndvi",
        name = "Sentinel-2 NDVI",
        image =
          visualize(
            call(
              "Image.normalizedDifference",
              "input" to sentinel2Composite(),
              "bandNames" to constant(listOf("B8", "B4")),
            ),
            min = -0.2,
            max = 0.8,
            palette = NDVI_PALETTE,
          ),
      ),
      source(
        id = "imagery-all-users-ee-s1-sar",
        name = "Sentinel-1 SAR (VV / VH)",
        image =
          visualize(
            median(
              filter(
                filter(load(SENTINEL_1), dateFilter()),
                call(
                  "Filter.equals",
                  "leftField" to constant("instrumentMode"),
                  "rightValue" to constant("IW"),
                ),
              )
            ),
            // GRD backscatter is in dB.
            bands = listOf("VV", "VH", "VV"),
            min = -25.0,
            max = 0.0,
          ),
      ),
      source(
        id = "imagery-all-users-ee-dynamic-world",
        name = "Dynamic World Land Cover",
        image =
          visualize(
            call(
              "reduce.mode",
              "collection" to filter(load(DYNAMIC_WORLD), dateFilter()),
            ),
            bands = listOf("label"),
            min = 0.0,
            max = 8.0,
            palette = DYNAMIC_WORLD_PALETTE,
          ),
      ),
    )

  fun defaultImagerySources(): List<ImagerySource> = presets

  private fun source(id: String, name: String, image: ValueNode): ImagerySource =
    ImagerySource(
      id = id,
      name = name,
      urlTemplate = "",
      type = ImagerySourceType.EARTH_ENGINE,
      allowOfflineDownload = false,
      expression = Expression(values = mapOf(RESULT to image), result = RESULT),
    )

  /** Sentinel-2 surface reflectance median composite with < 20% cloudy pixels. */
  private fun sentinel2Composite(): ValueNode =
    median(
      filter(
        filter(load(SENTINEL_2), dateFilter()),
        call(
          "Filter.lessThan",
          "leftField" to constant("CLOUDY_PIXEL_PERCENTAGE"),
          "rightValue" to constant(20.0),
        ),
      )
    )

  private fun load(collectionId: String): ValueNode =
    call("ImageCollection.load", "id" to constant(collectionId))

  private fun dateFilter(): ValueNode =
    call(
      "Filter.dateRangeContains",
      "leftValue" to
        call("DateRange", "start" to constant(START_DATE), "end" to constant(END_DATE)),
      "rightField" to constant("system:time_start"),
    )

  private fun filter(collection: ValueNode, filter: ValueNode): ValueNode =
    call("Collection.filter", "collection" to collection, "filter" to filter)

  private fun median(collection: ValueNode): ValueNode =
    call("reduce.median", "collection" to collection)

  private fun visualize(
    image: ValueNode,
    bands: List<String>? = null,
    min: Double = 0.0,
    max: Double,
    palette: List<String>? = null,
  ): ValueNode =
    call(
      "Image.visualize",
      *listOfNotNull(
          "image" to image,
          bands?.let { "bands" to constant(it) },
          "min" to constant(min),
          "max" to constant(max),
          palette?.let { "palette" to constant(it) },
        )
        .toTypedArray(),
    )

  private fun call(name: String, vararg args: Pair<String, ValueNode>): ValueNode =
    ValueNode(
      function_invocation_value = FunctionInvocation(function_name = name, arguments = args.toMap())
    )

  private fun constant(value: Any): ValueNode = ValueNode(constant_value = value)

  private const val RESULT = "0"
}
