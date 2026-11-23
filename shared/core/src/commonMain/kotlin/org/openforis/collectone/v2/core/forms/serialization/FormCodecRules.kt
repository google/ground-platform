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
package org.openforis.collectone.v2.core.forms.serialization

import org.openforis.collectone.v2.core.proto.schema.ProtoCodecRules

/**
 * Where the textproto ([TextProtoSerializer]) and ProtoJSON ([ProtoJsonSerializer]) of forms and
 * records depart from the plain proto3 mapping of the schema-driven codecs.
 */
internal val FormCodecRules =
  ProtoCodecRules(
    // `google.protobuf.Any` payloads would need a type registry to expand; they are dropped.
    ignoredFields =
      setOf(
        "ControlDef.custom_extensions",
        "FieldBinding.custom_extensions",
        "FormDef.custom_extensions",
        "ModelDef.custom_extensions",
      ),
    omittedWhenDefault =
      setOf("ActionDef.value_expression", "GeoPoint.accuracy_meters", "GeoPoint.altitude_meters"),
    // Coordinates are always spelled out in textproto, even at 0.
    alwaysWritten = setOf("GeoPoint.latitude", "GeoPoint.longitude"),
    omittedWhenEmpty =
      setOf("RecordInstance.audit_log", "RecordInstance.data", "RecordInstance.metadata"),
    omittedWithout = mapOf("PrimaryInstance.default_values" to "fields"),
    zeroEnumsDropped = setOf("ActionDef.events"),
    // An action with both reads as a literal action.
    preferredOneofMembers = setOf("ActionDef.literal_value"),
  )
