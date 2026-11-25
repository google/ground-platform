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
package org.openforis.collectone.v2.server.web

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.RequestHeaders
import com.linecorp.armeria.server.ServiceRequestContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.helpers.NOPLogger

class ClientErrorWebServiceTest {
  @Test
  fun acceptsValidPayloadAndRedactsSecretsInLog() {
    val recorded = mutableListOf<String>()
    val service = ClientErrorWebService.service(logger = recordingLogger(recorded))
    val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl"
    val body =
      """
      {
        "client": "web-console",
        "environment": "staging",
        "kind": "unhandledrejection",
        "typeName": "IllegalStateException",
        "message": "Auth failed for ada@example.org with Bearer secret-token and $jwt",
        "stackTrace": "at foo (app.js:12)",
        "breadcrumbs": ["[Info/Sync] user ada@example.org synced"]
      }
      """
        .trimIndent()

    val resp = executePost(service, body)

    assertEquals(HttpStatus.ACCEPTED, resp.status())
    assertEquals(1, recorded.size)
    val logLine = recorded.single()
    assertTrue("client=web-console" in logLine, logLine)
    assertTrue("env=staging" in logLine, logLine)
    assertTrue("Bearer <token>" in logLine, logLine)
    assertTrue("<email>" in logLine, logLine)
    assertFalse("ada@example.org" in logLine, logLine)
    assertFalse("secret-token" in logLine, logLine)
    assertFalse(jwt in logLine, logLine)
  }

  @Test
  fun rejectsEmptyOrInvalidJsonWithBadRequest() {
    val service = ClientErrorWebService.service(logger = NOPLogger.NOP_LOGGER)

    assertEquals(HttpStatus.BAD_REQUEST, executePost(service, "").status())
    assertEquals(HttpStatus.BAD_REQUEST, executePost(service, "{not json").status())
  }

  @Test
  fun rejectsOversizedPayloads() {
    val service = ClientErrorWebService.service(logger = NOPLogger.NOP_LOGGER)
    val huge = """{"message":"${"a".repeat(ClientErrorWebService.MAX_BODY_BYTES + 100)}"}"""

    assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, executePost(service, huge).status())
  }

  @Test
  fun rateLimitsExcessiveRequestsPerIp() {
    val fixedClock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC)
    val service =
      ClientErrorWebService.service(
        logger = NOPLogger.NOP_LOGGER,
        clock = fixedClock,
        maxReportsPerMinutePerIp = 2,
      )
    val payload = """{"client":"web-console","message":"oops"}"""

    assertEquals(HttpStatus.ACCEPTED, executePost(service, payload, ip = "198.51.100.1").status())
    assertEquals(HttpStatus.ACCEPTED, executePost(service, payload, ip = "198.51.100.1").status())
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      executePost(service, payload, ip = "198.51.100.1").status(),
    )
    assertEquals(HttpStatus.ACCEPTED, executePost(service, payload, ip = "198.51.100.2").status())
  }

  private fun executePost(
    service: com.linecorp.armeria.server.HttpService,
    body: String,
    ip: String = "203.0.113.10",
  ) =
    HttpRequest.of(
        RequestHeaders.builder(HttpMethod.POST, "/client-errors")
          .contentType(MediaType.JSON_UTF_8)
          .add("x-forwarded-for", ip)
          .build(),
        HttpData.ofUtf8(body),
      )
      .let { req -> service.serve(ServiceRequestContext.of(req), req).aggregate().join() }

  private fun recordingLogger(sink: MutableList<String>): org.slf4j.Logger =
    object : org.slf4j.helpers.MarkerIgnoringBase() {
      override fun isTraceEnabled() = false

      override fun trace(msg: String?) = Unit

      override fun trace(format: String?, arg: Any?) = Unit

      override fun trace(format: String?, arg1: Any?, arg2: Any?) = Unit

      override fun trace(format: String?, vararg arguments: Any?) = Unit

      override fun trace(msg: String?, t: Throwable?) = Unit

      override fun isDebugEnabled() = false

      override fun debug(msg: String?) = Unit

      override fun debug(format: String?, arg: Any?) = Unit

      override fun debug(format: String?, arg1: Any?, arg2: Any?) = Unit

      override fun debug(format: String?, vararg arguments: Any?) = Unit

      override fun debug(msg: String?, t: Throwable?) = Unit

      override fun isInfoEnabled() = false

      override fun info(msg: String?) = Unit

      override fun info(format: String?, arg: Any?) = Unit

      override fun info(format: String?, arg1: Any?, arg2: Any?) = Unit

      override fun info(format: String?, vararg arguments: Any?) = Unit

      override fun info(msg: String?, t: Throwable?) = Unit

      override fun isWarnEnabled() = false

      override fun warn(msg: String?) = Unit

      override fun warn(format: String?, arg: Any?) = Unit

      override fun warn(format: String?, vararg arguments: Any?) = Unit

      override fun warn(format: String?, arg1: Any?, arg2: Any?) = Unit

      override fun warn(msg: String?, t: Throwable?) = Unit

      override fun isErrorEnabled() = true

      override fun error(msg: String?) {
        if (msg != null) sink += msg
      }

      override fun error(format: String?, arg: Any?) = Unit

      override fun error(format: String?, arg1: Any?, arg2: Any?) = Unit

      override fun error(format: String?, vararg arguments: Any?) = Unit

      override fun error(msg: String?, t: Throwable?) {
        if (msg != null) sink += msg
      }
    }
}
