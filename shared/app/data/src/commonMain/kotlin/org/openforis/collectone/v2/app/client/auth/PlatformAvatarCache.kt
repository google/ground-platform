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
package org.openforis.collectone.v2.app.client.auth

/**
 * The bytes of the profile photo at [url] from the platform's own avatar cache (the browser's
 * sign-in bridge), without waiting for the network; `null` when it has none.
 */
internal expect fun platformCachedAvatar(url: String): ByteArray?

/**
 * The bytes of the profile photo at [url], fetched through the platform's avatar cache, which keeps
 * them for [platformCachedAvatar]. `null` when the platform has no such cache or it couldn't load
 * the photo, so the caller falls back to HTTP; an empty array when the cache answered with
 * unreadable data.
 */
internal expect suspend fun platformFetchAvatar(url: String): ByteArray?
