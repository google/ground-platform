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
package org.openforis.collectone.v2.app.di

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import org.koin.dsl.koinApplication
import org.openforis.collectone.v2.app.client.storage.JdbcSqlDriverFactory
import org.openforis.collectone.v2.app.client.storage.SqlDriverFactory
import org.openforis.collectone.v2.app.data.datasource.local.persistence.PersistentLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.data.datasource.remote.SeedUserDataService
import org.openforis.collectone.v2.app.testing.assertEveryDefinitionResolves
import org.openforis.collectone.v2.app.ui.session.TestAppSession

/**
 * The shared Koin graph ([appModules]) with the device's local database, as the mobile hosts
 * configure it: the platform module provides a [SqlDriverFactory] (here SQLite over JDBC).
 */
class AppModulesPersistenceTest {
  private val databaseFile = Files.createTempFile("collect-one-graph", ".db").toFile()
  private val service = SeedUserDataService()

  @AfterTest
  fun deleteDatabase() {
    databaseFile.delete()
  }

  @Test
  fun withLocalDatabase_everyDefinitionResolves() {
    assertEveryDefinitionResolves(
      appModules(
        syncEngineConfig(syncDownloadedSurveysOnly = true),
        platformModule(localDatabase = { JdbcSqlDriverFactory() }),
      )
    )
  }

  @Test
  fun withLocalDatabase_storeWritesThroughToTheDatabase() {
    val holder =
      createAppDataHolder(
        syncEngineConfig(),
        platformModule(localDatabase = { JdbcSqlDriverFactory() }),
      )

    assertIs<PersistentLocalStore>(holder.localStore)
  }

  @Test
  fun withLocalDatabase_relaunchOpensWithTheLastSyncedData() {
    val synced = launch(JdbcSqlDriverFactory(databaseFile)).surveyCount()

    val relaunched = launch(JdbcSqlDriverFactory(databaseFile))

    assertEquals(synced, relaunched.surveyCount())
    assertEquals(UserDataSyncState.Synced, relaunched.userDataSyncState.value)
  }

  @Test
  fun withLocalDatabase_rebuildingSessionsDoesNotRestoreAgain() {
    launch(JdbcSqlDriverFactory(databaseFile))
    val database = RestoreCountingSqlDriverFactory(JdbcSqlDriverFactory(databaseFile))
    val koin = koinApplication {
      modules(appModules(syncEngineConfig(), platformModule(localDatabase = { database })))
    }
      .koin

    // Rebuilding the UI (e.g. rotating an Android device) builds new sessions over the same graph.
    val holder = koin.get<AppDataHolder>()
    TestAppSession(holder).use {}
    TestAppSession(koin.get()).use {}

    assertSame(holder, koin.get<AppDataHolder>())
    assertEquals(1, database.restores)
  }

  @Test
  fun stoppingTheGraph_cancelsTheAppScopeAndClosesTheDatabase() {
    val database = CloseCountingSqlDriverFactory(JdbcSqlDriverFactory(databaseFile))
    val application = koinApplication {
      modules(appModules(syncEngineConfig(), platformModule(localDatabase = { database })))
    }
    val holder = application.koin.get<AppDataHolder>()
    assertEquals(1, database.opened, "the initial sync restores from the database")
    assertTrue(holder.scope.isActive)

    // As the Android app does when it switches to another user data service (stopKoin).
    application.close()

    assertFalse(holder.scope.isActive)
    assertEquals(1, database.closed)
  }

  private fun launch(database: SqlDriverFactory): AppDataHolder =
    createAppDataHolder(syncEngineConfig(), platformModule(localDatabase = { database }))

  private fun syncEngineConfig(syncDownloadedSurveysOnly: Boolean = false): AppConfig {
    val client = service.client()
    return AppConfig(
      syncDownloadedSurveysOnly = syncDownloadedSurveysOnly,
      userDataClient = { client },
      enableSyncEngine = true,
    )
  }

  private fun AppDataHolder.surveyCount() = runDirect { localStore.transaction { surveys().size } }

  /** Counts the drivers [delegate] opened and how many of them were closed. */
  private class CloseCountingSqlDriverFactory(private val delegate: SqlDriverFactory) :
    SqlDriverFactory {
    var opened = 0
      private set

    var closed = 0
      private set

    override fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
      val driver = delegate.create(schema)
      opened++
      return object : SqlDriver by driver {
        override fun close() {
          closed++
          driver.close()
        }
      }
    }
  }

  /**
   * Counts how often the stored sync state is restored: each restore reads whose state the database
   * holds once (`SyncStatePersistence.restore`).
   */
  private class RestoreCountingSqlDriverFactory(private val delegate: SqlDriverFactory) :
    SqlDriverFactory {
    var restores = 0
      private set

    override fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
      val driver = delegate.create(schema)
      return object : SqlDriver by driver {
        override fun <R> executeQuery(
          identifier: Int?,
          sql: String,
          mapper: (SqlCursor) -> QueryResult<R>,
          parameters: Int,
          binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
          if ("FROM stored_account" in sql) restores++
          return driver.executeQuery(identifier, sql, mapper, parameters, binders)
        }
      }
    }
  }
}
