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
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.server.geo.HttpWhispClient
import org.openforis.collectone.v2.server.messaging.InviteEmailTemplates
import org.openforis.collectone.v2.server.messaging.OutboundEmail
import org.openforis.collectone.v2.server.service.WhispEntityEnricher

/**
 * The server's settings, read once at startup from the environment variables documented on [main].
 * Reading them is kept apart from building the server so the defaults and fallbacks can be tested
 * without starting it. Values are kept as given: [main] rejects an unknown [authMode] or
 * [EmailConfig.mode] when it builds the authenticator or the email sender.
 */
internal data class ServerConfig(
  val port: Int,
  /** `dev` or `firebase`. */
  val authMode: String,
  val firebaseProjectId: String,
  val debugUiEnabled: Boolean,
  val debugResetEnabled: Boolean,
  val rpcParallelism: Int,
  val fcmEnabled: Boolean,
  /** Verified emails of administrators in `firebase` mode, trimmed and lowercased. */
  val adminEmails: Set<String>,
  val seedAreaBackfillEnabled: Boolean,
  /** `USER_DATA_SEED`: a seed file to load instead of the bundled sample seed, if set. */
  val seedPath: String?,
  val email: EmailConfig,
  val whisp: WhispConfig = WhispConfig(),
  val agStack: AgStackConfig = AgStackConfig(),
  val mobileClientVersion: MobileClientVersionPolicy = MobileClientVersionPolicy.EMPTY,
) {
  companion object {
    /**
     * The settings in [env]. The email, Whisp, and AgStack settings fall back to [localProperties]
     * (the repository's `local.properties`), so a local server can send real email, call Open Foris
     * Whisp, and register AgStack GeoIDs without exporting API keys in the shell environment.
     */
    fun from(env: (String) -> String?, localProperties: Properties = Properties()): ServerConfig {
      fun setting(name: String, default: String = ""): String =
        env(name)?.trim()?.takeIf { it.isNotEmpty() }
          ?: localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
          ?: default
      fun flag(name: String): Boolean? = env(name)?.lowercase()?.toBooleanStrictOrNull()

      val authMode = env("AUTH_MODE")?.takeIf { it.isNotBlank() } ?: "dev"
      val brevoApiKey = setting("BREVO_API_KEY")
      return ServerConfig(
        port = env("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
        authMode = authMode,
        firebaseProjectId =
          env("FIREBASE_PROJECT_ID")?.takeIf { it.isNotBlank() } ?: DEFAULT_FIREBASE_PROJECT_ID,
        debugUiEnabled = flag("DEBUG_UI_ENABLED") ?: (authMode == "dev"),
        debugResetEnabled = debugResetEnabled(env, authMode),
        rpcParallelism = rpcParallelism(env),
        fcmEnabled = flag("FCM_ENABLED") ?: (authMode == "firebase"),
        adminEmails =
          env("ADMIN_EMAILS")
            .orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet(),
        seedAreaBackfillEnabled = isSeedAreaBackfillEnabled(env),
        seedPath = env("USER_DATA_SEED"),
        email =
          EmailConfig(
            mode =
              setting("EMAIL_MODE", if (brevoApiKey.isNotEmpty()) "brevo" else "log").lowercase(),
            brevoApiKey = brevoApiKey,
            from = setting("EMAIL_FROM", OutboundEmail.DEFAULT_FROM_EMAIL),
            fromName = setting("EMAIL_FROM_NAME", OutboundEmail.DEFAULT_FROM_NAME),
            publicWebUrl = setting("PUBLIC_WEB_URL", InviteEmailTemplates.DEFAULT_PUBLIC_WEB_URL),
          ),
        whisp =
          WhispConfig(
            apiKey = setting("WHISP_API_KEY"),
            apiUrl = setting("WHISP_API_URL", WhispConfig.DEFAULT_API_URL),
            prefix = setting("WHISP_PREFIX", WhispConfig.DEFAULT_PREFIX),
          ),
        agStack =
          AgStackConfig(
            registryUrl = setting("AGSTACK_REGISTRY_URL"),
            apiKey = setting("AGSTACK_API_KEY"),
            authToken = setting("AGSTACK_AUTH_TOKEN"),
          ),
        mobileClientVersion =
          MobileClientVersionPolicy(
            latestVersion = setting("LATEST_MOBILE_VERSION"),
            minRequiredVersion = setting("MIN_REQUIRED_MOBILE_VERSION"),
            updateUrl = setting("MOBILE_UPDATE_URL"),
          ),
      )
    }

    private const val DEFAULT_PORT = 8080
    private const val DEFAULT_FIREBASE_PROJECT_ID = "collect-one-dev"
  }
}

/** How invitation emails are sent: [mode] `brevo` (with [brevoApiKey]) or `log`. */
internal data class EmailConfig(
  val mode: String,
  val brevoApiKey: String,
  val from: String,
  val fromName: String,
  /** The web console's base URL, for the links in invitations. */
  val publicWebUrl: String,
) {
  // Keeps the API key out of logs and test failure messages.
  override fun toString(): String =
    "EmailConfig(mode=$mode, brevoApiKey=${if (brevoApiKey.isEmpty()) "" else "***"}, " +
      "from=$from, fromName=$fromName, publicWebUrl=$publicWebUrl)"
}

/** How geospatial entities are enriched via Open Foris Whisp ([HttpWhispClient]). */
internal data class WhispConfig(
  val apiKey: String = "",
  val apiUrl: String = DEFAULT_API_URL,
  val prefix: String = DEFAULT_PREFIX,
) {
  val enabled: Boolean
    get() = apiKey.isNotBlank() && apiUrl.isNotBlank()

  // Keeps the API key out of logs and test failure messages.
  override fun toString(): String =
    "WhispConfig(apiKey=${if (apiKey.isEmpty()) "" else "***"}, apiUrl=$apiUrl, prefix=$prefix)"

  companion object {
    val DEFAULT_API_URL: String = HttpWhispClient.DEFAULT_ENDPOINT.toString()
    const val DEFAULT_PREFIX: String = WhispEntityEnricher.DEFAULT_PREFIX
  }
}

/**
 * AgStack Asset Registry settings (`AGSTACK_REGISTRY_URL`, `AGSTACK_API_KEY`,
 * `AGSTACK_AUTH_TOKEN`).
 */
internal data class AgStackConfig(
  val registryUrl: String = "",
  val apiKey: String = "",
  val authToken: String = "",
) {
  override fun toString(): String =
    "AgStackConfig(registryUrl=$registryUrl, " +
      "apiKey=${if (apiKey.isEmpty()) "" else "***"}, " +
      "authToken=${if (authToken.isEmpty()) "" else "***"})"
}
