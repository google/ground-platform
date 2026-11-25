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

// Browser crash and unhandled-error reporting bridge for Collect One web clients (`webApp`,
// `mobileWebApp`, and `balloonFormApp`).
//
// Captures uncaught `window` errors, unhandled promise rejections, and non-fatal errors forwarded
// from Kotlin/Wasm (`BrowserCrashReporter`), redacts email addresses and tokens, and posts compact
// JSON reports to `${window.COLLECT_ONE_USER_DATA_SERVICE_URL}/client-errors`. Safe inside
// sandboxed iframes (`Origin: null`): never touches browser storage or history.
window.CollectOneErrorReporter = (function () {
  const MAX_BREADCRUMBS = 30;
  const MAX_REPORTS_PER_SESSION = 20;
  const DEDUPE_WINDOW_MS = 30000;

  const EMAIL_RE = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+/g;
  const BEARER_RE = /\bbearer\s+[A-Za-z0-9._~+/=-]+/gi;
  const JWT_RE = /\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g;

  const breadcrumbs = [];
  const customKeys = {};
  const recentFingerprints = new Map();
  let reportsSent = 0;
  let userId = '';
  let config = {
    client: 'web',
    environment: typeof window !== 'undefined' && typeof window.COLLECT_ONE_ENV === 'string'
      ? window.COLLECT_ONE_ENV
      : '',
    appVersion: '',
    enabled: true,
  };

  function redact(text) {
    if (text == null) return '';
    return String(text)
      .replace(BEARER_RE, 'Bearer <token>')
      .replace(JWT_RE, '<token>')
      .replace(EMAIL_RE, '<email>');
  }

  function serviceUrl() {
    if (typeof window === 'undefined') return '';
    const raw = window.COLLECT_ONE_USER_DATA_SERVICE_URL;
    return typeof raw === 'string' ? raw.trim().replace(/\/+$/, '') : '';
  }

  function isEnabled() {
    return Boolean(config.enabled && serviceUrl());
  }

  function safeLocationUrl() {
    try {
      if (typeof window === 'undefined' || !window.location) return '';
      const loc = window.location;
      const base = (loc.origin && loc.origin !== 'null' ? loc.origin : '') + (loc.pathname || '');
      return redact(base);
    } catch (_) {
      return '';
    }
  }

  function logBreadcrumb(message) {
    if (!message) return;
    const cleaned = redact(message).slice(0, 256);
    breadcrumbs.push(cleaned);
    if (breadcrumbs.length > MAX_BREADCRUMBS) {
      breadcrumbs.shift();
    }
  }

  function reportError(typeName, message, stackTrace, kind) {
    if (!isEnabled()) return;
    if (reportsSent >= MAX_REPORTS_PER_SESSION) return;

    const cleanType = redact(typeName || 'Error').slice(0, 128);
    const cleanMessage = redact(message || '').slice(0, 1024);
    const cleanStack = redact(stackTrace || '').slice(0, 4096);
    const cleanKind = redact(kind || 'error').slice(0, 64);

    const fingerprint = cleanKind + '|' + cleanType + '|' + cleanMessage.slice(0, 160);
    const now = Date.now();
    const lastSent = recentFingerprints.get(fingerprint);
    if (lastSent && now - lastSent < DEDUPE_WINDOW_MS) return;
    recentFingerprints.set(fingerprint, now);
    reportsSent += 1;

    const payload = JSON.stringify({
      client: config.client || 'web',
      environment: config.environment || '',
      appVersion: config.appVersion || '',
      kind: cleanKind,
      typeName: cleanType,
      message: cleanMessage,
      stackTrace: cleanStack,
      url: safeLocationUrl(),
      userId: userId,
      customKeys: customKeys,
      breadcrumbs: breadcrumbs.slice(),
    });

    const endpoint = serviceUrl() + '/client-errors';
    try {
      if (typeof fetch === 'function') {
        fetch(endpoint, {
          method: 'POST',
          mode: 'cors',
          credentials: 'omit',
          keepalive: true,
          headers: { 'Content-Type': 'application/json' },
          body: payload,
        }).catch(function () {});
      }
    } catch (_) {}
  }

  function configure(next) {
    if (!next || typeof next !== 'object') return;
    if (typeof next.client === 'string' && next.client) config.client = next.client;
    if (typeof next.environment === 'string') config.environment = next.environment;
    if (typeof next.appVersion === 'string') config.appVersion = next.appVersion;
    if (typeof next.enabled === 'boolean') config.enabled = next.enabled;
  }

  function setCustomKey(key, value) {
    if (!key) return;
    customKeys[String(key).slice(0, 64)] = redact(value).slice(0, 128);
  }

  function setUserId(id) {
    userId = id ? redact(id).slice(0, 128) : '';
  }

  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('error', function (event) {
      try {
        const err = event && event.error;
        const typeName = (err && err.name) || 'Error';
        const message = (event && event.message) || (err && err.message) || 'Uncaught error';
        const stack = (err && err.stack) ||
          (event && event.filename ? event.filename + ':' + event.lineno + ':' + event.colno : '');
        reportError(typeName, message, stack, 'window.onerror');
      } catch (_) {}
    });

    window.addEventListener('unhandledrejection', function (event) {
      try {
        const reason = event && event.reason;
        const typeName = (reason && reason.name) || 'UnhandledRejection';
        const message = (reason && reason.message) || String(reason || 'Unhandled promise rejection');
        const stack = (reason && reason.stack) || '';
        reportError(typeName, message, stack, 'unhandledrejection');
      } catch (_) {}
    });
  }

  return {
    configure: configure,
    isEnabled: isEnabled,
    logBreadcrumb: logBreadcrumb,
    reportError: reportError,
    setCustomKey: setCustomKey,
    setUserId: setUserId,
  };
})();
