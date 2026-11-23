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
package org.openforis.collectone.v2.server.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test decorator that makes [parties] concurrent requests overlap: the first store call of each
 * request waits until every request has made its first call, so no request finishes its reads (or
 * its whole transaction) before the others start.
 *
 * It exposes check-then-act races that a serialized server hides: a request that decides what to
 * write from a read outside its write transaction acts on data another request is about to change.
 */
class RendezvousUserDataStore(private val delegate: UserDataStore, private val parties: Int) :
  UserDataStore by delegate {
  private val calls = AtomicInteger()
  private val arrivals = CountDownLatch(parties)

  private fun rendezvous() {
    if (calls.incrementAndGet() > parties) return
    arrivals.countDown()
    check(arrivals.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      "Only ${parties - arrivals.count} of $parties concurrent requests reached the store"
    }
  }

  override suspend fun <T> read(block: (UserDataReader) -> T): T {
    rendezvous()
    return delegate.read(block)
  }

  override suspend fun <T> write(block: (UserDataWriter) -> T): Committed<T> {
    rendezvous()
    return delegate.write(block)
  }

  private companion object {
    const val TIMEOUT_SECONDS = 30L
  }
}
