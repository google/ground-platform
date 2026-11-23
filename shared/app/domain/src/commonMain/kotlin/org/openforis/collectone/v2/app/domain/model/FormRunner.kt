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
package org.openforis.collectone.v2.app.domain.model

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldValue
import org.openforis.collectone.v2.core.forms.model.FinalizationResult
import org.openforis.collectone.v2.core.forms.model.FormState

/**
 * The form a collector is filling in, as data collection use cases see it. The presentation layer
 * implements it over its form runner, so use cases don't depend on the runner's UI.
 */
interface FormRunner {
  /** The form's current state. */
  val formState: FormState

  /** Finalizes the form, validating every answer, as the runner's submit action does. */
  fun finalizeForm(): FinalizationResult
}

/**
 * How collected answers read to people: the question labels and answer values the form runner
 * shows. Implemented by the presentation layer.
 */
interface AnswerDisplay {
  /**
   * The label of each question of [state], keyed by canonical path, in the order the form asks
   * them. Questions inside a repeat are prefixed with their repeat instance's label.
   */
  fun questionLabels(state: FormState): Map<String, String>

  /** [value], an answer of [dataType], as shown to people. */
  fun displayValue(value: FieldValue?, dataType: DataType): String
}
