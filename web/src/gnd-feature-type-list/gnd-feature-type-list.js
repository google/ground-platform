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
class GndFeatureTypeList extends Polymer.Element {

  static get is() { return 'gnd-feature-type-list'; }

  static get properties() {
    return {
      basePath: String,
      featureTypes: Array
    };
  }
}

window.customElements.define(GndFeatureTypeList.is, GndFeatureTypeList);