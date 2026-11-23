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
package org.openforis.collectone.v2.web.ui.text

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.openforis.collectone.v2.app.data.seed.SurveyEditorSamples
import org.openforis.collectone.v2.app.domain.model.editor.DatasetKind
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.ui.common.ProvideAppLocale
import org.openforis.collectone.v2.web.ui.surveyeditor.SurveyEditorSection
import org.openforis.collectone.v2.web.ui.surveyeditor.surveyEditorViewModel

/** The editors' starting names for new items, in English and in the language the console shows. */
@OptIn(ExperimentalTestApi::class)
class EditorDefaultNamesTest {
  @Test
  fun englishResources_areTheEnglishDefaults() {
    val english = runBlocking { editorDefaultNamesOf { getString(it) } }

    assertEquals(EditorDefaultNames.ENGLISH, english)
  }

  @Test
  fun consoleInKhmer_namesNewItemsInKhmer() {
    val names = namesShownIn("km")

    assertEquals("ស្រទាប់ផែនទីថ្មី", names.dataset(DatasetKind.MAP_LAYER))
    assertEquals("សំណួររូបថតថ្មី", names.question(EditorQuestionType.PHOTO))
  }

  @Test
  fun consoleInThai_namesNewItemsInThai() {
    val names = namesShownIn("th")

    assertEquals("ตารางข้อมูลใหม่", names.dataset(DatasetKind.DATA_TABLE))
    assertEquals("แบบฟอร์มใหม่", names.form)
  }

  @Test
  fun khmerNames_giveNewDatasetsFallbackIds() {
    val viewModel = surveyEditorViewModel(SurveyEditorSamples.draft())
    viewModel.useDefaultNames(namesShownIn("km"))

    viewModel.addDataset(DatasetKind.MAP_LAYER)

    val added = assertNotNull(viewModel.uiState.value.selectedDataset)
    assertEquals("ស្រទាប់ផែនទីថ្មី", added.displayName)
    assertEquals("dataset_1", added.id)
  }

  @Test
  fun thaiNames_labelNewQuestionsAndForms() {
    val viewModel = surveyEditorViewModel(SurveyEditorSamples.draft())
    viewModel.useDefaultNames(namesShownIn("th"))

    viewModel.addForm()
    val formKey = (viewModel.uiState.value.section as SurveyEditorSection.Form).key
    viewModel.formEditor(formKey).addQuestion(EditorQuestionType.PHOTO)

    val form = assertNotNull(viewModel.uiState.value.form(formKey)).form
    assertEquals("แบบฟอร์มใหม่", form.title)
    assertTrue(form.questions.any { it.label == "คำถามรูปภาพใหม่" })
  }

  /** The [EditorDefaultNames] the console resolves while it shows [languageCode]. */
  private fun namesShownIn(languageCode: String): EditorDefaultNames {
    var names: EditorDefaultNames? = null
    // On the JVM the app locale is the default locale, which the other tests expect in English.
    val defaultLocale = Locale.getDefault()
    try {
      runDesktopComposeUiTest {
        setContent { ProvideAppLocale(languageCode) { names = editorDefaultNames() } }
        waitForIdle()
      }
    } finally {
      Locale.setDefault(defaultLocale)
    }
    return assertNotNull(names)
  }
}
