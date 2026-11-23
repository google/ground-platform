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
package org.openforis.collectone.v2.app.domain.repository

/** Loads satellite map thumbnail imagery for survey cards. */
interface SurveyMapThumbnailRepository {
  /** The public Mapbox access token (`pk.*`) that Mapbox Static Images URLs need, if configured. */
  val mapboxPublicToken: String?

  /**
   * The encoded image bytes (`image/jpeg` or `image/png`) of the static map or tile at [url], or
   * `null` when offline or unavailable.
   */
  suspend fun fetchTile(url: String): ByteArray?
}
