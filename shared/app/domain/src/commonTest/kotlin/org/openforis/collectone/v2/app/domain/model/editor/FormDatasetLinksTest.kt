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
package org.openforis.collectone.v2.app.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng

class FormDatasetLinksTest {
  private fun q(
    name: String,
    type: EditorQuestionType = EditorQuestionType.TEXT,
    label: String = name.uppercase(),
    decimalPlaces: Int = 0,
    required: Boolean = false,
  ) =
    EditorQuestion(
      key = "k-$name",
      name = name,
      type = type,
      label = label,
      decimalPlaces = decimalPlaces,
      required = required,
    )

  private fun form(vararg questions: EditorQuestion) =
    EditorForm(formId = "f", title = "F", questions = questions.toList())

  private fun dataset(
    kind: DatasetKind = DatasetKind.MAP_LAYER,
    geometryType: GeometryType = GeometryType.POINT,
    keyProperty: String = "id",
    labelProperty: String = "id",
    properties: List<EntityProperty> = listOf(EntityProperty("id", "ID")),
    rows: List<EntityRow> = emptyList(),
  ) =
    EntityDataset(
      key = "d",
      kind = kind,
      id = "plots",
      displayName = "Plots",
      geometryType = geometryType,
      keyProperty = keyProperty,
      labelProperty = labelProperty,
      properties = properties,
      rows = rows,
    )

  @Test
  fun datasetKindAndGeometry_followThePrimaryGeometryQuestion() {
    val table = form(q("name"))
    val lines = form(q("name"), q("route", EditorQuestionType.LINE))
    val polygons =
      form(q("area", EditorQuestionType.POLYGON), q("loc", EditorQuestionType.LOCATION))

    assertEquals(DatasetKind.DATA_TABLE, FormDatasetLinks.datasetKindFor(table))
    assertEquals(DatasetKind.MAP_LAYER, FormDatasetLinks.datasetKindFor(lines))
    assertEquals(GeometryType.POINT, FormDatasetLinks.geometryTypeFor(table))
    assertEquals(GeometryType.LINE_STRING, FormDatasetLinks.geometryTypeFor(lines))
    assertEquals(GeometryType.POLYGON, FormDatasetLinks.geometryTypeFor(polygons))
  }

  @Test
  fun formProperties_mirrorQuestionsWithoutNotes() {
    val properties =
      FormDatasetLinks.formProperties(
        form(
          q("count", EditorQuestionType.NUMBER, required = true),
          q("height", EditorQuestionType.NUMBER, decimalPlaces = 2),
          q("visited", EditorQuestionType.DATE),
          q("intro", EditorQuestionType.NOTE),
          q("species", EditorQuestionType.SELECT_ONE, label = ""),
        )
      )

    assertEquals(
      listOf(
        EntityProperty("count", "COUNT", PropertyType.INTEGER, required = true),
        EntityProperty("height", "HEIGHT", PropertyType.DECIMAL),
        EntityProperty("visited", "VISITED", PropertyType.DATE),
        EntityProperty("species", "species", PropertyType.TEXT),
      ),
      properties,
    )
  }

  @Test
  fun linkedProperties_addAnIdUnlessTheFormHasOne() {
    assertEquals(
      listOf("id", "name"),
      FormDatasetLinks.linkedProperties(form(q("name"))).map { it.name },
    )
    assertEquals(
      listOf("name", "id"),
      FormDatasetLinks.linkedProperties(form(q("name"), q("id"))).map { it.name },
    )
  }

  @Test
  fun syncedWithForm_keepsTheKeyPropertyAndAdoptsTheFormSchema() {
    val code = EntityProperty("code", "Code", required = true)
    val synced =
      FormDatasetLinks.syncedWithForm(
        dataset(
          kind = DatasetKind.DATA_TABLE,
          keyProperty = "code",
          labelProperty = "gone",
          properties = listOf(code, EntityProperty("gone", "Gone")),
        ),
        form(q("name"), q("area", EditorQuestionType.POLYGON)),
      )

    assertEquals(DatasetKind.MAP_LAYER, synced.kind)
    assertEquals(GeometryType.POLYGON, synced.geometryType)
    // The polygon answer becomes each feature's geometry, so it gets no property of its own.
    assertEquals(listOf("code", "name"), synced.properties.map { it.name })
    assertEquals("code", synced.keyProperty)
    // The label property left the schema, so the best remaining label (the name) takes over.
    assertEquals("name", synced.labelProperty)
  }

  @Test
  fun syncedWithForm_generatedLayersKeepTheirGeometryType() {
    val generated =
      dataset(geometryType = GeometryType.POLYGON).copy(generator = SampleDesignConfig())

    val synced =
      FormDatasetLinks.syncedWithForm(generated, form(q("loc", EditorQuestionType.LOCATION)))

    assertEquals(GeometryType.POLYGON, synced.geometryType)
  }

  @Test
  fun syncedWithForm_withoutTheKeyInTheSchemaUsesTheIdProperty() {
    val synced =
      FormDatasetLinks.syncedWithForm(
        dataset(keyProperty = "missing", properties = emptyList()),
        form(q("name")),
      )

    assertEquals(listOf("id", "name"), synced.properties.map { it.name })
    assertEquals("id", synced.keyProperty)
    assertEquals(DatasetKind.DATA_TABLE, synced.kind)
  }

  @Test
  fun syncedWithForm_preservesPropertiesAndCollectedDataForDeletedQuestions() {
    val existingDataset =
      dataset(
        kind = DatasetKind.DATA_TABLE,
        keyProperty = "id",
        labelProperty = "farmer_name",
        properties =
          listOf(
            EntityProperty("id", "ID", required = true),
            EntityProperty("farmer_name", "Farmer name", required = true),
            EntityProperty("shade_trees", "Shade trees", PropertyType.INTEGER, required = true),
          ),
        rows =
          listOf(
            EntityRow(
              key = "r1",
              values = mapOf("id" to "P-01", "farmer_name" to "Amina", "shade_trees" to "12"),
            )
          ),
      )
    val formWithDeletedQuestion =
      EditorForm(
        formId = "f",
        title = "F",
        questions = listOf(q("farmer_name", required = true)),
        deletedQuestions =
          listOf(
            q("shade_trees", EditorQuestionType.NUMBER, label = "Shade trees", required = true)
              .copy(isDeleted = true)
          ),
      )

    val synced = FormDatasetLinks.syncedWithForm(existingDataset, formWithDeletedQuestion)

    assertEquals(listOf("id", "farmer_name", "shade_trees"), synced.properties.map { it.name })
    val deletedProp = synced.properties.first { it.name == "shade_trees" }
    assertEquals(false, deletedProp.required)
    assertEquals("12", synced.rows.single().values["shade_trees"])
  }

  @Test
  fun questionsFor_buildsAGeometryQuestionThenOnePerProperty() {
    val questions =
      FormDatasetLinks.questionsFor(
        dataset(
          geometryType = GeometryType.LINE_STRING,
          properties =
            listOf(
              EntityProperty("count", "Count", PropertyType.INTEGER, required = true),
              EntityProperty("height", "Height", PropertyType.DECIMAL),
              EntityProperty("alive", "Alive", PropertyType.BOOLEAN),
              EntityProperty("seen", "Seen", PropertyType.DATE),
            ),
        )
      )

    assertEquals(
      listOf(
        EditorQuestionType.LINE,
        EditorQuestionType.NUMBER,
        EditorQuestionType.NUMBER,
        EditorQuestionType.SELECT_ONE,
        EditorQuestionType.DATE,
      ),
      questions.map { it.type },
    )
    assertEquals(listOf("q1", "q2", "q3", "q4", "q5"), questions.map { it.key })
    assertEquals("location", questions[0].name)
    assertEquals(true, questions[1].required)
    assertEquals(0, questions[1].decimalPlaces)
    assertEquals(EditorQuestion.DEFAULT_DECIMAL_PLACES, questions[2].decimalPlaces)
    assertEquals(listOf("yes", "no"), questions[3].choices.map { it.value })
  }

  @Test
  fun questionsFor_dataTablesHaveNoGeometryQuestion() {
    val questions = FormDatasetLinks.questionsFor(dataset(kind = DatasetKind.DATA_TABLE))

    assertEquals(listOf("id"), questions.map { it.name })
  }

  @Test
  fun defaultGeometry_drawsAShapeOfTheLayersKindAtThePosition() {
    val at = LatLng(10.0, 20.0)

    assertEquals(listOf(at), FormDatasetLinks.defaultGeometry(dataset(), at))
    assertEquals(
      3,
      FormDatasetLinks.defaultGeometry(dataset(geometryType = GeometryType.LINE_STRING), at).size,
    )
    val square = FormDatasetLinks.defaultGeometry(dataset(geometryType = GeometryType.POLYGON), at)
    assertEquals(4, square.size)
    assertEquals(at, square.first())
  }

  @Test
  fun defaultGeometry_withoutAPositionOffsetsFromTheExistingFeatures() {
    val rows =
      listOf(
        EntityRow("a", geometry = listOf(LatLng(1.0, 1.0))),
        EntityRow("b", geometry = listOf(LatLng(3.0, 3.0))),
      )

    val point = FormDatasetLinks.defaultGeometry(dataset(rows = rows), at = null).single()

    // Centered on the features (2, 2), offset by 0.002 × (2 rows % 5 − 2) = 0.
    assertEquals(LatLng(2.0, 2.0), point)
  }

  @Test
  fun uniqueTitleAndId_appendTheFirstFreeNumber() {
    assertEquals("Plots", FormDatasetLinks.uniqueTitle("Plots", listOf("Trees")))
    assertEquals("Plots 3", FormDatasetLinks.uniqueTitle("Plots", listOf("Plots", "Plots 2")))
    assertEquals("plots", FormDatasetLinks.uniqueId("plots", emptyList()))
    assertEquals("plots_2", FormDatasetLinks.uniqueId("plots", listOf("plots")))
  }

  @Test
  fun withRenamedTargetDataset_updatesSaveToAndChoiceQuestions() {
    val form =
      EditorForm(
        formId = "f",
        title = "F",
        questions =
          listOf(
            EditorQuestion(
              "k1",
              "plot",
              EditorQuestionType.SELECT_ONE,
              "Plot",
              choiceDatasetId = "old",
            ),
            EditorQuestion(
              "k2",
              "other",
              EditorQuestionType.SELECT_ONE,
              "O",
              choiceDatasetId = "x",
            ),
          ),
        saveTo = EditorSaveTo(targetDatasetId = "old"),
      )

    val renamed = form.withRenamedTargetDataset("old", "new")

    assertEquals("new", renamed.saveTo.targetDatasetId)
    assertEquals(listOf("new", "x"), renamed.questions.map { it.choiceDatasetId })
  }

  @Test
  fun withRenamedTargetProperty_followsTheRenameInMatchingMappingsAndStatusRules() {
    val form =
      EditorForm(
        formId = "f",
        title = "F",
        questions = emptyList(),
        saveTo =
          EditorSaveTo(
            targetDatasetId = "plots",
            idMatchProperty = "code",
            mappings = listOf(EditorFieldMapping("k1", "code"), EditorFieldMapping("k2", "name")),
            status =
              EditorStatusConfig(
                rules =
                  listOf(
                    EditorStatusRule(
                      subject = StatusConditionSubject.ENTITY_PROPERTY,
                      property = "code",
                    ),
                    EditorStatusRule(subject = StatusConditionSubject.QUESTION, property = "code"),
                  )
              ),
          ),
      )

    val renamed = form.withRenamedTargetProperty("plots", "code", "plot_code", saveTarget = null)

    assertEquals("plot_code", renamed.saveTo.idMatchProperty)
    assertEquals(listOf("plot_code", "name"), renamed.saveTo.mappings.map { it.property })
    assertEquals(listOf("plot_code", "code"), renamed.saveTo.status.rules.map { it.property })
    // Other datasets' renames leave the Form alone.
    assertSame(form, form.withRenamedTargetProperty("trees", "code", "x", saveTarget = null))
  }

  @Test
  fun formProperties_ofAMapLayer_leaveOutOnlyThePrimaryGeometry() {
    val form =
      form(
        q("plot", EditorQuestionType.POLYGON),
        q("name"),
        q("waypoint", EditorQuestionType.LOCATION),
        q("info", EditorQuestionType.NOTE),
      )
    // The plot polygon becomes each feature's geometry; a second geometry is an ordinary answer.
    assertEquals(listOf("name", "waypoint"), FormDatasetLinks.formProperties(form).map { it.name })
    // In a Data table nothing becomes a geometry, so every answer is a property.
    assertEquals(
      listOf("plot", "name", "waypoint"),
      FormDatasetLinks.formProperties(form, DatasetKind.DATA_TABLE).map { it.name },
    )
    assertEquals(
      listOf("id", "name", "waypoint"),
      FormDatasetLinks.linkedProperties(form).map { it.name },
    )
  }

  @Test
  fun labelPropertyFor_prefersNamesThenIdentifiersThenChoicesThenText() {
    fun labelOf(vararg questions: EditorQuestion): String {
      val form = form(*questions)
      return FormDatasetLinks.labelPropertyFor(form, FormDatasetLinks.linkedProperties(form), "id")
    }
    val consent = q("data_consent", EditorQuestionType.SELECT_ONE)
    val notes = q("notes")
    assertEquals(
      "producer_name",
      labelOf(
        consent,
        notes,
        q("permit_id"),
        q("producer_name"),
        q("loc", EditorQuestionType.LOCATION),
      ),
    )
    assertEquals("permit_id", labelOf(consent, notes, q("permit_id")))
    assertEquals("data_consent", labelOf(notes, consent))
    assertEquals("notes", labelOf(notes, q("count", EditorQuestionType.NUMBER)))
    // Numbers, dates, multiple choices, media, and geometries make poor labels: the key labels.
    assertEquals(
      "id",
      labelOf(
        q("count", EditorQuestionType.NUMBER),
        q("seen", EditorQuestionType.DATE),
        q("threats", EditorQuestionType.SELECT_MULTIPLE),
        q("photo", EditorQuestionType.PHOTO),
        q("site", EditorQuestionType.POLYGON),
      ),
    )
    // The key property itself is never the label.
    val form = form(q("code"))
    assertEquals(
      "code",
      FormDatasetLinks.labelPropertyFor(form, FormDatasetLinks.formProperties(form), "code"),
    )
  }

  @Test
  fun newLinkedDataset_mirrorsTheFormAndIsLinkedToIt() {
    val form = form(q("community_name"), q("territory", EditorQuestionType.POLYGON))
    val layer =
      FormDatasetLinks.newLinkedDataset(form, "d9", "f8", displayName = "Territories", id = "terr")

    assertEquals(DatasetKind.MAP_LAYER, layer.kind)
    assertEquals(GeometryType.POLYGON, layer.geometryType)
    assertEquals("d9", layer.key)
    assertEquals("f8", layer.linkedFormKey)
    assertEquals("terr", layer.id)
    assertEquals("Territories", layer.displayName)
    assertEquals(listOf("id", "community_name"), layer.properties.map { it.name })
    assertEquals("id", layer.keyProperty)
    assertEquals("community_name", layer.labelProperty)

    // Forced into a Data table, the polygon answer is kept as a property.
    val table =
      FormDatasetLinks.newLinkedDataset(form, "d9", "f8", "T", "t", kind = DatasetKind.DATA_TABLE)
    assertEquals(DatasetKind.DATA_TABLE, table.kind)
    assertEquals(listOf("id", "community_name", "territory"), table.properties.map { it.name })
  }
}
