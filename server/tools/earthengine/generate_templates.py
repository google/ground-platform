#!/usr/bin/env python3
#
# Copyright 2026 The Collect One Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#
# The Landsat preparation, spectral indices, NDFI unmixing endmembers, and Sentinel-1 band math
# below are adapted from Collect Earth Online's Geo-Dash
# (https://github.com/openforis/collect-earth-online, src/py/gee/inputs.py and utils.py), which is
# distributed under the following license:
#
#   MIT License
#
#   Copyright (c) 2016-2021 FAO
#
#   Permission is hereby granted, free of charge, to any person obtaining a copy of this software
#   and associated documentation files (the "Software"), to deal in the Software without
#   restriction, including without limitation the rights to use, copy, modify, merge, publish,
#   distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
#   Software is furnished to do so, subject to the following conditions:
#
#   The above copyright notice and this permission notice shall be included in all copies or
#   substantial portions of the Software.
#
#   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
#   BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
#   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
#   DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
"""Generates the Earth Engine expression templates behind `GeoDataService`'s plot analytics.

Each template is an `ee.CustomFunction` serialized with the Earth Engine Cloud API encoder: an
`Expression` whose result is a `FunctionDefinition` with named arguments. The server
(`EarthEngineTemplates.kt`) adds argument values and a `FunctionInvocation` that refers to the
definition through `function_reference`, then sends the expression to `value:compute`.

Writing the algorithms with the Python client keeps them readable and close to Collect Earth
Online's Geo-Dash; regenerating a template is a reviewable JSON diff. Run from the repository root:

    python3 -m venv /tmp/ee-venv && /tmp/ee-venv/bin/pip install earthengine-api
    /tmp/ee-venv/bin/python server/tools/earthengine/generate_templates.py \
        --project <cloud-project> [--service-account-key <key.json>]

Initializing the client needs Earth Engine access (it downloads the algorithm signatures), but
generating the templates doesn't run any computation.
"""

import argparse
import json
import os
import sys

import ee

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "earthengine"
)

# Bumped whenever an algorithm changes in a way that should invalidate cached results.
TEMPLATE_VERSION = 1

# Landsat Collection 2 Tier 1 TOA reflectance (as in Geo-Dash's degradation tool), newest first.
# Bands: blue, green, red, NIR, SWIR1, SWIR2, and the QA band.
LANDSAT_TOA_COLLECTIONS = [
    ("LANDSAT/LC09/C02/T1_TOA", ["B2", "B3", "B4", "B5", "B6", "B7", "QA_PIXEL"]),
    ("LANDSAT/LC08/C02/T1_TOA", ["B2", "B3", "B4", "B5", "B6", "B7", "QA_PIXEL"]),
    ("LANDSAT/LE07/C02/T1_TOA", ["B1", "B2", "B3", "B4", "B5", "B7", "QA_PIXEL"]),
    ("LANDSAT/LT05/C02/T1_TOA", ["B1", "B2", "B3", "B4", "B5", "B7", "QA_PIXEL"]),
    ("LANDSAT/LT04/C02/T1_TOA", ["B1", "B2", "B3", "B4", "B5", "B7", "QA_PIXEL"]),
]
REFLECTANCE_BANDS = ["BLUE", "GREEN", "RED", "NIR", "SWIR1", "SWIR2"]
LANDSAT_SERIES_BANDS = REFLECTANCE_BANDS + ["NDVI", "EVI", "EVI2", "NBR", "NDMI", "NDWI", "NDFI"]
SENTINEL2_SERIES_BANDS = ["NDVI", "EVI", "EVI2", "NBR", "NDMI", "NDWI"]
SENTINEL1_SERIES_BANDS = ["VV", "VH", "VV/VH", "RFDI"]

# Geo-Dash keeps Landsat scenes with less than 60% cloud cover.
LANDSAT_MAX_CLOUD_COVER = 60

# QA_PIXEL bits masked out: dilated cloud (1), cirrus (2), cloud (3), cloud shadow (4), snow (5).
# Geo-Dash meant to mask bits 3-5, but its `getBitMask` ORs the tests into an all-ones image, so
# its mask never removes a pixel; this applies the intended test (plus dilated cloud and cirrus).
LANDSAT_QA_MASK = (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)

# Spectral mixture endmembers (blue, green, red, NIR, SWIR1, SWIR2) for NDFI (Souza et al. 2005).
NDFI_ENDMEMBERS = {
    "GV": [0.0500, 0.0900, 0.0400, 0.6100, 0.3000, 0.1000],
    "Shade": [0, 0, 0, 0, 0, 0],
    "NPV": [0.1400, 0.1700, 0.2200, 0.3000, 0.5500, 0.3000],
    "Soil": [0.2000, 0.3000, 0.3400, 0.5800, 0.6000, 0.5800],
    "Cloud": [0.9000, 0.9600, 0.8000, 0.7800, 0.7200, 0.6500],
}
NDFI_MAX_CLOUD_FRACTION = 0.1

# Sentinel-2 pixels need a Cloud Score+ `cs_cdf` of at least this value (Google's recommended
# default for clear pixels).
SENTINEL2_MIN_CLEAR_SCORE = 0.6
SENTINEL2_MAX_SCENE_CLOUD = 80

# Reductions use Geo-Dash's `bestEffort` with a pixel cap so large plots degrade gracefully.
MAX_PIXELS = 1e6


def _reduce_mean(image, geometry, scale):
  return image.reduceRegion(
      reducer=ee.Reducer.mean(),
      geometry=geometry,
      scale=scale,
      maxPixels=MAX_PIXELS,
      bestEffort=True,
  )


def _spectral_indices(refl):
  """Normalized-difference and enhanced vegetation indices of a reflectance image (0-1)."""
  bands = {name: refl.select(name) for name in REFLECTANCE_BANDS}
  return [
      refl.normalizedDifference(["NIR", "RED"]).rename("NDVI"),
      refl.expression(
          "2.5 * ((NIR - RED) / (NIR + 6 * RED - 7.5 * BLUE + 1))", bands
      ).rename("EVI"),
      refl.expression("2.5 * ((NIR - RED) / (NIR + 2.4 * RED + 1))", bands).rename("EVI2"),
      refl.normalizedDifference(["NIR", "SWIR2"]).rename("NBR"),
      refl.normalizedDifference(["NIR", "SWIR1"]).rename("NDMI"),
      refl.normalizedDifference(["GREEN", "NIR"]).rename("NDWI"),
  ]


def _ndfi(refl):
  """Normalized Difference Fraction Index from spectral mixture analysis, as in Geo-Dash."""
  names = list(NDFI_ENDMEMBERS.keys())
  fractions = refl.select(REFLECTANCE_BANDS).unmix(
      [NDFI_ENDMEMBERS[name] for name in names], True, True
  ).rename(names)
  ndfi = fractions.expression(
      "((GV / (1 - SHADE)) - (NPV + SOIL)) / ((GV / (1 - SHADE)) + NPV + SOIL)",
      {
          "GV": fractions.select("GV"),
          "SHADE": fractions.select("Shade"),
          "NPV": fractions.select("NPV"),
          "SOIL": fractions.select("Soil"),
      },
  ).rename("NDFI")
  return ndfi.updateMask(fractions.select("Cloud").lt(NDFI_MAX_CLOUD_FRACTION))


def _observations(features):
  """Sorted, de-duplicated observations as a list of `{t, id, p, v}` dictionaries.

  Adjacent scenes of one overpass (Landsat WRS rows, Sentinel-2 MGRS tiles) repeat the same
  acquisition, so only the first observation per day and platform is kept.
  """
  return (
      ee.FeatureCollection(features)
      .filter(ee.Filter.notNull(["ok"]))
      .distinct(["day", "p"])
      .sort("t")
      .aggregate_array("obs")
  )


def _observation(image, platform, values, key_band):
  time = image.get("system:time_start")
  return ee.Feature(
      None,
      {
          "t": time,
          "p": platform,
          "day": ee.Date(time).format("YYYY-MM-dd"),
          "ok": values.get(key_band),
          "obs": ee.Dictionary(
              {"t": time, "id": image.get("system:id"), "p": platform, "v": values}
          ),
      },
  )


def landsat_time_series(geometry, start, end):
  """Per-scene mean reflectance, indices, and NDFI over the plot (Geo-Dash degradation tool)."""
  geometry = ee.Geometry(geometry)

  def per_collection(collection_id, bands):
    def to_observation(image):
      qa_clear = image.select("QA_PIXEL").bitwiseAnd(LANDSAT_QA_MASK).eq(0)
      refl = image.select(bands[:-1], REFLECTANCE_BANDS).updateMask(qa_clear)
      stack = refl.addBands(ee.Image.cat(_spectral_indices(refl))).addBands(_ndfi(refl))
      values = _reduce_mean(stack.select(LANDSAT_SERIES_BANDS), geometry, 30)
      return _observation(image, image.get("SPACECRAFT_ID"), values, "NDVI")

    return ee.FeatureCollection(
        ee.ImageCollection(collection_id)
        .filterBounds(geometry)
        .filterDate(start, end)
        .filter(ee.Filter.lt("CLOUD_COVER", LANDSAT_MAX_CLOUD_COVER))
        .map(to_observation)
    )

  merged = ee.FeatureCollection([])
  for collection_id, bands in LANDSAT_TOA_COLLECTIONS:
    merged = merged.merge(per_collection(collection_id, bands))
  return _observations(merged)


def sentinel2_time_series(geometry, start, end):
  """Per-scene mean spectral indices over the plot from Sentinel-2 surface reflectance."""
  geometry = ee.Geometry(geometry)
  scores = ee.ImageCollection("GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED")

  def to_observation(image):
    clear = image.select("cs_cdf").gte(SENTINEL2_MIN_CLEAR_SCORE)
    refl = (
        image.select(["B2", "B3", "B4", "B8", "B11", "B12"], REFLECTANCE_BANDS)
        .divide(10000)
        .updateMask(clear)
    )
    values = _reduce_mean(
        ee.Image.cat(_spectral_indices(refl)).select(SENTINEL2_SERIES_BANDS), geometry, 10
    )
    return _observation(image, image.get("SPACECRAFT_NAME"), values, "NDVI")

  collection = (
      ee.ImageCollection("COPERNICUS/S2_SR_HARMONIZED")
      .filterBounds(geometry)
      .filterDate(start, end)
      .filter(ee.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", SENTINEL2_MAX_SCENE_CLOUD))
      .linkCollection(scores, ["cs_cdf"])
  )
  return _observations(collection.map(to_observation))


def sentinel1_time_series(geometry, start, end):
  """Per-scene mean SAR backscatter over the plot (Geo-Dash degradation tool, SAR mode).

  Geo-Dash charts ascending passes only; this uses whichever orbit direction has more
  acquisitions over the plot, since some regions are only imaged on descending passes.
  """
  geometry = ee.Geometry(geometry)
  collection = (
      ee.ImageCollection("COPERNICUS/S1_GRD")
      .filterBounds(geometry)
      .filterDate(start, end)
      .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VV"))
      .filter(ee.Filter.listContains("transmitterReceiverPolarisation", "VH"))
      .filter(ee.Filter.eq("instrumentMode", "IW"))
  )
  ascending = collection.filter(ee.Filter.eq("orbitProperties_pass", "ASCENDING"))
  descending = collection.filter(ee.Filter.eq("orbitProperties_pass", "DESCENDING"))
  dominant = ee.ImageCollection(
      ee.Algorithms.If(ascending.size().gte(descending.size()), ascending, descending)
  )

  def to_observation(image):
    db = image.select(["VV", "VH"])
    power = ee.Image(10).pow(db.divide(10))
    rfdi = (
        power.select("VV")
        .subtract(power.select("VH"))
        .divide(power.select("VV").add(power.select("VH")))
        .rename("RFDI")
    )
    # Geo-Dash's ratio band divides the dB values.
    ratio = db.select("VV").divide(db.select("VH")).rename("VV/VH")
    values = _reduce_mean(db.addBands(ratio).addBands(rfdi), geometry, 10)
    platform = ee.String("SENTINEL-1").cat(ee.String(image.get("platform_number")))
    return _observation(image, platform, values, "VV")

  return _observations(dominant.map(to_observation))


def plot_statistics(geometry, popYear):
  """Elevation (Copernicus GLO-30) and population (WorldPop R2025A) over the plot.

  Geo-Dash used GTOPO30 (~1 km) and GPWv4.11 (~1 km); these are 30 m and 100 m. Polygons smaller
  than two DEM pixels are sampled at their centroid, as Geo-Dash does for tiny plots. Keys are left
  out where a dataset has no coverage (e.g. at sea).
  """
  geometry = ee.Geometry(geometry)
  is_small = geometry.area(1).lt(1800)
  dem_collection = (
      ee.ImageCollection("COPERNICUS/DEM/GLO30_2024_1").filterBounds(geometry).select("DEM")
  )

  def elevation_stats():
    projection = ee.Image(dem_collection.first()).projection()
    dem = dem_collection.mosaic().setDefaultProjection(projection)
    sample_geometry = ee.Geometry(ee.Algorithms.If(is_small, geometry.centroid(1), geometry))
    stats = dem.reduceRegion(
        reducer=ee.Reducer.minMax().combine(ee.Reducer.mean(), "", True),
        geometry=sample_geometry,
        scale=30,
        maxPixels=MAX_PIXELS,
        bestEffort=True,
    )
    return ee.Dictionary(
        {
            "elevation_min": stats.get("DEM_min"),
            "elevation_mean": stats.get("DEM_mean"),
            "elevation_max": stats.get("DEM_max"),
        }
    )

  pop_collection = (
      ee.ImageCollection("WorldPop/GP/100m/R2025A/v1/pop")
      .filterBounds(geometry)
      .filter(ee.Filter.eq("year", popYear))
      .select("population")
  )

  def population_stats():
    projection = ee.Image(pop_collection.first()).projection()
    population = pop_collection.mosaic().setDefaultProjection(projection)
    # Weighted sums count the fraction of each pixel inside the plot. WorldPop masks unpopulated
    # pixels, so the density is the total over the plot's area rather than a mean of pixels; points
    # and tiny plots take the density of the pixel under their centroid.
    total = ee.Number(
        population.reduceRegion(
            reducer=ee.Reducer.sum(), geometry=geometry, maxPixels=MAX_PIXELS, bestEffort=True
        ).get("population")
    )
    pixel_density = (
        population.unmask(0)
        .divide(ee.Image.pixelArea().reproject(projection))
        .multiply(1e6)
        .reduceRegion(
            reducer=ee.Reducer.first(), geometry=geometry.centroid(1), maxPixels=MAX_PIXELS
        )
        .get("population")
    )
    density = ee.Algorithms.If(
        is_small, pixel_density, total.divide(geometry.area(1).divide(1e6))
    )
    return ee.Dictionary({"population": total, "population_density_per_km2": density})

  elevation = ee.Dictionary(
      ee.Algorithms.If(dem_collection.size().gt(0), elevation_stats(), ee.Dictionary({}))
  )
  population = ee.Dictionary(
      ee.Algorithms.If(pop_collection.size().gt(0), population_stats(), ee.Dictionary({}))
  )
  return elevation.combine(population)


# Template name -> (function, argument names). Arguments are declared as `Object` because the
# client can't serialize `String` or `Number` function variables; the functions cast them.
TEMPLATES = {
    "landsat_time_series": (landsat_time_series, ["geometry", "start", "end"]),
    "sentinel2_time_series": (sentinel2_time_series, ["geometry", "start", "end"]),
    "sentinel1_time_series": (sentinel1_time_series, ["geometry", "start", "end"]),
    "plot_statistics": (plot_statistics, ["geometry", "popYear"]),
}


def build(name):
  function, args = TEMPLATES[name]
  signature = {
      "name": "",
      "returns": "Object",
      "args": [{"name": arg, "type": "Object"} for arg in args],
  }
  custom = ee.CustomFunction(signature, function)
  return ee.serializer.encode(custom, for_cloud_api=True)


def main():
  parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
  parser.add_argument("--project", required=True, help="Cloud project registered for Earth Engine")
  parser.add_argument("--service-account-key", help="Service account key JSON (optional)")
  parser.add_argument("--output-dir", default=OUTPUT_DIR)
  options = parser.parse_args()

  if options.service_account_key:
    with open(options.service_account_key) as key:
      email = json.load(key)["client_email"]
    credentials = ee.ServiceAccountCredentials(email, options.service_account_key)
    ee.Initialize(credentials, project=options.project)
  else:
    ee.Initialize(project=options.project)

  os.makedirs(options.output_dir, exist_ok=True)
  for name in TEMPLATES:
    template = {"templateVersion": TEMPLATE_VERSION, "expression": build(name)}
    path = os.path.join(options.output_dir, name + ".json")
    with open(path, "w") as out:
      json.dump(template, out, indent=1, sort_keys=True)
      out.write("\n")
    print("Wrote", os.path.relpath(path), file=sys.stderr)


if __name__ == "__main__":
  main()
