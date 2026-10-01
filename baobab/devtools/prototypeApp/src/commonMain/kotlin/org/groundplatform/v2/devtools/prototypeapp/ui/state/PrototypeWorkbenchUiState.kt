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
package org.groundplatform.v2.devtools.prototypeapp.ui.state

import groundplatform.v2.forms.FormDef
import org.groundplatform.v2.core.forms.ui.WorkbenchExampleForm
import org.groundplatform.v2.devtools.prototypeapp.DEFAULT_PROTOTYPE_XFORMS_XML
import org.groundplatform.v2.devtools.prototypeapp.XFormsParseCache
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceFormFactor
import org.groundplatform.v2.devtools.prototypeapp.domain.model.DeviceOrientation

/**
 * Prototype-only UI state slice for the simulated device bezel, airplane mode toggle, and XForms
 * XML workbench editor.
 */
data class PrototypeWorkbenchUiState(
  val isDarkTheme: Boolean = false,
  val deviceFormFactor: DeviceFormFactor = DeviceFormFactor.MOBILE,
  val deviceOrientation: DeviceOrientation = DeviceFormFactor.MOBILE.defaultOrientation,
  val isAirplaneMode: Boolean = false,
  val customXFormsXml: String = DEFAULT_PROTOTYPE_XFORMS_XML,
  val selectedWorkbenchExampleForm: WorkbenchExampleForm? = WorkbenchExampleForm.ALL_FIELD_TYPES,
  val customFormDef: FormDef? = XFormsParseCache.formDef(WorkbenchExampleForm.ALL_FIELD_TYPES),
  val xformsXmlError: String? = null,
  val uploadedMediaCacheSizeLabel: String = "0 MB",
  val uploadedMediaFileCount: Int = 0,
  val isWebsiteModalOpen: Boolean = false,
)
