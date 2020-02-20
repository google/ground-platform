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

import { Component, OnInit } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Feature } from '../../shared/models/feature.model';
import { FeatureService } from '../../services/feature/feature.service';
import { Observable, Subscription } from 'rxjs';
import { List } from 'immutable';
import { Router, ActivatedRoute, NavigationExtras } from '@angular/router';

@Component({
  selector: 'ground-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.css'],
})
export class MapComponent implements OnInit {
  private subscription: Subscription = new Subscription();
  focusedFeatureId = '';
  features$: Observable<List<Feature>>;
  mapOptions: google.maps.MapOptions = {
    center: new google.maps.LatLng(40.767716, -73.971714),
    zoom: 3,
    fullscreenControl: false,
    mapTypeControl: false,
    streetViewControl: false,
    mapTypeId: google.maps.MapTypeId.HYBRID,
  };

  constructor(
    private featureService: FeatureService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.features$ = this.featureService.getFeatures$();
  }

  ngOnInit() {
    this.subscription.add(
      this.route.fragment.subscribe(fragment => {
        const params = new HttpParams({ fromString: fragment });
        if (params.get('f')) {
          this.focusedFeatureId = params.get('f')!;
        }
      })
    );
  }

  onFeatureClick(featureId: string) {
    // TODO: refactor URL read/write logic into its own service.
    const primaryUrl = this.router
      .parseUrl(this.router.url)
      .root.children['primary'].toString();
    const navigationExtras: NavigationExtras = { fragment: `f=${featureId}` };
    this.router.navigate([primaryUrl], navigationExtras);
  }

  createMarkerOptions(
    feature: Feature,
    focusedFeatureId: string
  ): google.maps.MarkerOptions {
    // Icon is not yet an input for <map-marker>, this is the only way to change icon for now.
    // Consider break this down when more inputs are available for <map-marker>.
    const icon = {
      url: 'assets/img/marker-icon.png',
      scaledSize: {
        width: 40,
        height: 40,
      },
    };
    const enlargedIcon = {
      url: 'assets/img/marker-icon.png',
      scaledSize: {
        width: 60,
        height: 60,
      },
    };
    return {
      position: new google.maps.LatLng(
        feature.location.latitude,
        feature.location.longitude
      ),
      icon: feature.id === focusedFeatureId ? enlargedIcon : icon,
    } as google.maps.MarkerOptions;
  }
}
