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
package org.openforis.collectone.v2.web.ui.surveyeditor

import org.openforis.collectone.v2.app.domain.model.editor.DatasetIds
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.FormDatasetLinks
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImportPreview
import org.openforis.collectone.v2.web.domain.model.editor.DatasetPropertyRename
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPlan
import org.openforis.collectone.v2.web.domain.model.editor.MapLayerImportPreview
import org.openforis.collectone.v2.web.ui.formeditor.moved

/**
 * [DatasetActions] of the Survey editor: Map layers and Data tables, their properties and features,
 * and importing them from files, applied to the draft of [session].
 */
internal class SurveyDatasetHandler(private val session: SurveyDraftSession) : DatasetActions {
  override fun addDataset(kind: DatasetKind) = session.edit {
    val title =
      FormDatasetLinks.uniqueTitle(
        defaultNames.dataset(kind),
        datasets.map { it.displayName },
      )
    val dataset =
      EntityDataset(
        key = newKey("d"),
        kind = kind,
        id = DatasetIds.forName(title, datasets.map { it.id }),
        displayName = title,
        geometryType = GeometryType.POINT,
        keyProperty = "id",
        labelProperty = "name",
        properties =
          listOf(
            EntityProperty("id", "ID", PropertyType.TEXT, required = true),
            EntityProperty("name", "Name", PropertyType.TEXT),
          ),
      )
    datasets = datasets + dataset
    section = SurveyEditorSection.Dataset(dataset.key)
  }

  override fun deleteDataset(key: String) = session.edit {
    val dataset = dataset(key) ?: return@edit
    val siblings = datasets.filter { it.kind == dataset.kind }
    val index = siblings.indexOf(dataset)
    datasets = datasets.filterNot { it.key == key }
    forms = forms.map { entry ->
      if (entry.form.openInGoogleEarth && !entry.form.canOpenInGoogleEarth(catalog(entry.key))) {
        entry.copy(form = entry.form.copy(openInGoogleEarth = false))
      } else {
        entry
      }
    }
    val remaining = siblings.filterNot { it.key == key }
    section =
      remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.let {
        SurveyEditorSection.Dataset(it.key)
      } ?: SurveyEditorSection.Details
  }

  override fun updateDataset(key: String, transform: (EntityDataset) -> EntityDataset) =
    session.updateDataset(key, transform)

  override fun moveDataset(key: String, toIndex: Int) = session.edit {
    val kind = dataset(key)?.kind ?: return@edit
    val siblings = datasets.filter { it.kind == kind }
    val reordered = siblings.moved(siblings.indexOfFirst { it.key == key }, toIndex).iterator()
    datasets = datasets.map { if (it.kind == kind) reordered.next() else it }
  }

  override fun addProperty(key: String) = session.edit {
    updateDataset(key) { d ->
      val name = FormDatasetLinks.uniqueId("property", d.properties.map { it.name })
      d.copy(properties = d.properties + EntityProperty(name, defaultNames.property))
    }
  }

  override fun updateProperty(key: String, index: Int, property: EntityProperty) = session.edit {
    val updated = DatasetPropertyRename.apply(toDraft(), key, index, property)
    forms = updated.forms
    datasets = updated.datasets
  }

  override fun removeProperty(key: String, index: Int) =
    updateDataset(key) { d ->
      val removed = d.properties.getOrNull(index) ?: return@updateDataset d
      val remaining = d.properties.filterIndexed { i, _ -> i != index }
      d.copy(
        properties = remaining,
        keyProperty =
          if (d.keyProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.keyProperty,
        labelProperty =
          if (d.labelProperty == removed.name) remaining.firstOrNull()?.name.orEmpty()
          else d.labelProperty,
        rows = d.rows.map { it.copy(values = it.values - removed.name) },
      )
    }

  override fun addRow(key: String, at: LatLng?, geometry: List<LatLng>?): String {
    var rowKey = ""
    session.edit {
      rowKey = newKey("r")
      updateDataset(key) { d ->
        val keyValue =
          FormDatasetLinks.uniqueId(
            "${d.id.take(3).uppercase()}-${d.rows.size + 1}",
            d.rows.map { it.values[d.keyProperty].orEmpty() },
          )
        val shape =
          if (d.kind == DatasetKind.MAP_LAYER) geometry ?: FormDatasetLinks.defaultGeometry(d, at)
          else emptyList()
        d.copy(rows = d.rows + EntityRow(rowKey, mapOf(d.keyProperty to keyValue), shape))
      }
    }
    return rowKey
  }

  override fun updateCell(key: String, rowKey: String, property: String, value: String) =
    updateDataset(key) { d ->
      d.copy(
        rows =
          d.rows.map {
            if (it.key == rowKey) it.copy(values = it.values + (property to value)) else it
          }
      )
    }

  override fun updateGeometry(key: String, rowKey: String, geometry: List<LatLng>) =
    updateDataset(key) { d ->
      if (d.isGenerated) d
      else d.copy(rows = d.rows.map { if (it.key == rowKey) it.copy(geometry = geometry) else it })
    }

  override fun removeRow(key: String, rowKey: String) =
    updateDataset(key) { d -> d.copy(rows = d.rows.filterNot { it.key == rowKey }) }

  override fun importMapLayer(plan: MapLayerImportPlan, selectAfterImport: Boolean): String {
    var datasetKey = ""
    session.edit {
      val title = FormDatasetLinks.uniqueTitle(plan.name, datasets.map { it.displayName })
      datasetKey = newKey("d")
      val dataset =
        EntityDataset(
          key = datasetKey,
          kind = DatasetKind.MAP_LAYER,
          id = DatasetIds.forName(title, datasets.map { it.id }),
          displayName = title,
          geometryType = plan.geometryType,
          keyProperty = plan.keyProperty,
          labelProperty = plan.labelProperty,
          properties = plan.properties,
          rows = plan.rows.map { (values, geometry) -> EntityRow(newKey("r"), values, geometry) },
        )
      datasets = datasets + dataset
      if (selectAfterImport) {
        section = SurveyEditorSection.Dataset(datasetKey)
      }
    }
    return datasetKey
  }

  override fun importDataTable(plan: DataTableImportPlan): String {
    var datasetKey = ""
    session.edit {
      val title = FormDatasetLinks.uniqueTitle(plan.name, datasets.map { it.displayName })
      datasetKey = newKey("d")
      val dataset =
        EntityDataset(
          key = datasetKey,
          kind = DatasetKind.DATA_TABLE,
          id = DatasetIds.forName(title, datasets.map { it.id }),
          displayName = title,
          geometryType = GeometryType.POINT,
          keyProperty = plan.keyProperty,
          labelProperty = plan.labelProperty,
          properties = plan.properties,
          rows = plan.rows.map { values -> EntityRow(newKey("r"), values, emptyList()) },
        )
      datasets = datasets + dataset
      section = SurveyEditorSection.Dataset(datasetKey)
    }
    return datasetKey
  }

  override fun previewDataTableImport(
    fileName: String,
    text: String,
    dateOrder: DateOrder,
  ): DataTableImportPreview = DataTableImportPreview.of(fileName, text, dateOrder)

  override fun previewMapLayerImport(fileName: String, text: String): MapLayerImportPreview =
    MapLayerImportPreview.of(fileName, text)
}
