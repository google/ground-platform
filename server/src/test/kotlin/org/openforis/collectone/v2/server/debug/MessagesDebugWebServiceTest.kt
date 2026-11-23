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
package org.openforis.collectone.v2.server.debug

import com.linecorp.armeria.server.Server
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.app.UserDataServer
import org.openforis.collectone.v2.server.auth.Caller
import org.openforis.collectone.v2.server.auth.CallerElement
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.messaging.EmailInviteNotifier
import org.openforis.collectone.v2.server.messaging.LoggingEmailSender
import org.openforis.collectone.v2.server.messaging.MessageLogRecorder
import org.openforis.collectone.v2.server.service.UserDataBackend

class MessagesDebugWebServiceTest {
  private lateinit var server: Server
  private lateinit var backend: UserDataBackend
  private lateinit var messageLogRecorder: MessageLogRecorder
  private lateinit var inviteNotifier: EmailInviteNotifier

  @BeforeTest
  fun setUp() {
    messageLogRecorder = MessageLogRecorder()
    inviteNotifier =
      EmailInviteNotifier(
        emailSender = LoggingEmailSender,
        messageLogRecorder = messageLogRecorder,
        publicWebUrl = "https://collectone.example.org",
        async = false,
      )
    backend =
      UserDataBackend(InMemoryUserDataStore(UserDataSeed()), inviteNotifier = inviteNotifier)
    server =
      UserDataServer.create(
        backend = backend,
        port = 0,
        debugUiEnabled = true,
        messageLogRecorder = messageLogRecorder,
        dataResetEnabled = true,
      )
    server.start().join()
  }

  @AfterTest
  fun tearDown() {
    inviteNotifier.close()
    server.stop().join()
  }

  @Test
  fun debugMessagesPage_andInProcessInviteFlow_workEndToEnd() = runTest {
    val port = server.activeLocalPort()
    val client = HttpClient.newHttpClient()

    val pageResp =
      client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/debug/messages")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, pageResp.statusCode())
    assertTrue(pageResp.body().contains("Collect One Message Inspector"))

    val caller =
      Caller(
        userId = "uid-owner",
        email = "organizer@example.org",
        emailVerified = true,
        displayName = "Alex Rivera",
        isAdmin = true,
      )
    withContext(CallerElement(caller)) {
      backend.registerCaller(caller)
      backend.MutateSurvey(
        MutateSurveyRequest(
          survey_id = "survey-123",
          mutations =
            listOf(
              SurveyMutation(
                client_id = "c1",
                client_seq = 1L,
                upsert = SurveyDef(survey_id = "survey-123", title = "Test Survey"),
                acl_upsert =
                  SurveyAcl(
                    entries =
                      listOf(
                        AclEntry(
                          email = "invitee@example.org",
                          role = Role.SURVEY_ORGANIZER,
                          invitation_status = AclEntry.InvitationStatus.PENDING,
                          invite_token = "tok-abc",
                        )
                      )
                  ),
              )
            ),
        )
      )
    }

    val messagesJson =
      client
        .send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/debug/messages/api/messages"))
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString(),
        )
        .body()
    assertTrue(messagesJson.contains("invitee@example.org"))
    assertTrue(messagesJson.contains("https://collectone.example.org/#join/survey-123/tok-abc"))

    val clearResp =
      client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/debug/messages/api/clear"))
          .POST(HttpRequest.BodyPublishers.noBody())
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    assertEquals(200, clearResp.statusCode())
    assertEquals(
      "[]",
      client
        .send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/debug/messages/api/messages"))
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString(),
        )
        .body(),
    )
  }
}
