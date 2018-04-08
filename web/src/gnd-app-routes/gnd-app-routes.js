/**
 * @license
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
/**
 * @customElement
 * @polymer
 */
class GndAppRoutes extends Polymer.Element {
  constructor() {
    super();
  }
  static get is() { return 'gnd-app-routes'; }

  static get properties() {
    return {
      route: {
        type: Object,
        observer: 'onRouteChange_'
      }
    };
  }

  onRouteChange_(val) {
    this.dispatchEvent(new CustomEvent('route-change', {
      detail: {route: this.parseRoute_(val.path)},
      bubbles: true, composed: true }));
  }

  parseRoute_(path) {
    let parts = path.split('/').reverse();
    parts.pop(); // Empty string before first '/'.
    let route = {mainView: parts.pop()};
    if (route.mainView == 'projects') {
      route.page = parts.length - 1;
      route.projectId = parts.pop();
      route.featureType = parts.pop();
      route.featureId = parts.pop();
    }
    return route;
  }
}

window.customElements.define(GndAppRoutes.is, GndAppRoutes);