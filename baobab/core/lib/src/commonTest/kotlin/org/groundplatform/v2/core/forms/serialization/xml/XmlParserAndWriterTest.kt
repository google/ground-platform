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
package org.groundplatform.v2.core.forms.serialization.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class XmlParserAndWriterTest {

  @Test
  fun testSimpleElementParsingAndWriting() {
    val xml =
      """<person id="123" active="true"><name>Alice &amp; Bob</name><age>30</age></person>"""
    val root = XmlParser.parse(xml)

    assertEquals("person", root.name)
    assertEquals("123", root.attr("id"))
    assertEquals("true", root.attr("active"))
    assertEquals(2, root.childElements.size)

    val nameElem = root.firstChildNamed("name")
    assertNotNull(nameElem)
    assertEquals("Alice & Bob", nameElem.textContent)

    val serialized = XmlWriter(prettyPrint = false).writeElement(root)
    assertEquals(xml, serialized)
  }

  @Test
  fun testMixedContentPreservationInLabels() {
    val xml =
      """<label>Welcome, <output value="/data/first_name"/> (<output value="/data/role"/>)!</label>"""
    val root = XmlParser.parse(xml)

    assertEquals("label", root.name)
    assertEquals(5, root.children.size)
    val textNode = assertIs<XmlText>(root.children[0])
    assertEquals("Welcome, ", textNode.text)
    val outputElem = assertIs<XmlElement>(root.children[1])
    assertEquals("output", outputElem.name)
    assertEquals("/data/first_name", outputElem.attr("value"))

    // Mixed content should not inject extra newlines even when prettyPrint = true
    val serialized = XmlWriter(prettyPrint = true).writeElement(root)
    assertEquals(xml, serialized)
  }

  @Test
  fun testNamespacesAndPrefixedAttributes() {
    val xml =
      """<h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa"><h:head><h:title>Test</h:title></h:head></h:html>"""
    val root = XmlParser.parse(xml)

    assertEquals("h:html", root.name)
    assertEquals("html", root.localName)
    assertEquals("h", root.prefix)
    assertEquals("http://openrosa.org/javarosa", root.attrExact("xmlns:jr"))

    val head = root.firstChildNamed("head")
    assertNotNull(head)
    assertEquals("h:head", head.name)
    assertEquals("Test", head.firstChildNamed("title")?.textContent)
  }

  @Test
  fun testNumericAndNamedEntityUnescaping() {
    val xml =
      """<note text="Quote &quot; &apos; &lt; &gt; &amp; &#65; &#x42;">Line 1 &amp; Line 2</note>"""
    val root = XmlParser.parse(xml)

    assertEquals("Quote \" ' < > & A B", root.attr("text"))
    assertEquals("Line 1 & Line 2", root.textContent)

    val rewritten = XmlWriter(prettyPrint = false).writeElement(root)
    val reparsed = XmlParser.parse(rewritten)
    assertEquals(root.attr("text"), reparsed.attr("text"))
    assertEquals(root.textContent, reparsed.textContent)
  }

  @Test
  fun testMismatchedClosingTagThrowsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> {
      XmlParser.parse("<person><name>Alice</wrong></person>")
    }
  }
}
