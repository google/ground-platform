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
package org.groundplatform.v2.core.forms.serialization.xml

/**
 * Pure-Kotlin Multiplatform XML serializer. Formats an [XmlElement] tree into well-formed XML with
 * configurable indentation and mixed-content preservation.
 */
internal class XmlWriter(
  private val prettyPrint: Boolean = true,
  private val indentStep: String = "  ",
  private val includeDeclaration: Boolean = false,
) {

  fun writeElement(root: XmlElement): String = buildString {
    if (includeDeclaration) {
      append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
      if (prettyPrint) append('\n')
    }
    appendElement(root, level = 0)
  }

  private fun StringBuilder.appendElement(element: XmlElement, level: Int) {
    if (prettyPrint && level > 0) {
      appendIndent(level)
    }
    append('<').append(element.name)
    appendAttributes(element.attributes)

    if (element.children.isEmpty()) {
      append("/>")
      return
    }

    append('>')

    val isMixedContent =
      element.children.any { it is XmlText } && element.children.any { it is XmlElement }
    val isPureText = element.children.all { it is XmlText }

    if (isPureText) {
      for (child in element.children.filterIsInstance<XmlText>()) {
        append(escapeText(child.text))
      }
      append("</").append(element.name).append('>')
    } else if (isMixedContent) {
      // Do not add extra indentation inside mixed content (e.g. <label>Hello <output
      // value="x"/>!</label>)
      for (child in element.children) {
        when (child) {
          is XmlText -> append(escapeText(child.text))
          is XmlElement -> appendInlineElement(child)
        }
      }
      append("</").append(element.name).append('>')
    } else {
      // Element-only children
      if (prettyPrint) append('\n')
      for (child in element.children) {
        if (child is XmlElement) {
          appendElement(child, level + 1)
          if (prettyPrint) append('\n')
        }
      }
      if (prettyPrint) {
        appendIndent(level)
      }
      append("</").append(element.name).append('>')
    }
  }

  private fun StringBuilder.appendInlineElement(element: XmlElement) {
    append('<').append(element.name)
    appendAttributes(element.attributes)
    if (element.children.isEmpty()) {
      append("/>")
      return
    }
    append('>')
    for (child in element.children) {
      when (child) {
        is XmlText -> append(escapeText(child.text))
        is XmlElement -> appendInlineElement(child)
      }
    }
    append("</").append(element.name).append('>')
  }

  private fun StringBuilder.appendAttributes(attributes: Map<String, String>) {
    for ((key, value) in attributes) {
      append(' ').append(key).append("=\"").append(escapeAttribute(value)).append('"')
    }
  }

  private fun StringBuilder.appendIndent(level: Int) {
    repeat(level) { append(indentStep) }
  }

  companion object {
    fun escapeText(text: String): String {
      if (text.none { it == '&' || it == '<' || it == '>' }) return text
      return buildString(text.length + 8) {
        for (c in text) {
          when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
          }
        }
      }
    }

    fun escapeAttribute(value: String): String {
      if (value.none { it == '&' || it == '<' || it == '>' || it == '"' }) return value
      return buildString(value.length + 8) {
        for (c in value) {
          when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
          }
        }
      }
    }
  }
}
