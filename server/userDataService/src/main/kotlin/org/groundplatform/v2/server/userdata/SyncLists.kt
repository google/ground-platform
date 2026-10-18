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
package org.groundplatform.v2.server.userdata

import com.squareup.wire.Instant
import io.grpc.Status
import io.grpc.StatusException
import java.util.Base64

/*
 * Query and delta modes of the `List*` sync RPCs (`docs/technical/sync-engine.md`, "List RPCs:
 * Query and Delta Modes" and "Sync Tokens").
 *
 * Tokens are private to this server: clients store them and send them back unchanged. Each one is
 * the base64url encoding of NUL-separated fields, starting with a format version, a kind, the data
 * epoch of the backend that issued it, and the scope it was issued for:
 * - Sync tokens (`S`) carry a position in commit order: a commit timestamp and the ID of the last
 *   record delivered at that timestamp, or `*` for "every record at that timestamp". In delta mode,
 *   `next_page_token` is a sync token too, so a client can store it after applying a page.
 * - Query page tokens (`Q`) carry the snapshot watermark pinned by the first page, a fingerprint of
 *   the query, and the sort key and ID of the last record returned (keyset paging).
 */

/**
 * Position in commit order. Records are delivered after it when `(commit_timestamp, id)` is later.
 */
data class SyncPosition(
  val epochSecond: Long,
  val nano: Int,
  /** ID of the last record delivered at this timestamp; `null` when all of them were. */
  val recordId: String?,
) {
  /** True when a record committed at [timestamp] with [id] comes after this position. */
  fun isBefore(timestamp: Instant?, id: String): Boolean {
    val second = timestamp?.epochSecond ?: 0L
    val nanos = timestamp?.nano ?: 0
    return when {
      second != epochSecond -> second > epochSecond
      nanos != nano -> nanos > nano
      else -> recordId != null && id > recordId
    }
  }

  companion object {
    /** Position after every record committed at or before [watermark]. */
    fun after(watermark: Instant) = SyncPosition(watermark.epochSecond, watermark.nano, null)

    /** Position of the record committed at [timestamp] with [id]. */
    fun of(timestamp: Instant?, id: String) =
      SyncPosition(timestamp?.epochSecond ?: 0L, timestamp?.nano ?: 0, id)
  }
}

/**
 * Up to [limit] of these records committed after [after], in commit order (`commit_timestamp`, then
 * ID).
 */
fun <T> Collection<T>.changedAfter(
  after: SyncPosition,
  limit: Int,
  commitTimestamp: (T) -> Instant?,
  id: (T) -> String,
): List<T> = filter {
  after.isBefore(commitTimestamp(it), id(it))
}
  .sortedWith(
    compareBy({ commitTimestamp(it)?.epochSecond ?: 0L }, { commitTimestamp(it)?.nano ?: 0 }, id)
  )
  .take(limit)

/** Paging and mode fields shared by every `List*` sync request. */
internal data class ListParams(val pageSize: Int, val pageToken: String, val syncToken: String)

/** Query-mode filtering and ordering of one `List*` request. */
internal class QuerySpec<T>(
  /** Whether a record in scope matches the query's filters. */
  val matches: (T) -> Boolean,
  /** Sort key compared as a string; records with equal keys are ordered by ID. */
  val sortKey: (T) -> String,
  val descending: Boolean = false,
  /** Canonical description of the filters and order, so a page token can't switch queries. */
  val fingerprint: String = "",
)

/** One page of a `List*` response. */
internal data class ListPage<T>(
  val items: List<T>,
  val nextPageToken: String,
  val nextSyncToken: String,
)

/** Implements query and delta modes over in-memory records for one backend data epoch. */
internal class SyncLists(private val epoch: () -> String) {

  /**
   * Returns one page of [records] (every record in scope, including tombstones).
   *
   * @param scopeKey resource name and scope field values the tokens are bound to.
   * @param queryFieldsSet names of query-only fields set on the request, rejected in delta mode.
   * @param watermark the current highest commit timestamp, pinned as the query snapshot.
   */
  fun <T> list(
    params: ListParams,
    scopeKey: String,
    records: Collection<T>,
    commitTimestamp: (T) -> Instant?,
    id: (T) -> String,
    query: QuerySpec<T>,
    queryFieldsSet: List<String>,
    watermark: Instant,
    accessEpoch: String = "",
    visible: (T) -> Boolean = { true },
  ): ListPage<T> =
    list(
      params,
      scopeKey,
      changedAfter = { after, limit -> records.changedAfter(after, limit, commitTimestamp, id) },
      queryRecords = { records },
      commitTimestamp,
      id,
      query,
      queryFieldsSet,
      watermark,
      accessEpoch,
      visible,
    )

  /**
   * Like the other [list], for records the store finds itself: [changedAfter] returns up to `limit`
   * records committed after a position, in commit order (delta mode), and [queryRecords] returns
   * the records the query may match, at least those [QuerySpec.matches] accepts (query mode).
   *
   * Tokens also carry [accessEpoch] (the caller's access epoch for caller-dependent lists), so a
   * token from before the caller's access changed fails with `FAILED_PRECONDITION`. Records
   * [visible] rejects (for example other collectors' records) are left out of both modes; delta
   * positions still advance past them.
   */
  fun <T> list(
    params: ListParams,
    scopeKey: String,
    changedAfter: (after: SyncPosition, limit: Int) -> List<T>,
    queryRecords: () -> Collection<T>,
    commitTimestamp: (T) -> Instant?,
    id: (T) -> String,
    query: QuerySpec<T>,
    queryFieldsSet: List<String>,
    watermark: Instant,
    accessEpoch: String = "",
    visible: (T) -> Boolean = { true },
  ): ListPage<T> {
    val epoch = epoch() + accessEpoch.let { if (it.isEmpty()) "" else "/$it" }
    val pageSize = pageSizeOf(params.pageSize)
    if (params.syncToken.isNotBlank()) {
      if (queryFieldsSet.isNotEmpty()) {
        throw invalidArgument("Query-only fields aren't allowed with sync_token: $queryFieldsSet")
      }
      val start = decodeSync(params.syncToken, scopeKey, epoch)
      val from =
        if (params.pageToken.isBlank()) start else decodeSync(params.pageToken, scopeKey, epoch)
      // One extra record shows whether there's another page.
      val changed = changedAfter(from, pageSize + 1)
      val page = changed.take(pageSize)
      val last = page.lastOrNull()?.let { SyncPosition.of(commitTimestamp(it), id(it)) } ?: from
      val token = encodeSync(scopeKey, last, epoch)
      val items = page.filter(visible)
      return if (changed.size > page.size) {
        ListPage(items, nextPageToken = token, nextSyncToken = "")
      } else {
        ListPage(items, nextPageToken = "", nextSyncToken = token)
      }
    }

    val cursor = params.pageToken.ifBlank { null }?.let { decodeQuery(it, scopeKey, query, epoch) }
    val snapshot = cursor?.snapshot ?: SyncPosition.after(watermark)
    val order = queryOrder(query.descending)
    val matching =
      queryRecords()
        .filter { query.matches(it) && visible(it) }
        .map { query.sortKey(it) to it }
        .sortedWith { a, b -> order.compare(a.first to id(a.second), b.first to id(b.second)) }
        .filter { (key, record) ->
          cursor == null || order.compare(key to id(record), cursor.sortKey to cursor.id) > 0
        }
    val page = matching.take(pageSize)
    return if (matching.size > page.size) {
      val (lastKey, lastRecord) = page.last()
      ListPage(
        page.map { it.second },
        nextPageToken = encodeQuery(scopeKey, query, snapshot, lastKey, id(lastRecord), epoch),
        nextSyncToken = "",
      )
    } else {
      ListPage(
        page.map { it.second },
        nextPageToken = "",
        nextSyncToken = encodeSync(scopeKey, snapshot, epoch),
      )
    }
  }

  private data class QueryCursor(val snapshot: SyncPosition, val sortKey: String, val id: String)

  private fun queryOrder(descending: Boolean) =
    compareBy<Pair<String, String>>({ it.first }, { it.second }).let {
      if (descending) it.reversed() else it
    }

  private fun encodeSync(scopeKey: String, position: SyncPosition, epoch: String): String =
    encode(
      SYNC,
      epoch,
      scopeKey,
      position.epochSecond.toString(),
      position.nano.toString(),
      position.recordId?.let { RECORD_PREFIX + it } ?: ALL_AT_TIMESTAMP,
    )

  private fun decodeSync(token: String, scopeKey: String, epoch: String): SyncPosition {
    val fields = decode(token, SYNC, epoch, scopeKey, fieldCount = 3)
    val second = fields[0].toLongOrNull() ?: throw malformed()
    val nano = fields[1].toIntOrNull() ?: throw malformed()
    val recordId =
      when {
        fields[2] == ALL_AT_TIMESTAMP -> null
        fields[2].startsWith(RECORD_PREFIX) -> fields[2].removePrefix(RECORD_PREFIX)
        else -> throw malformed()
      }
    return SyncPosition(second, nano, recordId)
  }

  private fun <T> encodeQuery(
    scopeKey: String,
    query: QuerySpec<T>,
    snapshot: SyncPosition,
    sortKey: String,
    id: String,
    epoch: String,
  ): String =
    encode(
      QUERY,
      epoch,
      scopeKey,
      query.fingerprint,
      snapshot.epochSecond.toString(),
      snapshot.nano.toString(),
      sortKey,
      id,
    )

  private fun <T> decodeQuery(
    token: String,
    scopeKey: String,
    query: QuerySpec<T>,
    epoch: String,
  ): QueryCursor {
    val fields = decode(token, QUERY, epoch, scopeKey, fieldCount = 5)
    if (fields[0] != query.fingerprint) {
      throw invalidArgument("page_token was issued for a different query")
    }
    val second = fields[1].toLongOrNull() ?: throw malformed()
    val nano = fields[2].toIntOrNull() ?: throw malformed()
    return QueryCursor(SyncPosition(second, nano, null), sortKey = fields[3], id = fields[4])
  }

  private fun encode(kind: String, epoch: String, scopeKey: String, vararg fields: String): String {
    val text = (listOf(VERSION, kind, epoch, scopeKey) + fields).joinToString(SEPARATOR)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(text.encodeToByteArray())
  }

  /** Validates [token]'s header and returns its [fieldCount] kind-specific fields. */
  private fun decode(
    token: String,
    kind: String,
    epoch: String,
    scopeKey: String,
    fieldCount: Int,
  ): List<String> {
    val text =
      try {
        Base64.getUrlDecoder().decode(token).decodeToString()
      } catch (e: IllegalArgumentException) {
        throw malformed()
      }
    val fields = text.split(SEPARATOR)
    if (fields.size != 4 + fieldCount || fields[0] != VERSION || fields[1] != kind) {
      throw malformed()
    }
    if (fields[2] != epoch) {
      throw StatusException(
        Status.FAILED_PRECONDITION.withDescription(
          "Token is no longer valid (the server's data was reset or your access changed); " +
            "run a full sync"
        )
      )
    }
    if (fields[3] != scopeKey) {
      throw invalidArgument("Token was issued for a different scope")
    }
    return fields.drop(4)
  }

  private fun malformed() = invalidArgument("Malformed sync_token or page_token")

  private fun invalidArgument(message: String) =
    StatusException(Status.INVALID_ARGUMENT.withDescription(message))

  companion object {
    /** Page size when the request doesn't set one. */
    const val DEFAULT_PAGE_SIZE = 500

    /** Largest page size served; larger requests are capped. */
    const val MAX_PAGE_SIZE = 1000

    private const val VERSION = "v1"
    private const val SYNC = "S"
    private const val QUERY = "Q"
    private const val SEPARATOR = "\u0000"
    private const val ALL_AT_TIMESTAMP = "*"
    private const val RECORD_PREFIX = "r:"

    fun pageSizeOf(requested: Int): Int =
      if (requested <= 0) DEFAULT_PAGE_SIZE else minOf(requested, MAX_PAGE_SIZE)

    /** Sort key for a commit timestamp that orders like the timestamp. */
    fun timestampSortKey(timestamp: Instant?): String =
      "%019d.%09d".format((timestamp?.epochSecond ?: 0L).coerceAtLeast(0L), timestamp?.nano ?: 0)
  }
}
