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
package org.openforis.collectone.v2.server.userdata

import com.google.api.client.json.webtoken.JsonWebToken
import com.google.auth.oauth2.TokenVerifier
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The authenticated user making an RPC.
 *
 * @property isDefault true for the development fallback user, when a `dev` mode call doesn't name a
 *   user. Request fields such as `user_id` still apply then, so fake clients keep working.
 * @property isAdmin true for administrators named by the deployment (`ADMIN_EMAILS`, or every
 *   signed-in user in `dev` mode); see [Access.isAdmin] for the others.
 */
data class Caller(
  val userId: String,
  val email: String,
  val emailVerified: Boolean,
  val displayName: String = "",
  val photoUrl: String = "",
  val isDefault: Boolean = false,
  val isAdmin: Boolean = false,
) {
  /** [email] trimmed and lowercased, as stored in `EmailNormalized` columns. */
  val normalizedEmail: String
    get() = email.trim().lowercase()
}

/** Carries the [Caller] of an RPC into the coroutine serving it. */
class CallerElement(val caller: Caller?) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<CallerElement>
}

/** The caller of the RPC being served, or null outside an RPC (for example in tests). */
suspend fun currentCaller(): Caller? = coroutineContext[CallerElement]?.caller

/** Authenticates RPCs from their request headers. */
fun interface Authenticator {
  /** The caller identified by [headers]; throws an `UNAUTHENTICATED` [StatusException] if none. */
  fun authenticate(headers: Metadata): Caller

  companion object {
    /** gRPC [Context] key under which [interceptor] stores the caller. */
    val CALLER: Context.Key<Caller> = Context.key("caller")

    /** Header naming a development user's ID in `dev` mode. */
    const val DEV_USER_ID_HEADER = "x-dev-user-id"
    const val DEV_USER_EMAIL_HEADER = "x-dev-user-email"
    const val DEV_USER_NAME_HEADER = "x-dev-user-name"

    /**
     * Role a `dev` mode call simulates: `admin` (an administrator, who may do anything) or `user`
     * (access only through ownership, ACL entries, memberships, and sharing).
     */
    const val DEV_ROLE_HEADER = "x-dev-role"

    /** Development fallback user: the sample organizer the fake clients sign in as. */
    val DEFAULT_DEV_CALLER =
      Caller(
        userId = "uid-maya-lin",
        email = "maya.lin@groundplatform.org",
        emailVerified = true,
        displayName = "Maya Lin",
        isDefault = true,
      )

    /**
     * Development mode: a call with an `Authorization` header is authenticated by [firebase] (when
     * set), so signed-in web apps run as their real user, as an administrator unless
     * [DEV_ROLE_HEADER] is `user`. With `x-dev-user-*` headers, [DEV_ROLE_HEADER] `admin` makes the
     * named user an administrator. Otherwise it trusts the `x-dev-user-*` headers, and falls back
     * to [DEFAULT_DEV_CALLER] when they're absent. Never use it with real data.
     */
    fun dev(firebase: Authenticator? = null): Authenticator = Authenticator { headers ->
      val userId = headers.get(key(DEV_USER_ID_HEADER))?.trim().orEmpty()
      val role = headers.get(key(DEV_ROLE_HEADER))?.trim()?.lowercase()
      if (firebase != null && headers.containsKey(key("authorization"))) {
        // Development data is shared sample data, so signed-in developers see all of it unless
        // they simulate a regular user.
        firebase.authenticate(headers).copy(isAdmin = role != "user")
      } else if (userId.isEmpty()) {
        DEFAULT_DEV_CALLER
      } else {
        Caller(
          userId = userId,
          email = headers.get(key(DEV_USER_EMAIL_HEADER)).orEmpty(),
          emailVerified = true,
          displayName = headers.get(key(DEV_USER_NAME_HEADER)).orEmpty(),
          isAdmin = role == "admin",
        )
      }
    }

    /**
     * Verifies Firebase Authentication ID tokens (`Authorization: Bearer <token>`) issued for
     * Firebase project [projectId], and rejects anonymous accounts. Users whose verified email is
     * in [adminEmails] are administrators.
     */
    fun firebase(
      projectId: String,
      verifier: TokenVerifier = firebaseVerifier(projectId),
      adminEmails: Set<String> = emptySet(),
    ) = Authenticator { headers ->
      val authorization = headers.get(key("authorization")).orEmpty()
      val token = authorization.removePrefix("Bearer ").trim()
      if (!authorization.startsWith("Bearer ") || token.isEmpty()) {
        throw unauthenticated("Sign in: missing Firebase ID token")
      }
      val payload =
        try {
          verifier.verify(token).payload
        } catch (e: TokenVerifier.VerificationException) {
          throw unauthenticated("Invalid Firebase ID token: ${e.message}")
        } catch (e: IllegalArgumentException) {
          throw unauthenticated("Malformed Firebase ID token")
        } catch (e: java.io.IOException) {
          throw unauthenticated("Malformed Firebase ID token")
        }
      callerOf(payload).let { caller ->
        val admin = caller.emailVerified && caller.normalizedEmail in adminEmails
        if (admin) caller.copy(isAdmin = true) else caller
      }
    }

    /** A verifier for Firebase ID tokens of [projectId], using Google's published keys. */
    fun firebaseVerifier(projectId: String): TokenVerifier =
      TokenVerifier.newBuilder()
        .setIssuer("https://securetoken.google.com/$projectId")
        .setAudience(projectId)
        .setCertificatesLocation(FIREBASE_CERTIFICATES)
        .build()

    private const val FIREBASE_CERTIFICATES =
      "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"

    private fun callerOf(payload: JsonWebToken.Payload): Caller {
      val firebase = payload["firebase"] as? Map<*, *>
      if (firebase?.get("sign_in_provider") == "anonymous") {
        throw unauthenticated("Anonymous accounts can't use this service; sign in")
      }
      val userId = payload.subject.orEmpty()
      if (userId.isEmpty()) throw unauthenticated("Firebase ID token has no subject")
      return Caller(
        userId = userId,
        email = payload["email"] as? String ?: "",
        emailVerified = payload["email_verified"] == true,
        displayName = payload["name"] as? String ?: "",
        photoUrl = payload["picture"] as? String ?: "",
      )
    }

    private fun unauthenticated(message: String) =
      StatusException(Status.UNAUTHENTICATED.withDescription(message))

    private fun key(name: String): Metadata.Key<String> =
      Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)
  }
}

/**
 * Authenticates every call with [authenticator] and stores the caller in [Authenticator.CALLER].
 */
fun Authenticator.interceptor(): ServerInterceptor =
  object : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
      call: ServerCall<ReqT, RespT>,
      headers: Metadata,
      next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
      val caller =
        try {
          authenticate(headers)
        } catch (e: StatusException) {
          call.close(e.status, Metadata())
          return object : ServerCall.Listener<ReqT>() {}
        }
      val context = Context.current().withValue(Authenticator.CALLER, caller)
      return Contexts.interceptCall(context, call, headers, next)
    }
  }
