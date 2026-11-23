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
package org.openforis.collectone.v2.web.ui.formeditor

/**
 * Hands out question keys `q1`, `q2`, … for one Form editor.
 *
 * Keys only need to be unique within the Form, but the counter never goes back, so a key freed by a
 * delete isn't handed out again in the same session and entries of the undo history keep referring
 * to the question they were recorded for.
 */
internal class QuestionKeyAllocator(private var nextId: Int) {
  /** The next `q<n>` key for which [isTaken] is false. */
  fun allocate(isTaken: (String) -> Boolean): String {
    var candidate: String
    do {
      candidate = "q${nextId++}"
    } while (isTaken(candidate))
    return candidate
  }
}
