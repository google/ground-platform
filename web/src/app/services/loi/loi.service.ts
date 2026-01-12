/**
 * Copyright 2020 The Ground Authors.
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

import { Injectable } from '@angular/core';
import { List } from 'immutable';
import { Observable, ReplaySubject, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

import { GeometryType } from 'app/models/geometry/geometry';
import { LocationOfInterest } from 'app/models/loi.model';
import { SurveyDataVisibility, SurveyState } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';

@Injectable({
  providedIn: 'root',
})
export class LocationOfInterestService {
  private lois$: Observable<List<LocationOfInterest>>;
  private selectedLoiId$ = new ReplaySubject<string>(1);
  private selectedLoi$: Observable<LocationOfInterest>;

  constructor(
    private authService: AuthService,
    private dataStore: DataStoreService,
    private surveyService: SurveyService
  ) {
    this.lois$ = this.authService
      .getUser$()
      .pipe(
        switchMap(user =>
          this.surveyService
            .getActiveSurvey$()
            .pipe(
              switchMap(survey =>
                !survey || survey.state === SurveyState.UNSAVED
                  ? of(List<LocationOfInterest>())
                  : this.dataStore.getAccessibleLois$(
                      survey,
                      user.id,
                      this.surveyService.canManageSurvey() ||
                        survey.dataVisibility ===
                          SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
                    )
              )
            )
        )
      );

    this.selectedLoi$ = this.selectedLoiId$.pipe(
      switchMap(loiId =>
        this.lois$.pipe(map(lois => lois.find(({ id }) => id === loiId)!))
      )
    );
  }

  getLocationsOfInterest$(): Observable<List<LocationOfInterest>> {
    return this.lois$.pipe(
      map(lois =>
        lois.sort((a, b) =>
          LocationOfInterestService.getDisplayName(a).localeCompare(
            LocationOfInterestService.getDisplayName(b)
          )
        )
      )
    );
  }

  selectLocationOfInterest(loiId: string) {
    this.selectedLoiId$.next(loiId);
  }

  getSelectedLocationOfInterest$(): Observable<LocationOfInterest> {
    return this.selectedLoi$;
  }

  getPredefinedLoisByJobId$(
    jobId: string
  ): Observable<List<LocationOfInterest>> {
    return this.getLocationsOfInterest$().pipe(
      map(lois =>
        lois.filter(loi => loi.jobId === jobId && loi.predefined !== false)
      )
    );
  }

  /** A label for a given geometry type. Defaults to 'Polygon'. */
  private static geometryTypeLabel(geometryType?: GeometryType): string {
    switch (geometryType) {
      case GeometryType.POINT:
        return 'Point';
      case GeometryType.MULTI_POLYGON:
      case GeometryType.POLYGON:
        return 'Area';
      default:
        return 'Geometry';
    }
  }

  static getDefaultName(loi: LocationOfInterest): string {
    const geometryType = loi.geometry?.geometryType;
    return (
      'Unnamed ' +
      LocationOfInterestService.geometryTypeLabel(
        geometryType
      ).toLocaleLowerCase()
    );
  }

  static getDisplayName(loi: LocationOfInterest): string {
    const { customId, properties } = loi;
    const name = properties?.get('name')?.toString()?.trim() || '';
    const loiId = customId?.trim() || '';
    if (name && loiId) {
      return `${name} (${loiId})`;
    } else if (name) {
      return name;
    } else if (loiId) {
      const geometryType = LocationOfInterestService.geometryTypeLabel(
        loi.geometry!.geometryType
      );
      return `${geometryType} ${loiId}`;
    } else {
      return LocationOfInterestService.getDefaultName(loi);
    }
  }

  static getLatLngBoundsFromLois(
    lois: LocationOfInterest[]
  ): google.maps.LatLngBounds | null {
    if (!lois.length) return null;

    const bounds = new google.maps.LatLngBounds();

    for (const loi of lois) {
      loi?.geometry?.extendBounds(bounds);
    }

    return bounds;
  }

  async addPoint(
    _lat: number,
    _lng: number,
    _jobId: string
  ): Promise<LocationOfInterest | null> {
    throw new Error('Adding LOIs via web app not yet supported');
  }

  async updatePoint(_loi: LocationOfInterest): Promise<void> {
    throw new Error('Editing LOIs via web app not yet supported');
  }
}
