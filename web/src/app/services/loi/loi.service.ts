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
import { List, Map } from 'immutable';
import { Observable, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

import { Coordinate } from 'app/models/geometry/coordinate';
import { GeometryType } from 'app/models/geometry/geometry';
import { LinearRing } from 'app/models/geometry/linear-ring';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';
import { LocationOfInterest } from 'app/models/loi.model';
import {
  Survey,
  SurveyDataVisibility,
  SurveyState,
} from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';

@Injectable({
  providedIn: 'root',
})
export class LocationOfInterestService {
  constructor(
    private authService: AuthService,
    private dataStore: DataStoreService,
    private surveyService: SurveyService
  ) {}

  getLocationsOfInterest$(
    survey: Survey
  ): Observable<List<LocationOfInterest>> {
    return this.authService.getUser$().pipe(
      switchMap(user =>
        !survey || survey.state === SurveyState.UNSAVED
          ? of(List<LocationOfInterest>())
          : this.dataStore.getAccessibleLois$(
              survey,
              user.id,
              this.surveyService.canManageSurvey(survey) ||
                survey.dataVisibility ===
                  SurveyDataVisibility.ALL_SURVEY_PARTICIPANTS
            )
      ),
      map(lois =>
        lois
          .map(loi => ({
            loi,
            name: LocationOfInterestService.getDisplayName(loi),
          }))
          .sort((a, b) => a.name.localeCompare(b.name))
          .map(({ loi }) => loi)
      )
    );
  }

  getPredefinedLoisByJobId$(
    survey: Survey,
    jobId: string
  ): Observable<List<LocationOfInterest>> {
    return this.getLocationsOfInterest$(survey).pipe(
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

  /**
   * Adds a new point LOI to Firestore for the given survey and job.
   */
  async addPoint(
    lat: number,
    lng: number,
    jobId: string,
    surveyId: string
  ): Promise<LocationOfInterest | null> {
    const id = this.dataStore.generateId();
    const loi = new LocationOfInterest(
      id,
      jobId,
      new Point(new Coordinate(lng, lat)),
      Map<string, string | number>(),
      /* customId= */ '',
      /* predefined= */ false,
      /* submissionCount= */ 0
    );
    await this.dataStore.addOrUpdateLoi(surveyId, loi);
    return loi;
  }

  /**
   * Adds a new polygon LOI to Firestore for the given survey and job.
   * @param vertices Ordered list of LatLng vertices (the ring is closed automatically).
   */
  async addPolygon(
    vertices: google.maps.LatLng[],
    jobId: string,
    surveyId: string
  ): Promise<LocationOfInterest | null> {
    if (vertices.length < 3) return null;
    const id = this.dataStore.generateId();
    // Close the ring by repeating the first vertex at the end.
    const coords = [...vertices, vertices[0]].map(
      v => new Coordinate(v.lng(), v.lat())
    );
    const shell = new LinearRing(List(coords));
    const polygon = new Polygon(shell, List());
    const loi = new LocationOfInterest(
      id,
      jobId,
      polygon,
      Map<string, string | number>(),
      /* customId= */ '',
      /* predefined= */ false,
      /* submissionCount= */ 0
    );
    await this.dataStore.addOrUpdateLoi(surveyId, loi);
    return loi;
  }

  async updatePoint(_loi: LocationOfInterest): Promise<void> {
    throw new Error('Editing LOIs via web app not yet supported');
  }
}
