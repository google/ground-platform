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
package org.openforis.collectone.v2.server.web

import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.openforis.collectone.v2.core.ids.RecordIds
import org.openforis.collectone.v2.server.auth.Caller

/** Verified viewer credentials relayed once from `/balloon-auth.html` to a KML balloon iframe. */
data class RelayedBalloonCredentials(
  val caller: Caller,
  val token: String = "",
  val devRole: String = "",
  val devUserId: String = "",
  val devEmail: String = "",
  val devName: String = "",
)

/** Outcome of polling `POST /balloon/auth/claim` with a PKCE `codeVerifier`. */
sealed interface BalloonClaimOutcome {
  data object Pending : BalloonClaimOutcome

  data object InvalidVerifier : BalloonClaimOutcome

  data class Authenticated(val credentials: RelayedBalloonCredentials) : BalloonClaimOutcome
}

/**
 * Ephemeral in-memory relay for zero-credential KML balloons: short-lived, single-use RFC 7636 PKCE
 * (`S256`) auth handoffs between the top-level `/balloon-auth.html` sign-in tab and a sandboxed
 * Google Earth KML balloon iframe.
 */
class BalloonAuthStore(private val clockMillis: () -> Long = System::currentTimeMillis) {
  private data class PendingAuthEntry(
    val codeChallenge: String,
    val credentials: RelayedBalloonCredentials,
    val expiresAtMillis: Long,
    val failedAttempts: AtomicInteger = AtomicInteger(0),
  )

  private val pendingAuth = ConcurrentHashMap<String, PendingAuthEntry>()

  fun completeAuth(
    sessionId: String,
    codeChallenge: String,
    credentials: RelayedBalloonCredentials,
  ): Boolean {
    val cleanSession = sessionId.trim()
    val cleanChallenge = codeChallenge.trim()
    if (!RecordIds.isUuid(cleanSession) || !CHALLENGE_REGEX.matches(cleanChallenge)) {
      return false
    }
    val now = clockMillis()
    evictExpiredAuth(now)
    if (pendingAuth.size >= MAX_PENDING_AUTH) {
      pendingAuth.keys.firstOrNull()?.let(pendingAuth::remove)
    }
    pendingAuth[cleanSession] =
      PendingAuthEntry(
        codeChallenge = cleanChallenge,
        credentials = credentials,
        expiresAtMillis = now + AUTH_TTL_MILLIS,
      )
    return true
  }

  fun claimAuth(sessionId: String, codeVerifier: String): BalloonClaimOutcome {
    val cleanSession = sessionId.trim()
    val cleanVerifier = codeVerifier.trim()
    if (!RecordIds.isUuid(cleanSession) || cleanVerifier.length !in 32..128) {
      return BalloonClaimOutcome.InvalidVerifier
    }
    val now = clockMillis()
    evictExpiredAuth(now)
    val entry = pendingAuth[cleanSession]
    return when {
      entry == null -> BalloonClaimOutcome.Pending
      entry.expiresAtMillis <= now -> {
        pendingAuth.remove(cleanSession, entry)
        BalloonClaimOutcome.Pending
      }
      !matchesChallenge(entry.codeChallenge, cleanVerifier) -> {
        if (entry.failedAttempts.incrementAndGet() >= MAX_FAILED_CLAIM_ATTEMPTS) {
          pendingAuth.remove(cleanSession, entry)
        }
        BalloonClaimOutcome.InvalidVerifier
      }
      pendingAuth.remove(cleanSession, entry) ->
        BalloonClaimOutcome.Authenticated(entry.credentials)
      else -> BalloonClaimOutcome.Pending
    }
  }

  private fun matchesChallenge(expectedChallenge: String, verifier: String): Boolean {
    val expectedBytes = expectedChallenge.toByteArray(Charsets.US_ASCII)
    val actualBytes = sha256Base64Url(verifier).toByteArray(Charsets.US_ASCII)
    return MessageDigest.isEqual(expectedBytes, actualBytes)
  }

  private fun evictExpiredAuth(now: Long) {
    pendingAuth.entries.removeIf { (_, v) -> v.expiresAtMillis <= now }
  }

  companion object {
    const val AUTH_TTL_MILLIS: Long = 3 * 60 * 1000L
    private const val MAX_PENDING_AUTH = 2048
    private const val MAX_FAILED_CLAIM_ATTEMPTS = 3
    private val CHALLENGE_REGEX = Regex("^[A-Za-z0-9_-]{43,88}$")

    fun sha256Base64Url(verifier: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
  }
}
