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
package org.openforis.collectone.v2.app.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import org.openforis.collectone.v2.app.client.auth.FakeAuthClient
import org.openforis.collectone.v2.app.data.datasource.local.store.InMemoryLocalStore
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationPayload
import org.openforis.collectone.v2.app.data.datasource.local.store.MutationTarget
import org.openforis.collectone.v2.app.data.datasource.local.store.OutboxState
import org.openforis.collectone.v2.app.data.datasource.local.store.PendingMutation
import org.openforis.collectone.v2.app.data.datasource.local.store.runDirect
import org.openforis.collectone.v2.app.domain.error.DomainError
import org.openforis.collectone.v2.app.domain.repository.ConnectivityRepository

/** [BackendErrorMonitor] picks which backend error the UI shows. */
class BackendErrorMonitorTest {
  private val store = InMemoryLocalStore()
  private val connectivity = ConnectivityRepository()
  private val initialSyncState = MutableStateFlow<UserDataSyncState>(UserDataSyncState.Synced)
  private val monitor =
    BackendErrorMonitor(
      localStore = store,
      syncCoordinator = null,
      connectivityRepository = connectivity,
      initialSyncState = initialSyncState,
      authClient = FakeAuthClient(),
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

  private fun putOutbox(vararg mutations: PendingMutation) = runDirect {
    store.transaction { putOutbox(mutations.toList()) }
  }

  private fun surveyEdit(
    id: String,
    state: OutboxState,
    lastError: DomainError?,
    lastErrorDetail: String? = null,
  ) =
    PendingMutation(
      mutationId = id,
      clientSeq = 1L,
      target = MutationTarget.Survey("s1", workingCopy = true),
      recordKey = "survey/s1",
      payload = MutationPayload.Survey(SurveyMutation(upsert = SurveyDef(title = "Edited"))),
      state = state,
      lastError = lastError,
      lastErrorDetail = lastErrorDetail,
    )

  @Test
  fun withNothingWrong_showsNoError() {
    assertNull(monitor.error.value)
  }

  @Test
  fun retryingEdit_isShownAsRetryable() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      )
    )

    assertEquals(
      BackendOperationError(
        key = "retrying:m1:Service unavailable",
        error = DomainError.Network,
        canRetry = true,
      ),
      monitor.error.value,
    )
  }

  @Test
  fun retryingEdit_isHiddenWhileOffline() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      )
    )

    connectivity.setOnline(false)

    assertNull(monitor.error.value)
  }

  @Test
  fun retryingEdit_isHiddenWhileTheInitialSyncFailureIsShown() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      )
    )

    initialSyncState.value =
      UserDataSyncState.Failed(serviceUrl = null, error = DomainError.Network)

    assertNull(monitor.error.value)
  }

  @Test
  fun rejectedEdit_takesPrecedenceOverARetryingOne() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.REJECTED,
        lastError = DomainError.Conflict,
        lastErrorDetail = "Survey is closed",
      ),
      surveyEdit(
        "m2",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      ),
    )

    assertEquals("rejected:m1", monitor.error.value?.key)
  }

  @Test
  fun dismissedRetryingEdit_isNoLongerShown() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      )
    )

    monitor.dismiss()

    assertNull(monitor.error.value)
  }

  @Test
  fun retry_showsADismissedRetryingEditAgain() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.RETRYING,
        lastError = DomainError.Network,
        lastErrorDetail = "Service unavailable",
      )
    )
    monitor.dismiss()

    monitor.retry()

    assertTrue(monitor.error.value?.canRetry == true)
  }

  @Test
  fun rejectedEdit_isShownWithItsReason_andCannotBeRetried() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.REJECTED,
        lastError = DomainError.PermissionDenied,
        lastErrorDetail = "gRPC status 7 for /MutateSurvey",
      )
    )

    assertEquals(
      BackendOperationError(
        key = "rejected:m1",
        error = DomainError.PermissionDenied,
        canRetry = false,
      ),
      monitor.error.value,
    )
  }

  @Test
  fun rejectedEdit_isHiddenWhileOffline() {
    putOutbox(
      surveyEdit(
        "m1",
        OutboxState.REJECTED,
        lastError = DomainError.PermissionDenied,
        lastErrorDetail = "gRPC status 7 for /MutateSurvey",
      )
    )

    connectivity.setOnline(false)

    assertNull(monitor.error.value)
  }

  @Test
  fun rejectedEdit_withoutAReason_isShownAsUnknown() {
    putOutbox(surveyEdit("m1", OutboxState.REJECTED, lastError = null))

    assertEquals(DomainError.Unknown(), monitor.error.value?.error)
  }

  @Test
  fun retryingEdit_withoutAReason_isNotShown() {
    putOutbox(surveyEdit("m1", OutboxState.RETRYING, lastError = null))

    assertNull(monitor.error.value)
  }
}
