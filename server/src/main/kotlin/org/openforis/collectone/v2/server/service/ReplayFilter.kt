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

import org.openforis.collectone.v2.server.data.UserDataReader
import org.openforis.collectone.v2.server.data.UserDataWriter

/** Identity of one client mutation, used for replay detection and `client_seq` tracking. */
internal data class MutationIdentity(val clientId: String, val clientSeq: Long)

/**
 * Recognizes replays in [scope]: a mutation is one when its `client_seq` is at or below its
 * client's watermark, or below a mutation earlier in the same request.
 */
internal class ReplayFilter(private val reader: UserDataReader, private val scope: String) {
  private val highest = HashMap<String, Long>()

  /** Whether [identity] is a replay. If it isn't, it raises its client's watermark here. */
  fun isReplay(identity: MutationIdentity): Boolean {
    if (identity.clientId.isBlank() || identity.clientSeq <= 0L) return false
    val watermark =
      highest.getOrPut(identity.clientId) {
        reader.appliedWatermarks(identity.clientId).firstOrNull { it.scope == scope }?.client_seq
          ?: 0L
      }
    if (identity.clientSeq <= watermark) return true
    highest[identity.clientId] = identity.clientSeq
    return false
  }
}

/** The [mutations] that aren't replays in [scope]. */
internal fun <T> UserDataReader.newMutations(
  scope: String,
  mutations: List<T>,
  identityOf: (T) -> MutationIdentity,
): List<T> {
  val replays = ReplayFilter(this, scope)
  return mutations.filterNot { replays.isReplay(identityOf(it)) }
}

/**
 * Records [identities] as applied in [scope], in the same transaction as the changes they made, so
 * a failed request can be retried.
 */
internal fun UserDataWriter.recordApplied(scope: String, identities: List<MutationIdentity>) {
  identities
    .filter { it.clientId.isNotBlank() && it.clientSeq > 0L }
    .groupBy { it.clientId }
    .forEach { (clientId, ids) -> recordApplied(scope, clientId, ids.maxOf { it.clientSeq }) }
}
