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
package org.openforis.collectone.v2.app.data.datasource.local

import openforis.collectone.v1.forms.DataType
import openforis.collectone.v1.library.Aggregation
import openforis.collectone.v1.library.CodeList
import openforis.collectone.v1.library.CodeListItem as CodeListItemProto
import openforis.collectone.v1.library.ConceptDef
import openforis.collectone.v1.library.ExportProfileDef
import openforis.collectone.v1.library.FormTemplateDef
import openforis.collectone.v1.library.LibraryBundle
import openforis.collectone.v1.library.LibraryStatus as LibraryStatusProto
import openforis.collectone.v1.library.LocalizedText as LocalizedTextProto
import openforis.collectone.v1.library.OrganizationLibrarySettings as OrganizationLibrarySettingsProto
import openforis.collectone.v1.library.Pillar
import openforis.collectone.v1.library.PrivacyClass as PrivacyClassProto
import openforis.collectone.v1.library.PurposePackDef
import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.ExportProfile
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrarySettings
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.EditorXFormsGenerator
import org.openforis.collectone.v2.app.domain.model.editor.FormImport

/**
 * Maps organization library domain models to and from their `openforis.collectone.v1.library`
 * protos (`LibraryBundle` and `OrganizationLibrarySettings`), for seed files, imports, exports, and
 * sync.
 *
 * Template Forms go through [EditorXFormsGenerator.compile] and [FormImport], so their questions'
 * concept links round-trip as `ground:concept` bind attributes.
 */
object LibraryProtoMapper {
  /**
   * [library]'s entries as a [LibraryBundle]. With [includeOrganizationId] false (seed files),
   * `organization_id` is left out, since a seed file's owner is implied.
   */
  fun toBundle(library: OrganizationLibrary, includeOrganizationId: Boolean = true): LibraryBundle {
    val owner = if (includeOrganizationId) library.organizationId else ""
    return LibraryBundle(
      concepts = library.concepts.map { toProto(it, owner) },
      form_templates = library.formTemplates.map { toProto(it, owner) },
      purpose_packs = library.purposePacks.map { toProto(it, owner) },
      export_profiles = library.exportProfiles.map { toProto(it, owner) },
    )
  }

  /**
   * [bundle] as the library of [organizationId]. Entries with a blank `organization_id` (seed
   * files) are assigned to [organizationId].
   */
  fun fromBundle(
    bundle: LibraryBundle,
    organizationId: String,
    settings: OrganizationLibrarySettings = OrganizationLibrarySettings(),
  ): OrganizationLibrary {
    fun owner(id: String) = id.ifBlank { organizationId }
    return OrganizationLibrary(
      organizationId = organizationId,
      concepts = bundle.concepts.map { fromProto(it, owner(it.organization_id)) },
      formTemplates = bundle.form_templates.map { fromProto(it, owner(it.organization_id)) },
      purposePacks = bundle.purpose_packs.map { fromProto(it, owner(it.organization_id)) },
      exportProfiles = bundle.export_profiles.map { fromProto(it, owner(it.organization_id)) },
      settings = settings,
    )
  }

  fun toProto(
    settings: OrganizationLibrarySettings,
    organizationId: String,
  ): OrganizationLibrarySettingsProto =
    OrganizationLibrarySettingsProto(
      organization_id = organizationId,
      hidden_global_entry_ids = settings.hiddenGlobalEntryIds.sorted(),
    )

  fun fromProto(settings: OrganizationLibrarySettingsProto): OrganizationLibrarySettings =
    OrganizationLibrarySettings(hiddenGlobalEntryIds = settings.hidden_global_entry_ids.toSet())

  // --- Concepts -------------------------------------------------------------------------------

  fun toProto(
    concept: LibraryConcept,
    organizationId: String = concept.organizationId,
  ): ConceptDef =
    ConceptDef(
      id = concept.id,
      version = concept.version,
      organization_id = organizationId,
      label = toProto(concept.label),
      description = toProtoOrNull(concept.description),
      keywords = concept.keywords.map(::toProto),
      data_type = toProto(concept.dataType),
      unit = concept.unit,
      code_list =
        concept.codeList
          .takeIf { it.isNotEmpty() }
          ?.let { items ->
            CodeList(
              items =
                items.map {
                  CodeListItemProto(
                    code = it.code,
                    label = toProto(it.label),
                    external_ids = it.externalIds,
                  )
                }
            )
          },
      aggregation = Aggregation.valueOf(concept.aggregation.name),
      privacy_class = PrivacyClassProto.valueOf(concept.privacyClass.name),
      goals = concept.goals,
      pillar = toProto(concept.pillar),
      references = concept.references,
      status = toProto(concept.status),
      aliases = concept.aliases,
    )

  fun fromProto(
    concept: ConceptDef,
    organizationId: String = concept.organization_id,
  ): LibraryConcept =
    LibraryConcept(
      id = concept.id,
      organizationId = organizationId,
      label = fromProto(concept.label),
      dataType = fromProto(concept.data_type),
      version = concept.version.coerceAtLeast(1),
      description = fromProto(concept.description),
      keywords = concept.keywords.map(::fromProto),
      unit = concept.unit,
      codeList =
        concept.code_list?.items.orEmpty().map {
          CodeListItem(code = it.code, label = fromProto(it.label), externalIds = it.external_ids)
        },
      aggregation =
        ConceptAggregation.entries.firstOrNull { it.name == concept.aggregation.name }
          ?: ConceptAggregation.NONE,
      privacyClass =
        PrivacyClass.entries.firstOrNull { it.name == concept.privacy_class.name }
          ?: PrivacyClass.ORG_ONLY,
      goals = concept.goals,
      pillar = fromProto(concept.pillar),
      references = concept.references,
      status = fromProto(concept.status),
      aliases = concept.aliases,
    )

  // --- Templates, packs, and profiles ---------------------------------------------------------

  fun toProto(
    template: FormTemplate,
    organizationId: String = template.organizationId,
  ): FormTemplateDef =
    FormTemplateDef(
      id = template.id,
      organization_id = organizationId,
      title = toProto(template.title),
      description = toProtoOrNull(template.description),
      form = EditorXFormsGenerator.compile(template.form).proto,
      status = toProto(template.status),
    )

  fun fromProto(
    template: FormTemplateDef,
    organizationId: String = template.organization_id,
  ): FormTemplate =
    FormTemplate(
      id = template.id,
      organizationId = organizationId,
      title = fromProto(template.title),
      form =
        FormImport.fromFormDef(
            template.form ?: openforis.collectone.v1.forms.FormDef(form_id = template.id),
            formId = template.id,
            fallbackTitle = fromProto(template.title).text,
          )
          .form,
      description = fromProto(template.description),
      status = fromProto(template.status),
    )

  fun toProto(pack: PurposePack, organizationId: String = pack.organizationId): PurposePackDef =
    PurposePackDef(
      id = pack.id,
      organization_id = organizationId,
      title = toProto(pack.title),
      description = toProtoOrNull(pack.description),
      icon = pack.icon,
      form_template_ids = pack.formTemplateIds,
      export_profile_ids = pack.exportProfileIds,
      program_ids = pack.programIds,
      goals = pack.goals,
      pillar = toProto(pack.pillar),
      status = toProto(pack.status),
    )

  fun fromProto(pack: PurposePackDef, organizationId: String = pack.organization_id): PurposePack =
    PurposePack(
      id = pack.id,
      organizationId = organizationId,
      title = fromProto(pack.title),
      description = fromProto(pack.description),
      icon = pack.icon,
      formTemplateIds = pack.form_template_ids,
      exportProfileIds = pack.export_profile_ids,
      programIds = pack.program_ids,
      goals = pack.goals,
      pillar = fromProto(pack.pillar),
      status = fromProto(pack.status),
    )

  fun toProto(
    profile: ExportProfile,
    organizationId: String = profile.organizationId,
  ): ExportProfileDef =
    ExportProfileDef(
      id = profile.id,
      organization_id = organizationId,
      title = toProto(profile.title),
      format = profile.format,
      field_concepts = profile.fieldConcepts,
      status = toProto(profile.status),
    )

  fun fromProto(
    profile: ExportProfileDef,
    organizationId: String = profile.organization_id,
  ): ExportProfile =
    ExportProfile(
      id = profile.id,
      organizationId = organizationId,
      title = fromProto(profile.title),
      format = profile.format,
      fieldConcepts = profile.field_concepts,
      status = fromProto(profile.status),
    )

  // --- Shared types ---------------------------------------------------------------------------

  private fun toProto(text: LocalizedText): LocalizedTextProto =
    LocalizedTextProto(values = text.values)

  private fun toProtoOrNull(text: LocalizedText): LocalizedTextProto? =
    text.takeUnless { it.isBlank }?.let(::toProto)

  private fun fromProto(text: LocalizedTextProto?): LocalizedText =
    LocalizedText(text?.values.orEmpty())

  private fun toProto(status: LibraryStatus): LibraryStatusProto =
    LibraryStatusProto.valueOf(status.name)

  private fun fromProto(status: LibraryStatusProto): LibraryStatus =
    LibraryStatus.entries.firstOrNull { it.name == status.name } ?: LibraryStatus.DRAFT

  private fun toProto(pillar: ImpactPillar?): Pillar =
    pillar?.let { Pillar.valueOf(it.name) } ?: Pillar.PILLAR_UNSPECIFIED

  private fun fromProto(pillar: Pillar): ImpactPillar? =
    ImpactPillar.entries.firstOrNull { it.name == pillar.name }

  private val DATA_TYPES =
    listOf(
      ConceptDataType.TEXT to DataType.TYPE_STRING,
      ConceptDataType.INTEGER to DataType.TYPE_INT32,
      ConceptDataType.DECIMAL to DataType.TYPE_DOUBLE,
      ConceptDataType.DATE to DataType.TYPE_DATE,
      ConceptDataType.SELECT_ONE to DataType.TYPE_SELECT_ONE,
      ConceptDataType.SELECT_MULTIPLE to DataType.TYPE_SELECT_MULTIPLE,
      ConceptDataType.POINT to DataType.TYPE_GEOPOINT,
      ConceptDataType.LINE to DataType.TYPE_GEOTRACE,
      ConceptDataType.POLYGON to DataType.TYPE_GEOSHAPE,
      ConceptDataType.MEDIA to DataType.TYPE_BINARY,
    )

  fun toProto(type: ConceptDataType): DataType = DATA_TYPES.first { it.first == type }.second

  /**
   * The concept type for [type]; 64-bit integers map to [ConceptDataType.INTEGER], others to text.
   */
  fun fromProto(type: DataType): ConceptDataType =
    DATA_TYPES.firstOrNull { it.second == type }?.first
      ?: if (type == DataType.TYPE_INT64) ConceptDataType.INTEGER else ConceptDataType.TEXT
}
