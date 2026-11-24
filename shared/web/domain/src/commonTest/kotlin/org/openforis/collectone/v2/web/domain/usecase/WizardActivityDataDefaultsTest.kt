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
package org.openforis.collectone.v2.web.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import org.openforis.collectone.v2.app.data.datasource.local.store.runNow
import org.openforis.collectone.v2.app.data.datasource.local.store.seededStore
import org.openforis.collectone.v2.app.data.repository.LibraryRepositoryImpl
import org.openforis.collectone.v2.app.data.seed.SampleOrganizationsData
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.SurveyEditorDraft
import org.openforis.collectone.v2.app.domain.usecase.ResolveLibraryUseCase
import org.openforis.collectone.v2.web.domain.model.editor.SurveyCollectionMode
import org.openforis.collectone.v2.web.domain.model.editor.SurveyWizardRules
import org.openforis.collectone.v2.web.domain.model.editor.WizardMapFeatureSource
import org.openforis.collectone.v2.web.domain.model.editor.WizardStartMethod

class WizardActivityDataDefaultsTest {
  private val kfs = SampleOrganizationsData.KENYA_FOREST_SERVICE

  @Test
  fun activityDataPurpose_defaultsSubPlotToGrid_andSeedsLandUseColorsAndStratumIcons() = runNow {
    val store = seededStore()
    val libraries = LibraryRepositoryImpl(store).observeLibraries().first()
    val library = ResolveLibraryUseCase().forOrganization(libraries, kfs)
    val useCase = ConfigureWizardSurveyDraftUseCase()

    assertEquals(
      SubPlotMode.GRID,
      SurveyWizardRules.defaultSubPlotMode(listOf("deforestation_activity_data")),
    )
    assertEquals(
      SubPlotMode.NONE,
      SurveyWizardRules.defaultSubPlotMode(listOf("lulc_training_data")),
    )

    val baseDraft = SurveyEditorDraft.blank(surveyId = "s-ad")
    val activityDraft =
      baseDraft.copy(
        details = baseDraft.details.copy(purposeIds = listOf("deforestation_activity_data"))
      )

    val synced =
      useCase.synchronizePrimaryLayerAndForms(
        current = activityDraft,
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.VISUAL_CLASSIFICATION,
        mapFeatureSource = WizardMapFeatureSource.SAMPLE_PLOTS,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = true,
        previousSeededFormKeys = emptySet(),
      )

    val primaryLayer = synced.draft.datasets.first { it.kind == DatasetKind.MAP_LAYER }
    assertEquals(SubPlotMode.GRID, primaryLayer.generator?.subPlot)

    val form = synced.draft.forms.first().form
    val baselineLandUse = form.questions.first { it.name == "baseline_land_use_ipcc" }
    val currentLandUse = form.questions.first { it.name == "current_land_use_ipcc" }
    val forestStratum = form.questions.first { it.name == "forest_stratum" }
    assertTrue(baselineLandUse.choices.all { !it.colorHex.isNullOrBlank() })
    assertTrue(currentLandUse.choices.all { !it.colorHex.isNullOrBlank() })
    assertTrue(forestStratum.choices.all { it.image != null })

    // Switching from Activity Data to LULC training before generating plots resets subPlot to NONE.
    val switched =
      useCase.synchronizePrimaryLayerAndForms(
        current =
          synced.draft.copy(
            details = synced.draft.details.copy(purposeIds = listOf("lulc_training_data"))
          ),
        library = library,
        startMethod = WizardStartMethod.FROM_SCRATCH,
        effectiveMode = SurveyCollectionMode.VISUAL_CLASSIFICATION,
        mapFeatureSource = WizardMapFeatureSource.SAMPLE_PLOTS,
        isGeometryTypeOverridden = false,
        isEudrPlotsUnderFourHaConfirmed = false,
        hasPurposeSelectionChanged = true,
        previousSeededFormKeys = synced.seededFormKeys,
      )
    val switchedLayer = switched.draft.datasets.first { it.kind == DatasetKind.MAP_LAYER }
    assertEquals(SubPlotMode.NONE, switchedLayer.generator?.subPlot)
  }
}
