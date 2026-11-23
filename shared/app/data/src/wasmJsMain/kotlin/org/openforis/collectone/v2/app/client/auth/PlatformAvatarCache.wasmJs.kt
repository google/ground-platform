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

import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// The avatar cache of the sign-in bridge (firebase-auth-bridge.js), which keeps profile photos as
// base64 so they show offline and without a second download.

@JsFun(
  "() => typeof window !== 'undefined' && !!window.AppFirebaseAuthBridge && " +
    "!!window.AppFirebaseAuthBridge.cachedAvatarBase64"
)
private external fun hasAvatarCacheBridgeJs(): Boolean

@JsFun("(url) => window.AppFirebaseAuthBridge.cachedAvatarBase64(url)")
private external fun cachedAvatarBase64Js(url: JsString): JsString?

@JsFun(
  "(url, callback) => window.AppFirebaseAuthBridge.fetchAvatarBase64(" +
    "url, (b64) => callback(b64))"
)
private external fun fetchAvatarBase64Js(url: JsString, callback: (JsString?) -> Unit)

private const val AVATAR_FETCH_TIMEOUT_MS = 5_000L

internal actual fun platformCachedAvatar(url: String): ByteArray? {
  if (!hasAvatarCacheBridgeJs()) return null
  val base64 = cachedAvatarBase64Js(url.toJsString())?.toString()?.takeIf { it.isNotBlank() }
  return base64?.let(::decodeBase64OrNull)
}

internal actual suspend fun platformFetchAvatar(url: String): ByteArray? =
  platformCachedAvatar(url)
    ?: fetchThroughBridge(url)?.let { decodeBase64OrNull(it) ?: ByteArray(0) }

/** The photo at [url] as base64 from the bridge, or `null` without a bridge or an answer. */
private suspend fun fetchThroughBridge(url: String): String? {
  if (!hasAvatarCacheBridgeJs()) return null
  val base64 =
    withTimeoutOrNull(AVATAR_FETCH_TIMEOUT_MS) {
      suspendCancellableCoroutine { continuation ->
        fetchAvatarBase64Js(url.toJsString()) { result -> continuation.resume(result?.toString()) }
      }
    }
  return base64?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64OrNull(base64: String): ByteArray? =
  try {
    Base64.decode(base64)
  } catch (_: IllegalArgumentException) {
    null
  }
