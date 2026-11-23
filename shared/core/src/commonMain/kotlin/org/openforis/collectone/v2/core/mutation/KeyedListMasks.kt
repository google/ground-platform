/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.mutation

/*
 * Sparse-proto mutation semantics shared by the user data service and the clients. Every mutation
 * carries two optional sparse instances of the target domain proto `T` (`delete` and `upsert`),
 * applied in order as `merge(applyDelete(base, delete), upsert)`:
 *
 * 1. Structural deletion mask (`delete`):
 *    - Any non-default scalar field clears that scalar on `base` to its proto3 default.
 *    - Any non-empty repeated value list (e.g., `supported_languages`, `purpose_ids`,
 *      `program_ids`, `quarantined_user_ids`, `hidden_global_entry_ids`, or geometry vertices)
 *      clears that list to `emptyList()`.
 *    - Any map entry `k -> v` removes key `k` when `v` is primitive or an empty message, or
 *      recurses into `v` when `v` has child fields set.
 *    - Any sub-message or keyed repeated item (matched by its stable ID/key) is removed/cleared
 *      when it is a leaf (no child fields set beyond its ID/key), or retained and recursed into
 *      when it is a branch (child fields are populated).
 *
 * 2. Sparse `upsert` merge (`upsert`):
 *    - Non-default scalars overwrite `base`.
 *    - Non-empty repeated value lists replace `base`'s list.
 *    - Keyed repeated entity lists upsert by ID (recursively merging existing items and appending
 *      new ones) and sort by `sort_order` when `sort_order` is used.
 *
 * Server bookkeeping (`revision`, `version`, `record_version`, `commit_timestamp`, `sync_status_key`)
 * is left to the caller.
 */

/**
 * Sorts by [orderOf] only when some item sets it: lists that never use `sort_order` (all 0) keep
 * their stored order instead of being reordered by a stable sort on equal keys.
 */
internal fun <T> List<T>.sortByOrderIfUsed(orderOf: (T) -> Int): List<T> =
  if (any { orderOf(it) != 0 }) sortedBy(orderOf) else this

/**
 * Applies a keyed delete mask: an item whose key matches a [isLeaf] mask item is removed, one that
 * matches a branch mask item is recursed into with [applyBranchDelete], and the rest are kept in
 * order. Mask items matching nothing are ignored, so deleting twice is harmless.
 */
internal fun <T> List<T>.applyKeyedDelete(
  deleteItems: List<T>,
  keyOf: (T) -> String,
  isLeaf: (T) -> Boolean,
  applyBranchDelete: (T, T) -> T,
): List<T> {
  if (deleteItems.isEmpty()) return this
  val byKey = deleteItems.associateBy { keyOf(it) }
  return mapNotNull { item ->
    val del = byKey[keyOf(item)] ?: return@mapNotNull item
    if (isLeaf(del)) null else applyBranchDelete(item, del)
  }
}

/**
 * Applies a keyed upsert: items with a stored key are merged in place with [mergeItem], new keys
 * are appended in upsert order, and the result is sorted by [orderOf] when given and used (see
 * [sortByOrderIfUsed]). Later upsert items with the same key merge onto earlier ones.
 */
internal fun <T> List<T>.mergeKeyedUpsert(
  upsertItems: List<T>,
  keyOf: (T) -> String,
  mergeItem: (T, T) -> T,
  orderOf: ((T) -> Int)? = null,
): List<T> {
  if (upsertItems.isEmpty()) return this
  val result = LinkedHashMap<String, T>()
  for (item in this) result[keyOf(item)] = item
  for (up in upsertItems) {
    val k = keyOf(up)
    val existing = result[k]
    result[k] = if (existing != null) mergeItem(existing, up) else up
  }
  val merged = result.values.toList()
  return if (orderOf != null) merged.sortByOrderIfUsed(orderOf) else merged
}
