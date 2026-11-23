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
package org.openforis.collectone.v2.web.domain.model.editor

import org.openforis.collectone.v2.app.domain.model.MessageKey

/** Why sample plots can't be generated; arguments are listed per entry. */
enum class SamplingMessage : MessageKey {
  /** No survey area to draw plots from, and no strata layer chosen instead. */
  AREA_MISSING,
  STRATA_DELETED,
  STRATA_NOT_POLYGON,
  /** The strata layer's display name. */
  STRATA_NO_POLYGONS,
  NO_DESIGN,
  /** The sampling engine refused the design without saying why. */
  CANT_GENERATE_DESIGN,
  GENERATE_FAILED,
  /** The error's message. */
  GENERATE_FAILED_WITH_REASON,
  NO_PLOTS_FIT,
}
