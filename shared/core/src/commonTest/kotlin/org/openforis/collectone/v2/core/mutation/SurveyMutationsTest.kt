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
package org.openforis.collectone.v2.core.mutation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import openforis.collectone.v1.data.Geometry
import openforis.collectone.v1.forms.ChoiceItem
import openforis.collectone.v1.forms.ControlDef
import openforis.collectone.v1.forms.ControlType
import openforis.collectone.v1.forms.FieldBinding
import openforis.collectone.v1.forms.FormDef
import openforis.collectone.v1.forms.GroupDef
import openforis.collectone.v1.forms.LabelDef
import openforis.collectone.v1.forms.LanguageTranslation
import openforis.collectone.v1.forms.LocalizedString
import openforis.collectone.v1.forms.ModelDef
import openforis.collectone.v1.forms.TranslationCatalog
import openforis.collectone.v1.forms.ViewComponent
import openforis.collectone.v1.forms.ViewDef
import openforis.collectone.v1.survey.AclEntry
import openforis.collectone.v1.survey.DataTableRow
import openforis.collectone.v1.survey.EntityDatasetDef
import openforis.collectone.v1.survey.ImportProvenance
import openforis.collectone.v1.survey.QuotaLimits
import openforis.collectone.v1.survey.QuotaTier
import openforis.collectone.v1.survey.Role
import openforis.collectone.v1.survey.SamplingDesign
import openforis.collectone.v1.survey.SurveyAcl
import openforis.collectone.v1.survey.SurveyDef

/** Merging sparse upserts onto survey parts that already exist (the recursive merge paths). */
class SurveyMutationsTest {

  private fun control(fieldRef: String, label: String, vararg choices: ChoiceItem) =
    ViewComponent(
      control =
        ControlDef(
          field_ref = fieldRef,
          type = ControlType.CONTROL_SELECT_ONE,
          label = LabelDef(text = label),
          choices = choices.toList(),
        )
    )

  private fun choice(value: String, label: String, sortOrder: Int = 0) =
    ChoiceItem(value_ = value, label = LabelDef(text = label), sort_order = sortOrder)

  @Test
  fun datasetUpsert_mergesRowsByKeyAndKeepsUnsetRowFields() {
    val geometry = Geometry()
    val base =
      SurveyDef(
        entity_datasets =
          listOf(
            EntityDatasetDef(
              id = "trees",
              display_name = "Trees",
              rows =
                listOf(
                  DataTableRow(
                    row_key = "r1",
                    values = mapOf("species" to "oak", "dbh" to "10"),
                    geometry = geometry,
                    sort_order = 2,
                  ),
                  DataTableRow(row_key = "r2", sort_order = 1),
                ),
            )
          )
      )
    val upsert =
      SurveyDef(
        entity_datasets =
          listOf(
            EntityDatasetDef(
              id = "trees",
              rows = listOf(DataTableRow(row_key = "r1", values = mapOf("dbh" to "12"))),
            )
          )
      )

    val dataset = SurveyMutations.apply(base, upsert = upsert).entity_datasets.single()

    assertEquals("Trees", dataset.display_name)
    assertEquals(listOf("r2", "r1"), dataset.rows.map { it.row_key })
    val r1 = dataset.rows.single { it.row_key == "r1" }
    assertEquals(mapOf("species" to "oak", "dbh" to "12"), r1.values)
    assertEquals(geometry, r1.geometry)
    assertEquals(2, r1.sort_order)
  }

  @Test
  fun datasetUpsert_samplingAndImportReplaceEachOther() {
    val imported =
      SurveyDef(entity_datasets = listOf(EntityDatasetDef(id = "plots"))).let {
        SurveyMutations.apply(
          it,
          upsert =
            SurveyDef(
              entity_datasets =
                listOf(
                  EntityDatasetDef(id = "plots", imported = ImportProvenance(file_name = "a.kml"))
                )
            ),
        )
      }

    val sampled =
      SurveyMutations.apply(
        imported,
        upsert =
          SurveyDef(
            entity_datasets = listOf(EntityDatasetDef(id = "plots", sampling = SamplingDesign()))
          ),
      )

    val dataset = sampled.entity_datasets.single()
    assertEquals(SamplingDesign(), dataset.sampling)
    assertNull(dataset.imported)
  }

  @Test
  fun formUpsert_mergesControlsByFieldRefAndChoicesByValue() {
    val base =
      SurveyDef(
        forms =
          listOf(
            FormDef(
              form_id = "f1",
              title = "Plot",
              view =
                ViewDef(
                  components =
                    listOf(
                      control("species", "Species", choice("oak", "Oak"), choice("pine", "Pine")),
                      ViewComponent(
                        group =
                          GroupDef(
                            field_ref = "site",
                            components = listOf(control("slope", "Slope")),
                          )
                      ),
                    )
                ),
            )
          )
      )
    val upsert =
      SurveyDef(
        forms =
          listOf(
            FormDef(
              form_id = "f1",
              view =
                ViewDef(
                  components =
                    listOf(
                      ViewComponent(
                        control =
                          ControlDef(
                            field_ref = "species",
                            choices = listOf(choice("pine", "Scots pine"), choice("fir", "Fir")),
                          )
                      ),
                      ViewComponent(
                        group =
                          GroupDef(
                            field_ref = "site",
                            components = listOf(control("aspect", "Aspect")),
                          )
                      ),
                    )
                ),
            )
          )
      )

    val form = SurveyMutations.apply(base, upsert = upsert).forms.single()

    assertEquals("Plot", form.title)
    val species = form.view!!.components[0].control!!
    // Unset fields of the upsert keep their stored values.
    assertEquals("Species", species.label?.text)
    assertEquals(ControlType.CONTROL_SELECT_ONE, species.type)
    assertEquals(
      listOf("oak" to "Oak", "pine" to "Scots pine", "fir" to "Fir"),
      species.choices.map { it.value_ to it.label?.text },
    )
    assertEquals(
      listOf("slope", "aspect"),
      form.view.components[1].group!!.components.map { it.control?.field_ref },
    )
  }

  @Test
  fun formUpsert_upsertsBindingsAndMergesTranslations() {
    val base =
      SurveyDef(
        forms =
          listOf(
            FormDef(
              form_id = "f1",
              model =
                ModelDef(
                  bindings =
                    listOf(
                      FieldBinding(field_path = "/data/a", required_expression = "true()"),
                      FieldBinding(field_path = "/data/b"),
                    ),
                  translations =
                    TranslationCatalog(
                      languages =
                        listOf(
                          LanguageTranslation(
                            language = "en",
                            is_default = true,
                            strings = mapOf("a" to LocalizedString(value_ = "A")),
                          )
                        )
                    ),
                ),
            )
          )
      )
    val upsert =
      SurveyDef(
        forms =
          listOf(
            FormDef(
              form_id = "f1",
              model =
                ModelDef(
                  bindings = listOf(FieldBinding(field_path = "/data/a", read_only = true)),
                  translations =
                    TranslationCatalog(
                      languages =
                        listOf(
                          LanguageTranslation(
                            language = "en",
                            strings = mapOf("b" to LocalizedString(value_ = "B")),
                          ),
                          LanguageTranslation(language = "fr"),
                        )
                    ),
                ),
            )
          )
      )

    val model = SurveyMutations.apply(base, upsert = upsert).forms.single().model!!

    // Bindings are replaced whole, not merged field by field.
    assertEquals(
      listOf(
        FieldBinding(field_path = "/data/a", read_only = true),
        FieldBinding(field_path = "/data/b"),
      ),
      model.bindings,
    )
    val english = model.translations!!.languages.single { it.language == "en" }
    assertEquals(setOf("a", "b"), english.strings.keys)
    assertEquals(true, english.is_default)
    assertEquals(listOf("en", "fr"), model.translations.languages.map { it.language })
  }

  @Test
  fun quotaUpsert_overwritesOnlySetLimits() {
    val base =
      SurveyDef(
        quota_limits =
          QuotaLimits(
            tier = QuotaTier.QUOTA_TIER_UNSPECIFIED,
            max_forms_per_survey = 5,
            max_acl_users = 10,
          )
      )

    val merged =
      SurveyMutations.apply(
        base,
        upsert = SurveyDef(quota_limits = QuotaLimits(max_acl_users = 20)),
      )

    assertEquals(QuotaLimits(max_forms_per_survey = 5, max_acl_users = 20), merged.quota_limits)
  }

  @Test
  fun aclUpsert_mergesEntriesCaseInsensitivelyByEmail() {
    val base =
      SurveyAcl(
        entries =
          listOf(
            AclEntry(email = "ana@example.org", role = Role.VIEWER, invite_token = "t1"),
            AclEntry(email = "bo@example.org", role = Role.VIEWER),
          )
      )

    val merged =
      SurveyMutations.applyAcl(
        base,
        upsert =
          SurveyAcl(
            entries = listOf(AclEntry(email = "ANA@example.org", role = Role.DATA_COLLECTOR))
          ),
      )

    val ana = merged.entries.first()
    assertEquals(2, merged.entries.size)
    assertEquals(Role.DATA_COLLECTOR, ana.role)
    assertEquals("t1", ana.invite_token)
  }

  @Test
  fun aclUpsert_acceptsOnlySharingRolesAsDefault() {
    val base = SurveyAcl(default_role = Role.VIEWER)

    val organizer =
      SurveyMutations.applyAcl(base, upsert = SurveyAcl(default_role = Role.SURVEY_ORGANIZER))
    val collector =
      SurveyMutations.applyAcl(base, upsert = SurveyAcl(default_role = Role.DATA_COLLECTOR))

    assertEquals(Role.VIEWER, organizer.default_role)
    assertEquals(Role.DATA_COLLECTOR, collector.default_role)
  }

  @Test
  fun aclDeleteThenUpsert_appliesTheDeleteFirst() {
    val base =
      SurveyAcl(
        entries =
          listOf(AclEntry(email = "ana@example.org", role = Role.VIEWER, invite_token = "old"))
      )

    val result =
      SurveyMutations.applyAcl(
        base,
        delete = SurveyAcl(entries = listOf(AclEntry(email = "ana@example.org"))),
        upsert =
          SurveyAcl(entries = listOf(AclEntry(email = "ana@example.org", role = Role.VIEWER))),
      )

    // The leaf delete removes the entry (and its token); the upsert then adds it back fresh.
    assertEquals(listOf(AclEntry(email = "ana@example.org", role = Role.VIEWER)), result.entries)
  }
}
