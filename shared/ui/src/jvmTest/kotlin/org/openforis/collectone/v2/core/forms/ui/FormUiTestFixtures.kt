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
package org.openforis.collectone.v2.core.forms.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.serialization.XFormsXmlSerializer

/** A [FormWizardController] over an XForm whose `<model>` and `<h:body>` are given. */
internal fun formController(title: String, model: String, body: String): FormWizardController =
  FormWizardController(
    formDef =
      XFormsXmlSerializer.deserializeFormDef(
        """
        <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml"
                xmlns:jr="http://openrosa.org/javarosa">
          <h:head>
            <h:title>$title</h:title>
            <model>$model</model>
          </h:head>
          <h:body>$body</h:body>
        </h:html>
        """
          .trimIndent()
      )
  )

/** The control of the current step, read during composition so it follows every answer. */
internal fun FormWizardController.currentQuestion(): ComponentState.ControlState =
  (currentStep as FormWizardStep.QuestionStep).control

/** A [GeoLocationSource] that always reports [fix]. */
internal class FixedGeoLocationSource(fix: GeoLocationFix?) : GeoLocationSource {
  override val currentFix: StateFlow<GeoLocationFix?> = MutableStateFlow(fix)
}

/** A fix with a known position and accuracy near the Eiffel Tower. */
internal val PARIS_FIX =
  GeoLocationFix(latitude = 48.858370, longitude = 2.294481, accuracyMeters = 4.0)
