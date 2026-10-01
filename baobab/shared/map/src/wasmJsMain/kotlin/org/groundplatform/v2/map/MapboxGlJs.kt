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
package org.groundplatform.v2.map

import kotlin.js.Promise

// Thin bindings to Mapbox GL JS (v1+), loaded by the host page as the global `mapboxgl`. Style
// objects cross the boundary as JSON strings built by MapboxStyleSpec, so these stay one-liners.

/** A map plus the DOM elements that hold it; opaque to Kotlin. */
internal external interface MapboxHost : JsAny

@JsFun("() => typeof window.mapboxgl !== 'undefined' && !!window.mapboxgl.Map")
internal external fun isMapboxGlAvailable(): Boolean

@JsFun("() => window.devicePixelRatio || 1") internal external fun devicePixelRatio(): Double

/**
 * Creates a display-only map in a fixed-position `div` below the Compose canvas. The outer element
 * clips to the visible part of the map's layout; the inner one is the full layout. [style] is a
 * style URL or style JSON (see [MapboxStyleSpec.style]).
 */
@JsFun(
  """(style, lng, lat, zoom, bearing, pitch) => {
    const outer = document.createElement('div');
    outer.style.cssText = 'position:fixed;left:0;top:0;width:0;height:0;overflow:hidden;' +
      'pointer-events:none;z-index:1;visibility:hidden;';
    const inner = document.createElement('div');
    inner.style.cssText = 'position:absolute;left:0;top:0;width:1px;height:1px;';
    outer.appendChild(inner);
    document.body.appendChild(outer);
    const map = new window.mapboxgl.Map({
      container: inner, style: style.charAt(0) === '{' ? JSON.parse(style) : style,
      center: [lng, lat], zoom: zoom, bearing: bearing, pitch: pitch, interactive: false,
      attributionControl: false, fadeDuration: 0,
    });
    const host = { outer: outer, inner: inner, map: map, w: 0, h: 0, ready: false, waiters: [] };
    map.on('style.load', () => {
      host.ready = true;
      const waiters = host.waiters;
      host.waiters = [];
      waiters.forEach((resolve) => resolve(null));
    });
    map.on('error', (e) => console.warn('GroundMap:', e && e.error ? e.error.message : e));
    return host;
  }"""
)
internal external fun createMapboxHost(
  style: String,
  lng: Double,
  lat: Double,
  zoom: Double,
  bearing: Double,
  pitch: Double,
): MapboxHost

/** Resolves once the current style has loaded. */
@JsFun(
  """(h) => h.ready ? Promise.resolve(null) : new Promise((resolve) => h.waiters.push(resolve))"""
)
internal external fun mapboxStyleReady(host: MapboxHost): Promise<JsAny?>

/** Replaces the style with [style], a style URL or style JSON. */
@JsFun(
  """(h, style) => {
    h.ready = false;
    h.map.setStyle(style.charAt(0) === '{' ? JSON.parse(style) : style, { diff: false });
  }"""
)
internal external fun mapboxSetStyle(host: MapboxHost, style: String)

/** Positions the clip (`c*`) and map (`i*`) elements, in CSS px; resizes the map if needed. */
@JsFun(
  """(h, visible, cl, ct, cw, ch, il, it, iw, ih) => {
    const o = h.outer.style, i = h.inner.style;
    o.visibility = visible ? 'visible' : 'hidden';
    o.left = cl + 'px'; o.top = ct + 'px'; o.width = cw + 'px'; o.height = ch + 'px';
    i.left = il + 'px'; i.top = it + 'px'; i.width = iw + 'px'; i.height = ih + 'px';
    if (Math.abs(h.w - iw) > 0.5 || Math.abs(h.h - ih) > 0.5) {
      h.w = iw; h.h = ih;
      h.map.resize();
    }
  }"""
)
internal external fun mapboxSetBounds(
  host: MapboxHost,
  visible: Boolean,
  clipLeft: Double,
  clipTop: Double,
  clipWidth: Double,
  clipHeight: Double,
  left: Double,
  top: Double,
  width: Double,
  height: Double,
)

@JsFun(
  """(h, lng, lat, zoom, bearing, pitch) =>
    h.map.jumpTo({ center: [lng, lat], zoom: zoom, bearing: bearing, pitch: pitch })"""
)
internal external fun mapboxJumpTo(
  host: MapboxHost,
  lng: Double,
  lat: Double,
  zoom: Double,
  bearing: Double,
  pitch: Double,
)

@JsFun("(h, id, sourceJson) => h.map.addSource(id, JSON.parse(sourceJson))")
internal external fun mapboxAddSource(host: MapboxHost, id: String, sourceJson: String)

@JsFun(
  """(h, id, dataJson) => {
    const source = h.map.getSource(id);
    if (source) source.setData(JSON.parse(dataJson));
  }"""
)
internal external fun mapboxSetSourceData(host: MapboxHost, id: String, dataJson: String)

@JsFun("(h, id) => { if (h.map.getSource(id)) h.map.removeSource(id); }")
internal external fun mapboxRemoveSource(host: MapboxHost, id: String)

/** Adds a layer below [beforeId], or below [fallbackBeforeId] when [beforeId] is absent. */
@JsFun(
  """(h, layerJson, beforeId, fallbackBeforeId) => {
    const before = beforeId && h.map.getLayer(beforeId) ? beforeId
      : (h.map.getLayer(fallbackBeforeId) ? fallbackBeforeId : undefined);
    h.map.addLayer(JSON.parse(layerJson), before);
  }"""
)
internal external fun mapboxAddLayer(
  host: MapboxHost,
  layerJson: String,
  beforeId: String?,
  fallbackBeforeId: String,
)

/** Updates a same-type layer in place: filter, then paint and layout (removed keys reset). */
@JsFun(
  """(h, oldJson, newJson) => {
    const m = h.map, prev = JSON.parse(oldJson), next = JSON.parse(newJson), id = next.id;
    m.setFilter(id, next.filter === undefined ? null : next.filter);
    [['paint', (k, v) => m.setPaintProperty(id, k, v)],
     ['layout', (k, v) => m.setLayoutProperty(id, k, v)]].forEach(([group, set]) => {
      const a = prev[group] || {}, b = next[group] || {};
      Object.keys(a).forEach((k) => { if (!(k in b)) set(k, undefined); });
      Object.keys(b).forEach((k) => {
        if (JSON.stringify(a[k]) !== JSON.stringify(b[k])) set(k, b[k]);
      });
    });
  }"""
)
internal external fun mapboxUpdateLayer(
  host: MapboxHost,
  oldLayerJson: String,
  newLayerJson: String,
)

@JsFun("(h, id) => { if (h.map.getLayer(id)) h.map.removeLayer(id); }")
internal external fun mapboxRemoveLayer(host: MapboxHost, id: String)

/** Rasterizes [svg] at 4× and registers it as image [id]; resolves when done or on failure. */
@JsFun(
  """(h, id, svg) => new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      const scale = 4;
      const w = Math.max(1, Math.round(img.width * scale));
      const ht = Math.max(1, Math.round(img.height * scale));
      const canvas = document.createElement('canvas');
      canvas.width = w; canvas.height = ht;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, w, ht);
      if (h.map.hasImage(id)) h.map.removeImage(id);
      h.map.addImage(id, ctx.getImageData(0, 0, w, ht), { pixelRatio: scale });
      resolve(null);
    };
    img.onerror = () => { console.warn('GroundMap: could not load icon ' + id); resolve(null); };
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  })"""
)
internal external fun mapboxAddIcon(host: MapboxHost, id: String, svg: String): Promise<JsAny?>

@JsFun("(h, id) => { if (h.map.hasImage(id)) h.map.removeImage(id); }")
internal external fun mapboxRemoveIcon(host: MapboxHost, id: String)

/**
 * The topmost feature with a `featureIdProperty` within [radius] CSS px of ([x], [y]), among the
 * newline-separated [layerIds], as "layerId\nfeatureId"; empty when nothing is hit.
 */
@JsFun(
  """(h, x, y, radius, layerIds, featureIdProperty) => {
    if (!h.ready) return '';
    const layers = layerIds.split('\n').filter((id) => id && h.map.getLayer(id));
    if (layers.length === 0) return '';
    const hits = h.map.queryRenderedFeatures(
      [[x - radius, y - radius], [x + radius, y + radius]], { layers: layers });
    for (const f of hits) {
      const id = f.properties && f.properties[featureIdProperty];
      if (id !== undefined && id !== null) return f.layer.id + '\n' + id;
    }
    return '';
  }"""
)
internal external fun mapboxQueryFeature(
  host: MapboxHost,
  x: Double,
  y: Double,
  radius: Double,
  layerIds: String,
  featureIdProperty: String,
): String

@JsFun("(h) => { h.map.remove(); h.outer.remove(); }")
internal external fun mapboxDestroy(host: MapboxHost)
