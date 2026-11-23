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
package org.openforis.collectone.v2.app.client.grpc

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticMessageTest {
  @Test
  fun grpcFailure_usesTheServerDescription() {
    val error = GrpcWebException(status = 7, path = "/SurveyService/Get", description = "No access")

    assertEquals("No access", error.diagnosticMessage())
  }

  @Test
  fun grpcFailureWithoutDescription_usesTheExceptionMessage() {
    val error = GrpcWebException(status = 14, path = "/SurveyService/Get", description = null)

    assertEquals("gRPC status 14 for /SurveyService/Get", error.diagnosticMessage())
  }

  @Test
  fun otherFailure_usesItsMessage() {
    assertEquals(
      "connection refused",
      IllegalStateException("connection refused").diagnosticMessage(),
    )
  }

  @Test
  fun failureWithoutMessage_usesItsClassName() {
    assertEquals("IllegalStateException", IllegalStateException().diagnosticMessage())
  }
}
