/*
 * Copyright 2026 The Ground Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.ui

import org.groundplatform.v2.core.forms.model.FormDefinition
import org.groundplatform.v2.core.forms.model.FormRecordInstance
import org.groundplatform.v2.core.forms.serialization.XFormsXmlSerializer

/**
 * Catalog of swappable workbench example forms for the Collect One mobile workbench.
 *
 * Covers the five core field data collection and entity-centric workflows:
 * 1. [SINGLE_POINT_LAND_USE]: Simple single-point collection (pan allowed, <=10m GPS accuracy) +
 *    land use classification.
 * 2. [SAMPLE_PLOTS_FOREST_ASSESSMENT]: Predefined sample plot entities (`sample_plots`) + forest
 *    assessment (entity selection, canopy photo, basal area, canopy cover, regeneration).
 * 3. [COMMODITY_PERIMETER_AND_CENTER]: Forest-risk commodity plot perimeter (`geoshape` with GPS
 *    override / pan allowed while walking) + plot center (`geopoint` with no pan allowed and strict
 *    <=5m GPS accuracy).
 * 4. [HOUSEHOLD_SURVEY_PAST_INDIVIDUALS]: Household longitudinal survey preloaded with an entity
 *    list of past individuals (`past_individuals`) for respondent lookup and roster reconciliation.
 * 5. [ALL_FIELD_TYPES]: Comprehensive showcase of all XForms / ProtoForms field types (`string`,
 *    `multiline`, `int`, `decimal`, `boolean`, `date`, `time`, `dateTime`, `select1`, `likert`,
 *    `select`, `range`, `rank`, `geopoint`, `geotrace`, `geoshape`, photo / video / audio `upload`,
 *    `trigger`, `calculate`, `field-list` group, and `repeat`).
 */
enum class WorkbenchExampleForm(
  val id: String,
  val shortLabel: String,
  val title: String,
  val subtitle: String,
  val badgeText: String,
  val requiresEntity: Boolean,
  val xformsXml: String,
  val sampleInstanceXml: String,
) {
  SINGLE_POINT_LAND_USE(
    id = "single_point_land_use",
    shortLabel = "1. Single Point + Land Use",
    title = "Simple Point & Land Use Observation",
    subtitle =
      "Single geopoint (pan allowed via placement-map, <=10m GPS accuracy required) + IPCC land use.",
    badgeText = "Pan Allowed • <=10m GPS",
    requiresEntity = false,
    xformsXml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa"
              xmlns:orx="http://openrosa.org/xforms"
              xmlns:entities="http://www.opendatakit.org/xforms/entities">
        <h:head>
          <h:title>Simple Point &amp; Land Use Observation</h:title>
          <model orx:xforms-version="1.0.0" entities:entities-version="2024.1.0">
            <instance>
              <data id="single_point_land_use" version="2026092401">
                <sample_point>-1.292066 36.821946 1680.0 6.4</sample_point>
                <land_use>agroforestry_shade</land_use>
                <observer_note/>
                <orx:meta>
                  <orx:instanceID/>
                  <entities:entity dataset="land_use_observations" create="1">
                    <entities:label calculate="/data/land_use"/>
                  </entities:entity>
                </orx:meta>
              </data>
            </instance>
            <instance id="land_use">
              <root>
                <item>
                  <itextId>land_use-0</itextId>
                  <name>primary_forest</name>
                  <color>#1B5E20</color>
                </item>
                <item>
                  <itextId>land_use-1</itextId>
                  <name>secondary_forest</name>
                  <color>#388E3C</color>
                </item>
                <item>
                  <itextId>land_use-2</itextId>
                  <name>agroforestry_shade</name>
                  <color>#689F38</color>
                </item>
                <item>
                  <itextId>land_use-3</itextId>
                  <name>annual_cropland</name>
                  <color>#FBC02D</color>
                </item>
                <item>
                  <itextId>land_use-4</itextId>
                  <name>pasture_grassland</name>
                  <color>#AED581</color>
                </item>
                <item>
                  <itextId>land_use-5</itextId>
                  <name>wetland_riparian</name>
                  <color>#0288D1</color>
                </item>
                <item>
                  <itextId>land_use-6</itextId>
                  <name>settlement</name>
                  <color>#E64A19</color>
                </item>
              </root>
            </instance>
            <itext>
              <translation lang="English (en)" default="true()">
                <text id="/data/sample_point:label">
                  <value>Observation Point Location</value>
                </text>
                <text id="/data/sample_point:hint">
                  <value>Pan map crosshair to adjust position, or capture via GPS (requires &lt;= 10m accuracy when using GPS).</value>
                </text>
                <text id="/data/land_use:label">
                  <value>Primary Land Use Classification</value>
                </text>
                <text id="/data/land_use:hint">
                  <value>Select the dominant land cover / land use within a 20m radius of the point.</value>
                </text>
                <text id="land_use-0">
                  <value>Primary / Intact Natural Forest</value>
                </text>
                <text id="land_use-1">
                  <value>Secondary / Regenerating Forest</value>
                </text>
                <text id="land_use-2">
                  <value>Agroforestry / Shade-Grown Tree Crop</value>
                </text>
                <text id="land_use-3">
                  <value>Annual Cropland / Cultivated Agriculture</value>
                </text>
                <text id="land_use-4">
                  <value>Grazing Pasture / Grassland</value>
                </text>
                <text id="land_use-5">
                  <value>Wetland / Riparian Buffer</value>
                </text>
                <text id="land_use-6">
                  <value>Built-Up / Rural Settlement</value>
                </text>
                <text id="/data/observer_note:label">
                  <value>Brief Field Note (Optional)</value>
                </text>
              </translation>
            </itext>
            <bind nodeset="/data/sample_point"
                  type="geopoint"
                  required="true()"
                  accuracyThreshold="10.0"
                  constraint="string-length(.) = 0 or number(selected-at(., 3)) &lt;= 10.0"
                  jr:constraintMsg="GPS accuracy must be 10.0m or better when capturing GPS coordinates."
                  entities:saveto="geometry"/>
            <bind nodeset="/data/land_use"
                  type="select1"
                  required="true()"
                  entities:saveto="land_use"/>
            <bind nodeset="/data/observer_note"
                  type="string"
                  entities:saveto="observer_note"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/sample_point" appearance="placement-map" accuracyThreshold="10.0">
            <label ref="jr:itext('/data/sample_point:label')"/>
            <hint ref="jr:itext('/data/sample_point:hint')"/>
          </input>
          <select1 ref="/data/land_use">
            <label ref="jr:itext('/data/land_use:label')"/>
            <hint ref="jr:itext('/data/land_use:hint')"/>
            <itemset nodeset="instance('land_use')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select1>
          <input ref="/data/observer_note">
            <label ref="jr:itext('/data/observer_note:label')"/>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent(),
    sampleInstanceXml =
      """
      <data id="single_point_land_use">
        <sample_point>-1.292066 36.821946 1680.0 6.4</sample_point>
        <land_use>agroforestry_shade</land_use>
        <observer_note>Mature Grevillea and Markhamia shade trees bordering terraced coffee plot.</observer_note>
      </data>
      """
        .trimIndent(),
  ),
  SAMPLE_PLOTS_FOREST_ASSESSMENT(
    id = "sample_plots_forest_assessment",
    shortLabel = "2. Sample Plots & Forest Assessment",
    title = "Sample Plot Entity & Forest Stand Assessment",
    subtitle =
      "Selects from predefined sample plot entities (`sample_plots`), captures canopy photo, canopy cover, basal area, and disturbances.",
    badgeText = "Predefined Plot Entities + Photo",
    requiresEntity = true,
    xformsXml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa"
              xmlns:orx="http://openrosa.org/xforms"
              xmlns:entities="http://www.opendatakit.org/xforms/entities">
        <h:head>
          <h:title>Sample Plot Entity &amp; Forest Stand Assessment</h:title>
          <model orx:xforms-version="1.0.0" entities:entities-version="2024.1.0">
            <instance>
              <data id="sample_plots_forest_assessment" version="2026092401">
                <sample_plot_entity>00000016-cc1f-5125-8841-166ab5b5f68f</sample_plot_entity>
                <selected_plot_stratum/>
                <selected_plot_elevation/>
                <canopy_photo>hemispherical_canopy_sp01.jpg</canopy_photo>
                <canopy_stratum>canopy</canopy_stratum>
                <canopy_cover_pct>75</canopy_cover_pct>
                <dominant_species>Cordia africana</dominant_species>
                <stand_basal_area_m2_ha>24.5</stand_basal_area_m2_ha>
                <regenerating_saplings_count>18</regenerating_saplings_count>
                <disturbance_indicators>none</disturbance_indicators>
                <assessment_notes>Closed upper canopy with dense natural regeneration.</assessment_notes>
                <orx:meta>
                  <orx:instanceID/>
                  <entities:entity dataset="sample_plots" id="" update="1" baseVersion=""/>
                </orx:meta>
              </data>
            </instance>
            <instance id="sample_plots" src="jr://file-csv/sample_plots.csv">
              <root>
                <item>
                  <name>00000016-cc1f-5125-8841-166ab5b5f68f</name>
                  <label>Plot SP-01 • Upper Montane Buffer (-1.2889, 36.8194)</label>
                  <stratum>Montane Moist Indigenous Forest</stratum>
                  <elevation_m>1840m</elevation_m>
                </item>
                <item>
                  <name>00000017-d1a0-51d3-a686-e6ca450c23b2</name>
                  <label>Plot SP-02 • Riparian Gallery Transect (-1.2934, 36.8248)</label>
                  <stratum>Riparian Corridor Restoration</stratum>
                  <elevation_m>1715m</elevation_m>
                </item>
                <item>
                  <name>00000018-4d16-5ab7-a57d-7dae5949cb7a</name>
                  <label>Plot SP-03 • Shade Agroforestry Core (-1.2961, 36.8172)</label>
                  <stratum>Multi-Strata Shade Coffee</stratum>
                  <elevation_m>1690m</elevation_m>
                </item>
                <item>
                  <name>00000019-882d-5830-a254-1fe5abf727e3</name>
                  <label>Plot SP-04 • Community Forest Edge (-1.2855, 36.8271)</label>
                  <stratum>Secondary Enrichment Planting</stratum>
                  <elevation_m>1795m</elevation_m>
                </item>
                <item>
                  <name>0000001a-f05a-503e-9697-12c420a33cdb</name>
                  <label>Plot SP-05 • Ridge Benchmark Control (-1.2908, 36.8130)</label>
                  <stratum>Intact Reference Stand</stratum>
                  <elevation_m>1910m</elevation_m>
                </item>
              </root>
            </instance>
            <instance id="disturbance_indicators">
              <root>
                <item>
                  <name>none</name>
                  <label>None — Undisturbed Stand</label>
                  <color>#2E7D32</color>
                </item>
                <item>
                  <name>selective_logging</name>
                  <label>Selective Pole / Timber Cutting</label>
                  <color>#8D6E63</color>
                </item>
                <item>
                  <name>fire_charcoal</name>
                  <label>Charcoal Kiln / Fire Scars</label>
                  <color>#D84315</color>
                </item>
                <item>
                  <name>livestock_grazing</name>
                  <label>Livestock Browsing / Trampling</label>
                  <color>#F9A825</color>
                </item>
                <item>
                  <name>invasive_species</name>
                  <label>Invasive Understory Shrubs (e.g., Lantana)</label>
                  <color>#7B1FA2</color>
                </item>
              </root>
            </instance>
            <instance id="canopy_stratum">
              <root>
                <item>
                  <itextId>canopy_stratum-0</itextId>
                  <name>emergent</name>
                </item>
                <item>
                  <itextId>canopy_stratum-1</itextId>
                  <name>canopy</name>
                </item>
                <item>
                  <itextId>canopy_stratum-2</itextId>
                  <name>understory</name>
                </item>
                <item>
                  <itextId>canopy_stratum-3</itextId>
                  <name>ground_cover</name>
                </item>
              </root>
            </instance>
            <itext>
              <translation lang="default" default="true()">
                <text id="canopy_stratum-0">
                  <value>Emergent Tree Canopy (&gt; 30m)</value>
                  <value form="image">jr://images/canopy_stratum-emergent.png</value>
                </text>
                <text id="canopy_stratum-1">
                  <value>Dominant Main Canopy (15-30m)</value>
                  <value form="image">jr://images/canopy_stratum-canopy.png</value>
                </text>
                <text id="canopy_stratum-2">
                  <value>Sub-Canopy / Understory (5-15m)</value>
                  <value form="image">jr://images/canopy_stratum-understory.png</value>
                </text>
                <text id="canopy_stratum-3">
                  <value>Ground Flora / Shrub Layer (&lt; 5m)</value>
                  <value form="image">jr://images/canopy_stratum-ground_cover.png</value>
                </text>
              </translation>
            </itext>
            <bind nodeset="/data/sample_plot_entity" type="select1" required="true()"/>
            <bind nodeset="/data/canopy_stratum" type="select1" required="true()"/>
            <bind nodeset="/data/selected_plot_stratum"
                  type="string"
                  readonly="true()"
                  calculate="instance('sample_plots')/root/item[name = /data/sample_plot_entity]/stratum"/>
            <bind nodeset="/data/selected_plot_elevation"
                  type="string"
                  readonly="true()"
                  calculate="instance('sample_plots')/root/item[name = /data/sample_plot_entity]/elevation_m"/>
            <bind nodeset="/data/canopy_photo" type="binary" required="true()"/>
            <bind nodeset="/data/canopy_cover_pct"
                  type="int"
                  required="true()"
                  constraint=". &gt;= 0 and . &lt;= 100"
                  jr:constraintMsg="Canopy cover must be between 0% and 100%."
                  entities:saveto="canopy_cover_pct"/>
            <bind nodeset="/data/dominant_species"
                  type="string"
                  required="true()"
                  entities:saveto="dominant_species"/>
            <bind nodeset="/data/stand_basal_area_m2_ha"
                  type="decimal"
                  required="true()"
                  constraint=". &gt;= 0 and . &lt;= 120"
                  jr:constraintMsg="Basal area must be between 0 and 120 m2/ha."/>
            <bind nodeset="/data/regenerating_saplings_count"
                  type="int"
                  required="true()"
                  constraint=". &gt;= 0"/>
            <bind nodeset="/data/disturbance_indicators" type="select"/>
            <bind nodeset="/data/assessment_notes" type="string"/>
            <bind nodeset="/data/orx:meta/entities:entity/@id" calculate="/data/sample_plot_entity"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/sample_plot_entity">
            <label>Select Predefined Sample Plot Entity</label>
            <hint>Choose a permanent monitoring plot from the preloaded `sample_plots` entity dataset.</hint>
            <itemset nodeset="instance('sample_plots')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <input ref="/data/selected_plot_stratum">
            <label>Preloaded Plot Forest Stratum (Entity Lookup)</label>
            <hint>Auto-populated from the selected `sample_plots` entity.</hint>
          </input>
          <input ref="/data/selected_plot_elevation">
            <label>Preloaded Plot Benchmark Elevation</label>
          </input>
          <upload ref="/data/canopy_photo" mediatype="image/*">
            <label>North-Facing Hemispherical Canopy Photo</label>
            <hint>Stand at the sample plot center stake and capture an upward canopy photo.</hint>
          </upload>
          <select1 ref="/data/canopy_stratum">
            <label>Dominant Overstory Canopy Stratum</label>
            <hint>Identify the vertical forest layer with the highest crown density.</hint>
            <itemset nodeset="instance('canopy_stratum')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select1>
          <range ref="/data/canopy_cover_pct" start="0" end="100" step="5">
            <label>Measured Canopy Closure (%)</label>
            <hint>Estimate crown closure using spherical densiometer reading (0% to 100%).</hint>
          </range>
          <input ref="/data/dominant_species">
            <label>Dominant Overstory Tree Species</label>
            <hint>Record scientific or local name of the dominant canopy species in the 20m radius plot.</hint>
          </input>
          <input ref="/data/stand_basal_area_m2_ha">
            <label>Stand Basal Area (m²/ha)</label>
            <hint>Bitterlich wedge prism or DBH tally conversion across stems &gt;= 10cm DBH.</hint>
          </input>
          <input ref="/data/regenerating_saplings_count">
            <label>Natural Regeneration Count (Saplings &gt; 50cm height)</label>
            <hint>Count surviving indigenous tree saplings inside the 5m regeneration subplot.</hint>
          </input>
          <select ref="/data/disturbance_indicators">
            <label>Observed Forest Disturbance Signs</label>
            <hint>Select all disturbance indicators observed within the sample plot boundary.</hint>
            <itemset nodeset="instance('disturbance_indicators')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select>
          <input ref="/data/assessment_notes" appearance="multiline">
            <label>Silvicultural &amp; Biodiversity Notes</label>
            <hint>Document epiphytes, deadwood snags, or boundary maintenance needs.</hint>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent(),
    sampleInstanceXml =
      """
      <data id="sample_plots_forest_assessment">
        <sample_plot_entity>00000016-cc1f-5125-8841-166ab5b5f68f</sample_plot_entity>
        <canopy_photo>hemispherical_canopy_sp01.jpg</canopy_photo>
        <canopy_stratum>canopy</canopy_stratum>
        <canopy_cover_pct>75</canopy_cover_pct>
        <dominant_species>Cordia africana</dominant_species>
        <stand_basal_area_m2_ha>24.5</stand_basal_area_m2_ha>
        <regenerating_saplings_count>18</regenerating_saplings_count>
        <disturbance_indicators>none</disturbance_indicators>
        <assessment_notes>Closed upper canopy with dense natural regeneration.</assessment_notes>
      </data>
      """
        .trimIndent(),
  ),
  COMMODITY_PERIMETER_AND_CENTER(
    id = "commodity_perimeter_and_center",
    shortLabel = "3. Commodity Perimeter + Center",
    title = "Forest-Risk Commodity Plot Perimeter & Center Mapping",
    subtitle =
      "Walk perimeter (`geoshape` with GPS override / pan allowed) + capture plot center (`geopoint` with NO pan allowed & <=5m GPS accuracy).",
    badgeText = "Perimeter (Pan OK) + Center (No Pan, <=5m)",
    requiresEntity = false,
    xformsXml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa"
              xmlns:orx="http://openrosa.org/xforms"
              xmlns:entities="http://www.opendatakit.org/xforms/entities">
        <h:head>
          <h:title>Forest-Risk Commodity Plot Perimeter &amp; Center Mapping</h:title>
          <model orx:xforms-version="1.0.0" entities:entities-version="2024.1.0">
            <instance>
              <data id="commodity_perimeter_and_center" version="2026092401">
                <commodity_type>cocoa</commodity_type>
                <soil_moisture_class>moist_well_drained</soil_moisture_class>
                <producer_farm_id>EUDR-GH-2026-0419</producer_farm_id>
                <plot_perimeter>-1.2921 36.8219 1680 3.5; -1.2925 36.8224 1681 3.8; -1.2918 36.8228 1682 3.2; -1.2915 36.8221 1680 3.4; -1.2921 36.8219 1680 3.5</plot_perimeter>
                <plot_center_point>-1.29201 36.82228 1681.0 3.1</plot_center_point>
                <planting_year>2016</planting_year>
                <eudr_deforestation_free_attestation>true</eudr_deforestation_free_attestation>
                <mapper_remarks/>
                <orx:meta>
                  <orx:instanceID/>
                  <entities:entity dataset="commodity_plots" create="1">
                    <entities:label calculate="concat(/data/commodity_type, ' • ', /data/producer_farm_id)"/>
                  </entities:entity>
                </orx:meta>
              </data>
            </instance>
            <instance id="commodity_type">
              <root>
                <item>
                  <itextId>commodity_type-0</itextId>
                  <name>cocoa</name>
                </item>
                <item>
                  <itextId>commodity_type-1</itextId>
                  <name>coffee</name>
                </item>
                <item>
                  <itextId>commodity_type-2</itextId>
                  <name>oil_palm</name>
                </item>
                <item>
                  <itextId>commodity_type-3</itextId>
                  <name>rubber</name>
                </item>
                <item>
                  <itextId>commodity_type-4</itextId>
                  <name>soy</name>
                </item>
                <item>
                  <itextId>commodity_type-5</itextId>
                  <name>cattle</name>
                </item>
                <item>
                  <itextId>commodity_type-6</itextId>
                  <name>timber</name>
                </item>
              </root>
            </instance>
            <instance id="soil_moisture_class">
              <root>
                <item>
                  <name>dry</name>
                  <label>Dry / Well Drained</label>
                  <color>#D84315</color>
                </item>
                <item>
                  <name>moist_well_drained</name>
                  <label>Moist Organic Loam</label>
                  <color>#2E7D32</color>
                </item>
                <item>
                  <name>saturated</name>
                  <label>Waterlogged / Hydric</label>
                  <color>#0277BD</color>
                </item>
              </root>
            </instance>
            <itext>
              <translation lang="default" default="true()">
                <text id="commodity_type-0">
                  <value>Cocoa (Theobroma cacao)</value>
                  <value form="image">jr://images/commodity_type-cocoa.png</value>
                </text>
                <text id="commodity_type-1">
                  <value>Coffee (Coffea arabica / canephora)</value>
                  <value form="image">jr://images/commodity_type-coffee.png</value>
                </text>
                <text id="commodity_type-2">
                  <value>Oil Palm (Elaeis guineensis)</value>
                  <value form="image">jr://images/commodity_type-oil_palm.png</value>
                </text>
                <text id="commodity_type-3">
                  <value>Natural Rubber (Hevea brasiliensis)</value>
                  <value form="image">jr://images/commodity_type-rubber.png</value>
                </text>
                <text id="commodity_type-4">
                  <value>Soy (Glycine max)</value>
                  <value form="image">jr://images/commodity_type-soy.png</value>
                </text>
                <text id="commodity_type-5">
                  <value>Cattle / Grazing Pasture</value>
                  <value form="image">jr://images/commodity_type-cattle.png</value>
                </text>
                <text id="commodity_type-6">
                  <value>Timber / Wood Products</value>
                  <value form="image">jr://images/commodity_type-timber.png</value>
                </text>
              </translation>
            </itext>
            <bind nodeset="/data/commodity_type"
                  type="select1"
                  required="true()"
                  entities:saveto="commodity_type"/>
            <bind nodeset="/data/soil_moisture_class" type="select1" required="true()"/>
            <bind nodeset="/data/producer_farm_id"
                  type="string"
                  required="true()"
                  entities:saveto="producer_farm_id"/>
            <bind nodeset="/data/plot_perimeter"
                  type="geoshape"
                  required="true()"
                  accuracyThreshold="10.0"
                  entities:saveto="geometry"/>
            <bind nodeset="/data/plot_center_point"
                  type="geopoint"
                  required="true()"
                  accuracyThreshold="5.0"
                  constraint="string-length(.) = 0 or number(selected-at(., 3)) &lt;= 5.0"
                  jr:constraintMsg="Plot center requires strict hardware GPS accuracy of 5.0m or better (manual panning is disabled)."
                  entities:saveto="plot_center_point"/>
            <bind nodeset="/data/planting_year"
                  type="int"
                  required="true()"
                  constraint=". &gt;= 1950 and . &lt;= 2026"
                  jr:constraintMsg="Planting year must be between 1950 and 2026."
                  entities:saveto="planting_year"/>
            <bind nodeset="/data/eudr_deforestation_free_attestation"
                  type="boolean"
                  required="true()"
                  entities:saveto="eudr_deforestation_free_attestation"/>
            <bind nodeset="/data/mapper_remarks"
                  type="string"
                  entities:saveto="mapper_remarks"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/commodity_type">
            <label>Forest-Risk Commodity Crop</label>
            <hint>Select the regulated forest-risk commodity produced on this plot (EUDR Annex I).</hint>
            <itemset nodeset="instance('commodity_type')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select1>
          <select1 ref="/data/soil_moisture_class">
            <label>Soil Moisture &amp; Drainage Class</label>
            <hint>Verify hydric / drainage conditions across the planting area.</hint>
            <itemset nodeset="instance('soil_moisture_class')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <input ref="/data/producer_farm_id">
            <label>Producer Cooperative / Parcel Registry ID</label>
            <hint>Enter the national traceability ID or cooperative member code.</hint>
          </input>
          <input ref="/data/plot_perimeter" appearance="walk-or-draw placement-map" accuracyThreshold="10.0">
            <label>Commodity Plot Perimeter Polygon (Walk + GPS Override / Pan Allowed)</label>
            <hint>Walk the perimeter to log GPS vertices. When dense canopy or ditches obstruct walking, pan the map crosshair (`placement-map`) to override/drop corner vertices.</hint>
          </input>
          <input ref="/data/plot_center_point" accuracyThreshold="5.0">
            <label>Commodity Plot Center Point (Hardware GPS Only • No Pan • &lt;= 5m Accuracy)</label>
            <hint>Stand at the physical centroid of the commodity plot. Map panning is disabled; hardware GNSS fix must achieve &lt;= 5.0m horizontal accuracy.</hint>
          </input>
          <input ref="/data/planting_year">
            <label>Year Crop Was Established on Plot</label>
            <hint>Verify whether establishment predates the December 31, 2020 EUDR cutoff date.</hint>
          </input>
          <input ref="/data/eudr_deforestation_free_attestation">
            <label>Attest No Forest Conversion After Dec 31, 2020</label>
            <hint>Confirm visual and satellite baseline verification for EUDR due diligence.</hint>
          </input>
          <input ref="/data/mapper_remarks" appearance="multiline">
            <label>Boundary &amp; Buffer Remarks (Optional)</label>
            <hint>Note any adjacent riparian buffers, shared boundary markers, or canopy obstructions.</hint>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent(),
    sampleInstanceXml =
      """
      <data id="commodity_perimeter_and_center">
        <commodity_type>cocoa</commodity_type>
        <soil_moisture_class>moist_well_drained</soil_moisture_class>
        <producer_farm_id>EUDR-GH-2026-0419</producer_farm_id>
        <plot_perimeter>-1.2921 36.8219 1680 3.5; -1.2925 36.8224 1681 3.8; -1.2918 36.8228 1682 3.2; -1.2915 36.8221 1680 3.4; -1.2921 36.8219 1680 3.5</plot_perimeter>
        <plot_center_point>-1.29201 36.82228 1681.0 3.1</plot_center_point>
        <planting_year>2016</planting_year>
        <eudr_deforestation_free_attestation>true</eudr_deforestation_free_attestation>
      </data>
      """
        .trimIndent(),
  ),
  HOUSEHOLD_SURVEY_PAST_INDIVIDUALS(
    id = "household_survey_past_individuals",
    shortLabel = "4. Household Survey (Past Individuals)",
    title = "Household Panel Survey (Preloaded Past Individuals)",
    subtitle =
      "Uses a preloaded secondary instance (`past_individuals`) of panel household members for respondent selection and roster follow-up.",
    badgeText = "Preloaded Individual Entities",
    requiresEntity = false,
    xformsXml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa"
              xmlns:orx="http://openrosa.org/xforms">
        <h:head>
          <h:title>Household Panel Survey (Preloaded Past Individuals)</h:title>
          <model orx:xforms-version="1.0.0">
            <instance>
              <data id="household_survey_past_individuals" version="2026092401">
                <primary_respondent_id>0000000c-a9ae-59f6-ac51-7f5a8777598f</primary_respondent_id>
                <preloaded_household_code/>
                <preloaded_baseline_role/>
                <preloaded_baseline_year/>
                <returning_individuals_present>0000000c-a9ae-59f6-ac51-7f5a8777598f 0000000d-2972-591c-b23d-4918d3e3b7b1 0000000f-23a2-50ad-b0e8-98639587eba4</returning_individuals_present>
                <respondent_residency_status>resident_full_time</respondent_residency_status>
                <current_household_size>5</current_household_size>
                <new_individuals_joined_count>1</new_individuals_joined_count>
                <primary_livelihood_sources>agroforestry_cocoa beekeeping</primary_livelihood_sources>
                <clean_cooking_energy>improved_cookstove</clean_cooking_energy>
                <enumerator_notes/>
                <orx:meta>
                  <orx:instanceID/>
                </orx:meta>
              </data>
            </instance>
            <instance id="past_individuals" src="jr://file-csv/past_individuals.csv">
              <root>
                <item>
                  <name>0000000c-a9ae-59f6-ac51-7f5a8777598f</name>
                  <label>Amina Wanjiku (ID: IND-101 • Age 44 • HH-KAK-014)</label>
                  <household_code>HH-KAK-014 (Kakamega East)</household_code>
                  <baseline_role>Household Head &amp; Lead Agroforester</baseline_role>
                  <baseline_year>2023 Wave 1</baseline_year>
                </item>
                <item>
                  <name>0000000d-2972-591c-b23d-4918d3e3b7b1</name>
                  <label>Samuel Ochieng (ID: IND-102 • Age 47 • HH-KAK-014)</label>
                  <household_code>HH-KAK-014 (Kakamega East)</household_code>
                  <baseline_role>Spouse / Co-Manager</baseline_role>
                  <baseline_year>2023 Wave 1</baseline_year>
                </item>
                <item>
                  <name>0000000e-95ee-5695-ad0b-54576e8d7785</name>
                  <label>Grace Atieno (ID: IND-103 • Age 29 • HH-KAK-022)</label>
                  <household_code>HH-KAK-022 (Shinyalu Ridge)</household_code>
                  <baseline_role>Primary Respondent / Nursery Operator</baseline_role>
                  <baseline_year>2023 Wave 1</baseline_year>
                </item>
                <item>
                  <name>0000000f-23a2-50ad-b0e8-98639587eba4</name>
                  <label>David Barasa (ID: IND-104 • Age 21 • HH-KAK-014)</label>
                  <household_code>HH-KAK-014 (Kakamega East)</household_code>
                  <baseline_role>Adult Child / Pruning Crew</baseline_role>
                  <baseline_year>2024 Wave 2</baseline_year>
                </item>
                <item>
                  <name>00000010-1195-5b30-ab83-7939d81cd71f</name>
                  <label>Esther Nekesa (ID: IND-105 • Age 61 • HH-KAK-031)</label>
                  <household_code>HH-KAK-031 (Ikolomani South)</household_code>
                  <baseline_role>Elder / Seed Guardian</baseline_role>
                  <baseline_year>2022 Baseline</baseline_year>
                </item>
                <item>
                  <name>00000011-d6fa-5a2d-8848-5419c62a0359</name>
                  <label>Peter Wafula (ID: IND-106 • Age 35 • HH-KAK-039)</label>
                  <household_code>HH-KAK-039 (Lurambi North)</household_code>
                  <baseline_role>Household Head / Cooperative Treasurer</baseline_role>
                  <baseline_year>2023 Wave 1</baseline_year>
                </item>
              </root>
            </instance>
            <instance id="respondent_residency_status">
              <root>
                <item>
                  <name>resident_full_time</name>
                  <label>Full-Time Resident (Present &gt;= 9 months/year)</label>
                  <color>#2E7D32</color>
                </item>
                <item>
                  <name>seasonal_migrant</name>
                  <label>Seasonal Labor Migrant (Present 3-8 months/year)</label>
                  <color>#F9A825</color>
                </item>
                <item>
                  <name>relocated</name>
                  <label>Relocated to Another Village / Urban Center</label>
                  <color>#1565C0</color>
                </item>
              </root>
            </instance>
            <instance id="primary_livelihood_sources">
              <root>
                <item>
                  <itextId>primary_livelihood_sources-0</itextId>
                  <name>agroforestry_cocoa</name>
                  <color>#388E3C</color>
                </item>
                <item>
                  <itextId>primary_livelihood_sources-1</itextId>
                  <name>annual_food_crops</name>
                  <color>#FBC02D</color>
                </item>
                <item>
                  <itextId>primary_livelihood_sources-2</itextId>
                  <name>beekeeping</name>
                  <color>#FFA000</color>
                </item>
                <item>
                  <itextId>primary_livelihood_sources-3</itextId>
                  <name>tree_nursery_pes</name>
                  <color>#00897B</color>
                </item>
                <item>
                  <itextId>primary_livelihood_sources-4</itextId>
                  <name>off_farm_wage</name>
                  <color>#5C6BC0</color>
                </item>
              </root>
            </instance>
            <instance id="clean_cooking_energy">
              <root>
                <item>
                  <itextId>clean_cooking_energy-0</itextId>
                  <name>improved_cookstove</name>
                </item>
                <item>
                  <itextId>clean_cooking_energy-1</itextId>
                  <name>lpg_biogas</name>
                </item>
                <item>
                  <itextId>clean_cooking_energy-2</itextId>
                  <name>three_stone_fire</name>
                </item>
              </root>
            </instance>
            <itext>
              <translation lang="default" default="true()">
                <text id="primary_livelihood_sources-0">
                  <value>Shade Cocoa / Coffee Agroforestry</value>
                </text>
                <text id="primary_livelihood_sources-1">
                  <value>Food Crops (Maize, Beans, Cassava)</value>
                </text>
                <text id="primary_livelihood_sources-2">
                  <value>Apiculture / Forest Honey &amp; Non-Timber Products</value>
                </text>
                <text id="primary_livelihood_sources-3">
                  <value>Tree Seedling Nursery / Restoration Payments (PES)</value>
                </text>
                <text id="primary_livelihood_sources-4">
                  <value>Off-Farm Wage Employment / Trade</value>
                </text>
                <text id="clean_cooking_energy-0">
                  <value>Improved Fuelwood Rocket / Clay Cookstove</value>
                  <value form="image">jr://images/clean_cooking_energy-improved_cookstove.png</value>
                </text>
                <text id="clean_cooking_energy-1">
                  <value>LPG / Biogas / Electric Clean Stove</value>
                  <value form="image">jr://images/clean_cooking_energy-lpg_biogas.png</value>
                </text>
                <text id="clean_cooking_energy-2">
                  <value>Traditional Three-Stone Firewood Hearth</value>
                  <value form="image">jr://images/clean_cooking_energy-three_stone_fire.png</value>
                </text>
              </translation>
            </itext>
            <bind nodeset="/data/primary_respondent_id" type="select1" required="true()"/>
            <bind nodeset="/data/preloaded_household_code"
                  type="string"
                  readonly="true()"
                  calculate="instance('past_individuals')/root/item[name = /data/primary_respondent_id]/household_code"/>
            <bind nodeset="/data/preloaded_baseline_role"
                  type="string"
                  readonly="true()"
                  calculate="instance('past_individuals')/root/item[name = /data/primary_respondent_id]/baseline_role"/>
            <bind nodeset="/data/preloaded_baseline_year"
                  type="string"
                  readonly="true()"
                  calculate="instance('past_individuals')/root/item[name = /data/primary_respondent_id]/baseline_year"/>
            <bind nodeset="/data/returning_individuals_present" type="select" required="true()"/>
            <bind nodeset="/data/respondent_residency_status" type="select1" required="true()"/>
            <bind nodeset="/data/current_household_size"
                  type="int"
                  required="true()"
                  constraint=". &gt;= 1 and . &lt;= 30"
                  jr:constraintMsg="Household size must be between 1 and 30 members."/>
            <bind nodeset="/data/new_individuals_joined_count"
                  type="int"
                  required="true()"
                  constraint=". &gt;= 0 and . &lt;= 15"/>
            <bind nodeset="/data/primary_livelihood_sources" type="select" required="true()"/>
            <bind nodeset="/data/clean_cooking_energy" type="select1" required="true()"/>
            <bind nodeset="/data/enumerator_notes" type="string"/>
          </model>
        </h:head>
        <h:body>
          <select1 ref="/data/primary_respondent_id">
            <label>Select Primary Respondent (Preloaded Past Individuals List)</label>
            <hint>Choose the individual from the preloaded `past_individuals` entity roster.</hint>
            <itemset nodeset="instance('past_individuals')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <input ref="/data/preloaded_household_code">
            <label>Preloaded Household ID &amp; Village Cluster</label>
            <hint>Auto-populated from the selected individual entity record.</hint>
          </input>
          <input ref="/data/preloaded_baseline_role">
            <label>Preloaded Baseline Household Role</label>
          </input>
          <input ref="/data/preloaded_baseline_year">
            <label>First Panel Enrollment Wave</label>
          </input>
          <select ref="/data/returning_individuals_present">
            <label>Reconcile Past Household Individuals Present Today</label>
            <hint>Check all previously enrolled individuals from `past_individuals` who currently reside in this household.</hint>
            <itemset nodeset="instance('past_individuals')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select>
          <select1 ref="/data/respondent_residency_status">
            <label>Primary Respondent Current Residency Status</label>
            <itemset nodeset="instance('respondent_residency_status')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <input ref="/data/current_household_size">
            <label>Total Current Household Members (All Ages)</label>
            <hint>Include all individuals sleeping under the same roof and sharing meals.</hint>
          </input>
          <input ref="/data/new_individuals_joined_count">
            <label>New Individuals Joined Since Last Survey Wave</label>
            <hint>Number of births or new household members not in the preloaded roster.</hint>
          </input>
          <select ref="/data/primary_livelihood_sources">
            <label>Household Livelihood &amp; Income Sources</label>
            <itemset nodeset="instance('primary_livelihood_sources')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select>
          <select1 ref="/data/clean_cooking_energy">
            <label>Primary Household Cooking Fuel / Stove Type</label>
            <itemset nodeset="instance('clean_cooking_energy')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select1>
          <input ref="/data/enumerator_notes" appearance="multiline">
            <label>Roster Reconciliation Notes (Optional)</label>
            <hint>Record any name corrections or household split details for the panel tracker.</hint>
          </input>
        </h:body>
      </h:html>
      """
        .trimIndent(),
    sampleInstanceXml =
      """
      <data id="household_survey_past_individuals">
        <primary_respondent_id>0000000c-a9ae-59f6-ac51-7f5a8777598f</primary_respondent_id>
        <returning_individuals_present>0000000c-a9ae-59f6-ac51-7f5a8777598f 0000000d-2972-591c-b23d-4918d3e3b7b1 0000000f-23a2-50ad-b0e8-98639587eba4</returning_individuals_present>
        <respondent_residency_status>resident_full_time</respondent_residency_status>
        <current_household_size>5</current_household_size>
        <new_individuals_joined_count>1</new_individuals_joined_count>
        <primary_livelihood_sources>agroforestry_cocoa beekeeping</primary_livelihood_sources>
        <clean_cooking_energy>improved_cookstove</clean_cooking_energy>
      </data>
      """
        .trimIndent(),
  ),
  ALL_FIELD_TYPES(
    id = "all_field_types_showcase",
    shortLabel = "5. All Form Field Types (Current)",
    title = "EUDR & Shade-Tree Field Survey",
    subtitle =
      "Comprehensive showcase of all XForms / ProtoForms field types (`string`, `int`, `decimal`, `boolean`, `date`, `time`, `select1`, `likert`, `select`, `range`, `rank`, `geopoint`, `geotrace`, `geoshape`, photo / video / audio `upload`, `trigger`, `calculate`, `group`, `repeat`).",
    badgeText = "All 20+ Field Types",
    requiresEntity = false,
    xformsXml =
      """
      <?xml version="1.0"?>
      <h:html xmlns="http://www.w3.org/2002/xforms"
              xmlns:h="http://www.w3.org/1999/xhtml"
              xmlns:jr="http://openrosa.org/javarosa"
              xmlns:orx="http://openrosa.org/xforms"
              xmlns:entities="http://www.opendatakit.org/xforms/entities">
        <h:head>
          <h:title>EUDR &amp; Shade-Tree Field Survey</h:title>
          <model orx:xforms-version="1.0.0" entities:entities-version="2024.1.0">
            <instance>
              <data id="eudr_shade_survey" version="2026032901">
                <dominant_shade_species>Grevillea robusta</dominant_shade_species>
                <shade_species_choice>erythrina_poeppigiana</shade_species_choice>
                <surviving_saplings_count>188</surviving_saplings_count>
                <mean_dbh_cm>28.4</mean_dbh_cm>
                <canopy_cover_pct>70</canopy_cover_pct>
                <eudr_compliant_buffer>true</eudr_compliant_buffer>
                <survey_date>2026-09-24</survey_date>
                <arrival_time>08:30</arrival_time>
                <canopy_health_rating>good</canopy_health_rating>
                <certification_schemes>rainforest_alliance organic_eu</certification_schemes>
                <conservation_priority_rank>riparian_buffer native_canopy soil_carbon</conservation_priority_rank>
                <plot_center_gps>-1.292066 36.821946 1680.0 4.2</plot_center_gps>
                <riparian_transect>-1.2921 36.8219 1680 3.5; -1.2924 36.8222 1679 3.6</riparian_transect>
                <plot_boundary>-1.2921 36.8219 1680 3.5; -1.2925 36.8224 1681 3.8; -1.2918 36.8228 1682 3.2; -1.2921 36.8219 1680 3.5</plot_boundary>
                <leaf_voucher_photo>erythrina_voucher_104.jpg</leaf_voucher_photo>
                <canopy_walkthrough_video/>
                <farmer_interview_audio/>
                <safety_protocol_ack>OK</safety_protocol_ack>
                <calculated_shade_index/>
                <site_conditions_group>
                  <soil_moisture_class>moist_well_drained</soil_moisture_class>
                  <field_notes>Healthy Erythrina canopy with active natural regeneration.</field_notes>
                </site_conditions_group>
                <sampled_trees>
                  <tree_tag>T-001</tree_tag>
                  <stem_dbh_cm>31.5</stem_dbh_cm>
                </sampled_trees>
                <orx:meta>
                  <orx:instanceID/>
                </orx:meta>
              </data>
            </instance>
            <instance id="shade_species" src="jr://file-csv/shade_species.csv">
              <root>
                <item>
                  <name>erythrina_poeppigiana</name>
                  <label>Erythrina poeppigiana (Mountain Immortelle)</label>
                </item>
                <item>
                  <name>inga_edulis</name>
                  <label>Inga edulis (Ice-cream-bean)</label>
                </item>
                <item>
                  <name>cordia_alliodora</name>
                  <label>Cordia alliodora (Spanish Elm)</label>
                </item>
                <item>
                  <name>gliricidia_sepium</name>
                  <label>Gliricidia sepium (Mother of Cocoa)</label>
                </item>
              </root>
            </instance>
            <instance id="canopy_health_rating">
              <root>
                <item>
                  <name>very_poor</name>
                  <label>1 - Severe Dieback</label>
                  <color>#C62828</color>
                </item>
                <item>
                  <name>poor</name>
                  <label>2 - Moderate Stress</label>
                  <color>#EF6C00</color>
                </item>
                <item>
                  <name>fair</name>
                  <label>3 - Acceptable</label>
                  <color>#F9A825</color>
                </item>
                <item>
                  <name>good</name>
                  <label>4 - Vigorous Crown</label>
                  <color>#689F38</color>
                </item>
                <item>
                  <name>excellent</name>
                  <label>5 - Optimal Closed Canopy</label>
                  <color>#1B5E20</color>
                </item>
              </root>
            </instance>
            <instance id="certification_schemes">
              <root>
                <item>
                  <itextId>certification_schemes-0</itextId>
                  <name>rainforest_alliance</name>
                </item>
                <item>
                  <itextId>certification_schemes-1</itextId>
                  <name>organic_eu</name>
                </item>
                <item>
                  <itextId>certification_schemes-2</itextId>
                  <name>fairtrade</name>
                </item>
                <item>
                  <itextId>certification_schemes-3</itextId>
                  <name>carbon_verified</name>
                </item>
              </root>
            </instance>
            <instance id="soil_moisture_class">
              <root>
                <item>
                  <name>dry</name>
                  <label>Dry / Cracked Surface</label>
                  <color>#D84315</color>
                </item>
                <item>
                  <name>moist_well_drained</name>
                  <label>Moist &amp; Well-Drained Loam</label>
                  <color>#2E7D32</color>
                </item>
                <item>
                  <name>saturated</name>
                  <label>Waterlogged / Hydric</label>
                  <color>#0277BD</color>
                </item>
              </root>
            </instance>
            <itext>
              <translation lang="English (en)" default="true()">
                <text id="/data/dominant_shade_species:label">
                  <value>Dominant Intercropped Shade Species (Text String Input)</value>
                </text>
                <text id="/data/dominant_shade_species:hint">
                  <value>Single-line text input (`type="string"`) for recording scientific or local species name.</value>
                </text>
                <text id="/data/surviving_saplings_count:label">
                  <value>Surviving Shade Tree Saplings Count (Integer Input)</value>
                </text>
                <text id="/data/surviving_saplings_count:hint">
                  <value>Whole number input with range constraint (0 to 500 saplings).</value>
                </text>
                <text id="/data/plot_boundary:label">
                  <value>Plot Perimeter Polygon (GeoShape Walk / Map)</value>
                </text>
                <text id="/data/plot_boundary:hint">
                  <value>Closed polygon geometry (`geoshape`) with vertex list and area calculation.</value>
                </text>
                <text id="certification_schemes-0">
                  <value>Rainforest Alliance / UTZ</value>
                  <value form="image">jr://images/certification_schemes-rainforest_alliance.png</value>
                </text>
                <text id="certification_schemes-1">
                  <value>EU / USDA Certified Organic</value>
                  <value form="image">jr://images/certification_schemes-organic_eu.png</value>
                </text>
                <text id="certification_schemes-2">
                  <value>Fairtrade International</value>
                  <value form="image">jr://images/certification_schemes-fairtrade.png</value>
                </text>
                <text id="certification_schemes-3">
                  <value>Plan Vivo / Gold Standard Carbon</value>
                  <value form="image">jr://images/certification_schemes-carbon_verified.png</value>
                </text>
              </translation>
            </itext>
            <bind nodeset="/data/dominant_shade_species" type="string" required="true()" entities:saveto="dominant_species"/>
            <bind nodeset="/data/shade_species_choice" type="select1" required="true()"/>
            <bind nodeset="/data/surviving_saplings_count" type="int" required="true()" constraint=". &gt;= 0 and . &lt;= 500" jr:constraintMsg="Enter a valid count between 0 and 500." entities:saveto="sapling_count"/>
            <bind nodeset="/data/mean_dbh_cm" type="decimal" required="true()" constraint=". &gt; 0 and . &lt;= 250"/>
            <bind nodeset="/data/canopy_cover_pct" type="int" required="true()" constraint=". &gt;= 0 and . &lt;= 100"/>
            <bind nodeset="/data/eudr_compliant_buffer" type="boolean" required="true()"/>
            <bind nodeset="/data/survey_date" type="date" required="true()"/>
            <bind nodeset="/data/arrival_time" type="time"/>
            <bind nodeset="/data/canopy_health_rating" type="select1" required="true()"/>
            <bind nodeset="/data/certification_schemes" type="select"/>
            <bind nodeset="/data/conservation_priority_rank" type="rank"/>
            <bind nodeset="/data/plot_center_gps" type="geopoint" required="true()" accuracyThreshold="10.0"/>
            <bind nodeset="/data/riparian_transect" type="geotrace"/>
            <bind nodeset="/data/plot_boundary" type="geoshape" required="true()" entities:saveto="geometry"/>
            <bind nodeset="/data/leaf_voucher_photo" type="binary" orx:max-pixels="1600"/>
            <bind nodeset="/data/canopy_walkthrough_video" type="binary"/>
            <bind nodeset="/data/farmer_interview_audio" type="binary"/>
            <bind nodeset="/data/safety_protocol_ack" type="string"/>
            <bind nodeset="/data/calculated_shade_index"
                  type="string"
                  readonly="true()"
                  calculate="concat('Species: ', /data/dominant_shade_species, ' | Saplings: ', /data/surviving_saplings_count, ' | Canopy: ', /data/canopy_cover_pct, '%')"/>
            <bind nodeset="/data/site_conditions_group/soil_moisture_class" type="select1"/>
            <bind nodeset="/data/site_conditions_group/field_notes" type="string"/>
            <bind nodeset="/data/sampled_trees/tree_tag" type="string" required="true()"/>
            <bind nodeset="/data/sampled_trees/stem_dbh_cm" type="decimal" required="true()"/>
          </model>
        </h:head>
        <h:body>
          <input ref="/data/dominant_shade_species">
            <label ref="jr:itext('/data/dominant_shade_species:label')"/>
            <hint ref="jr:itext('/data/dominant_shade_species:hint')"/>
          </input>
          <select1 ref="/data/shade_species_choice" appearance="minimal">
            <label>Select Reference Shade Species (Select1 / Itemset)</label>
            <hint>Single-choice selection populated from `shade_species` secondary instance.</hint>
            <itemset nodeset="instance('shade_species')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <input ref="/data/surviving_saplings_count">
            <label ref="jr:itext('/data/surviving_saplings_count:label')"/>
            <hint ref="jr:itext('/data/surviving_saplings_count:hint')"/>
          </input>
          <input ref="/data/mean_dbh_cm">
            <label>Mean Stand DBH in cm (Decimal Input)</label>
            <hint>Floating-point diameter at breast height (1.3m) across canopy trees.</hint>
          </input>
          <range ref="/data/canopy_cover_pct" start="0" end="100" step="5">
            <label>Overstory Crown Closure % (Range Slider)</label>
            <hint>Interactive slider control (`range`) from 0% to 100% in 5% increments.</hint>
          </range>
          <input ref="/data/eudr_compliant_buffer">
            <label>Riparian Buffer Intact? (Boolean Toggle)</label>
            <hint>Boolean true/false verification control.</hint>
          </input>
          <input ref="/data/survey_date">
            <label>Field Inspection Date (Date Picker)</label>
            <hint>ISO-8601 calendar date (`YYYY-MM-DD`).</hint>
          </input>
          <input ref="/data/arrival_time">
            <label>Plot Arrival Time (Time Input)</label>
            <hint>24-hour local observation timestamp (`HH:MM`).</hint>
          </input>
          <select1 ref="/data/canopy_health_rating" appearance="likert">
            <label>Crown Vigor &amp; Foliage Health (Select1 Likert Scale)</label>
            <hint>5-point Likert scale rating of upper-canopy leaf density.</hint>
            <itemset nodeset="instance('canopy_health_rating')/root/item">
              <value ref="name"/>
              <label ref="label"/>
            </itemset>
          </select1>
          <select ref="/data/certification_schemes">
            <label>Active Sustainability Certifications (Multi-Select Checkboxes)</label>
            <hint>Select all sustainability standards verified for this parcel.</hint>
            <itemset nodeset="instance('certification_schemes')/root/item">
              <value ref="name"/>
              <label ref="jr:itext(itextId)"/>
            </itemset>
          </select>
          <rank ref="/data/conservation_priority_rank">
            <label>Rank Conservation Interventions (Ordered Rank Control)</label>
            <hint>Order conservation priorities from highest (1) to lowest priority.</hint>
            <item>
              <label>Riparian Stream Buffer Protection</label>
              <value>riparian_buffer</value>
            </item>
            <item>
              <label>Indigenous Shade Tree Enrichment</label>
              <value>native_canopy</value>
            </item>
            <item>
              <label>Organic Mulch &amp; Soil Carbon Retention</label>
              <value>soil_carbon</value>
            </item>
          </rank>
          <input ref="/data/plot_center_gps" appearance="placement-map" accuracyThreshold="10.0">
            <label>Plot Reference Point (GeoPoint with Map Panning)</label>
            <hint>Single coordinate (`geopoint`) with `placement-map` appearance and &lt;=10m threshold.</hint>
          </input>
          <input ref="/data/riparian_transect">
            <label>Riparian Stream Buffer Line (GeoTrace Polyline)</label>
            <hint>Multi-vertex open polyline (`geotrace`) following the watercourse buffer edge.</hint>
          </input>
          <input ref="/data/plot_boundary" appearance="walk-or-draw placement-map" accuracyThreshold="10.0">
            <label ref="jr:itext('/data/plot_boundary:label')"/>
            <hint ref="jr:itext('/data/plot_boundary:hint')"/>
          </input>
          <upload ref="/data/leaf_voucher_photo" mediatype="image/*">
            <label>Botanical Voucher Photo (Photo Capture)</label>
            <hint>Take or choose a leaf/bark specimen photo (`mediatype="image/*"`, downscaled to 1600 px via `orx:max-pixels`).</hint>
          </upload>
          <upload ref="/data/canopy_walkthrough_video" mediatype="video/*" appearance="new">
            <label>Canopy Walkthrough Video (Video Capture)</label>
            <hint>Record a short 360° pan of the shade canopy from plot center (`mediatype="video/*"`; `appearance="new"` requires a fresh recording).</hint>
          </upload>
          <upload ref="/data/farmer_interview_audio" mediatype="audio/*">
            <label>Farmer Interview Note (Audio Recording)</label>
            <hint>Record the grower describing shade-tree management practices (`mediatype="audio/*"`).</hint>
          </upload>
          <trigger ref="/data/safety_protocol_ack">
            <label>Acknowledge Canopy &amp; Wildlife Safety Protocol (Trigger Control)</label>
            <hint>Tap to confirm safety checklist completion (`OK`).</hint>
          </trigger>
          <input ref="/data/calculated_shade_index">
            <label>Live Calculated Stand Summary (Read-Only Calculate Expression)</label>
            <hint>Reactive XPath `calculate` field combining species, saplings, and canopy %.</hint>
          </input>
          <group ref="/data/site_conditions_group" appearance="field-list">
            <label>Site Microclimate &amp; Notes Group (field-list Page Group)</label>
            <select1 ref="/data/site_conditions_group/soil_moisture_class">
              <label>Topsoil Moisture Regime</label>
              <itemset nodeset="instance('soil_moisture_class')/root/item">
                <value ref="name"/>
                <label ref="jr:itext(itextId)"/>
              </itemset>
            </select1>
            <input ref="/data/site_conditions_group/field_notes" appearance="multiline">
              <label>Detailed Agronomist Observations (Multiline Textarea)</label>
              <hint>Multi-line text input (`appearance="multiline"`).</hint>
            </input>
          </group>
          <group ref="/data/sampled_trees">
            <label>Individual Tree Measurements (Repeat Group)</label>
            <repeat nodeset="/data/sampled_trees">
              <input ref="/data/sampled_trees/tree_tag">
                <label>Tree Aluminum Tag ID</label>
              </input>
              <input ref="/data/sampled_trees/stem_dbh_cm">
                <label>Measured Stem DBH (cm)</label>
              </input>
            </repeat>
          </group>
        </h:body>
      </h:html>
      """
        .trimIndent(),
    sampleInstanceXml =
      """
      <data id="eudr_shade_survey">
        <dominant_shade_species>Grevillea robusta</dominant_shade_species>
        <shade_species_choice>erythrina_poeppigiana</shade_species_choice>
        <surviving_saplings_count>188</surviving_saplings_count>
        <mean_dbh_cm>28.4</mean_dbh_cm>
        <canopy_cover_pct>70</canopy_cover_pct>
        <eudr_compliant_buffer>true</eudr_compliant_buffer>
        <survey_date>2026-09-24</survey_date>
        <arrival_time>08:30</arrival_time>
        <canopy_health_rating>good</canopy_health_rating>
        <certification_schemes>rainforest_alliance organic_eu</certification_schemes>
        <conservation_priority_rank>riparian_buffer native_canopy soil_carbon</conservation_priority_rank>
        <plot_center_gps>-1.292066 36.821946 1680.0 4.2</plot_center_gps>
        <riparian_transect>-1.2921 36.8219 1680 3.5; -1.2924 36.8222 1679 3.6</riparian_transect>
        <plot_boundary>-1.2921 36.8219 1680 3.5; -1.2925 36.8224 1681 3.8; -1.2918 36.8228 1682 3.2; -1.2921 36.8219 1680 3.5</plot_boundary>
        <leaf_voucher_photo>erythrina_voucher_104.jpg</leaf_voucher_photo>
        <canopy_walkthrough_video/>
        <farmer_interview_audio/>
        <safety_protocol_ack>OK</safety_protocol_ack>
        <site_conditions_group>
          <soil_moisture_class>moist_well_drained</soil_moisture_class>
          <field_notes>Healthy Erythrina canopy with active natural regeneration.</field_notes>
        </site_conditions_group>
        <sampled_trees>
          <tree_tag>T-001</tree_tag>
          <stem_dbh_cm>31.5</stem_dbh_cm>
        </sampled_trees>
      </data>
      """
        .trimIndent(),
  );

  /** Parsed [FormDefinition] domain wrapper for this example form. */
  val formDefinition: FormDefinition by lazy {
    FormDefinition(XFormsXmlSerializer.deserializeFormDef(xformsXml))
  }

  /** Parsed [FormRecordInstance] domain wrapper for this example form's sample submission. */
  val sampleRecordInstance: FormRecordInstance by lazy {
    FormRecordInstance(
      XFormsXmlSerializer.deserializeRecordInstance(
        xml = sampleInstanceXml,
        formDef = formDefinition.proto,
      )
    )
  }

  companion object {
    fun fromIdOrDefault(
      id: String?,
      default: WorkbenchExampleForm = ALL_FIELD_TYPES,
    ): WorkbenchExampleForm = entries.firstOrNull { it.id == id } ?: default
  }
}
