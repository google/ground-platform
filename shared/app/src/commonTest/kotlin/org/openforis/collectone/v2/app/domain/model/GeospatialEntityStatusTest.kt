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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeospatialEntityStatusTest {
  private fun entity(properties: Map<String, String>) =
    GeospatialEntityItem(
      id = "e1",
      label = "Plot 1",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots",
      geoId = "",
      geometryTypeLabel = "Point",
      areaHectares = 0.0,
      perimeterMeters = 0,
      coordinatesLabel = "",
      normalizedX = 0f,
      normalizedY = 0f,
      colorHex = 0xFF2E7D32L,
      properties = properties,
      submissions = emptyList(),
    )

  @Test
  fun noSymbolOrStatus_hasNoWorkflowStatus() {
    val e = entity(mapOf("marker-color" to "#1565C0", "status" to "  "))

    assertFalse(e.hasWorkflowStatus)
    assertEquals("", e.workflowStatus)
    assertEquals("", e.mapStatusSummaryBadge)
  }

  @Test
  fun canonicalSymbolWithoutStatus_derivesStatusLabel() {
    val e = entity(mapOf("marker-symbol" to "◐"))

    assertTrue(e.hasWorkflowStatus)
    assertEquals("In progress", e.workflowStatus)
    assertEquals("◐ In progress", e.mapStatusSummaryBadge)
  }

  @Test
  fun customSymbolWithoutStatus_showsSymbolOnly() {
    val e = entity(mapOf("marker-symbol" to "★"))

    assertTrue(e.hasWorkflowStatus)
    assertEquals("", e.workflowStatus)
    assertEquals("★", e.mapStatusSummaryBadge)
  }

  @Test
  fun statusWithoutSymbol_showsStatusOnly() {
    val e = entity(mapOf("status" to "Needs review"))

    assertTrue(e.hasWorkflowStatus)
    assertEquals("Needs review", e.workflowStatus)
    assertEquals("Needs review", e.mapStatusSummaryBadge)
  }

  @Test
  fun cluster_omitsFeaturesWithoutSymbolFromStatusChipsButCountsThem() {
    val features =
      listOf(
          entity(mapOf("marker-symbol" to "✓")).copy(id = "a"),
          entity(mapOf("marker-symbol" to "○")).copy(id = "b"),
          entity(emptyMap()).copy(id = "c"),
          entity(mapOf("status" to "")).copy(id = "d"),
        )
        .toClusterFeatures()
    val cluster =
      MapFeatureCluster(
        id = "cluster",
        normalizedX = 0f,
        normalizedY = 0f,
        features = features,
        symbolGroups = groupClusterFeaturesByMarkerSymbol(features),
      )

    assertEquals(4, cluster.siteCount)
    assertEquals(listOf("✓", "○"), cluster.siteStatusChipGroups.map { it.markerSymbol })
    assertEquals("✓ 1 • ○ 1", cluster.siteStatesSummaryLabel)
    assertEquals("✓ 1 • ○ 1", cluster.balloonSummaryLabel)
    assertEquals(2, cluster.noMarkerSymbolCount)
  }
}
