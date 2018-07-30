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
class GndLdfPanel extends Polymer.Element {

  static get is() { return 'gnd-ldf-panel'; }

  static get properties() {
    return {
      page: {
        type: String
      },
      riverChecked: {
      	type: Boolean
      },
      roadChecked: {
        type: Boolean
      },
      naturalPercentage: {
        type: Number
      },
      semiNaturalPercentage: {
        type: Number
      },
      degradedPercentage: {
        type: Number
      },
      plantationsPercentage: {
        type: Number
      },
    }
  }

  onButtonClick_(ev) {
  	alert(
      "River: " + this.$.riverCheckbox.checked + "\n" +
      "Natural: " + this.$.naturalInput.value + "\n" +
      "Semi-Natural: " + this.$.semiNaturalInput.value + "\n" +
      "Degraded: " + this.$.degradedInput.value + "\n" +
      "Pantations: " + this.$.plantationsInput.value + "\n" +
      "Road: " + this.$.roadCheckbox.checked + "\n"
    );
  }

  onCheckedRiverChanged_(ev) {
  	// alert(this.riverChecked);
  }
}

window.customElements.define(GndLdfPanel.is, GndLdfPanel);