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
package org.groundplatform.v2.devtools.prototypeapp

/**
 * Opens a hidden `<input type="file">` and reads the chosen file with `FileReader.readAsText`.
 * Calls back with `(fileName, text, error)`; an empty file name means the picker was cancelled.
 */
@JsFun(
  """(accept, callback) => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = accept;
  input.style.display = 'none';
  let done = false;
  const finish = (name, text, error) => {
    if (done) return;
    done = true;
    input.remove();
    callback(name, text, error);
  };
  input.addEventListener('change', () => {
    const file = input.files && input.files[0];
    if (!file) { finish('', '', ''); return; }
    const reader = new FileReader();
    reader.onload = () => finish(file.name, String(reader.result || ''), '');
    reader.onerror = () => finish(file.name, '', 'Couldn\'t read ' + file.name + '. Try again.');
    reader.readAsText(file);
  });
  input.addEventListener('cancel', () => finish('', '', ''));
  document.body.appendChild(input);
  input.click();
}"""
)
private external fun jsPickTextFile(accept: String, callback: (String, String, String) -> Unit)

internal actual val isPlatformTextFilePickerAvailable: Boolean = true

internal actual fun openPlatformTextFilePicker(
  accept: String,
  onResult: (TextFilePickResult) -> Unit,
) {
  jsPickTextFile(accept) { fileName, text, error ->
    onResult(
      when {
        error.isNotEmpty() -> TextFilePickResult.Failed(error)
        fileName.isEmpty() -> TextFilePickResult.Cancelled
        else -> TextFilePickResult.Picked(fileName, text)
      }
    )
  }
}
