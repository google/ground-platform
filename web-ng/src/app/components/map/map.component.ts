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

import { Component } from '@angular/core';
import { Feature } from '../../shared/models/feature.model';
import { FeatureService } from '../../services/feature/feature.service';
import { Observable } from 'rxjs';
import { List } from 'immutable';

@Component({
  selector: 'ground-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.css'],
})
export class MapComponent {
  zoom = 3;
  lat = 40.767716;
  lng = -73.971714;
  features$: Observable<List<Feature>>;

  constructor(private featureService: FeatureService) {
    this.features$ = this.featureService.getFeatures$();
  }
}
