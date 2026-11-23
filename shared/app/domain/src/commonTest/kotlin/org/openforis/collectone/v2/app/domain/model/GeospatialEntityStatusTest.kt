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
import org.openforis.collectone.v2.map.Geometry
import org.openforis.collectone.v2.map.LatLng

class GeospatialEntityStatusTest {
  private fun entity(properties: Map<String, String>) =
    GeospatialEntityItem(
      id = "e1",
      label = "Plot 1",
      datasetId = "plots",
      datasetName = "Plots",
      layerId = "plots",
      geoId = "",
      geometry = Geometry.Point(LatLng(-0.4198, 36.9512)),
      areaHectares = 0.0,
      perimeterMeters = 0,
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
        .toClusterFeatures(SurveyMapAnchor.forSurvey(""))
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
    assertEquals(
      listOf(ClusterMarkerStatus.COMPLETED, ClusterMarkerStatus.PENDING),
      cluster.siteStatusChipGroups.map { it.status },
    )
    assertEquals(2, cluster.noMarkerSymbolCount)
  }

  @Test
  fun statusFilterOptions_returnsOrderedOptionsWhenStatusIsUsed() {
    val entities =
      listOf(
        entity(mapOf("marker-symbol" to "✓", "status" to "Completed")).copy(id = "e1"),
        entity(mapOf("marker-symbol" to "◐", "status" to "In progress")).copy(id = "e2"),
        entity(mapOf("marker-symbol" to "○", "status" to "Pending")).copy(id = "e3"),
        entity(mapOf("marker-symbol" to "◐", "status" to "In progress")).copy(id = "e4"),
      )

    assertTrue(entities.usesWorkflowStatus)
    val options = entities.statusFilterOptions()
    assertEquals(
      listOf("○ Pending", "◐ In progress", "✓ Completed"),
      options.map { it.badgeText },
    )
    assertEquals(listOf(1, 2, 1), options.map { it.count })
  }

  @Test
  fun statusFilterOptions_returnsEmptyWhenStatusIsNotUsed() {
    val entities =
      listOf(
        entity(mapOf("land_use" to "Cropland")).copy(id = "e1"),
        entity(mapOf("land_use" to "Forest")).copy(id = "e2"),
      )

    assertFalse(entities.usesWorkflowStatus)
    assertTrue(entities.statusFilterOptions().isEmpty())
  }

  @Test
  fun matchesStatusFilter_matchesStatusKeyOrSummaryBadgeCaseInsensitively() {
    val pending = entity(mapOf("marker-symbol" to "○", "status" to "Pending"))
    val noStatus = entity(mapOf("land_use" to "Cropland"))

    assertTrue(pending.matchesStatusFilter(null))
    assertTrue(pending.matchesStatusFilter(""))
    assertTrue(pending.matchesStatusFilter("Pending"))
    assertTrue(pending.matchesStatusFilter("pending"))
    assertTrue(pending.matchesStatusFilter("○ Pending"))
    assertFalse(pending.matchesStatusFilter("Completed"))
    assertTrue(noStatus.matchesStatusFilter(null))
    assertFalse(noStatus.matchesStatusFilter("Pending"))
  }

  @Test
  fun datasetStatusFilterOptions_mergesConfiguredOptionsAndHidesHomogeneousSingleStatus() {
    val completedOnly = listOf(entity(mapOf("status" to "Completed", "marker-symbol" to "✓")))
    assertTrue(completedOnly.datasetStatusFilterOptions().isEmpty())
    assertEquals(
      listOf("○ Pending", "✓ Completed"),
      completedOnly.datasetStatusFilterOptions(selectedStatus = "Pending").map { it.badgeText },
    )

    val configured =
      listOf(
        EntityStatusFilterOption(status = "Pending", symbol = "○"),
        EntityStatusFilterOption(status = "Completed", symbol = "✓"),
      )
    assertEquals(
      listOf("○ Pending", "✓ Completed"),
      completedOnly.datasetStatusFilterOptions(configuredOptions = configured).map { it.badgeText },
    )

    val mixedWithUnset =
      listOf(
        entity(mapOf("status" to "Completed", "marker-symbol" to "✓")),
        entity(emptyMap()),
      )
    assertEquals(
      listOf("✓ Completed"),
      mixedWithUnset.datasetStatusFilterOptions().map { it.badgeText },
    )
  }
}
