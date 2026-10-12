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

// Browser bridge used by the Ground prototype app to save, preview, and share PDFs generated
// client-side in Kotlin (`pdf/` package). PDFs arrive as base64 so Kotlin/JS and Kotlin/Wasm can
// share one implementation.
//
// Sharing uses the Web Share API Level 2 (`navigator.share({ files })`), which opens the OS share
// sheet (WhatsApp, Gmail, Drive, Bluetooth, ...) on Android, iOS, ChromeOS, Windows, and macOS
// Safari. Where files can't be shared, the PDF is downloaded instead.
window.GroundPdfExportBridge = (function () {
  'use strict';

  const MIME_TYPE = 'application/pdf';

  function toBlob(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return new Blob([bytes], { type: MIME_TYPE });
  }

  function save(fileName, base64) {
    const url = URL.createObjectURL(toBlob(base64));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    // Revoke later: some browsers start the download asynchronously.
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  function preview(fileName, base64) {
    const url = URL.createObjectURL(toBlob(base64));
    // No 'noopener' feature: with it, window.open() always returns null and we couldn't detect a
    // blocked pop-up. The blob: URL has no access to this page anyway.
    const opened = window.open(url, '_blank');
    if (!opened) {
      // Pop-up blocked (or no built-in viewer): fall back to a download.
      URL.revokeObjectURL(url);
      save(fileName, base64);
      return;
    }
    setTimeout(() => URL.revokeObjectURL(url), 120000);
  }

  function canShareFiles() {
    try {
      if (!navigator.share || !navigator.canShare) return false;
      const probe = new File([new Blob(['%PDF'], { type: MIME_TYPE })], 'probe.pdf', {
        type: MIME_TYPE,
      });
      return navigator.canShare({ files: [probe] });
    } catch (e) {
      return false;
    }
  }

  /**
   * @param {string} fileName
   * @param {string} title
   * @param {string} base64
   * @param {function(string)} callback "shared", "cancelled", "saved", or "failed:<reason>".
   */
  function share(fileName, title, base64, callback) {
    const done = typeof callback === 'function' ? callback : function () {};
    let file;
    try {
      file = new File([toBlob(base64)], fileName, { type: MIME_TYPE });
    } catch (e) {
      done('failed:' + e);
      return;
    }
    if (!canShareFiles() || !navigator.canShare({ files: [file] })) {
      save(fileName, base64);
      done('saved');
      return;
    }
    navigator
      .share({ files: [file], title: title })
      .then(() => done('shared'))
      .catch((e) => {
        if (e && e.name === 'AbortError') {
          done('cancelled');
        } else if (e && e.name === 'NotAllowedError') {
          // Lost the user activation (e.g. slow generation): download instead.
          save(fileName, base64);
          done('saved');
        } else {
          done('failed:' + (e && e.message ? e.message : e));
        }
      });
  }

  return { save: save, preview: preview, share: share, canShareFiles: canShareFiles };
})();
