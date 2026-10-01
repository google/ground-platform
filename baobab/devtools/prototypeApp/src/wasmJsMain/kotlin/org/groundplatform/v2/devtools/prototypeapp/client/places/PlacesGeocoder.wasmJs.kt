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
package org.groundplatform.v2.devtools.prototypeapp.client.places

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createPlacesHttpClient(): HttpClient? = HttpClient(Js)

/**
 * Reads the token `index.html` resolves from the `mapbox_token` URL parameter or local storage and
 * sets on Mapbox GL JS.
 */
internal actual fun mapboxAccessToken(): String? = jsMapboxAccessToken().ifEmpty { null }

@JsFun(
  "() => (window.mapboxgl && window.mapboxgl.accessToken) ? String(window.mapboxgl.accessToken) : ''"
)
private external fun jsMapboxAccessToken(): String
