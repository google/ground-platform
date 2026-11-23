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
package org.openforis.collectone.v2.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.SurveyConfig
import org.openforis.collectone.v2.app.domain.repository.FallbackFormProvider
import org.openforis.collectone.v2.core.forms.model.FormDefinition

class ResolveFormDefForLaunchUseCaseTest {
  private val form =
    FormPreviewItem(
      id = "form-plot-visit",
      title = "Plot visit",
      description = "",
      version = "3",
      targetDatasetId = "",
      targetDatasetName = "",
      questionCount = 0,
      ctaLabel = "Visit",
    )
  private val storedForm = FormDefinition(FormDef(form_id = "stored"))
  private val fallbackForm = FormDefinition(FormDef(form_id = "fallback"))
  private val customFormDef = FormDef(form_id = "custom")
  private val fallbackForms = FallbackFormProvider { fallbackForm }

  @Test
  fun customFormDefWinsOverTheStoredForm() {
    val resolve = ResolveFormDefForLaunchUseCase(fallbackForms)

    val formDef =
      resolve(
        customFormDef = customFormDef,
        form = form,
        surveyConfig = SurveyConfig(formsById = mapOf(form.id to storedForm)),
      )

    assertEquals(customFormDef, formDef)
  }

  @Test
  fun storedFormWinsOverTheFallback() {
    val resolve = ResolveFormDefForLaunchUseCase(fallbackForms)

    val formDef =
      resolve(
        customFormDef = null,
        form = form,
        surveyConfig = SurveyConfig(formsById = mapOf(form.id to storedForm)),
      )

    assertEquals(storedForm.proto, formDef)
  }

  @Test
  fun fallbackIsUsedWhenTheStoreHasNoDefinition() {
    val resolve = ResolveFormDefForLaunchUseCase(fallbackForms)

    val formDef = resolve(customFormDef = null, form = form, surveyConfig = SurveyConfig())

    assertEquals(fallbackForm.proto, formDef)
  }

  @Test
  fun withoutFallbackAFormMissingFromTheStoreIsEmpty() {
    val resolve = ResolveFormDefForLaunchUseCase()

    val formDef = resolve(customFormDef = null, form = form, surveyConfig = null)

    assertEquals("Plot visit", formDef.title)
    assertEquals("3", formDef.version)
    assertTrue(formDef.view?.components.orEmpty().isEmpty())
  }

  @Test
  fun emptyFormStillPreloadsTheInstanceId() {
    val resolve = ResolveFormDefForLaunchUseCase()

    val formDef = resolve(customFormDef = null, form = form)

    assertEquals(
      listOf("/data/meta/instanceID"),
      formDef.model?.bindings.orEmpty().map { it.field_path },
    )
  }
}
