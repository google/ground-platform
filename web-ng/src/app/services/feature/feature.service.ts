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
import { Project } from './../../shared/models/project.model';
import { ProjectService } from './../project/project.service';
import { Injectable } from '@angular/core';
import { Feature, LocationFeature } from '../../shared/models/feature.model';
import { List } from 'immutable';
import firebase from 'firebase/app';

@Injectable({
  providedIn: 'root',
})
export class FeatureService {
  private features$: Observable<List<Feature>>;
  private selectedFeatureId$ = new ReplaySubject<string>(1);
  private selectedFeature$: Observable<Feature>;

  constructor(
    private dataStore: DataStoreService,
    private projectService: ProjectService
  ) {
    this.features$ = projectService
      .getActiveProject$()
      .pipe(
        switchMap(project =>
          project.isUnsavedNew() ? List() : dataStore.features$(project)
        )
      );

    this.selectedFeature$ = this.selectedFeatureId$.pipe(
      switchMap(featureId =>
        projectService
          .getActiveProject$()
          .pipe(
            switchMap(project =>
              this.dataStore.loadFeature$(project.id, featureId)
            )
          )
      )
    );
  }

  getFeatures$(): Observable<List<Feature>> {
    return this.features$;
  }

  selectFeature(featureId: string) {
    this.selectedFeatureId$.next(featureId);
  }

  getSelectedFeature$(): Observable<Feature> {
    return this.selectedFeature$;
  }

  async addPoint(
    lat: number,
    lng: number,
    layerId: string
  ): Promise<Feature | null> {
    // TODO: Update to use `await firstValueFrom(getActiveProject$()` when
    // upgrading to RxJS 7.
    const project = await this.projectService
      .getActiveProject$()
      .pipe(take(1))
      .toPromise();
    return await this.addPointInternal(project, lat, lng, layerId);
  }

  async updatePoint(feature: Feature): Promise<void> {
    // TODO: Update to use `await firstValueFrom(getActiveProject$()` when
    // upgrading to RxJS 7.
    const project = await this.projectService
      .getActiveProject$()
      .pipe(take(1))
      .toPromise();
    return await this.updatePointInternal(project, feature);
  }

  private async addPointInternal(
    project: Project,
    lat: number,
    lng: number,
    layerId: string
  ): Promise<Feature | null> {
    if (!(project.layers || new Map()).get(layerId)) {
      return null;
    }
    const newFeature = new LocationFeature(
      this.dataStore.generateId(),
      layerId,
      new firebase.firestore.GeoPoint(lat, lng)
    );
    await this.dataStore.updateFeature(project.id, newFeature);
    return newFeature;
  }

  private async updatePointInternal(project: Project, feature: Feature) {
    if (project.layers.isEmpty()) {
      return;
    }
    await this.dataStore.updateFeature(project.id, feature);
  }
}
