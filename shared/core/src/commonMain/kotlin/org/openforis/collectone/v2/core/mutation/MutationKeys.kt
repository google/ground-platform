/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.core.mutation

import openforis.collectone.v1.forms.ActionDef
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.EntityDeclaration
import openforis.collectone.v1.forms.EntityPropertyMapping
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.ForeignAttribute
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.EntityPropertyDefinition
import openforis.collectone.v1.survey.OrganizationMember

/**
 * Keys that match items of a repeated message between stored data and a sparse mask (see
 * `KeyedListMasks.kt`): the item's `id` when set, else a natural key that is unique within the list
 * (a name, path, or value), so masks written before an item had an ID still find it. People are
 * keyed by ID, else email, else user ID, trimmed and lowercased because addresses are
 * case-insensitive.
 */
object MutationKeys {
  fun aclEntryKey(e: AclEntry): String =
    e.id.ifBlank { e.email.ifBlank { e.user_id } }.trim().lowercase()

  fun memberKey(m: OrganizationMember): String =
    m.id.ifBlank { m.email.ifBlank { m.user_id } }.trim().lowercase()

  fun fieldDefKey(f: FieldDefinition): String = f.id.ifBlank { f.name }

  fun bindingKey(b: FieldBinding): String = b.id.ifBlank { b.field_path }

  fun foreignAttrKey(a: ForeignAttribute): String = a.id.ifBlank { a.qualified_name }

  fun actionKey(a: ActionDef): String = a.id.ifBlank { "${a.type}:${a.target_field}" }

  fun entityDeclKey(e: EntityDeclaration): String = e.id.ifBlank { e.dataset }

  fun entityPropMapKey(m: EntityPropertyMapping): String = m.id.ifBlank { m.entity_property }

  fun choiceKey(c: ChoiceItem): String = c.id.ifBlank { c.value_ }

  fun componentKey(c: ViewComponent): String =
    c.id.ifBlank {
      c.control?.field_ref ?: c.group?.field_ref ?: c.repeat?.field_ref ?: ""
    }

  fun propDefKey(p: EntityPropertyDefinition): String = p.id.ifBlank { p.name }

  /** Unidentified record nodes fall back to their position, so only same-position nodes match. */
  fun recordNodeKey(n: RecordNode, index: Int = 0): String = n.id.ifBlank { index.toString() }
}
