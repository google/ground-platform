/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.di

import org.openforis.collectone.v2.app.client.auth.AuthClient
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.client.grpc.remoteUserDataGrpcClient
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.core.version.ClientVersionHeaders

/**
 * How a host configures the app's dependency graph ([appModules]): which user data service it talks
 * to and how it syncs.
 */
class AppConfig(
  /**
   * Base URL of the Collect One server (`server/`), e.g. `http://localhost:8080`. When null, and
   * without a [userDataClient], nothing is synced and the store keeps whatever it holds (empty for
   * a new store).
   */
  val userDataServiceUrl: String? = null,
  /**
   * Whether to keep only the surveys the user downloaded offline (the mobile app), instead of every
   * survey the user can list (the web console).
   */
  val syncDownloadedSurveysOnly: Boolean = false,
  /**
   * Builds the user data service client from the signed-in [AuthClient]. By default a gRPC-Web
   * client of [userDataServiceUrl]; tests pass an in-memory service instead. Null without a
   * service.
   */
  val userDataClient: ((AuthClient) -> CollectOneGrpcClient)? = userDataServiceUrl?.let { url ->
    DefaultRemoteClientFactory(url)
  },
  /**
   * Whether local edits are queued in the outbox and synced continuously (drain, then pull). On by
   * default when connected to a real service ([userDataServiceUrl]); tests that pass an in-memory
   * [userDataClient] keep the simulated uploads unless they turn it on.
   */
  val enableSyncEngine: Boolean = userDataServiceUrl != null,
  /**
   * Whether entity syncs request only lightweight metadata + core/concept-linked properties,
   * leaving bulk tabular/enrichment properties to be lazy-loaded on scroll via
   * [org.openforis.collectone.v2.app.domain.repository.EntityTableRepository]. Enabled on the
   * online-only web console; kept false on offline-first mobile clients.
   */
  val lazyLoadEntityProperties: Boolean = false,
  /**
   * Client platform sent in `x-client-platform` (e.g., `"mobile"` on mobile hosts, null on web
   * console). The server evaluates mobile version policies only for mobile clients.
   */
  val clientPlatform: String? = null,
  /**
   * Client version sent in `x-client-version` and evaluated against the server's version policy.
   */
  val clientVersion: String = AppBuildConfig.APP_VERSION,
) {
  /** Whether the app talks to a user data service. */
  val hasUserDataService: Boolean
    get() = userDataClient != null

  /** Whether the outbox sync engine runs (it needs a user data service). */
  val runsSyncEngine: Boolean
    get() = enableSyncEngine && hasUserDataService

  /**
   * Builds the [CollectOneGrpcClient] for [authClient], wiring [onResponseMetadata] into the
   * default gRPC-Web transport when [userDataServiceUrl] is used without a custom [userDataClient].
   */
  internal fun createUserDataClient(
    authClient: AuthClient,
    onResponseMetadata: (Map<String, String>) -> Unit = {},
  ): CollectOneGrpcClient? {
    val factory = userDataClient ?: return null
    if (factory is DefaultRemoteClientFactory) {
      return remoteUserDataClient(
        url = factory.url,
        authClient = authClient,
        clientPlatform = clientPlatform,
        clientVersion = clientVersion,
        onResponseMetadata = onResponseMetadata,
      )
    }
    return factory(authClient)
  }

  private class DefaultRemoteClientFactory(val url: String) : (AuthClient) -> CollectOneGrpcClient {
    override fun invoke(authClient: AuthClient): CollectOneGrpcClient =
      remoteUserDataClient(url, authClient)
  }

  private companion object {
    fun remoteUserDataClient(
      url: String,
      authClient: AuthClient,
      clientPlatform: String? = null,
      clientVersion: String = AppBuildConfig.APP_VERSION,
      onResponseMetadata: (Map<String, String>) -> Unit = {},
    ): CollectOneGrpcClient =
      remoteUserDataGrpcClient(
        GrpcWebTransport(
          url,
          authToken = authClient::idToken,
          headers = {
            buildMap {
              putAll(authClient.requestHeaders())
              clientPlatform
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                  put(ClientVersionHeaders.CLIENT_PLATFORM, it)
                }
              clientVersion
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let {
                  put(ClientVersionHeaders.CLIENT_VERSION, it)
                }
            }
          },
          onResponseMetadata = onResponseMetadata,
        )
      )
  }
}
