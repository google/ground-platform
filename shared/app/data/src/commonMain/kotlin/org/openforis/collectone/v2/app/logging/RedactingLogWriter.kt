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
package org.openforis.collectone.v2.app.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Passes log messages on to [delegate] without the email addresses and access tokens in them, so a
 * server error or exception that quotes the signed-in account never reaches the device's logs.
 *
 * An exception is appended to the message as its redacted stack trace, since its own message can't
 * be rewritten.
 */
class RedactingLogWriter(private val delegate: LogWriter) : LogWriter() {
  override fun isLoggable(tag: String, severity: Severity): Boolean =
    delegate.isLoggable(tag, severity)

  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    val stackTrace = throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()
    delegate.log(severity, redactSecrets(message + stackTrace), tag, throwable = null)
  }
}

/** [text] with email addresses replaced by `<email>` and bearer tokens and JWTs by `<token>`. */
fun redactSecrets(text: String): String =
  text
    .replace(BEARER_TOKEN, "Bearer <token>")
    .replace(JSON_WEB_TOKEN, "<token>")
    .replace(EMAIL_ADDRESS, "<email>")

private val EMAIL_ADDRESS = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+""")

private val BEARER_TOKEN = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")

/** Three base64url segments, the first a JSON header (`eyJ` is `{"` encoded). */
private val JSON_WEB_TOKEN = Regex("""\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
