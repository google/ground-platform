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
package org.openforis.collectone.v2.app.ui.datacollection

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import org.openforis.collectone.v2.app.domain.model.AnswerDisplay
import org.openforis.collectone.v2.app.domain.model.FormRunner
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.formatFieldValueForDisplay

/** The [FormRunner] of the shared form wizard [controller]. */
class WizardFormRunner(private val controller: FormWizardController) : FormRunner {
  override val formState: FormState
    get() = controller.formState

  override fun finalizeForm(): FinalizationResult = controller.finalizeForm()
}

/**
 * [AnswerDisplay] with the question labels of the form wizard's steps and its answer formatting.
 */
object WizardAnswerDisplay : AnswerDisplay {
  override fun questionLabels(state: FormState): Map<String, String> {
    val labels = LinkedHashMap<String, String>()
    FormWizardController.buildSteps(state).forEach { step ->
      val repeatPrefix = step.repeatContext?.instanceLabel?.takeIf { it.isNotBlank() }
      fun label(base: String) = if (repeatPrefix != null) "$repeatPrefix • $base" else base
      when (step) {
        is FormWizardStep.QuestionStep ->
          labels[step.control.canonicalPath] = label(step.questionLabel)
        is FormWizardStep.FieldListGroupStep ->
          step.controls.forEach { ctrl ->
            val path = ctrl.canonicalPath
            labels[path] =
              label(ctrl.label?.text?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'))
          }
        else -> {}
      }
    }
    return labels
  }

  override fun displayValue(value: FieldValue?, dataType: DataType): String =
    formatFieldValueForDisplay(value, dataType)
}
