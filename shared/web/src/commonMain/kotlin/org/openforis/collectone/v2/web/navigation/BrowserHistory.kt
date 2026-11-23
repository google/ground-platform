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
package org.openforis.collectone.v2.web.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The browser's session history as the web console reads and writes it: the current URL, the state
 * stored with each history entry, and the entries the user moves to.
 */
interface BrowserHistory {
  /** The path of the current URL, e.g. `/surveys/abc/dashboard`. */
  val path: String

  /** The fragment of the current URL including its `#`, or an empty string. */
  val hash: String

  /** The state stored with the current history entry, or `null` for a new entry. */
  val state: String?

  /** Adds a history entry for [path], storing [state] with it. */
  fun push(path: String, state: String)

  /** Replaces the current history entry with one for [path], storing [state] with it. */
  fun replace(path: String, state: String)

  /**
   * The state of every entry the user moves to with the browser's Back and Forward buttons, or
   * `null` for one they opened by editing the URL's hash.
   */
  val pops: Flow<String?>
}

/**
 * A [BrowserHistory] kept in memory, for hosts without a browser window (desktop previews and
 * tests), starting with one entry. [back] and [forward] move through it like the browser's buttons.
 */
class MemoryBrowserHistory(
  initialPath: String = "/",
  initialHash: String = "",
  initialState: String? = null,
) : BrowserHistory {
  private data class Entry(val path: String, val hash: String, val state: String?)

  private val entries = mutableListOf(Entry(initialPath, initialHash, initialState))
  private var index = 0
  private val _pops = MutableSharedFlow<String?>(extraBufferCapacity = 16)

  /** The paths of the history entries, oldest first. */
  val paths: List<String>
    get() = entries.map { it.path }

  override val path: String
    get() = entries[index].path

  override val hash: String
    get() = entries[index].hash

  override val state: String?
    get() = entries[index].state

  override val pops: Flow<String?> = _pops.asSharedFlow()

  override fun push(path: String, state: String) {
    entries.subList(index + 1, entries.size).clear()
    entries += Entry(path, hash = "", state = state)
    index = entries.lastIndex
  }

  override fun replace(path: String, state: String) {
    entries[index] = Entry(path, hash = "", state = state)
  }

  /** Moves to the previous entry like the browser's Back button; returns whether there was one. */
  fun back(): Boolean = moveTo(index - 1)

  /** Moves to the next entry like the browser's Forward button; returns whether there was one. */
  fun forward(): Boolean = moveTo(index + 1)

  private fun moveTo(target: Int): Boolean {
    if (target !in entries.indices) return false
    index = target
    _pops.tryEmit(entries[index].state)
    return true
  }
}
