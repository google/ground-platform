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
package org.openforis.collectone.v2.app.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.openforis.collectone.v2.app.domain.model.BasemapType
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.CountedMessageKey
import org.openforis.collectone.v2.app.domain.model.DomainMessage
import org.openforis.collectone.v2.app.domain.model.EffortComparison
import org.openforis.collectone.v2.app.domain.model.ImagerySourceType
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.InvitationStatus
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocationLockState
import org.openforis.collectone.v2.app.domain.model.MeasurementUnitSystem
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.MessageKey
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.MutationSyncState
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.QuotaCountedMessage
import org.openforis.collectone.v2.app.domain.model.QuotaMessage
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.SubmissionNotice
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.UploadStatusFilter
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.DisplayLogicMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueCountedMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorIssueMessage
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.FormPreviewTarget
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.MediaSource
import org.openforis.collectone.v2.app.domain.model.editor.PeerDataVisibility
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.StatusConditionSubject
import org.openforis.collectone.v2.app.domain.model.editor.StatusMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern
import org.openforis.collectone.v2.app.domain.model.message
import org.openforis.collectone.v2.map.GeometryType

class DomainMessageTextTest {
  private val textKeys: List<MessageKey> =
    EditorIssueMessage.entries +
      DisplayLogicMessage.entries +
      QuotaMessage.entries +
      SubmissionNotice.entries

  private val countedKeys: List<CountedMessageKey> =
    EditorIssueCountedMessage.entries + QuotaCountedMessage.entries

  /** Every domain enum a [DomainMessage.Label] may carry. */
  private val labeledValues: List<Enum<*>> =
    listOf(
        ImpactEventType.entries,
        SurveyLifecycleState.entries,
        SurveyOutcomeKind.entries,
        EffortComparison.entries,
        SyncStatus.entries,
        QuotaTier.entries,
        MeasurementUnitSystem.entries,
        LocationLockState.entries,
        BasemapType.entries,
        OrganizationRole.entries,
        MembershipStatus.entries,
        ImagerySourceType.entries,
        OrganizationType.entries,
        UploadStatusFilter.entries,
        MutationSyncState.entries,
        MutationOperationKind.entries,
        InvitationStatus.entries,
        GeometryType.entries,
        LibraryStatus.entries,
        ConceptAggregation.entries,
        PrivacyClass.entries,
        ImpactPillar.entries,
        ConceptDataType.entries,
        CollaboratorRole.entries,
        SharingPolicy.entries,
        PeerDataVisibility.entries,
        PropertyType.entries,
        SampleMethod.entries,
        AllocationMode.entries,
        PlotShapeOption.entries,
        SubPlotMode.entries,
        FormPreviewTarget.entries,
        EditorQuestionType.entries,
        MediaSource.entries,
        GeometryCapture.entries,
        RelevanceOperator.entries,
        ChoiceSource.entries,
        StatusMarkerSymbol.entries,
        StatusConditionSubject.entries,
        EditorPropertyKind.entries,
        DateRule.entries,
        TextPattern.entries,
      )
      .flatten()

  private fun english(text: UiText) = runBlocking { text.resolveString() }

  @Test
  fun everyMessageKey_hasItsOwnStringResource() {
    val resources = textKeys.map { key ->
      assertIs<UiText.Resource>(key.message().toUiText()).resource
    }

    assertEquals(textKeys.size, resources.toSet().size)
  }

  @Test
  fun everyCountedMessageKey_hasItsOwnPluralResource() {
    val resources = countedKeys.map { key ->
      assertIs<UiText.Plural>(key.message(2).toUiText()).resource
    }

    assertEquals(countedKeys.size, resources.toSet().size)
  }

  @Test
  fun everyLabeledDomainEnum_hasALabel() {
    val unlabeled = labeledValues.filter { value ->
      english(DomainMessage.Label(value).toUiText()).isBlank()
    }

    assertTrue(unlabeled.isEmpty(), "No English label for $unlabeled")
  }

  @Test
  fun countedMessage_keepsItsQuantityAndFormatsTheWordingForIt() {
    val one = QuotaCountedMessage.MAX_FORMS.message(1, 1, 0, "Free").toUiText()
    val many = QuotaCountedMessage.MAX_FORMS.message(3, 3, 2, "Free").toUiText()

    assertEquals(3, assertIs<UiText.Plural>(many).quantity)
    assertEquals("Survey contains 1 form, exceeding the limit of 0 for Free tier.", english(one))
    assertEquals("Survey contains 3 forms, exceeding the limit of 2 for Free tier.", english(many))
  }

  @Test
  fun nestedMessageArguments_areLocalizedAndOtherArgumentsPassThrough() {
    val message =
      DisplayLogicMessage.SHOWN_IF.message(
        "Tree count",
        DomainMessage.Lowercase(DomainMessage.Label(RelevanceOperator.entries.first())),
        42,
      )

    val text = assertIs<UiText.Resource>(message.toUiText())

    assertEquals("Tree count", text.args[0])
    assertIs<UiText.Lowercase>(text.args[1])
    assertEquals(42, text.args[2])
    val operator = english(DomainMessage.Label(RelevanceOperator.entries.first()).toUiText())
    assertEquals("Shown only if \"Tree count\" ${operator.lowercase()} 42.", english(text))
  }

  @Test
  fun lowercase_lowersTheLocalizedLabel() {
    val label = DomainMessage.Label(SurveyLifecycleState.entries.first())

    val lowered = english(DomainMessage.Lowercase(label).toUiText())

    assertEquals(english(label.toUiText()).lowercase(), lowered)
  }

  @Test
  fun keysWithoutAWording_failLoudly() {
    val unknownKey = object : MessageKey {}
    val unknownCountedKey = object : CountedMessageKey {}

    assertFailsWith<IllegalArgumentException> { unknownKey.message().toUiText() }
    assertFailsWith<IllegalArgumentException> { unknownCountedKey.message(1).toUiText() }
    assertFailsWith<IllegalArgumentException> {
      DomainMessage.Label(UnlabeledEnum.VALUE).toUiText()
    }
  }

  private enum class UnlabeledEnum {
    VALUE
  }
}
