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

import {DOCUMENT} from '@angular/common';
import {Inject, Injectable, OnDestroy, effect, signal} from '@angular/core';
import {
  IsActiveMatchOptions,
  NavigationEnd,
  Params,
  Router,
} from '@angular/router';
import {BehaviorSubject, Observable, Subscription} from 'rxjs';
import {filter} from 'rxjs/operators';

import {UrlParams} from './url-params';

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationService implements OnDestroy {
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
  static readonly ANDROID_SEGMENT = 'android';

  private sidePanelExpanded = true;

  public urlParams = signal<UrlParams>(new UrlParams(null, null, null, null));

  private surveyId$ = new BehaviorSubject<string | null>(null);
  private loiId$ = new BehaviorSubject<string | null>(null);
  private submissionId$ = new BehaviorSubject<string | null>(null);
  private taskId$ = new BehaviorSubject<string | null>(null);
  private sideNavMode$ = new BehaviorSubject<SideNavMode>(SideNavMode.JOB_LIST);

  private subscription: Subscription;

  constructor(
    @Inject(DOCUMENT) private document: Document,
    private router: Router
  ) {
    this.subscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        let currentRoute = this.router.routerState.root;

        while (currentRoute.firstChild) {
          currentRoute = currentRoute.firstChild;
        }

        const params: Params = {};
        let route = currentRoute;

        while (route) {
          Object.assign(params, route.snapshot.params);
          route = route.parent!;
        }

        this.urlParams.set(
          new UrlParams(
            params[SURVEY_ID],
            params[LOI_ID],
            params[SUBMISSION_ID],
            params[TASK_ID]
          )
        );
      });

    effect(() => {
      const {surveyId, loiId, submissionId, taskId, sideNavMode} =
        this.urlParams();
      this.surveyId$.next(surveyId);
      this.loiId$.next(loiId);
      this.submissionId$.next(submissionId);
      this.taskId$.next(taskId);
      this.sideNavMode$.next(sideNavMode);
    });
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
    const surveyId = this.urlParams().surveyId;
    this.router.navigateByUrl(`${SURVEY_SEGMENT}/${surveyId}`);
  }

  clearSubmissionId() {
    const surveyId = this.urlParams().surveyId;
    const loiId = this.urlParams().loiId;
    surveyId && loiId && this.selectLocationOfInterest(surveyId, loiId);
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
      paths: 'subset',
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

  isShareSurveyPage(): boolean {
    return this.router.url.endsWith('/share');
  }

  getSurveyAppLink(surveyId: string): string {
    return `${this.document.location.origin}/${ANDROID_SEGMENT}/${SURVEY_SEGMENT}/${surveyId}`;
  }

  getSidePanelExpanded(): boolean {
    return this.sidePanelExpanded;
  }

  onClickSidePanelButton() {
    this.sidePanelExpanded = !this.sidePanelExpanded;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
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
  ANDROID_SEGMENT,
} = NavigationService;
