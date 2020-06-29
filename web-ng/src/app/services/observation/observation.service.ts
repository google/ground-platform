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
import { Observable, ReplaySubject, Subscription, BehaviorSubject } from 'rxjs';
import { Feature } from './../../shared/models/feature.model';
import { Project } from './../../shared/models/project.model';
import { Injectable } from '@angular/core';
import { Observation } from '../../shared/models/observation/observation.model';
import { List } from 'immutable';
import { switchMap } from 'rxjs/operators';
import { ProjectService } from '../project/project.service';
import { FeatureService } from '../feature/feature.service';
import { LoadingState } from '../loading-state.model';

@Injectable({
  providedIn: 'root',
})
export class ObservationService {
  // TODO: Move selected observation into side panel component where it is
  // used.
  private selectedObservationId$ = new ReplaySubject<string>(1);
  private selectedObservation$ = new BehaviorSubject<
    Observation | LoadingState
  >(LoadingState.NOT_LOADED);
  private subscription = new Subscription();

  constructor(
    private dataStore: DataStoreService,
    projectService: ProjectService,
    featureService: FeatureService
  ) {
    this.subscription.add(
      this.selectedObservationId$
        .pipe(
          switchMap(observationId =>
            projectService
              .getActiveProject$()
              .pipe(
                switchMap(project =>
                  featureService
                    .getSelectedFeature$()
                    .pipe(
                      switchMap(feature =>
                        this.dataStore.loadObservation$(
                          project,
                          feature,
                          observationId
                        )
                      )
                    )
                )
              )
          )
        )
        .subscribe(o => this.selectedObservation$.next(o))
    );
  }

  observations$(
    project: Project,
    feature: Feature
  ): Observable<List<Observation>> {
    return this.dataStore.observations$(project, feature);
  }

  selectObservation(observationId: string) {
    this.selectedObservation$.next(LoadingState.LOADING);
    this.selectedObservationId$.next(observationId);
  }

  deselectObservation() {
    this.selectedObservation$.next(LoadingState.NOT_LOADED);
  }

  getSelectedObservation$(): BehaviorSubject<Observation | LoadingState> {
    return this.selectedObservation$;
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
