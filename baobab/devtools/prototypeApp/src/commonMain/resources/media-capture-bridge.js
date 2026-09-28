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

// Browser bridge used by the Ground prototype app to answer photo, video, and audio questions.
//
// Uses the HTML Media Capture `capture` attribute on `<input type="file">`: on mobile browsers this
// opens the camera or voice recorder directly; on desktop browsers it opens a file picker. The
// result is handed back to Kotlin (JS or Wasm) as base64 plus metadata so both targets can share
// one implementation. Photos larger than `maxPixels` (XForms `orx:max-pixels`) are downscaled
// proportionally and re-encoded as JPEG.
window.GroundMediaCaptureBridge = (function () {
  'use strict';

  /**
   * @param {string} accept MIME pattern(s) for the input's `accept` attribute.
   * @param {string} capture "environment", "user", or "" to allow picking existing files.
   * @param {number} maxPixels Long-edge photo limit, or 0 for none.
   * @param {function(string, string, string, number, number, number, string)} callback
   *     (fileName, mimeType, base64, durationMs, widthPx, heightPx, error). An empty fileName with
   *     an empty error means the user cancelled. Unknown numeric metadata is -1.
   */
  function open(accept, capture, maxPixels, callback) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept || '';
    if (capture) input.setAttribute('capture', capture);
    input.style.display = 'none';
    document.body.appendChild(input);

    let settled = false;
    const finish = (name, mime, b64, durationMs, width, height, error) => {
      if (settled) return;
      settled = true;
      input.remove();
      callback(name, mime, b64, durationMs, width, height, error);
    };
    const cancel = () => finish('', '', '', -1, -1, -1, '');

    input.addEventListener('cancel', cancel);
    // Older browsers do not fire `cancel`; treat regaining focus without a selection as cancel.
    window.addEventListener(
      'focus',
      () =>
        setTimeout(() => {
          if (!settled && !(input.files && input.files.length)) cancel();
        }, 1500),
      { once: true },
    );

    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (!file) {
        cancel();
        return;
      }
      const mime = file.type || '';
      const url = URL.createObjectURL(file);

      const deliver = (blob, outMime, durationMs, width, height) => {
        const reader = new FileReader();
        reader.onload = () => {
          URL.revokeObjectURL(url);
          const dataUrl = String(reader.result);
          finish(
            file.name,
            outMime,
            dataUrl.substring(dataUrl.indexOf(',') + 1),
            durationMs,
            width,
            height,
            '',
          );
        };
        reader.onerror = () => {
          URL.revokeObjectURL(url);
          finish('', '', '', -1, -1, -1, 'Could not read ' + file.name);
        };
        reader.readAsDataURL(blob);
      };

      if (mime.startsWith('image/')) {
        const img = new Image();
        img.onload = () => {
          const w = img.naturalWidth;
          const h = img.naturalHeight;
          const longEdge = Math.max(w, h);
          if (maxPixels > 0 && longEdge > maxPixels) {
            const scale = maxPixels / longEdge;
            const cw = Math.round(w * scale);
            const ch = Math.round(h * scale);
            const canvas = document.createElement('canvas');
            canvas.width = cw;
            canvas.height = ch;
            canvas.getContext('2d').drawImage(img, 0, 0, cw, ch);
            canvas.toBlob(
              (scaled) =>
                scaled ? deliver(scaled, 'image/jpeg', -1, cw, ch) : deliver(file, mime, -1, w, h),
              'image/jpeg',
              0.9,
            );
          } else {
            deliver(file, mime, -1, w, h);
          }
        };
        img.onerror = () => deliver(file, mime, -1, -1, -1);
        img.src = url;
      } else if (mime.startsWith('video/') || mime.startsWith('audio/')) {
        const el = document.createElement(mime.startsWith('video/') ? 'video' : 'audio');
        el.preload = 'metadata';
        el.onloadedmetadata = () => {
          const durationMs = isFinite(el.duration) ? Math.round(el.duration * 1000) : -1;
          deliver(file, mime, durationMs, el.videoWidth || -1, el.videoHeight || -1);
        };
        el.onerror = () => deliver(file, mime, -1, -1, -1);
        el.src = url;
      } else {
        deliver(file, mime || 'application/octet-stream', -1, -1, -1);
      }
    });

    input.click();
  }

  return { open: open };
})();
