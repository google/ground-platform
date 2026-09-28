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

/*
 * Basemap for the Survey editor's Map layer editor.
 *
 * A dedicated, non-interactive mapboxgl.Map rendered in its own DOM container behind the
 * transparent Compose canvas. Compose owns the camera (pan / zoom gestures) and draws the layer's
 * features on top; this bridge only follows the camera via `sync(...)`. It is intentionally
 * independent from `GroundMapboxBridge`, which drives the mobile prototype's map.
 */
window.GroundLayerEditorMap = (function () {
  const CONTAINER_ID = 'layer-editor-basemap-container';
  let container = null;
  let map = null;
  let lastSize = '';
  let lastBasemap = '';

  function ensureContainer() {
    if (container) return container;
    container = document.createElement('div');
    container.id = CONTAINER_ID;
    Object.assign(container.style, {
      position: 'fixed',
      left: '0px',
      top: '0px',
      width: '0px',
      height: '0px',
      zIndex: '1',
      overflow: 'hidden',
      display: 'none',
      pointerEvents: 'none',
      backgroundColor: '#1B2A22',
      boxSizing: 'border-box',
    });
    const compose = document.getElementById('ComposeTarget');
    document.body.insertBefore(container, compose || null);
    const css = document.createElement('style');
    css.textContent =
      '#' + CONTAINER_ID + ' .mapboxgl-canvas-container, #' + CONTAINER_ID +
      ' .mapboxgl-canvas { width: 100% !important; height: 100% !important; }' +
      '#' + CONTAINER_ID + ' .mapboxgl-ctrl-logo, #' + CONTAINER_ID +
      ' .mapboxgl-ctrl-attrib { display: none !important; }';
    document.head.appendChild(css);
    return container;
  }

  function rasterSource(url) {
    return { type: 'raster', tiles: [url], tileSize: 256, maxzoom: 19 };
  }

  function buildStyle() {
    return {
      version: 8,
      name: 'Ground layer editor',
      sources: {
        satellite: rasterSource(
          'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
        ),
        terrain: rasterSource(
          'https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}'
        ),
      },
      layers: [
        { id: 'bg', type: 'background', paint: { 'background-color': '#1B2A22' } },
        { id: 'satellite', type: 'raster', source: 'satellite', layout: { visibility: 'visible' } },
        { id: 'terrain', type: 'raster', source: 'terrain', layout: { visibility: 'none' } },
      ],
    };
  }

  function applyBasemap(basemap) {
    if (!map || !map.isStyleLoaded() || basemap === lastBasemap) return;
    try {
      map.setLayoutProperty('satellite', 'visibility', basemap === 'SATELLITE' ? 'visible' : 'none');
      map.setLayoutProperty('terrain', 'visibility', basemap === 'TERRAIN' ? 'visible' : 'none');
      lastBasemap = basemap;
    } catch (e) {}
  }

  return {
    sync: function (left, top, width, height, radius, lat, lng, zoom, basemap) {
      const el = ensureContainer();
      const visible = width > 4 && height > 4 && basemap !== 'NONE';
      el.style.display = visible ? 'block' : 'none';
      if (!visible) return;
      el.style.left = left + 'px';
      el.style.top = top + 'px';
      el.style.width = width + 'px';
      el.style.height = height + 'px';
      el.style.borderRadius = radius + 'px';
      if (typeof window.mapboxgl === 'undefined') return;
      if (!map) {
        map = new window.mapboxgl.Map({
          container: el,
          style: buildStyle(),
          center: [lng, lat],
          zoom: zoom,
          interactive: false,
          attributionControl: false,
          fadeDuration: 0,
          renderWorldCopies: true,
          maxZoom: 22,
        });
        map.on('load', function () {
          lastBasemap = '';
          applyBasemap(el.dataset.basemap || 'SATELLITE');
        });
      }
      const size = width + 'x' + height;
      if (size !== lastSize) {
        lastSize = size;
        map.resize();
      }
      el.dataset.basemap = basemap;
      applyBasemap(basemap);
      map.jumpTo({ center: [lng, lat], zoom: zoom });
    },

    hide: function () {
      if (container) container.style.display = 'none';
    },
  };
})();
