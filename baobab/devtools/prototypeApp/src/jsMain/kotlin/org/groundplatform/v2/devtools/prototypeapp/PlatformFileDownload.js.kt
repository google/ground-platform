/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp

internal actual fun downloadTextFile(fileName: String, mimeType: String, content: String) {
  val blob = js("new Blob([content], { type: mimeType })")
  val url = js("URL.createObjectURL(blob)")
  val anchor = js("document.createElement('a')")
  anchor.href = url
  anchor.download = fileName
  anchor.style.display = "none"
  js("document.body.appendChild(anchor)")
  anchor.click()
  js("document.body.removeChild(anchor)")
  js("URL.revokeObjectURL(url)")
}
