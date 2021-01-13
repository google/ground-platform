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

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  private static readonly LAYER_ID_FRAGMENT_PARAM = 'l';
  private static readonly FEATURE_ID_FRAGMENT_PARAM = 'f';
  private static readonly OBSERVATION_ID_FRAGMENT_PARAM = 'o';
  static readonly LAYER_ID_NEW = ':new';
  static readonly OBSERVATION_ID_NEW = ':new';

  private static fragmentParamsToSideNavMode(params: HttpParams): SideNavMode {
    if (params.get(NavigationService.OBSERVATION_ID_FRAGMENT_PARAM)) {
      return SideNavMode.OBSERVATION;
    }
    if (params.get(NavigationService.FEATURE_ID_FRAGMENT_PARAM)) {
      return SideNavMode.FEATURE;
    }
    return SideNavMode.LAYER_LIST;
  }

  private activatedRoute?: ActivatedRoute;
  private projectId$?: Observable<string | null>;
  private layerId$?: Observable<string | null>;
  private featureId$?: Observable<string | null>;
  private observationId$?: Observable<string | null>;
  private sideNavMode$?: Observable<SideNavMode>;
  private routeChangeInterceptor?: Function<>;

  constructor(private router: Router) {}

  setRouteChangeInterceptor(fn) {
    this.routeChangeInterceptor = fn;
  }
  clearRouteChangeInterceptor() {
    this.routeChangeInterceptor = undefined;
  }
  /**
   * Set up streams using provided route. This must be called before any of
   * the accessors are called.
   */
  init(route: ActivatedRoute) {
    this.activatedRoute = route;
    // Pipe values from URL query parameters.
    this.projectId$ = route.paramMap.pipe(
      map(params => params.get('projectId'))
    );
    // Pipe values from URL fragment.
    const fragmentParams$ = route.fragment.pipe(
      map(fragment => new HttpParams({ fromString: fragment || '' }))
    );
    this.layerId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.LAYER_ID_FRAGMENT_PARAM))
    );
    this.featureId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.FEATURE_ID_FRAGMENT_PARAM))
    );
    this.observationId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.OBSERVATION_ID_FRAGMENT_PARAM))
    );
    this.sideNavMode$ = fragmentParams$.pipe(
      map(params => NavigationService.fragmentParamsToSideNavMode(params))
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

  getSideNavMode$(): Observable<SideNavMode> {
    return this.sideNavMode$!;
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

    if (params.toString()) {
      const navigationExtras: NavigationExtras = {
        fragment: params.toString(),
      };

      if (this.interceptor) {
        boolean canNavigate = await this.navigationListener();
        if (!canNavigate) {
          return;
        }
      }
        this.router.navigate([primaryUrl], navigationExtras);
    } else {
      this.router.navigate([primaryUrl]);
    }
  }

  /**
   * Navigate to the current URL, replacing the single URL fragment param
   * with the specified value.
   */
  private setFragmentParam(key: string, value: string | null) {
    if (value) {
      this.setFragmentParams(this.getFragmentParams().set(key, value));
    } else {
      this.setFragmentParams(this.getFragmentParams().delete(key));
    }
  }

  /**
   * Navigate to the current URL, updating the feature id in the URL fragment.
   */
  setFeatureId(id: string | null) {
    this.setFragmentParam(NavigationService.FEATURE_ID_FRAGMENT_PARAM, id);
  }

  /**
   * Navigate to the current URL, updating the observation id in the URL
   * fragment.
   */
  setObservationId(id: string) {
    this.setFragmentParam(NavigationService.OBSERVATION_ID_FRAGMENT_PARAM, id);
  }

  /**
   * Navigate to the current URL, updating the layer id in the URL
   * fragment.
   */
  setLayerId(id: string) {
    this.setFragmentParam(NavigationService.LAYER_ID_FRAGMENT_PARAM, id);
  }

  /**
   * Navigate to the URL with new project id
   */
  setProjectId(id: string) {
    this.router.navigateByUrl(`/p/${id}`);
  }
}

export enum SideNavMode {
  LAYER_LIST = 1,
  OBSERVATION = 2,
  FEATURE = 3,
}
