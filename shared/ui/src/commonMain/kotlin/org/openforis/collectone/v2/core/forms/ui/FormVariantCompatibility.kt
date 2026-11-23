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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.ViewComponent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.core.forms.engine.CompiledForm
import org.openforis.collectone.v2.core.forms.media.MediaCapture
import org.openforis.collectone.v2.core.forms.media.MediaCaptureKind
import org.openforis.collectone.v2.core.forms.model.ComponentState
import org.openforis.collectone.v2.core.forms.ui.controls.Surface
import org.openforis.collectone.v2.core.forms.ui.resources.Res
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_audio
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_file
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_gps_line
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_gps_location
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_gps_polygon
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_line
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_location
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_photo
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_polygon
import org.openforis.collectone.v2.core.forms.ui.resources.forms_incompatible_type_video
import org.openforis.collectone.v2.core.forms.ui.resources.forms_variant_google_earth
import org.openforis.collectone.v2.core.forms.ui.resources.forms_variant_incompatible_warning
import org.openforis.collectone.v2.core.forms.ui.resources.forms_variant_web

/**
 * Runtime form surface variant where a form is rendered.
 *
 * Used to gracefully skip rendering input controls that are already present in a saved survey but
 * cannot be answered on the active variant (for example, geometry and media upload questions in
 * [GOOGLE_EARTH] KML balloons).
 */
enum class FormVariant {
  /** Native/offline mobile app runner (`MobileFormRunner`), where all field types are supported. */
  MOBILE,

  /** Web dashboard / browser form runner (`CompactFormRunner`). */
  WEB,

  /** Embedded KML placemark balloon form runner (`BalloonFormScreen`) in Google Earth. */
  GOOGLE_EARTH,
}

/** Active [FormVariant] for form question cards; defaults to [FormVariant.MOBILE]. */
val LocalFormVariant = compositionLocalOf { FormVariant.MOBILE }

/**
 * Question type label displayed in [VariantIncompatibleQuestionWarning] when a question cannot be
 * answered on the active [FormVariant].
 */
enum class IncompatibleQuestionType(val labelRes: StringResource) {
  GPS_LOCATION(Res.string.forms_incompatible_type_gps_location),
  GPS_LINE(Res.string.forms_incompatible_type_gps_line),
  GPS_POLYGON(Res.string.forms_incompatible_type_gps_polygon),
  LOCATION(Res.string.forms_incompatible_type_location),
  LINE(Res.string.forms_incompatible_type_line),
  POLYGON(Res.string.forms_incompatible_type_polygon),
  PHOTO(Res.string.forms_incompatible_type_photo),
  VIDEO(Res.string.forms_incompatible_type_video),
  AUDIO(Res.string.forms_incompatible_type_audio),
  FILE(Res.string.forms_incompatible_type_file),
}

/**
 * Returns the [IncompatibleQuestionType] if [control] cannot be answered on [variant], or `null`
 * when [control] is compatible with [variant].
 */
fun incompatibleQuestionTypeForVariant(
  control: ComponentState.ControlState,
  variant: FormVariant,
): IncompatibleQuestionType? =
  incompatibleQuestionTypeForVariant(
    controlDef = control.controlDef,
    dataType = control.fieldState.dataType,
    binding = control.fieldState.binding,
    mediaKind = control.mediaCapture?.kind,
    variant = variant,
  )

/**
 * Static variant compatibility check over a control's [ControlDef], resolved [DataType], and
 * optional [FieldBinding].
 */
fun incompatibleQuestionTypeForVariant(
  controlDef: ControlDef,
  dataType: DataType,
  binding: FieldBinding? = null,
  mediaKind: MediaCaptureKind? = null,
  variant: FormVariant,
): IncompatibleQuestionType? =
  when (variant) {
    FormVariant.MOBILE,
    FormVariant.WEB -> null
    FormVariant.GOOGLE_EARTH ->
      when {
        dataType == DataType.TYPE_GEOPOINT -> IncompatibleQuestionType.LOCATION
        dataType == DataType.TYPE_GEOTRACE -> IncompatibleQuestionType.LINE
        dataType == DataType.TYPE_GEOSHAPE -> IncompatibleQuestionType.POLYGON
        controlDef.type == ControlType.CONTROL_UPLOAD || dataType == DataType.TYPE_BINARY -> {
          val resolvedKind = mediaKind ?: MediaCapture.resolveSpec(controlDef, binding)?.kind
          when (resolvedKind) {
            MediaCaptureKind.PHOTO -> IncompatibleQuestionType.PHOTO
            MediaCaptureKind.VIDEO -> IncompatibleQuestionType.VIDEO
            MediaCaptureKind.AUDIO -> IncompatibleQuestionType.AUDIO
            MediaCaptureKind.FILE,
            null -> IncompatibleQuestionType.FILE
          }
        }
        else -> null
      }
  }

/**
 * Collects the normalized relative paths of all controls in [compiledForm] that are incompatible
 * with [variant], including controls nested inside groups or repeat definitions.
 */
fun collectIncompatibleFieldPaths(
  compiledForm: CompiledForm,
  variant: FormVariant,
): Set<String> {
  if (variant == FormVariant.MOBILE) return emptySet()
  val paths = linkedSetOf<String>()
  fun visit(components: List<ViewComponent>, parentRelPath: String) {
    for (comp in components) {
      val controlDef = comp.control
      val groupDef = comp.group
      val repeatDef = comp.repeat
      when {
        controlDef != null -> {
          val relPath =
            compiledForm.resolveComponentRelativePath(controlDef.field_ref, parentRelPath)
          if (relPath.isNotEmpty()) {
            val dataType = compiledForm.resolveDataType(relPath)
            val binding = compiledForm.bindingsByRelativePath[relPath]?.binding
            if (
              incompatibleQuestionTypeForVariant(
                controlDef = controlDef,
                dataType = dataType,
                binding = binding,
                variant = variant,
              ) != null
            ) {
              paths.add(relPath)
            }
          }
        }
        groupDef != null -> {
          val groupRel =
            compiledForm.resolveComponentRelativePath(groupDef.field_ref, parentRelPath)
          visit(groupDef.components, groupRel)
        }
        repeatDef != null -> {
          val repeatRel =
            compiledForm.resolveComponentRelativePath(repeatDef.field_ref, parentRelPath)
          visit(repeatDef.components, repeatRel)
        }
      }
    }
  }
  visit(compiledForm.formDef.view?.components ?: emptyList(), "")
  return paths
}

/** Test tag prefix for [VariantIncompatibleQuestionWarning] banners, followed by `fieldPath`. */
const val VARIANT_INCOMPATIBLE_WARNING_TAG_PREFIX = "variant_incompatible_warning_"

/**
 * User-friendly fallback notice rendered in place of an incompatible input widget when a saved
 * survey already contains a question that cannot be answered on [variant].
 */
@Composable
fun VariantIncompatibleQuestionWarning(
  incompatibleType: IncompatibleQuestionType,
  variant: FormVariant,
  fieldPath: String,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val variantLabel =
    when (variant) {
      FormVariant.WEB -> stringResource(Res.string.forms_variant_web)
      FormVariant.GOOGLE_EARTH -> stringResource(Res.string.forms_variant_google_earth)
      FormVariant.MOBILE -> ""
    }
  val warningText =
    stringResource(
      Res.string.forms_variant_incompatible_warning,
      stringResource(incompatibleType.labelRes),
      variantLabel,
    )
  Surface(
    modifier =
      modifier.fillMaxWidth().testTag("$VARIANT_INCOMPATIBLE_WARNING_TAG_PREFIX$fieldPath"),
    shape = MaterialTheme.shapes.small,
    color = colors.tertiaryContainer,
    contentColor = colors.onTertiaryContainer,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = colors.onTertiaryContainer,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = warningText,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onTertiaryContainer,
      )
    }
  }
}
