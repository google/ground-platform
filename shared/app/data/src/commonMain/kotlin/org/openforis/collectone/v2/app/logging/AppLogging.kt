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

/**
 * The lowest severity the apps log: [Severity.Debug] in development builds (local servers, the
 * mobile workbench), [Severity.Info] in builds served to real users.
 */
fun appLogSeverity(isDevelopmentBuild: Boolean): Severity =
  if (isDevelopmentBuild) Severity.Debug else Severity.Info

/**
 * Configures the client apps' one logger, Kermit's global [Logger], which every module logs through
 * with a tagged logger (`Logger.withTag("SyncCoordinator")`). Each host calls this once at startup,
 * before anything logs.
 *
 * Messages go to [writer] (the platform's: Logcat on Android, the browser console on Wasm, standard
 * output on the JVM) at [appLogSeverity] and above, with email addresses and access tokens removed
 * (see [RedactingLogWriter]).
 */
fun configureAppLogging(isDevelopmentBuild: Boolean, writer: LogWriter = platformLogWriter()) {
  Logger.setMinSeverity(appLogSeverity(isDevelopmentBuild))
  Logger.setLogWriters(RedactingLogWriter(writer))
}
