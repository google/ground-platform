<!--
  Copyright 2026 The Ground Authors.

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

# Impact Aggregation

`ImpactAggregator.run` turns survey snapshots (map features, concept links, impact events, and
survey outcomes) into privacy-safe aggregate rows at survey, organization, country, global, and S2
grid cell scope. It implements the aggregation job designed in
[Impact Aggregation and Spatial Overlays](../../../../../../../../../../docs/technical/backend/impact-aggregation.md);
rows correspond to `groundplatform.v2.data.ImpactAggregate`.

The same pure, deterministic code runs in the backend job (JVM) and in clients, so dashboards
match across environments. A run is O(n log n) in the number of features.

## Usage

```kotlin
val result = ImpactAggregator.run(ImpactInput(surveys, concepts, organizations))
result.rows(ImpactScopeType.SURVEY, surveyId)  // A survey dashboard.
result.rows(ImpactScopeType.GLOBAL)            // Platform-wide totals and suggested indicators.
result.diagnostics.forSurvey(surveyId)         // Skipped links and values, for "why?" hints.
result.attributions.getValue(surveyId).score   // 1–5 attribution score.
```

## Files

| File | Contents |
|---|---|
| `ImpactModels.kt` | Public inputs, configuration, and outputs |
| `ImpactAggregator.kt` | The run (scopes and row order) and UCUM unit conversion (`ImpactUnits`) |
| `ImpactDedup.kt` | Link resolution, GeoID deduplication, geodesic area, representative points |
| `ImpactConceptAggregation.kt` | Number parsing, unit normalization, per-rule concept rows, distinct values |
| `ImpactAttributionRules.kt` | Attribution score, outcome keys, event, outcome, and attribution rows |
| `ImpactCells.kt` | Grid cell thresholds, merging into coarse cells, and suppression |

## Key rules

- **Deduplication**: per scope, by trimmed GeoID (features without one count individually). The
  most recently updated duplicate wins (ties: smallest entity ID, then survey ID) and alone
  provides area and values.
- **Privacy**: `SENSITIVE` concepts and organization concepts never reach main global or country
  rows; organization concepts with suggested goals appear globally only as
  organization-suggested rows. Organizations with `exclude_from_platform_aggregates` are left out
  of global, country, cell, and suggested rows.
- **Thresholds**: cells need `cellMinFeatures` features from `cellMinOrganizations` organizations,
  else merge into their coarse ancestor or are suppressed; countries need `countryMinFeatures`.
  Suppressed rows keep zeroed counts so dashboards can show "fewer than 10".
