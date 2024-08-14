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

import {HttpParams} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {
  ActivatedRoute,
  IsActiveMatchOptions,
  NavigationExtras,
  Router,
} from '@angular/router';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  private static readonly JOB_ID_FRAGMENT_PARAM = 'l';
  private static readonly LOI_ID_FRAGMENT_PARAM = 'f';
  private static readonly LOI_JOB_ID_FRAGMENT_PARAM = 'fl';
  private static readonly SUBMISSION_ID_FRAGMENT_PARAM = 'o';
  private static readonly TASK_ID_FRAGMENT_PARAM = 't';
  static readonly JOB_ID_NEW = 'new';
  static readonly SUBMISSION_ID_NEW = 'new';
  static readonly SURVEY_ID_NEW = 'new';
  static readonly SURVEY_ID = 'surveyId';
  static readonly SURVEY_SEGMENT = 'survey';
  static readonly SIGN_IN_SEGMENT = 'signin';
  static readonly SURVEYS_SEGMENT = 'surveys';
  static readonly SURVEYS_CREATE = 'create';
  static readonly SURVEYS_EDIT = 'edit';
  static readonly JOB_SEGMENT = 'job';
  static readonly ERROR = 'error';
  static readonly ABOUT = 'about';
  static readonly TERMS = 'terms';

  private sidePanelExpanded = true;

  // TODO: remove this logic once the new side panel replaces the old one
  private fragmentParamsToSideNavMode(params: HttpParams): SideNavMode {
    const submissionId = params.get(
      NavigationService.SUBMISSION_ID_FRAGMENT_PARAM
    );
    const loiId = params.get(NavigationService.LOI_ID_FRAGMENT_PARAM);
    const loiJobId = params.get(NavigationService.LOI_JOB_ID_FRAGMENT_PARAM);

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
    if (loiJobId) {
      if (loiJobId.includes('null')) {
        this.error(
          new Error(
            'Check your URL. Location of interest id and/or job id was set to null'
          )
        );
      }
      return SideNavMode.LOI_LIST;
    }
    return SideNavMode.JOB_LIST;
  }

  private activatedRoute?: ActivatedRoute;
  private surveyId$?: Observable<string | null>;
  private jobId$?: Observable<string | null>;
  private loiId$?: Observable<string | null>;
  private submissionId$?: Observable<string | null>;
  private taskId$?: Observable<string | null>;
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
      map(fragment => new HttpParams({fromString: fragment || ''}))
    );
    this.jobId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.JOB_ID_FRAGMENT_PARAM))
    );
    this.loiId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.LOI_ID_FRAGMENT_PARAM))
    );
    this.submissionId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.SUBMISSION_ID_FRAGMENT_PARAM))
    );
    this.taskId$ = fragmentParams$.pipe(
      map(params => params.get(NavigationService.TASK_ID_FRAGMENT_PARAM))
    );
    this.sideNavMode$ = fragmentParams$.pipe(
      map(params => this.fragmentParamsToSideNavMode(params))
    );
  }

  getSurveyId$(): Observable<string | null> {
    return this.surveyId$!;
  }

  getJobId$(): Observable<string | null> {
    return this.jobId$!;
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
   * Get current LOI id in the URL fragment.
   */
  getLocationOfInterestId(): string | null {
    return this.getFragmentParams().get(
      NavigationService.LOI_ID_FRAGMENT_PARAM
    );
  }

  /**
   * Navigate to the current URL, updating the LOI id in the URL fragment.
   */
  selectLocationOfInterest(id: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_ID_FRAGMENT_PARAM] = id;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  showLocationOfInterestList(jobId: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_JOB_ID_FRAGMENT_PARAM] = jobId;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  showSubmissionDetail(jobId: string, submissionId: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_ID_FRAGMENT_PARAM] = jobId;
    newParam[NavigationService.SUBMISSION_ID_FRAGMENT_PARAM] = submissionId;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  showSubmissionDetailWithHighlightedTask(taskId: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_ID_FRAGMENT_PARAM] =
      this.getLocationOfInterestId()!;
    newParam[NavigationService.SUBMISSION_ID_FRAGMENT_PARAM] =
      this.getSubmissionId()!;
    newParam[NavigationService.TASK_ID_FRAGMENT_PARAM] = taskId;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  clearLocationOfInterestId() {
    this.setFragmentParams(new HttpParams({fromString: ''}));
  }

  /**
   * Get current submission id in the URL fragment.
   */
  getSubmissionId(): string | null {
    return this.getFragmentParams().get(
      NavigationService.SUBMISSION_ID_FRAGMENT_PARAM
    );
  }

  /**
   * Navigate to the current URL, updating the submission id in the URL
   * fragment.
   */
  editSubmission(loiId: string, submissionId: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_ID_FRAGMENT_PARAM] = loiId;
    newParam[NavigationService.SUBMISSION_ID_FRAGMENT_PARAM] = submissionId;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  clearSubmissionId() {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.LOI_ID_FRAGMENT_PARAM] =
      this.getLocationOfInterestId()!;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
  }

  /**
   * Navigate to the current URL, updating the job id in the URL
   * fragment.
   */
  customizeJob(id: string) {
    const newParam: {[key: string]: string} = {};
    newParam[NavigationService.JOB_ID_FRAGMENT_PARAM] = id;
    this.setFragmentParams(new HttpParams({fromObject: newParam}));
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
   * Navigate to the terms of service page
   */
  navigateToTermsOfService() {
    this.router.navigate([NavigationService.TERMS]);
  }

  /**
   * Navigate to the URL for viewing a list of available surveys.
   */
  navigateToSurveyList() {
    this.router.navigate([NavigationService.SURVEYS_SEGMENT]);
  }

  navigateToCreateSurvey(surveyId: string | null, replaceUrl = false): void {
    const url = `${NavigationService.SURVEYS_SEGMENT}${
      surveyId ? `/${surveyId}` : ''
    }/${NavigationService.SURVEYS_CREATE}`;
    this.router.navigateByUrl(url, {replaceUrl});
  }

  navigateToEditSurvey(surveyId: string): void {
    const url = `${NavigationService.SURVEY_SEGMENT}/${surveyId}/${NavigationService.SURVEYS_EDIT}/${NavigationService.SURVEY_SEGMENT}`;
    this.router.navigateByUrl(url);
  }

  /**
   * Navigate to the URL with new survey id.
   */
  navigateToEditJob(surveyId: string, jobId: string) {
    this.router.navigateByUrl(
      `${NavigationService.SURVEY_SEGMENT}/${surveyId}/${NavigationService.SURVEYS_EDIT}/${NavigationService.JOB_SEGMENT}/${jobId}`
    );
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

  /**
   * Navigate to the URL for error.
   */
  error(error: Error) {
    this.router.navigate([NavigationService.ERROR, {error}]);
  }

  isSurveyPage(surveyId: string): boolean {
    return this.router.isActive(
      `${NavigationService.SURVEY_SEGMENT}/${surveyId}`,
      {
        matrixParams: 'ignored',
        queryParams: 'ignored',
        paths: 'exact',
        fragment: 'ignored',
      } as IsActiveMatchOptions
    );
  }

  isEditSurveyPage(surveyId: string): boolean {
    return this.router.isActive(
      `${NavigationService.SURVEY_SEGMENT}/${surveyId}/${NavigationService.SURVEYS_EDIT}`,
      {
        matrixParams: 'ignored',
        queryParams: 'ignored',
        paths: 'subset',
        fragment: 'ignored',
      } as IsActiveMatchOptions
    );
  }

  getSidePanelExpanded(): boolean {
    return this.sidePanelExpanded;
  }

  onClickSidePanelButton() {
    this.sidePanelExpanded = !this.sidePanelExpanded;
  }
}

export enum SideNavMode {
  JOB_LIST = 1,
  SUBMISSION = 2,
  LOI = 3,
  LOI_LIST = 4,
}
