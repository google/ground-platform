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
  subscription: Subscription = new Subscription();
  zoom = 3;
  focusedFeatureId = '';
  features$: Observable<List<Feature>>;
  icon = {
    url: 'http://maps.google.com/mapfiles/kml/paddle/red-circle.png',
    scaledSize: {
      width: 40,
      height: 40,
    },
  };
  enlargedIcon = {
    url: 'http://maps.google.com/mapfiles/kml/paddle/red-circle.png',
    scaledSize: {
      width: 60,
      height: 60,
    },
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

  featureDetails(featureId: string) {
    const primaryUrl = this.router
      .parseUrl(this.router.url)
      .root.children['primary'].toString();
    const navigationExtras: NavigationExtras = { fragment: `f=${featureId}` };
    this.router.navigate([primaryUrl], navigationExtras);
  }
}
