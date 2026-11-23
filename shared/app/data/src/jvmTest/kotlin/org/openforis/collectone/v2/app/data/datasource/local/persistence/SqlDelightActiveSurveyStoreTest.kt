/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.datasource.local.persistence

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.userdata.PendingMutationRecord
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.session.StoredSignIn

/** The active survey in a real SQLite database (JDBC), including databases from version 2. */
class SqlDelightActiveSurveyStoreTest {
  private val databaseFile = Files.createTempFile("collect-one-active-survey", ".db").toFile()
  private val connection = LocalDatabaseConnection(JdbcSqlDriverFactory(databaseFile))
  private val store = SqlDelightActiveSurveyStore(connection)

  @AfterTest
  fun deleteDatabase() {
    connection.closeNow()
    databaseFile.delete()
  }

  @Test
  fun newDatabase_hasNoActiveSurvey() {
    assertNull(runBlocking { store.load() })
  }

  @Test
  fun savedSurvey_survivesReopeningTheDatabase() {
    runBlocking {
      store.save("survey-kenya")
      connection.close()
    }

    val reopened = SqlDelightActiveSurveyStore(connection)

    assertEquals("survey-kenya", runBlocking { reopened.load() })
  }

  @Test
  fun savingAgain_replacesTheSurvey() {
    runBlocking {
      store.save("survey-kenya")
      store.save("survey-brazil")
    }

    assertEquals("survey-brazil", runBlocking { store.load() })
  }

  @Test
  fun clearing_forgetsTheSurvey() {
    runBlocking {
      store.save("survey-kenya")
      store.clear()
    }

    assertNull(runBlocking { store.load() })
  }

  @Test
  fun versionTwoDatabase_isMigratedWithItsOutboxAndSignedInAccountKept() {
    // A database of schema version 2 (src/commonMain/sqldelight/databases/2.db) holding one unsent
    // edit and the signed-in account.
    File(VERSION_TWO_SCHEMA).copyTo(databaseFile, overwrite = true)
    val pending = PendingMutationRecord(mutation_id = "m1", client_seq = 1L)
    DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { jdbc ->
      jdbc.createStatement().use { it.execute("PRAGMA user_version = 2") }
      jdbc.prepareStatement("INSERT INTO outbox_mutation VALUES (?, ?, ?)").use {
        it.setString(1, "m1")
        it.setLong(2, 1L)
        it.setBytes(3, PendingMutationRecord.ADAPTER.encode(pending))
        it.executeUpdate()
      }
      jdbc.createStatement().use {
        it.execute(
          "INSERT INTO signed_in_session VALUES " +
            "(0, 'maya.lin@example.org', 'Maya Lin', 'Field team', NULL, 1)"
        )
      }
    }

    runBlocking { store.save("survey-kenya") }

    assertEquals("survey-kenya", runBlocking { store.load() })
    assertEquals(
      listOf(pending),
      runBlocking { SqlDelightSyncStateStore(connection).load() }.outbox,
    )
    assertEquals(
      StoredSignIn(
        email = "maya.lin@example.org",
        displayName = "Maya Lin",
        organization = "Field team",
        termsAcceptedEmail = "maya.lin@example.org",
      ),
      runBlocking { SqlDelightSignedInSessionStore(connection).load() },
    )
  }

  private companion object {
    const val VERSION_TWO_SCHEMA = "src/commonMain/sqldelight/databases/2.db"
  }
}
