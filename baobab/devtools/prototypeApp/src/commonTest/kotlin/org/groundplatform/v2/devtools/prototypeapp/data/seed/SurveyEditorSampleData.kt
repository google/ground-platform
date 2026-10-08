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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.DatasetKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorRelevance
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityDataset
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityProperty
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EntityRow
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryKind
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LatLng
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.LayerStyle
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.PropertyType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.RelevanceOperator
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SharingPolicy
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyDetails
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorDraft
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.SurveyEditorForm

/** Hand-authored Survey editor fixtures used by tests (fictional sample data). */
internal object SurveyEditorSamples {
  /** The sample survey as a complete editor draft. */
  fun draft(): SurveyEditorDraft =
    SurveyEditorDraft(
      details = details(),
      sharing = sharing(),
      forms =
        listOf(
          SurveyEditorForm("f1", FormEditorSamples.shadeTreeVisit()),
          SurveyEditorForm("f2", FormEditorSamples.parcelBoundaryCheck()),
        ),
      datasets = listOf(coffeeParcels(), shadePlots(), farmers(), treeSpecies()),
    )

  fun details() =
    SurveyDetails(
      surveyId = "kenya_coffee_shade",
      title = "Kenya Coffee Shade Monitoring",
      description =
        "Monitor shade tree cover and parcel boundaries across smallholder coffee farms.",
      supportedLanguages = listOf("en", "sw"),
      organizationId = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE,
      surveyArea = PrototypeFakeSurveyEditorData.kenyaSurveyArea(),
    )

  fun sharing() = PrototypeFakeSurveyEditorData.kenyaSharing()

  /** ID of the sample survey shared with everyone in its organization. */
  const val ORGANIZATION_SHARED_SURVEY_ID: String =
    PrototypeFakeSurveyEditorData.ORGANIZATION_SHARED_SURVEY_ID

  /**
   * A minimal draft for [ORGANIZATION_SHARED_SURVEY_ID]: owned by a Mekong Mangrove Alliance member
   * and open to everyone in that organization ([SharingPolicy.ORGANIZATION]).
   */
  fun organizationSharedDraft(): SurveyEditorDraft =
    PrototypeFakeSurveyEditorData.organizationSharedDraft(
      SurveyEditorDraft.blank(ORGANIZATION_SHARED_SURVEY_ID)
    )

  private fun square(lat: Double, lng: Double, d: Double) =
    listOf(
      LatLng(lat, lng),
      LatLng(lat, lng + d),
      LatLng(lat - d * 0.8, lng + d * 1.1),
      LatLng(lat - d, lng - d * 0.1),
    )

  fun coffeeParcels() =
    EntityDataset(
      key = "d1",
      kind = DatasetKind.MAP_LAYER,
      id = "coffee_parcels",
      displayName = "Coffee parcels",
      description = "Registered smallholder parcel boundaries.",
      geometryKind = GeometryKind.POLYGON,
      keyProperty = "parcel_id",
      labelProperty = "parcel_name",
      properties =
        listOf(
          EntityProperty("parcel_id", "Parcel ID", PropertyType.TEXT, required = true),
          EntityProperty("parcel_name", "Parcel name", PropertyType.TEXT, required = true),
          EntityProperty("area_ha", "Area (ha)", PropertyType.DECIMAL),
          EntityProperty("status", "Status", PropertyType.TEXT),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf(
              "parcel_id" to "NYR-104",
              "parcel_name" to "Gatura Ridge",
              "area_ha" to "1.8",
              "status" to "completed",
            ),
            square(-0.4180, 36.9480, 0.004),
          ),
          EntityRow(
            "r2",
            mapOf(
              "parcel_id" to "NYR-108",
              "parcel_name" to "Kiamariga",
              "area_ha" to "2.4",
              "status" to "in_progress",
            ),
            square(-0.4230, 36.9560, 0.005),
          ),
          EntityRow(
            "r3",
            mapOf(
              "parcel_id" to "NYR-112",
              "parcel_name" to "Mathira East",
              "area_ha" to "0.9",
              "status" to "pending",
            ),
            square(-0.4120, 36.9620, 0.003),
          ),
        ),
      style =
        LayerStyle(colorHex = "#6D4C41", strokeWidth = 2.0, fillOpacity = 0.25, iconName = "eco"),
    )

  fun shadePlots() =
    EntityDataset(
      key = "d2",
      kind = DatasetKind.MAP_LAYER,
      id = "shade_monitoring_plots",
      displayName = "Shade monitoring plots",
      description = "Permanent 20 m radius plots for shade tree measurements.",
      geometryKind = GeometryKind.POINT,
      keyProperty = "plot_id",
      labelProperty = "plot_id",
      fieldCreationEnabled = true,
      properties =
        listOf(
          EntityProperty("plot_id", "Plot ID", PropertyType.TEXT, required = true),
          EntityProperty("established", "Established", PropertyType.DATE),
          EntityProperty("canopy_pct", "Canopy cover (%)", PropertyType.INTEGER),
        ),
      rows =
        listOf(
          EntityRow(
            "r1",
            mapOf("plot_id" to "SHD-201", "established" to "2025-03-14", "canopy_pct" to "42"),
            listOf(LatLng(-0.4150, 36.9500)),
          ),
          EntityRow(
            "r2",
            mapOf("plot_id" to "SHD-202", "established" to "2025-03-15", "canopy_pct" to "35"),
            listOf(LatLng(-0.4205, 36.9585)),
          ),
          EntityRow(
            "r3",
            mapOf("plot_id" to "SHD-203", "established" to "2025-04-02", "canopy_pct" to "58"),
            listOf(LatLng(-0.4108, 36.9641)),
          ),
          EntityRow(
            "r4",
            mapOf("plot_id" to "SHD-204", "established" to "2025-04-03"),
            listOf(LatLng(-0.4252, 36.9470)),
          ),
        ),
      style = LayerStyle(colorHex = "#2E7D32", iconName = "park"),
    )

  fun farmers() = PrototypeFakeSurveyEditorData.farmers()

  fun treeSpecies() = PrototypeFakeSurveyEditorData.treeSpecies()
}

/** Sample Forms for the Survey editor (fictional sample data). */
internal object FormEditorSamples {
  fun parcelBoundaryCheck(): EditorForm =
    EditorForm(
      formId = "parcel_boundary_check",
      title = "Parcel Boundary Check",
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = "boundary_matches",
            type = EditorQuestionType.SELECT_ONE,
            label = "Does the mapped boundary match what you see?",
            required = true,
            choices = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No")),
          ),
          EditorQuestion(
            key = "q2",
            name = "boundary_notes",
            type = EditorQuestionType.LONG_TEXT,
            label = "Describe what's different",
            required = true,
            relevance = EditorRelevance("q1", RelevanceOperator.EQUALS, "no"),
          ),
          EditorQuestion(
            key = "q3",
            name = "corner_location",
            type = EditorQuestionType.LOCATION,
            label = "Location of the nearest corner marker",
          ),
        ),
    )

  fun shadeTreeVisit(): EditorForm {
    val yesNo = listOf(EditorChoice("yes", "Yes"), EditorChoice("no", "No"))
    return EditorForm(
      formId = "shade_tree_visit",
      title = "Shade Tree Farm Visit",
      questions =
        listOf(
          EditorQuestion(
            key = "q1",
            name = "visit_date",
            type = EditorQuestionType.DATE,
            label = "Visit date",
            required = true,
          ),
          EditorQuestion(
            key = "q2",
            name = "farm_location",
            type = EditorQuestionType.LOCATION,
            label = "Farm location",
            hint = "Stand near the center of the plot.",
            required = true,
          ),
          EditorQuestion(
            key = "q3",
            name = "has_shade_trees",
            type = EditorQuestionType.SELECT_ONE,
            label = "Are shade trees present?",
            required = true,
            choices = yesNo,
          ),
          EditorQuestion(
            key = "q4",
            name = "shade_tree_count",
            type = EditorQuestionType.INTEGER,
            label = "How many shade trees?",
            required = true,
            relevance = EditorRelevance("q3", RelevanceOperator.EQUALS, "yes"),
          ),
          EditorQuestion(
            key = "q5",
            name = "canopy_photo",
            type = EditorQuestionType.PHOTO,
            label = "Canopy photo",
            hint = "Point the camera straight up.",
            relevance = EditorRelevance("q3", RelevanceOperator.EQUALS, "yes"),
          ),
          EditorQuestion(
            key = "q6",
            name = "observed_issues",
            type = EditorQuestionType.SELECT_MULTIPLE,
            label = "Observed issues",
            choices =
              listOf(
                EditorChoice("pests", "Pests"),
                EditorChoice("disease", "Disease"),
                EditorChoice("erosion", "Erosion"),
              ),
          ),
          EditorQuestion(
            key = "q7",
            name = "pest_notes",
            type = EditorQuestionType.LONG_TEXT,
            label = "Describe the pest damage",
            relevance = EditorRelevance("q6", RelevanceOperator.INCLUDES, "pests"),
          ),
          EditorQuestion(
            key = "q8",
            name = "closing_note",
            type = EditorQuestionType.NOTE,
            label = "Thanks! Review your answers before submitting.",
          ),
        ),
    )
  }
}
