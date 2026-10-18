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

import com.squareup.wire.Instant
import groundplatform.v2.userdata.UserDataSeed
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Classpath location of the sample seed generated from the app's test fixtures. */
const val SAMPLE_SEED_RESOURCE = "/seed/user_data_seed.json"

/** How long a trashed survey stays restorable before the daily purge job removes its data. */
const val SURVEY_TRASH_RETENTION_DAYS = 30L

/**
 * Starts the user data service.
 *
 * Environment variables:
 * - `PORT`: port to listen on (default 8080; set by Cloud Run).
 * - `USER_DATA_SEED`: path to a `UserDataSeed` in proto JSON to load instead of the bundled sample
 *   seed.
 * - `SPANNER_EMULATOR_HOST`, `SPANNER_PROJECT_ID`, `SPANNER_INSTANCE_ID`, `SPANNER_DATABASE_ID`:
 *   the Cloud Spanner database to connect to ([SpannerSettings.fromEnv]). When set, the Spanner
 *   debug web UI at `/debug/spanner` browses it and records its operations.
 * - `USER_DATA_STORE`: `memory` (default) serves data from memory; `spanner` stores it in the
 *   Spanner database above, applying the schema and loading the seed into an empty database.
 */
fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  val seed = loadSeed(System.getenv("USER_DATA_SEED"))
  val spannerDebug =
    SpannerSettings.fromEnv()?.let { settings ->
      val recorder = SpannerOperationRecorder()
      SpannerDebug(SpannerConnection.open(settings, recorder), recorder)
    }
  val store =
    when (val kind = System.getenv("USER_DATA_STORE")?.takeIf { it.isNotBlank() } ?: "memory") {
      "memory" -> InMemoryUserDataStore(seed)
      "spanner" -> {
        val connection =
          requireNotNull(spannerDebug?.connection) {
            "USER_DATA_STORE=spanner needs SPANNER_EMULATOR_HOST or SPANNER_INSTANCE_ID"
          }
        spannerStore(connection, seed)
      }
      else -> error("Unknown USER_DATA_STORE \"$kind\" (expected memory or spanner)")
    }
  val backend = UserDataBackend(store)
  val purgeScheduler =
    Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "survey-trash-purge").apply { isDaemon = true }
      }
      .apply {
        scheduleAtFixedRate(
          {
            runCatching {
              val cutoff =
                Instant.ofEpochSecond(
                  backend.currentWatermark().epochSecond -
                    TimeUnit.DAYS.toSeconds(SURVEY_TRASH_RETENTION_DAYS),
                  0,
                )
              backend.purgeTrashedSurveys(cutoff)
            }
          },
          1,
          24,
          TimeUnit.HOURS,
        )
      }
  val server = UserDataServer.create(backend, port, spannerDebug = spannerDebug)
  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        purgeScheduler.shutdownNow()
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
  println("Store: ${store::class.simpleName}")
  server.blockUntilShutdown()
}

/**
 * Applies the schema to the database in [connection] and returns a store on it, loading [seed] if
 * the database was never loaded. Store operations go through the traced client, so they show in the
 * Spanner debug UI.
 */
fun spannerStore(connection: SpannerConnection, seed: UserDataSeed): SpannerUserDataStore {
  SpannerSchema.apply(connection.databaseAdmin, connection.settings)
  val store = SpannerUserDataStore(connection.databaseClient, seed)
  if (store.dataEpoch == SpannerUserDataStore.NO_EPOCH) store.reset()
  return store
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
