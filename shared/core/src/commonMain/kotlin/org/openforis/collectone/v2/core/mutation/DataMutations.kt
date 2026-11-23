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

import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.ReviewStatus
import openforis.collectone.v1.data.SubmissionRecord
import openforis.collectone.v1.forms.RecordInstance
import openforis.collectone.v1.forms.RecordNode
import openforis.collectone.v1.forms.RecordNodeList
import org.openforis.collectone.v2.core.geo.GeometryProtos

/** Applies sparse `EntityRecord` and `SubmissionRecord` `delete` and `upsert` masks. */
object DataMutations {
  /** Applies [delete] then merges [upsert] onto [record]. */
  fun applyEntity(
    record: EntityRecord,
    delete: EntityRecord? = null,
    upsert: EntityRecord? = null,
  ): EntityRecord {
    val afterDelete = if (delete != null) applyEntityDelete(record, delete) else record
    return if (upsert != null) mergeEntity(afterDelete, upsert) else afterDelete
  }

  /**
   * Clears what [delete] marks on [record]. Any geometry in the mask, canonical or legacy, clears
   * every geometry field, so a record never keeps a stale legacy copy; properties are removed by
   * key.
   */
  fun applyEntityDelete(record: EntityRecord, delete: EntityRecord): EntityRecord {
    var r = record
    if (delete.label.isNotEmpty()) r = r.copy(label = "")
    if (GeometryProtos.hasGeometry(delete)) {
      r = r.copy(geometry = null, point = null, trace = null, shape = null)
    }
    if (delete.properties.isNotEmpty()) {
      r = r.copy(properties = r.properties - delete.properties.keys)
    }
    if (delete.is_field_created) r = r.copy(is_field_created = false)
    if (delete.is_deleted) r = r.copy(is_deleted = false)
    if (delete.s2_cell_token.isNotEmpty()) r = r.copy(s2_cell_token = "")
    if (delete.active_lock != null) r = r.copy(active_lock = null)
    if (delete.dataset_name.isNotEmpty()) r = r.copy(dataset_name = "")
    if (delete.layer_id.isNotEmpty()) r = r.copy(layer_id = "")
    if (delete.geo_id.isNotEmpty()) r = r.copy(geo_id = "")
    if (delete.area_hectares != 0.0) r = r.copy(area_hectares = 0.0)
    if (delete.perimeter_meters != 0) r = r.copy(perimeter_meters = 0)
    if (delete.color_hex != 0L) r = r.copy(color_hex = 0L)
    if (delete.singular_type_label.isNotEmpty()) r = r.copy(singular_type_label = "")
    if (delete.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = "")
    return r
  }

  /** Merges the fields [upsert] sets onto [base]; properties merge by key. */
  fun mergeEntity(base: EntityRecord, upsert: EntityRecord): EntityRecord {
    var r = base
    if (upsert.entity_id.isNotEmpty()) r = r.copy(entity_id = upsert.entity_id)
    if (upsert.dataset_id.isNotEmpty()) r = r.copy(dataset_id = upsert.dataset_id)
    if (upsert.survey_id.isNotEmpty()) r = r.copy(survey_id = upsert.survey_id)
    if (upsert.label.isNotEmpty()) r = r.copy(label = upsert.label)
    // A new geometry, canonical or legacy, replaces the old one in either form.
    when {
      upsert.geometry != null ->
        r = r.copy(geometry = upsert.geometry, point = null, trace = null, shape = null)
      upsert.point != null ->
        r = r.copy(geometry = null, point = upsert.point, trace = null, shape = null)
      upsert.trace != null ->
        r = r.copy(geometry = null, point = null, trace = upsert.trace, shape = null)
      upsert.shape != null ->
        r = r.copy(geometry = null, point = null, trace = null, shape = upsert.shape)
    }
    if (upsert.properties.isNotEmpty()) {
      r = r.copy(properties = r.properties + upsert.properties)
    }
    if (upsert.is_field_created) r = r.copy(is_field_created = true)
    if (upsert.audit_info != null) r = r.copy(audit_info = upsert.audit_info)
    if (upsert.is_deleted) r = r.copy(is_deleted = true)
    if (upsert.s2_cell_token.isNotEmpty()) r = r.copy(s2_cell_token = upsert.s2_cell_token)
    if (upsert.active_lock != null) r = r.copy(active_lock = upsert.active_lock)
    if (upsert.dataset_name.isNotEmpty()) r = r.copy(dataset_name = upsert.dataset_name)
    if (upsert.layer_id.isNotEmpty()) r = r.copy(layer_id = upsert.layer_id)
    if (upsert.geo_id.isNotEmpty()) r = r.copy(geo_id = upsert.geo_id)
    if (upsert.area_hectares != 0.0) r = r.copy(area_hectares = upsert.area_hectares)
    if (upsert.perimeter_meters != 0) r = r.copy(perimeter_meters = upsert.perimeter_meters)
    if (upsert.color_hex != 0L) r = r.copy(color_hex = upsert.color_hex)
    if (upsert.singular_type_label.isNotEmpty()) {
      r = r.copy(singular_type_label = upsert.singular_type_label)
    }
    if (upsert.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = upsert.sync_status_key)
    return r
  }

  /** Applies [delete] then merges [upsert] onto [record]. */
  fun applySubmission(
    record: SubmissionRecord,
    delete: SubmissionRecord? = null,
    upsert: SubmissionRecord? = null,
  ): SubmissionRecord {
    val afterDelete = if (delete != null) applySubmissionDelete(record, delete) else record
    return if (upsert != null) mergeSubmission(afterDelete, upsert) else afterDelete
  }

  /**
   * Clears what [delete] marks on [record]. Inside the record's answers, a field mask with no
   * children removes the answer, and repeat entries are matched by their node ID.
   */
  fun applySubmissionDelete(record: SubmissionRecord, delete: SubmissionRecord): SubmissionRecord {
    var r = record
    if (delete.entity_id.isNotEmpty()) r = r.copy(entity_id = "")
    if (delete.form_version.isNotEmpty()) r = r.copy(form_version = "")
    if (delete.is_deleted) r = r.copy(is_deleted = false)
    if (delete.is_quarantined) r = r.copy(is_quarantined = false)
    if (delete.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED) {
      r = r.copy(review_status = ReviewStatus.REVIEW_STATUS_UNSPECIFIED)
    }
    if (delete.entity_label.isNotEmpty()) r = r.copy(entity_label = "")
    if (delete.form_title.isNotEmpty()) r = r.copy(form_title = "")
    if (delete.collector_name.isNotEmpty()) r = r.copy(collector_name = "")
    if (delete.collector_email.isNotEmpty()) r = r.copy(collector_email = "")
    if (delete.target_type_label.isNotEmpty()) r = r.copy(target_type_label = "")
    if (delete.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = "")
    if (delete.location != null) r = r.copy(location = null)
    if (delete.field_entries.isNotEmpty()) r = r.copy(field_entries = emptyList())
    if (delete.geometry_polygons.isNotEmpty()) r = r.copy(geometry_polygons = emptyList())
    if (delete.metadata != null) r = r.copy(metadata = null)
    if (delete.client_updated_at != null) r = r.copy(client_updated_at = null)
    if (delete.raw_payload != null) r = r.copy(raw_payload = null)
    if (delete.record != null) {
      r =
        if (delete.record == RecordInstance()) r.copy(record = null)
        else r.copy(record = r.record?.let { applyRecordInstanceDelete(it, delete.record) })
    }
    return r
  }

  private fun applyRecordInstanceDelete(
    base: RecordInstance,
    del: RecordInstance,
  ): RecordInstance {
    var ri = base
    if (del.metadata != null) ri = ri.copy(metadata = null)
    if (del.audit_log != null) ri = ri.copy(audit_log = null)
    if (del.data_ != null) {
      ri =
        if (del.data_.fields.isEmpty()) ri.copy(data_ = null)
        else ri.copy(data_ = ri.data_?.let { applyRecordNodeDelete(it, del.data_) })
    }
    return ri
  }

  private fun applyRecordNodeDelete(base: RecordNode, del: RecordNode): RecordNode {
    if (del.fields.isEmpty()) return base
    val updated = LinkedHashMap(base.fields)
    for ((k, dVal) in del.fields) {
      val bVal = updated[k] ?: continue
      val bNode = bVal.node_value
      val dNode = dVal.node_value
      val bRep = bVal.repeat_value
      val dRep = dVal.repeat_value
      when {
        bNode != null && dNode != null && dNode.fields.isNotEmpty() ->
          updated[k] = bVal.copy(node_value = applyRecordNodeDelete(bNode, dNode))
        bRep != null && dRep != null && dRep.nodes.isNotEmpty() -> {
          val nodes =
            bRep.nodes.applyKeyedDelete(
              deleteItems = dRep.nodes,
              keyOf = { it.id },
              isLeaf = { it.fields.isEmpty() },
              applyBranchDelete = ::applyRecordNodeDelete,
            )
          updated[k] = bVal.copy(repeat_value = RecordNodeList(nodes = nodes))
        }
        else -> updated.remove(k)
      }
    }
    return base.copy(fields = updated)
  }

  /** Merges the fields [upsert] sets onto [base]; see [mergeRecordNode] for the answers. */
  fun mergeSubmission(base: SubmissionRecord, upsert: SubmissionRecord): SubmissionRecord {
    var r = base
    if (upsert.submission_id.isNotEmpty()) r = r.copy(submission_id = upsert.submission_id)
    if (upsert.form_id.isNotEmpty()) r = r.copy(form_id = upsert.form_id)
    if (upsert.survey_id.isNotEmpty()) r = r.copy(survey_id = upsert.survey_id)
    if (upsert.entity_id.isNotEmpty()) r = r.copy(entity_id = upsert.entity_id)
    if (upsert.form_version.isNotEmpty()) r = r.copy(form_version = upsert.form_version)
    if (upsert.audit_info != null) r = r.copy(audit_info = upsert.audit_info)
    if (upsert.is_deleted) r = r.copy(is_deleted = true)
    if (upsert.is_quarantined) r = r.copy(is_quarantined = true)
    if (upsert.review_status != ReviewStatus.REVIEW_STATUS_UNSPECIFIED) {
      r = r.copy(review_status = upsert.review_status)
    }
    if (upsert.entity_label.isNotEmpty()) r = r.copy(entity_label = upsert.entity_label)
    if (upsert.form_title.isNotEmpty()) r = r.copy(form_title = upsert.form_title)
    if (upsert.collector_name.isNotEmpty()) r = r.copy(collector_name = upsert.collector_name)
    if (upsert.collector_email.isNotEmpty()) r = r.copy(collector_email = upsert.collector_email)
    if (upsert.target_type_label.isNotEmpty()) {
      r = r.copy(target_type_label = upsert.target_type_label)
    }
    if (upsert.sync_status_key.isNotEmpty()) r = r.copy(sync_status_key = upsert.sync_status_key)
    if (upsert.location != null) r = r.copy(location = upsert.location)
    if (upsert.field_entries.isNotEmpty()) r = r.copy(field_entries = upsert.field_entries)
    if (upsert.geometry_polygons.isNotEmpty()) {
      r = r.copy(geometry_polygons = upsert.geometry_polygons)
    }
    if (upsert.metadata != null) r = r.copy(metadata = upsert.metadata)
    if (upsert.client_updated_at != null) {
      r = r.copy(client_updated_at = upsert.client_updated_at)
    }
    // `server_updated_at` is server-owned: the server stamps it after merging, so an upsert's value
    // is ignored.
    // `record` and `raw_payload` share the `payload` oneof: setting one clears the other.
    if (upsert.record != null) {
      r =
        r.copy(
          record = r.record?.let { mergeRecordInstance(it, upsert.record) } ?: upsert.record,
          raw_payload = null,
        )
    }
    if (upsert.raw_payload != null) r = r.copy(record = null, raw_payload = upsert.raw_payload)
    return r
  }

  private fun mergeRecordInstance(base: RecordInstance, up: RecordInstance): RecordInstance {
    var ri = base
    if (up.form_id.isNotEmpty()) ri = ri.copy(form_id = up.form_id)
    if (up.form_version.isNotEmpty()) ri = ri.copy(form_version = up.form_version)
    if (up.metadata != null) ri = ri.copy(metadata = up.metadata)
    if (up.audit_log != null) ri = ri.copy(audit_log = up.audit_log)
    if (up.data_ != null) {
      ri = ri.copy(data_ = ri.data_?.let { mergeRecordNode(it, up.data_) } ?: up.data_)
    }
    return ri
  }

  /**
   * Merges answers: groups merge recursively, and other answers are replaced. Repeat entries merge
   * by node ID only when every stored and upserted entry has one; otherwise the upsert's entries
   * replace the stored ones whole, since entries without IDs can't be matched.
   */
  private fun mergeRecordNode(base: RecordNode, up: RecordNode): RecordNode {
    val updated = LinkedHashMap(base.fields)
    for ((k, uVal) in up.fields) {
      val bVal = updated[k]
      val bNode = bVal?.node_value
      val uNode = uVal.node_value
      val bRep = bVal?.repeat_value
      val uRep = uVal.repeat_value
      updated[k] =
        when {
          bNode != null && uNode != null -> uVal.copy(node_value = mergeRecordNode(bNode, uNode))
          bRep != null && uRep != null -> {
            val canKeyById =
              bRep.nodes.all { it.id.isNotEmpty() } && uRep.nodes.all { it.id.isNotEmpty() }
            val mergedNodes =
              if (canKeyById) {
                bRep.nodes.mergeKeyedUpsert(
                  upsertItems = uRep.nodes,
                  keyOf = { it.id },
                  mergeItem = ::mergeRecordNode,
                  orderOf = { it.sort_order },
                )
              } else {
                uRep.nodes
              }
            uVal.copy(repeat_value = RecordNodeList(nodes = mergedNodes))
          }
          else -> uVal
        }
    }
    return base.copy(
      fields = updated,
      id = up.id.ifBlank { base.id },
      sort_order = if (up.sort_order != 0) up.sort_order else base.sort_order,
    )
  }
}
