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

import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ActivatedRoute, Router, NavigationExtras } from '@angular/router';
import { HttpParams } from '@angular/common/http';
import { ProjectService } from '../project/project.service';
import { DataStoreService } from '../data-store/data-store.service';

interface ParamMap {
  [key: string]: string;
}

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class RouterService {
  private static readonly LAYER_ID_FRAGMENT_PARAM = 'l';
  private static readonly FEATURE_ID_FRAGMENT_PARAM = 'f';
  private static readonly OBSERVATION_ID_FRAGMENT_PARAM = 'o';

  private activatedRoute?: ActivatedRoute;
  private projectId$?: Observable<string | null>;
  private layerId$?: Observable<string | null>;
  private featureId$?: Observable<string | null>;
  private observationId$?: Observable<string | null>;

  constructor(
    private router: Router,
    private projectService: ProjectService,
    private dataStoreService: DataStoreService
  ) {}

  /**
   * Set up streams using provided route. This must be called before any of
   * the accessors are called.
   */
  init(route: ActivatedRoute) {
    this.activatedRoute = route;
    // Pipe values from URL query parameters.
    this.projectId$ = route.paramMap.pipe(
      map(params => {
        if (params.get('projectId') === ':new') {
          const projectId = this.dataStoreService.generateId();
          this.projectService.createProject(projectId).then(() => {
            this.router.navigate([`../${projectId}`], {
              relativeTo: this.activatedRoute,
            });
          });
        }
        return params.get('projectId');
      })
    );
    // Pipe values from URL fragment.
    const fragmentParams$ = route.fragment.pipe(
      map(fragment => new HttpParams({ fromString: fragment || '' }))
    );
    this.layerId$ = fragmentParams$.pipe(
      map(params => params.get(RouterService.LAYER_ID_FRAGMENT_PARAM))
    );
    this.featureId$ = fragmentParams$.pipe(
      map(params => params.get(RouterService.FEATURE_ID_FRAGMENT_PARAM))
    );
    this.observationId$ = fragmentParams$.pipe(
      map(params => params.get(RouterService.OBSERVATION_ID_FRAGMENT_PARAM))
    );
  }

  getProjectId$(): Observable<string | null> {
    return this.projectId$!;
  }

  getLayerId$(): Observable<string | null> {
    return this.layerId$!;
  }

  getFeatureId$(): Observable<string | null> {
    return this.featureId$!;
  }

  getObservationId$(): Observable<string | null> {
    return this.observationId$!;
  }

  /**
   * Returns the current URL fragment, parsed as if their were normal HTTP
   * query parameter key/value pairs.
   */
  private getFragmentParams(): HttpParams {
    const fragment = this.activatedRoute!.snapshot.fragment;
    return new HttpParams({ fromString: fragment || '' });
  }

  /**
   * Navigate to the current URL, replacing the URL fragment with the specified
   * params.
   */
  private setFragmentParams(params: HttpParams) {
    const primaryUrl = this.router
      .parseUrl(this.router.url)
      .root.children['primary'].toString();
    const navigationExtras: NavigationExtras = {
      fragment: params.toString(),
    };
    this.router.navigate([primaryUrl], navigationExtras);
  }

  /**
   * Navigate to the current URL, replacing the single URL fragment param
   * with the specified value.
   */
  private setFragmentParam(key: string, value: string) {
    this.setFragmentParams(this.getFragmentParams().set(key, value));
  }

  /**
   * Navigate to the current URL, updating the feature id in the URL fragment.
   */
  setFeatureId(id: string) {
    this.setFragmentParam(RouterService.FEATURE_ID_FRAGMENT_PARAM, id);
  }

  /**
   * Navigate to the current URL, updating the observation id in the URL
   * fragment.
   */
  setObservationId(id: string) {
    this.setFragmentParam(RouterService.OBSERVATION_ID_FRAGMENT_PARAM, id);
  }
}
