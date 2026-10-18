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
package org.groundplatform.v2.core.forms.model

import groundplatform.v2.forms.FieldValue
import groundplatform.v2.forms.FormDef
import groundplatform.v2.forms.RecordInstance
import groundplatform.v2.forms.RecordNode
import groundplatform.v2.forms.SecondaryInstance
import groundplatform.v2.forms.TypedValue

/**
 * Core domain model wrapping a [FormDef] Protocol Buffer message.
 *
 * Provides strongly typed access to form metadata, schema, bindings, secondary instances, and UI
 * view hierarchy without exposing raw interchange strings (such as XML or JSON) internally.
 */
data class FormDefinition(val proto: FormDef) {
  val id: String
    get() = proto.form_id

  val title: String
    get() = proto.title

  val version: String
    get() = proto.version

  val defaultLanguage: String
    get() = proto.default_language
}

/**
 * Core domain model wrapping a [RecordInstance] Protocol Buffer message.
 *
 * Represents a populated form record instance in memory and local storage.
 */
data class FormRecordInstance(val proto: RecordInstance) {
  val formId: String
    get() = proto.form_id

  val formVersion: String
    get() = proto.form_version

  val instanceId: String
    get() = proto.metadata?.instance_id.orEmpty()
}

/** Constructs a [RecordNode] row for a [SecondaryInstance] from string column key-value pairs. */
fun secondaryInstanceRow(vararg columns: Pair<String, String>): RecordNode =
  RecordNode(
    fields =
      columns.associate { (k, v) -> k to FieldValue(scalar_value = TypedValue(string_value = v)) }
  )

/** Constructs a [RecordNode] row for a [SecondaryInstance] from a string column map. */
fun secondaryInstanceRow(columns: Map<String, String>): RecordNode =
  RecordNode(
    fields = columns.mapValues { (_, v) -> FieldValue(scalar_value = TypedValue(string_value = v)) }
  )
