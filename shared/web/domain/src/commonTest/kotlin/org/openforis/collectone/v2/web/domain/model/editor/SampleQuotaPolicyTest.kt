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
package org.openforis.collectone.v2.web.domain.model.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EntityDataset
import org.openforis.collectone.v2.app.domain.model.editor.EntityProperty
import org.openforis.collectone.v2.app.domain.model.editor.EntityRow
import org.openforis.collectone.v2.app.domain.model.editor.SampleDesignConfig
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.map.GeometryType
import org.openforis.collectone.v2.web.domain.model.editor.SampleQuotaPolicy.Result

class SampleQuotaPolicyTest {
  private fun generatedLayer(key: String, plots: Int) =
    EntityDataset(
      key = key,
      kind = DatasetKind.MAP_LAYER,
      id = "plots_$key",
      displayName = "Plots $key",
      geometryType = GeometryType.POLYGON,
      keyProperty = "plot_id",
      labelProperty = "plot_id",
      properties = listOf(EntityProperty("plot_id", "Plot ID")),
      rows = List(plots) { EntityRow("$key-$it", mapOf("plot_id" to "$it"), emptyList()) },
      generator = SampleDesignConfig(),
    )

  private fun draft(
    maxGeneratedEntities: Int,
    vararg layers: EntityDataset,
    organizationId: String? = null,
  ): SurveyEditorDraft {
    val blank = SurveyEditorDraft.blank(surveyId = "s1")
    return blank.copy(
      details =
        blank.details.copy(
          organizationId = organizationId,
          quotaLimits = QuotaLimitsConfig(maxGeneratedEntities = maxGeneratedEntities),
        ),
      datasets = layers.toList(),
    )
  }

  @Test
  fun plotsUpToTheLimit_areWithinLimit() {
    val draft = draft(10, generatedLayer("a", 4), generatedLayer("b", 0))

    assertEquals(Result.WithinLimit, SampleQuotaPolicy.check(draft, "b", 6, emptyList()))
  }

  @Test
  fun plotsAboveTheLimit_countOtherGeneratedLayers() {
    val draft = draft(10, generatedLayer("a", 4), generatedLayer("b", 0))

    assertEquals(Result.Exceeded(11, 10), SampleQuotaPolicy.check(draft, "b", 7, emptyList()))
  }

  @Test
  fun previousPlotsOfTheRegeneratedLayer_areReplacedNotAdded() {
    val draft = draft(10, generatedLayer("a", 9))

    assertEquals(Result.WithinLimit, SampleQuotaPolicy.check(draft, "a", 10, emptyList()))
  }

  @Test
  fun organizationLimit_appliesWhenTheSurveyHasNone() {
    val organization =
      Organization(
        id = "org1",
        name = "Org",
        quotaLimits = QuotaLimitsConfig(maxGeneratedEntities = 5),
      )
    val draft = draft(0, generatedLayer("a", 0), organizationId = "org1")

    assertEquals(
      Result.Exceeded(6, 5),
      SampleQuotaPolicy.check(draft, "a", 6, listOf(organization)),
    )
  }
}
