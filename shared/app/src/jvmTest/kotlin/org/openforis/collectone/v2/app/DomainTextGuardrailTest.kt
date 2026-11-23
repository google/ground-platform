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
package org.openforis.collectone.v2.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The domain holds no display text: a domain type doesn't expose a `String` property named like
 * display text (`label`, `message`, `description`, `reason`, `summary`, or `noun`). Text the user
 * sees comes from string resources, via an enum label mapper or a `DomainMessage` localized in
 * `ui/text`.
 *
 * Scans the classes (including enums, data classes, and nested classes) of `:shared:app:domain` and
 * `:shared:web:domain`. Properties that hold the user's own data (a survey's description, a
 * question's label, a choice's label, the nouns an author gives a layer's features) are in
 * [allowlist], keyed `ClassName.property`. The list may only shrink: the test also fails on entries
 * that no longer match.
 *
 * The domain also writes no unit symbols after a number (`"$meters m"`, `"4 ha"`): it returns a
 * `Measurement`, whose unit the UI names in the user's language. Exceptions are in
 * [unitTextAllowlist], keyed by file name and the matched text, and may only shrink too.
 */
class DomainTextGuardrailTest {
  private val domainSources =
    listOf("shared/app/domain/src/commonMain/kotlin", "shared/web/domain/src/commonMain/kotlin")
  private val displayTextName = Regex("""(?i)(label|message|description|reason|summary|noun)$""")

  /** User-authored or user-data properties, keyed by class name and property. */
  private val allowlist: Set<String> =
    setOf(
      // The user's own data: survey, form, dataset, and library content, and names from imports.
      "CollectEarthProjectImport.description",
      "ColumnPrefixGroup.wildcardLabel",
      "EditorChoice.label",
      "EditorDatasetProperty.label",
      "EditorDatasetRow.label",
      "EditorForm.ctaLabel",
      "EditorForm.effectiveCtaLabel",
      "EditorQuestion.label",
      "EditorStatusBadge.label",
      "EditorValidation.message",
      "EntityDataset.description",
      "EntityProperty.label",
      "FormPreviewItem.ctaLabel",
      "FormPreviewItem.description",
      "FormPreviewItem.targetTypeNoun",
      "GeospatialEntityItem.label",
      "GeospatialEntityItem.typeNoun",
      "ImpactBreakdownItem.label",
      "ImpactIndicator.label",
      "LinkedColumn.label",
      "MapClusterFeatureItem.label",
      "MapLayerItem.label",
      "MapLayerItem.pluralNoun",
      "MapLayerItem.singularNoun",
      "MutationLogItem.targetLabel",
      "Organization.description",
      "ProfileFieldMapping.label",
      "Single.groupLabel",
      "SubmissionFieldEntry.questionLabel",
      "SubmissionGeometryPolygon.questionLabel",
      "SubmissionPreviewItem.entityLabel",
      "SubmissionPreviewItem.targetTypeNoun",
      "SurveyDetails.description",
      "SurveyMapAnchor.label",
      "SurveyPreviewItem.description",
      // External Open Foris Whisp enrichment dataset/indicator names derived from whisp_* keys.
      "WhispBeforeAfterComparison.label",
      "WhispIndicator.label",
      "WhispMetric.label",
      "WhispRiskVerdict.label",
      "WhispYearlySeries.label",
      // A geocoder or gazetteer place's own category (typed coordinates are named by the UI).
      "SurveyPlaceItem.categoryLabel",
      // Language names, shown in their own language on purpose.
      "LanguageOption.label",
      // Formatted numbers, coordinates, and sizes rather than sentences.
      "DeviceLocationSnapshot.coordinatesLabel",
      "MediaCacheInfo.sizeLabel",
      // Not shown to users: diagnostics.
      "MutationLogItem.payloadSummary",
      "MutationOperationKind.protoOperationLabel",
      // Workbench device presets: developer tooling that isn't shipped, so it stays English.
      "DeviceFormFactor.dimensionsLabel",
      "DeviceFormFactor.label",
      "DeviceOrientation.label",
    )

  /** Text written with a unit symbol after a number, keyed `File.kt: matched text`. */
  private val unitTextAllowlist: Set<String> =
    setOf(
      // Export-plan warnings are English diagnostics no screen shows yet; type them before one
      // does.
      "ExportSurveyDataUseCase.kt: over 4 ha",
      // The simulated device location's text, which data collection also stores in records; derive
      // it from the position and a measured accuracy before localizing it.
      "LocationRepository.kt: 36.9512° (±3.2m",
    )

  /** A number or a string template value followed by a length or area unit symbol. */
  private val unitAfterNumber =
    Regex("""(\d|\$\{[^}"]*}|\$\w+)\s?(m|km|ft|mi|ha|acres?|km²|m²)(?![\p{L}\d²])""")

  /** Every string literal in the domain sources that writes a unit symbol after a number. */
  private fun unitTexts(): Set<String> =
    domainSources
      .flatMap { source -> Konsist.scopeFromDirectory(source).files }
      .flatMap { file ->
        Regex(""""(?:[^"\\\n]|\\.)*"""").findAll(file.text).flatMap { literal ->
          Regex("""(?:\S+\s)?\S*?""" + unitAfterNumber.pattern).findAll(literal.value).map {
            "${file.nameWithExtension}: ${it.value.trim()}"
          }
        }
      }
      .toSet()

  private fun KoClassDeclaration.stringProperties(): List<String> {
    val constructorProperties =
      primaryConstructor
        ?.parameters
        .orEmpty()
        .filter { it.hasValModifier || it.hasVarModifier }
        .filter { it.type.name.removeSuffix("?") == "String" }
        .map { it.name }
    val bodyProperties =
      properties(includeNested = false)
        .filter { it.type?.name?.removeSuffix("?") == "String" }
        .map { it.name }
    return (constructorProperties + bodyProperties).distinct()
  }

  private fun displayTextProperties(): Set<String> =
    domainSources
      .flatMap { Konsist.scopeFromDirectory(it).classes(includeNested = true) }
      .flatMap { declaration ->
        declaration
          .stringProperties()
          .filter { displayTextName.containsMatchIn(it) }
          .map { "${declaration.name}.$it" }
      }
      .toSet()

  @Test
  fun scopeFindsDomainClasses() {
    domainSources.forEach { source ->
      assertTrue(
        Konsist.scopeFromDirectory(source).classes().isNotEmpty(),
        "No classes found in $source",
      )
    }
  }

  @Test
  fun domainTypesExposeNoDisplayText() {
    val found = displayTextProperties()
    val unexpected = (found - allowlist).sorted()
    assertEquals(
      emptyList(),
      unexpected,
      "Domain types expose display text; return semantic data (an enum or a DomainMessage) and " +
        "localize it in ui/text instead:\n" +
        unexpected.joinToString("\n"),
    )
  }

  @Test
  fun allowlistHasNoStaleEntries() {
    val stale = (allowlist - displayTextProperties()).sorted()
    assertEquals(emptyList(), stale, "Remove these entries from the allowlist")
  }

  @Test
  fun domainWritesNoUnitSymbols() {
    val unexpected = (unitTexts() - unitTextAllowlist).sorted()
    assertEquals(
      emptyList(),
      unexpected,
      "The domain writes unit symbols; return a Measurement and name its unit in ui/text instead:\n" +
        unexpected.joinToString("\n"),
    )
  }

  @Test
  fun unitTextAllowlistHasNoStaleEntries() {
    val stale = (unitTextAllowlist - unitTexts()).sorted()
    assertEquals(emptyList(), stale, "Remove these entries from the unit text allowlist")
  }
}
