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
package org.groundplatform.v2.core.geo.s2

/** S2 level used for `EntityRecord.s2_cell_token` (cells of about 1 km²). */
const val ENTITY_S2_CELL_LEVEL: Int = 13

/**
 * Token (`S2CellId::ToToken`) of the S2 cell at [level] containing the WGS 84 point ([latDeg],
 * [lngDeg]). Used to fill `EntityRecord.s2_cell_token` for bounding-box queries.
 */
fun s2CellToken(latDeg: Double, lngDeg: Double, level: Int = ENTITY_S2_CELL_LEVEL): String {
  require(level in 0..S2.MAX_LEVEL) { "level must be in 0..${S2.MAX_LEVEL}" }
  val point = S2.latLngToPoint(S2.degreesToRadians(latDeg), S2.degreesToRadians(lngDeg))
  return S2CellId.fromPoint(point).parent(level).toToken()
}
