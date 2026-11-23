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
package org.openforis.collectone.v2.app.domain.model

import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType

/**
 * Text in one or more languages, keyed by BCP-47 language tag (`"en"`, `"fr"`, `"sw"`, …). Mirrors
 * `openforis.collectone.v1.library.LocalizedText`.
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
enum class LibraryStatus {
  DRAFT,
  STABLE,
  DEPRECATED,
}

/** How dashboards roll up answers to a [LibraryConcept]. */
enum class ConceptAggregation {
  COUNT_DISTINCT_FEATURES,
  COUNT_BY_CODE,
  SUM,
  MEAN,
  SHARE_BY_CODE,
  NONE,
}

/** What may leave the owning organization and what is suppressed publicly. */
enum class PrivacyClass {
  AGGREGATE_PUBLIC,
  ORG_ONLY,
  SENSITIVE,
}

/** Impact pillar used to group goals in dashboards. */
enum class ImpactPillar {
  MITIGATION,
  ADAPTATION,
  PROTECTION,
}

/**
 * Suggested answer type of a [LibraryConcept] (`ConceptDef.data_type`), with the Form editor
 * question types it can be linked to.
 */
enum class ConceptDataType(val compatibleQuestionTypes: Set<EditorQuestionType>) {
  TEXT(setOf(EditorQuestionType.TEXT, EditorQuestionType.LONG_TEXT)),
  INTEGER(setOf(EditorQuestionType.NUMBER)),
  /** Whole-number answers are valid decimals, so number questions can link to decimal concepts. */
  DECIMAL(setOf(EditorQuestionType.NUMBER)),
  DATE(setOf(EditorQuestionType.DATE)),
  SELECT_ONE(setOf(EditorQuestionType.SELECT_ONE)),
  SELECT_MULTIPLE(setOf(EditorQuestionType.SELECT_MULTIPLE)),
  POINT(setOf(EditorQuestionType.LOCATION)),
  LINE(setOf(EditorQuestionType.LINE)),
  POLYGON(setOf(EditorQuestionType.POLYGON)),
  MEDIA(setOf(EditorQuestionType.PHOTO, EditorQuestionType.VIDEO, EditorQuestionType.AUDIO));

  /** Whether a question of [type] can be linked to a concept of this data type. */
  fun isCompatibleWith(type: EditorQuestionType): Boolean = type in compatibleQuestionTypes

  /**
   * Whether [question] can be linked to a concept of this data type; for [INTEGER], requires a
   * [EditorQuestionType.NUMBER] question with `0` decimal places.
   */
  fun isCompatibleWith(question: EditorQuestion): Boolean =
    when (this) {
      INTEGER -> question.type == EditorQuestionType.NUMBER && question.decimalPlaces <= 0
      else -> isCompatibleWith(question.type)
    }

  /** Whether concepts of this type carry a code list. */
  val hasCodeList: Boolean
    get() = this == SELECT_ONE || this == SELECT_MULTIPLE
}

/**
 * A link from a form question or entity property to a [LibraryConcept]
 * (`openforis.collectone.v1.library.ConceptRef`): the concept ID and the version it was linked
 * against.
 */
data class ConceptLink(val conceptId: String, val version: Int = 1) {
  /**
   * The `collectone:concept` attribute value, `<conceptId>@<version>` (e.g. `eudr.commodity@1`).
   */
  val encoded: String
    get() = "$conceptId@$version"

  companion object {
    /** A link to [concept]'s current version. */
    fun to(concept: LibraryConcept): ConceptLink = ConceptLink(concept.id, concept.version)

    /**
     * Parses a `collectone:concept` value (`<conceptId>@<version>`, or a bare ID meaning version
     * 1). Returns `null` for blank values, malformed versions, or IDs with whitespace.
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
 * `openforis.collectone.v1.library.ConceptDef`; see `docs/technical/model/library/01-concepts.md`.
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
 * A reusable Form in an organization's library (`openforis.collectone.v1.library.FormTemplateDef`).
 * Adding it to a survey copies [form]; later template edits don't change existing surveys. Its
 * questions' concept links ([EditorQuestion.conceptLink]) travel in the template's `FormDef` as
 * `collectone:concept` bind attributes.
 */
data class FormTemplate(
  val id: String,
  val organizationId: String,
  val title: LocalizedText,
  val form: EditorForm,
  val description: LocalizedText = LocalizedText(),
  val status: LibraryStatus = LibraryStatus.DRAFT,
  /**
   * Version of the template (`FormTemplateDef.version`), recorded on the Forms copied from it (see
   * [org.openforis.collectone.v2.app.domain.model.editor.TemplateOrigin]).
   */
  val version: Int = 1,
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
 * (`openforis.collectone.v1.library.PurposePackDef`): templates added to the new survey, export
 * profiles enabled by default, and goal mapping.
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
  /**
   * Starter templates offered after picking this purpose (e.g., EUDR or UK FRC). When empty, the
   * pack's own [formTemplateIds] and [exportProfileIds] are its only template; see
   * [effectiveStarterTemplates].
   */
  val starterTemplates: List<StarterTemplate> = emptyList(),
) {
  val isGlobal: Boolean
    get() = LibraryIds.isGlobalId(id)

  /**
   * The starter templates to choose from: [starterTemplates], or a single default one (ID `""`)
   * built from the pack's own templates and export profiles, presetting the pack's program when it
   * offers just one.
   */
  val effectiveStarterTemplates: List<StarterTemplate>
    get() = starterTemplates.ifEmpty {
      listOf(
        StarterTemplate(
          id = DEFAULT_STARTER_TEMPLATE_ID,
          title = title,
          description = description,
          formTemplateIds = formTemplateIds,
          exportProfileIds = exportProfileIds,
          programIds = listOfNotNull(programIds.singleOrNull()),
        )
      )
    }

  /** The starter template chosen when the purpose is picked: the first one. */
  val defaultStarterTemplate: StarterTemplate
    get() = effectiveStarterTemplates.first()

  /** Starter template [id], falling back to [defaultStarterTemplate] for unknown or blank IDs. */
  fun starterTemplate(id: String): StarterTemplate =
    effectiveStarterTemplates.firstOrNull { it.id == id } ?: defaultStarterTemplate

  /** Templates a survey using starter template [starterTemplateId] starts with. */
  fun formTemplateIdsFor(starterTemplateId: String): List<String> =
    starterTemplate(starterTemplateId).formTemplateIds

  /**
   * Export profiles enabled for a survey whose Forms were copied from [usedTemplateIds]: those of
   * every starter template whose Forms the survey uses, or the default template's when it uses none
   * (for example, Forms built from scratch).
   */
  fun exportProfileIdsFor(usedTemplateIds: Set<String>): List<String> =
    effectiveStarterTemplates
      .filter { starter -> starter.formTemplateIds.any { it in usedTemplateIds } }
      .ifEmpty { listOf(defaultStarterTemplate) }
      .flatMap { it.exportProfileIds }
      .distinct()

  companion object {
    /** ID of the default starter template of a pack without [starterTemplates]. */
    const val DEFAULT_STARTER_TEMPLATE_ID = ""
  }
}

/**
 * One starter template of a [PurposePack] (`openforis.collectone.v1.library.StarterTemplateDef`):
 * the forms, export profiles, and preset programs of one way of meeting the purpose (e.g., EUDR).
 */
data class StarterTemplate(
  /** Unique within the pack; stored as the survey's `starter_template_id`. */
  val id: String,
  val title: LocalizedText,
  val description: LocalizedText = LocalizedText(),
  val formTemplateIds: List<String> = emptyList(),
  val exportProfileIds: List<String> = emptyList(),
  /** Programs (of the pack's) preselected when this template is chosen. */
  val programIds: List<String> = emptyList(),
)

/**
 * Reporting programs, regulations, and certification frameworks a survey can report to
 * (`SurveyDef.program_ids`), offered from its Purpose Packs' [PurposePack.programIds].
 */
object LibraryPrograms {
  private val LABELS =
    mapOf(
      "eudr" to "EU Deforestation Regulation (EUDR)",
      "uk_frc" to "UK Forest Risk Commodities",
      "rainforest_alliance" to "Rainforest Alliance",
      "fairtrade" to "Fairtrade",
      "organic" to "Organic / IFOAM",
      "agstack" to "AgStack Asset Registry",
      "ferm" to "Framework for Ecosystem Restoration Monitoring (FERM)",
      "gbf_target_2" to "Kunming-Montreal GBF Target 2 (Restoration)",
      "bonn_challenge" to "Bonn Challenge / AFR100",
      "gd_pame" to "Global Database on Protected Area Management Effectiveness (GD-PAME)",
      "mett" to "Management Effectiveness Tracking Tool (METT-4)",
      "imet" to "Integrated Management Effectiveness Tool (IMET)",
      "smart" to "Spatial Monitoring and Reporting Tool (SMART)",
      "gbf_target_3" to "Kunming-Montreal GBF Target 3 (30x30)",
      "pame" to "Protected area management effectiveness",
      "landmark" to "LandMark Indigenous & Community Lands",
      "icca" to "ICCA Registry (Territories of Life)",
      "vggt" to "FAO Voluntary Guidelines on Responsible Governance of Tenure (VGGT)",
      "fdap" to "Forest Data Partnership (FDaP)",
      "fao_lccs" to "FAO Land Cover Classification System (LCCS)",
      "fao_fra" to "FAO Global Forest Resources Assessment (FRA)",
      "open_foris" to "Open Foris (Collect Earth / Arena)",
      "redd_plus" to "REDD+ (UNFCCC)",
      "tfff" to "Tropical Forest Forever Facility (TFFF)",
      "art_trees" to "ART-TREES",
      "vcs" to "Verra Verified Carbon Standard (VCS)",
      "fsc" to "Forest Stewardship Council (FSC)",
      "pefc" to "Programme for the Endorsement of Forest Certification (PEFC)",
      "flegt" to "EU FLEGT / VPA",
    )

  private val DESCRIPTIONS =
    mapOf(
      "eudr" to
        "EU regulation requiring operators to prove cattle, cocoa, coffee, oil palm, rubber, " +
          "soya, and wood were produced on land not deforested after 31 December 2020, with " +
          "plot geolocation in their due diligence statements.",
      "uk_frc" to
        "UK Environment Act rules barring large businesses from using forest-risk commodities " +
          "grown on illegally deforested land, backed by annual due diligence reports.",
      "rainforest_alliance" to
        "Sustainable agriculture certification covering farm practices, deforestation, worker " +
          "rights, and farm-level traceability.",
      "fairtrade" to
        "Certification guaranteeing minimum prices and premiums to producer organizations, " +
          "with environmental and deforestation-free requirements.",
      "organic" to
        "Organic production standards (IFOAM family) verified through farm inspections and " +
          "internal control systems for smallholder groups.",
      "agstack" to
        "Linux Foundation open registry that assigns field boundaries a unique, anonymous GeoID " +
          "so plots can be referenced across systems.",
      "ferm" to
        "FAO's platform for reporting restoration areas and progress to the UN Decade on " +
          "Ecosystem Restoration and GBF Target 2.",
      "gbf_target_2" to
        "Global Biodiversity Framework goal to have at least 30% of degraded ecosystems under " +
          "effective restoration by 2030 (FAO is a custodian agency).",
      "bonn_challenge" to
        "Global goal to bring 350 million hectares of degraded land into restoration, with " +
          "regional pledges such as AFR100.",
      "gd_pame" to
        "UNEP-WCMC database of protected area management effectiveness assessments, used to " +
          "report on protected area quality.",
      "mett" to
        "Questionnaire-based tool (version 4) for assessing how well a protected area is " +
          "managed against its objectives.",
      "imet" to
        "Integrated tool for planning and evaluating protected area management, widely used in " +
          "Africa, the Caribbean, and the Pacific.",
      "smart" to
        "Ranger patrol data collection and reporting system for monitoring threats and law " +
          "enforcement in conservation areas.",
      "gbf_target_3" to
        "Global Biodiversity Framework goal to conserve at least 30% of land and sea in " +
          "protected and conserved areas by 2030 (30x30).",
      "pame" to
        "Generic protected area management effectiveness assessment, for methods other than " +
          "METT or IMET.",
      "landmark" to
        "Global platform mapping lands held or used by Indigenous Peoples and local " +
          "communities, including tenure and recognition status.",
      "icca" to
        "UNEP-WCMC registry of territories and areas conserved by Indigenous Peoples and local " +
          "communities.",
      "vggt" to
        "FAO guidelines on responsible governance of land, fisheries, and forest tenure, " +
          "including recognition of customary rights.",
      "fdap" to
        "Forest Data Partnership: open commodity and forest maps trained and validated with " +
          "shared ground-truth data.",
      "fao_lccs" to
        "FAO's standard for describing land cover classes consistently across maps and " +
          "countries.",
      "fao_fra" to
        "FAO's five-yearly global assessment of forest area, condition, and management, " +
          "including its remote sensing survey.",
      "open_foris" to
        "FAO's open-source tools for forest and land monitoring, including Collect Earth, " +
          "Collect Earth Online, and Arena.",
      "redd_plus" to
        "UNFCCC framework for results-based payments for reducing emissions from deforestation " +
          "and forest degradation, reported through FREL/FRL and BTR submissions.",
      "tfff" to
        "Tropical Forest Forever Facility: fund paying tropical forest countries per " +
          "hectare of standing forest, with deductions for deforestation and degradation.",
      "art_trees" to
        "Architecture for REDD+ Transactions' TREES standard for crediting jurisdictional " +
          "(national or subnational) emission reductions and removals, using IPCC activity " +
          "data × emission factors with Monte Carlo uncertainty.",
      "vcs" to
        "Verra's Verified Carbon Standard for project-level and jurisdictional (JNR) carbon " +
          "credits.",
      "fsc" to
        "Forest Stewardship Council certification for responsible forest management and " +
          "chain of custody.",
      "pefc" to
        "Programme for the Endorsement of Forest Certification: an umbrella for national " +
          "sustainable forest management and chain-of-custody schemes.",
      "flegt" to
        "EU Forest Law Enforcement, Governance and Trade action plan and Voluntary Partnership " +
          "Agreements for verifying timber legality.",
    )

  /** Display name of program [id], or [id] itself for programs without one. */
  fun label(id: String): String = LABELS[id] ?: id

  /** One-sentence description of program [id], or `""` for programs without one. */
  fun description(id: String): String = DESCRIPTIONS[id].orEmpty()

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
 * A named export with a fixed output schema (`openforis.collectone.v1.library.ExportProfileDef`).
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
 * Per-organization library preferences
 * (`openforis.collectone.v1.library.OrganizationLibrarySettings`).
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
   * This library with each template's version increased when its title, description, or form
   * differs from the same template in [previous] (versions never go back). See `TemplateVersioning`
   * for the server-side equivalent.
   */
  fun withTemplateVersionsFrom(previous: OrganizationLibrary): OrganizationLibrary {
    val before = previous.formTemplates.associateBy { it.id }
    return copy(
      formTemplates =
        formTemplates.map { template ->
          val old = before[template.id] ?: return@map template
          val unchanged = template.copy(version = old.version, status = old.status) == old
          template.copy(
            version =
              if (unchanged) maxOf(template.version, old.version)
              else maxOf(template.version, old.version + 1)
          )
        }
    )
  }

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
