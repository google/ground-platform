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
package org.openforis.collectone.v2.core.library

import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.OrganizationLibrarySettings
import org.openforis.collectone.v2.core.forms.serialization.TextProtoSerializer
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoParser
import org.openforis.collectone.v2.core.forms.serialization.textproto.TextProtoWriter
import org.openforis.collectone.v2.core.proto.schema.ProtoCodecRules

/**
 * Serializer and deserializer between Organization Library messages
 * (`openforis.collectone.v1.library`) and Protocol Buffer Text Format (`textproto`).
 *
 * Used for the global library seed files (`shared/assets/library/<vocabulary>.textproto`, each a
 * [LibraryBundle]) and library imports and exports. Template forms (`FormDef`) are written like
 * [TextProtoSerializer] writes them. Enum values must be spelled exactly; an unknown name is an
 * [IllegalArgumentException].
 *
 * Not yet supported: `FormTemplateDef.default_dataset` and the `audit_info` of concepts, templates
 * and purpose packs, which seed files don't use; they are dropped on serialization and left unset
 * on parsing.
 */
object LibraryTextProtoSerializer {
  /** Formats a [LibraryBundle] into a pretty-printed `textproto` string. */
  fun serializeBundle(bundle: LibraryBundle): String =
    TextProtoWriter().writeMessage(codec.write(LibraryBundle.ADAPTER, bundle))

  /** Parses a `textproto` string (comments allowed) into a [LibraryBundle]. */
  fun deserializeBundle(textproto: String): LibraryBundle =
    codec.read(LibraryBundle.ADAPTER, TextProtoParser.parse(textproto))

  /** Formats [OrganizationLibrarySettings] into a pretty-printed `textproto` string. */
  fun serializeSettings(settings: OrganizationLibrarySettings): String =
    TextProtoWriter().writeMessage(codec.write(OrganizationLibrarySettings.ADAPTER, settings))

  /** Parses a `textproto` string into [OrganizationLibrarySettings]. */
  fun deserializeSettings(textproto: String): OrganizationLibrarySettings =
    codec.read(OrganizationLibrarySettings.ADAPTER, TextProtoParser.parse(textproto))

  private val codec =
    TextProtoSerializer.codec(
      ProtoCodecRules(
        ignoredFields =
          setOf(
            "ConceptDef.audit_info",
            "FormTemplateDef.audit_info",
            "FormTemplateDef.default_dataset",
            "PurposePackDef.audit_info",
          ),
        // The seed files list a template's version, and a purpose pack's starter templates, before
        // their sort order.
        fieldOrder =
          mapOf(
            "FormTemplateDef" to
              listOf(
                "id",
                "organization_id",
                "title",
                "description",
                "form",
                "default_dataset",
                "status",
                "audit_info",
                "version",
                "sort_order",
              ),
            "PurposePackDef" to
              listOf(
                "id",
                "organization_id",
                "title",
                "description",
                "icon",
                "form_template_ids",
                "export_profile_ids",
                "program_ids",
                "goals",
                "pillar",
                "status",
                "audit_info",
                "starter_templates",
                "sort_order",
              ),
          ),
        strictEnumPackages = setOf("openforis.collectone.v1.library"),
      )
    )
}
