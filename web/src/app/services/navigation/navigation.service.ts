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
  private static readonly LOCATIONOFINTEREST_ID_FRAGMENT_PARAM = 'f';
  private static readonly LOCATIONOFINTEREST_LAYER_ID_FRAGMENT_PARAM = 'fl';
  private static readonly OBSERVATION_ID_FRAGMENT_PARAM = 'o';
  static readonly LAYER_ID_NEW = 'new';
  static readonly OBSERVATION_ID_NEW = 'new';
  static readonly SURVEY_ID_NEW = 'new';
  static readonly SURVEY_ID = 'surveyId';
  static readonly SURVEY_SEGMENT = 'survey';
  static readonly SIGN_IN_SEGMENT = 'signin';
  static readonly SURVEYS_SEGMENT = 'surveys';

  private static fragmentParamsToSideNavMode(params: HttpParams): SideNavMode {
    if (params.get(NavigationService.OBSERVATION_ID_FRAGMENT_PARAM)) {
      return SideNavMode.OBSERVATION;
    }
    if (params.get(NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM)) {
      return SideNavMode.LOCATIONOFINTEREST;
    }
    if (
      params.get(NavigationService.LOCATIONOFINTEREST_LAYER_ID_FRAGMENT_PARAM)
    ) {
      return SideNavMode.LOCATIONOFINTEREST_LIST;
    }
    return SideNavMode.LAYER_LIST;
  }

  private activatedRoute?: ActivatedRoute;
  private surveyId$?: Observable<string | null>;
  private layerId$?: Observable<string | null>;
  private loiId$?: Observable<string | null>;
  private observationId$?: Observable<string | null>;
  private sideNavMode$?: Observable<SideNavMode>;

  constructor(private router: Router) {}

  /**
   * Set up streams using provided route. This must be called before any of
   * the accessors are called.
   */
  init(route: ActivatedRoute) {
    this.activatedRoute = route;
    // Pipe values from URL query parameters.
    this.surveyId$ = route.paramMap.pipe(
      map(params => params.get(NavigationService.SURVEY_ID))
    );
    // Pipe values from URL fragment.
    const fragmentParams$ = route.fragment.pipe(
      map(fragment => new HttpParams({ fromString: fragment || '' }))
    );
    this.layerId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.LAYER_ID_FRAGMENT_PARAM))
    );
    this.loiId$ = fragmentParams$.pipe(
      map(params =>
        params.get(NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM)
      )
    );
    this.observationId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.OBSERVATION_ID_FRAGMENT_PARAM))
    );
    this.sideNavMode$ = fragmentParams$.pipe(
      map(params => NavigationService.fragmentParamsToSideNavMode(params))
    );
  }

  getSurveyId$(): Observable<string | null> {
    return this.surveyId$!;
  }

  getLayerId$(): Observable<string | null> {
    return this.layerId$!;
  }

  getLocationOfInterestId$(): Observable<string | null> {
    return this.loiId$!;
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
      this.router.navigate([primaryUrl], navigationExtras);
    } else {
      this.router.navigate([primaryUrl]);
    }
  }

  /**
   * Get current LOI id in the URL fragment.
   */
  getLocationOfInterestId(): string | null {
    return this.getFragmentParams().get(
      NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM
    );
  }

  /**
   * Navigate to the current URL, updating the LOI id in the URL fragment.
   */
  selectLocationOfInterest(id: string) {
    const newParam: { [key: string]: string } = {};
    newParam[NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM] = id;
    this.setFragmentParams(new HttpParams({ fromObject: newParam }));
  }

  showLocationOfInterestList(layerId: string) {
    const newParam: { [key: string]: string } = {};
    newParam[
      NavigationService.LOCATIONOFINTEREST_LAYER_ID_FRAGMENT_PARAM
    ] = layerId;
    this.setFragmentParams(new HttpParams({ fromObject: newParam }));
  }

  clearLocationOfInterestId() {
    this.setFragmentParams(new HttpParams({ fromString: '' }));
  }

  /**
   * Get current observation id in the URL fragment.
   */
  getObservationId(): string | null {
    return this.getFragmentParams().get(
      NavigationService.OBSERVATION_ID_FRAGMENT_PARAM
    );
  }

  /**
   * Navigate to the current URL, updating the observation id in the URL
   * fragment.
   */
  editObservation(loiId: string, observationId: string) {
    const newParam: { [key: string]: string } = {};
    newParam[NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM] = loiId;
    newParam[NavigationService.OBSERVATION_ID_FRAGMENT_PARAM] = observationId;
    this.setFragmentParams(new HttpParams({ fromObject: newParam }));
  }

  clearObservationId() {
    const newParam: { [key: string]: string } = {};
    newParam[
      NavigationService.LOCATIONOFINTEREST_ID_FRAGMENT_PARAM
    ] = this.getLocationOfInterestId()!;
    this.setFragmentParams(new HttpParams({ fromObject: newParam }));
  }

  /**
   * Navigate to the current URL, updating the layer id in the URL
   * fragment.
   */
  customizeLayer(id: string) {
    const newParam: { [key: string]: string } = {};
    newParam[NavigationService.LAYER_ID_FRAGMENT_PARAM] = id;
    this.setFragmentParams(new HttpParams({ fromObject: newParam }));
  }

  /**
   * Navigate to the URL with new survey id.
   */
  selectSurvey(id: string) {
    this.router.navigateByUrl(`${NavigationService.SURVEY_SEGMENT}/${id}`);
  }

  /**
   * Navigate to the URL for new survey creation.
   */
  newSurvey() {
    this.router.navigate([
      NavigationService.SURVEY_SEGMENT,
      NavigationService.SURVEY_ID_NEW,
    ]);
  }

  /**
   * Navigate to the URL for viewing a list of available surveys.
   */
  navigateToSurveyList() {
    this.router.navigate([NavigationService.SURVEYS_SEGMENT]);
  }

  /**
   * Navigate to the URL for signin.
   */
  signIn() {
    this.router.navigate([NavigationService.SIGN_IN_SEGMENT]);
  }

  /**
   * Navigate to the URL for signout.
   */
  signOut() {
    this.router.navigate(['/']);
  }
}

export enum SideNavMode {
  LAYER_LIST = 1,
  OBSERVATION = 2,
  LOCATIONOFINTEREST = 3,
  LOCATIONOFINTEREST_LIST = 4,
}
