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
package org.openforis.collectone.v2.core.proto

import com.squareup.wire.Message
import okio.ByteString

/**
 * Field-by-field comparison of Wire messages that names the fields whose values differ as
 * `DeclaringMessage.field` (e.g. `ChoiceItem.parent_value`), wherever the message is nested, so a
 * difference can be matched against documented exclusions. A nested message that is missing on one
 * side is reported as the field holding it, not as each of its fields.
 */
object ProtoDiff {

  /** The fields whose values differ between [expected] and [actual]. */
  fun diff(expected: Message<*, *>, actual: Message<*, *>): Set<String> {
    val fields = sortedSetOf<String>()
    diffMessages(expected, actual, fields)
    return fields
  }

  /** The fields of [message], at any depth, that are set to a non-default value. */
  fun setFields(message: Message<*, *>): Set<String> {
    val fields = sortedSetOf<String>()
    diffMessages(message.adapter.decode(ByteString.EMPTY), message, fields)
    return fields
  }

  private fun diffMessages(expected: Any, actual: Any, out: MutableSet<String>) {
    for (field in FullyPopulatedProtos.fieldsOf(expected.javaClass)) {
      field.javaField.isAccessible = true
      diffValues(field.javaField.get(expected), field.javaField.get(actual), field.toString(), out)
    }
  }

  private fun diffValues(expected: Any?, actual: Any?, fieldName: String, out: MutableSet<String>) {
    when {
      expected == actual -> Unit
      expected is Message<*, *> && actual is Message<*, *> -> diffMessages(expected, actual, out)
      expected is List<*> && actual is List<*> && expected.size == actual.size ->
        expected.zip(actual).forEach { (e, a) -> diffValues(e, a, fieldName, out) }
      expected is Map<*, *> && actual is Map<*, *> && expected.keys == actual.keys ->
        expected.keys.forEach { key -> diffValues(expected[key], actual[key], fieldName, out) }
      else -> out.add(fieldName)
    }
  }
}
