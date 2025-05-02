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

import {Location} from '@angular/common';
import {HttpParams} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {
  ActivatedRoute,
  IsActiveMatchOptions,
  NavigationExtras,
  Router,
} from '@angular/router';
import {Observable, of} from 'rxjs';
import {map} from 'rxjs/operators';

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  static readonly LOI_SEGMENT = 'site';
  static readonly LOI_ID = 'siteId';
  static readonly JOB_ID_NEW = 'new';
  static readonly SUBMISSION_SEGMENT = 'submission';
  static readonly SUBMISSION_ID = 'submissionId';
  static readonly SUBMISSION_ID_NEW = 'new';
  static readonly SURVEY_ID_NEW = 'new';
  static readonly SURVEY_ID = 'surveyId';
  static readonly SURVEY_SEGMENT = 'survey';
  static readonly SIGN_IN_SEGMENT = 'signin';
  static readonly SURVEYS_SEGMENT = 'surveys';
  static readonly SURVEYS_CREATE = 'create';
  static readonly SURVEYS_EDIT = 'edit';
  static readonly TASK_SEGMENT = 'task';
  static readonly TASK_ID = 'taskId';
  static readonly JOB_SEGMENT = 'job';
  static readonly ERROR = 'error';
  static readonly ABOUT = 'about';
  static readonly TERMS = 'terms';

  private sidePanelExpanded = true;

  private getSideNavMode(
    loiId: string | null,
    submissionId: string | null
  ): SideNavMode {
    if (submissionId) {
      if (submissionId.includes('null')) {
        this.error(new Error('Check your URL. Submission id was set to null'));
      }
      return SideNavMode.SUBMISSION;
    }
    if (loiId) {
      if (loiId.includes('null')) {
        this.error(
          new Error('Check your URL. Location of interest id was set to null')
        );
      }
      return SideNavMode.JOB_LIST;
    }
    return SideNavMode.JOB_LIST;
  }

  private activatedRoute?: ActivatedRoute;
  private surveyId$?: Observable<string | null>;
  private loiId$?: Observable<string | null>;
  private submissionId$?: Observable<string | null>;
  private taskId$?: Observable<string | null>;
  private sideNavMode$?: Observable<SideNavMode>;

  constructor(private location: Location, private router: Router) {}

  /**
   * Set up streams using provided route. This must be called before any of
   * the accessors are called.
   */
  init(route: ActivatedRoute) {
    this.activatedRoute = route;
    // Pipe values from URL query parameters.
    this.surveyId$ = route.paramMap.pipe(map(params => params.get(SURVEY_ID)));
    this.loiId$ = route.paramMap.pipe(map(params => params.get(LOI_ID)));

    this.submissionId$ = route.paramMap.pipe(
      map(params => params.get(SUBMISSION_ID))
    );

    this.taskId$ = route.paramMap.pipe(map(params => params.get(TASK_ID)));

    this.sideNavMode$ = route.paramMap.pipe(
      map(params => {
        const loiId = params.get(LOI_ID);
        const submissionId = params.get(SUBMISSION_ID);
        return this.getSideNavMode(loiId, submissionId);
      })
    );
  }

  getSurveyId$(): Observable<string | null> {
    return this.surveyId$!;
  }

  getLocationOfInterestId$(): Observable<string | null> {
    return this.loiId$!;
  }

  getSubmissionId$(): Observable<string | null> {
    return this.submissionId$!;
  }

  getTaskId$(): Observable<string | null> {
    return this.taskId$!;
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
    return new HttpParams({fromString: fragment || ''});
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
   * Navigate to the current URL, updating the LOI id in the URL fragment.
   */
  selectLocationOfInterest(surveyId: string, loiId: string) {
    this.router.navigateByUrl(
      `${SURVEY_SEGMENT}/${surveyId}/${LOI_SEGMENT}/${loiId}`
    );
  }

  showSubmissionDetail(surveyId: string, loiId: string, submissionId: string) {
    this.router.navigateByUrl(
      `${SURVEY_SEGMENT}/${surveyId}/${LOI_SEGMENT}/${loiId}/${SUBMISSION_SEGMENT}/${submissionId}`
    );
  }

  showSubmissionDetailWithHighlightedTask(
    surveyId: string,
    loiId: string,
    submissionId: string,
    taskId: string
  ) {
    this.router.navigateByUrl(
      `${SURVEY_SEGMENT}/${surveyId}/${LOI_SEGMENT}/${loiId}/${SUBMISSION_SEGMENT}/${submissionId}/${TASK_SEGMENT}/${taskId}`
    );
  }

  clearLocationOfInterestId() {
    this.loiId$ = of('');
  }

  clearSubmissionId() {
    this.submissionId$ = of('');
  }

  /**
   * Navigate to the URL with new survey id.
   */
  selectSurvey(id: string) {
    this.router.navigateByUrl(`${SURVEY_SEGMENT}/${id}`);
  }

  /**
   * Navigate to the URL for new survey creation.
   */
  newSurvey() {
    this.router.navigate([SURVEY_SEGMENT, SURVEY_ID_NEW]);
  }

  /**
   * Navigate to the about page
   */
  navigateToAboutPage() {
    this.router.navigate([ABOUT]);
  }

  /**
   * Navigate to the terms of service page
   */
  navigateToTermsOfService() {
    this.router.navigate([TERMS]);
  }

  /**
   * Navigate to the URL for viewing a list of available surveys.
   */
  navigateToSurveyList() {
    this.router.navigate([SURVEYS_SEGMENT]);
  }

  navigateToCreateSurvey(surveyId: string | null, replaceUrl = false): void {
    const url = `${SURVEYS_SEGMENT}${
      surveyId ? `/${surveyId}` : ''
    }/${SURVEYS_CREATE}`;
    this.router.navigateByUrl(url, {replaceUrl});
  }

  navigateToEditSurvey(surveyId: string): void {
    const url = `${SURVEY_SEGMENT}/${surveyId}/${SURVEYS_EDIT}/${SURVEY_SEGMENT}`;
    this.router.navigateByUrl(url);
  }

  /**
   * Navigate to the URL with new survey id.
   */
  navigateToEditJob(surveyId: string, jobId: string) {
    this.router.navigateByUrl(
      `${SURVEY_SEGMENT}/${surveyId}/${SURVEYS_EDIT}/${JOB_SEGMENT}/${jobId}`
    );
  }

  /**
   * Navigate to the URL for signin.
   */
  signIn() {
    this.router.navigate([SIGN_IN_SEGMENT]);
  }

  /**
   * Navigate to the URL for signout.
   */
  signOut() {
    this.router.navigate(['/']);
  }

  /**
   * Navigate to the URL for error.
   */
  error(error: Error) {
    this.router.navigate([ERROR, {error}]);
  }

  isSurveyPage(surveyId: string): boolean {
    return this.router.isActive(`${SURVEY_SEGMENT}/${surveyId}`, {
      matrixParams: 'ignored',
      queryParams: 'ignored',
      paths: 'exact',
      fragment: 'ignored',
    } as IsActiveMatchOptions);
  }

  isEditSurveyPage(surveyId: string): boolean {
    return this.router.isActive(
      `${SURVEY_SEGMENT}/${surveyId}/${SURVEYS_EDIT}`,
      {
        matrixParams: 'ignored',
        queryParams: 'ignored',
        paths: 'subset',
        fragment: 'ignored',
      } as IsActiveMatchOptions
    );
  }

  getSurveyAppLink(surveyId: string): string {
    return `${this.getBaseUrl()}android/${SURVEY_SEGMENT}/${surveyId}`;
  }

  getSidePanelExpanded(): boolean {
    return this.sidePanelExpanded;
  }

  getBaseUrl(): string {
    const baseHref = this.location.prepareExternalUrl('');
    const fullPath = window.location.origin + baseHref;
    return fullPath;
  }

  onClickSidePanelButton() {
    this.sidePanelExpanded = !this.sidePanelExpanded;
  }
}

export enum SideNavMode {
  JOB_LIST = 1,
  SUBMISSION = 2,
}

const {
  ABOUT,
  ERROR,
  LOI_ID,
  LOI_SEGMENT,
  JOB_SEGMENT,
  SIGN_IN_SEGMENT,
  SUBMISSION_ID,
  SUBMISSION_SEGMENT,
  SURVEY_ID,
  SURVEY_ID_NEW,
  SURVEY_SEGMENT,
  SURVEYS_CREATE,
  SURVEYS_EDIT,
  SURVEYS_SEGMENT,
  TASK_ID,
  TASK_SEGMENT,
  TERMS,
} = NavigationService;
