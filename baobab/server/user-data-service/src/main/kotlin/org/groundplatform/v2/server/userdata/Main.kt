/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.server.userdata

import groundplatform.v2.userdata.UserDataSeed
import java.io.File

/** Classpath location of the sample seed generated from the app's test fixtures. */
const val SAMPLE_SEED_RESOURCE = "/seed/user_data_seed.json"

/**
 * Starts the user data service.
 *
 * Environment variables:
 * - `PORT`: port to listen on (default 8080; set by Cloud Run).
 * - `USER_DATA_SEED`: path to a `UserDataSeed` in proto JSON to load instead of the bundled sample
 *   seed.
 * - `SPANNER_EMULATOR_HOST`, `SPANNER_PROJECT_ID`, `SPANNER_INSTANCE_ID`, `SPANNER_DATABASE_ID`:
 *   the Cloud Spanner database to connect to ([SpannerSettings.fromEnv]). When set, the Spanner
 *   debug web UI at `/debug/spanner` browses it and records its operations. Data is still served
 *   from memory until the Spanner store lands.
 */
fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  val seed = loadSeed(System.getenv("USER_DATA_SEED"))
  val spannerDebug =
    SpannerSettings.fromEnv()?.let { settings ->
      val recorder = SpannerOperationRecorder()
      SpannerDebug(SpannerConnection.open(settings, recorder), recorder)
    }
  val server = UserDataServer.create(UserDataBackend(seed), port, spannerDebug = spannerDebug)
  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        server.stop().join()
        spannerDebug?.connection?.close()
      }
    )
  server.start().join()
  println(
    "User data service listening on port ${server.activeLocalPort()} " +
      "(${seed.surveys.size} surveys, ${seed.organizations.size} organizations)"
  )
  spannerDebug?.let { println("Spanner: ${it.connection.settings.description}") }
  server.blockUntilShutdown()
}

/** Loads the seed at [path], or the bundled sample seed when [path] is null or blank. */
fun loadSeed(path: String?): UserDataSeed {
  val json =
    if (!path.isNullOrBlank()) {
      File(path).readText()
    } else {
      UserDataBackend::class.java.getResourceAsStream(SAMPLE_SEED_RESOURCE)?.use {
        it.readBytes().decodeToString()
      } ?: return UserDataSeed()
    }
  return requireNotNull(userDataSeedJsonAdapter.fromJson(json)) { "Empty user data seed" }
}
