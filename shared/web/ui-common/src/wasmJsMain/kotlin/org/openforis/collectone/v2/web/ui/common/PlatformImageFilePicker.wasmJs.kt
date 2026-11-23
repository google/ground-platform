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
package org.openforis.collectone.v2.web.ui.common

import org.openforis.collectone.v2.app.ui.text.toUiText
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.web_file_read_failed

/**
 * Opens a hidden `<input type="file">` accepting common web image formats, decodes the selected
 * image in-browser, scales it proportionally to fit within `maxDimensionPx × maxDimensionPx`, and
 * encodes it as a compact PNG (or JPEG fallback when the PNG exceeds 200 KB) data URI.
 *
 * Calls back with `(fileName, dataUrl, error)`; an empty `fileName` means the picker was cancelled,
 * and a non-empty `error` means the file couldn't be read or decoded as an image.
 */
@JsFun(
  """(maxDim, callback) => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/png,image/jpeg,image/webp,image/gif,image/svg+xml';
  input.style.display = 'none';
  let done = false;
  const finish = (name, dataUrl, error) => {
    if (done) return;
    done = true;
    input.remove();
    callback(name, dataUrl, error);
  };
  input.addEventListener('change', () => {
    const file = input.files && input.files[0];
    if (!file) { finish('', '', ''); return; }
    const objectUrl = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      try {
        const naturalW = img.naturalWidth || maxDim;
        const naturalH = img.naturalHeight || maxDim;
        const scale = Math.min(1, maxDim / Math.max(naturalW, naturalH));
        const targetW = Math.max(1, Math.round(naturalW * scale));
        const targetH = Math.max(1, Math.round(naturalH * scale));
        const canvas = document.createElement('canvas');
        canvas.width = targetW;
        canvas.height = targetH;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          URL.revokeObjectURL(objectUrl);
          finish(file.name, '', 'canvas-unavailable');
          return;
        }
        ctx.drawImage(img, 0, 0, targetW, targetH);
        let dataUrl = canvas.toDataURL('image/png');
        if (dataUrl.length > 200000) {
          ctx.fillStyle = '#FFFFFF';
          ctx.globalCompositeOperation = 'destination-over';
          ctx.fillRect(0, 0, targetW, targetH);
          dataUrl = canvas.toDataURL('image/jpeg', 0.85);
        }
        URL.revokeObjectURL(objectUrl);
        finish(file.name, dataUrl, '');
      } catch (e) {
        URL.revokeObjectURL(objectUrl);
        finish(file.name, '', 'encode-failed');
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      finish(file.name, '', 'decode-failed');
    };
    img.src = objectUrl;
  });
  input.addEventListener('cancel', () => finish('', '', ''));
  document.body.appendChild(input);
  input.click();
}"""
)
private external fun jsPickImageFile(maxDim: Int, callback: (String, String, String) -> Unit)

actual fun openPlatformImageFilePicker(
  maxDimensionPx: Int,
  onResult: (ImageFilePickResult) -> Unit,
) {
  jsPickImageFile(maxDimensionPx) { fileName, dataUrl, error ->
    when {
      error.isNotEmpty() ->
        onResult(ImageFilePickResult.Failed(Res.string.web_file_read_failed.toUiText(fileName)))
      fileName.isEmpty() || dataUrl.isEmpty() -> onResult(ImageFilePickResult.Cancelled)
      else -> onResult(ImageFilePickResult.Picked(fileName, dataUrl))
    }
  }
}
