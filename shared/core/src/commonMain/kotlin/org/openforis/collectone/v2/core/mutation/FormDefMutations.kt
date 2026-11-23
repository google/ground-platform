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

import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.EntityDeclaration
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FieldDefinition
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.PreloadType
import openforis.collectone.v1.forms.PrimaryInstance
import openforis.collectone.v1.forms.RecordSchema
import openforis.collectone.v1.forms.RepeatDef
import openforis.collectone.v1.forms.SecondaryInstance
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef

/**
 * Delete masks and upserts of one `FormDef` inside a survey (its model and view trees) and of
 * translation catalogs, with the mask semantics described in `KeyedListMasks.kt`. View components
 * are keyed by ID or field reference and merge recursively through groups and repeats.
 */
internal object FormDefMutations {
  fun applyFormDelete(base: FormDef, del: FormDef): FormDef {
    var f = base
    if (del.title.isNotEmpty()) f = f.copy(title = "")
    if (del.version.isNotEmpty()) f = f.copy(version = "")
    if (del.default_language.isNotEmpty()) f = f.copy(default_language = "")
    if (del.model != null) {
      f =
        if (del.model == ModelDef()) f.copy(model = null)
        else f.copy(model = f.model?.let { applyModelDelete(it, del.model) })
    }
    if (del.view != null) {
      f =
        if (del.view == ViewDef()) f.copy(view = null)
        else f.copy(view = f.view?.let { applyViewDelete(it, del.view) })
    }
    return f
  }

  fun mergeForm(base: FormDef, up: FormDef): FormDef {
    var f = base
    if (up.form_id.isNotEmpty()) f = f.copy(form_id = up.form_id)
    if (up.title.isNotEmpty()) f = f.copy(title = up.title)
    if (up.version.isNotEmpty()) f = f.copy(version = up.version)
    if (up.default_language.isNotEmpty()) f = f.copy(default_language = up.default_language)
    if (up.sort_order != 0) f = f.copy(sort_order = up.sort_order)
    if (up.custom_extensions.isNotEmpty()) f = f.copy(custom_extensions = up.custom_extensions)
    if (up.model != null) {
      f = f.copy(model = f.model?.let { mergeModel(it, up.model) } ?: up.model)
    }
    if (up.view != null) {
      f = f.copy(view = f.view?.let { mergeView(it, up.view) } ?: up.view)
    }
    return f
  }

  private fun applyModelDelete(base: ModelDef, del: ModelDef): ModelDef {
    var m = base
    if (del.primary_instance != null) {
      val dpi = del.primary_instance
      m =
        if (dpi == PrimaryInstance()) m.copy(primary_instance = null)
        else
          m.copy(primary_instance = m.primary_instance?.let { applyPrimaryInstanceDelete(it, dpi) })
    }
    if (del.secondary_instances.isNotEmpty()) {
      m =
        m.copy(
          secondary_instances =
            m.secondary_instances.applyKeyedDelete(
              deleteItems = del.secondary_instances,
              keyOf = { it.id },
              isLeaf = { it == SecondaryInstance(id = it.id) },
              applyBranchDelete = { b, _ -> b },
            )
        )
    }
    if (del.bindings.isNotEmpty()) {
      m =
        m.copy(
          bindings =
            m.bindings.applyKeyedDelete(
              deleteItems = del.bindings,
              keyOf = MutationKeys::bindingKey,
              isLeaf = { it == FieldBinding(id = it.id, field_path = it.field_path) },
              applyBranchDelete = ::applyBindingDelete,
            )
        )
    }
    if (del.translations != null) {
      m =
        if (del.translations.languages.isEmpty()) m.copy(translations = null)
        else
          m.copy(
            translations =
              m.translations?.let { applyTranslationCatalogDelete(it, del.translations) }
          )
    }
    if (del.actions.isNotEmpty()) {
      m =
        m.copy(
          actions =
            m.actions.applyKeyedDelete(
              deleteItems = del.actions,
              keyOf = MutationKeys::actionKey,
              isLeaf = { true },
              applyBranchDelete = { b, _ -> b },
            )
        )
    }
    if (del.entities.isNotEmpty()) {
      m =
        m.copy(
          entities =
            m.entities.applyKeyedDelete(
              deleteItems = del.entities,
              keyOf = MutationKeys::entityDeclKey,
              isLeaf = { it == EntityDeclaration(id = it.id, dataset = it.dataset) },
              applyBranchDelete = ::applyEntityDeclarationDelete,
            )
        )
    }
    if (del.metadata != null) m = m.copy(metadata = null)
    if (del.submission != null) m = m.copy(submission = null)
    return m
  }

  private fun applyPrimaryInstanceDelete(
    base: PrimaryInstance,
    del: PrimaryInstance,
  ): PrimaryInstance {
    var pi = base
    if (del.record_schema != null) {
      val drs = del.record_schema
      pi =
        if (drs == RecordSchema()) pi.copy(record_schema = null)
        else
          pi.copy(
            record_schema =
              pi.record_schema?.let { brs ->
                brs.copy(
                  fields =
                    brs.fields.applyKeyedDelete(
                      deleteItems = drs.fields,
                      keyOf = MutationKeys::fieldDefKey,
                      isLeaf = { it.fields.isEmpty() },
                      applyBranchDelete = ::applyFieldDefDelete,
                    )
                )
              }
          )
    }
    if (del.default_values != null) pi = pi.copy(default_values = null)
    return pi
  }

  private fun applyEntityDeclarationDelete(
    base: EntityDeclaration,
    del: EntityDeclaration,
  ): EntityDeclaration =
    base.copy(
      property_mappings =
        base.property_mappings.applyKeyedDelete(
          deleteItems = del.property_mappings,
          keyOf = MutationKeys::entityPropMapKey,
          isLeaf = { true },
          applyBranchDelete = { bm, _ -> bm },
        )
    )

  private fun applyFieldDefDelete(base: FieldDefinition, del: FieldDefinition): FieldDefinition =
    base.copy(
      fields =
        base.fields.applyKeyedDelete(
          deleteItems = del.fields,
          keyOf = MutationKeys::fieldDefKey,
          isLeaf = { it.fields.isEmpty() },
          applyBranchDelete = ::applyFieldDefDelete,
        )
    )

  private fun applyBindingDelete(base: FieldBinding, del: FieldBinding): FieldBinding {
    var b = base
    if (del.read_only) b = b.copy(read_only = false)
    if (del.relevant_expression.isNotEmpty()) b = b.copy(relevant_expression = "")
    if (del.calculate_expression.isNotEmpty()) b = b.copy(calculate_expression = "")
    if (del.constraint_expression.isNotEmpty()) b = b.copy(constraint_expression = "")
    if (del.required_expression.isNotEmpty()) b = b.copy(required_expression = "")
    if (del.constraint_message.isNotEmpty()) b = b.copy(constraint_message = "")
    if (del.required_message.isNotEmpty()) b = b.copy(required_message = "")
    if (del.sms_tag.isNotEmpty()) b = b.copy(sms_tag = "")
    if (del.entity_saveto.isNotEmpty()) b = b.copy(entity_saveto = "")
    if (del.max_pixels != 0) b = b.copy(max_pixels = 0)
    if (del.preload != PreloadType.PRELOAD_UNSPECIFIED) {
      b = b.copy(preload = PreloadType.PRELOAD_UNSPECIFIED)
    }
    if (del.preload_param.isNotEmpty()) b = b.copy(preload_param = "")
    if (del.read_only_expression.isNotEmpty()) b = b.copy(read_only_expression = "")
    return applyForeignAttributesDelete(b, del)
  }

  private fun applyForeignAttributesDelete(base: FieldBinding, del: FieldBinding): FieldBinding =
    if (del.foreign_attributes.isEmpty()) base
    else
      base.copy(
        foreign_attributes =
          base.foreign_attributes.applyKeyedDelete(
            deleteItems = del.foreign_attributes,
            keyOf = MutationKeys::foreignAttrKey,
            isLeaf = { true },
            applyBranchDelete = { ba, _ -> ba },
          )
      )

  private fun mergeModel(base: ModelDef, up: ModelDef): ModelDef {
    var m = base
    if (up.primary_instance != null) m = m.copy(primary_instance = up.primary_instance)
    if (up.secondary_instances.isNotEmpty()) {
      m =
        m.copy(
          secondary_instances =
            m.secondary_instances.mergeKeyedUpsert(
              upsertItems = up.secondary_instances,
              keyOf = { it.id },
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.bindings.isNotEmpty()) {
      m =
        m.copy(
          bindings =
            m.bindings.mergeKeyedUpsert(
              upsertItems = up.bindings,
              keyOf = MutationKeys::bindingKey,
              mergeItem = { _, u -> u },
            )
        )
    }
    if (up.translations != null) {
      m =
        m.copy(
          translations =
            m.translations?.let { mergeTranslationCatalog(it, up.translations) } ?: up.translations
        )
    }
    if (up.actions.isNotEmpty()) {
      m =
        m.copy(
          actions =
            m.actions.mergeKeyedUpsert(
              upsertItems = up.actions,
              keyOf = MutationKeys::actionKey,
              mergeItem = { _, u -> u },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.entities.isNotEmpty()) {
      m =
        m.copy(
          entities =
            m.entities.mergeKeyedUpsert(
              upsertItems = up.entities,
              keyOf = MutationKeys::entityDeclKey,
              mergeItem = { _, u -> u },
            )
        )
    }
    if (up.metadata != null) m = m.copy(metadata = up.metadata)
    if (up.submission != null) m = m.copy(submission = up.submission)
    if (up.custom_extensions.isNotEmpty()) m = m.copy(custom_extensions = up.custom_extensions)
    return m
  }

  private fun applyViewDelete(base: ViewDef, del: ViewDef): ViewDef =
    base.copy(components = applyComponentsDelete(base.components, del.components))

  private fun applyComponentsDelete(
    base: List<ViewComponent>,
    del: List<ViewComponent>,
  ): List<ViewComponent> =
    base.applyKeyedDelete(
      deleteItems = del,
      keyOf = MutationKeys::componentKey,
      isLeaf = ::isComponentLeafDelete,
      applyBranchDelete = ::applyComponentBranchDelete,
    )

  private fun isComponentLeafDelete(c: ViewComponent): Boolean {
    val ctrl = c.control
    val grp = c.group
    val rep = c.repeat
    return when {
      ctrl != null -> ctrl == ControlDef(field_ref = ctrl.field_ref)
      grp != null -> grp == GroupDef(field_ref = grp.field_ref)
      rep != null -> rep == RepeatDef(field_ref = rep.field_ref)
      else -> true
    }
  }

  private fun applyComponentBranchDelete(base: ViewComponent, del: ViewComponent): ViewComponent {
    val bCtrl = base.control
    val dCtrl = del.control
    val bGrp = base.group
    val dGrp = del.group
    val bRep = base.repeat
    val dRep = del.repeat
    return when {
      bCtrl != null && dCtrl != null -> {
        var c = bCtrl
        if (dCtrl.label != null) c = c.copy(label = null)
        if (dCtrl.hint != null) c = c.copy(hint = null)
        if (dCtrl.appearance.isNotEmpty()) c = c.copy(appearance = "")
        if (dCtrl.choices.isNotEmpty()) {
          c =
            c.copy(
              choices =
                c.choices.applyKeyedDelete(
                  deleteItems = dCtrl.choices,
                  keyOf = MutationKeys::choiceKey,
                  isLeaf = { true },
                  applyBranchDelete = { bc, _ -> bc },
                )
            )
        }
        if (dCtrl.itemset != null) c = c.copy(itemset = null)
        if (dCtrl.range_config != null) c = c.copy(range_config = null)
        if (dCtrl.geo_config != null) c = c.copy(geo_config = null)
        base.copy(control = c)
      }
      bGrp != null && dGrp != null ->
        base.copy(
          group = bGrp.copy(components = applyComponentsDelete(bGrp.components, dGrp.components))
        )
      bRep != null && dRep != null ->
        base.copy(
          repeat = bRep.copy(components = applyComponentsDelete(bRep.components, dRep.components))
        )
      else -> base
    }
  }

  private fun mergeView(base: ViewDef, up: ViewDef): ViewDef =
    base.copy(components = mergeComponents(base.components, up.components))

  private fun mergeComponents(
    base: List<ViewComponent>,
    up: List<ViewComponent>,
  ): List<ViewComponent> =
    base.mergeKeyedUpsert(
      upsertItems = up,
      keyOf = MutationKeys::componentKey,
      mergeItem = ::mergeComponent,
      orderOf = { it.sort_order },
    )

  /**
   * Merges a component of the same key: a control, group, or repeat merges into one of its own
   * kind; an upsert of another kind replaces the stored component.
   */
  private fun mergeComponent(base: ViewComponent, up: ViewComponent): ViewComponent {
    val bCtrl = base.control
    val uCtrl = up.control
    val bGrp = base.group
    val uGrp = up.group
    val bRep = base.repeat
    val uRep = up.repeat
    val sortOrder = if (up.sort_order != 0) up.sort_order else base.sort_order
    val id = up.id.ifBlank { base.id }
    return when {
      bCtrl != null && uCtrl != null ->
        base.copy(control = mergeControl(bCtrl, uCtrl), id = id, sort_order = sortOrder)
      bGrp != null && uGrp != null ->
        base.copy(group = mergeGroup(bGrp, uGrp), id = id, sort_order = sortOrder)
      bRep != null && uRep != null ->
        base.copy(repeat = mergeRepeat(bRep, uRep), id = id, sort_order = sortOrder)
      else -> up.copy(id = id, sort_order = sortOrder)
    }
  }

  private fun mergeControl(base: ControlDef, up: ControlDef): ControlDef {
    var c = base
    if (up.field_ref.isNotEmpty()) c = c.copy(field_ref = up.field_ref)
    if (up.type != ControlType.CONTROL_TYPE_UNSPECIFIED) c = c.copy(type = up.type)
    if (up.label != null) c = c.copy(label = up.label)
    if (up.hint != null) c = c.copy(hint = up.hint)
    if (up.appearance.isNotEmpty()) c = c.copy(appearance = up.appearance)
    if (up.choices.isNotEmpty()) {
      c =
        c.copy(
          choices =
            c.choices.mergeKeyedUpsert(
              upsertItems = up.choices,
              keyOf = MutationKeys::choiceKey,
              mergeItem = { _, uc -> uc },
              orderOf = { it.sort_order },
            )
        )
    }
    if (up.itemset != null) c = c.copy(itemset = up.itemset)
    if (up.range_config != null) c = c.copy(range_config = up.range_config)
    if (up.media_type.isNotEmpty()) c = c.copy(media_type = up.media_type)
    if (up.intent != null) c = c.copy(intent = up.intent)
    if (up.actions.isNotEmpty()) c = c.copy(actions = up.actions)
    if (up.geo_config != null) c = c.copy(geo_config = up.geo_config)
    if (up.parent_question_key.isNotEmpty()) {
      c = c.copy(parent_question_key = up.parent_question_key)
    }
    return c
  }

  private fun mergeGroup(base: GroupDef, up: GroupDef): GroupDef {
    var g = base
    if (up.field_ref.isNotEmpty()) g = g.copy(field_ref = up.field_ref)
    if (up.label != null) g = g.copy(label = up.label)
    if (up.appearance.isNotEmpty()) g = g.copy(appearance = up.appearance)
    if (up.intent != null) g = g.copy(intent = up.intent)
    if (up.components.isNotEmpty()) {
      g = g.copy(components = mergeComponents(g.components, up.components))
    }
    return g
  }

  private fun mergeRepeat(base: RepeatDef, up: RepeatDef): RepeatDef {
    var r = base
    if (up.field_ref.isNotEmpty()) r = r.copy(field_ref = up.field_ref)
    if (up.label != null) r = r.copy(label = up.label)
    if (up.appearance.isNotEmpty()) r = r.copy(appearance = up.appearance)
    if (up.count_expression.isNotEmpty()) r = r.copy(count_expression = up.count_expression)
    if (up.no_add_remove) r = r.copy(no_add_remove = true)
    if (up.components.isNotEmpty()) {
      r = r.copy(components = mergeComponents(r.components, up.components))
    }
    if (up.actions.isNotEmpty()) r = r.copy(actions = up.actions)
    return r
  }

  fun applyTranslationCatalogDelete(
    base: TranslationCatalog,
    del: TranslationCatalog,
  ): TranslationCatalog =
    base.copy(
      languages =
        base.languages.applyKeyedDelete(
          deleteItems = del.languages,
          keyOf = { it.language },
          isLeaf = { it.strings.isEmpty() && !it.is_default },
          applyBranchDelete = { b, d ->
            b.copy(
              is_default = if (d.is_default) false else b.is_default,
              strings = b.strings - d.strings.keys,
            )
          },
        )
    )

  fun mergeTranslationCatalog(
    base: TranslationCatalog,
    up: TranslationCatalog,
  ): TranslationCatalog =
    base.copy(
      languages =
        base.languages.mergeKeyedUpsert(
          upsertItems = up.languages,
          keyOf = { it.language },
          mergeItem = { b, u ->
            b.copy(
              is_default = if (u.is_default) true else b.is_default,
              strings = b.strings + u.strings,
              sort_order = if (u.sort_order != 0) u.sort_order else b.sort_order,
            )
          },
          orderOf = { it.sort_order },
        )
    )
}
