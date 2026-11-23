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

import com.squareup.wire.ofEpochSecond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import openforis.collectone.v1.data.SubmissionRecord

/**
 * `SubmissionRecord.client_updated_at` and `server_updated_at` map to the submission's update
 * times, and rows stored before either existed fall back to their commit time.
 */
class SubmissionUpdateTimesMappingTest {
  /** 2026-09-24T08:15:00Z. */
  private val savedOnDevice = ofEpochSecond(1_790_237_700L, 0L)

  /** 2026-09-24T08:17:00Z. */
  private val acceptedByServer = ofEpochSecond(1_790_237_820L, 0L)

  /** 2026-02-02T02:40:00Z. */
  private val committed = ofEpochSecond(1_770_000_000L, 0L)

  private fun record(
    clientUpdatedAt: com.squareup.wire.Instant? = null,
    serverUpdatedAt: com.squareup.wire.Instant? = null,
  ) =
    SubmissionRecord(
      submission_id = "submission-1",
      form_id = "form-1",
      client_updated_at = clientUpdatedAt,
      server_updated_at = serverUpdatedAt,
      commit_timestamp = committed,
    )

  @Test
  fun updateTimes_areReadFromTheirTimestamps() {
    val submission =
      ProtoRecordMapper.submissionFromProto(
        record(clientUpdatedAt = savedOnDevice, serverUpdatedAt = acceptedByServer)
      )

    assertEquals(Instant.parse("2026-09-24T08:15:00Z"), submission.clientUpdatedAt)
    assertEquals(Instant.parse("2026-09-24T08:17:00Z"), submission.serverUpdatedAt)
  }

  @Test
  fun updateTimes_areWrittenToTheirTimestamps() {
    val submission =
      ProtoRecordMapper.submissionFromProto(
        record(clientUpdatedAt = savedOnDevice, serverUpdatedAt = acceptedByServer)
      )

    val proto = ProtoRecordMapper.submissionToProto("survey-1", submission)

    assertEquals(savedOnDevice, proto.client_updated_at)
    assertEquals(acceptedByServer, proto.server_updated_at)
  }

  @Test
  fun rowWithoutUpdateTimes_fallsBackToTheCommitTime() {
    val submission = ProtoRecordMapper.submissionFromProto(record())

    assertNull(submission.clientUpdatedAt)
    assertEquals(Instant.parse("2026-02-02T02:40:00Z"), submission.serverUpdatedAt)
  }

  @Test
  fun notYetUploadedRow_keepsNoServerTime() {
    val submission = ProtoRecordMapper.submissionFromProto(record(clientUpdatedAt = savedOnDevice))

    assertNull(submission.serverUpdatedAt)
  }

  @Test
  fun rowWithTheReservedTimestampLabel_stillDecodes() {
    val label = "2026-09-24 08:15 UTC".encodeUtf8()
    // Field 20 (`timestamp_label`, now reserved), length-delimited: tag (20 << 3) | 2 = 0xA2 0x01.
    val oldRow =
      Buffer()
        .write(record().encodeByteString())
        .writeByte(0xA2)
        .writeByte(0x01)
        .writeByte(label.size)
        .write(label)
        .readByteString()

    val submission = ProtoRecordMapper.submissionFromProto(SubmissionRecord.ADAPTER.decode(oldRow))

    assertEquals("submission-1", submission.id)
    assertEquals(Instant.parse("2026-02-02T02:40:00Z"), submission.updatedAt)
  }
}
