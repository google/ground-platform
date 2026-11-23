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
package org.openforis.collectone.v2.core.geo

/**
 * A WGS 84 coordinate in decimal degrees, as `shared/core` computes with it: the geo file readers,
 * the sample design engine, and the impact calculations.
 *
 * It is not the map's `LatLng` (`:shared:map:geometry`), which only holds valid coordinates: a
 * [GeoCoord] holds whatever a file or a computation produced, including out-of-range or NaN values,
 * so `RingValidator` can report them and the engine's arithmetic can step past the range edges. It
 * also lives below the map types, which `shared/core` (shared with the server) doesn't depend on.
 * Code that has a `LatLng` converts it at the boundary (see
 * `docs/technical/model/data/05-geometry.md`).
 */
data class GeoCoord(val lat: Double, val lng: Double)
