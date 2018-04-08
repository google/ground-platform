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
 * @author gmiceli@google.com (Gino Miceli)
 */ 

/**
 * @customElement
 * @polymer
 */
class GndPanelOverlay extends Polymer.Element {

  // TODO: Rename GndPanel?
  static get is() { return 'gnd-panel-overlay'; }

  static get properties() {
    return {
      page: {
        type: String
      },
      basePath: {
        type: String
      },
      projectDefn: {
        type: Object
      },
      selectedFeatureType: {
        type: String
      },
      selectedFeatureTypeDefn: {
        type: Object,
        computed: 'getFeatureTypeDefn_(projectDefn, selectedFeatureType)'
      }
    }
  }

  getFeatureTypeDefn_(projectDefn, selectedFeatureType) {
    return projectDefn.featureTypes.find(e => e.pathKey === selectedFeatureType);
  }
}

window.customElements.define(GndPanelOverlay.is, GndPanelOverlay);