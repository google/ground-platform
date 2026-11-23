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
package org.openforis.collectone.v2.server.service

import io.grpc.Status
import io.grpc.StatusException
import openforis.collectone.v1.userdata.ClientVersionPolicy
import openforis.collectone.v1.userdata.ClientVersionServiceServer
import openforis.collectone.v1.userdata.GetClientVersionPolicyRequest
import openforis.collectone.v1.userdata.GetClientVersionPolicyResponse
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyRequest
import openforis.collectone.v1.userdata.UpdateClientVersionPolicyResponse
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.server.auth.currentCaller
import org.openforis.collectone.v2.server.data.UserDataStore

/**
 * Serves [ClientVersionServiceServer] and maintains an in-memory cache of the effective
 * [MobileClientVersionPolicy] so the gRPC interceptor can evaluate mobile client versions without
 * extra database round-trips per RPC.
 */
internal class ClientVersionServiceImpl(
  private val store: UserDataStore,
  private val defaultPolicy: MobileClientVersionPolicy = MobileClientVersionPolicy.EMPTY,
) : ClientVersionServiceServer {

  @Volatile private var cachedPolicy: MobileClientVersionPolicy? = null

  /** Returns the latest cached policy, or [defaultPolicy] before the store has been read. */
  fun currentPolicy(): MobileClientVersionPolicy = cachedPolicy ?: defaultPolicy

  /** Refreshes the cached policy from [store] (falling back to [defaultPolicy] when unset). */
  suspend fun refreshPolicy(): MobileClientVersionPolicy {
    val effective = (store.clientVersionPolicy() ?: defaultPolicy).normalized()
    cachedPolicy = effective
    return effective
  }

  /** Drops the cached policy (for example after a dev store reset). */
  fun clearCache() {
    cachedPolicy = null
  }

  override suspend fun GetClientVersionPolicy(
    request: GetClientVersionPolicyRequest
  ): GetClientVersionPolicyResponse =
    GetClientVersionPolicyResponse(policy = refreshPolicy().toProto())

  override suspend fun UpdateClientVersionPolicy(
    request: UpdateClientVersionPolicyRequest
  ): UpdateClientVersionPolicyResponse {
    val caller = currentCaller()
    val isAdmin = store.read { Access(it, caller).isAdmin }
    if (!isAdmin) {
      throw StatusException(
        Status.PERMISSION_DENIED.withDescription(
          "Only platform administrators can update the mobile client version policy"
        )
      )
    }
    val updated = request.policy.toDomain().normalized()
    updated.validationError()?.let { message ->
      throw StatusException(Status.INVALID_ARGUMENT.withDescription(message))
    }
    store.saveClientVersionPolicy(updated)
    cachedPolicy = updated
    return UpdateClientVersionPolicyResponse(policy = updated.toProto())
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
