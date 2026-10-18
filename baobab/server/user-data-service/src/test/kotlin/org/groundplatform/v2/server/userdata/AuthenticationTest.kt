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
package org.groundplatform.v2.server.userdata

import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.api.client.json.webtoken.JsonWebToken
import com.google.auth.oauth2.TokenVerifier
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import java.security.KeyPair
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [Authenticator.dev] headers and [Authenticator.firebase] ID token checks. */
class AuthenticationTest {
  private val keys: KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
  private val firebase =
    Authenticator.firebase(
      PROJECT,
      TokenVerifier.newBuilder()
        .setIssuer("https://securetoken.google.com/$PROJECT")
        .setAudience(PROJECT)
        .setPublicKey(keys.public)
        .build(),
    )

  @Test
  fun dev_withoutHeaders_isTheDefaultUser() {
    val caller = Authenticator.dev().authenticate(Metadata())

    assertEquals(Authenticator.DEFAULT_DEV_CALLER, caller)
    assertTrue(caller.isDefault)
  }

  @Test
  fun dev_headersNameTheUser() {
    val caller =
      Authenticator.dev()
        .authenticate(
          headers(
            Authenticator.DEV_USER_ID_HEADER to "uid-ana",
            Authenticator.DEV_USER_EMAIL_HEADER to "Ana@Example.org",
          )
        )

    assertEquals("uid-ana", caller.userId)
    assertEquals("ana@example.org", caller.normalizedEmail)
    assertFalse(caller.isDefault)
  }

  @Test
  fun dev_verifiesTokensWhenCallsSendThem() {
    val dev = Authenticator.dev(firebase = firebase)

    val signedIn = dev.authenticate(bearer(token("email" to "ana@example.org")))
    assertEquals("uid-ana", signedIn.userId)
    assertTrue(signedIn.isAdmin)
    assertEquals(Authenticator.DEFAULT_DEV_CALLER, dev.authenticate(Metadata()))
    val error = assertFailsWith<StatusException> { dev.authenticate(bearer("not-a-token")) }
    assertEquals(Status.Code.UNAUTHENTICATED, error.status.code)
  }

  @Test
  fun dev_roleHeader_simulatesAnAdministratorOrARegularUser() {
    val dev = Authenticator.dev(firebase = firebase)
    val token = token("email" to "ana@example.org")

    assertFalse(
      dev.authenticate(bearer(token).also { it.put(roleKey, "user") }).isAdmin,
      "Signed-in developers simulating a regular user aren't administrators",
    )
    val named =
      headers(
        Authenticator.DEV_USER_ID_HEADER to "uid-collector-one",
        Authenticator.DEV_ROLE_HEADER to "admin",
      )
    assertTrue(dev.authenticate(named).isAdmin)
    assertFalse(
      dev.authenticate(headers(Authenticator.DEV_USER_ID_HEADER to "uid-collector-one")).isAdmin
    )
  }

  @Test
  fun firebase_acceptsAValidToken() {
    val caller =
      firebase.authenticate(
        bearer(
          token(
            "email" to "ana@example.org",
            "email_verified" to true,
            "name" to "Ana",
            "firebase" to mapOf("sign_in_provider" to "google.com"),
          )
        )
      )

    assertEquals("uid-ana", caller.userId)
    assertEquals("ana@example.org", caller.email)
    assertTrue(caller.emailVerified)
    assertEquals("Ana", caller.displayName)
  }

  @Test
  fun firebase_adminEmails_areAdministratorsOnceVerified() {
    val withAdmins =
      Authenticator.firebase(
        PROJECT,
        TokenVerifier.newBuilder()
          .setIssuer("https://securetoken.google.com/$PROJECT")
          .setAudience(PROJECT)
          .setPublicKey(keys.public)
          .build(),
        adminEmails = setOf("ana@example.org"),
      )

    val verified = token("email" to "Ana@Example.org", "email_verified" to true)
    val unverified = token("email" to "ana@example.org", "email_verified" to false)
    assertTrue(withAdmins.authenticate(bearer(verified)).isAdmin)
    assertFalse(withAdmins.authenticate(bearer(unverified)).isAdmin)
  }

  @Test
  fun firebase_rejectsMissingInvalidAndAnonymousTokens() {
    val anonymous = token("firebase" to mapOf("sign_in_provider" to "anonymous"))
    val otherProject = token(audience = "another-project")
    val expired = token(expiresInSeconds = -3600L)
    for (headers in
      listOf(
        Metadata(),
        headers("authorization" to "Basic abc"),
        bearer("not-a-token"),
        bearer(anonymous),
        bearer(otherProject),
        bearer(expired),
      )) {
      val error = assertFailsWith<StatusException> { firebase.authenticate(headers) }
      assertEquals(Status.Code.UNAUTHENTICATED, error.status.code)
    }
  }

  private fun token(
    vararg claims: Pair<String, Any>,
    audience: String = PROJECT,
    expiresInSeconds: Long = 3600L,
  ): String {
    val now = System.currentTimeMillis() / 1000
    val header = JsonWebSignature.Header().setAlgorithm("RS256").setType("JWT")
    val payload =
      JsonWebToken.Payload()
        .setIssuer("https://securetoken.google.com/$PROJECT")
        .setAudience(audience)
        .setSubject("uid-ana")
        .setIssuedAtTimeSeconds(now - 60)
        .setExpirationTimeSeconds(now + expiresInSeconds)
    claims.forEach { (name, value) -> payload[name] = value }
    return JsonWebSignature.signUsingRsaSha256(
      keys.private,
      GsonFactory.getDefaultInstance(),
      header,
      payload,
    )
  }

  private fun bearer(token: String) = headers("authorization" to "Bearer $token")

  private fun headers(vararg values: Pair<String, String>) =
    Metadata().apply {
      values.forEach { (name, value) ->
        put(Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER), value)
      }
    }

  companion object {
    const val PROJECT = "test-project"
    private val roleKey =
      Metadata.Key.of(Authenticator.DEV_ROLE_HEADER, Metadata.ASCII_STRING_MARSHALLER)
  }
}
