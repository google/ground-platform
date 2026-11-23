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
package org.openforis.collectone.v2.app.domain.repository

/**
 * Loads the encoded image bytes (PNG, JPEG, ...) of user profile photos, by photo URL. The
 * presentation decodes and caches the images; implementations only fetch the bytes.
 */
interface ProfileImageLoader {
  /**
   * The bytes of the photo at [url] if the platform already keeps them (e.g. the browser's avatar
   * cache), without waiting for the network; `null` otherwise.
   */
  fun cachedBytes(url: String): ByteArray?

  /** The bytes of the photo at [url], or `null` when it can't be loaded. Never throws. */
  suspend fun fetchBytes(url: String): ByteArray?

  /** A [ProfileImageLoader] that loads nothing, so profile pictures fall back to initials. */
  object None : ProfileImageLoader {
    override fun cachedBytes(url: String): ByteArray? = null

    override suspend fun fetchBytes(url: String): ByteArray? = null
  }
}
