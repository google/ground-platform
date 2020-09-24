import { Project } from './../../shared/models/project.model';
import { firestore } from 'firebase/app';
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
import { ProjectService } from './../project/project.service';
import { Injectable } from '@angular/core';
import { Feature, LocationFeature } from '../../shared/models/feature.model';
import { List } from 'immutable';

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

  addPoint(lat: number, lng: number, layerId: string): Promise<void> {
    // TODO: Update to use `await firstValueFrom(getActiveProject$()` when
    // upgrading to RxJS 7.
    return this.projectService
      .getActiveProject$()
      .pipe(take(1))
      .toPromise()
      .then(project => this.addPointInternal(project, lat, lng, layerId));
  }

  private addPointInternal(
    project: Project,
    lat: number,
    lng: number,
    layerId: string
  ) {
    if (project.layers.isEmpty()) {
      return Promise.resolve();
    }
    const newFeature = new LocationFeature(
      this.dataStore.generateId(),
      layerId,
      new firestore.GeoPoint(lat, lng)
    );
    return this.dataStore
      .updateFeature(project.id, newFeature)
      .then(() => this.selectFeature(newFeature.id));
  }
}
