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
package org.groundplatform.v2.core.library

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FormDef
import groundplatform.v2.library.Aggregation
import groundplatform.v2.library.CodeList
import groundplatform.v2.library.CodeListItem
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.ExportProfileDef
import groundplatform.v2.library.FormTemplateDef
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LibraryStatus
import groundplatform.v2.library.LocalizedText
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.library.Pillar
import groundplatform.v2.library.PrivacyClass
import groundplatform.v2.library.PurposePackDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

class LibraryTextProtoSerializerTest {
  private val commodity =
    ConceptDef(
      id = "eudr.commodity",
      version = 1,
      label = LocalizedText(mapOf("en" to "Commodity", "fr" to "Produit", "vi" to "Mặt hàng")),
      description = LocalizedText(mapOf("en" to "Relevant commodity produced on the plot.")),
      keywords =
        listOf(LocalizedText(mapOf("es" to "cultivo")), LocalizedText(mapOf("en" to "crop"))),
      data_type = DataType.TYPE_SELECT_ONE,
      code_list =
        CodeList(
          items =
            listOf(
              CodeListItem(
                code = "coffee",
                label = LocalizedText(mapOf("en" to "Coffee", "es" to "Café")),
                external_ids = mapOf("hs" to "0901"),
              ),
              CodeListItem(code = "cocoa", label = LocalizedText(mapOf("en" to "Cocoa"))),
            )
        ),
      aggregation = Aggregation.COUNT_BY_CODE,
      privacy_class = PrivacyClass.AGGREGATE_PUBLIC,
      goals = listOf("deforestation_free_supply_chains"),
      pillar = Pillar.MITIGATION,
      references = listOf("https://eur-lex.europa.eu/eli/reg/2023/1115/oj"),
      status = LibraryStatus.STABLE,
      aliases = listOf("org.example.crop"),
    )

  private val form: FormDef =
    XFormsXmlSerializer.deserializeFormDef(
      """
      <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml">
        <h:head>
          <h:title>Plot registration</h:title>
          <model>
            <instance><data id="plot_registration"><commodity/></data></instance>
            <bind nodeset="/data/commodity" type="string"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/commodity"><label>Commodity</label></input>
        </h:body>
      </h:html>
      """
        .trimIndent()
    )

  private val bundle =
    LibraryBundle(
      concepts = listOf(commodity, ConceptDef(id = "core.area_ha", unit = "ha")),
      form_templates =
        listOf(
          FormTemplateDef(
            id = "eudr_plot_registration",
            title = LocalizedText(mapOf("en" to "EUDR plot registration")),
            form = form,
            status = LibraryStatus.STABLE,
          )
        ),
      purpose_packs =
        listOf(
          PurposePackDef(
            id = "eudr_due_diligence",
            title = LocalizedText(mapOf("en" to "EUDR due diligence")),
            icon = "verified",
            form_template_ids = listOf("eudr_plot_registration"),
            export_profile_ids = listOf("eudr_geojson"),
            program_ids = listOf("eudr"),
            goals = listOf("deforestation_free_supply_chains"),
            pillar = Pillar.MITIGATION,
            status = LibraryStatus.STABLE,
          )
        ),
      export_profiles =
        listOf(
          ExportProfileDef(
            id = "eudr_geojson",
            title = LocalizedText(mapOf("en" to "EUDR GeoJSON")),
            format = "geojson",
            field_concepts = mapOf("ProductionPlace" to "eudr.production_place"),
            status = LibraryStatus.STABLE,
          )
        ),
    )

  @Test
  fun bundleRoundTripsThroughTextProto() {
    val text = LibraryTextProtoSerializer.serializeBundle(bundle)
    assertEquals(bundle, LibraryTextProtoSerializer.deserializeBundle(text))
  }

  @Test
  fun serializedBundleUsesEnumNamesAndMapEntries() {
    val text = LibraryTextProtoSerializer.serializeBundle(bundle)
    assertTrue("data_type: TYPE_SELECT_ONE" in text, text)
    assertTrue("aggregation: COUNT_BY_CODE" in text, text)
    assertTrue("key: \"vi\"" in text && "value: \"Mặt hàng\"" in text, text)
  }

  @Test
  fun parsesHandWrittenSeedWithComments() {
    val text =
      """
      # proto-file: shared/protos/library/library_bundle.proto
      # proto-message: groundplatform.v2.library.LibraryBundle
      concepts {
        id: "core.area_ha"
        version: 1
        label { values { key: "en" value: "Area (ha)" } }
        data_type: TYPE_DOUBLE
        unit: "ha"
        aggregation: SUM
        status: STABLE
      }
      """
        .trimIndent()
    val parsed = LibraryTextProtoSerializer.deserializeBundle(text)
    assertEquals(
      ConceptDef(
        id = "core.area_ha",
        version = 1,
        label = LocalizedText(mapOf("en" to "Area (ha)")),
        data_type = DataType.TYPE_DOUBLE,
        unit = "ha",
        aggregation = Aggregation.SUM,
        status = LibraryStatus.STABLE,
      ),
      parsed.concepts.single(),
    )
  }

  @Test
  fun unknownEnumValueFails() {
    assertFailsWith<IllegalArgumentException> {
      LibraryTextProtoSerializer.deserializeBundle("concepts { id: \"x.y\" aggregation: MEDIAN }")
    }
  }

  @Test
  fun settingsRoundTrip() {
    val settings =
      OrganizationLibrarySettings(
        organization_id = "org-example",
        hidden_global_entry_ids = listOf("ferm_restoration", "ferm_monitoring"),
      )
    val text = LibraryTextProtoSerializer.serializeSettings(settings)
    assertEquals(settings, LibraryTextProtoSerializer.deserializeSettings(text))
  }
}
