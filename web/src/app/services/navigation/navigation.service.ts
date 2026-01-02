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

import { DOCUMENT } from '@angular/common';
import {
  Inject,
  Injectable,
  OnDestroy,
  Signal,
  computed,
  effect,
  signal,
} from '@angular/core';
import {
  IsActiveMatchOptions,
  NavigationEnd,
  Params,
  Router,
} from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

import {
  ABOUT,
  ANDROID_SEGMENT,
  ERROR,
  JOB_ID_NEW,
  JOB_SEGMENT,
  LOI_ID,
  LOI_SEGMENT,
  SIGN_IN_SEGMENT,
  SUBMISSION_ID,
  SUBMISSION_ID_NEW,
  SUBMISSION_SEGMENT,
  SURVEYS_CREATE,
  SURVEYS_EDIT,
  SURVEYS_SEGMENT,
  SURVEYS_SHARE,
  SURVEY_ID,
  SURVEY_ID_NEW,
  SURVEY_SEGMENT,
  TASK_ID,
  TASK_SEGMENT,
  TERMS,
} from './navigation.constants';
import { SideNavMode, UrlParams } from './url-params';
import { DataStoreService } from '../data-store/data-store.service';

/**
 * Exposes application state in the URL as streams to other services
 * and components, and provides methods for altering said state.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationService implements OnDestroy {
  private sidePanelExpanded = true;

  private urlSignal = signal<string>('');

  private urlParamsSignal = signal<UrlParams>(
    new UrlParams(null, null, null, null)
  );

  private surveyIdSignal = computed(() => this.urlParamsSignal().surveyId);
  private loiIdSignal = computed(() => this.urlParamsSignal().loiId);
  private submissionIdSignal = computed(
    () => this.urlParamsSignal().submissionId
  );
  private taskIdSignal = computed(() => this.urlParamsSignal().taskId);
  private sideNavModeSignal = computed(
    () => this.urlParamsSignal().sideNavMode
  );

  private surveyId$ = new BehaviorSubject<string | null>(null);
  private loiId$ = new BehaviorSubject<string | null>(null);
  private submissionId$ = new BehaviorSubject<string | null>(null);
  private taskId$ = new BehaviorSubject<string | null>(null);
  private sideNavMode$ = new BehaviorSubject<SideNavMode | null>(
    SideNavMode.JOB_LIST
  );

  private subscription: Subscription;

  constructor(
    @Inject(DOCUMENT) private document: Document,
    private dataStore: DataStoreService,
    private router: Router
  ) {
    this.subscription = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(e => {
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

        this.urlSignal.set(e?.url ?? '');

        this.urlParamsSignal.set(
          new UrlParams(
            params[SURVEY_ID],
            params[LOI_ID],
            params[SUBMISSION_ID],
            params[TASK_ID]
          )
        );
      });

    // TODO remove this effect when everything will be migrated to Signals
    effect(() => {
      const { surveyId, loiId, submissionId, taskId, sideNavMode } =
        this.urlParamsSignal();
      this.surveyId$.next(surveyId);
      this.loiId$.next(loiId);
      this.submissionId$.next(submissionId);
      this.taskId$.next(taskId);
      this.sideNavMode$.next(sideNavMode);
    });
  }

  getUrlParams(): Signal<UrlParams> {
    return this.urlParamsSignal;
  }

  getSurveyId(): Signal<string | null> {
    return this.surveyIdSignal;
  }

  getLoiId(): Signal<string | null> {
    return this.loiIdSignal;
  }

  getSubmissionId(): Signal<string | null> {
    return this.submissionIdSignal;
  }

  getTaskId(): Signal<string | null> {
    return this.taskIdSignal;
  }

  getSideNavMode(): Signal<SideNavMode | null> {
    return this.sideNavModeSignal;
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

  getSideNavMode$(): Observable<SideNavMode | null> {
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
    const surveyId = this.urlParamsSignal().surveyId;
    this.router.navigateByUrl(`${SURVEY_SEGMENT}/${surveyId}`);
  }

  clearSubmissionId() {
    const surveyId = this.urlParamsSignal().surveyId;
    const loiId = this.urlParamsSignal().loiId;
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

  async getAccessDeniedLink(): Promise<string | undefined> {
    const accessDeniedMessage = await this.dataStore.getAccessDeniedMessage();

    return accessDeniedMessage?.link;
  }

  async navigateToSubscriptionForm() {
    const accessDeniedLink = await this.getAccessDeniedLink();

    if (accessDeniedLink) window.location.href = accessDeniedLink;
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
    this.router.navigateByUrl(url, { replaceUrl });
  }

  navigateToEditSurvey(surveyId: string): void {
    const url = `${SURVEY_SEGMENT}/${surveyId}/${SURVEYS_EDIT}/${SURVEY_SEGMENT}`;
    this.router.navigateByUrl(url);
  }

  navigateToSurveyDashboard(surveyId: string): void {
    const url = `${SURVEY_SEGMENT}/${surveyId}`;
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
    this.router.navigate([ERROR, { error }]);
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
    return this.router.url.endsWith(SURVEYS_SHARE);
  }

  getHost(): string {
    return this.document.location.host;
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

  private editSurveyPageSignal = computed(() => {
    const url = this.urlSignal();
    if (url.endsWith('survey')) return SURVEY_SEGMENT;
    else if (url.endsWith('share')) return SURVEYS_SHARE;
    else return '';
  });

  getEditSurveyPageSignal(): Signal<string> {
    return this.editSurveyPageSignal;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
