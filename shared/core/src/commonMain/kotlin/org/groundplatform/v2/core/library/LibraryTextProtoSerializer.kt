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
package org.groundplatform.v2.core.library

import groundplatform.v2.forms.DataType
import groundplatform.v2.library.Aggregation
import groundplatform.v2.library.CodeList
import groundplatform.v2.library.CodeListItem
import groundplatform.v2.library.ConceptDef
import groundplatform.v2.library.ExportProfileDef
import groundplatform.v2.library.FormTemplateDef
import groundplatform.v2.library.LibraryBundle
import groundplatform.v2.library.LibraryStatus
import groundplatform.v2.library.LocalizedText
import groundplatform.v2.library.OrganizationLibrarySettings
import groundplatform.v2.library.Pillar
import groundplatform.v2.library.PrivacyClass
import groundplatform.v2.library.PurposePackDef
import org.groundplatform.v2.core.forms.serialization.TextProtoSerializer
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoField
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoMessage
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoParser
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoValue
import org.groundplatform.v2.core.forms.serialization.textproto.TextProtoWriter

/**
 * Serializer and deserializer between Organization Library messages (`groundplatform.v2.library`)
 * and Protocol Buffer Text Format (`textproto`).
 *
 * Used for the global library seed files (`shared/assets/library/<vocabulary>.textproto`, each a
 * [LibraryBundle]) and library imports and exports. Template forms reuse the [FormDef]
 * (`groundplatform.v2.forms.FormDef`) codec of [TextProtoSerializer].
 *
 * Not yet supported: `FormTemplateDef.default_dataset` and `audit_info`, which seed files don't
 * use; they are dropped on serialization and left unset on parsing.
 */
object LibraryTextProtoSerializer {
  /** Formats a [LibraryBundle] into a pretty-printed `textproto` string. */
  fun serializeBundle(bundle: LibraryBundle): String =
    TextProtoWriter().writeMessage(encodeBundle(bundle))

  /** Parses a `textproto` string (comments allowed) into a [LibraryBundle]. */
  fun deserializeBundle(textproto: String): LibraryBundle =
    decodeBundle(TextProtoParser.parse(textproto))

  /** Formats [OrganizationLibrarySettings] into a pretty-printed `textproto` string. */
  fun serializeSettings(settings: OrganizationLibrarySettings): String =
    TextProtoWriter()
      .writeMessage(
        message {
          string("organization_id", settings.organization_id)
          settings.hidden_global_entry_ids.forEach { stringAlways("hidden_global_entry_ids", it) }
        }
      )

  /** Parses a `textproto` string into [OrganizationLibrarySettings]. */
  fun deserializeSettings(textproto: String): OrganizationLibrarySettings {
    val node = TextProtoParser.parse(textproto)
    return OrganizationLibrarySettings(
      organization_id = node.getString("organization_id"),
      hidden_global_entry_ids = node.getStrings("hidden_global_entry_ids"),
    )
  }

  // ===========================================================================
  // Encoding
  // ===========================================================================

  private fun encodeBundle(msg: LibraryBundle): TextProtoMessage = message {
    msg.concepts.forEach { message("concepts", encodeConcept(it)) }
    msg.form_templates.forEach { message("form_templates", encodeTemplate(it)) }
    msg.purpose_packs.forEach { message("purpose_packs", encodePurposePack(it)) }
    msg.export_profiles.forEach { message("export_profiles", encodeExportProfile(it)) }
  }

  private fun encodeConcept(msg: ConceptDef): TextProtoMessage = message {
    string("id", msg.id)
    int("version", msg.version)
    string("organization_id", msg.organization_id)
    text("label", msg.label)
    text("description", msg.description)
    msg.keywords.forEach { text("keywords", it) }
    enum("data_type", msg.data_type, DataType.DATA_TYPE_UNSPECIFIED)
    string("unit", msg.unit)
    msg.code_list?.let { list ->
      message(
        "code_list",
        message { list.items.forEach { message("items", encodeCodeListItem(it)) } },
      )
    }
    enum("aggregation", msg.aggregation, Aggregation.AGGREGATION_UNSPECIFIED)
    enum("privacy_class", msg.privacy_class, PrivacyClass.PRIVACY_CLASS_UNSPECIFIED)
    msg.goals.forEach { stringAlways("goals", it) }
    enum("pillar", msg.pillar, Pillar.PILLAR_UNSPECIFIED)
    msg.references.forEach { stringAlways("references", it) }
    enum("status", msg.status, LibraryStatus.LIBRARY_STATUS_UNSPECIFIED)
    msg.aliases.forEach { stringAlways("aliases", it) }
  }

  private fun encodeCodeListItem(msg: CodeListItem): TextProtoMessage = message {
    string("code", msg.code)
    text("label", msg.label)
    map("external_ids", msg.external_ids)
  }

  private fun encodeTemplate(msg: FormTemplateDef): TextProtoMessage = message {
    string("id", msg.id)
    string("organization_id", msg.organization_id)
    text("title", msg.title)
    text("description", msg.description)
    msg.form?.let { message("form", TextProtoSerializer.encodeFormDef(it)) }
    enum("status", msg.status, LibraryStatus.LIBRARY_STATUS_UNSPECIFIED)
  }

  private fun encodePurposePack(msg: PurposePackDef): TextProtoMessage = message {
    string("id", msg.id)
    string("organization_id", msg.organization_id)
    text("title", msg.title)
    text("description", msg.description)
    string("icon", msg.icon)
    msg.form_template_ids.forEach { stringAlways("form_template_ids", it) }
    msg.export_profile_ids.forEach { stringAlways("export_profile_ids", it) }
    msg.program_ids.forEach { stringAlways("program_ids", it) }
    msg.goals.forEach { stringAlways("goals", it) }
    enum("pillar", msg.pillar, Pillar.PILLAR_UNSPECIFIED)
    enum("status", msg.status, LibraryStatus.LIBRARY_STATUS_UNSPECIFIED)
  }

  private fun encodeExportProfile(msg: ExportProfileDef): TextProtoMessage = message {
    string("id", msg.id)
    string("organization_id", msg.organization_id)
    text("title", msg.title)
    string("format", msg.format)
    map("field_concepts", msg.field_concepts)
    enum("status", msg.status, LibraryStatus.LIBRARY_STATUS_UNSPECIFIED)
  }

  // ===========================================================================
  // Decoding
  // ===========================================================================

  private fun decodeBundle(node: TextProtoMessage): LibraryBundle =
    LibraryBundle(
      concepts = node.getMessages("concepts").map(::decodeConcept),
      form_templates = node.getMessages("form_templates").map(::decodeTemplate),
      purpose_packs = node.getMessages("purpose_packs").map(::decodePurposePack),
      export_profiles = node.getMessages("export_profiles").map(::decodeExportProfile),
    )

  private fun decodeConcept(node: TextProtoMessage): ConceptDef =
    ConceptDef(
      id = node.getString("id"),
      version = node.getInt("version"),
      organization_id = node.getString("organization_id"),
      label = node.getMessageOrNull("label")?.let(::decodeText),
      description = node.getMessageOrNull("description")?.let(::decodeText),
      keywords = node.getMessages("keywords").map(::decodeText),
      data_type = enumOf(node.getIdentifierOrNull("data_type"), DataType.DATA_TYPE_UNSPECIFIED),
      unit = node.getString("unit"),
      code_list =
        node.getMessageOrNull("code_list")?.let { list ->
          CodeList(items = list.getMessages("items").map(::decodeCodeListItem))
        },
      aggregation =
        enumOf(node.getIdentifierOrNull("aggregation"), Aggregation.AGGREGATION_UNSPECIFIED),
      privacy_class =
        enumOf(node.getIdentifierOrNull("privacy_class"), PrivacyClass.PRIVACY_CLASS_UNSPECIFIED),
      goals = node.getStrings("goals"),
      pillar = enumOf(node.getIdentifierOrNull("pillar"), Pillar.PILLAR_UNSPECIFIED),
      references = node.getStrings("references"),
      status = enumOf(node.getIdentifierOrNull("status"), LibraryStatus.LIBRARY_STATUS_UNSPECIFIED),
      aliases = node.getStrings("aliases"),
    )

  private fun decodeCodeListItem(node: TextProtoMessage): CodeListItem =
    CodeListItem(
      code = node.getString("code"),
      label = node.getMessageOrNull("label")?.let(::decodeText),
      external_ids = decodeMap(node, "external_ids"),
    )

  private fun decodeTemplate(node: TextProtoMessage): FormTemplateDef =
    FormTemplateDef(
      id = node.getString("id"),
      organization_id = node.getString("organization_id"),
      title = node.getMessageOrNull("title")?.let(::decodeText),
      description = node.getMessageOrNull("description")?.let(::decodeText),
      form = node.getMessageOrNull("form")?.let(TextProtoSerializer::decodeFormDef),
      status = enumOf(node.getIdentifierOrNull("status"), LibraryStatus.LIBRARY_STATUS_UNSPECIFIED),
    )

  private fun decodePurposePack(node: TextProtoMessage): PurposePackDef =
    PurposePackDef(
      id = node.getString("id"),
      organization_id = node.getString("organization_id"),
      title = node.getMessageOrNull("title")?.let(::decodeText),
      description = node.getMessageOrNull("description")?.let(::decodeText),
      icon = node.getString("icon"),
      form_template_ids = node.getStrings("form_template_ids"),
      export_profile_ids = node.getStrings("export_profile_ids"),
      program_ids = node.getStrings("program_ids"),
      goals = node.getStrings("goals"),
      pillar = enumOf(node.getIdentifierOrNull("pillar"), Pillar.PILLAR_UNSPECIFIED),
      status = enumOf(node.getIdentifierOrNull("status"), LibraryStatus.LIBRARY_STATUS_UNSPECIFIED),
    )

  private fun decodeExportProfile(node: TextProtoMessage): ExportProfileDef =
    ExportProfileDef(
      id = node.getString("id"),
      organization_id = node.getString("organization_id"),
      title = node.getMessageOrNull("title")?.let(::decodeText),
      format = node.getString("format"),
      field_concepts = decodeMap(node, "field_concepts"),
      status = enumOf(node.getIdentifierOrNull("status"), LibraryStatus.LIBRARY_STATUS_UNSPECIFIED),
    )

  private fun decodeText(node: TextProtoMessage): LocalizedText =
    LocalizedText(values = decodeMap(node, "values"))

  /** Decodes a `map<string, string>` field written as repeated `{ key: … value: … }` entries. */
  private fun decodeMap(node: TextProtoMessage, name: String): Map<String, String> =
    node.getMessages(name).associate { it.getString("key") to it.getString("value") }

  private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E {
    if (name == null) return default
    return enumValues<E>().firstOrNull { it.name == name }
      ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} value '$name'")
  }

  // ===========================================================================
  // Message builder
  // ===========================================================================

  private class Builder {
    val fields = mutableListOf<TextProtoField>()

    fun string(name: String, value: String) {
      if (value.isNotEmpty()) stringAlways(name, value)
    }

    fun stringAlways(name: String, value: String) {
      fields += TextProtoField(name, TextProtoValue.StringVal(value))
    }

    fun int(name: String, value: Int) {
      if (value != 0) fields += TextProtoField(name, TextProtoValue.NumberVal(value.toString()))
    }

    fun <E : Enum<E>> enum(name: String, value: E, unspecified: E) {
      if (value != unspecified)
        fields += TextProtoField(name, TextProtoValue.IdentifierVal(value.name))
    }

    fun message(name: String, value: TextProtoMessage) {
      fields += TextProtoField(name, TextProtoValue.MessageVal(value))
    }

    fun text(name: String, value: LocalizedText?) {
      if (value != null) message(name, message { map("values", value.values) })
    }

    fun map(name: String, entries: Map<String, String>) {
      entries.forEach { (key, value) ->
        message(
          name,
          message {
            stringAlways("key", key)
            stringAlways("value", value)
          },
        )
      }
    }
  }

  private fun message(build: Builder.() -> Unit): TextProtoMessage =
    TextProtoMessage(Builder().apply(build).fields.toList())
}
