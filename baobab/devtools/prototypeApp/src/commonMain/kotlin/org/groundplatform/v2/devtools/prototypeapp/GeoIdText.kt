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
package org.groundplatform.v2.devtools.prototypeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.groundplatform.v2.devtools.prototypeapp.domain.model.GeospatialEntityItem

/** Inline content ID of the pending-sync icon that [appendGeoId] places after a GeoID. */
internal const val GeoIdPendingSyncIconId = "geoIdPendingSync"

/** Accessible description of the pending-sync icon shown next to a provisional GeoID. */
internal const val GeoIdPendingSyncDescription = "Pending sync"

/**
 * Icon marking a GeoID that is still a local candidate ([GeospatialEntityItem.isGeoIdPendingSync]):
 * the record hasn't synced, so the AgStack registry hasn't confirmed which GeoID it assigns.
 */
@Composable
internal fun GeoIdPendingSyncIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 16.dp,
  contentDescription: String? = GeoIdPendingSyncDescription,
) {
  Icon(
    imageVector = Icons.Outlined.CloudSync,
    contentDescription = contentDescription,
    tint = tint,
    modifier = modifier.size(size),
  )
}

/**
 * Appends [entity]'s GeoID followed, while it is pending sync, by an inline [GeoIdPendingSyncIcon].
 * Render the result with [geoIdInlineContent].
 */
internal fun AnnotatedString.Builder.appendGeoId(entity: GeospatialEntityItem) {
  append(entity.geoId)
  if (entity.isGeoIdPendingSync) {
    append('\u00A0') // No-break space: keep the icon on the same line as the GeoID.
    // The alternate text is what screen readers announce in place of the icon.
    appendInlineContent(GeoIdPendingSyncIconId, "($GeoIdPendingSyncDescription)")
  }
}

/** Inline content for text built with [appendGeoId], drawing the icon in [tint]. */
internal fun geoIdInlineContent(tint: Color): Map<String, InlineTextContent> =
  mapOf(
    GeoIdPendingSyncIconId to
      InlineTextContent(
        Placeholder(
          width = 1.2.em,
          height = 1.2.em,
          placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
      ) {
        GeoIdPendingSyncIcon(
          tint = tint,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
        )
      }
  )

/**
 * Text containing [entity]'s GeoID between [prefix] and [suffix], with a [GeoIdPendingSyncIcon]
 * right after the GeoID while it is pending sync.
 */
@Composable
internal fun GeoIdText(
  entity: GeospatialEntityItem,
  style: TextStyle,
  color: Color,
  modifier: Modifier = Modifier,
  prefix: String = "",
  suffix: String = "",
) {
  Text(
    text =
      buildAnnotatedString {
        append(prefix)
        appendGeoId(entity)
        append(suffix)
      },
    inlineContent = geoIdInlineContent(tint = color),
    style = style,
    color = color,
    modifier = modifier,
  )
}
