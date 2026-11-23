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
package org.openforis.collectone.v2.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.openforis.collectone.v2.app.data.datasource.local.store.sampleDataHolder
import org.openforis.collectone.v2.app.domain.model.LayerSourceType
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_pdf_share_title_map_feature
import org.openforis.collectone.v2.app.resources.domain_layer_source_type_entity_dataset
import org.openforis.collectone.v2.app.ui.datacollection.awaitUiState
import org.openforis.collectone.v2.app.ui.text.UiText
import org.openforis.collectone.v2.app.ui.text.badgeLabelRes
import org.openforis.collectone.v2.app.ui.text.typeText
import org.openforis.collectone.v2.mobile.ui.navigation.MobileRoute

/** User-facing terminology of the mobile UI models. */
class MobileTerminologyTest {
  private val state =
    MobileAppState(initialRoute = MobileRoute.MainSurvey, dataHolder = sampleDataHolder())

  /** Fails when [text] shows a `1:1` or `1:N` cardinality to users. */
  private fun assertNoCardinality(text: String) {
    assertFalse(text.contains("1:1"), "Unexpected 1:1 in: $text")
    assertFalse(text.contains("1:N"), "Unexpected 1:N in: $text")
  }

  /** Fails when [text] says "entity" or "entities" to users. */
  private fun assertNoEntityWord(text: String) {
    assertFalse(text.contains("entity", ignoreCase = true), "Unexpected 'entity' in: $text")
    assertFalse(text.contains("entities", ignoreCase = true), "Unexpected 'entities' in: $text")
  }

  // --- No cardinalities ---

  @Test
  fun mapLayerLabels_showNoCardinality() {
    state.viewModels.dashboard.uiState.value.mapLayers.forEach { assertNoCardinality(it.label) }
  }

  @Test
  fun formTexts_showNoCardinality() {
    state.viewModels.dataCollection.uiState.value.forms.forEach { form ->
      assertNoCardinality(form.title)
      assertNoCardinality(form.description)
      assertNoCardinality(form.targetDatasetName)
    }
  }

  @Test
  fun mapFeatureLabels_showNoCardinality() {
    state.viewModels.dataCollection.uiState.value.entities.forEach { entity ->
      assertNoCardinality(entity.label)
      assertNoCardinality(entity.datasetName)
    }
  }

  // --- No "entity" ---

  @Test
  fun entityDatasetLayerBadge_saysSurveyLayer() {
    // Its English text, "Survey Layer", is checked in shared/app's DomainStringsParityTest.
    assertEquals(
      Res.string.domain_layer_source_type_entity_dataset,
      LayerSourceType.ENTITY_DATASET.badgeLabelRes,
    )
  }

  @Test
  fun mapLayerTexts_doNotSayEntity() {
    state.viewModels.dashboard.uiState.value.mapLayers.forEach { layer ->
      assertNoEntityWord(layer.label)
      assertNoEntityWord(layer.singularNoun)
      assertNoEntityWord(layer.pluralNoun)
    }
  }

  @Test
  fun mapFeatureTypeLabels_doNotSayEntity() {
    state.viewModels.dataCollection.uiState.value.entities.forEach { entity ->
      assertNoEntityWord(entity.typeNoun)
      assertNoEntityWord(entity.datasetName)
    }
  }

  @Test
  fun mapFeaturePdfSheets_doNotSayEntity() = runTest {
    state.viewModels.dataCollection.uiState.value.entities.forEach { entity ->
      state.viewModels.dataCollection.shareEntityPdf(entity.id)
      val sheet =
        checkNotNull(
          state.viewModels.dataCollection
            .awaitUiState { it.activeSharedPdfSheet?.targetId == entity.id }
            .activeSharedPdfSheet
        )

      assertEquals(
        UiText.Resource(
          Res.string.app_pdf_share_title_map_feature,
          listOf(entity.typeText),
        ),
        sheet.shareTitle,
      )
      assertNoEntityWord(sheet.pdfFileName)
    }
  }

  @Test
  fun submissionTargetLabels_doNotSayEntity() {
    state.viewModels.dataCollection.uiState.value.allSubmissions.forEach {
      assertNoEntityWord(it.targetTypeNoun)
    }
  }
}
