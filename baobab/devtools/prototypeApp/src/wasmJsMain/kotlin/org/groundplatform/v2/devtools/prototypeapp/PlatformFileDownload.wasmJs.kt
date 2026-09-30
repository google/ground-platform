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

@JsFun(
  "(fileName, mimeType, content) => {" +
    " const blob = new Blob([content], { type: mimeType });" +
    " const url = URL.createObjectURL(blob);" +
    " const anchor = document.createElement('a');" +
    " anchor.href = url;" +
    " anchor.download = fileName;" +
    " anchor.style.display = 'none';" +
    " document.body.appendChild(anchor);" +
    " anchor.click();" +
    " document.body.removeChild(anchor);" +
    " URL.revokeObjectURL(url);" +
    " }"
)
private external fun jsDownloadTextFile(fileName: String, mimeType: String, content: String)

internal actual fun downloadTextFile(fileName: String, mimeType: String, content: String) {
  jsDownloadTextFile(fileName, mimeType, content)
}
