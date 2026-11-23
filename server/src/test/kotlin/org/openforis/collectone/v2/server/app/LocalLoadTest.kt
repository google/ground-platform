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
package org.openforis.collectone.v2.server.app

import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCalls
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import openforis.collectone.v1.data.DataMutation
import openforis.collectone.v1.data.DataServiceServer
import openforis.collectone.v1.data.EntityMutation
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.GetAppliedMutationsRequest
import openforis.collectone.v1.data.ListEntitiesRequest
import openforis.collectone.v1.data.MutateDataRequest
import openforis.collectone.v1.survey.GetSurveyRequest
import openforis.collectone.v1.survey.ListSurveyOverviewsRequest
import openforis.collectone.v1.survey.MutateSurveyRequest
import openforis.collectone.v1.survey.SurveyDef
import openforis.collectone.v1.survey.SurveyMutation
import openforis.collectone.v1.survey.SurveyServiceServer
import org.junit.jupiter.api.Tag
import org.openforis.collectone.v2.server.data.SpannerTestDatabase
import org.openforis.collectone.v2.server.service.UserDataBackend

/**
 * Local load test: concurrent clients fire mixed reads and mutations at a real server (native gRPC)
 * over the Spanner emulator, once per RPC parallelism, and check that no update is lost.
 *
 * It runs only against the local emulator (never a deployed server) with `./gradlew
 * :server:loadTest`. System properties tune it: `load.parallelism` (comma-separated, default
 * `1,8`), `load.clients` (default 16), and `load.requestsPerClient` (default 30). A call past its
 * 30 s deadline counts as timed out; it may still have applied, so the checks allow for it. The
 * summary is printed and written to `build/reports/load/summary.txt`.
 */
@Tag(LOAD_TAG)
class LocalLoadTest {
  private val parallelisms =
    System.getProperty("load.parallelism", "1,8").split(',').map { it.trim().toInt() }
  private val clients = System.getProperty("load.clients", "16").toInt()
  private val requestsPerClient = System.getProperty("load.requestsPerClient", "30").toInt()

  @Test
  fun mixedReadsAndMutations_atEachParallelism_loseNoUpdates() {
    val summaries = parallelisms.map(::run)
    val report = buildString {
      appendLine(
        "Local load test: $clients clients x $requestsPerClient requests, Spanner emulator"
      )
      summaries.forEach { append(it) }
    }
    println(report)
    File("build/reports/load").apply { mkdirs() }.resolve("summary.txt").writeText(report)
  }

  /** One run at [parallelism] on a freshly seeded database; returns its summary. */
  private fun run(parallelism: Int): String {
    val backend = UserDataBackend(SpannerTestDatabase.store(loadSeed(null)))
    val server = UserDataServer.create(backend, port = 0, rpcParallelism = parallelism)
    server.start().join()
    val channel =
      ManagedChannelBuilder.forAddress("127.0.0.1", server.activeLocalPort()).usePlaintext().build()
    try {
      val rpc = Rpcs(backend, channel)
      val entityIds =
        rpc
          .call(rpc.listEntities, ListEntitiesRequest(survey_id = KENYA, page_size = 1000))
          .entities
          .map {
            it.entity_id
          }
      val revisionsBefore = rpc.revisions()
      val recordVersionBefore = rpc.recordVersion()

      val started = System.nanoTime()
      val samples = runBlocking {
        withTimeout(TimeUnit.MINUTES.toMillis(10)) {
          (0 until clients)
            .map { client ->
              async(Dispatchers.IO) { rpc.clientSession(client, entityIds, requestsPerClient) }
            }
            .awaitAll()
            .flatten()
        }
      }
      val elapsedSeconds = (System.nanoTime() - started) / 1e9

      // A request that timed out may or may not have applied; every other failure is a bug.
      val unexpected = samples.filter { !it.ok && !it.timedOut }
      assertEquals(emptyList(), unexpected.map { "${it.kind}: ${it.error}" }, "failed requests")

      // Correctness: every applied mutation counts exactly once, and no update is lost.
      fun applied(kind: Kind) = samples.count { it.kind == kind && it.ok }.toLong()
      fun unknown(kind: Kind) = samples.count { it.kind == kind && it.timedOut }.toLong()
      val revisionsAfter = rpc.revisions()
      val revisionDelta = revisionsAfter.entries.sumOf { (id, r) -> r - (revisionsBefore[id] ?: 0) }
      assertWithin(applied(Kind.MUTATE_DATA), unknown(Kind.MUTATE_DATA), revisionDelta, "entity")
      assertWithin(
        applied(Kind.MUTATE_SURVEY),
        unknown(Kind.MUTATE_SURVEY),
        rpc.recordVersion() - recordVersionBefore,
        "survey record version",
      )
      for (client in 0 until clients) {
        val mine = samples.filter { it.client == client && it.seq > 0 }
        val lowest = mine.filter { it.ok }.maxOfOrNull { it.seq } ?: 0L
        val highest = mine.maxOf { it.seq }
        val watermark = rpc.watermark(client)
        check(watermark in lowest..highest) {
          "watermark of client $client is $watermark, not in $lowest..$highest"
        }
      }

      return summarize(parallelism, samples, elapsedSeconds)
    } finally {
      channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS)
      server.stop().join()
    }
  }

  /** [actual] counts the [applied] writes plus any of the [unknown] (timed-out) ones. */
  private fun assertWithin(applied: Long, unknown: Long, actual: Long, what: String) {
    check(actual in applied..(applied + unknown)) {
      "$what gained $actual writes, expected $applied applied (+ up to $unknown timed out)"
    }
  }

  private fun summarize(parallelism: Int, samples: List<Sample>, elapsedSeconds: Double): String =
    buildString {
      appendLine(
        "parallelism=$parallelism: ${samples.size} requests in %.2f s = %.1f req/s, %d timed out"
          .format(elapsedSeconds, samples.size / elapsedSeconds, samples.count { it.timedOut })
      )
      for ((kind, ofKind) in samples.groupBy { it.kind }.toSortedMap()) {
        val millis = ofKind.map { it.nanos / 1e6 }.sorted()
        fun pct(p: Double) = millis[((millis.size - 1) * p).toInt()]
        appendLine(
          "  %-14s n=%4d p50=%7.1f ms p95=%7.1f ms p99=%7.1f ms max=%7.1f ms"
            .format(kind, millis.size, pct(0.50), pct(0.95), pct(0.99), millis.last())
        )
      }
    }

  private enum class Kind {
    GET_SURVEY,
    LIST_OVERVIEWS,
    LIST_ENTITIES,
    MUTATE_DATA,
    MUTATE_SURVEY,
  }

  private class Sample(
    val client: Int,
    val kind: Kind,
    val nanos: Long,
    val ok: Boolean,
    val seq: Long = 0L,
    val error: String = "",
    val timedOut: Boolean = false,
  )

  /** Native gRPC calls to the server, with Wire's method descriptors. */
  private class Rpcs(backend: UserDataBackend, private val channel: ManagedChannel) {
    private val surveys =
      WireGrpcBindings.bind(
        SurveyServiceServer::class.java,
        backend,
        CoroutineScope(Dispatchers.IO),
      )
    private val data =
      WireGrpcBindings.bind(DataServiceServer::class.java, backend, CoroutineScope(Dispatchers.IO))

    @Suppress("UNCHECKED_CAST")
    private fun <Req, Res> method(definition: io.grpc.ServerServiceDefinition, name: String) =
      definition.getMethod(name).methodDescriptor as MethodDescriptor<Req, Res>

    val getSurvey =
      method<GetSurveyRequest, openforis.collectone.v1.survey.GetSurveyResponse>(
        surveys,
        "openforis.collectone.v1.survey.SurveyService/GetSurvey",
      )
    val listOverviews =
      method<
        ListSurveyOverviewsRequest,
        openforis.collectone.v1.survey.ListSurveyOverviewsResponse,
      >(
        surveys,
        "openforis.collectone.v1.survey.SurveyService/ListSurveyOverviews",
      )
    val mutateSurvey =
      method<MutateSurveyRequest, openforis.collectone.v1.survey.MutateSurveyResponse>(
        surveys,
        "openforis.collectone.v1.survey.SurveyService/MutateSurvey",
      )
    val listEntities =
      method<ListEntitiesRequest, openforis.collectone.v1.data.ListEntitiesResponse>(
        data,
        "openforis.collectone.v1.data.DataService/ListEntities",
      )
    val mutateData =
      method<MutateDataRequest, openforis.collectone.v1.data.MutateDataResponse>(
        data,
        "openforis.collectone.v1.data.DataService/MutateData",
      )
    private val appliedMutations =
      method<
        GetAppliedMutationsRequest,
        openforis.collectone.v1.data.GetAppliedMutationsResponse,
      >(
        data,
        "openforis.collectone.v1.data.DataService/GetAppliedMutations",
      )

    fun <Req, Res> call(method: MethodDescriptor<Req, Res>, request: Req): Res =
      ClientCalls.blockingUnaryCall(
        channel,
        method,
        CallOptions.DEFAULT.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS),
        request,
      )

    fun revisions(): Map<String, Long> =
      call(listEntities, ListEntitiesRequest(survey_id = KENYA, page_size = 1000))
        .entities
        .associate {
          it.entity_id to it.revision
        }

    fun recordVersion(): Long =
      call(getSurvey, GetSurveyRequest(survey_id = KENYA)).survey!!.record_version

    fun watermark(client: Int): Long =
      call(appliedMutations, GetAppliedMutationsRequest(client_id = clientId(client)))
        .watermarks
        .filter { it.scope.startsWith("surveys/$KENYA") }
        .maxOfOrNull { it.client_seq } ?: 0L

    /**
     * One client's session: per five requests, one entity label patch (rotating over [entityIds],
     * so clients contend on the same rows), one working-copy title patch, and three reads.
     */
    fun clientSession(client: Int, entityIds: List<String>, requests: Int): List<Sample> {
      var seq = 0L
      return (0 until requests).map { i ->
        when (i % 5) {
          0 -> {
            val s = ++seq
            val entityId = entityIds[(client + i) % entityIds.size]
            timed(client, Kind.MUTATE_DATA, s) {
              call(
                mutateData,
                MutateDataRequest(
                  survey_id = KENYA,
                  mutations =
                    listOf(
                      DataMutation(
                        client_id = clientId(client),
                        client_seq = s,
                        entity_mutation =
                          EntityMutation(
                            entity_id = entityId,
                            upsert = EntityRecord(label = "Load $client.$i"),
                          ),
                      )
                    ),
                ),
              )
            }
          }
          1 -> {
            val s = ++seq
            timed(client, Kind.MUTATE_SURVEY, s) {
              call(
                mutateSurvey,
                MutateSurveyRequest(
                  survey_id = KENYA,
                  working_copy = true,
                  mutations =
                    listOf(
                      SurveyMutation(
                        client_id = clientId(client),
                        client_seq = s,
                        upsert = SurveyDef(title = "Load $client.$i"),
                      )
                    ),
                ),
              )
            }
          }
          2 ->
            timed(client, Kind.GET_SURVEY) {
              call(getSurvey, GetSurveyRequest(survey_id = KENYA))
            }
          3 ->
            timed(client, Kind.LIST_OVERVIEWS) { call(listOverviews, ListSurveyOverviewsRequest()) }
          else ->
            timed(client, Kind.LIST_ENTITIES) {
              call(listEntities, ListEntitiesRequest(survey_id = KENYA))
            }
        }
      }
    }

    private fun timed(client: Int, kind: Kind, seq: Long = 0L, block: () -> Any): Sample {
      val start = System.nanoTime()
      return try {
        block()
        Sample(client, kind, System.nanoTime() - start, ok = true, seq = seq)
      } catch (e: StatusRuntimeException) {
        Sample(
          client,
          kind,
          System.nanoTime() - start,
          ok = false,
          seq = seq,
          error = e.status.toString(),
          timedOut = e.status.code == Status.Code.DEADLINE_EXCEEDED,
        )
      }
    }

    private fun clientId(client: Int) = "load-client-$client"
  }

  private companion object {
    const val KENYA = "00000033-6b50-51cd-ac94-baea2ea06d65"

    /** Per-call deadline, like a client's; a call past it counts as timed out. */
    const val DEADLINE_SECONDS = 30L
  }
}

/** JUnit tag of [LocalLoadTest], which only `./gradlew :server:loadTest` runs. */
const val LOAD_TAG = "load"
