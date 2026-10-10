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
package org.groundplatform.v2.devtools.prototypeapp.domain.model

import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType

/**
 * Text in one or more languages, keyed by BCP-47 language tag (`"en"`, `"fr"`, `"sw"`, …). Mirrors
 * `groundplatform.v2.library.LocalizedText`.
 */
data class LocalizedText(val values: Map<String, String> = emptyMap()) {
  /**
   * Text in [language], falling back to its base language (`"es"` for `"es-MX"`), then
   * [DEFAULT_LANGUAGE], then any language, then `""`.
   */
  fun get(language: String = DEFAULT_LANGUAGE): String =
    values[language]?.takeIf { it.isNotBlank() }
      ?: values[language.substringBefore('-')]?.takeIf { it.isNotBlank() }
      ?: values[DEFAULT_LANGUAGE]?.takeIf { it.isNotBlank() }
      ?: values.values.firstOrNull { it.isNotBlank() }
      ?: ""

  /** Text in [DEFAULT_LANGUAGE] (or the first available language). */
  val text: String
    get() = get(DEFAULT_LANGUAGE)

  val isBlank: Boolean
    get() = values.values.all { it.isBlank() }

  /** A copy with [language] set to [text]; a blank [text] removes the language. */
  fun with(language: String, text: String): LocalizedText =
    LocalizedText(if (text.isBlank()) values - language else values + (language to text))

  companion object {
    const val DEFAULT_LANGUAGE = "en"

    /** English-only text. */
    fun en(text: String): LocalizedText = LocalizedText(mapOf(DEFAULT_LANGUAGE to text))

    /** Text from `language to text` pairs, skipping blank ones. */
    fun of(vararg translations: Pair<String, String>): LocalizedText =
      LocalizedText(translations.filter { it.second.isNotBlank() }.toMap())
  }
}

/**
 * Lifecycle of a library entry. Deprecated entries are hidden from pickers and search, while
 * existing references keep working.
 */
enum class LibraryStatus(val label: String) {
  DRAFT("Draft"),
  STABLE("Stable"),
  DEPRECATED("Deprecated"),
}

/** How dashboards roll up answers to a [LibraryConcept]. */
enum class ConceptAggregation(val label: String, val description: String) {
  COUNT_DISTINCT_FEATURES("Count map features", "Counts map features with an answer."),
  COUNT_BY_CODE("Count by value", "Counts map features for each value in the list."),
  SUM("Sum", "Adds up the answers."),
  MEAN("Average", "Averages the answers."),
  SHARE_BY_CODE("Share by value", "Shows the share of map features with each value."),
  NONE("Not aggregated", "Names, identifiers, and other answers that aren't rolled up."),
}

/** What may leave the owning organization and what is suppressed publicly. */
enum class PrivacyClass(val label: String, val description: String) {
  AGGREGATE_PUBLIC("Public totals", "Totals can appear in platform-wide and public dashboards."),
  ORG_ONLY("Organization only", "Totals are visible only within the owning organization."),
  SENSITIVE("Sensitive", "Never included in cross-organization or public totals."),
}

/** Impact pillar used to group goals in dashboards. */
enum class ImpactPillar(val label: String) {
  MITIGATION("Mitigation"),
  ADAPTATION("Adaptation"),
  PROTECTION("Protection"),
}

/**
 * Suggested answer type of a [LibraryConcept] (`ConceptDef.data_type`), with the Form editor
 * question types it can be linked to.
 */
enum class ConceptDataType(
  val label: String,
  val compatibleQuestionTypes: Set<EditorQuestionType>,
) {
  TEXT("Text", setOf(EditorQuestionType.TEXT, EditorQuestionType.LONG_TEXT)),
  INTEGER("Integer", setOf(EditorQuestionType.INTEGER)),
  /** Whole-number answers are valid decimals, so integer questions can link to decimal concepts. */
  DECIMAL("Decimal", setOf(EditorQuestionType.DECIMAL, EditorQuestionType.INTEGER)),
  DATE("Date", setOf(EditorQuestionType.DATE)),
  SELECT_ONE("Select one", setOf(EditorQuestionType.SELECT_ONE)),
  SELECT_MULTIPLE("Select multiple", setOf(EditorQuestionType.SELECT_MULTIPLE)),
  POINT("Point", setOf(EditorQuestionType.LOCATION)),
  LINE("Line", setOf(EditorQuestionType.LINE)),
  POLYGON("Polygon", setOf(EditorQuestionType.POLYGON)),
  MEDIA(
    "Photo, video, or audio",
    setOf(EditorQuestionType.PHOTO, EditorQuestionType.VIDEO, EditorQuestionType.AUDIO),
  );

  /** Whether a question of [type] can be linked to a concept of this data type. */
  fun isCompatibleWith(type: EditorQuestionType): Boolean = type in compatibleQuestionTypes

  /** Whether concepts of this type carry a code list. */
  val hasCodeList: Boolean
    get() = this == SELECT_ONE || this == SELECT_MULTIPLE
}

/**
 * A link from a form question or entity property to a [LibraryConcept]
 * (`groundplatform.v2.library.ConceptRef`): the concept ID and the version it was linked against.
 */
data class ConceptLink(val conceptId: String, val version: Int = 1) {
  /** The `ground:concept` attribute value, `<conceptId>@<version>` (e.g. `eudr.commodity@1`). */
  val encoded: String
    get() = "$conceptId@$version"

  companion object {
    /** A link to [concept]'s current version. */
    fun to(concept: LibraryConcept): ConceptLink = ConceptLink(concept.id, concept.version)

    /**
     * Parses a `ground:concept` value (`<conceptId>@<version>`, or a bare ID meaning version 1).
     * Returns `null` for blank values, malformed versions, or IDs with whitespace.
     */
    fun parse(value: String): ConceptLink? {
      val trimmed = value.trim()
      val id = trimmed.substringBefore('@')
      if (id.isEmpty() || id.any { it.isWhitespace() }) return null
      val version =
        if ('@' in trimmed)
          trimmed.substringAfter('@').toIntOrNull()?.takeIf { it > 0 } ?: return null
        else 1
      return ConceptLink(id, version)
    }
  }
}

/** One allowed value of a select [LibraryConcept]. */
data class CodeListItem(
  /** Language-independent value (e.g. `"coffee"`). */
  val code: String,
  val label: LocalizedText,
  /** External identifiers keyed by scheme (e.g. `"hs"` to `"0901"`). */
  val externalIds: Map<String, String> = emptyMap(),
)

/**
 * A concept (dictionary entry): a standard field definition in an organization's library. Mirrors
 * `groundplatform.v2.library.ConceptDef`; see `docs/technical/model/library/01-concepts.md`.
 *
 * Global concepts (owned by `"All users"`) use `<vocabulary>.<name>` IDs; organization concepts use
 * `org.<organizationId>.<name>` (see [LibraryIds]).
 */
data class LibraryConcept(
  val id: String,
  val organizationId: String,
  val label: LocalizedText,
  val dataType: ConceptDataType,
  val version: Int = 1,
  val description: LocalizedText = LocalizedText(),
  /** Localized synonyms used by search. */
  val keywords: List<LocalizedText> = emptyList(),
  /** UCUM unit code (e.g. `"ha"`, `"m3"`, `"kg"`), or blank. */
  val unit: String = "",
  val codeList: List<CodeListItem> = emptyList(),
  val aggregation: ConceptAggregation = ConceptAggregation.NONE,
  val privacyClass: PrivacyClass = PrivacyClass.ORG_ONLY,
  /**
   * Goal IDs (e.g. `"deforestation_free_supply_chains"`); suggestions for organization concepts.
   */
  val goals: List<String> = emptyList(),
  val pillar: ImpactPillar? = null,
  /** Source framework references (e.g. regulation URLs). */
  val references: List<String> = emptyList(),
  val status: LibraryStatus = LibraryStatus.DRAFT,
  /** Previous organization concept IDs that now resolve to this (promoted) concept. */
  val aliases: List<String> = emptyList(),
) {
  val isGlobal: Boolean
    get() = LibraryIds.isGlobalId(id)

  val isDeprecated: Boolean
    get() = status == LibraryStatus.DEPRECATED
}

/**
 * A reusable Form in an organization's library (`groundplatform.v2.library.FormTemplateDef`).
 * Adding it to a survey copies [form]; later template edits don't change existing surveys. Its
 * questions' concept links ([EditorQuestion.conceptLink]) travel in the template's `FormDef` as
 * `ground:concept` bind attributes.
 */
data class FormTemplate(
  val id: String,
  val organizationId: String,
  val title: LocalizedText,
  val form: EditorForm,
  val description: LocalizedText = LocalizedText(),
  val status: LibraryStatus = LibraryStatus.DRAFT,
) {
  val isGlobal: Boolean
    get() = LibraryIds.isGlobalId(id)

  /** IDs of every concept the template's questions link to. */
  val conceptIds: Set<String>
    get() = form.questions.mapNotNull { it.conceptLink?.conceptId }.toSet()

  /** Number of questions linked to a concept. */
  val linkedQuestionCount: Int
    get() = form.questions.count { it.conceptLink != null }
}

/**
 * A choice in the survey-creation step "What will this data be used for?"
 * (`groundplatform.v2.library.PurposePackDef`): templates added to the new survey, export profiles
 * enabled by default, and goal mapping.
 */
data class PurposePack(
  val id: String,
  val organizationId: String,
  val title: LocalizedText,
  val description: LocalizedText = LocalizedText(),
  /** Material icon name shown in the picker. */
  val icon: String = "",
  val formTemplateIds: List<String> = emptyList(),
  val exportProfileIds: List<String> = emptyList(),
  val programIds: List<String> = emptyList(),
  val goals: List<String> = emptyList(),
  val pillar: ImpactPillar? = null,
  val status: LibraryStatus = LibraryStatus.DRAFT,
) {
  val isGlobal: Boolean
    get() = LibraryIds.isGlobalId(id)
}

/**
 * Programs a survey can report to (`SurveyDef.program_ids`), offered from its Purpose Packs'
 * [PurposePack.programIds].
 */
object LibraryPrograms {
  private val LABELS =
    mapOf(
      "eudr" to "EU Deforestation Regulation (EUDR)",
      "uk_frc" to "UK Forest Risk Commodities",
      "ferm" to "Framework for Ecosystem Restoration Monitoring (FERM)",
      "pame" to "Protected area management effectiveness",
      "redd_plus" to "REDD+",
      "tfff" to "Tropical Forest Forever Facility (TFFF)",
    )

  /** Display name of program [id], or [id] itself for programs without one. */
  fun label(id: String): String = LABELS[id] ?: id

  /**
   * Programs offered for [packs] (in order, without duplicates), followed by any of [selected] they
   * don't offer, so programs chosen earlier stay visible.
   */
  fun offeredFor(packs: List<PurposePack>, selected: List<String> = emptyList()): List<String> {
    val offered = packs.flatMap { it.programIds }.distinct()
    return offered + selected.filterNot { it in offered }
  }
}

/**
 * A named export with a fixed output schema (`groundplatform.v2.library.ExportProfileDef`).
 * Questions are located through their concept links, so a profile works across differently worded
 * Forms.
 */
data class ExportProfile(
  val id: String,
  val organizationId: String,
  val title: LocalizedText,
  /** Output format (`"geojson"`, `"csv"`, …). */
  val format: String,
  /** Output field name to concept ID. */
  val fieldConcepts: Map<String, String> = emptyMap(),
  val status: LibraryStatus = LibraryStatus.DRAFT,
)

/**
 * Per-organization library preferences (`groundplatform.v2.library.OrganizationLibrarySettings`).
 */
data class OrganizationLibrarySettings(
  /**
   * Global template and Purpose Pack IDs hidden from the organization's pickers. Global concepts
   * can't be hidden.
   */
  val hiddenGlobalEntryIds: Set<String> = emptySet()
)

/**
 * Everything one organization's library holds. The `"All users"` organization's library is the
 * global library.
 *
 * Stored per organization (`organizations/{organizationId}/concepts`, `/form_templates`,
 * `/purpose_packs`, `/export_profiles`) rather than inline on [Organization].
 */
data class OrganizationLibrary(
  val organizationId: String,
  val concepts: List<LibraryConcept> = emptyList(),
  val formTemplates: List<FormTemplate> = emptyList(),
  val purposePacks: List<PurposePack> = emptyList(),
  val exportProfiles: List<ExportProfile> = emptyList(),
  val settings: OrganizationLibrarySettings = OrganizationLibrarySettings(),
) {
  /** Whether this is the global library owned by `"All users"`. */
  val isGlobal: Boolean
    get() = LibraryIds.isGlobalLibrary(organizationId)

  fun concept(id: String): LibraryConcept? = concepts.firstOrNull { it.id == id }

  fun formTemplate(id: String): FormTemplate? = formTemplates.firstOrNull { it.id == id }

  fun purposePack(id: String): PurposePack? = purposePacks.firstOrNull { it.id == id }

  fun exportProfile(id: String): ExportProfile? = exportProfiles.firstOrNull { it.id == id }

  /**
   * Why this library's entries break the ownership rules, or `null` when they don't: every entry
   * must be owned by [organizationId] and carry an ID valid for this library (no shadowing), IDs
   * must be unique per kind, and template and Purpose Pack IDs must not collide (they share
   * [OrganizationLibrarySettings.hiddenGlobalEntryIds]).
   */
  fun integrityError(): String? {
    val entries =
      concepts.map { Triple("concept", it.id, it.organizationId) } +
        formTemplates.map { Triple("template", it.id, it.organizationId) } +
        purposePacks.map { Triple("purpose", it.id, it.organizationId) } +
        exportProfiles.map { Triple("export profile", it.id, it.organizationId) }
    for ((kind, id, owner) in entries) {
      if (owner != organizationId) return "The $kind \"$id\" belongs to another organization."
      val error =
        if (kind == "concept") LibraryIds.conceptIdError(id, organizationId)
        else LibraryIds.entryIdError(id, organizationId)
      if (error != null) return error
    }
    listOf(
        concepts.map { it.id },
        formTemplates.map { it.id } + purposePacks.map { it.id },
        exportProfiles.map { it.id },
      )
      .forEach { ids ->
        ids
          .groupBy { it }
          .entries
          .firstOrNull { it.value.size > 1 }
          ?.let {
            return "The ID \"${it.key}\" is used more than once."
          }
      }
    return null
  }
}

/**
 * ID rules of organization libraries (see `docs/technical/model/library/01-concepts.md`, "IDs and
 * Versioning"):
 * - Global concepts: `<vocabulary>.<name>` (e.g. `eudr.commodity`).
 * - Global templates, Purpose Packs, and export profiles: `<name>` (e.g. `eudr_due_diligence`).
 * - Organization entries of every kind: `org.<organizationId>.<name>`, so organizations can never
 *   redefine a global ID (no shadowing) and every reference names its owner.
 *
 * Names and vocabularies are lowercase snake case (`[a-z][a-z0-9_]*`); `org` is reserved.
 */
object LibraryIds {
  /** Prefix of every organization entry ID. */
  const val ORGANIZATION_PREFIX = "org."

  private val NAME = Regex("^[a-z][a-z0-9_]*$")

  /** Whether [name] is a valid entry name or vocabulary. */
  fun isValidName(name: String): Boolean = NAME.matches(name)

  /** Whether [organizationId] owns the global library. */
  fun isGlobalLibrary(organizationId: String?): Boolean =
    organizationId == Organization.ALL_USERS_ID

  /** Whether [id] is a global (not organization-prefixed) entry ID. */
  fun isGlobalId(id: String): Boolean = !id.startsWith(ORGANIZATION_PREFIX)

  /** `org.<organizationId>.<name>`. */
  fun organizationEntryId(organizationId: String, name: String): String =
    "$ORGANIZATION_PREFIX$organizationId.$name"

  /** `<vocabulary>.<name>`. */
  fun globalConceptId(vocabulary: String, name: String): String = "$vocabulary.$name"

  /** Owning organization ID of an organization entry ID, or `null` for a global ID. */
  fun organizationIdOf(id: String): String? {
    if (isGlobalId(id)) return null
    val rest = id.removePrefix(ORGANIZATION_PREFIX)
    return rest.substringBeforeLast('.', missingDelimiterValue = "").ifEmpty { null }
  }

  /** Name part of [id] (after the last `.`). */
  fun nameOf(id: String): String = id.substringAfterLast('.')

  /** Vocabulary of a global concept ID, or `null` for organization IDs. */
  fun vocabularyOf(id: String): String? = if (isGlobalId(id)) id.substringBefore('.') else null

  /** Whether [id] is a valid global concept ID (`<vocabulary>.<name>`). */
  fun isValidGlobalConceptId(id: String): Boolean {
    val parts = id.split('.')
    return parts.size == 2 && parts[0] != "org" && parts.all(::isValidName)
  }

  /** Whether [id] is a valid global template, Purpose Pack, or export profile ID (`<name>`). */
  fun isValidGlobalEntryId(id: String): Boolean = isValidName(id) && id != "org"

  /** Whether [id] is a valid entry ID of [organizationId] (`org.<organizationId>.<name>`). */
  fun isValidOrganizationEntryId(id: String, organizationId: String): Boolean {
    val prefix = "$ORGANIZATION_PREFIX$organizationId."
    return organizationId.isNotBlank() &&
      id.startsWith(prefix) &&
      isValidName(id.removePrefix(prefix))
  }

  /** Why [id] isn't a valid concept ID in [libraryOrganizationId]'s library, or `null`. */
  fun conceptIdError(id: String, libraryOrganizationId: String): String? =
    if (isGlobalLibrary(libraryOrganizationId)) {
      if (isValidGlobalConceptId(id)) null
      else "Global concept IDs look like vocabulary.name, for example eudr.commodity."
    } else {
      if (isValidOrganizationEntryId(id, libraryOrganizationId)) null
      else "Concept IDs in this organization start with org.$libraryOrganizationId."
    }

  /**
   * Why [id] isn't a valid template, Purpose Pack, or export profile ID in
   * [libraryOrganizationId]'s library, or `null`.
   */
  fun entryIdError(id: String, libraryOrganizationId: String): String? =
    if (isGlobalLibrary(libraryOrganizationId)) {
      if (isValidGlobalEntryId(id)) null
      else "Global IDs use lowercase letters, numbers, and underscores, for example eudr_plots."
    } else {
      if (isValidOrganizationEntryId(id, libraryOrganizationId)) null
      else "IDs in this organization start with org.$libraryOrganizationId."
    }

  /** A valid name derived from free text (`"Cherry delivery (kg)"` → `cherry_delivery_kg`). */
  fun nameFrom(text: String, fallback: String = "entry"): String {
    val slug =
      LibraryText.fold(text)
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .take(48)
        .trimEnd('_')
    return when {
      slug.isEmpty() -> fallback
      slug.first().isDigit() -> "n_$slug"
      else -> slug
    }
  }
}
