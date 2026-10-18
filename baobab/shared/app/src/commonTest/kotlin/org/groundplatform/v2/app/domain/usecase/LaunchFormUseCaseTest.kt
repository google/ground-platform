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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.groundplatform.v2.app.data.datasource.local.store.runNow
import org.groundplatform.v2.app.data.datasource.local.store.seededStore
import org.groundplatform.v2.app.data.repository.SurveyRepositoryImpl
import org.groundplatform.v2.app.domain.model.FormPreviewItem
import org.groundplatform.v2.app.domain.model.GeospatialEntityItem
import org.groundplatform.v2.app.ui.datacollection.ENTITY_REF_FIELD_PATH
import org.groundplatform.v2.core.forms.ui.FormWizardController

class LaunchFormUseCaseTest {
  private val useCase = LaunchFormUseCase()
  private val repository = SurveyRepositoryImpl(seededStore())
  private val forms: List<FormPreviewItem> = runNow { repository.getForms() }
  private val entities: List<GeospatialEntityItem> = runNow { repository.getEntities() }
  private val entityForm = forms.first { it.id == "form-eudr-baseline" }
  private val candidates = entities.filter { it.datasetId == entityForm.targetDatasetId }

  @Test
  fun withoutEntity_addsRequiredEntityRefStepFirst() {
    val controller =
      useCase(customFormDef = null, form = entityForm, candidateEntities = candidates)
    assertEquals(0, controller.currentStepIndex)
    assertTrue(LaunchFormUseCase.isEntityRefStep(controller.currentStep))
    assertFalse(controller.nextStep(), "the entityref step is required")
    assertTrue(controller.stringAt(LaunchFormUseCase.ENTITY_REF_FIELD_PATH).isNullOrEmpty())
  }

  @Test
  fun withoutEntity_canSkipTheEntityRefStep() {
    val controller = useCase(customFormDef = null, form = entityForm, includeEntityRefStep = false)
    assertFalse(LaunchFormUseCase.isEntityRefStep(controller.currentStep))
  }

  @Test
  fun withEntity_prepopulatesReferences_andSkipsTheStep() {
    val entity = candidates.first()
    val controller = useCase(customFormDef = null, form = entityForm, entity = entity)
    assertFalse(LaunchFormUseCase.isEntityRefStep(controller.currentStep))
    val fields = controller.formState.fieldStates
    LaunchFormUseCase.ENTITY_REFERENCE_PATHS.filter { it in fields }
      .forEach { path -> assertEquals(entity.id, controller.stringAt(path), path) }
  }

  @Test
  fun defaultSelectedEntityId_preselectsTheEntityRefStep() {
    val entity = candidates.last()
    val controller =
      useCase(
        customFormDef = null,
        form = entityForm,
        candidateEntities = candidates,
        defaultSelectedEntityId = entity.id,
      )
    assertTrue(LaunchFormUseCase.isEntityRefStep(controller.currentStep))
    assertEquals(entity.id, controller.stringAt(LaunchFormUseCase.ENTITY_REF_FIELD_PATH))
  }

  @Test
  fun prepopulateEntityReference_onlyTouchesQuestionsTheFormHas() {
    val controller =
      useCase(customFormDef = null, form = entityForm, candidateEntities = candidates)
    val before = controller.formState.fieldStates.keys
    useCase.prepopulateEntityReference(controller, "00000007-640e-5ed7-9874-4d6b6717f21d")
    assertEquals(before, controller.formState.fieldStates.keys)
    assertEquals(
      "00000007-640e-5ed7-9874-4d6b6717f21d",
      controller.stringAt(LaunchFormUseCase.ENTITY_REF_FIELD_PATH),
    )
  }

  @Test
  fun standaloneForm_neverGetsAnEntityRefStep() {
    val standalone = forms.first { !it.requiresEntity }
    val controller = useCase(customFormDef = null, form = standalone, includeEntityRefStep = true)
    assertFalse(LaunchFormUseCase.isEntityRefStep(controller.currentStep))
    assertFalse(LaunchFormUseCase.ENTITY_REF_FIELD_PATH in controller.formState.fieldStates)
  }

  @Test
  fun stepPredicates_handleNull() {
    assertFalse(LaunchFormUseCase.isEntityRefStep(null))
    assertFalse(LaunchFormUseCase.isGeometryStep(null))
  }

  @Test
  fun launchForm_startsWithEmptyUserFields() {
    val controller =
      useCase(customFormDef = null, form = entityForm, includeEntityRefStep = false)
    assertTrue(
      controller.stringAt("/data/commodity_type").isNullOrEmpty(),
      "Commodity type should not be prefilled with sample choice",
    )
    assertTrue(
      controller.stringAt("/data/certification_schemes").isNullOrEmpty(),
      "Certification schemes should not be prefilled with sample choices",
    )
    assertTrue(
      controller.stringAt("/data/collector_observation").isNullOrEmpty(),
      "Observation should not be prefilled with sample text",
    )
    val metricVal = controller.formState.fieldStates["/data/canopy_or_parcel_metric"]?.value?.scalar_value?.int32_value
    assertTrue(
      metricVal == null || metricVal == 0,
      "Metric should not be prefilled with sample value",
    )
  }
}

/** The string answer at [path], or `null` when the Form lacks the question or it is empty. */
private fun FormWizardController.stringAt(path: String): String? =
  formState.fieldStates[path]?.value?.scalar_value?.string_value
