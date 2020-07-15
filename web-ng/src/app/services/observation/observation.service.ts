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
import {
  of,
  Observable,
  ReplaySubject,
  Subscription,
  BehaviorSubject,
} from 'rxjs';
import { Feature } from './../../shared/models/feature.model';
import { Project } from './../../shared/models/project.model';
import { Injectable } from '@angular/core';
import { Observation } from '../../shared/models/observation/observation.model';
import { List, Map } from 'immutable';
import { switchMap } from 'rxjs/operators';
import { ProjectService } from '../project/project.service';
import { FeatureService } from '../feature/feature.service';
import { LoadingState } from '../loading-state.model';
import { AuditInfo } from '../../shared/models/audit-info.model';
import { AuthService } from './../../services/auth/auth.service';
import { User } from '../../shared/models/user.model';
import { Response } from '../../shared/models/observation/response.model';
import { Form } from '../../shared/models/form/form.model';

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
    featureService: FeatureService,
    authService: AuthService
  ) {
    this.subscription.add(
      this.selectedObservationId$
        .pipe(
          switchMap(observationId =>
            projectService.getActiveProject$().pipe(
              switchMap(project =>
                featureService.getSelectedFeature$().pipe(
                  switchMap(feature =>
                    authService.getUser$().pipe(
                      switchMap(user => {
                        if (observationId === ':new') {
                          return of(
                            this.createNewObservation(user, project, feature)
                          );
                        }
                        return this.dataStore.loadObservation$(
                          project,
                          feature,
                          observationId
                        );
                      })
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

  createNewObservation(
    user: User | null | undefined,
    project: Project,
    feature: Feature
  ): Observation | LoadingState {
    if (user === null || user === undefined) {
      return LoadingState.LOADING_FAILED;
    }
    const form: Form | undefined = project
      .getLayer(feature.layerId)!
      .forms?.first();
    if (form === undefined) {
      return LoadingState.LOADING_FAILED;
    }
    const newObservationId = this.dataStore.generateId();
    const auditInfo = new AuditInfo(
      user,
      new Date(),
      this.dataStore.getServerTimestamp()
    );
    return new Observation(
      newObservationId,
      feature.id,
      form,
      auditInfo,
      auditInfo,
      Map<string, Response>([])
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
