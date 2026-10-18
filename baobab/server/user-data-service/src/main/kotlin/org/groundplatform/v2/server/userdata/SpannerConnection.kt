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

import com.google.cloud.NoCredentials
import com.google.cloud.spanner.DatabaseAdminClient
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.InstanceConfigId
import com.google.cloud.spanner.InstanceId
import com.google.cloud.spanner.InstanceInfo
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.SpannerOptions
import java.util.concurrent.ExecutionException

/** Where the service's Cloud Spanner database lives. */
data class SpannerSettings(
  val projectId: String,
  val instanceId: String,
  val databaseId: String,
  /** `host:port` of the Spanner emulator, or null for Cloud Spanner itself. */
  val emulatorHost: String? = null,
) {
  val database: DatabaseId
    get() = DatabaseId.of(projectId, instanceId, databaseId)

  /** Human-readable target, e.g. `emulator localhost:9010 · projects/p/instances/i/databases/d`. */
  val description: String
    get() = (emulatorHost?.let { "emulator $it · " } ?: "") + database.name

  companion object {
    const val DEFAULT_PROJECT_ID = "collect-one-dev"
    const val DEFAULT_INSTANCE_ID = "dev"
    const val DEFAULT_DATABASE_ID = "user-data"

    /**
     * Reads the settings from environment variables, or returns null when Spanner isn't configured
     * (neither `SPANNER_EMULATOR_HOST` nor `SPANNER_INSTANCE_ID` is set):
     * - `SPANNER_EMULATOR_HOST`: emulator address, e.g. `localhost:9010`.
     * - `SPANNER_PROJECT_ID`, `SPANNER_INSTANCE_ID`, `SPANNER_DATABASE_ID`: the database (defaults
     *   [DEFAULT_PROJECT_ID], [DEFAULT_INSTANCE_ID], [DEFAULT_DATABASE_ID]).
     */
    fun fromEnv(env: Map<String, String> = System.getenv()): SpannerSettings? {
      val emulatorHost = env["SPANNER_EMULATOR_HOST"]?.takeIf { it.isNotBlank() }
      val instanceId = env["SPANNER_INSTANCE_ID"]?.takeIf { it.isNotBlank() }
      if (emulatorHost == null && instanceId == null) return null
      return SpannerSettings(
        projectId = env["SPANNER_PROJECT_ID"]?.takeIf { it.isNotBlank() } ?: DEFAULT_PROJECT_ID,
        instanceId = instanceId ?: DEFAULT_INSTANCE_ID,
        databaseId = env["SPANNER_DATABASE_ID"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATABASE_ID,
        emulatorHost = emulatorHost,
      )
    }
  }
}

/**
 * Open clients for one Spanner database.
 *
 * Two independent [Spanner] instances keep debugging out of the operation log:
 * - [databaseClient] is for service code. Its OpenTelemetry spans go to the
 *   [SpannerOperationRecorder], so every query, read, and commit shows up in the Spanner debug UI.
 * - [inspectorClient] and [databaseAdmin] serve the debug UI's own table browsing, which isn't
 *   recorded.
 */
class SpannerConnection
private constructor(
  val settings: SpannerSettings,
  private val tracedSpanner: Spanner,
  private val plainSpanner: Spanner,
) : AutoCloseable {
  val databaseClient: DatabaseClient = tracedSpanner.getDatabaseClient(settings.database)

  val inspectorClient: DatabaseClient = plainSpanner.getDatabaseClient(settings.database)

  val databaseAdmin: DatabaseAdminClient = plainSpanner.databaseAdminClient

  override fun close() {
    tracedSpanner.close()
    plainSpanner.close()
  }

  companion object {
    /**
     * Connects to the database in [settings], recording [databaseClient] operations in [recorder].
     * On the emulator, creates the instance and an empty database first if they don't exist.
     */
    fun open(settings: SpannerSettings, recorder: SpannerOperationRecorder): SpannerConnection {
      SpannerOptions.enableOpenTelemetryTraces()
      val traced =
        optionsFor(settings)
          .setOpenTelemetry(recorder.openTelemetry)
          // Adds the SQL text (`db.statement`) to query spans.
          .setEnableExtendedTracing(true)
          .build()
          .service
      val plain = optionsFor(settings).build().service
      if (settings.emulatorHost != null) ensureEmulatorDatabase(plain, settings)
      return SpannerConnection(settings, traced, plain)
    }

    private fun optionsFor(settings: SpannerSettings): SpannerOptions.Builder {
      val builder = SpannerOptions.newBuilder().setProjectId(settings.projectId)
      if (settings.emulatorHost != null) {
        builder.setEmulatorHost(settings.emulatorHost).setCredentials(NoCredentials.getInstance())
      }
      return builder
    }

    private fun ensureEmulatorDatabase(spanner: Spanner, settings: SpannerSettings) {
      val instance = InstanceId.of(settings.projectId, settings.instanceId)
      ignoreAlreadyExists {
        spanner.instanceAdminClient
          .createInstance(
            InstanceInfo.newBuilder(instance)
              .setInstanceConfigId(InstanceConfigId.of(settings.projectId, "emulator-config"))
              .setDisplayName(settings.instanceId)
              .setNodeCount(1)
              .build()
          )
          .get()
      }
      ignoreAlreadyExists {
        spanner.databaseAdminClient
          .createDatabase(settings.instanceId, settings.databaseId, emptyList())
          .get()
      }
    }

    private fun ignoreAlreadyExists(create: () -> Unit) {
      try {
        create()
      } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause !is SpannerException || cause.errorCode != ErrorCode.ALREADY_EXISTS) throw e
      } catch (e: SpannerException) {
        if (e.errorCode != ErrorCode.ALREADY_EXISTS) throw e
      }
    }
  }
}
