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

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.openforis.collectone.v2.core.version.ClientVersionHeaders
import org.openforis.collectone.v2.core.version.MobileClientVersionPolicy
import org.openforis.collectone.v2.core.version.MobileUpdateStatus
import org.openforis.collectone.v2.core.version.evaluateMobileClientVersion

/**
 * Inspects mobile client version headers (`x-client-platform: mobile`, `x-client-version`) on
 * incoming gRPC calls:
 * - Attaches the active [MobileClientVersionPolicy] headers to every mobile response so clients
 *   learn about suggested or cleared updates without extra round-trips.
 * - Rejects mobile RPCs (other than `GetClientVersionPolicy`) with `FAILED_PRECONDITION` when the
 *   client is older than `minRequiredVersion`, carrying the policy headers in the trailers.
 * - Leaves non-mobile clients (such as the Web Console) untouched so platform admins are never
 *   locked out.
 */
internal class ClientVersionInterceptor(
  private val policyProvider: () -> MobileClientVersionPolicy
) : ServerInterceptor {

  override fun <ReqT, RespT> interceptCall(
    call: ServerCall<ReqT, RespT>,
    headers: Metadata,
    next: ServerCallHandler<ReqT, RespT>,
  ): ServerCall.Listener<ReqT> {
    val platform = headers.get(CLIENT_PLATFORM_KEY)?.trim()?.lowercase()
    if (platform != ClientVersionHeaders.PLATFORM_MOBILE) {
      return next.startCall(call, headers)
    }
    val policy = policyProvider()
    val clientVersion = headers.get(CLIENT_VERSION_KEY)?.trim().orEmpty()
    val evaluation = evaluateMobileClientVersion(clientVersion, policy)
    if (
      evaluation is MobileUpdateStatus.Required &&
        call.methodDescriptor.fullMethodName != GET_POLICY_FULL_METHOD
    ) {
      val trailers = Metadata()
      writePolicyHeaders(trailers, policy)
      call.close(
        Status.FAILED_PRECONDITION.withDescription(
          "Mobile client version ${evaluation.currentVersion} is no longer supported; " +
            "version ${evaluation.minRequiredVersion} or higher is required."
        ),
        trailers,
      )
      return object : ServerCall.Listener<ReqT>() {}
    }

    var headersSent = false
    val wrappedCall =
      object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
        override fun sendHeaders(responseHeaders: Metadata) {
          headersSent = true
          writePolicyHeaders(responseHeaders, policy)
          super.sendHeaders(responseHeaders)
        }

        override fun close(status: Status, trailers: Metadata) {
          if (!headersSent) {
            writePolicyHeaders(trailers, policy)
          }
          super.close(status, trailers)
        }
      }
    return next.startCall(wrappedCall, headers)
  }

  private fun writePolicyHeaders(metadata: Metadata, policy: MobileClientVersionPolicy) {
    policy.toResponseHeaders().forEach { (name, value) ->
      val key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER)
      metadata.discardAll(key)
      metadata.put(key, value)
    }
  }

  companion object {
    private val CLIENT_PLATFORM_KEY: Metadata.Key<String> =
      Metadata.Key.of(ClientVersionHeaders.CLIENT_PLATFORM, Metadata.ASCII_STRING_MARSHALLER)
    private val CLIENT_VERSION_KEY: Metadata.Key<String> =
      Metadata.Key.of(ClientVersionHeaders.CLIENT_VERSION, Metadata.ASCII_STRING_MARSHALLER)
    private const val GET_POLICY_FULL_METHOD =
      "openforis.collectone.v1.userdata.ClientVersionService/GetClientVersionPolicy"
  }
}
