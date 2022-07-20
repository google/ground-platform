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
import { LocationOfInterest } from './../../shared/models/loi.model';
import { Survey } from './../../shared/models/survey.model';
import { Injectable } from '@angular/core';
import { Observation } from '../../shared/models/observation/observation.model';
import { List, Map } from 'immutable';
import { switchMap } from 'rxjs/operators';
import { SurveyService } from '../survey/survey.service';
import { LocationOfInterestService } from '../loi/loi.service';
import { LoadingState } from '../loading-state.model';
import { AuditInfo } from '../../shared/models/audit-info.model';
import { AuthService } from './../../services/auth/auth.service';
import { User } from '../../shared/models/user.model';
import { Response } from '../../shared/models/observation/response.model';
import { NavigationService } from '../navigation/navigation.service';

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
    surveyService: SurveyService,
    loiService: LocationOfInterestService,
    authService: AuthService
  ) {
    this.subscription.add(
      this.selectedObservationId$
        .pipe(
          switchMap(observationId =>
            surveyService.getActiveSurvey$().pipe(
              switchMap(survey =>
                loiService.getSelectedLocationOfInterest$().pipe(
                  switchMap(loi =>
                    authService.getUser$().pipe(
                      switchMap(user => {
                        if (
                          observationId === NavigationService.OBSERVATION_ID_NEW
                        ) {
                          return of(
                            this.createNewObservation(user, survey, loi)
                          );
                        }
                        return this.dataStore.loadObservation$(
                          survey,
                          loi,
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
    user: User,
    survey: Survey,
    loi: LocationOfInterest
  ): Observation | LoadingState {
    if (!user) {
      throw Error('Login required to create new observation.');
    }
    const form = survey
      .getLayer(loi.layerId)!
      .tasks?.first(/*notSetValue=*/ null);
    if (!form) {
      throw Error(`No form in layer ${loi.layerId}`);
    }
    const newObservationId = this.dataStore.generateId();
    const auditInfo = new AuditInfo(
      user,
      new Date(),
      this.dataStore.getServerTimestamp()
    );
    return new Observation(
      newObservationId,
      loi.id,
      loi.layerId,
      form!,
      auditInfo,
      auditInfo,
      Map<string, Response>([])
    );
  }

  observations$(
    survey: Survey,
    loi: LocationOfInterest
  ): Observable<List<Observation>> {
    return this.dataStore.observations$(survey, loi);
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
