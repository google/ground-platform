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

/** The signed-in account in a real SQLite database (JDBC), including databases from version 1. */
class SqlDelightSignedInSessionStoreTest {
  private val databaseFile = Files.createTempFile("collect-one-session", ".db").toFile()
  private val connection = LocalDatabaseConnection(JdbcSqlDriverFactory(databaseFile))
  private val store = SqlDelightSignedInSessionStore(connection)

  private val signIn =
    StoredSignIn(
      email = "maya.lin@example.org",
      displayName = "Maya Lin",
      organization = "Field team",
      photoUrl = "https://example.org/maya.png",
      termsAcceptedEmail = "maya.lin@example.org",
    )

  @AfterTest
  fun deleteDatabase() {
    connection.closeNow()
    databaseFile.delete()
  }

  @Test
  fun newDatabase_hasNoSignIn() {
    assertNull(runBlocking { store.load() })
  }

  @Test
  fun savedSignIn_survivesReopeningTheDatabase() {
    runBlocking {
      store.save(signIn)
      connection.close()
    }

    val reopened = SqlDelightSignedInSessionStore(connection)

    assertEquals(signIn, runBlocking { reopened.load() })
  }

  @Test
  fun savingAgain_replacesTheSignIn() {
    val other = StoredSignIn(email = "ima.user@example.org", displayName = "Ima", organization = "")

    runBlocking {
      store.save(signIn)
      store.save(other)
    }

    assertEquals(other, runBlocking { store.load() })
  }

  @Test
  fun clearing_forgetsTheSignIn() {
    runBlocking {
      store.save(signIn)
      store.clear()
    }

    assertNull(runBlocking { store.load() })
  }

  @Test
  fun versionOneDatabase_isMigratedWithItsOutboxKept() {
    // A database of the first released schema (src/commonMain/sqldelight/databases/1.db) holding
    // one unsent edit.
    File(VERSION_ONE_SCHEMA).copyTo(databaseFile, overwrite = true)
    val pending = PendingMutationRecord(mutation_id = "m1", client_seq = 1L)
    DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { jdbc ->
      jdbc.createStatement().use { it.execute("PRAGMA user_version = 1") }
      jdbc.prepareStatement("INSERT INTO outbox_mutation VALUES (?, ?, ?)").use {
        it.setString(1, "m1")
        it.setLong(2, 1L)
        it.setBytes(3, PendingMutationRecord.ADAPTER.encode(pending))
        it.executeUpdate()
      }
    }

    runBlocking { store.save(signIn) }

    assertEquals(signIn, runBlocking { store.load() })
    assertEquals(
      listOf(pending),
      runBlocking { SqlDelightSyncStateStore(connection).load() }.outbox,
    )
  }

  @Test
  fun versionThreeDatabase_tiesAnAcceptanceToTheStoredAccount() {
    val accepted = versionThreeSignIn(email = "maya.lin@example.org", hasAcceptedTerms = true)

    assertEquals("maya.lin@example.org", accepted?.termsAcceptedEmail)
    assertEquals("Maya Lin", accepted?.displayName)
  }

  @Test
  fun versionThreeDatabase_withoutAnAcceptance_keepsTheAccountWithoutOne() {
    val notAccepted = versionThreeSignIn(email = "maya.lin@example.org", hasAcceptedTerms = false)

    assertEquals("maya.lin@example.org", notAccepted?.email)
    assertNull(notAccepted?.termsAcceptedEmail)
  }

  /**
   * The sign-in read back from a database of schema version 3 (src/commonMain/sqldelight/databases/
   * 3.db), whose signed-in session kept the terms as a flag, after migrating it.
   */
  private fun versionThreeSignIn(email: String, hasAcceptedTerms: Boolean): StoredSignIn? {
    File(VERSION_THREE_SCHEMA).copyTo(databaseFile, overwrite = true)
    DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { jdbc ->
      jdbc.createStatement().use { it.execute("PRAGMA user_version = 3") }
      jdbc
        .prepareStatement("INSERT INTO signed_in_session VALUES (0, ?, 'Maya Lin', '', NULL, ?)")
        .use {
          it.setString(1, email)
          it.setLong(2, if (hasAcceptedTerms) 1L else 0L)
          it.executeUpdate()
        }
    }
    return runBlocking { store.load() }
  }

  private companion object {
    const val VERSION_ONE_SCHEMA = "src/commonMain/sqldelight/databases/1.db"
    const val VERSION_THREE_SCHEMA = "src/commonMain/sqldelight/databases/3.db"
  }
}
