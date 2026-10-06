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
// Two entry points share one callback contract so Kotlin (JS or Wasm) can treat them alike:
//
// - `open()` uses the HTML Media Capture `capture` attribute on `<input type="file">`: on mobile
//   browsers this opens the camera or voice recorder directly; on desktop browsers the attribute is
//   ignored and it opens a file picker, so it is only appropriate when uploads are allowed or on
//   mobile browsers (`prefersFileInputCapture()`).
// - `openLive()` captures in-page through `getUserMedia` + `MediaRecorder` with a full-screen
//   overlay (live preview, capture / record, switch camera, confirm). This is what enforces
//   "capture only" questions on desktop browsers, where a file input would silently degrade to a
//   file picker.
//
// Results are handed back as base64 plus metadata. Photos larger than `maxPixels` (XForms
// `orx:max-pixels`) are downscaled proportionally and re-encoded as JPEG.
window.GroundMediaCaptureBridge = (function () {
  'use strict';

  /** Whether in-page live capture (`openLive`) can work in this browser at all. */
  function isLiveCaptureSupported() {
    return !!(
      typeof navigator !== 'undefined' &&
      navigator.mediaDevices &&
      navigator.mediaDevices.getUserMedia &&
      window.MediaRecorder
    );
  }

  /**
   * Whether the HTML `capture` attribute is the better capture UX here: on phones and tablets it
   * opens the native camera / recorder app, which beats an in-page overlay.
   */
  function prefersFileInputCapture() {
    const ua = (typeof navigator !== 'undefined' && navigator.userAgent) || '';
    if (/Android|iPhone|iPad|iPod/i.test(ua)) return true;
    // iPadOS 13+ reports a desktop UA but is a touch device.
    if (/Macintosh/i.test(ua) && typeof navigator !== 'undefined' && navigator.maxTouchPoints > 1) {
      return true;
    }
    return !!(window.matchMedia && window.matchMedia('(pointer: coarse)').matches);
  }

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

  // ---------------------------------------------------------------------------------------------
  // Live in-page capture (getUserMedia + MediaRecorder)
  // ---------------------------------------------------------------------------------------------

  const OVERLAY_ID = 'ground-media-capture-overlay';

  /** Picks the first `MediaRecorder` MIME type the browser can encode, or `''` for its default. */
  function pickRecorderMimeType(candidates) {
    if (!window.MediaRecorder || !MediaRecorder.isTypeSupported) return '';
    for (const type of candidates) {
      if (MediaRecorder.isTypeSupported(type)) return type;
    }
    return '';
  }

  function humanMediaError(err, kind) {
    const device = kind === 'audio' ? 'Microphone' : 'Camera';
    const name = (err && err.name) || '';
    if (['NotAllowedError', 'SecurityError', 'PermissionDeniedError'].includes(name)) {
      return device + ' access was denied.';
    }
    if (['NotFoundError', 'DevicesNotFoundError', 'OverconstrainedError'].includes(name)) {
      return 'No ' + device.toLowerCase() + ' was found on this device.';
    }
    if (['NotReadableError', 'TrackStartError', 'AbortError'].includes(name)) {
      return 'The ' + device.toLowerCase() + ' is in use by another app.';
    }
    return device + ' capture failed' + (err && err.message ? ': ' + err.message : '.');
  }

  function formatElapsed(ms) {
    const total = Math.round(ms / 1000);
    const m = Math.floor(total / 60);
    const s = String(total % 60).padStart(2, '0');
    return m + ':' + s;
  }

  function el(tag, styles, text) {
    const node = document.createElement(tag);
    if (styles) Object.assign(node.style, styles);
    if (text != null) node.textContent = text;
    return node;
  }

  function button(label, variant) {
    const filled = variant === 'filled';
    const b = el(
      'button',
      {
        font: '500 15px/20px system-ui, sans-serif',
        padding: '0 24px',
        height: '44px',
        borderRadius: '22px',
        border: filled ? 'none' : '1px solid rgba(255,255,255,0.6)',
        background: filled ? '#D0BCFF' : 'transparent',
        color: filled ? '#381E72' : '#FFFFFF',
        cursor: 'pointer',
        minWidth: '96px',
      },
      label,
    );
    b.type = 'button';
    return b;
  }

  /**
   * Captures a photo, video clip, or audio recording in-page with a live preview overlay.
   *
   * @param {string} kind "photo", "video", or "audio".
   * @param {string} facing "environment" (rear camera) or "user" (front camera).
   * @param {number} maxPixels Long-edge photo limit, or 0 for none.
   * @param {function(string, string, string, number, number, number, string)} callback Same
   *     contract as `open()`: (fileName, mimeType, base64, durationMs, widthPx, heightPx, error).
   */
  function openLive(kind, facing, maxPixels, callback) {
    let settled = false;
    let stream = null;
    let recorder = null;
    let timer = null;
    let previewUrl = null;
    const previous = document.getElementById(OVERLAY_ID);
    if (previous) previous.remove();

    const overlay = el('div', {
      position: 'fixed',
      inset: '0',
      zIndex: '2147483000',
      background: 'rgba(0,0,0,0.88)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '16px',
      padding: '24px',
      boxSizing: 'border-box',
      color: '#FFFFFF',
      font: '400 14px/20px system-ui, sans-serif',
    });
    overlay.id = OVERLAY_ID;
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');

    const title = el('div', { font: '500 18px/24px system-ui, sans-serif' });
    const stage = el('div', {
      width: 'min(90vw, 720px)',
      maxHeight: '60vh',
      borderRadius: '16px',
      overflow: 'hidden',
      background: '#1C1B1F',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    });
    const status = el('div', { minHeight: '20px', opacity: '0.85' });
    const controls = el('div', {
      display: 'flex',
      flexWrap: 'wrap',
      gap: '12px',
      justifyContent: 'center',
    });
    overlay.append(title, stage, status, controls);
    document.body.appendChild(overlay);

    const cleanup = () => {
      if (timer) clearInterval(timer);
      timer = null;
      if (recorder && recorder.state !== 'inactive') {
        try {
          recorder.stop();
        } catch (e) {
          // Already stopping.
        }
      }
      recorder = null;
      if (stream) stream.getTracks().forEach((t) => t.stop());
      stream = null;
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      previewUrl = null;
      document.removeEventListener('keydown', onKey);
      overlay.remove();
    };
    const finish = (name, mime, b64, durationMs, width, height, error) => {
      if (settled) return;
      settled = true;
      cleanup();
      callback(name, mime, b64, durationMs, width, height, error);
    };
    const cancel = () => finish('', '', '', -1, -1, -1, '');
    const fail = (message) => finish('', '', '', -1, -1, -1, message);
    const onKey = (e) => {
      if (e.key === 'Escape') cancel();
    };
    document.addEventListener('keydown', onKey);

    if (!isLiveCaptureSupported()) {
      fail(
        window.isSecureContext === false
          ? 'Live capture needs a secure (https) connection.'
          : "This browser can't capture media directly.",
      );
      return;
    }

    const wantsVideo = kind === 'photo' || kind === 'video';
    title.textContent =
      kind === 'photo' ? 'Take photo' : kind === 'video' ? 'Record video' : 'Record audio';

    const deliverBlob = (blob, fileName, durationMs, width, height) => {
      const reader = new FileReader();
      reader.onload = () => {
        const dataUrl = String(reader.result);
        finish(
          fileName,
          blob.type || '',
          dataUrl.substring(dataUrl.indexOf(',') + 1),
          durationMs,
          width,
          height,
          '',
        );
      };
      reader.onerror = () => fail('Could not read the captured ' + kind + '.');
      reader.readAsDataURL(blob);
    };

    /** Confirm step: shows the still / clip with Use / Retake. */
    const showConfirm = (blob, durationMs, width, height, onRetake) => {
      stage.replaceChildren();
      controls.replaceChildren();
      status.textContent = '';
      previewUrl = URL.createObjectURL(blob);
      let media;
      if (kind === 'photo') {
        media = el('img', { maxWidth: '100%', maxHeight: '60vh', display: 'block' });
        media.alt = 'Captured photo';
      } else {
        media = el(kind === 'video' ? 'video' : 'audio', {
          maxWidth: '100%',
          maxHeight: '60vh',
          display: 'block',
        });
        if (kind === 'audio') media.style.width = 'min(90vw, 480px)';
        media.controls = true;
        media.playsInline = true;
      }
      media.src = previewUrl;
      stage.appendChild(media);
      if (kind === 'audio') {
        stage.style.padding = '16px';
      }
      const ext = kind === 'photo' ? 'jpg' : blob.type.split(';')[0].split('/')[1] || 'webm';
      const fileName = kind + '-' + Date.now() + '.' + ext;
      const use = button('Use', 'filled');
      use.onclick = () => deliverBlob(blob, fileName, durationMs, width, height);
      const retake = button(kind === 'photo' ? 'Retake' : 'Record again', 'outlined');
      retake.onclick = () => {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = null;
        stage.style.padding = '';
        onRetake();
      };
      const cancelBtn = button('Cancel', 'outlined');
      cancelBtn.onclick = cancel;
      controls.append(retake, use, cancelBtn);
      use.focus();
    };

    const start = (useFacing) => {
      if (stream) stream.getTracks().forEach((t) => t.stop());
      stream = null;
      stage.replaceChildren();
      controls.replaceChildren();
      status.textContent = kind === 'audio' ? 'Waiting for microphone…' : 'Starting camera…';

      const constraints = wantsVideo
        ? {
            video: { facingMode: useFacing || 'environment' },
            audio: kind === 'video',
          }
        : { audio: true };

      navigator.mediaDevices
        .getUserMedia(constraints)
        .then((s) => {
          if (settled) {
            s.getTracks().forEach((t) => t.stop());
            return;
          }
          stream = s;
          status.textContent = '';
          if (wantsVideo) setUpCameraStage(useFacing);
          else setUpAudioStage();
        })
        .catch((err) => fail(humanMediaError(err, kind)));
    };

    const addSwitchCamera = (currentFacing) => {
      if (!navigator.mediaDevices.enumerateDevices) return;
      navigator.mediaDevices
        .enumerateDevices()
        .then((devices) => {
          if (settled) return;
          const cameras = devices.filter((d) => d.kind === 'videoinput');
          if (cameras.length < 2) return;
          const sw = button('Switch camera', 'outlined');
          sw.onclick = () => start(currentFacing === 'user' ? 'environment' : 'user');
          controls.insertBefore(sw, controls.firstChild);
        })
        .catch(() => {});
    };

    const setUpCameraStage = (currentFacing) => {
      const video = el('video', { maxWidth: '100%', maxHeight: '60vh', display: 'block' });
      video.autoplay = true;
      video.muted = true;
      video.playsInline = true;
      video.srcObject = stream;
      stage.appendChild(video);
      const cancelBtn = button('Cancel', 'outlined');
      cancelBtn.onclick = cancel;

      if (kind === 'photo') {
        const shoot = button('Capture', 'filled');
        shoot.onclick = () => {
          let w = video.videoWidth;
          let h = video.videoHeight;
          if (!w || !h) {
            status.textContent = 'Camera is not ready yet.';
            return;
          }
          const longEdge = Math.max(w, h);
          if (maxPixels > 0 && longEdge > maxPixels) {
            const scale = maxPixels / longEdge;
            w = Math.round(w * scale);
            h = Math.round(h * scale);
          }
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          canvas.getContext('2d').drawImage(video, 0, 0, w, h);
          canvas.toBlob(
            (blob) => {
              if (!blob) {
                fail('Could not encode the photo.');
                return;
              }
              if (stream) stream.getTracks().forEach((t) => t.stop());
              stream = null;
              showConfirm(blob, -1, w, h, () => start(currentFacing));
            },
            'image/jpeg',
            0.9,
          );
        };
        controls.append(shoot, cancelBtn);
        shoot.focus();
      } else {
        const rec = button('Start recording', 'filled');
        const dims = { width: () => video.videoWidth, height: () => video.videoHeight };
        rec.onclick = () => beginRecording(rec, dims, currentFacing);
        controls.append(rec, cancelBtn);
        rec.focus();
      }
      addSwitchCamera(currentFacing);
    };

    const setUpAudioStage = () => {
      stage.style.padding = '24px';
      const meter = el('div', {
        width: 'min(80vw, 360px)',
        height: '8px',
        borderRadius: '4px',
        background: 'rgba(255,255,255,0.2)',
        overflow: 'hidden',
      });
      const level = el('div', { width: '0%', height: '100%', background: '#D0BCFF' });
      meter.appendChild(level);
      const label = el('div', { marginTop: '12px', textAlign: 'center' }, 'Ready to record');
      const wrap = el('div', {});
      wrap.append(meter, label);
      stage.appendChild(wrap);

      // Simple level meter via AnalyserNode when available.
      let audioCtx = null;
      try {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (Ctx) {
          audioCtx = new Ctx();
          const analyser = audioCtx.createAnalyser();
          analyser.fftSize = 256;
          audioCtx.createMediaStreamSource(stream).connect(analyser);
          const data = new Uint8Array(analyser.frequencyBinCount);
          const tick = () => {
            if (settled || !stream) {
              if (audioCtx) audioCtx.close().catch(() => {});
              return;
            }
            analyser.getByteTimeDomainData(data);
            let peak = 0;
            for (let i = 0; i < data.length; i++) peak = Math.max(peak, Math.abs(data[i] - 128));
            level.style.width = Math.min(100, Math.round((peak / 128) * 100)) + '%';
            requestAnimationFrame(tick);
          };
          tick();
        }
      } catch (e) {
        // Level meter is cosmetic.
      }

      const cancelBtn = button('Cancel', 'outlined');
      cancelBtn.onclick = cancel;
      const rec = button('Start recording', 'filled');
      rec.onclick = () => beginRecording(rec, null, null, label);
      controls.append(rec, cancelBtn);
      rec.focus();
    };

    const beginRecording = (recButton, dims, currentFacing, elapsedLabel) => {
      const mimeType =
        kind === 'video'
          ? pickRecorderMimeType([
              'video/webm;codecs=vp9,opus',
              'video/webm;codecs=vp8,opus',
              'video/webm',
              'video/mp4',
            ])
          : pickRecorderMimeType([
              'audio/webm;codecs=opus',
              'audio/webm',
              'audio/mp4',
              'audio/ogg',
            ]);
      try {
        recorder = mimeType
          ? new MediaRecorder(stream, { mimeType: mimeType })
          : new MediaRecorder(stream);
      } catch (err) {
        fail(humanMediaError(err, kind));
        return;
      }
      const chunks = [];
      const startedAt = Date.now();
      const width = dims ? dims.width() || -1 : -1;
      const height = dims ? dims.height() || -1 : -1;
      const fallbackType = kind === 'video' ? 'video/webm' : 'audio/webm';
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size) chunks.push(e.data);
      };
      recorder.onerror = () => fail('Recording failed.');
      recorder.onstop = () => {
        const durationMs = Date.now() - startedAt;
        const type = (recorder && recorder.mimeType) || mimeType || fallbackType;
        recorder = null;
        if (timer) clearInterval(timer);
        timer = null;
        if (settled) return;
        if (stream) stream.getTracks().forEach((t) => t.stop());
        stream = null;
        const blob = new Blob(chunks, { type: type.split(';')[0] });
        if (!blob.size) {
          fail('Nothing was recorded.');
          return;
        }
        showConfirm(blob, durationMs, width, height, () => start(currentFacing));
      };
      recorder.start(250);

      recButton.textContent = 'Stop';
      recButton.style.background = '#F2B8B5';
      recButton.style.color = '#601410';
      recButton.onclick = () => {
        recButton.disabled = true;
        if (recorder && recorder.state !== 'inactive') recorder.stop();
      };
      // Disable the Switch camera button while recording.
      Array.from(controls.children).forEach((c) => {
        if (c !== recButton && c.textContent === 'Switch camera') c.disabled = true;
      });
      const update = () => {
        const text = '● Recording ' + formatElapsed(Date.now() - startedAt);
        if (elapsedLabel) elapsedLabel.textContent = text;
        else status.textContent = text;
      };
      update();
      timer = setInterval(update, 500);
    };

    start(facing === 'user' ? 'user' : 'environment');
  }

  return {
    open: open,
    openLive: openLive,
    isLiveCaptureSupported: isLiveCaptureSupported,
    prefersFileInputCapture: prefersFileInputCapture,
  };
})();
