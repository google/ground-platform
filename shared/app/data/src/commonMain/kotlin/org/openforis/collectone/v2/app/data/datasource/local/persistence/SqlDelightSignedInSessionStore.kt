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

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import org.openforis.collectone.v2.app.data.datasource.local.session.SignedInSessionStore
import org.openforis.collectone.v2.app.data.datasource.local.session.StoredSignIn

/** [SignedInSessionStore] in the local database's `signed_in_session` table (one row). */
class SqlDelightSignedInSessionStore(private val connection: LocalDatabaseConnection) :
  SignedInSessionStore {
  override suspend fun load(): StoredSignIn? = connection.withDatabase { db ->
    db.signedInSessionQueries.select().awaitAsOneOrNull()?.let {
      StoredSignIn(
        email = it.email,
        displayName = it.display_name,
        organization = it.organization,
        photoUrl = it.photo_url,
        termsAcceptedEmail = it.terms_accepted_email,
      )
    }
  }

  override suspend fun save(signIn: StoredSignIn) {
    connection.withDatabase { db ->
      db.signedInSessionQueries.put(
        email = signIn.email,
        display_name = signIn.displayName,
        organization = signIn.organization,
        photo_url = signIn.photoUrl,
        terms_accepted_email = signIn.termsAcceptedEmail,
      )
    }
  }

  override suspend fun clear() {
    connection.withDatabase { db -> db.signedInSessionQueries.delete() }
  }
}
