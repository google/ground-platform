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
package org.groundplatform.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ChoiceListImporterTest {

  @Test
  fun parseChoices_singleColumnText_slugifiesValues() {
    val content =
      """
      Apple
      Banana
      Dragon Fruit
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "fruits.txt")
    assertEquals(3, choices.size)
    assertEquals("apple", choices[0].value)
    assertEquals("Apple", choices[0].label)
    assertEquals("", choices[0].parentValue)

    assertEquals("banana", choices[1].value)
    assertEquals("Banana", choices[1].label)

    assertEquals("dragon_fruit", choices[2].value)
    assertEquals("Dragon Fruit", choices[2].label)
  }

  @Test
  fun parseChoices_twoColumnCsvWithoutHeader_mapsValueAndLabel() {
    val content =
      """
      ny,New York
      ca,California
      tx,Texas
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "states.csv")
    assertEquals(3, choices.size)
    assertEquals("ny", choices[0].value)
    assertEquals("New York", choices[0].label)
    assertEquals("", choices[0].parentValue)

    assertEquals("ca", choices[1].value)
    assertEquals("California", choices[1].label)

    assertEquals("tx", choices[2].value)
    assertEquals("Texas", choices[2].label)
  }

  @Test
  fun parseChoices_twoColumnCsvWithHeader_mapsByHeader() {
    val content =
      """
      value,label
      red,Crimson Red
      green,Emerald Green
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "colors.csv")
    assertEquals(2, choices.size)
    assertEquals("red", choices[0].value)
    assertEquals("Crimson Red", choices[0].label)

    assertEquals("green", choices[1].value)
    assertEquals("Emerald Green", choices[1].label)
  }

  @Test
  fun parseChoices_threeColumnCascadingWithoutHeader_mapsParentValueAndLabel() {
    val content =
      """
      us,ny,New York
      us,ca,California
      ke,nbi,Nairobi
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "provinces.csv")
    assertEquals(3, choices.size)
    assertEquals("us", choices[0].parentValue)
    assertEquals("ny", choices[0].value)
    assertEquals("New York", choices[0].label)

    assertEquals("us", choices[1].parentValue)
    assertEquals("ca", choices[1].value)
    assertEquals("California", choices[1].label)

    assertEquals("ke", choices[2].parentValue)
    assertEquals("nbi", choices[2].value)
    assertEquals("Nairobi", choices[2].label)
  }

  @Test
  fun parseChoices_threeColumnCascadingWithHeader_mapsByHeaderNames() {
    val content =
      """
      parent_value,code,title
      kenya,nyeri,Nyeri County
      kenya,kiambu,Kiambu County
      uganda,kampala,Kampala Central
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "counties.csv")
    assertEquals(3, choices.size)
    assertEquals("kenya", choices[0].parentValue)
    assertEquals("nyeri", choices[0].value)
    assertEquals("Nyeri County", choices[0].label)

    assertEquals("kenya", choices[1].parentValue)
    assertEquals("kiambu", choices[1].value)
    assertEquals("Kiambu County", choices[1].label)

    assertEquals("uganda", choices[2].parentValue)
    assertEquals("kampala", choices[2].value)
    assertEquals("Kampala Central", choices[2].label)
  }

  @Test
  fun parseChoices_handlesQuotedCommasAndEmptyLines() {
    val content =
      """
      # Comment line
      name,label
      "c1","Choice 1, with comma"
      "c2","Choice ""quoted"" label"

      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content)
    assertEquals(2, choices.size)
    assertEquals("c1", choices[0].value)
    assertEquals("Choice 1, with comma", choices[0].label)

    assertEquals("c2", choices[1].value)
    assertEquals("Choice \"quoted\" label", choices[1].label)
  }

  @Test
  fun parseChoices_resolvesDuplicateValuesWithSuffix() {
    val content =
      """
      Red
      Red
      Red
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content)
    assertEquals(3, choices.size)
    assertEquals("red", choices[0].value)
    assertEquals("red_2", choices[1].value)
    assertEquals("red_3", choices[2].value)
  }

  @Test
  fun parseChoices_xlsFormChoicesHeader_ignoresListNameColumn() {
    val content =
      """
      list_name,name,label
      tree_species,oak,Oak Tree
      tree_species,pine,Pine Tree
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "choices.csv")
    assertEquals(2, choices.size)
    assertEquals("oak", choices[0].value)
    assertEquals("Oak Tree", choices[0].label)
    assertEquals("", choices[0].parentValue)

    assertEquals("pine", choices[1].value)
    assertEquals("Pine Tree", choices[1].label)
    assertEquals("", choices[1].parentValue)
  }

  @Test
  fun parseChoices_threeColumnWithoutHeader_constantFirstColumnTreatedAsListName() {
    val content =
      """
      crops,wheat,Wheat
      crops,corn,Corn
      crops,rice,Rice
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "crops.csv")
    assertEquals(3, choices.size)
    assertEquals("wheat", choices[0].value)
    assertEquals("Wheat", choices[0].label)
    assertEquals("", choices[0].parentValue)

    assertEquals("corn", choices[1].value)
    assertEquals("Corn", choices[1].label)
    assertEquals("", choices[1].parentValue)
  }

  @Test
  fun parseChoices_explicitFilterHeader_mapsParentCorrectly() {
    val content =
      """
      filter,name,label
      asia,japan,Japan
      europe,france,France
      """
        .trimIndent()

    val choices = ChoiceListImporter.parseChoices(content, "countries.csv")
    assertEquals(2, choices.size)
    assertEquals("japan", choices[0].value)
    assertEquals("Japan", choices[0].label)
    assertEquals("asia", choices[0].parentValue)

    assertEquals("france", choices[1].value)
    assertEquals("France", choices[1].label)
    assertEquals("europe", choices[1].parentValue)
  }
}
