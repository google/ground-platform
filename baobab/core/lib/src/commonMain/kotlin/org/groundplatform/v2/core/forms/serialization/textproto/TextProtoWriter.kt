/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.serialization.textproto

/** Pretty-printer for Protocol Buffer Text Format (`textproto` / `txtpb`). */
internal class TextProtoWriter(private val indentStep: String = "  ") {

  fun writeMessage(message: TextProtoMessage): String {
    val sb = StringBuilder()
    writeFields(message.fields, sb, currentIndent = "")
    return sb.toString().trimEnd() + (if (sb.isNotEmpty()) "\n" else "")
  }

  private fun writeFields(fields: List<TextProtoField>, sb: StringBuilder, currentIndent: String) {
    for (field in fields) {
      writeField(field, sb, currentIndent)
    }
  }

  private fun writeField(field: TextProtoField, sb: StringBuilder, currentIndent: String) {
    when (val v = field.value) {
      is TextProtoValue.MessageVal -> {
        sb.append(currentIndent).append(field.name).append(" {\n")
        writeFields(v.message.fields, sb, currentIndent + indentStep)
        sb.append(currentIndent).append("}\n")
      }
      is TextProtoValue.ListVal -> {
        for (elem in v.elements) {
          writeField(TextProtoField(field.name, elem), sb, currentIndent)
        }
      }
      is TextProtoValue.StringVal -> {
        sb
          .append(currentIndent)
          .append(field.name)
          .append(": \"")
          .append(escapeString(v.value))
          .append("\"\n")
      }
      is TextProtoValue.NumberVal -> {
        sb.append(currentIndent).append(field.name).append(": ").append(v.raw).append("\n")
      }
      is TextProtoValue.IdentifierVal -> {
        sb.append(currentIndent).append(field.name).append(": ").append(v.name).append("\n")
      }
    }
  }

  companion object {
    fun escapeString(str: String): String = buildString {
      for (c in str) {
        when (c) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> append(c)
        }
      }
    }

    fun formatDouble(d: Double): String {
      if (d.isNaN()) return "nan"
      if (d == Double.POSITIVE_INFINITY) return "inf"
      if (d == Double.NEGATIVE_INFINITY) return "-inf"
      val str = d.toString()
      return if (str.endsWith(".0")) str.dropLast(2) + ".0" else str
    }
  }
}
