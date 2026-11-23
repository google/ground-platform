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
package org.openforis.collectone.v2.core.forms.engine

import com.squareup.wire.ofEpochSecond
import kotlin.math.floor
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.xpath.model.TemporalUtils

/**
 * Fills `jr:preload` values and the OpenRosa `<orx:meta>` fields (`instanceID`, start/end times,
 * device properties) from the [FormEnvironment] clock, UUID generator and device properties.
 */
internal class PreloadExecutor(private val pass: EvaluationPass) {

  /**
   * Applies the preloads due when a record is opened ([isFirstLoad] for a new one) or finalized
   * ([isFinalize]) and returns the updated record node and metadata.
   */
  fun execute(
    rawNode: RecordNode,
    metadata: RecordMetadata,
    isFirstLoad: Boolean,
    isFinalize: Boolean,
  ): Pair<RecordNode, RecordMetadata> {
    val compiled = pass.compiled
    val environment = pass.environment
    var currentRaw = rawNode
    var currentMeta = metadata
    val epochMillis = environment.clockEpochMillis()
    val epochSeconds = floor(epochMillis / 1000.0).toLong()
    val nanos = ((epochMillis % 1000L + 1000L) % 1000L) * 1_000_000L
    val currentInstant = ofEpochSecond(epochSeconds, nanos)
    // Dates and times of day are the local calendar's; timestamps stay UTC instants.
    val localSeconds =
      floor((epochMillis + environment.utcOffsetMillis(epochMillis)) / 1000.0).toLong()
    val epochDays = floor(localSeconds / 86400.0).toLong()
    val currentDate = TemporalUtils.epochDaysToDate(epochDays)
    val remSec = ((localSeconds % 86400L) + 86400L) % 86400L
    val currentTime =
      com.google.type.TimeOfDay(
        hours = (remSec / 3600L).toInt(),
        minutes = ((remSec % 3600L) / 60L).toInt(),
        seconds = (remSec % 60L).toInt(),
        nanos = nanos.toInt(),
      )

    // Ensure RecordMetadata.instance_id is populated on first load
    if (isFirstLoad && currentMeta.instance_id.isEmpty()) {
      val rawUuid = environment.uuidGenerator()
      val formattedUid = if (rawUuid.startsWith("uuid:")) rawUuid else "uuid:$rawUuid"
      currentMeta = currentMeta.copy(instance_id = formattedUid)
    }
    if (isFirstLoad && currentMeta.start_time == null) {
      currentMeta = currentMeta.copy(start_time = currentInstant)
    }
    if (isFirstLoad && currentMeta.today == null) {
      currentMeta = currentMeta.copy(today = currentDate)
    }
    if (isFinalize) {
      currentMeta = currentMeta.copy(end_time = currentInstant)
    }

    // Populate device properties on metadata if provided in environment
    environment.getDeviceProperty("deviceid")?.let {
      if (currentMeta.device_id.isEmpty()) currentMeta = currentMeta.copy(device_id = it)
    }
    environment.getDeviceProperty("subscriberid")?.let {
      if (currentMeta.subscriber_id.isEmpty()) currentMeta = currentMeta.copy(subscriber_id = it)
    }
    environment.getDeviceProperty("simserial")?.let {
      if (currentMeta.sim_serial.isEmpty()) currentMeta = currentMeta.copy(sim_serial = it)
    }
    environment.getDeviceProperty("phonenumber")?.let {
      if (currentMeta.phone_number.isEmpty()) currentMeta = currentMeta.copy(phone_number = it)
    }

    // Evaluate explicit FieldBinding preloads
    for (compiledBinding in compiled.bindingsByRelativePath.values) {
      val b = compiledBinding.binding
      if (b.preload == PreloadType.PRELOAD_UNSPECIFIED) continue
      val param = b.preload_param.trim().lowercase()
      val canonicalPath = "/${compiled.rootName}/${compiledBinding.relativePath}"
      val existingVal =
        RecordMutator.getFieldValue(currentRaw, currentMeta, compiled.rootAliases, canonicalPath)
      val isExistingEmpty = RecordMutator.isFieldValueEmpty(existingVal)

      val preloadTypedValue: TypedValue? =
        when (b.preload) {
          PreloadType.PRELOAD_UID -> {
            if (isFirstLoad && isExistingEmpty) {
              val uid =
                currentMeta.instance_id.ifEmpty {
                  val generated = environment.uuidGenerator()
                  if (generated.startsWith("uuid:")) generated else "uuid:$generated"
                }
              TypedValue(string_value = uid)
            } else null
          }
          PreloadType.PRELOAD_TIMESTAMP -> {
            when (param) {
              "start",
              "" ->
                if (isFirstLoad && isExistingEmpty) TypedValue(timestamp_value = currentInstant)
                else null
              "end" ->
                if (isFinalize || isFirstLoad) TypedValue(timestamp_value = currentInstant)
                else null
              else -> null
            }
          }
          PreloadType.PRELOAD_DATE -> {
            if (isFirstLoad && isExistingEmpty) TypedValue(date_value = currentDate) else null
          }
          PreloadType.PRELOAD_TIME -> {
            if (isFirstLoad && isExistingEmpty) TypedValue(time_value = currentTime) else null
          }
          PreloadType.PRELOAD_PROPERTY -> {
            if (isFirstLoad && isExistingEmpty) {
              environment.getDeviceProperty(param)?.let { TypedValue(string_value = it) }
            } else null
          }
          PreloadType.PRELOAD_CONTEXT -> {
            if (isFirstLoad && isExistingEmpty) {
              environment.contextParams[b.preload_param]?.let { TypedValue(string_value = it) }
            } else null
          }
          PreloadType.PRELOAD_UNSPECIFIED -> null
        }

      if (preloadTypedValue != null) {
        val (nextRaw, nextMeta) =
          RecordMutator.setFieldValue(
            rootNode = currentRaw,
            metadata = currentMeta,
            rootAliases = compiled.rootAliases,
            canonicalPath = canonicalPath,
            newValue = FieldValue(scalar_value = preloadTypedValue),
          )
        currentRaw = nextRaw
        if (nextMeta != null) currentMeta = nextMeta
      }
    }

    return currentRaw to currentMeta
  }
}
