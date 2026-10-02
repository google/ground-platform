#!/usr/bin/env python3
#
# Copyright 2026 The Ground Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Generates golden test data for AgStackGeoId from the AgStack reference code.

The S2 covering and hashing below are copied from the AgStack Asset Registry
(https://github.com/agstack/asset-registry, `s2_service.py` and `utils.py`) so
the Kotlin port can be checked against the exact algorithm the registry runs.

Usage (from shared/core/):

    python3 -m venv /tmp/geoid-venv
    /tmp/geoid-venv/bin/pip install s2sphere==0.2.5 shapely future
    /tmp/geoid-venv/bin/python tools/geoid/generate_agstack_golden.py

then format the generated Kotlin file with ktfmt (Google style).
"""

import hashlib
import math
import os
import random

import s2sphere as s2
from shapely.wkt import loads

OUTPUT = os.path.join(
    os.path.dirname(__file__),
    "../../src/commonTest/kotlin/org/groundplatform/v2/core/geo/geoid/AgStackGeoIdGoldenData.kt",
)

LICENSE = """/*
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
"""


# --- Copied from agstack/asset-registry s2_service.py (S2Service) ---------------------------------
def get_bounding_box_cell_ids(latitudes, longitudes, resolution_level):
    r = s2.RegionCoverer()
    r.min_level = resolution_level
    r.max_level = resolution_level
    lb = s2.LatLng.from_degrees(min(latitudes), min(longitudes))
    ub = s2.LatLng.from_degrees(max(latitudes), max(longitudes))
    return r.get_covering(s2.LatLngRect.from_point_pair(lb, ub))


def wkt_to_cell_tokens(field_wkt, resolution_level, point=False):
    poly = loads(field_wkt)
    if point:
        longs, lats = poly.coords.xy
    else:
        longs, lats = poly.exterior.coords.xy
    cell_ids = get_bounding_box_cell_ids(lats.tolist(), longs.tolist(), resolution_level)
    return [cell_id.to_token() for cell_id in cell_ids]


# --- Copied from agstack/asset-registry utils.py (Utils.generate_geo_id) --------------------------
def generate_geo_id(s2_cell_tokens):
    m = hashlib.sha256()
    for s in tuple(s2_cell_tokens):
        m.update(s.encode())
    return m.hexdigest()


# --------------------------------------------------------------------------------------------------


def polygon_wkt(vertices):
    ring = list(vertices) + [vertices[0]]
    return "POLYGON((" + ",".join(f"{lng!r} {lat!r}" for lat, lng in ring) + "))"


def point_wkt(vertex):
    lat, lng = vertex
    return f"POINT({lng!r} {lat!r})"


def kotlin_vertices(vertices):
    return "listOf(" + ", ".join(f"p({lat!r}, {lng!r})" for lat, lng in vertices) + ")"


def kotlin_strings(values):
    return "listOf(" + ", ".join(f'"{v}"' for v in values) + ")"


def case(name, vertices, level, point=False):
    wkt = point_wkt(vertices[0]) if point else polygon_wkt(vertices)
    tokens = wkt_to_cell_tokens(wkt, level, point=point)
    full = kotlin_strings(tokens) if len(tokens) <= 16 else "null"
    return (
        f"    GoldenCase(\n"
        f'      name = "{name}",\n'
        f"      vertices = {kotlin_vertices(vertices)},\n"
        f"      level = {level},\n"
        f"      tokenCount = {len(tokens)},\n"
        f'      firstToken = "{tokens[0]}",\n'
        f'      lastToken = "{tokens[-1]}",\n'
        f"      tokens = {full},\n"
        f'      geoId = "{generate_geo_id(tokens)}",\n'
        f"    ),"
    )


def square(lat, lng, half_size_deg):
    return [
        (lat - half_size_deg, lng - half_size_deg),
        (lat - half_size_deg, lng + half_size_deg),
        (lat + half_size_deg, lng + half_size_deg),
        (lat + half_size_deg, lng - half_size_deg),
    ]


CUBE_CORNER_LAT = math.degrees(math.asin(1 / math.sqrt(3)))

NAMED_FIELDS = [
    (
        "AgStack README field (Haryana, India)",
        [
            (30.311450431756946, 76.88855767250062),
            (30.310732833916543, 76.88841819763184),
            (30.31070505582999, 76.88945889472961),
            (30.311399505631794, 76.8894535303116),
        ],
    ),
    ("Coffee parcel (Huila, Colombia)", square(2.19385, -75.62841, 0.0006)),
    ("Cocoa farm (Bahia, Brazil)", square(-14.79612, -39.27311, 0.0011)),
    ("Smallholder plot (Kakamega, Kenya)", square(0.28271, 34.75203, 0.0004)),
    ("Antimeridian field (Taveuni, Fiji)", [(-16.8801, 179.9997), (-16.8801, -179.9996), (-16.8794, -179.9996), (-16.8794, 179.9997)]),
    ("High-latitude field (Svalbard)", square(78.2232, 15.6267, 0.0008)),
    ("Equator and prime meridian", square(0.0, 0.0, 0.0005)),
    ("Large ~940 acre ranch (Texas, USA)", square(31.9686, -99.9018, 0.0095)),
]

NAMED_POINTS = [
    ("AgStack README point (Punjab, India)", (30.90706, 74.78209)),
    ("AgStack README bulk point (Rajasthan, India)", (29.92052, 75.77439)),
    ("Face 0 center (on a cell vertex at every level)", (0.0, 0.0)),
    ("Face edge (lng 45)", (10.0, 45.0)),
    ("Cube corner", (CUBE_CORNER_LAT, 45.0)),
    ("North pole", (90.0, 0.0)),
    ("Antimeridian west edge", (12.5, -180.0)),
]


def main():
    lines = []
    for name, vertices in NAMED_FIELDS:
        for level in (13, 20):
            lines.append(case(f"{name} L{level}", vertices, level))
    for name, vertex in NAMED_POINTS:
        for level in (13, 20, 30):
            lines.append(case(f"{name} L{level}", [vertex], level, point=True))

    rng = random.Random(20261002)
    random_cases = []
    for index in range(120):
        lat = rng.uniform(-60.0, 70.0)
        lng = rng.uniform(-180.0, 180.0)
        n = rng.randint(3, 7)
        radius = rng.uniform(0.0002, 0.003)
        vertices = []
        for k in range(n):
            angle = 2 * math.pi * k / n + rng.uniform(-0.3, 0.3)
            r = radius * rng.uniform(0.6, 1.0)
            vertices.append(
                (round(lat + r * math.sin(angle), 7), round(lng + r * math.cos(angle) / max(0.2, math.cos(math.radians(lat))), 7))
            )
        vertices = [(la, ((lo + 180.0) % 360.0) - 180.0) for la, lo in vertices]
        wkt = polygon_wkt(vertices)
        l13 = generate_geo_id(wkt_to_cell_tokens(wkt, 13))
        l20_tokens = wkt_to_cell_tokens(wkt, 20)
        random_cases.append(
            f'    RandomFieldCase({kotlin_vertices(vertices)}, "{l13}", {len(l20_tokens)}, "{generate_geo_id(l20_tokens)}"),'
        )

    with open(OUTPUT, "w") as out:
        out.write(LICENSE)
        out.write("package org.groundplatform.v2.core.geo.geoid\n\n")
        out.write("import groundplatform.v2.forms.GeoPoint\n\n")
        out.write("// GENERATED by tools/geoid/generate_agstack_golden.py from the AgStack reference code. Do not\n")
        out.write("// edit by hand.\n\n")
        out.write("private fun p(latitude: Double, longitude: Double) = GeoPoint(latitude = latitude, longitude = longitude)\n\n")
        out.write("/** Hand-picked fields and points, including edge cases, at the levels the registry uses. */\n")
        out.write("internal val GOLDEN_CASES: List<GoldenCase> =\n  listOf(\n")
        out.write("\n".join(lines))
        out.write("\n  )\n\n")
        out.write("/** Seeded random small fields worldwide with their level-13 and level-20 GeoIDs. */\n")
        out.write("internal val RANDOM_FIELD_CASES: List<RandomFieldCase> =\n  listOf(\n")
        out.write("\n".join(random_cases))
        out.write("\n  )\n")


if __name__ == "__main__":
    main()
