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
package org.openforis.collectone.v2.app.ui.text

import org.openforis.collectone.v2.app.domain.model.ClusterMarkerStatus
import org.openforis.collectone.v2.app.domain.model.ClusterMarkerSymbolGroup
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_cluster_marker_status_completed
import org.openforis.collectone.v2.app.resources.domain_cluster_marker_status_in_progress
import org.openforis.collectone.v2.app.resources.domain_cluster_marker_status_no_marker_symbol
import org.openforis.collectone.v2.app.resources.domain_cluster_marker_status_other_symbol
import org.openforis.collectone.v2.app.resources.domain_cluster_marker_status_pending

/** Status of this map cluster group, e.g. `"In progress"`, or `"Symbol ★"` for other symbols. */
val ClusterMarkerSymbolGroup.statusText: UiText
  get() =
    when (status) {
      ClusterMarkerStatus.COMPLETED ->
        UiText.Resource(Res.string.domain_cluster_marker_status_completed)
      ClusterMarkerStatus.IN_PROGRESS ->
        UiText.Resource(Res.string.domain_cluster_marker_status_in_progress)
      ClusterMarkerStatus.PENDING ->
        UiText.Resource(Res.string.domain_cluster_marker_status_pending)
      ClusterMarkerStatus.NO_MARKER_SYMBOL ->
        UiText.Resource(Res.string.domain_cluster_marker_status_no_marker_symbol)
      ClusterMarkerStatus.OTHER_SYMBOL ->
        UiText.Resource(Res.string.domain_cluster_marker_status_other_symbol, listOf(markerSymbol))
    }
