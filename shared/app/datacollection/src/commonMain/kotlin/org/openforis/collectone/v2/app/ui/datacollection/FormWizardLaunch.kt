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
import openforis.collectone.v1.forms.FormDef
import org.openforis.collectone.v2.app.domain.model.FormPreviewItem
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.domain.model.deviceFormEnvironment
import org.openforis.collectone.v2.app.domain.usecase.FormLaunch
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase
import org.openforis.collectone.v2.app.domain.usecase.LaunchFormUseCase.Companion.ENTITY_REFERENCE_PATHS
import org.openforis.collectone.v2.app.domain.usecase.ResolveFormDefForLaunchUseCase
import org.openforis.collectone.v2.core.forms.engine.FormEnvironment
import org.openforis.collectone.v2.core.forms.ui.FormWizardController
import org.openforis.collectone.v2.core.forms.ui.FormWizardStep
import org.openforis.collectone.v2.core.forms.ui.isEntityReferenceStep

/**
 * Canonical XForms `entityref` nodeset path (`/data/target_entity`) representing the geospatial
 * entity reference step (`select_one_from_file <dataset>.csv` / `appearance="map-select"`) when a
 * form requires a target geospatial entity. Alias of [LaunchFormUseCase.ENTITY_REF_FIELD_PATH].
 */
const val ENTITY_REF_FIELD_PATH: String = LaunchFormUseCase.ENTITY_REF_FIELD_PATH

/**
 * Ensures that when [form] requires a geospatial entity (`form.requiresEntity == true`), the
 * [FormDef] contains an `entityref` step (`[ENTITY_REF_FIELD_PATH] = "/data/target_entity"`,
 * `appearance = "map-select"`, `required = "true()"`) bound to the eligible candidate entities in
 * `form.targetDatasetId`.
 */
fun ensureEntityRefStepInFormDef(
  baseFormDef: FormDef,
  form: FormPreviewItem,
  candidateEntities: List<GeospatialEntityItem> = emptyList(),
  defaultSelectedEntityId: String = "",
): FormDef =
  ResolveFormDefForLaunchUseCase()
    .ensureEntityRefStepInFormDef(
      baseFormDef = baseFormDef,
      form = form,
      candidateEntities = candidateEntities,
      defaultSelectedEntityId = defaultSelectedEntityId,
    )

/**
 * Resolves the [FormDef] to execute in [FormWizardController] for a given [form] via
 * [ResolveFormDefForLaunchUseCase].
 */
fun resolveFormDefForLaunch(
  customFormDef: FormDef?,
  form: FormPreviewItem,
  candidateEntities: List<GeospatialEntityItem> = emptyList(),
  defaultSelectedEntityId: String = "",
  includeEntityRefStep: Boolean = false,
): FormDef =
  ResolveFormDefForLaunchUseCase()(
    customFormDef = customFormDef,
    form = form,
    candidateEntities = candidateEntities,
    defaultSelectedEntityId = defaultSelectedEntityId,
    includeEntityRefStep = includeEntityRefStep,
  )

/**
 * The form wizard that runs [launch] in [environment] (the device's clock and time zone, see
 * [deviceFormEnvironment]), with its entity-reference questions filled in.
 */
fun formWizardFor(launch: FormLaunch, environment: FormEnvironment): FormWizardController {
  val controller = FormWizardController(formDef = launch.formDef, environment = environment)
  launch.entityId?.let { prepopulateEntityReference(controller, it) }
  return controller
}

/**
 * Fills every entity-reference question of [controller]'s Form ([ENTITY_REFERENCE_PATHS]) that the
 * Form actually has with [entityId]. Questions the Form doesn't have are left alone.
 */
fun prepopulateEntityReference(controller: FormWizardController, entityId: String) {
  val fields = controller.formState.fieldStates
  ENTITY_REFERENCE_PATHS.filter { it in fields }.forEach { controller.updateString(it, entityId) }
}

/**
 * Returns `true` if [step] represents an `entityref` question step (e.g. bound to
 * [ENTITY_REF_FIELD_PATH] `/data/target_entity`, `/data/entity_id`, or configured with
 * `appearance="map-select"` / `"entityref"`).
 */
fun isEntityRefStep(step: FormWizardStep?): Boolean = isEntityReferenceStep(step)

/**
 * Returns `true` if [step] contains a geometry (`geopoint`, `geotrace`, or `geoshape`) question
 * control.
 */
fun isGeometryStep(step: FormWizardStep?): Boolean =
  controlsOf(step).any {
    val dt = it.fieldState.dataType
    dt == DataType.TYPE_GEOPOINT || dt == DataType.TYPE_GEOTRACE || dt == DataType.TYPE_GEOSHAPE
  }

private fun controlsOf(step: FormWizardStep?) =
  when (step) {
    is FormWizardStep.QuestionStep -> listOf(step.control)
    is FormWizardStep.FieldListGroupStep -> step.controls
    else -> emptyList()
  }

/** Returns `true` if [step] is an `entityref` question step; see [isEntityRefStep]. */
fun isWizardStepEntityRef(step: FormWizardStep?): Boolean = isEntityRefStep(step)

/** Returns `true` if [step] contains a geometry question; see [isGeometryStep]. */
fun isWizardStepGeoPoint(step: FormWizardStep?): Boolean = isGeometryStep(step)
