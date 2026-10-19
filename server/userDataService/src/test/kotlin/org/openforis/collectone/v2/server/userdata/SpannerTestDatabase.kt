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

import groundplatform.v2.userdata.UserDataSeed
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The Spanner emulator the `spanner`-tagged tests (`./gradlew spannerTest`) run against: the one at
 * `SPANNER_EMULATOR_HOST` when that's set, otherwise one started from `scripts/spanner-emulator.sh`
 * on free ports for this test JVM and stopped when it exits. The tests use their own instance and
 * databases, so they can share an emulator with a running development server.
 */
object TestSpannerEmulator {
  /** How long to wait for an emulator this JVM starts (the first run downloads it). */
  private val START_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5)

  /** Whether this JVM started the emulator, which then stops (and loses its data) on exit. */
  var startedHere = false
    private set

  val host: String by lazy {
    System.getenv("SPANNER_EMULATOR_HOST")?.takeIf { it.isNotBlank() } ?: start()
  }

  private fun start(): String {
    val script =
      generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "scripts/spanner-emulator.sh") }
        .firstOrNull { it.isFile }
        ?: error("scripts/spanner-emulator.sh not found; set SPANNER_EMULATOR_HOST instead")
    val grpcPort = freePort()
    val restPort = freePort()
    val log = File.createTempFile("spanner-emulator", ".log").apply { deleteOnExit() }
    val process =
      ProcessBuilder("bash", script.path)
        .redirectErrorStream(true)
        .redirectOutput(log)
        .apply {
          environment()["SPANNER_EMULATOR_GRPC_PORT"] = grpcPort.toString()
          environment()["SPANNER_EMULATOR_REST_PORT"] = restPort.toString()
        }
        .start()
    // SIGTERM makes the script stop the emulator it started.
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          process.destroy()
          process.waitFor(10, TimeUnit.SECONDS)
        }
      )
    val deadline = System.nanoTime() + START_TIMEOUT_NANOS
    while (!accepts(grpcPort)) {
      check(process.isAlive) { "The Spanner emulator exited:\n${log.readText()}" }
      check(System.nanoTime() < deadline) {
        "The Spanner emulator didn't start:\n${log.readText()}"
      }
      Thread.sleep(200)
    }
    startedHere = true
    return "localhost:$grpcPort"
  }

  private fun freePort(): Int = ServerSocket(0).use { it.localPort }

  private fun accepts(port: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress("localhost", port), 200) }
  }
    .isSuccess
}

/**
 * One emulator database with the schema applied, shared by the tests (each [store] resets it). On
 * an emulator this JVM didn't start, the test databases are dropped when it exits.
 */
object SpannerTestDatabase {
  private val connection: SpannerConnection by lazy { openDatabase() }

  fun applySchema() = SpannerSchema.apply(connection.databaseAdmin, connection.settings)

  fun updateDdl(vararg statements: String) {
    val settings = connection.settings
    connection.databaseAdmin
      .updateDatabaseDdl(settings.instanceId, settings.databaseId, statements.toList(), null)
      .get()
  }

  fun ddl(): List<String> =
    connection.databaseAdmin.getDatabaseDdl(
      connection.settings.instanceId,
      connection.settings.databaseId,
    )

  /** A store on the shared database, reset to [seed]. */
  fun store(seed: UserDataSeed = UserDataSeed()): SpannerUserDataStore =
    SpannerUserDataStore(connection.databaseClient, seed).also { it.reset() }

  /** A backend over [store] loaded with the bundled sample seed. */
  fun sampleBackend(): UserDataBackend = UserDataBackend(store(loadSeed(null)))

  private fun openDatabase(): SpannerConnection {
    val settings =
      SpannerSettings(
        projectId = "test-project",
        instanceId = "test-instance",
        databaseId = "store-" + UUID.randomUUID().toString().take(8),
        emulatorHost = TestSpannerEmulator.host,
      )
    val connection = SpannerConnection.open(settings, SpannerOperationRecorder())
    SpannerSchema.apply(connection.databaseAdmin, settings)
    // An emulator this JVM started stops at exit too (calls to it would then hang).
    if (!TestSpannerEmulator.startedHere) {
      Runtime.getRuntime()
        .addShutdownHook(
          Thread {
            runCatching {
              CompletableFuture.runAsync {
                  connection.databaseAdmin.dropDatabase(settings.instanceId, settings.databaseId)
                }
                .get(10, TimeUnit.SECONDS)
            }
          }
        )
    }
    return connection
  }
}
