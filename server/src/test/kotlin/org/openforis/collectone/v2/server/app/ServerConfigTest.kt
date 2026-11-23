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
package org.openforis.collectone.v2.server.app

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openforis.collectone.v2.server.messaging.InviteEmailTemplates
import org.openforis.collectone.v2.server.messaging.OutboundEmail

/** The defaults and fallbacks of the startup settings ([ServerConfig.from]). */
class ServerConfigTest {
  private fun config(vararg env: Pair<String, String>, properties: Properties = Properties()) =
    ServerConfig.from(mapOf(*env)::get, properties)

  @Test
  fun withoutSettings_aLocalDevServerStarts() {
    val config = config()

    assertEquals(8080, config.port)
    assertEquals("dev", config.authMode)
    assertEquals("collect-one-dev", config.firebaseProjectId)
    assertTrue(config.debugUiEnabled)
    assertTrue(config.debugResetEnabled)
    assertFalse(config.fcmEnabled)
    assertEquals(DEFAULT_RPC_PARALLELISM, config.rpcParallelism)
    assertEquals(emptySet(), config.adminEmails)
    assertTrue(config.seedAreaBackfillEnabled)
    assertNull(config.seedPath)
    assertEquals(
      EmailConfig(
        mode = "log",
        brevoApiKey = "",
        from = OutboundEmail.DEFAULT_FROM_EMAIL,
        fromName = OutboundEmail.DEFAULT_FROM_NAME,
        publicWebUrl = InviteEmailTemplates.DEFAULT_PUBLIC_WEB_URL,
      ),
      config.email,
    )
    assertEquals(
      WhispConfig(
        apiKey = "",
        apiUrl = WhispConfig.DEFAULT_API_URL,
        prefix = WhispConfig.DEFAULT_PREFIX,
      ),
      config.whisp,
    )
    assertFalse(config.whisp.enabled)
  }

  @Test
  fun firebaseMode_turnsOffTheDebugUis_andTurnsOnFcm() {
    val config = config("AUTH_MODE" to "firebase", "FIREBASE_PROJECT_ID" to "my-project")

    assertEquals("my-project", config.firebaseProjectId)
    assertFalse(config.debugUiEnabled)
    assertFalse(config.debugResetEnabled)
    assertTrue(config.fcmEnabled)
  }

  @Test
  fun explicitFlags_overrideTheModesDefaults_andInvalidOnesAreIgnored() {
    val config =
      config(
        "AUTH_MODE" to "firebase",
        "DEBUG_UI_ENABLED" to "TRUE",
        "FCM_ENABLED" to "false",
        "PORT" to "not-a-port",
      )
    val invalid = config("DEBUG_UI_ENABLED" to "yes", "FCM_ENABLED" to "1")

    assertTrue(config.debugUiEnabled)
    assertFalse(config.fcmEnabled)
    assertEquals(8080, config.port)
    assertTrue(invalid.debugUiEnabled)
    assertFalse(invalid.fcmEnabled)
  }

  @Test
  fun blankModesAndProjects_fallBackToTheDefaults() {
    val config = config("AUTH_MODE" to " ", "FIREBASE_PROJECT_ID" to "")

    assertEquals("dev", config.authMode)
    assertEquals("collect-one-dev", config.firebaseProjectId)
  }

  @Test
  fun adminEmails_areNormalized_andBlankEntriesDropped() {
    val config = config("ADMIN_EMAILS" to " Ana@Example.org,,bo@example.org , ")

    assertEquals(setOf("ana@example.org", "bo@example.org"), config.adminEmails)
  }

  @Test
  fun aBrevoKey_selectsBrevo_unlessTheModeSaysOtherwise() {
    assertEquals("brevo", config("BREVO_API_KEY" to "xkeysib-test").email.mode)
    assertEquals("log", config("BREVO_API_KEY" to "xkeysib-test", "EMAIL_MODE" to "LOG").email.mode)
  }

  @Test
  fun emailSettings_fallBackToLocalProperties_butTheEnvironmentWins() {
    val properties =
      Properties().apply {
        setProperty("BREVO_API_KEY", " xkeysib-local ")
        setProperty("EMAIL_FROM", "local@example.org")
        setProperty("PUBLIC_WEB_URL", "http://localhost:8092")
        // Only the email settings are read from local.properties.
        setProperty("AUTH_MODE", "firebase")
      }

    val config = config("EMAIL_FROM" to "env@example.org", properties = properties)

    assertEquals("brevo", config.email.mode)
    assertEquals("xkeysib-local", config.email.brevoApiKey)
    assertEquals("env@example.org", config.email.from)
    assertEquals("http://localhost:8092", config.email.publicWebUrl)
    assertEquals("dev", config.authMode)
  }

  @Test
  fun theBrevoKey_staysOutOfToString() {
    val config = config("BREVO_API_KEY" to "xkeysib-secret")

    assertFalse("xkeysib-secret" in config.toString())
  }

  @Test
  fun whispSettings_fallBackToLocalProperties_andStayOutOfToString() {
    val properties =
      Properties().apply {
        setProperty("WHISP_API_KEY", " whisp-local-secret ")
        setProperty("WHISP_API_URL", "https://whisp.openforis.org/api/submit/wkt")
        setProperty("WHISP_PREFIX", "whisp_")
      }

    val fromLocal = config(properties = properties)
    assertTrue(fromLocal.whisp.enabled)
    assertEquals("whisp-local-secret", fromLocal.whisp.apiKey)
    assertEquals("https://whisp.openforis.org/api/submit/wkt", fromLocal.whisp.apiUrl)
    assertEquals("whisp_", fromLocal.whisp.prefix)
    assertFalse("whisp-local-secret" in fromLocal.toString())
    assertFalse("whisp-local-secret" in fromLocal.whisp.toString())

    val fromEnv =
      config(
        "WHISP_API_KEY" to "whisp-env-secret",
        "WHISP_PREFIX" to "eudr_",
        properties = properties,
      )
    assertEquals("whisp-env-secret", fromEnv.whisp.apiKey)
    assertEquals("eudr_", fromEnv.whisp.prefix)
    assertFalse("whisp-env-secret" in fromEnv.toString())
  }
}
