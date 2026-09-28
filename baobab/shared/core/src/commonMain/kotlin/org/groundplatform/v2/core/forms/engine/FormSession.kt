/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.groundplatform.v2.core.forms.engine

import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.TypedValue
import groundplatform.v2.forms.TypedValueList
import org.groundplatform.v2.core.forms.media.MediaAttachment
import org.groundplatform.v2.core.forms.media.MediaAttachmentRejectedException
import org.groundplatform.v2.core.forms.media.MediaCapture
import org.groundplatform.v2.core.forms.model.FinalizationResult
import org.groundplatform.v2.core.forms.model.FormState

/**
 * Stateful controller managing an active form entry session over [FormEngine].
 *
 * Holds the latest [FormState] snapshot, notifies registered state listeners on every mutation, and
 * provides typed convenience methods for updating field values, adding/removing repeat instances,
 * switching languages, and finalizing the record.
 *
 * Also holds the bytes of photo, video, and audio answers ([attachments]); the record itself stores
 * only each attachment's file name. Pass [existingAttachments] when resuming a saved draft.
 */
class FormSession(
  val formDef: FormDef,
  existingRecord: RecordInstance? = null,
  isFirstLoad: Boolean = existingRecord == null,
  activeLanguage: String? = null,
  var environment: FormEnvironment = FormEnvironment.DEFAULT,
  existingAttachments: Collection<MediaAttachment> = emptyList(),
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

  // ---------------------------------------------------------------------------
  // Photo / video / audio attachments
  // ---------------------------------------------------------------------------

  private val attachmentStore: MutableMap<String, MediaAttachment> =
    linkedMapOf<String, MediaAttachment>().apply {
      existingAttachments.forEach { put(it.fileName, it) }
    }

  /**
   * All media attachments held by this session, keyed by file name. May include attachments whose
   * question is currently non-relevant; use [referencedAttachments] for the submission set.
   */
  val attachments: Map<String, MediaAttachment>
    get() = attachmentStore.toMap()

  /** Returns the attachment currently answering the media question at [path], if loaded. */
  fun attachmentFor(path: String): MediaAttachment? {
    val fileName = MediaCapture.attachmentFileName(state.findFieldState(path)?.value) ?: return null
    return attachmentStore[fileName]
  }

  /**
   * Answers the photo / video / audio question at [path] with [attachment].
   *
   * The record stores only [MediaAttachment.fileName] (per the OpenRosa submission convention); the
   * bytes are retained by the session. Any attachment previously answering this question is
   * discarded if no other question references it.
   *
   * @throws MediaAttachmentRejectedException if [path] is not a media question, or the attachment's
   *   MIME type is not accepted by the question's `mediatype`.
   */
  fun attachMedia(path: String, attachment: MediaAttachment): FormState {
    val control =
      MediaCapture.findMediaControl(state, path)
        ?: throw MediaAttachmentRejectedException("No photo, video, or audio question at $path")
    val spec = control.mediaCapture!!
    if (!spec.accepts(attachment.mimeType)) {
      throw MediaAttachmentRejectedException(
        "This question accepts ${spec.acceptedMediaType}, not ${attachment.mimeType}"
      )
    }
    if (attachment.fileName.isBlank() || attachment.fileName.contains('/')) {
      throw MediaAttachmentRejectedException(
        "Invalid attachment file name '${attachment.fileName}'"
      )
    }
    val previous = MediaCapture.attachmentFileName(control.fieldState.rawValue)
    attachmentStore[attachment.fileName] = attachment
    val next = updateString(control.canonicalPath, attachment.fileName)
    if (previous != null && previous != attachment.fileName) {
      dropIfUnreferenced(previous)
    }
    return next
  }

  /** Clears the media question at [path] and discards its attachment if no longer referenced. */
  fun removeMedia(path: String): FormState {
    val previous = MediaCapture.attachmentFileName(state.findFieldState(path)?.rawValue)
    val next = clearField(path)
    if (previous != null) dropIfUnreferenced(previous)
    return next
  }

  /**
   * Attachments referenced by relevant, answered media questions in the current record: the files
   * that must be sent with a submission. File names with no loaded bytes (e.g. a draft restored
   * without its media) are omitted; see [missingAttachmentFileNames].
   */
  fun referencedAttachments(): List<MediaAttachment> =
    MediaCapture.referencedFileNames(state).mapNotNull { attachmentStore[it] }

  /** File names referenced by relevant media questions whose bytes are not loaded. */
  fun missingAttachmentFileNames(): Set<String> =
    MediaCapture.referencedFileNames(state).filterTo(linkedSetOf()) { it !in attachmentStore }

  private fun dropIfUnreferenced(fileName: String) {
    val stillReferenced =
      state.fieldStates.values.any { MediaCapture.attachmentFileName(it.rawValue) == fileName }
    if (!stillReferenced) attachmentStore.remove(fileName)
  }

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
