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
package org.openforis.collectone.v2.app.ui.session

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import org.openforis.collectone.v2.app.client.grpc.GrpcWebException
import org.openforis.collectone.v2.app.di.BackendOperationError
import org.openforis.collectone.v2.app.di.UserDataSyncState
import org.openforis.collectone.v2.app.domain.error.DomainError

/** The sync error banners show why something failed in the user's words, never transport text. */
@OptIn(ExperimentalTestApi::class)
class UserDataServiceErrorBannersTest {
  private val transportFailure =
    GrpcWebException(16, "/SurveyService/ListSurveys", "Sign in: missing ID token")

  @Test
  fun syncErrorBanner_showsTheLocalizedReason() = runDesktopComposeUiTest {
    setContent {
      MaterialTheme {
        UserDataSyncErrorBanner(
          state = UserDataSyncState.Failed(serviceUrl = null, error = DomainError.Unauthenticated),
          onRetry = {},
        )
      }
    }

    onNodeWithText("Your session has expired. Sign in again.").assertIsDisplayed()
    onAllNodesWithText("Sign in: missing ID token", substring = true).assertCountEquals(0)
  }

  @Test
  fun backendErrorBanner_showsTheLocalizedReason_notTheServerMessage() = runDesktopComposeUiTest {
    setContent {
      MaterialTheme {
        BackendErrorBanner(
          error =
            BackendOperationError(
              key = "sync:Unknown",
              error = DomainError.Unknown(transportFailure),
              canRetry = true,
            ),
          onRetry = {},
          onDismiss = {},
        )
      }
    }

    onNodeWithText("Something went wrong. Try again.").assertIsDisplayed()
    onAllNodesWithText("gRPC status", substring = true).assertCountEquals(0)
  }
}
