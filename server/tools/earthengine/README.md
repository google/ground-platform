<!--
  Copyright 2026 The Collect One Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Earth Engine Expression Templates

The web console's **Time series & imagery** tab (Collect Earth Online's Geo-Dash) is served by
`GeoDataService.GetPlotStatistics` and `GetPlotTimeSeries`, which evaluate Earth Engine computations
with the REST `value:compute` method. The computations are written with the Earth Engine Python
client in [`generate_templates.py`](generate_templates.py) and checked in as JSON under
[`src/main/resources/earthengine/`](../../src/main/resources/earthengine/).

Each template is a serialized `ee.CustomFunction`: an `Expression` whose result is a
`FunctionDefinition` with named arguments (`geometry`, `start`, `end`, or `popYear`). The server
(`EarthEngineTemplate.invoke`) adds the argument values and a `FunctionInvocation` that refers to the
definition through `function_reference`, so no expression is assembled by hand in Kotlin.

| Template | Arguments | Result |
| :--- | :--- | :--- |
| `landsat_time_series` | `geometry`, `start`, `end` | Per-scene plot means of Landsat 4–9 TOA reflectance, NDVI, EVI, EVI2, NBR, NDMI, NDWI, and NDFI |
| `sentinel2_time_series` | `geometry`, `start`, `end` | Per-scene plot means of Sentinel-2 spectral indices, cloud-masked with Cloud Score+ |
| `sentinel1_time_series` | `geometry`, `start`, `end` | Per-scene plot means of Sentinel-1 VV, VH, VV/VH, and RFDI from the dominant orbit direction |
| `plot_statistics` | `geometry`, `popYear` | Elevation (Copernicus GLO-30) and population (WorldPop R2025A) |

Series results are lists of `{t, id, p, v}` observations: acquisition time, scene asset ID, platform,
and a dictionary of band means.

## Regenerating

Initializing the Python client needs an account with Earth Engine access, because it downloads the
algorithm signatures; generating the templates doesn't run any computation.

```bash
python3 -m venv /tmp/ee-venv && /tmp/ee-venv/bin/pip install earthengine-api
/tmp/ee-venv/bin/python server/tools/earthengine/generate_templates.py \
  --project <cloud-project> --service-account-key <key.json>
```

Bump `TEMPLATE_VERSION` when an algorithm changes in a way that should invalidate cached results,
and run `ImageryAnalyticsTest`, which checks that every template loads with the expected arguments.

## Service account permissions

The server's Earth Engine service account needs `earthengine.maps.create` (overlays and scenes) and
`earthengine.computations.create` (`value:compute`), plus `serviceusage.services.use` on the project.
