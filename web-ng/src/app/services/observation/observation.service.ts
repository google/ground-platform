import { Feature } from './../../shared/models/feature.model';
import { Project } from './../../shared/models/project.model';
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

import { DataStoreService } from './../data-store/data-store.service';
import { switchMap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { FeatureService } from './../feature/feature.service';
import { Injectable } from '@angular/core';
import { Observation } from '../../shared/models/observation/observation.model';
import { List } from 'immutable';

@Injectable({
  providedIn: 'root',
})
export class ObservationService {
  constructor(private dataStore: DataStoreService) {}

  observations$(
    project: Project,
    feature: Feature
  ): Observable<List<Observation>> {
    return this.dataStore.observations$(project, feature);
  }
}
