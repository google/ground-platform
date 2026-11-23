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
package org.openforis.collectone.v2.core.forms.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FieldValue
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordMetadata
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.TypedValue
import org.openforis.collectone.v2.core.forms.buildRecordNode

class RecordMutatorTest {

  private val rootAliases = setOf("household", "data")

  private fun stringValue(value: String) =
    FieldValue(scalar_value = TypedValue(string_value = value))

  @Test
  fun resolveRootName_usesRecordSchemaName() {
    val formDef =
      FormDef(
        form_id = "form_id",
        model =
          ModelDef(
            primary_instance = PrimaryInstance(record_schema = RecordSchema(name = "household"))
          ),
      )

    assertEquals("household", RecordMutator.resolveRootName(formDef))
  }

  @Test
  fun resolveRootName_withoutSchemaName_usesFormId() {
    assertEquals("census", RecordMutator.resolveRootName(FormDef(form_id = "census")))
  }

  @Test
  fun resolveRootName_withoutSchemaNameOrFormId_usesData() {
    assertEquals("data", RecordMutator.resolveRootName(FormDef()))
  }

  @Test
  fun normalizeSchemaPath_stripsRootAndRepeatIndexes() {
    assertEquals(
      "person/age",
      RecordMutator.normalizeSchemaPath("/household/person[2]/age", rootAliases),
    )
  }

  @Test
  fun normalizeSchemaPath_mapsOrxMetaToMeta() {
    assertEquals(
      "meta/instanceID",
      RecordMutator.normalizeSchemaPath("orx:meta/instanceID", rootAliases),
    )
  }

  @Test
  fun resolveCanonicalPath_absolutePathWithRootAlias_isRootedAtRootName() {
    assertEquals(
      "/household/person/age",
      RecordMutator.resolveCanonicalPath("/data/person/age", "household", rootAliases),
    )
  }

  @Test
  fun resolveCanonicalPath_siblingOfRepeatChild_keepsRepeatIndex() {
    assertEquals(
      "/household/person[2]/name",
      RecordMutator.resolveCanonicalPath(
        rawPath = "../name",
        rootName = "household",
        rootAliases = rootAliases,
        contextCanonicalPath = "/household/person[2]/age",
      ),
    )
  }

  @Test
  fun parseSingleSegment_withIndex_returnsNameAndIndex() {
    assertEquals(
      RecordMutator.PathSegment("person", repeatIndex = 3),
      RecordMutator.parseSingleSegment("person[3]"),
    )
  }

  @Test
  fun isFieldValueEmpty_blankString_isEmpty() {
    assertTrue(RecordMutator.isFieldValueEmpty(stringValue("")))
  }

  @Test
  fun isFieldValueEmpty_answeredString_isNotEmpty() {
    assertFalse(RecordMutator.isFieldValueEmpty(stringValue("Amina")))
  }

  @Test
  fun buildInitialRecordNode_addsSchemaGroupsAndDefaults() {
    val schema =
      RecordSchema(
        name = "household",
        fields =
          listOf(
            FieldDefinition(name = "country", type = DataType.TYPE_STRING),
            FieldDefinition(
              name = "address",
              type = DataType.TYPE_MESSAGE,
              fields = listOf(FieldDefinition(name = "city", type = DataType.TYPE_STRING)),
            ),
          ),
      )

    val node =
      RecordMutator.buildInitialRecordNode(
        schema = schema,
        defaultValues = buildRecordNode { string("country", "Kenya") },
      )

    assertEquals(stringValue("Kenya"), node.fields["country"])
    assertEquals(RecordNode(), node.fields["address"]?.node_value)
  }

  @Test
  fun buildInitialRecordNode_existingAnswersOverrideDefaults() {
    val schema =
      RecordSchema(
        name = "household",
        fields = listOf(FieldDefinition(name = "country", type = DataType.TYPE_STRING)),
      )

    val node =
      RecordMutator.buildInitialRecordNode(
        schema = schema,
        defaultValues = buildRecordNode { string("country", "Kenya") },
        existingNode = buildRecordNode { string("country", "Ghana") },
      )

    assertEquals(stringValue("Ghana"), node.fields["country"])
  }

  @Test
  fun setFieldValue_nestedGroupPath_canBeReadBack() {
    val update =
      RecordMutator.setFieldValue(
        rootNode = RecordNode(),
        metadata = null,
        rootAliases = rootAliases,
        canonicalPath = "/household/address/city",
        newValue = stringValue("Accra"),
      )

    assertEquals(
      stringValue("Accra"),
      RecordMutator.getFieldValue(update.node, null, rootAliases, "/household/address/city"),
    )
  }

  @Test
  fun setFieldValue_secondRepeatInstance_padsTheFirst() {
    val update =
      RecordMutator.setFieldValue(
        rootNode = RecordNode(),
        metadata = null,
        rootAliases = rootAliases,
        canonicalPath = "/household/person[2]/age",
        newValue = stringValue("41"),
      )

    val people = RecordMutator.getRepeatInstances(update.node, rootAliases, "/household/person")
    assertEquals(
      listOf(RecordNode(), RecordNode(fields = mapOf("age" to stringValue("41")))),
      people,
    )
  }

  @Test
  fun setFieldValue_metaInstanceId_updatesMetadata() {
    val update =
      RecordMutator.setFieldValue(
        rootNode = RecordNode(),
        metadata = RecordMetadata(),
        rootAliases = rootAliases,
        canonicalPath = "/household/meta/instanceID",
        newValue = stringValue("uuid:1234"),
      )

    assertEquals("uuid:1234", update.metadata?.instance_id)
  }

  @Test
  fun getFieldValue_metaDeviceId_readsMetadata() {
    val value =
      RecordMutator.getFieldValue(
        rootNode = RecordNode(),
        metadata = RecordMetadata(device_id = "pixel-9"),
        rootAliases = rootAliases,
        canonicalPath = "/household/meta/deviceID",
      )

    assertEquals(stringValue("pixel-9"), value)
  }

  @Test
  fun getFieldValue_missingField_returnsNull() {
    assertNull(RecordMutator.getFieldValue(RecordNode(), null, rootAliases, "/household/absent"))
  }

  @Test
  fun setRepeatInstances_replacesTheRepeatList() {
    val instances = listOf(buildRecordNode { string("name", "Ama") })

    val node =
      RecordMutator.setRepeatInstances(RecordNode(), rootAliases, "/household/person", instances)

    assertEquals(
      instances,
      RecordMutator.getRepeatInstances(node, rootAliases, "/household/person"),
    )
  }

  @Test
  fun pruneNonRelevantNodes_dropsNonRelevantField() {
    val node = buildRecordNode {
      string("has_farm", "no")
      string("crop", "coffee")
    }

    val pruned =
      RecordMutator.pruneNonRelevantNodes(
        node = node,
        currentPathPrefix = "/household",
        relevancyMap = mapOf("/household/crop" to false),
      )

    assertEquals(buildRecordNode { string("has_farm", "no") }, pruned)
  }

  @Test
  fun pruneNonRelevantNodes_dropsNonRelevantRepeatInstance() {
    val node = buildRecordNode {
      repeat("person") {
        item { string("name", "Ama") }
        item { string("name", "Kofi") }
      }
    }

    val pruned =
      RecordMutator.pruneNonRelevantNodes(
        node = node,
        currentPathPrefix = "/household",
        relevancyMap = mapOf("/household/person[1]" to false),
      )

    assertEquals(
      listOf(buildRecordNode { string("name", "Kofi") }),
      RecordMutator.getRepeatInstances(pruned, rootAliases, "/household/person"),
    )
  }
}
