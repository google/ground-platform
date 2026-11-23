/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.web.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SampleAreaSource
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.map.LatLng
import org.openforis.collectone.v2.web.domain.model.editor.DataTableImporter
import org.openforis.collectone.v2.web.domain.model.editor.DateOrder
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreaResult
import org.openforis.collectone.v2.web.domain.model.editor.SamplingAreas
import org.openforis.collectone.v2.web.domain.model.editor.SamplingMessage
import org.openforis.collectone.v2.web.domain.usecase.GenerateSamplePlotsUseCase

/** The English text of the Survey editor's data table import notes and sampling problems. */
class SurveyEditorTextTest {

  private fun english(text: EditorText): String = runBlocking {
    when (text) {
      is EditorText.Resource -> getString(text.res, *text.args.toTypedArray())
      is EditorText.Plural -> getPluralString(text.res, text.quantity, *text.args.toTypedArray())
      is EditorText.Verbatim -> text.text
      is EditorText.Localized -> text.text.resolveString()
    }
  }

  private fun importNotes(csv: String, dateOrder: DateOrder = DateOrder.DAY_FIRST) =
    DataTableImporter.plan("trees.csv", csv, dateOrder).notes.map { english(it.toEditorText()) }

  private fun unavailableArea(config: SampleDesignConfig, datasets: List<EntityDataset>) =
    english(
      assertIs<SamplingAreaResult.Unavailable>(
          SamplingAreas.samplingArea(config, surveyArea = null, datasets)
        )
        .message
        .toEditorText()
    )

  @Test
  fun dateOrderLabels() {
    assertEquals(
      listOf("Day first", "Month first"),
      DateOrder.entries.map { runBlocking { getString(it.labelRes) } },
    )
  }

  @Test
  fun importNotes_emptyFiles() {
    assertEquals(listOf("The file is empty."), importNotes(""))
    assertEquals(listOf("The file has a header row but no data rows."), importNotes("id,name\n"))
  }

  @Test
  fun importNotes_raggedRowsAndRepeatedIds() {
    assertEquals(
      listOf(
        "2 rows have a different number of columns than the header. Missing cells are left " +
          "blank and extra cells are dropped.",
        "The id column has blank or repeated values, so a new Row ID column is added.",
      ),
      importNotes("id,name\n1,A,extra\n1\n"),
    )
  }

  @Test
  fun importNotes_ambiguousDates() {
    assertEquals(
      listOf("Dates in \"Visited\" could be day or month first. They're read as MM/DD/YYYY."),
      importNotes("name,Visited\nA,03/04/2025\n", DateOrder.MONTH_FIRST),
    )
  }

  @Test
  fun samplingAreaProblems() {
    val strata = SampleDesignConfig(areaSource = SampleAreaSource.StrataLayer("strata", ""))
    val layer =
      EntityDataset(
        key = "strata",
        kind = DatasetKind.MAP_LAYER,
        id = "zones",
        displayName = "Zones",
        geometryType = GeometryType.POLYGON,
        keyProperty = "id",
        labelProperty = "id",
        properties = emptyList(),
      )
    assertEquals(
      "Set a survey area in Survey details, or use a polygon map layer as strata.",
      unavailableArea(SampleDesignConfig(), emptyList()),
    )
    assertEquals(
      "The strata map layer was deleted. Choose another one.",
      unavailableArea(strata, emptyList()),
    )
    assertEquals(
      "Strata must come from a polygon map layer.",
      unavailableArea(
        strata,
        listOf(
          layer.copy(
            geometryType = GeometryType.POINT,
            rows = listOf(EntityRow("r1", geometry = listOf(LatLng(0.0, 0.0)))),
          )
        ),
      ),
    )
    assertEquals(
      "\"Zones\" has no polygons yet. Add polygons to use it as strata.",
      unavailableArea(strata, listOf(layer)),
    )
  }

  @Test
  fun generationFailures() {
    assertEquals(
      listOf(
        "This design can't be generated.",
        "Couldn't generate sample plots. Check the design and try again.",
        "Couldn't generate sample plots: out of memory",
        "No sample plots fit in the area. Try a smaller spacing or plot size, or a larger area.",
        "Can't fit 50 plots 900 m apart.",
      ),
      listOf(
          GenerateSamplePlotsUseCase.Result.Failed(SamplingMessage.CANT_GENERATE_DESIGN.message()),
          GenerateSamplePlotsUseCase.Result.Failed(SamplingMessage.GENERATE_FAILED.message()),
          GenerateSamplePlotsUseCase.Result.Failed(
            SamplingMessage.GENERATE_FAILED_WITH_REASON.message("out of memory")
          ),
          GenerateSamplePlotsUseCase.Result.Failed(SamplingMessage.NO_PLOTS_FIT.message()),
          GenerateSamplePlotsUseCase.Result.DesignRejected("Can't fit 50 plots 900 m apart."),
        )
        .map { english(it.toEditorText()) },
    )
  }

  @Test
  fun generationWithoutADesign() {
    val draft = SurveyEditorSamples.draft()
    val layer =
      EntityDataset(
        key = "d9",
        kind = DatasetKind.MAP_LAYER,
        id = "sample_plots",
        displayName = "Sample plots",
        keyProperty = "plot_id",
        labelProperty = "plot_id",
        properties = emptyList(),
      )
    val result =
      assertIs<GenerateSamplePlotsUseCase.Result.Failed>(
        runDirect {
          GenerateSamplePlotsUseCase()(layer, draft.details.surveyArea, draft.datasets)
        }
      )
    assertEquals(
      "This map layer doesn't have a sample design.",
      english(result.message.toEditorText()),
    )
  }
}
