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
package org.openforis.collectone.v2.app.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.AuditInfo
import openforis.collectone.v1.data.SubmissionRecord
import org.openforis.collectone.v2.app.domain.model.SubmissionPreviewItem

/**
 * `SubmissionRecord.audit_info` is server-owned: the client reads it but never writes it to its own
 * copies of submissions.
 */
class SubmissionAuditInfoMappingTest {
  @Test
  fun submissionToProto_leavesTheServerOwnedAuditInfoUnset() {
    val submission =
      SubmissionPreviewItem(
        id = "submission-1",
        formId = "form-1",
        formTitle = "Tree inventory",
        formVersion = "1",
        collectorName = "Amina Otieno",
        collectorEmail = "amina@example.org",
        fields = emptyList(),
      )

    val proto = ProtoRecordMapper.submissionToProto("survey-1", submission)

    assertNull(proto.audit_info)
    assertEquals("amina@example.org", proto.collector_email)
  }

  @Test
  fun submissionFromProto_readsTheCollectorFromServerAuditInfo_whenItHasNoCollectorEmail() {
    val proto =
      SubmissionRecord(
        submission_id = "submission-1",
        form_id = "form-1",
        audit_info = AuditInfo(created_by = "amina@example.org"),
      )

    assertEquals("amina@example.org", ProtoRecordMapper.submissionFromProto(proto).collectorEmail)
  }

  @Test
  fun submissionFromProto_prefersTheCollectorEmail_overServerAuditInfo() {
    val proto =
      SubmissionRecord(
        submission_id = "submission-1",
        form_id = "form-1",
        collector_email = "amina@example.org",
        audit_info = AuditInfo(created_by = "user-42"),
      )

    assertEquals("amina@example.org", ProtoRecordMapper.submissionFromProto(proto).collectorEmail)
  }
}
