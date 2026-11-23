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
package org.openforis.collectone.v2.server.geo

import com.google.auth.oauth2.ServiceAccountCredentials
import io.grpc.Status
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Service account credentials and Cloud project for Earth Engine REST calls, shared by
 * [EarthEngineTileClient] (`maps.create`) and [EarthEngineValueClient] (`value:compute`).
 *
 * The account needs `earthengine.maps.create` for overlays and `earthengine.computations.create`
 * for plot statistics and time series, plus `serviceusage.services.use` on the project.
 */
internal class EarthEngineAuth(
  private val streamProvider: () -> InputStream,
  private val projectIdOverride: String?,
) {
  private val serviceAccountCredentials: ServiceAccountCredentials by lazy {
    streamProvider().use { stream -> ServiceAccountCredentials.fromStream(stream) }
  }

  private val scopedCredentials by lazy {
    serviceAccountCredentials.createScoped(listOf(EARTH_ENGINE_READONLY_SCOPE))
  }

  /** Cloud project that Earth Engine requests are billed and quota-checked against. */
  val projectId: String
    get() = projectIdOverride ?: serviceAccountCredentials.projectId ?: DEFAULT_PROJECT_ID

  /** A valid OAuth2 access token, refreshed when expired. */
  fun accessToken(): String {
    scopedCredentials.refreshIfExpired()
    return scopedCredentials.accessToken?.tokenValue
      ?: throw Status.UNAVAILABLE.withDescription(
          "Failed to obtain OAuth2 access token for Earth Engine"
        )
        .asRuntimeException()
  }

  companion object {
    const val EARTH_ENGINE_READONLY_SCOPE = "https://www.googleapis.com/auth/earthengine.readonly"
    private const val DEFAULT_PROJECT_ID = "collect-one-dev"

    /** Description of the missing configuration, for `UNAVAILABLE` errors. */
    const val NOT_CONFIGURED =
      "Earth Engine credentials are not configured (set EE_SERVICE_ACCOUNT_FILE or EE_SERVICE_ACCOUNT_JSON)"

    /**
     * Reads the key from `EE_SERVICE_ACCOUNT_JSON`, or the file named by `EE_SERVICE_ACCOUNT_FILE`
     * (environment, system property, or server build config), and the project from `EE_PROJECT_ID`.
     * Returns `null` when no readable key is configured.
     */
    fun fromEnvironment(): EarthEngineAuth? {
      val jsonEnv = System.getenv("EE_SERVICE_ACCOUNT_JSON")?.takeIf { it.isNotBlank() }
      val fileEnv =
        System.getenv("EE_SERVICE_ACCOUNT_FILE")?.takeIf { it.isNotBlank() }
          ?: System.getProperty("EE_SERVICE_ACCOUNT_FILE")?.takeIf { it.isNotBlank() }
          ?: ServerBuildConfig.eeServiceAccountFile?.takeIf { it.isNotBlank() }

      val projectIdOverride =
        System.getenv("EE_PROJECT_ID")?.takeIf { it.isNotBlank() }
          ?: System.getProperty("EE_PROJECT_ID")?.takeIf { it.isNotBlank() }
          ?: ServerBuildConfig.eeProjectId?.takeIf { it.isNotBlank() }

      val streamProvider: (() -> InputStream) =
        when {
          jsonEnv != null -> {
            { ByteArrayInputStream(jsonEnv.toByteArray(Charsets.UTF_8)) }
          }
          fileEnv != null -> {
            val file = File(fileEnv)
            if (!file.isFile || !file.canRead()) return null
            { file.inputStream() }
          }
          else -> return null
        }
      return EarthEngineAuth(streamProvider, projectIdOverride)
    }
  }
}
