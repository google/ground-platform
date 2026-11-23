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
package org.openforis.collectone.v2.app.ui.common

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
import org.jetbrains.compose.resources.stringResource
import org.openforis.collectone.v2.app.domain.model.GeospatialEntityItem
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.app_geo_id_label
import org.openforis.collectone.v2.app.resources.app_geo_id_pending_sync

/** Inline content ID of the pending-sync icon that [appendGeoId] places after a GeoID. */
const val GeoIdPendingSyncIconId = "geoIdPendingSync"

/** Accessible description of the pending-sync icon shown next to a provisional GeoID. */
@Composable
fun geoIdPendingSyncDescription(): String = stringResource(Res.string.app_geo_id_pending_sync)

/**
 * Icon marking a GeoID that is still a local candidate ([GeospatialEntityItem.isGeoIdPendingSync]):
 * the record hasn't synced, so the AgStack registry hasn't confirmed which GeoID it assigns.
 */
@Composable
fun GeoIdPendingSyncIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 16.dp,
  contentDescription: String? = geoIdPendingSyncDescription(),
) {
  Icon(
    imageVector = Icons.Outlined.CloudSync,
    contentDescription = contentDescription,
    tint = tint,
    modifier = modifier.size(size),
  )
}

/**
 * Appends [entity]'s GeoID followed, while it is pending sync, by an inline [GeoIdPendingSyncIcon]
 * announced as [pendingSyncDescription] (see [geoIdPendingSyncDescription]). Render the result with
 * [geoIdInlineContent].
 */
fun AnnotatedString.Builder.appendGeoId(
  entity: GeospatialEntityItem,
  pendingSyncDescription: String,
) {
  append(entity.geoId)
  if (entity.isGeoIdPendingSync) {
    append('\u00A0') // No-break space: keep the icon on the same line as the GeoID.
    // The alternate text is what screen readers announce in place of the icon.
    appendInlineContent(GeoIdPendingSyncIconId, "($pendingSyncDescription)")
  }
}

/** Inline content for text built with [appendGeoId], drawing the icon in [tint]. */
fun geoIdInlineContent(tint: Color): Map<String, InlineTextContent> =
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
 * Text before and after the GeoID in the localized `“GeoID: <id>”` label, for [GeoIdText]'s
 * `prefix` and `suffix` (the GeoID itself is drawn separately to append the pending-sync icon).
 */
@Composable
fun geoIdLabelAffixes(): Pair<String, String> {
  val label = stringResource(Res.string.app_geo_id_label, GEO_ID_PLACEHOLDER)
  return label.substringBefore(GEO_ID_PLACEHOLDER) to label.substringAfter(GEO_ID_PLACEHOLDER, "")
}

/** Stand-in for the GeoID when splitting the localized GeoID label around it. */
private const val GEO_ID_PLACEHOLDER = "\u0000"

/**
 * Text containing [entity]'s GeoID between [prefix] and [suffix], with a [GeoIdPendingSyncIcon]
 * right after the GeoID while it is pending sync.
 */
@Composable
fun GeoIdText(
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
        appendGeoId(entity, geoIdPendingSyncDescription())
        append(suffix)
      },
    inlineContent = geoIdInlineContent(tint = color),
    style = style,
    color = color,
    modifier = modifier,
  )
}
