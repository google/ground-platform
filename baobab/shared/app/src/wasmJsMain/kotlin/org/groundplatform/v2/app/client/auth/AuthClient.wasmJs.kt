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
package org.groundplatform.v2.app.client.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@JsFun(
  "() => typeof window !== 'undefined' && !!window.GroundFirebaseAuthBridge && " +
    "window.GroundFirebaseAuthBridge.isConfigured()"
)
private external fun isFirebaseConfigured(): Boolean

@JsFun(
  "(onChange, onError) => window.GroundFirebaseAuthBridge.observe(" +
    "(json) => onChange(json), (error) => onError(error))"
)
private external fun observeUser(onChange: (JsString?) -> Unit, onError: (JsString) -> Unit)

@JsFun(
  "(callback) => window.GroundFirebaseAuthBridge.signInWithGoogle(" +
    "(json, error) => callback(json, error))"
)
private external fun signInWithGoogleJs(callback: (JsString?, JsString?) -> Unit)

@JsFun("(callback) => window.GroundFirebaseAuthBridge.signOut((error) => callback(error))")
private external fun signOutJs(callback: (JsString?) -> Unit)

@JsFun(
  "(force, callback) => window.GroundFirebaseAuthBridge.idToken(" +
    "force, (token, error) => callback(token, error))"
)
private external fun idTokenJs(force: Boolean, callback: (JsString?, JsString?) -> Unit)

/** Firebase Authentication when the page configures it (`firebase-config.js`), otherwise null. */
actual fun platformAuthClient(): AuthClient? =
  if (isFirebaseConfigured()) FirebaseWebAuthClient() else null

/**
 * [AuthClient] backed by the Firebase JS SDK through `firebase-auth-bridge.js`: signs in with a
 * Google account in a popup, restores the session the SDK persists in the browser, and returns the
 * user's Firebase ID token, which the user data service verifies.
 */
class FirebaseWebAuthClient : AuthClient {
  private val user = MutableStateFlow<AuthenticatedCollectorProfile?>(null)
  private val restored = CompletableDeferred<Unit>()

  override val currentUser: StateFlow<AuthenticatedCollectorProfile?> = user.asStateFlow()

  init {
    observeUser(
      onChange = { json ->
        user.value = json?.toString()?.let(::profileOf)
        restored.complete(Unit)
      },
      // Without Firebase (for example offline at startup) the user stays signed out.
      onError = { restored.complete(Unit) },
    )
  }

  override suspend fun awaitInitialState() = restored.await()

  override suspend fun signInWithGoogle(): AuthenticatedCollectorProfile {
    val json = suspendCancellableCoroutine { continuation ->
      signInWithGoogleJs { json, error ->
        if (json != null) continuation.resume(json.toString())
        else continuation.resumeWithException(authException(error?.toString()))
      }
    }
    return profileOf(json).also { user.value = it }
  }

  override suspend fun signOut() {
    suspendCancellableCoroutine { continuation ->
      signOutJs { error ->
        if (error == null) continuation.resume(Unit)
        else continuation.resumeWithException(authException(error.toString()))
      }
    }
    user.value = null
  }

  override suspend fun idToken(forceRefresh: Boolean): String? {
    restored.await()
    return suspendCancellableCoroutine { continuation ->
      idTokenJs(forceRefresh) { token, error ->
        if (error != null) continuation.resumeWithException(authException(error.toString()))
        else continuation.resume(token?.toString())
      }
    }
  }

  private fun profileOf(json: String): AuthenticatedCollectorProfile {
    val fields = Json.parseToJsonElement(json) as JsonObject
    fun field(name: String) = fields[name]?.jsonPrimitive?.content.orEmpty()
    val email = field("email")
    return AuthenticatedCollectorProfile(
      displayName = field("displayName").ifBlank { email.substringBefore('@') },
      email = email,
      organization = "",
    )
  }

  private fun authException(error: String?): AuthException {
    val message = error ?: "Sign-in failed"
    return AuthException(message, cancelled = CANCELLED_CODES.any { message.startsWith(it) })
  }

  private companion object {
    /** Firebase error codes for sign-in windows the user closed or replaced. */
    val CANCELLED_CODES =
      listOf("auth/popup-closed-by-user", "auth/cancelled-popup-request", "auth/user-cancelled")
  }
}
