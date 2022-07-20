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
import { switchMap, take } from 'rxjs/operators';
import { Observable, ReplaySubject } from 'rxjs';
import { Survey } from './../../shared/models/survey.model';
import { SurveyService } from './../survey/survey.service';
import { Injectable } from '@angular/core';
import {
  LocationOfInterest,
  PointOfInterest,
} from '../../shared/models/loi.model';
import { List } from 'immutable';
import firebase from 'firebase/app';

@Injectable({
  providedIn: 'root',
})
export class LocationOfInterestService {
  private lois$: Observable<List<LocationOfInterest>>;
  private selectedLocationOfInterestId$ = new ReplaySubject<string>(1);
  private selectedLocationOfInterest$: Observable<LocationOfInterest>;

  constructor(
    private dataStore: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.lois$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          survey.isUnsavedNew() ? List() : dataStore.lois$(survey)
        )
      );

    this.selectedLocationOfInterest$ = this.selectedLocationOfInterestId$.pipe(
      switchMap(loiId =>
        surveyService
          .getActiveSurvey$()
          .pipe(
            switchMap(survey =>
              this.dataStore.loadLocationOfInterest$(survey.id, loiId)
            )
          )
      )
    );
  }

  getLocationsOfInterest$(): Observable<List<LocationOfInterest>> {
    return this.lois$;
  }

  selectLocationOfInterest(loiId: string) {
    this.selectedLocationOfInterestId$.next(loiId);
  }

  getSelectedLocationOfInterest$(): Observable<LocationOfInterest> {
    return this.selectedLocationOfInterest$;
  }

  async addPoint(
    lat: number,
    lng: number,
    layerId: string
  ): Promise<LocationOfInterest | null> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return await this.addPointInternal(survey, lat, lng, layerId);
  }

  async updatePoint(loi: LocationOfInterest): Promise<void> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await this.surveyService
      .getActiveSurvey$()
      .pipe(take(1))
      .toPromise();
    return await this.updatePointInternal(survey, loi);
  }

  private async addPointInternal(
    survey: Survey,
    lat: number,
    lng: number,
    layerId: string
  ): Promise<LocationOfInterest | null> {
    if (!(survey.layers || new Map()).get(layerId)) {
      return null;
    }
    const newLocationOfInterest = new PointOfInterest(
      this.dataStore.generateId(),
      layerId,
      new firebase.firestore.GeoPoint(lat, lng)
    );
    await this.dataStore.updateLocationOfInterest(
      survey.id,
      newLocationOfInterest
    );
    return newLocationOfInterest;
  }

  private async updatePointInternal(survey: Survey, loi: LocationOfInterest) {
    if (survey.layers.isEmpty()) {
      return;
    }
    await this.dataStore.updateLocationOfInterest(survey.id, loi);
  }
}
