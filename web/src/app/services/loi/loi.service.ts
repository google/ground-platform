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

import {DataStoreService} from 'app/services/data-store/data-store.service';
import {switchMap} from 'rxjs/operators';
import {firstValueFrom, Observable, of, ReplaySubject} from 'rxjs';
import {Survey} from 'app/models/survey.model';
import {SurveyService} from 'app/services/survey/survey.service';
import {Injectable} from '@angular/core';
import {
  GenericLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {List, Map as ImmutableMap} from 'immutable';
import {Point} from 'app/models/geometry/point';
import {Coordinate} from 'app/models/geometry/coordinate';

@Injectable({
  providedIn: 'root',
})
export class LocationOfInterestService {
  private lois$: Observable<List<LocationOfInterest>>;
  private selectedLocationOfInterestId$ = new ReplaySubject<string>(1);
  private selectedLocationOfInterest$: Observable<LocationOfInterest>;

  // Properties in this order are used to give a LOI a name, otherwise it will
  // be given a default name.
  private static _inferredNamesProperties = [
    'label',
    'caption',
    'name',
    'id',
    'code',
    'key',
  ];

  constructor(
    private dataStore: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.lois$ = surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey =>
          survey.isUnsavedNew()
            ? of(List<LocationOfInterest>())
            : dataStore.lois$(survey)
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

  private static getAnonymousDisplayName({
    loi,
    index,
  }: {
    loi: LocationOfInterest;
    index: number;
  }): string {
    const geometryType = loi.geometry?.geometryType
      .toLocaleLowerCase()
      .replace(/_/g, ' ');
    return `Unnamed ${geometryType} ${index + 1}`;
  }

  static getLoisWithNames(
    lois: List<LocationOfInterest>
  ): List<LocationOfInterest> {
    return lois.map((loi, index) => {
      let displayName =
        this.getLoiNameFromProperties(loi) ||
        this.getAnonymousDisplayName({loi, index});
      return {
        ...loi,
        name: displayName,
      };
    });
  }

  static getLoiNameFromProperties(loi: LocationOfInterest): string | null {
    const properties = loi.properties;
    let applicableProperties: string[] = [];

    if (properties) {
      applicableProperties = [...properties.keys()].filter(property => {
        return this._inferredNamesProperties.includes(property);
      });
    }

    if (applicableProperties.length > 0) {
      let loiName = '';
      this._inferredNamesProperties.every(name => {
        if (applicableProperties.includes(name)) {
          loiName = properties!.get(name) as string;
          return false;
        }
        return true;
      });
      return loiName;
    }
    return null;
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
    jobId: string
  ): Promise<LocationOfInterest | null> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await firstValueFrom(this.surveyService.getActiveSurvey$());
    return await this.addPointInternal(survey, lat, lng, jobId);
  }

  async updatePoint(loi: LocationOfInterest): Promise<void> {
    // TODO: Update to use `await firstValueFrom(getActiveSurvey$()` when
    // upgrading to RxJS 7.
    const survey = await firstValueFrom(this.surveyService.getActiveSurvey$());
    return await this.updatePointInternal(survey, loi);
  }

  private async addPointInternal(
    survey: Survey,
    lat: number,
    lng: number,
    jobId: string
  ): Promise<LocationOfInterest | null> {
    if (!(survey.jobs || new Map()).get(jobId)) {
      return null;
    }
    const newLocationOfInterest = new GenericLocationOfInterest(
      this.dataStore.generateId(),
      jobId,
      new Point(new Coordinate(lat, lng)),
      ImmutableMap()
    );
    await this.dataStore.updateLocationOfInterest(
      survey.id,
      newLocationOfInterest
    );
    return newLocationOfInterest;
  }

  private async updatePointInternal(survey: Survey, loi: LocationOfInterest) {
    if (survey.jobs.isEmpty()) {
      return;
    }
    await this.dataStore.updateLocationOfInterest(survey.id, loi);
  }
}
