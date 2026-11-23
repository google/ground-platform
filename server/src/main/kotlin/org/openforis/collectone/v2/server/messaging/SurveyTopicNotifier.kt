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
package org.openforis.collectone.v2.server.messaging

import com.google.auth.oauth2.GoogleCredentials
import com.squareup.wire.Instant
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Publishes lightweight data-only Firebase Cloud Messaging (FCM) topic "tickles" when a survey's
 * definition/ACL or entities/plot locks change (`docs/technical/sync-engine.md`, "FCM Topic Tickles
 * (Mobile)").
 *
 * Two topics exist per survey:
 * - `survey_<survey_id>_def`: published when the saved survey definition, forms, dataset schemas,
 *   trash/restore/purge status, or ACL/sharing rules change. Working-copy autosaves do **not**
 *   publish to this topic.
 * - `survey_<survey_id>_entities`: published when entities or plot locks change.
 */
interface SurveyTopicNotifier : AutoCloseable {
  /** Notifies subscribers of `survey_<surveyId>_def` that [recordVersion] was committed. */
  fun notifySurveyDefChanged(surveyId: String, recordVersion: Long)

  /** Notifies subscribers of `survey_<surveyId>_entities` that entities changed at [commitTs]. */
  fun notifySurveyEntitiesChanged(surveyId: String, commitTs: Instant)

  override fun close() {}
}

/** Default no-op [SurveyTopicNotifier] when FCM topic publishing is not enabled. */
object NoopSurveyTopicNotifier : SurveyTopicNotifier {
  override fun notifySurveyDefChanged(surveyId: String, recordVersion: Long) {}

  override fun notifySurveyEntitiesChanged(surveyId: String, commitTs: Instant) {}
}

/** Low-level sender for one FCM topic message. */
fun interface FcmTopicMessageSender {
  fun sendTopicData(topic: String, collapseKey: String, data: Map<String, String>)
}

/**
 * [SurveyTopicNotifier] that sends `survey_<id>_def` tickles immediately and debounces
 * `survey_<id>_entities` tickles over [entityDebounceMs] per survey so burst entity imports or
 * rapid field edits collapse into a single topic publish.
 */
class DebouncedSurveyTopicNotifier(
  private val sender: FcmTopicMessageSender,
  private val entityDebounceMs: Long = 2_000L,
  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r ->
      Thread(r, "fcm-topic-notifier").apply { isDaemon = true }
    },
  private val ownsScheduler: Boolean = true,
) : SurveyTopicNotifier {
  private val pendingEntities = ConcurrentHashMap<String, ScheduledFuture<*>>()
  private val latestCommitMillis = ConcurrentHashMap<String, Long>()

  override fun notifySurveyDefChanged(surveyId: String, recordVersion: Long) {
    if (surveyId.isBlank()) return
    val topic = "survey_${surveyId}_def"
    val data =
      mapOf(
        "survey_id" to surveyId,
        "scope" to "def",
        "record_version" to recordVersion.toString(),
      )
    if (entityDebounceMs <= 0L) {
      runCatching { sender.sendTopicData(topic, topic, data) }
    } else {
      scheduler.execute { runCatching { sender.sendTopicData(topic, topic, data) } }
    }
  }

  override fun notifySurveyEntitiesChanged(surveyId: String, commitTs: Instant) {
    if (surveyId.isBlank()) return
    val commitMs = commitTs.epochSecond * 1_000L + commitTs.nano / 1_000_000L
    latestCommitMillis.merge(surveyId, commitMs, ::maxOf)
    if (entityDebounceMs <= 0L) {
      flushEntities(surveyId)
      return
    }
    pendingEntities.compute(surveyId) { _, existing ->
      if (existing != null && !existing.isDone) {
        existing
      } else {
        scheduler.schedule({ flushEntities(surveyId) }, entityDebounceMs, TimeUnit.MILLISECONDS)
      }
    }
  }

  private fun flushEntities(surveyId: String) {
    pendingEntities.remove(surveyId)
    val commitMs = latestCommitMillis.remove(surveyId) ?: System.currentTimeMillis()
    val topic = "survey_${surveyId}_entities"
    val data =
      mapOf(
        "survey_id" to surveyId,
        "scope" to "entities",
        "commit_ts" to commitMs.toString(),
      )
    runCatching { sender.sendTopicData(topic, topic, data) }
  }

  override fun close() {
    if (ownsScheduler) {
      scheduler.shutdownNow()
    }
  }
}

/**
 * Sends data-only FCM HTTP v1 messages (`projects/{projectId}/messages:send`) authenticated with
 * Application Default Credentials scoped to `firebase.messaging`.
 */
class FcmHttpV1TopicSender(
  private val projectId: String,
  private val credentials: GoogleCredentials =
    GoogleCredentials.getApplicationDefault()
      .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging")),
  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : FcmTopicMessageSender {
  private val endpoint =
    URI.create("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")

  override fun sendTopicData(topic: String, collapseKey: String, data: Map<String, String>) {
    val token =
      synchronized(credentials) {
        credentials.refreshIfExpired()
        credentials.accessToken?.tokenValue
      } ?: return
    val payload = buildFcmMessageJson(topic = topic, collapseKey = collapseKey, data = data)
    val request =
      HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(10))
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()
    httpClient.send(request, HttpResponse.BodyHandlers.discarding())
  }

  companion object {
    internal fun buildFcmMessageJson(
      topic: String,
      collapseKey: String,
      data: Map<String, String>,
    ): String {
      fun esc(s: String): String =
        buildString(s.length + 4) {
          for (ch in s) {
            when (ch) {
              '\\' -> append("\\\\")
              '"' -> append("\\\"")
              '\n' -> append("\\n")
              '\r' -> append("\\r")
              '\t' -> append("\\t")
              else -> append(ch)
            }
          }
        }
      val dataEntries = data.entries.joinToString(",") { (k, v) -> "\"${esc(k)}\":\"${esc(v)}\"" }
      return """{"message":{"topic":"${esc(topic)}","data":{$dataEntries},"android":{"priority":"normal","collapse_key":"${esc(collapseKey)}"},"apns":{"headers":{"apns-push-type":"background","apns-priority":"5","apns-collapse-id":"${esc(collapseKey)}"},"payload":{"aps":{"content-available":1}}}}}"""
    }
  }
}
