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
package org.openforis.collectone.v2.server.service

import com.squareup.wire.Instant
import org.openforis.collectone.v2.server.data.QuerySpec
import org.openforis.collectone.v2.server.data.SyncLists

/** Applies `page_size` / `page_token` offset paging to an already filtered and sorted list. */
internal fun <T> page(items: List<T>, pageSize: Int, pageToken: String): Pair<List<T>, String> {
  val offset = pageToken.toIntOrNull() ?: 0
  if (pageSize <= 0) return items.drop(offset) to ""
  val end = minOf(offset + pageSize, items.size)
  val next = if (end < items.size) end.toString() else ""
  return items.subList(minOf(offset, items.size), end) to next
}

/** Query that matches [matches] in commit order (the default for full syncs). */
internal fun <T> commitOrderQuery(
  commitTimestamp: (T) -> Instant?,
  matches: (T) -> Boolean = { true },
) =
  QuerySpec(
    matches = matches,
    sortKey = { record: T -> SyncLists.timestampSortKey(commitTimestamp(record)) },
  )

/**
 * Query sorted by `order_by`: [stringKeys] maps sortable field names to string sort keys;
 * `commit_timestamp` (the default) is always supported. Unknown fields fall back to commit order.
 */
internal fun <T> orderedQuery(
  orderBy: String,
  commitTimestamp: (T) -> Instant?,
  stringKeys: Map<String, (T) -> String>,
  fingerprint: String,
  matches: (T) -> Boolean,
): QuerySpec<T> {
  val parts = orderBy.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val field = parts.firstOrNull().orEmpty()
  val descending = parts.getOrNull(1) == "desc"
  val key: (T) -> String =
    stringKeys[field] ?: { record -> SyncLists.timestampSortKey(commitTimestamp(record)) }
  return QuerySpec(matches, key, descending, fingerprint)
}
