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
package org.groundplatform.v2.core.forms.xpath

import groundplatform.v2.forms.ChoiceItem
import groundplatform.v2.forms.ControlDef
import groundplatform.v2.forms.ControlType
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.LabelDef
import groundplatform.v2.forms.LanguageTranslation
import groundplatform.v2.forms.LocalizedString
import groundplatform.v2.forms.ModelDef
import groundplatform.v2.forms.PrimaryInstance
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordSchema
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TranslationCatalog
import groundplatform.v2.forms.ViewComponent
import groundplatform.v2.forms.ViewDef

typealias RecordNodeBuilder = org.groundplatform.v2.core.forms.RecordNodeBuilder

typealias RepeatListBuilder = org.groundplatform.v2.core.forms.RepeatListBuilder

fun buildRecordInstance(
  formId: String = "household",
  version: String = "1",
  instanceId: String = "uuid:test-1234",
  deviceId: String = "device-abc",
  schemaName: String = formId,
  block: RecordNodeBuilder.() -> Unit,
): RecordInstance =
  org.groundplatform.v2.core.forms.buildRecordInstance(
    formId = formId,
    version = version,
    instanceId = instanceId,
    deviceId = deviceId,
    schemaName = schemaName,
    block = block,
  )

fun buildTestFormDef(
  formId: String = "household",
  schemaName: String = formId,
  defaultLanguage: String = "English",
  secondaryInstances: List<SecondaryInstance> = emptyList(),
  translations: Map<String, Map<String, String>> = emptyMap(),
  choicesByFieldRef: Map<String, List<Pair<String, String>>> = emptyMap(),
): FormDef {
  val langTranslations = translations.map { (langName, dict) ->
    LanguageTranslation(
      language = langName,
      is_default = langName == defaultLanguage,
      strings = dict.mapValues { (_, text) -> LocalizedString(value_ = text) },
    )
  }

  val controls = choicesByFieldRef.map { (fieldRef, choicePairs) ->
    ViewComponent(
      control =
        ControlDef(
          field_ref = fieldRef,
          type = ControlType.CONTROL_SELECT_ONE,
          choices =
            choicePairs.map { (valStr, labelOrId) ->
              if (labelOrId.startsWith("itext:")) {
                ChoiceItem(
                  value_ = valStr,
                  label = LabelDef(text_id = labelOrId.removePrefix("itext:")),
                )
              } else {
                ChoiceItem(value_ = valStr, label = LabelDef(text = labelOrId))
              }
            },
        )
    )
  }

  return FormDef(
    form_id = formId,
    default_language = defaultLanguage,
    model =
      ModelDef(
        primary_instance = PrimaryInstance(record_schema = RecordSchema(name = schemaName)),
        secondary_instances = secondaryInstances,
        translations = TranslationCatalog(languages = langTranslations),
      ),
    view = ViewDef(components = controls),
  )
}
