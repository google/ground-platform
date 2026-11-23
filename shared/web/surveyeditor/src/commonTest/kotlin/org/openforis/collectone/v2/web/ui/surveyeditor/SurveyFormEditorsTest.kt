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
package org.openforis.collectone.v2.web.ui.surveyeditor

import androidx.lifecycle.viewModelScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.openforis.collectone.v2.app.domain.model.editor.EditorFormTemplates
import org.openforis.collectone.v2.web.ui.formeditor.FormEditorViewModel

/** [SurveyFormEditors]: one editor per Form, each in a child scope of the Survey editor. */
class SurveyFormEditorsTest {
  private val parent = CoroutineScope(Job() + Dispatchers.Unconfined)
  private val formScopes = mutableMapOf<String, CoroutineScope>()
  private val editors =
    SurveyFormEditors(parent) { formKey, formScope ->
      formScopes[formKey] = formScope
      FormEditorViewModel(
        formFlow = MutableStateFlow(EditorFormTemplates.blank(formId = formKey, title = formKey)),
        datasetsFlow = MutableStateFlow(emptyList()),
        updateForm = {},
        coroutineScope = formScope,
      )
    }

  @AfterTest
  fun tearDown() {
    parent.cancel()
  }

  @Test
  fun get_createsAnEditorOncePerForm() {
    val first = editors["f1"]

    assertSame(first, editors["f1"])
    assertNotSame(first, editors["f2"])
    assertEquals(setOf("f1", "f2"), formScopes.keys)
  }

  @Test
  fun close_stopsTheEditor_andTheNextGetCreatesANewOne() {
    val first = editors["f1"]
    val other = editors["f2"]

    editors.close("f1")
    editors.close("missing")

    assertFalse(first.viewModelScope.isActive)
    assertFalse(formScopes.getValue("f1").isActive, "the editor ran in its Form scope")
    assertTrue(other.viewModelScope.isActive)
    assertNotSame(first, editors["f1"])
  }

  @Test
  fun clear_stopsEveryEditor() {
    val first = editors["f1"]
    val second = editors["f2"]

    editors.clear()

    assertFalse(first.viewModelScope.isActive)
    assertFalse(second.viewModelScope.isActive)
    assertNotSame(first, editors["f1"])
  }

  @Test
  fun formScopes_stopWithTheSurveyEditor_butNotWithEachOther() {
    editors["f1"]
    editors["f2"]

    formScopes.getValue("f1").cancel()
    assertTrue(parent.isActive, "a Form editor's scope doesn't take the Survey editor down")
    assertTrue(formScopes.getValue("f2").isActive)

    parent.cancel()
    assertTrue(formScopes.getValue("f2").coroutineContext.job.isCancelled)
  }
}
