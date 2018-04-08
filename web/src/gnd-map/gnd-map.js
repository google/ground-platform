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
class GndMap extends Polymer.Element {
  constructor() {
    super();
    this.markers_ = {};
  }

  static get is() { return 'gnd-map'; }

  static get properties() {
    return {
      apiKey: {
        type: String,
        value: function() {
          return new Polymer.IronMeta({key: 'googleMapsApiKey'}).value;
        }
      },
      markerIcons: {
        type: Object
      },
      features: {
        type: Object
      }
    };
  }

  addOrUpdateMarker(featureId, lat, lng, iconId) {
    // TODO: Find a better way to share icon SVGs.
    const icon = {
      path: window.gndIcons.getSvgPath(iconId),
      fillColor: 'yellow',
      fillOpacity: 1,
      scale: 1,
      strokeColor: 'black',
      strokeWeight: .1
    }
    const position = {lat, lng};
    var marker = this.markers_[featureId];
    if (marker) {
      marker.setPosition(position);
    } else {
      this.markers_[featureId] = new google.maps.Marker({
        position: position, 
        map: this.map,
        icon: icon
      });
    }
  }

  removeMarker(featureId) {
    var marker = this.markers_[featureId];
    if (marker) {
      marker.setMap(null);
      delete markers[id];
    }
  }
}

window.customElements.define(GndMap.is, GndMap);
