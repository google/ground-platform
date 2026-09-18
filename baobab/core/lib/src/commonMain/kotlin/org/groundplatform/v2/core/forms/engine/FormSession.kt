/**
 * IGNORE_COPYRIGHT: Ground is a Google-developed open-source project (The Ground Authors) Copyright
 * 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.engine

import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState

/**
 * Stateful controller managing an active form entry session over [FormEngine].
 *
 * Holds the latest [FormState] snapshot, notifies registered state listeners on every mutation, and
 * provides typed convenience methods for updating field values, adding/removing repeat instances,
 * switching languages, and finalizing the record.
 */
class FormSession(
  val formDef: FormDef,
  existingRecord: RecordInstance? = null,
  isFirstLoad: Boolean = existingRecord == null,
  activeLanguage: String? = null,
  var environment: FormEnvironment = FormEnvironment.DEFAULT,
) {
  private val listeners = mutableListOf<(FormState) -> Unit>()

  /** The latest evaluated [FormState] snapshot. */
  var state: FormState =
    FormEngine.initialize(
      formDef = formDef,
      existingRecord = existingRecord,
      isFirstLoad = isFirstLoad,
      activeLanguage = activeLanguage,
      environment = environment,
    )
    private set

  /** Registers a listener invoked whenever [state] transitions to a new snapshot. */
  fun addStateListener(listener: (FormState) -> Unit): () -> Unit {
    listeners.add(listener)
    return { listeners.remove(listener) }
  }

  /** Updates a field at [path] with a raw [FieldValue] (or `null` to clear). */
  fun updateField(path: String, value: FieldValue?): FormState {
    val next = FormEngine.updateFieldValue(state, path, value, environment)
    publish(next)
    return next
  }

  /** Updates a field at [path] with a string answer. */
  fun updateString(path: String, value: String): FormState =
    updateField(path, FieldValue(scalar_value = TypedValue(string_value = value)))

  /** Updates a field at [path] with an integer answer. */
  fun updateInt(path: String, value: Int): FormState =
    updateField(path, FieldValue(scalar_value = TypedValue(int32_value = value)))

  /** Updates a field at [path] with a 64-bit integer answer. */
  fun updateLong(path: String, value: Long): FormState =
    updateField(path, FieldValue(scalar_value = TypedValue(int64_value = value)))

  /** Updates a field at [path] with a floating-point answer. */
  fun updateDouble(path: String, value: Double): FormState =
    updateField(path, FieldValue(scalar_value = TypedValue(double_value = value)))

  /** Updates a field at [path] with a boolean answer. */
  fun updateBoolean(path: String, value: Boolean): FormState =
    updateField(path, FieldValue(scalar_value = TypedValue(bool_value = value)))

  /** Updates a multi-select (`CONTROL_SELECT_MULTIPLE` or `CONTROL_RANK`) field at [path]. */
  fun updateMultiSelect(path: String, choices: List<String>): FormState =
    updateField(
      path,
      FieldValue(
        list_value = TypedValueList(values = choices.map { TypedValue(string_value = it) })
      ),
    )

  /** Updates a geospatial point (`TYPE_GEOPOINT`) field at [path]. */
  fun updateGeoPoint(
    path: String,
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double = 0.0,
    accuracyMeters: Double = 0.0,
  ): FormState =
    updateField(
      path,
      FieldValue(
        scalar_value =
          TypedValue(
            geopoint_value =
              GeoPoint(
                latitude = latitude,
                longitude = longitude,
                altitude_meters = altitudeMeters,
                accuracy_meters = accuracyMeters,
              )
          )
      ),
    )

  /** Clears the answer of the field at [path]. */
  fun clearField(path: String): FormState = updateField(path, null)

  /** Appends a new repeat instance to the repeat group at [repeatPath]. */
  fun addRepeatInstance(repeatPath: String): FormState {
    val next = FormEngine.addRepeatInstance(state, repeatPath, environment)
    publish(next)
    return next
  }

  /** Removes the 1-based [repeatIndex] instance from the repeat group at [repeatPath]. */
  fun removeRepeatInstance(repeatPath: String, repeatIndex: Int): FormState {
    val next = FormEngine.removeRepeatInstance(state, repeatPath, repeatIndex, environment)
    publish(next)
    return next
  }

  /** Switches the active translation language and re-evaluates all labels and choice options. */
  fun setLanguage(language: String): FormState {
    val next = FormEngine.setLanguage(state, language, environment)
    publish(next)
    return next
  }

  /** Finalizes and validates the current record, updating `end` timestamp preloads. */
  fun finalize(): FinalizationResult {
    val res = FormEngine.finalize(state, environment)
    when (res) {
      is FinalizationResult.Success -> publish(res.state)
      is FinalizationResult.ValidationFailure -> publish(res.state)
    }
    return res
  }

  private fun publish(next: FormState) {
    state = next
    for (listener in listeners.toList()) {
      listener(next)
    }
  }
}
