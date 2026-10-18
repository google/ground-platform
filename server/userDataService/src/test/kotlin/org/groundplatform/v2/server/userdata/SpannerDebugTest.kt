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

import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.Statement
import com.google.cloud.spanner.Value
import com.linecorp.armeria.server.Server
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * End-to-end test of the Spanner debug UI's backend against the Spanner emulator. Skipped unless
 * `SPANNER_EMULATOR_HOST` is set (for example by running `scripts/spanner-emulator.sh`).
 */
class SpannerDebugTest {
  private val emulatorHost: String? = System.getenv("SPANNER_EMULATOR_HOST")
  private lateinit var debug: SpannerDebug
  private lateinit var server: Server
  private val http = HttpClient.newHttpClient()

  @BeforeTest
  fun setUp() {
    assumeTrue(!emulatorHost.isNullOrBlank(), "Set SPANNER_EMULATOR_HOST to run")
    val settings =
      SpannerSettings(
        projectId = "test-project",
        instanceId = "test-instance",
        databaseId = "debug-" + UUID.randomUUID().toString().take(8),
        emulatorHost = emulatorHost,
      )
    val recorder = SpannerOperationRecorder()
    debug = SpannerDebug(SpannerConnection.open(settings, recorder), recorder)
    debug.connection.databaseAdmin
      .updateDatabaseDdl(
        settings.instanceId,
        settings.databaseId,
        listOf(
          """CREATE TABLE Surveys (
               SurveyId STRING(36) NOT NULL,
               Title STRING(MAX),
               CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true)
             ) PRIMARY KEY (SurveyId)""",
          """CREATE TABLE Entities (
               SurveyId STRING(36) NOT NULL,
               EntityId STRING(36) NOT NULL,
               Label STRING(MAX),
               LabelUpper STRING(MAX) AS (UPPER(Label)) STORED
             ) PRIMARY KEY (SurveyId, EntityId),
               INTERLEAVE IN PARENT Surveys ON DELETE CASCADE""",
          "CREATE INDEX EntitiesByLabel ON Entities(SurveyId, Label), INTERLEAVE IN Surveys",
        ),
        null,
      )
      .get()
    debug.connection.databaseClient.write(
      listOf(
        Mutation.newInsertBuilder("Surveys")
          .set("SurveyId")
          .to(SURVEY)
          .set("Title")
          .to("Coffee plots")
          .set("CommitTs")
          .to(Value.COMMIT_TIMESTAMP)
          .build()
      ) +
        listOf("e1" to "north", "e2" to "south", "e3" to "west").map { (id, label) ->
          Mutation.newInsertBuilder("Entities")
            .set("SurveyId")
            .to(SURVEY)
            .set("EntityId")
            .to(id)
            .set("Label")
            .to(label)
            .build()
        }
    )
    server = UserDataServer.create(UserDataBackend(), port = 0, spannerDebug = debug)
    server.start().join()
  }

  @AfterTest
  fun tearDown() {
    if (!::debug.isInitialized) return
    server.stop().join()
    val settings = debug.connection.settings
    debug.connection.databaseAdmin.dropDatabase(settings.instanceId, settings.databaseId)
    debug.connection.close()
  }

  @Test
  fun inspector_describesTablesKeysIndexesAndGeneratedColumns() {
    val tables = debug.inspector.tables()

    assertEquals(listOf("Entities", "Surveys"), tables.map { it.name })
    val entities = tables.first { it.name == "Entities" }
    assertEquals("Surveys", entities.parent)
    assertEquals("CASCADE", entities.onDelete)
    assertEquals(
      listOf("SurveyId", "EntityId"),
      entities.primaryKey.map { it.substringBefore(' ') },
    )
    assertEquals("UPPER(Label)", entities.columns.first { it.name == "LabelUpper" }.generatedAs)
    val index = entities.indexes.single { it.name == "EntitiesByLabel" }
    assertEquals("Surveys", index.interleavedIn)
    assertEquals(listOf("SurveyId", "Label"), index.keyColumns.map { it.substringBefore(' ') })
    assertTrue(debug.inspector.ddl().any { "CREATE TABLE Surveys" in it })
  }

  @Test
  fun inspector_pagesRowsInKeyOrder_andCountsThem() {
    val first = debug.inspector.rows("Entities", limit = 2, offset = 0)
    val second = debug.inspector.rows("Entities", limit = 2, offset = 2)

    val entityId = first.columns.indexOfFirst { it.name == "EntityId" }
    assertEquals(listOf("e1", "e2"), first.rows.map { it[entityId] })
    assertTrue(first.hasMore)
    assertEquals(listOf("e3"), second.rows.map { it[entityId] })
    assertTrue(!second.hasMore)
    val upper = first.columns.indexOfFirst { it.name == "LabelUpper" }
    assertEquals("NORTH", first.rows[0][upper])
    assertEquals(3L, debug.inspector.rowCount("Entities"))
    assertFailsWith<IllegalArgumentException> { debug.inspector.rows("Nope", 10, 0) }
  }

  @Test
  fun recorder_tracksServiceOperations_butNotTableBrowsing() {
    debug.connection.databaseClient.readWriteTransaction().run { tx ->
      tx.executeUpdate(
        Statement.of("UPDATE Surveys SET Title = 'Renamed' WHERE SurveyId = '$SURVEY'")
      )
    }
    val result = debug.inspector.runQuery("SELECT COUNT(*) AS n FROM Entities")
    debug.inspector.tables()

    assertEquals(listOf(listOf<Any?>(3L)), result.rows)
    val operations = debug.recorder.snapshot()
    assertTrue(operations.any { it.category == "commit" }, operations.map { it.name }.toString())
    assertTrue(
      operations.any { it.category == "transaction" },
      operations.map { it.name }.toString(),
    )
    assertTrue(operations.any { it.statement?.contains("COUNT(*)") == true })
    assertTrue(operations.any { it.statement?.startsWith("UPDATE Surveys") == true })
    assertTrue(operations.none { it.statement?.contains("INFORMATION_SCHEMA") == true })
    assertFailsWith<SpannerException> {
      debug.inspector.runQuery("DELETE FROM Entities WHERE TRUE")
    }
  }

  @Test
  fun httpApi_servesThePageTablesRowsAndQueries() {
    val page = get("/debug/spanner")
    assertEquals(200, page.statusCode())
    assertTrue("Spanner Inspector" in page.body())

    val status = get("/debug/spanner/api/status").body()
    assertTrue("\"configured\":true" in status, status)
    val tables = get("/debug/spanner/api/tables").body()
    assertTrue("\"EntitiesByLabel\"" in tables, tables)
    val rows = get("/debug/spanner/api/tables/Surveys/rows?limit=10").body()
    assertTrue("Coffee plots" in rows, rows)
    assertEquals(400, get("/debug/spanner/api/tables/Nope/rows").statusCode())

    val query =
      http.send(
        HttpRequest.newBuilder(uri("/debug/spanner/api/query"))
          .header("content-type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"sql":"SELECT Title FROM Surveys"}"""))
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, query.statusCode())
    assertTrue("Coffee plots" in query.body(), query.body())
    val operations = get("/debug/spanner/api/operations").body()
    assertTrue("SELECT Title FROM Surveys" in operations, operations)
  }

  private fun uri(path: String) = URI("http://127.0.0.1:${server.activeLocalPort()}$path")

  private fun get(path: String): HttpResponse<String> =
    http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString())

  companion object {
    const val SURVEY = "survey-1"
  }
}
