/**
 * Copyright 2020 Google LLC
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
import { Observable } from 'rxjs';
import { FeatureService } from '../../services/feature/feature.service';
import { Feature } from '../../shared/models/feature.model';
import { Observation } from '../../shared/models/observation/observation.model';
import { ObservationService } from '../../services/observation/observation.service';

@Component({
  selector: 'ground-side-panel',
  templateUrl: './side-panel.component.html',
  styleUrls: ['./side-panel.component.css'],
})
export class SidePanelComponent {
  readonly selectedFeature$: Observable<Feature>;
  readonly selectedObservation$: Observable<Observation>;
  readonly lang: string;

  constructor(
    private featureService: FeatureService,
    private observationService: ObservationService
  ) {
    console.log("reload SidePanelComponent")
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    this.selectedFeature$ = this.featureService.getSelectedFeature$();
    this.selectedObservation$ = this.observationService.getSelectedObservation$();
  }
}
