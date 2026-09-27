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

/**
 * Screen orientation of the simulated device in the Prototype App wrapper (`Portrait` vs
 * `Landscape`).
 */
enum class DeviceOrientation(val label: String) {
  PORTRAIT("Portrait"),
  LANDSCAPE("Landscape"),
}

/**
 * Device hardware bezel form factor selectable in the Prototype App wrapper page (`Mobile` vs
 * `Tablet`).
 */
enum class DeviceFormFactor(
  val label: String,
  val dimensionsLabel: String,
  val frameWidthDp: Int,
  val frameHeightDp: Int,
  val outerCornerRadiusDp: Int,
  val innerCornerRadiusDp: Int,
  val defaultOrientation: DeviceOrientation,
) {
  MOBILE(
    label = "Mobile",
    dimensionsLabel = "404 × 764 dp",
    frameWidthDp = 404,
    frameHeightDp = 764,
    outerCornerRadiusDp = 40,
    innerCornerRadiusDp = 32,
    defaultOrientation = DeviceOrientation.PORTRAIT,
  ),
  TABLET(
    label = "Tablet",
    dimensionsLabel = "780 × 620 dp",
    frameWidthDp = 780,
    frameHeightDp = 620,
    outerCornerRadiusDp = 28,
    innerCornerRadiusDp = 20,
    defaultOrientation = DeviceOrientation.LANDSCAPE,
  );

  /** Returns the device bezel width in `dp` for the given [orientation]. */
  fun widthForOrientation(orientation: DeviceOrientation): Int =
    when (orientation) {
      DeviceOrientation.PORTRAIT -> minOf(frameWidthDp, frameHeightDp)
      DeviceOrientation.LANDSCAPE -> maxOf(frameWidthDp, frameHeightDp)
    }

  /** Returns the device bezel height in `dp` for the given [orientation]. */
  fun heightForOrientation(orientation: DeviceOrientation): Int =
    when (orientation) {
      DeviceOrientation.PORTRAIT -> maxOf(frameWidthDp, frameHeightDp)
      DeviceOrientation.LANDSCAPE -> minOf(frameWidthDp, frameHeightDp)
    }

  /** Returns the formatted `W × H dp` label for the given [orientation]. */
  fun dimensionsLabelForOrientation(orientation: DeviceOrientation): String =
    "${widthForOrientation(orientation)} × ${heightForOrientation(orientation)} dp"
}
