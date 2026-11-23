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
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.ImpactEventType
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.MutationOperationKind
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.model.OrganizationType
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.QuotaTier
import org.openforis.collectone.v2.app.domain.model.SurveyLifecycleState
import org.openforis.collectone.v2.app.domain.model.SurveyOutcomeKind
import org.openforis.collectone.v2.app.domain.model.SyncStatus
import org.openforis.collectone.v2.app.domain.model.editor.AllocationMode
import org.openforis.collectone.v2.app.domain.model.editor.ChoiceSource
import org.openforis.collectone.v2.app.domain.model.editor.CollaboratorRole
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.DateRule
import org.openforis.collectone.v2.app.domain.model.editor.EditorPropertyKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture
import org.openforis.collectone.v2.app.domain.model.editor.PlotShapeOption
import org.openforis.collectone.v2.app.domain.model.editor.PropertyType
import org.openforis.collectone.v2.app.domain.model.editor.RelevanceOperator
import org.openforis.collectone.v2.app.domain.model.editor.SampleMethod
import org.openforis.collectone.v2.app.domain.model.editor.SharingPolicy
import org.openforis.collectone.v2.app.domain.model.editor.StatusMarkerSymbol
import org.openforis.collectone.v2.app.domain.model.editor.SubPlotMode
import org.openforis.collectone.v2.app.domain.model.editor.TextPattern
import org.openforis.collectone.v2.app.resources.Res
import org.openforis.collectone.v2.app.resources.domain_entity_geometry_kind_none
import org.openforis.collectone.v2.map.GeometryType

/**
 * The English string resources of domain enum labels keep the English text the enums carried before
 * the labels moved out of the domain (see [DomainStringsParityTest] for earlier moves).
 */
class DomainEnumLabelsEnglishTest {
  private fun <T> assertEnglish(expected: Map<T, String>, res: (T) -> StringResource) {
    runBlocking {
      expected.forEach { (value, english) ->
        assertEquals(english, getString(res(value)), "$value")
      }
    }
  }

  @Test
  fun impactEventType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ImpactEventType.EXPORT to "Exported",
        ImpactEventType.PARTNER_PUSH to "Sent to a partner",
        ImpactEventType.RECEIPT_GENERATED to "Receipt created",
        ImpactEventType.RECEIPT_SHARED to "Receipt shared",
        ImpactEventType.DOI_MINTED to "DOI minted",
        ImpactEventType.SURVEY_CLOSED to "Survey closed",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun surveyLifecycleState_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SurveyLifecycleState.DRAFT to "Draft",
        SurveyLifecycleState.PUBLISHED to "Published",
        SurveyLifecycleState.CLOSED to "Closed",
        SurveyLifecycleState.ARCHIVED to "Archived",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun surveyOutcomeKind_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SurveyOutcomeKind.SUBMITTED_EUDR_DDS to "Submitted in an EUDR due diligence statement",
        SurveyOutcomeKind.REPORTED_FERM to "Reported to FERM",
        SurveyOutcomeKind.LAND_TITLING to "Used for land titling",
        SurveyOutcomeKind.SHARED_WITH_BUYERS to "Shared with buyers",
        SurveyOutcomeKind.TRAINED_OR_VALIDATED_MODEL to "Trained or checked a map or model",
        SurveyOutcomeKind.PROTECTED_AREA_MANAGEMENT to "Used to manage a protected area",
        SurveyOutcomeKind.NOT_YET to "Not yet",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun syncStatus_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SyncStatus.UPLOADING to "Uploading",
        SyncStatus.SYNCED to "Synced",
        SyncStatus.FAILED to "Failed",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun syncStatus_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SyncStatus.UPLOADING to "Uploading local changes to server",
        SyncStatus.SYNCED to "Synchronized with cloud server",
        SyncStatus.FAILED to "Upload failed — tap to retry",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun quotaTier_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        QuotaTier.UNSPECIFIED to "Unspecified",
        QuotaTier.BASIC to "Basic",
        QuotaTier.SPONSORED to "Sponsored",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun organizationRole_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        OrganizationRole.MEMBER to "Member",
        OrganizationRole.MANAGER to "Manager",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun organizationRole_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        OrganizationRole.MEMBER to
          "Listed as affiliated, can see the organization's surveys and create new ones.",
        OrganizationRole.MANAGER to
          "Can edit the organization, manage members, and manage all of its surveys.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun organizationType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        OrganizationType.GOVERNMENT_AGENCY to "Government agency",
        OrganizationType.COOPERATIVE to "Cooperative",
        OrganizationType.IPLC_ORGANIZATION to "Indigenous Peoples or local community organization",
        OrganizationType.NGO to "NGO",
        OrganizationType.COMPANY to "Company",
        OrganizationType.RESEARCH to "Research institution",
        OrganizationType.OTHER to "Other",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun mutationOperationKind_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        MutationOperationKind.CREATE_SUBMISSION to "Form submitted",
        MutationOperationKind.UPDATE_SUBMISSION to "Form modified",
        MutationOperationKind.DELETE_SUBMISSION to "Form deleted",
        MutationOperationKind.CREATE_ENTITY to "Map feature created",
        MutationOperationKind.UPDATE_ENTITY to "Map feature modified",
        MutationOperationKind.DELETE_ENTITY to "Map feature deleted",
        MutationOperationKind.UPLOAD_MEDIA to "Photo attached",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun entityGeometryType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        GeometryType.POINT to "Point",
        GeometryType.LINE_STRING to "Line",
        GeometryType.POLYGON to "Polygon",
        null to "No geometry",
      ),
      { it?.labelRes ?: Res.string.domain_entity_geometry_kind_none },
    )
  }

  @Test
  fun libraryStatus_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        LibraryStatus.DRAFT to "Draft",
        LibraryStatus.STABLE to "Stable",
        LibraryStatus.DEPRECATED to "Deprecated",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun conceptAggregation_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ConceptAggregation.COUNT_DISTINCT_FEATURES to "Count map features",
        ConceptAggregation.COUNT_BY_CODE to "Count by value",
        ConceptAggregation.SUM to "Sum",
        ConceptAggregation.MEAN to "Average",
        ConceptAggregation.SHARE_BY_CODE to "Share by value",
        ConceptAggregation.NONE to "Not aggregated",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun conceptAggregation_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ConceptAggregation.COUNT_DISTINCT_FEATURES to "Counts map features with an answer.",
        ConceptAggregation.COUNT_BY_CODE to "Counts map features for each value in the list.",
        ConceptAggregation.SUM to "Adds up the answers.",
        ConceptAggregation.MEAN to "Averages the answers.",
        ConceptAggregation.SHARE_BY_CODE to "Shows the share of map features with each value.",
        ConceptAggregation.NONE to "Names, identifiers, and other answers that aren't rolled up.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun privacyClass_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        PrivacyClass.AGGREGATE_PUBLIC to "Public totals",
        PrivacyClass.ORG_ONLY to "Organization only",
        PrivacyClass.SENSITIVE to "Sensitive",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun privacyClass_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        PrivacyClass.AGGREGATE_PUBLIC to
          "Totals can appear in platform-wide and public dashboards.",
        PrivacyClass.ORG_ONLY to "Totals are visible only within the owning organization.",
        PrivacyClass.SENSITIVE to "Never included in cross-organization or public totals.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun impactPillar_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ImpactPillar.MITIGATION to "Mitigation",
        ImpactPillar.ADAPTATION to "Adaptation",
        ImpactPillar.PROTECTION to "Protection",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun conceptDataType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ConceptDataType.TEXT to "Text",
        ConceptDataType.INTEGER to "Integer",
        ConceptDataType.DECIMAL to "Decimal",
        ConceptDataType.DATE to "Date",
        ConceptDataType.SELECT_ONE to "Select one",
        ConceptDataType.SELECT_MULTIPLE to "Select multiple",
        ConceptDataType.POINT to "Point",
        ConceptDataType.LINE to "Line",
        ConceptDataType.POLYGON to "Polygon",
        ConceptDataType.MEDIA to "Photo, video, or audio",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun collaboratorRole_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        CollaboratorRole.VIEWER to "Viewer",
        CollaboratorRole.DATA_COLLECTOR to "Data collector",
        CollaboratorRole.SURVEY_ORGANIZER to "Survey organizer",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun collaboratorRole_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        CollaboratorRole.VIEWER to "Can view the map, Map layers, and submissions.",
        CollaboratorRole.DATA_COLLECTOR to "Can collect submissions and add map features.",
        CollaboratorRole.SURVEY_ORGANIZER to "Can edit Forms, manage sharing, and export data.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun sharingPolicy_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SharingPolicy.RESTRICTED to "Restricted",
        SharingPolicy.ORGANIZATION to "Anyone in the organization",
        SharingPolicy.ANYONE_WITH_LINK to "Link or QR code required",
        SharingPolicy.PUBLIC to "Listed in the directory",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun sharingPolicy_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SharingPolicy.RESTRICTED to "Only people added above can open this survey.",
        SharingPolicy.ORGANIZATION to
          "Members of the survey's organization can collect data. Managers can also edit the survey.",
        SharingPolicy.ANYONE_WITH_LINK to
          "Users need a link or QR code to access this survey and collect data.",
        SharingPolicy.PUBLIC to
          "Listed in the directory so anyone can find this survey and collect data.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun datasetKind_singulars_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        DatasetKind.MAP_LAYER to "Map layer",
        DatasetKind.DATA_TABLE to "Data table",
      ),
      { it.singularRes },
    )
  }

  @Test
  fun datasetKind_plurals_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        DatasetKind.MAP_LAYER to "Map layers",
        DatasetKind.DATA_TABLE to "Data tables",
      ),
      { it.pluralRes },
    )
  }

  @Test
  fun geometryTypePlural_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        GeometryType.POINT to "Points",
        GeometryType.LINE_STRING to "Lines",
        GeometryType.POLYGON to "Polygons",
      ),
      { it.pluralRes },
    )
  }

  @Test
  fun propertyType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        PropertyType.TEXT to "Text",
        PropertyType.INTEGER to "Integer",
        PropertyType.DECIMAL to "Decimal",
        PropertyType.BOOLEAN to "Yes / No",
        PropertyType.DATE to "Date",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun sampleMethod_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SampleMethod.SYSTEMATIC_GRID to "Systematic grid",
        SampleMethod.SIMPLE_RANDOM to "Simple random",
        SampleMethod.STRATIFIED_RANDOM to "Stratified random",
        SampleMethod.CLUSTER to "Cluster",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun sampleMethod_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SampleMethod.SYSTEMATIC_GRID to "Plots on a regular grid with a random starting point.",
        SampleMethod.SIMPLE_RANDOM to "A set number of plots placed at random.",
        SampleMethod.STRATIFIED_RANDOM to "Plots placed at random within each stratum.",
        SampleMethod.CLUSTER to "Groups of plots placed at random.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun allocationMode_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        AllocationMode.EQUAL to "Equal",
        AllocationMode.PROPORTIONAL to "Proportional to area",
        AllocationMode.CUSTOM to "Custom",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun plotShapeOption_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        PlotShapeOption.SQUARE to "Square",
        PlotShapeOption.CIRCLE to "Circle",
        PlotShapeOption.POINT to "Point (center only)",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun subPlotMode_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        SubPlotMode.NONE to "None",
        SubPlotMode.CENTER to "One at the center",
        SubPlotMode.GRID to "Grid",
        SubPlotMode.RANDOM to "Random",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun editorQuestionType_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        EditorQuestionType.TEXT to "Short text",
        EditorQuestionType.LONG_TEXT to "Paragraph",
        EditorQuestionType.NUMBER to "Number",
        EditorQuestionType.SELECT_ONE to "Select one",
        EditorQuestionType.SELECT_MULTIPLE to "Select multiple",
        EditorQuestionType.DATE to "Date",
        EditorQuestionType.LOCATION to "Point",
        EditorQuestionType.LINE to "Line",
        EditorQuestionType.POLYGON to "Polygon",
        EditorQuestionType.PHOTO to "Photo",
        EditorQuestionType.VIDEO to "Video",
        EditorQuestionType.AUDIO to "Audio",
        EditorQuestionType.NOTE to "Note",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun geometryCapture_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        GeometryCapture.GPS_ONLY to "GPS only",
        GeometryCapture.GPS_OR_MAP to "GPS or draw on map",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun geometryCapture_descriptions_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        GeometryCapture.GPS_ONLY to
          "Collectors record their device's GPS position. Not available on web.",
        GeometryCapture.GPS_OR_MAP to
          "Collectors can use their GPS position or place it on the map. Required for web.",
      ),
      { it.descriptionRes },
    )
  }

  @Test
  fun relevanceOperator_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        RelevanceOperator.EQUALS to "equals",
        RelevanceOperator.NOT_EQUALS to "does not equal",
        RelevanceOperator.GREATER_THAN to "is greater than",
        RelevanceOperator.LESS_THAN to "is less than",
        RelevanceOperator.INCLUDES to "includes",
        RelevanceOperator.IS_ANSWERED to "is answered",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun choiceSource_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        ChoiceSource.MANUAL to "Manual list",
        ChoiceSource.MAP_LAYER to "Map layer",
        ChoiceSource.DATA_TABLE to "Data table",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun statusMarkerSymbol_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        StatusMarkerSymbol.OPEN to "Open circle",
        StatusMarkerSymbol.HALF to "Half circle",
        StatusMarkerSymbol.CHECK to "Checkmark",
        StatusMarkerSymbol.ALERT to "Exclamation",
        StatusMarkerSymbol.CROSS to "Cross",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun editorPropertyKind_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        EditorPropertyKind.TEXT to "Text",
        EditorPropertyKind.INTEGER to "Integer",
        EditorPropertyKind.DECIMAL to "Decimal",
        EditorPropertyKind.BOOLEAN to "Yes / no",
        EditorPropertyKind.DATE to "Date",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun dateRule_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        DateRule.NOT_IN_FUTURE to "Not in the future",
        DateRule.NOT_IN_PAST to "Not in the past",
        DateRule.BETWEEN to "Between two dates",
      ),
      { it.labelRes },
    )
  }

  @Test
  fun textPattern_labels_keepTheirEnglishText() {
    assertEnglish(
      mapOf(
        TextPattern.EMAIL to "Email address",
        TextPattern.DIGITS to "Digits only",
        TextPattern.LETTERS to "Letters only",
        TextPattern.CUSTOM to "Custom pattern",
      ),
      { it.labelRes },
    )
  }
}
