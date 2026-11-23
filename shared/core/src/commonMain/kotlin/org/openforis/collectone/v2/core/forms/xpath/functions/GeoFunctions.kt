/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.forms.xpath.functions

import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** ODK geographic functions over geopoint, geotrace and geoshape values. */
internal object GeoFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("area") {
      checkEagerArgCount(name, args, 1..1)
      val points = GeoUtils.extractPoints(args[0])
      XPathValue.Number(GeoUtils.calculateAreaSquareMeters(points))
    }
    define("distance") {
      checkEagerArgCount(name, args, 1..Int.MAX_VALUE)
      if (args.size == 1) {
        val points = GeoUtils.extractPoints(args[0])
        XPathValue.Number(GeoUtils.totalDistanceMeters(points))
      } else {
        val points = mutableListOf<openforis.collectone.v1.forms.GeoPoint>()
        var missingArg = false
        for (arg in args) {
          val extracted = GeoUtils.extractPoints(arg)
          if (extracted.isEmpty()) {
            missingArg = true
            break
          }
          points.addAll(extracted)
        }
        if (missingArg || points.size < 2) {
          XPathValue.Number(0.0)
        } else {
          XPathValue.Number(GeoUtils.totalDistanceMeters(points))
        }
      }
    }
    define("geofence") {
      checkEagerArgCount(name, args, 2..2)
      val pt = GeoUtils.extractPoints(args[0]).firstOrNull()
      val polygon = GeoUtils.extractPoints(args[1])
      val inside =
        if (pt != null && polygon.size >= 3) GeoUtils.isPointInPolygon(pt, polygon) else false
      XPathValue.Bool(inside)
    }
    define("intersects") {
      checkEagerArgCount(name, args, 1..2)
      val points = GeoUtils.extractPoints(args[0])
      XPathValue.Bool(GeoUtils.hasSelfIntersection(points))
    }
  }
}
