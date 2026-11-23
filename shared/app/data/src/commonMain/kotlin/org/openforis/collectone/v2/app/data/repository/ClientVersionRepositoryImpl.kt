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
package org.openforis.collectone.v2.app.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import openforis.collectone.v1.userdata.ClientVersionPolicy
import openforis.collectone.v1.userdata.GetClientVersionPolicyRequest
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyRequest
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.client.grpc.GrpcWebTransport
import org.openforis.collectone.v2.app.domain.model.AppBuildConfig
import org.openforis.collectone.v2.app.domain.repository.ClientVersionRepository
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.core.version.MobileUpdateStatus
import org.openforis.collectone.v2.core.version.evaluateMobileClientVersion

/**
 * Shared tracker that receives mobile version policy headers from [GrpcWebTransport] responses
 * before [ClientVersionRepositoryImpl] or [CollectOneGrpcClient] is resolved.
 */
class ClientVersionPolicyTracker {
  private val _policy = MutableStateFlow(MobileClientVersionPolicy.EMPTY)
  val policy: StateFlow<MobileClientVersionPolicy> = _policy.asStateFlow()

  fun update(policy: MobileClientVersionPolicy) {
    _policy.value = policy.normalized()
  }

  fun recordFromResponseMetadata(headers: Map<String, String>) {
    MobileClientVersionPolicy.fromResponseHeaders(headers)?.let { update(it) }
  }
}

/** [ClientVersionRepository] backed by [ClientVersionPolicyTracker] and [CollectOneGrpcClient]. */
class ClientVersionRepositoryImpl(
  private val tracker: ClientVersionPolicyTracker,
  private val grpcClient: CollectOneGrpcClient?,
  private val scope: CoroutineScope,
  private val clientVersion: String = AppBuildConfig.APP_VERSION,
) : ClientVersionRepository {

  private val dismissedVersion = MutableStateFlow<String?>(null)

  override val policy: StateFlow<MobileClientVersionPolicy> = tracker.policy

  override val updateStatus: StateFlow<MobileUpdateStatus> =
    policy
      .map { evaluateMobileClientVersion(clientVersion, it) }
      .stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = evaluateMobileClientVersion(clientVersion, policy.value),
      )

  override val isSuggestionDismissed: StateFlow<Boolean> =
    combine(updateStatus, dismissedVersion) { status, dismissed ->
        status is MobileUpdateStatus.Suggested && dismissed == status.latestVersion
      }
      .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = false)

  @Suppress("TooGenericExceptionCaught")
  override suspend fun refreshPolicy(): Result<MobileClientVersionPolicy> {
    val client = grpcClient ?: return Result.success(policy.value)
    return try {
      val response = client.getClientVersionPolicy(GetClientVersionPolicyRequest())
      val fetched = response.policy.toDomain().normalized()
      tracker.update(fetched)
      Result.success(fetched)
    } catch (e: CancellationException) {
      throw e
    } catch (e: GrpcWebException) {
      if (e.status == GrpcWebTransport.UNIMPLEMENTED) {
        Result.success(policy.value)
      } else {
        Result.failure(e)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  override suspend fun updatePolicy(
    policy: MobileClientVersionPolicy
  ): Result<MobileClientVersionPolicy> {
    val norm = policy.normalized()
    norm.validationError()?.let {
      return Result.failure(IllegalArgumentException(it))
    }
    val client =
      grpcClient ?: return Result.failure(IllegalStateException("User data service not configured"))
    return try {
      val response =
        client.updateClientVersionPolicy(UpdateClientVersionPolicyRequest(policy = norm.toProto()))
      val saved = response.policy.toDomain().normalized()
      tracker.update(saved)
      Result.success(saved)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override fun dismissSuggestion() {
    val status = updateStatus.value
    if (status is MobileUpdateStatus.Suggested) {
      dismissedVersion.value = status.latestVersion
    }
  }
}

private fun MobileClientVersionPolicy.toProto(): ClientVersionPolicy =
  ClientVersionPolicy(
    latest_mobile_version = latestVersion,
    min_required_mobile_version = minRequiredVersion,
    mobile_update_url = updateUrl,
  )

private fun ClientVersionPolicy?.toDomain(): MobileClientVersionPolicy =
  if (this == null) {
    MobileClientVersionPolicy.EMPTY
  } else {
    MobileClientVersionPolicy(
      latestVersion = latest_mobile_version,
      minRequiredVersion = min_required_mobile_version,
      updateUrl = mobile_update_url,
    )
  }
