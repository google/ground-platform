/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import openforis.collectone.v1.data.EntityRecord
import openforis.collectone.v1.data.SubmissionRecord

/**
 * Type nouns are the author's; the English texts older clients stored in their place read blank.
 */
class StoredTypeNounsTest {
  private fun entityNoun(stored: String) =
    ProtoRecordMapper.entityFromProto(
        EntityRecord(entity_id = "e1", singular_type_label = stored),
        submissions = emptyList(),
        isGenerated = false,
      )
      .typeNoun

  private fun submissionNoun(stored: String) =
    ProtoRecordMapper.submissionFromProto(
        SubmissionRecord(submission_id = "s1", target_type_label = stored)
      )
      .targetTypeNoun

  @Test
  fun authorNouns_decodeAsTyped() {
    assertEquals("Coffee Parcel", entityNoun("Coffee Parcel"))
    assertEquals("Coffee Parcel", submissionNoun("Coffee Parcel"))
  }

  @Test
  fun englishFallbackLocation_decodesBlank() {
    assertEquals("", entityNoun("Location"))
    assertEquals("", submissionNoun("Location"))
  }

  @Test
  fun englishStandaloneFieldLog_decodesBlank() {
    assertEquals("", submissionNoun("Standalone Field Log"))
  }
}
