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
package org.groundplatform.v2.app.data.repository

import kotlinx.coroutines.flow.Flow
import org.groundplatform.v2.app.data.datasource.device.AuthDataSource
import org.groundplatform.v2.app.domain.model.AuthProfile
import org.groundplatform.v2.app.domain.model.AuthSession
import org.groundplatform.v2.app.domain.model.SignInMode
import org.groundplatform.v2.app.domain.model.SimulatedRole
import org.groundplatform.v2.app.domain.repository.AuthRepository

/** [AuthRepository] backed by an [AuthDataSource]. */
class AuthRepositoryImpl(private val dataSource: AuthDataSource = AuthDataSource()) :
  AuthRepository {
  override fun observeSession(): Flow<AuthSession> = dataSource.session

  override suspend fun getSession(): AuthSession = dataSource.session.value

  override suspend fun signInWithGoogle(role: SimulatedRole?): AuthProfile =
    dataSource.signInWithGoogle(role)

  override fun signInMode(): SignInMode = dataSource.signInMode()

  override suspend fun signOut() = dataSource.signOut()
}
