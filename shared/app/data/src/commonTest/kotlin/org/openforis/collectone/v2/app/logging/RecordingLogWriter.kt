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
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/** One message a [RecordingLogWriter] received. */
data class LoggedMessage(
  val severity: Severity,
  val tag: String,
  val message: String,
  val throwable: Throwable?,
)

/** A fake [LogWriter] that keeps the messages it receives. */
class RecordingLogWriter : LogWriter() {
  val messages = mutableListOf<LoggedMessage>()

  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    messages += LoggedMessage(severity, tag, message, throwable)
  }
}

/**
 * Runs [block] with the apps' logging configured as in a production build ([configureAppLogging])
 * but writing to a [RecordingLogWriter], which it returns. Restores Kermit's defaults afterwards.
 */
inline fun recordAppLogs(
  isDevelopmentBuild: Boolean = false,
  block: () -> Unit,
): List<LoggedMessage> {
  val writer = RecordingLogWriter()
  configureAppLogging(isDevelopmentBuild, writer)
  try {
    block()
  } finally {
    Logger.setMinSeverity(Severity.Verbose)
    Logger.setLogWriters(platformLogWriter())
  }
  return writer.messages
}
