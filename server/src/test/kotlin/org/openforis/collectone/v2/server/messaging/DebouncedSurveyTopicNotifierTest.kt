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

import com.squareup.wire.ofEpochSecond
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The debouncing of [DebouncedSurveyTopicNotifier] on a [ManualScheduler], and the FCM message JSON
 * of [FcmHttpV1TopicSender].
 */
class DebouncedSurveyTopicNotifierTest {
  private val sent = mutableListOf<Pair<String, Map<String, String>>>()
  private val scheduler = ManualScheduler()
  private val notifier =
    DebouncedSurveyTopicNotifier(
      sender = { topic, _, data -> sent += topic to data },
      entityDebounceMs = 2_000L,
      scheduler = scheduler,
      ownsScheduler = false,
    )

  @Test
  fun entityTickles_collapseIntoOne_withTheLatestCommitTime() {
    notifier.notifySurveyEntitiesChanged("s1", ofEpochSecond(10L, 500_000_000L))
    notifier.notifySurveyEntitiesChanged("s1", ofEpochSecond(12L, 0L))
    notifier.notifySurveyEntitiesChanged("s1", ofEpochSecond(11L, 0L))
    scheduler.advanceBy(1_999L)
    assertEquals(emptyList(), sent)

    scheduler.advanceBy(1L)

    assertEquals(
      listOf(
        "survey_s1_entities" to
          mapOf("survey_id" to "s1", "scope" to "entities", "commit_ts" to "12000")
      ),
      sent,
    )
  }

  @Test
  fun entityTickles_debouncePerSurvey_andStartOverAfterAPublish() {
    notifier.notifySurveyEntitiesChanged("s1", ofEpochSecond(1L, 0L))
    scheduler.advanceBy(1_000L)
    notifier.notifySurveyEntitiesChanged("s2", ofEpochSecond(2L, 0L))
    scheduler.advanceBy(1_000L)
    notifier.notifySurveyEntitiesChanged("s1", ofEpochSecond(3L, 0L))
    scheduler.advanceBy(1_000L)
    scheduler.advanceBy(2_000L)

    assertEquals(
      listOf(
        "survey_s1_entities" to "1000",
        "survey_s2_entities" to "2000",
        "survey_s1_entities" to "3000",
      ),
      sent.map { (topic, data) -> topic to data.getValue("commit_ts") },
    )
  }

  @Test
  fun defTickles_areSentWithoutDebouncing_offTheCallersThread() {
    notifier.notifySurveyDefChanged("s1", recordVersion = 7L)
    notifier.notifySurveyDefChanged("s1", recordVersion = 8L)
    assertEquals(emptyList(), sent)

    scheduler.advanceBy(0L)

    assertEquals(listOf("7", "8"), sent.map { it.second.getValue("record_version") })
    assertEquals(setOf("survey_s1_def"), sent.map { it.first }.toSet())
  }

  @Test
  fun ticklesWithoutASurvey_areDropped_andSendFailuresDontEscape() {
    val failing =
      DebouncedSurveyTopicNotifier(
        sender = { _, _, _ -> error("FCM is down") },
        entityDebounceMs = 0L,
      )

    notifier.notifySurveyEntitiesChanged("", ofEpochSecond(1L, 0L))
    notifier.notifySurveyDefChanged(" ", recordVersion = 1L)
    failing.notifySurveyEntitiesChanged("s1", ofEpochSecond(1L, 0L))
    failing.notifySurveyDefChanged("s1", recordVersion = 1L)
    failing.close()
    scheduler.advanceBy(10_000L)

    assertEquals(emptyList(), sent)
  }

  @Test
  fun fcmMessages_escapeTheirStrings_andAreBackgroundPushes() {
    val json =
      FcmHttpV1TopicSender.buildFcmMessageJson(
        topic = "survey_s1_def",
        collapseKey = "key\"1",
        data = mapOf("note" to "a\\b\n\tc\r"),
      )

    assertTrue("\"topic\":\"survey_s1_def\"" in json, json)
    assertTrue("\"data\":{\"note\":\"a\\\\b\\n\\tc\\r\"}" in json, json)
    assertTrue("\"collapse_key\":\"key\\\"1\"" in json, json)
    assertTrue("\"apns-collapse-id\":\"key\\\"1\"" in json, json)
    assertTrue("\"apns-push-type\":\"background\"" in json, json)
    assertTrue("\"content-available\":1" in json, json)
  }
}

/**
 * A [ScheduledExecutorService] that runs tasks only when the test advances its clock, in due-time
 * order (submission order for ties). Only what [DebouncedSurveyTopicNotifier] uses is supported.
 */
private class ManualScheduler : AbstractExecutorService(), ScheduledExecutorService {
  private var nowMs = 0L
  private var nextSeq = 0L
  private val tasks = mutableListOf<Task>()

  private inner class Task(val dueMs: Long, val seq: Long, val action: Runnable) :
    ScheduledFuture<Unit> {
    var done = false

    override fun getDelay(unit: TimeUnit) = unit.convert(dueMs - nowMs, TimeUnit.MILLISECONDS)

    override fun compareTo(other: Delayed) =
      getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))

    override fun cancel(mayInterruptIfRunning: Boolean) = tasks.remove(this).also { done = true }

    override fun isCancelled() = done && this !in tasks

    override fun isDone() = done

    override fun get() = Unit

    override fun get(timeout: Long, unit: TimeUnit) = Unit
  }

  /** Moves the clock forward by [ms] and runs every task due by then. */
  fun advanceBy(ms: Long) {
    nowMs += ms
    while (true) {
      val next =
        tasks.filter { it.dueMs <= nowMs }.minWithOrNull(compareBy({ it.dueMs }, { it.seq }))
      next ?: return
      tasks.remove(next)
      next.done = true
      next.action.run()
    }
  }

  override fun execute(command: Runnable) {
    schedule(command, 0L, TimeUnit.MILLISECONDS)
  }

  override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
    Task(nowMs + unit.toMillis(delay), nextSeq++, command).also { tasks += it }

  override fun <V> schedule(
    callable: Callable<V>,
    delay: Long,
    unit: TimeUnit,
  ): ScheduledFuture<V> = throw UnsupportedOperationException()

  override fun scheduleAtFixedRate(
    command: Runnable,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit,
  ): ScheduledFuture<*> = throw UnsupportedOperationException()

  override fun scheduleWithFixedDelay(
    command: Runnable,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit,
  ): ScheduledFuture<*> = throw UnsupportedOperationException()

  override fun shutdown() = tasks.clear()

  override fun shutdownNow(): List<Runnable> = tasks.map { it.action }.also { tasks.clear() }

  override fun isShutdown() = false

  override fun isTerminated() = false

  override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}
