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
package org.groundplatform.v2.devtools.prototypeapp.data.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.devtools.prototypeapp.data.datasource.device.PrototypeAuthDataSource
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.AuthSession
import org.groundplatform.v2.devtools.prototypeapp.domain.repository.AuthRepository

/** [AuthRepository] backed by [PrototypeAuthDataSource]. */
class AuthRepositoryImpl(
  private val dataSource: PrototypeAuthDataSource = PrototypeAuthDataSource()
) : AuthRepository {
  override fun observeSession(): Flow<AuthSession> = dataSource.session

  override suspend fun getSession(): AuthSession = dataSource.session.value

  override suspend fun signInWithGoogle(): AuthProfile = dataSource.signInWithGoogle()

  override suspend fun signOut() = dataSource.signOut()
}
