/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, AfterViewInit, ViewChild, ElementRef } from '@angular/core';

@Component({
  selector: 'ground-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.css'],
})
export class MapComponent implements AfterViewInit {
  map!: google.maps.Map;
  @ViewChild('map', { static: false }) mapElement!: ElementRef;

  ngAfterViewInit() {
    this.initializeMap();
  }

  initializeMap() {
    const lngLat = new google.maps.LatLng(40.767716, -73.971714);
    const mapOptions: google.maps.MapOptions = {
      center: lngLat,
      zoom: 3,
      fullscreenControl: false,
      mapTypeControl: false,
      streetViewControl: false,
      mapTypeId: google.maps.MapTypeId.HYBRID,
    };
    this.map = new google.maps.Map(this.mapElement.nativeElement, mapOptions);
  }
}
