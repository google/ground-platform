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

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireRpc
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Exposes a Wire-generated `*Server` interface (for example `SurveyServiceServer`) as a grpc-java
 * [ServerServiceDefinition].
 *
 * Each interface method carries a [WireRpc] annotation with its gRPC path and message adapters, so
 * the bindings are derived at startup instead of generated. Every RPC is unary: the sync engine
 * polls `List*` RPCs instead of holding server streams open (`docs/technical/sync-engine.md`).
 * Failures map to gRPC statuses: a thrown [StatusException] is sent as is,
 * [IllegalArgumentException] becomes `INVALID_ARGUMENT`, and [IllegalStateException] becomes
 * `FAILED_PRECONDITION`.
 *
 * The caller authenticated by [Authenticator.interceptor] is available to the implementation
 * through [currentCaller], after [bind]'s `beforeCall` ran for it.
 */
object WireGrpcBindings {

  /**
   * Binds every [WireRpc] method of [serviceInterface] to [implementation]. [beforeCall] runs
   * before each RPC with its caller (for example to register the user).
   */
  fun <T : Any> bind(
    serviceInterface: Class<T>,
    implementation: T,
    scope: CoroutineScope,
    beforeCall: suspend (Caller?) -> Unit = {},
  ): ServerServiceDefinition {
    val rpcs =
      serviceInterface.methods
        .mapNotNull { method -> method.getAnnotation(WireRpc::class.java)?.let { method to it } }
        .sortedBy { (_, rpc) -> rpc.path }
    require(rpcs.isNotEmpty()) { "${serviceInterface.name} has no @WireRpc methods" }
    val serviceName = rpcs.first().second.path.removePrefix("/").substringBefore('/')
    val builder = ServerServiceDefinition.builder(serviceName)
    for ((method, rpc) in rpcs) {
      require(method.parameterTypes.size == 2) {
        "${method.name} isn't a unary RPC; only unary RPCs are supported"
      }
      val descriptor =
        MethodDescriptor.newBuilder<Any, Any>()
          .setType(MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(rpc.path.removePrefix("/"))
          .setRequestMarshaller(WireMarshaller(adapter(rpc.requestAdapter)))
          .setResponseMarshaller(WireMarshaller(adapter(rpc.responseAdapter)))
          .build()
      val handler =
        ServerCalls.asyncUnaryCall<Any, Any> { request, responses ->
          val caller = Authenticator.CALLER.get()
          scope.launch(CallerElement(caller)) {
            serveUnary(method, implementation, request, responses) { beforeCall(caller) }
          }
        }
      builder.addMethod(descriptor, handler)
    }
    return builder.build()
  }

  private suspend fun serveUnary(
    method: Method,
    implementation: Any,
    request: Any,
    responses: StreamObserver<Any>,
    beforeCall: suspend () -> Unit,
  ) {
    try {
      beforeCall()
      val response = checkNotNull(method.kotlinFunction).callSuspend(implementation, request)
      responses.onNext(checkNotNull(response) { "${method.name} returned null" })
      responses.onCompleted()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      responses.onError(toStatusException(e))
    }
  }

  private fun toStatusException(e: Throwable): StatusException {
    val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
    val status =
      when (cause) {
        is StatusException -> return cause
        is IllegalArgumentException -> Status.INVALID_ARGUMENT
        is IllegalStateException -> Status.FAILED_PRECONDITION
        is UnsupportedOperationException -> Status.UNIMPLEMENTED
        else -> Status.INTERNAL
      }
    return status.withDescription(cause.message).withCause(cause).asException()
  }

  @Suppress("UNCHECKED_CAST")
  private fun adapter(adapterString: String): ProtoAdapter<Any> =
    ProtoAdapter.get(adapterString) as ProtoAdapter<Any>

  /** Encodes and decodes messages with a Wire [ProtoAdapter]. */
  private class WireMarshaller(private val adapter: ProtoAdapter<Any>) :
    MethodDescriptor.Marshaller<Any> {
    override fun stream(value: Any): InputStream = ByteArrayInputStream(adapter.encode(value))

    override fun parse(stream: InputStream): Any = adapter.decode(stream)
  }
}
