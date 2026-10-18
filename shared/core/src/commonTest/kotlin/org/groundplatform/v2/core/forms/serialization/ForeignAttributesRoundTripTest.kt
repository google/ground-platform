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
package org.groundplatform.v2.core.forms.serialization

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.FieldBinding
import groundplatform.v2.forms.ForeignAttribute
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.library.FormTemplateDef
import groundplatform.v2.library.LibraryBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.serialization.xml.XmlElement
import org.groundplatform.v2.core.forms.serialization.xml.XmlParser
import org.groundplatform.v2.core.library.LibraryTextProtoSerializer

/**
 * Round trips of `FieldBinding.foreign_attributes`: `<bind>` attributes in namespaces ProtoForms
 * doesn't model (e.g. `ground:concept`, see `docs/technical/model/library/01-concepts.md`).
 */
class ForeignAttributesRoundTripTest {

  private val acmeNamespace = "https://example.org/acme"

  /** Wraps [binds] (and optional extra root [rootNamespaces]) in a minimal XForms document. */
  private fun form(binds: String, rootNamespaces: String = "", modelAttrs: String = ""): String =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <h:html xmlns="http://www.w3.org/2002/xforms" xmlns:h="http://www.w3.org/1999/xhtml" xmlns:jr="http://openrosa.org/javarosa" xmlns:odk="http://www.opendatakit.org/xforms" xmlns:orx="http://openrosa.org/xforms" $rootNamespaces>
      <h:head>
        <h:title>Foreign attributes</h:title>
        <model odk:xforms-version="1.0.0" $modelAttrs>
          <instance>
            <data id="foreign_attributes">
              <commodity/>
              <notes/>
              <photo/>
            </data>
          </instance>
          $binds
        </model>
      </h:head>
      <h:body>
        <input ref="/data/commodity"><label>Commodity</label></input>
        <input ref="/data/notes"><label>Notes</label></input>
      </h:body>
    </h:html>
    """
      .trimIndent()

  private fun FormDef.binding(path: String): FieldBinding =
    model!!.bindings.first { it.field_path == path }

  private fun parseXml(xml: String): XmlElement = XmlParser.parse(xml)

  private fun XmlElement.bind(nodeset: String): XmlElement =
    firstChildNamed("head")!!.firstChildNamed("model")!!.childrenNamed("bind").first {
      it.attrExact("nodeset") == nodeset
    }

  /**
   * Asserts that XML → proto → XML → proto is lossless and that serializing again yields the same
   * XML. Returns the re-serialized XML.
   */
  private fun assertStableRoundTrip(formDef: FormDef): String {
    val xml1 = XFormsXmlSerializer.serializeFormDef(formDef)
    val reparsed = XFormsXmlSerializer.deserializeFormDef(xml1)
    assertEquals(formDef, reparsed)
    val xml2 = XFormsXmlSerializer.serializeFormDef(reparsed)
    assertEquals(xml1, xml2)
    return xml1
  }

  @Test
  fun testThirdPartyNamespaceAttributeRoundTrips() {
    val xml =
      form(
        rootNamespaces = """xmlns:acme="$acmeNamespace"""",
        binds =
          """
          <bind nodeset="/data/notes" type="string" acme:hint-style="bold" jr:constraintMsg="Too long" acme:required="true()" constraint="string-length(.) &lt; 100"/>
          """,
      )

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val notes = formDef.binding("notes")
    assertEquals(
      listOf(
        ForeignAttribute(acmeNamespace, "acme:hint-style", "bold"),
        ForeignAttribute(acmeNamespace, "acme:required", "true()"),
      ),
      notes.foreign_attributes,
    )
    // Modeled attributes are read as before; a foreign attribute whose local name matches a modeled
    // one (`acme:required`) is not mistaken for it.
    assertEquals("Too long", notes.constraint_message)
    assertEquals("string-length(.) < 100", notes.constraint_expression)
    assertEquals("", notes.required_expression)

    val outXml = assertStableRoundTrip(formDef)
    val root = parseXml(outXml)
    assertEquals(acmeNamespace, root.attrExact("xmlns:acme"))
    val bind = root.bind("/data/notes")
    assertEquals("bold", bind.attrExact("acme:hint-style"))
    assertEquals("true()", bind.attrExact("acme:required"))
    assertNull(bind.attrExact("required"))
    // Foreign attributes follow the modeled ones, in their original order.
    assertEquals(
      listOf("acme:hint-style", "acme:required"),
      bind.attributes.keys.toList().takeLast(2),
    )
  }

  @Test
  fun testGroundConceptRoundTrips() {
    val xml =
      form(
        rootNamespaces = """xmlns:ground="$GROUND_XFORMS_NAMESPACE"""",
        binds =
          """
          <bind nodeset="/data/commodity" type="string" required="true()" ground:concept="eudr.commodity@1"/>
          <bind nodeset="/data/notes" type="string"/>
          """,
      )

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val commodity = formDef.binding("commodity")
    assertEquals(
      listOf(ForeignAttribute(GROUND_XFORMS_NAMESPACE, "ground:concept", "eudr.commodity@1")),
      commodity.foreign_attributes,
    )
    assertEquals("true()", commodity.required_expression)
    assertTrue(formDef.binding("notes").foreign_attributes.isEmpty())

    val outXml = assertStableRoundTrip(formDef)
    val root = parseXml(outXml)
    assertEquals(GROUND_XFORMS_NAMESPACE, root.attrExact("xmlns:$GROUND_XFORMS_PREFIX"))
    assertEquals("eudr.commodity@1", root.bind("/data/commodity").attrExact("ground:concept"))
    assertNull(root.bind("/data/notes").attrExact("ground:concept"))
    // Declared once, on the root element only.
    assertEquals(1, Regex("xmlns:ground=").findAll(outXml).count())
  }

  @Test
  fun testNamespacesDeclaredOnModelOrBindAreResolvedAndMovedToRoot() {
    val xml =
      form(
        modelAttrs = """xmlns:ground="$GROUND_XFORMS_NAMESPACE"""",
        binds =
          """
          <bind nodeset="/data/commodity" type="string" ground:concept="eudr.commodity@1"/>
          <bind xmlns:acme="$acmeNamespace" nodeset="/data/notes" type="string" acme:hint-style="bold"/>
          """,
      )

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    assertEquals(
      listOf(ForeignAttribute(GROUND_XFORMS_NAMESPACE, "ground:concept", "eudr.commodity@1")),
      formDef.binding("commodity").foreign_attributes,
    )
    // The `xmlns:acme` declaration itself is not a foreign attribute.
    assertEquals(
      listOf(ForeignAttribute(acmeNamespace, "acme:hint-style", "bold")),
      formDef.binding("notes").foreign_attributes,
    )

    val root = parseXml(assertStableRoundTrip(formDef))
    assertEquals(GROUND_XFORMS_NAMESPACE, root.attrExact("xmlns:ground"))
    assertEquals(acmeNamespace, root.attrExact("xmlns:acme"))
    assertNull(root.bind("/data/notes").attrExact("xmlns:acme"))
  }

  @Test
  fun testModeledNamespacesAreNotForeign() {
    val xml =
      form(
        rootNamespaces =
          """xmlns:entities="$ODK_ENTITIES_NAMESPACE" xmlns:javarosa="$JAVAROSA_NAMESPACE"""",
        binds =
          """
          <bind nodeset="/data/commodity" type="string" jr:constraintMsg="Pick one" jr:requiredMsg="Required" odk:length="1" entities:saveto="commodity" javarosa:noAppErrorString="x"/>
          <bind nodeset="/data/photo" type="binary" orx:max-pixels="1024" jr:preload="property" jr:preloadParams="deviceid" ev:event="x" xsd:thing="y"/>
          """,
      )

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val commodity = formDef.binding("commodity")
    assertTrue(commodity.foreign_attributes.isEmpty(), "${commodity.foreign_attributes}")
    assertEquals("Pick one", commodity.constraint_message)
    assertEquals("Required", commodity.required_message)
    assertEquals("commodity", commodity.entity_saveto)
    val photo = formDef.binding("photo")
    // `ev:` and `xsd:` are undeclared here: treated as their conventional namespaces.
    assertTrue(photo.foreign_attributes.isEmpty(), "${photo.foreign_attributes}")
    assertEquals(1024, photo.max_pixels)

    val outXml = XFormsXmlSerializer.serializeFormDef(formDef)
    // Modeled attributes are emitted once, by the modeled serializer, never duplicated.
    assertEquals(1, Regex("jr:constraintMsg=").findAll(outXml).count())
    assertEquals(1, Regex("orx:max-pixels=").findAll(outXml).count())
    assertFalse(outXml.contains("odk:length"))
    assertFalse(outXml.contains("javarosa:"))
  }

  @Test
  fun testPrefixCollisionsKeepFirstBindingAndRenameLater() {
    val otherNamespace = "https://example.org/other"
    val notJavaRosa = "https://example.org/not-javarosa"
    val xml =
      form(
        binds =
          """
          <bind xmlns:acme="$acmeNamespace" nodeset="/data/commodity" type="string" acme:a="1"/>
          <bind xmlns:acme="$otherNamespace" nodeset="/data/notes" type="string" acme:b="2" acme:c="3"/>
          <bind xmlns:jr="$notJavaRosa" nodeset="/data/photo" type="binary" jr:d="4"/>
          """
      )

    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    assertEquals(
      listOf(ForeignAttribute(acmeNamespace, "acme:a", "1")),
      formDef.binding("commodity").foreign_attributes,
    )
    assertEquals(
      listOf(
        ForeignAttribute(otherNamespace, "acme:b", "2"),
        ForeignAttribute(otherNamespace, "acme:c", "3"),
      ),
      formDef.binding("notes").foreign_attributes,
    )
    // `jr` rebound to a non-JavaRosa URI is foreign.
    assertEquals(
      listOf(ForeignAttribute(notJavaRosa, "jr:d", "4")),
      formDef.binding("photo").foreign_attributes,
    )

    val xml1 = XFormsXmlSerializer.serializeFormDef(formDef)
    val root = parseXml(xml1)
    assertEquals(acmeNamespace, root.attrExact("xmlns:acme"))
    assertEquals(otherNamespace, root.attrExact("xmlns:acme2"))
    assertEquals(JAVAROSA_NAMESPACE, root.attrExact("xmlns:jr"))
    assertEquals(notJavaRosa, root.attrExact("xmlns:jr2"))
    assertEquals("1", root.bind("/data/commodity").attrExact("acme:a"))
    assertEquals("2", root.bind("/data/notes").attrExact("acme2:b"))
    assertEquals("3", root.bind("/data/notes").attrExact("acme2:c"))
    assertEquals("4", root.bind("/data/photo").attrExact("jr2:d"))

    // The renamed prefix shows up in the re-parsed qualified name; namespace and value are kept,
    // and from then on the round trip is stable.
    val reparsed = XFormsXmlSerializer.deserializeFormDef(xml1)
    assertEquals(
      listOf(
        ForeignAttribute(otherNamespace, "acme2:b", "2"),
        ForeignAttribute(otherNamespace, "acme2:c", "3"),
      ),
      reparsed.binding("notes").foreign_attributes,
    )
    assertEquals(
      listOf(ForeignAttribute(notJavaRosa, "jr2:d", "4")),
      reparsed.binding("photo").foreign_attributes,
    )
    assertEquals(xml1, assertStableRoundTrip(reparsed))
  }

  @Test
  fun testUnprefixedOrUndeclaredForeignAttributesFromProto() {
    val formDef =
      FormDef(
        form_id = "from_proto",
        model =
          ModelDef(
            primary_instance = PrimaryInstance(record_schema = RecordSchema(name = "data")),
            bindings =
              listOf(
                FieldBinding(
                  field_path = "commodity",
                  type = DataType.TYPE_STRING,
                  foreign_attributes =
                    listOf(
                      // No prefix: one is generated.
                      ForeignAttribute(acmeNamespace, "hint-style", "bold"),
                      // No namespace (undeclared prefix in the source): emitted as written.
                      ForeignAttribute("", "legacy:flag", "on"),
                    ),
                )
              ),
          ),
      )

    val xml1 = XFormsXmlSerializer.serializeFormDef(formDef)
    val root = parseXml(xml1)
    assertEquals(acmeNamespace, root.attrExact("xmlns:ns1"))
    assertNull(root.attrExact("xmlns:legacy"))
    val bind = root.bind("/data/commodity")
    assertEquals("bold", bind.attrExact("ns1:hint-style"))
    assertEquals("on", bind.attrExact("legacy:flag"))

    val reparsed = XFormsXmlSerializer.deserializeFormDef(xml1)
    assertEquals(
      listOf(
        ForeignAttribute(acmeNamespace, "ns1:hint-style", "bold"),
        ForeignAttribute("", "legacy:flag", "on"),
      ),
      reparsed.binding("commodity").foreign_attributes,
    )
    assertEquals(xml1, XFormsXmlSerializer.serializeFormDef(reparsed))
  }

  private val conceptBinding =
    FieldBinding(
      field_path = "commodity",
      type = DataType.TYPE_SELECT_ONE,
      required_expression = "true()",
      foreign_attributes =
        listOf(
          ForeignAttribute(GROUND_XFORMS_NAMESPACE, "ground:concept", "eudr.commodity@1"),
          ForeignAttribute(acmeNamespace, "acme:hint-style", ""),
        ),
    )

  private val conceptForm =
    FormDef(
      form_id = "concepts",
      title = "Concepts",
      model =
        ModelDef(
          primary_instance = PrimaryInstance(record_schema = RecordSchema(name = "data")),
          bindings = listOf(conceptBinding),
        ),
    )

  @Test
  fun testForeignAttributesTextProtoRoundTrip() {
    val textproto = TextProtoSerializer.serializeFormDef(conceptForm)
    assertTrue(textproto.contains("foreign_attributes {"), textproto)
    assertTrue(textproto.contains("namespace_uri: \"$GROUND_XFORMS_NAMESPACE\""), textproto)
    assertTrue(textproto.contains("qualified_name: \"ground:concept\""), textproto)
    assertTrue(textproto.contains("value: \"eudr.commodity@1\""), textproto)
    assertEquals(conceptForm, TextProtoSerializer.deserializeFormDef(textproto))
  }

  @Test
  fun testForeignAttributesInLibraryTemplateTextProtoRoundTrip() {
    val bundle =
      LibraryBundle(form_templates = listOf(FormTemplateDef(id = "template", form = conceptForm)))
    val textproto = LibraryTextProtoSerializer.serializeBundle(bundle)
    assertTrue(textproto.contains("qualified_name: \"ground:concept\""), textproto)
    assertEquals(bundle, LibraryTextProtoSerializer.deserializeBundle(textproto))
  }

  @Test
  fun testForeignAttributesJsonRoundTrip() {
    val camel = ProtoJsonSerializer.serializeFormDef(conceptForm)
    assertTrue(camel.contains("\"foreignAttributes\""), camel)
    assertTrue(camel.contains("\"qualifiedName\": \"ground:concept\""), camel)
    assertEquals(conceptForm, ProtoJsonSerializer.deserializeFormDef(camel))

    val snake = ProtoJsonSerializer.serializeFormDef(conceptForm, preserveProtoFieldNames = true)
    assertTrue(snake.contains("\"foreign_attributes\""), snake)
    assertTrue(snake.contains("\"namespace_uri\": \"$GROUND_XFORMS_NAMESPACE\""), snake)
    assertEquals(conceptForm, ProtoJsonSerializer.deserializeFormDef(snake))
  }

  @Test
  fun testForeignAttributesSurviveXmlTextProtoJsonChain() {
    val xml =
      form(
        rootNamespaces = """xmlns:ground="$GROUND_XFORMS_NAMESPACE"""",
        binds =
          """<bind nodeset="/data/commodity" type="string" ground:concept="eudr.commodity@1"/>""",
      )
    val formDef = XFormsXmlSerializer.deserializeFormDef(xml)
    val viaTextProto =
      TextProtoSerializer.deserializeFormDef(TextProtoSerializer.serializeFormDef(formDef))
    val viaJson =
      ProtoJsonSerializer.deserializeFormDef(ProtoJsonSerializer.serializeFormDef(viaTextProto))
    assertEquals(formDef, viaJson)
    assertEquals(
      XFormsXmlSerializer.serializeFormDef(formDef),
      XFormsXmlSerializer.serializeFormDef(viaJson),
    )
  }
}
