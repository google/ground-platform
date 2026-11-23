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
package org.openforis.collectone.v2.core.geo.io

import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter.SessionConfig
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter.escapeXml
import org.openforis.collectone.v2.core.geo.io.KmlBalloonWriter.jsonString

/**
 * Builds `<BalloonStyle>` HTML: either the bootstrap script framing `balloon-form.html` when a
 * layer has Google Earth-enabled forms, or a self-contained read-only feature properties card when
 * it does not.
 */
internal object KmlBalloonHtml {
  private const val MAX_INLINE_HASH_FORMS_CHARS = 16_384

  /**
   * Builds a self-contained read-only Terrarium-styled properties balloon for layers without Google
   * Earth-enabled forms, rendering the placemark's title, GeoID, coordinates, and [propertyKeys]
   * from `<ExtendedData>` without loading the interactive form iframe.
   */
  fun buildReadOnlyPropertiesHtml(
    surveyTitle: String,
    datasetName: String,
    propertyKeys: List<String>,
  ): String {
    val cleanDataset = datasetName.trim().ifEmpty { "Map feature" }
    val cleanSurvey = surveyTitle.trim()
    val eyebrow =
      if (cleanSurvey.isNotEmpty() && !cleanSurvey.equals(cleanDataset, ignoreCase = true)) {
        "$cleanDataset • $cleanSurvey"
      } else {
        cleanDataset
      }
    val rowsHtml =
      propertyKeys.joinToString("\n") { key ->
        val label = escapeXml(formatPropertyLabel(key))
        """        <tr data-c1-prop-row="true"><th>$label</th><td data-c1-prop-val="true">${'$'}[$key]</td></tr>"""
      }
    val fallbackTitleJs = jsStringLiteral(cleanDataset)
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
  html, body { margin: 0; padding: 0; width: 100%; background: #FFFFFF; color: #1F1F1F; font-family: Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; font-size: 13px; line-height: 1.45; }
  #earth-balloon-content, #balloon-content { display: block !important; width: 100% !important; margin: 0 !important; padding: 0 !important; }
  .c1-card { padding: 14px 16px; box-sizing: border-box; }
  .c1-eyebrow { font-size: 11px; font-weight: 600; color: #0B57D0; letter-spacing: 0.02em; margin-bottom: 4px; }
  .c1-title { margin: 0 0 8px 0; font-size: 16px; font-weight: 600; color: #1F1F1F; word-break: break-word; }
  .c1-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
  .c1-chip { display: inline-block; padding: 2px 8px; border-radius: 999px; background: #F0F4F9; color: #444746; font-size: 11px; font-weight: 500; }
  .c1-table { width: 100%; border-collapse: collapse; border-top: 1px solid #E0E2EC; }
  .c1-table th, .c1-table td { text-align: left; vertical-align: top; padding: 8px 0; border-bottom: 1px solid #E0E2EC; word-break: break-word; }
  .c1-table th { width: 38%; padding-right: 12px; color: #444746; font-weight: 500; font-size: 12px; }
  .c1-table td { color: #1F1F1F; font-size: 13px; }
  .c1-empty { margin: 8px 0 0 0; color: #444746; font-size: 12px; }
</style>
</head>
<body>
<div class="c1-card">
  <div class="c1-eyebrow">${escapeXml(eyebrow)}</div>
  <h1 class="c1-title" id="c1-ro-title">${'$'}[c1_entity_title]</h1>
  <div class="c1-meta" id="c1-ro-meta">
    <span class="c1-chip" id="c1-ro-geoid">${'$'}[c1_geo_id]</span>
    <span class="c1-chip" id="c1-ro-coords"><span id="c1-ro-lat">${'$'}[c1_latitude]</span>, <span id="c1-ro-lng">${'$'}[c1_longitude]</span></span>
  </div>
  <table class="c1-table" id="c1-ro-table"${if (propertyKeys.isEmpty()) """ style="display:none"""" else ""}>
    <tbody>
$rowsHtml
    </tbody>
  </table>
  <p class="c1-empty" id="c1-ro-empty"${if (propertyKeys.isNotEmpty()) """ style="display:none"""" else ""}>No recorded properties for this feature.</p>
</div>
<script>
(function () {
  function clean(el) {
    var txt = el ? (el.textContent || '').trim() : '';
    return txt.indexOf('${'$'}[') === 0 ? '' : txt;
  }
  var titleEl = document.getElementById('c1-ro-title');
  var title = clean(titleEl);
  if (!title && titleEl) {
    title = $fallbackTitleJs;
    titleEl.textContent = title;
  }
  var geoEl = document.getElementById('c1-ro-geoid');
  var geo = clean(geoEl);
  var hasGeo = !!geo && geo !== title;
  if (geoEl && !hasGeo) geoEl.style.display = 'none';
  var lat = clean(document.getElementById('c1-ro-lat'));
  var lng = clean(document.getElementById('c1-ro-lng'));
  var coordsEl = document.getElementById('c1-ro-coords');
  var hasCoords = !!lat && !!lng;
  if (coordsEl && !hasCoords) coordsEl.style.display = 'none';
  var metaEl = document.getElementById('c1-ro-meta');
  if (metaEl && !hasGeo && !hasCoords) metaEl.style.display = 'none';
  var rows = document.querySelectorAll('tr[data-c1-prop-row]');
  var visibleCount = 0;
  for (var i = 0; i < rows.length; i++) {
    var val = clean(rows[i].querySelector('[data-c1-prop-val]'));
    if (!val) {
      rows[i].style.display = 'none';
    } else {
      visibleCount++;
    }
  }
  var tableEl = document.getElementById('c1-ro-table');
  var emptyEl = document.getElementById('c1-ro-empty');
  if (tableEl) tableEl.style.display = visibleCount > 0 ? '' : 'none';
  if (emptyEl) emptyEl.style.display = visibleCount > 0 ? 'none' : '';
})();
</script>
</body>
</html>"""
  }

  internal fun formatPropertyLabel(key: String): String {
    val words = key.trim().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")
    return words.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
  }

  fun buildTemplateHtml(
    balloonAppUrl: String,
    surveyId: String,
    surveyTitle: String,
    datasetId: String,
    datasetName: String,
    formsPayloadBase64: String,
    session: SessionConfig,
  ): String {
    val script =
      buildBootstrapScript(
        balloonAppUrl = balloonAppUrl,
        surveyId = surveyId,
        surveyTitle = surveyTitle,
        datasetId = datasetId,
        datasetName = datasetName,
        formsPayloadBase64 = formsPayloadBase64,
        session = session,
      )
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
  html, body { margin: 0 !important; padding: 0 !important; width: 100% !important; background: #FFFFFF; overflow: hidden; }
  #earth-balloon-content, #balloon-content { display: block !important; width: 100% !important; margin: 0 !important; padding: 0 !important; }
  #c1-balloon-root { width: 100%; height: 560px; margin: 0; padding: 0; overflow: hidden; background: #FFFFFF; }
</style>
</head>
<body>
<div id="c1-balloon-root">
  <div id="c1-meta" style="display:none">
    <span id="c1-entity-id">$[c1_entity_id]</span>
    <span id="c1-entity-title">$[c1_entity_title]</span>
    <span id="c1-geo-id">$[c1_geo_id]</span>
    <span id="c1-lat">$[c1_latitude]</span>
    <span id="c1-lng">$[c1_longitude]</span>
  </div>
</div>
<script>
$script
</script>
</body>
</html>"""
  }

  private fun buildBootstrapScript(
    balloonAppUrl: String,
    surveyId: String,
    surveyTitle: String,
    datasetId: String,
    datasetName: String,
    formsPayloadBase64: String,
    session: SessionConfig,
  ): String {
    val inlineForms =
      if (formsPayloadBase64.length <= MAX_INLINE_HASH_FORMS_CHARS) "true" else "false"
    val extraEntries = buildSessionJsFields(session)
    return """(function () {
  function readMeta(id) {
    var el = document.getElementById(id);
    var txt = el ? (el.textContent || '').trim() : '';
    return txt.indexOf('$[') === 0 ? '' : txt;
  }
  var root = document.getElementById('c1-balloon-root');
  var balloonUrl = ${jsStringLiteral(balloonAppUrl)};
  var sharedForms = ${jsStringLiteral(formsPayloadBase64)};
  var fullParams = {
    surveyId: ${jsStringLiteral(surveyId)},
    surveyTitle: ${jsStringLiteral(surveyTitle)},
    datasetId: ${jsStringLiteral(datasetId)},
    datasetName: ${jsStringLiteral(datasetName)},
    entityId: readMeta('c1-entity-id'),
    entityLabel: readMeta('c1-entity-title'),
    geoId: readMeta('c1-geo-id'),
    lat: readMeta('c1-lat'),
    lng: readMeta('c1-lng'),
    forms: sharedForms,
    serviceUrl: ${jsStringLiteral(session.serviceUrl)}$extraEntries
  };
  var hashParams = new URLSearchParams();
  Object.keys(fullParams).forEach(function (k) {
    if (k !== 'forms' && fullParams[k]) hashParams.set(k, fullParams[k]);
  });
  if ($inlineForms && sharedForms) hashParams.set('forms', sharedForms);
  var frame = document.createElement('iframe');
  frame.setAttribute('credentialless', '');
  frame.setAttribute('allow', 'clipboard-write');
  frame.style.cssText = 'position:fixed;inset:0;width:100vw;height:100vh;border:0;display:block;';
  frame.src = balloonUrl + '#' + hashParams.toString();
  function sendInit() {
    try {
      if (frame.contentWindow) {
        frame.contentWindow.postMessage({ type: 'collectone:balloon-init', params: fullParams }, '*');
      }
    } catch (e) {}
  }
$BALLOON_OPEN_EXTERNAL_JS
  window.addEventListener('message', function (ev) {
    if (!ev || !ev.data) return;
    if (ev.data.type === 'collectone:balloon-ready') sendInit();
    if (ev.data.type === 'collectone:open-external-url') openExternal(ev.data.url);
  });
  frame.addEventListener('load', sendInit);
  root.appendChild(frame);
})();"""
  }

  private fun buildSessionJsFields(session: SessionConfig): String = buildString {
    if (!session.authToken.isNullOrBlank()) {
      append(",\n    token: ${jsStringLiteral(session.authToken)}")
    }
    if (!session.devRole.isNullOrBlank()) {
      append(",\n    devRole: ${jsStringLiteral(session.devRole)}")
    }
    if (session.collectorName.isNotBlank()) {
      append(",\n    collectorName: ${jsStringLiteral(session.collectorName)}")
    }
    if (session.collectorEmail.isNotBlank()) {
      append(",\n    collectorEmail: ${jsStringLiteral(session.collectorEmail)}")
    }
  }

  /** Escapes [text] as a JavaScript string literal safe inside an inline `<script>` tag. */
  private fun jsStringLiteral(text: String): String = jsonString(text).replace("</", "<\\/")

  private const val BALLOON_OPEN_EXTERNAL_JS =
    """  function openExternal(url) {
    if (typeof url !== 'string' || !/^https:\/\/|^http:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/i.test(url)) return;
    try {
      var a = document.createElement('a');
      a.href = url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.style.display = 'none';
      root.appendChild(a);
      var handled = !a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true }));
      root.removeChild(a);
      if (handled) return;
    } catch (e) {}
    try {
      if (typeof window.sendCommand_ === 'function') {
        window.sendCommand_('routeLink', { href: url });
        return;
      }
    } catch (e) {}
    try {
      if (typeof window.open === 'function') {
        window.open(url, '_blank', 'noopener,noreferrer');
      }
    } catch (e) {}
  }"""
}
