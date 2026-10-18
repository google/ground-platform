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
 * Pure-Kotlin Multiplatform representation of an XML AST node. Supports elements, text nodes, mixed
 * content, and namespace-aware queries.
 */
internal sealed class XmlNode

/** Represents a text node within an XML element (including mixed content). */
internal data class XmlText(val text: String) : XmlNode()

/** Represents an XML element with qualified name, attributes, and ordered child nodes. */
internal data class XmlElement(
  val name: String,
  val attributes: Map<String, String> = emptyMap(),
  val children: List<XmlNode> = emptyList(),
) : XmlNode() {

  /** Local name without namespace prefix (e.g., `"html"` for `"h:html"`). */
  val localName: String
    get() = name.substringAfter(':')

  /** Namespace prefix if present (e.g., `"h"` for `"h:html"`), or empty string. */
  val prefix: String
    get() = name.substringBefore(':', missingDelimiterValue = "")

  /** Returns all child nodes that are [XmlElement]s. */
  val childElements: List<XmlElement>
    get() = children.filterIsInstance<XmlElement>()

  /** Returns all child elements matching [targetLocalName] (ignoring prefix). */
  fun childrenNamed(targetLocalName: String): List<XmlElement> = childElements.filter {
    it.localName == targetLocalName
  }

  /** Returns the first child element matching [targetLocalName] (ignoring prefix), or null. */
  fun firstChildNamed(targetLocalName: String): XmlElement? = childElements.firstOrNull {
    it.localName == targetLocalName
  }

  /** Gets an attribute by exact qualified name first, falling back to matching local name. */
  fun attr(attrName: String): String? {
    val exactMatch = attributes[attrName]
    if (exactMatch != null) return exactMatch
    val targetLocalName = attrName.substringAfter(':')
    return attributes.entries
      .firstOrNull { (key, _) -> key.substringAfter(':') == targetLocalName }
      ?.value
  }

  /** Gets an attribute by exact qualified name only. */
  fun attrExact(qualifiedName: String): String? = attributes[qualifiedName]

  /** Concatenates all text content within this element recursively. */
  val textContent: String
    get() = buildString {
      for (child in children) {
        when (child) {
          is XmlText -> append(child.text)
          is XmlElement -> append(child.textContent)
        }
      }
    }

  /** Formats this element and its descendants as an XML string. */
  fun toXmlString(prettyPrint: Boolean = true, indent: String = "  "): String =
    XmlWriter(prettyPrint = prettyPrint, indentStep = indent).writeElement(this)
}
