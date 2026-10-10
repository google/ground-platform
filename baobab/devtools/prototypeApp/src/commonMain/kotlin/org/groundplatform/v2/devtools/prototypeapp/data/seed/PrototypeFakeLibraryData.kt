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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptAggregation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactPillar
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryIds
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrivacyClass
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType

/**
 * Sample organization libraries: a few Kenya Forest Service concepts, a template, and a Purpose
 * Pack that combines it with the global EUDR template. The global library itself comes from
 * [GlobalLibrarySeedData].
 */
internal object PrototypeFakeLibraryData {
  private const val KFS = PrototypeFakeOrganizationsData.KENYA_FOREST_SERVICE

  private fun kfsId(name: String) = LibraryIds.organizationEntryId(KFS, name)

  fun defaultOrganizationLibraries(): List<OrganizationLibrary> = listOf(kenyaForestService())

  private fun kenyaForestService(): OrganizationLibrary {
    val cherryDelivery =
      LibraryConcept(
        id = kfsId("cherry_delivery_kg"),
        organizationId = KFS,
        label =
          LocalizedText.of("en" to "Cherry delivered (kg)", "sw" to "Cheri zilizowasilishwa (kg)"),
        dataType = ConceptDataType.DECIMAL,
        description = LocalizedText.en("Coffee cherry delivered to the washing station, in kg."),
        keywords = listOf(LocalizedText.en("coffee cherry"), LocalizedText.en("delivery")),
        unit = "kg",
        aggregation = ConceptAggregation.SUM,
        privacyClass = PrivacyClass.ORG_ONLY,
        status = LibraryStatus.STABLE,
      )
    val shadeTrees =
      LibraryConcept(
        id = kfsId("shade_tree_count"),
        organizationId = KFS,
        label = LocalizedText.en("Shade trees"),
        dataType = ConceptDataType.INTEGER,
        description = LocalizedText.en("Number of shade trees standing in the coffee parcel."),
        keywords = listOf(LocalizedText.en("canopy trees"), LocalizedText.en("agroforestry")),
        aggregation = ConceptAggregation.SUM,
        privacyClass = PrivacyClass.AGGREGATE_PUBLIC,
        goals = listOf("ecosystem_restoration"),
        pillar = ImpactPillar.ADAPTATION,
        status = LibraryStatus.DRAFT,
      )
    val audit =
      FormTemplate(
        id = kfsId("coop_member_plot_audit"),
        organizationId = KFS,
        title = LocalizedText.en("Coop member plot audit"),
        description = LocalizedText.en("Annual check of a cooperative member's coffee parcel."),
        form =
          EditorForm(
            formId = "coop_member_plot_audit",
            title = "Coop member plot audit",
            questions =
              listOf(
                EditorQuestion(
                  key = "cherry_delivery_kg",
                  name = "cherry_delivery_kg",
                  type = EditorQuestionType.DECIMAL,
                  label = "Cherry delivered (kg)",
                  conceptLink = ConceptLink.to(cherryDelivery),
                ),
                EditorQuestion(
                  key = "shade_tree_count",
                  name = "shade_tree_count",
                  type = EditorQuestionType.INTEGER,
                  label = "Shade trees",
                  conceptLink = ConceptLink.to(shadeTrees),
                ),
              ),
          ),
        status = LibraryStatus.STABLE,
      )
    return OrganizationLibrary(
      organizationId = KFS,
      concepts = listOf(cherryDelivery, shadeTrees),
      formTemplates = listOf(audit),
      purposePacks =
        listOf(
          PurposePack(
            id = kfsId("coop_certification_audit"),
            organizationId = KFS,
            title = LocalizedText.en("Coop annual certification audit"),
            description =
              LocalizedText.en("EUDR plot registration plus the cooperative's own plot audit."),
            icon = "fact_check",
            formTemplateIds = listOf("eudr_plot_registration", audit.id),
            exportProfileIds = listOf("eudr_geojson"),
            status = LibraryStatus.STABLE,
          )
        ),
    )
  }
}
