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
package org.openforis.collectone.v2.web.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnPrefixGroupingTest {

  @Test
  fun leadingPrefixToken_extractsSupportedDelimitersOnlyWithTrailingName() {
    assertEquals("whisp_", leadingPrefixToken("whisp_Area"))
    assertEquals("s2.", leadingPrefixToken("s2.b04"))
    assertEquals("eudr-", leadingPrefixToken("eudr-risk"))
    assertEquals("geo:", leadingPrefixToken("geo:area"))
    assertNull(leadingPrefixToken("stratum"))
    assertNull(leadingPrefixToken("whisp_"))
    assertNull(leadingPrefixToken("_private"))
    assertNull(leadingPrefixToken("123_foo"))
    assertNull(leadingPrefixToken(""))
  }

  @Test
  fun smallRelatedGroupsBelowThreshold_stayUngrouped() {
    val cols =
      listOf(
        "plot_id",
        "stratum",
        "sample_order",
        "sample_ids",
        "forest_change_class",
        "forest_stratum",
        "disturbance_year",
        "disturbance_driver",
      )
    val layout = groupColumnsByCommonPrefix(cols, keyOf = { it })
    assertTrue(layout.groups.isEmpty())
    assertEquals(cols.size, layout.displaySlots.size)
    assertTrue(layout.displaySlots.all { it is GroupedTableColumn.Single && it.groupId == null })
  }

  @Test
  fun whispColumns_collapseByDefault_andExpandInPlaceWhenSelected() {
    val whispCols = (1..230).map { "whisp_metric_$it" }
    val cols = listOf("plot_id", "stratum", "sample_order", "inclusion_weight") + whispCols

    val collapsed = groupColumnsByCommonPrefix(cols, keyOf = { it })
    assertEquals(1, collapsed.groups.size)
    val group = collapsed.groups.single()
    assertEquals("whisp_", group.id)
    assertEquals("whisp_metric_*", group.wildcardLabel)
    assertEquals(230, group.size)
    // 4 regular columns + 1 collapsed placeholder slot = 5 slots total.
    assertEquals(5, collapsed.displaySlots.size)
    val placeholder = assertIs<GroupedTableColumn.CollapsedGroup<String>>(collapsed.displaySlots[4])
    assertEquals(230, placeholder.group.size)
    assertEquals(4, collapsed.collapsedSlotIndex("whisp_"))
    assertEquals(-1, collapsed.firstExpandedSlotIndex("whisp_"))

    val expanded =
      groupColumnsByCommonPrefix(cols, keyOf = { it }, expandedGroupIds = setOf("whisp_"))
    assertEquals(234, expanded.displaySlots.size)
    assertEquals(4, expanded.firstExpandedSlotIndex("whisp_"))
    val firstMember = assertIs<GroupedTableColumn.Single<String>>(expanded.displaySlots[4])
    assertEquals("whisp_metric_1", firstMember.item)
    assertEquals("whisp_", firstMember.groupId)
    assertTrue(firstMember.isGroupStart)
    assertEquals(230, firstMember.groupSize)
    val secondMember = assertIs<GroupedTableColumn.Single<String>>(expanded.displaySlots[5])
    assertEquals(false, secondMember.isGroupStart)
  }

  @Test
  fun mixedWhispColumns_refinePrefixToWhispUnderscore() {
    val cols =
      listOf(
        "plot_id",
        "whisp_plotId",
        "whisp_Area",
        "whisp_Country",
        "whisp_TMF_def_2000",
        "whisp_TMF_def_2001",
        "whisp_risk_timber",
      )
    val layout = groupColumnsByCommonPrefix(cols, keyOf = { it })
    assertEquals(1, layout.groups.size)
    assertEquals("whisp_", layout.groups[0].prefix)
    assertEquals("whisp_*", layout.groups[0].wildcardLabel)
    assertEquals(6, layout.groups[0].size)
    assertEquals(2, layout.displaySlots.size)
  }

  @Test
  fun multipleAndInterleavedPrefixGroups_emitEachOnceInOriginalOrder() {
    val cols =
      listOf(
        "plot_id",
        "whisp_a",
        "whisp_b",
        "notes",
        "ee_ndvi_2020",
        "ee_ndvi_2021",
        "ee_ndvi_2022",
        "ee_ndvi_2023",
        "ee_ndvi_2024",
        "ee_ndvi_2025",
        "whisp_c",
        "whisp_d",
        "whisp_e",
        "whisp_f",
      )
    val layout = groupColumnsByCommonPrefix(cols, keyOf = { it }, expandedGroupIds = setOf("ee_"))
    assertEquals(listOf("whisp_*", "ee_ndvi_*"), layout.groups.map { it.wildcardLabel })
    // plot_id, [whisp_*], notes, ee_ndvi_2020..2025 (6 slots)
    assertEquals(9, layout.displaySlots.size)
    assertIs<GroupedTableColumn.CollapsedGroup<String>>(layout.displaySlots[1])
    assertEquals("notes", assertIs<GroupedTableColumn.Single<String>>(layout.displaySlots[2]).item)
    assertEquals(
      "ee_ndvi_2020",
      assertIs<GroupedTableColumn.Single<String>>(layout.displaySlots[3]).item,
    )
  }

  @Test
  fun protectedColumns_neverCollapseIntoPrefixGroup() {
    val cols = (1..7).map { "whisp_f$it" }
    val layout =
      groupColumnsByCommonPrefix(
        cols,
        keyOf = { it },
        isGroupable = { it != "whisp_f1" },
      )
    assertEquals(1, layout.groups.size)
    assertEquals(6, layout.groups[0].size)
    assertEquals(2, layout.displaySlots.size)
    assertEquals(
      "whisp_f1",
      assertIs<GroupedTableColumn.Single<String>>(layout.displaySlots[0]).item,
    )
  }
}
