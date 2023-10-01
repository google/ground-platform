/**
 * Copyright 2019 The Ground Authors.
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

import {Injectable} from '@angular/core';
import {firstValueFrom, Observable, ReplaySubject} from 'rxjs';
import {
  switchMap,
  shareReplay,
  distinctUntilChanged,
  tap,
  filter,
  map,
} from 'rxjs/operators';
import {Survey} from 'app/models/survey.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {AuthService} from 'app/services/auth/auth.service';
import {Role} from 'app/models/role.model';
import {List, Map} from 'immutable';
import {of} from 'rxjs';
import {environment} from 'environments/environment';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {AclEntry} from 'app/models/acl-entry.model';

@Injectable({
  providedIn: 'root',
})
export class SurveyService {
  private activateSurveyRequest$ = new ReplaySubject<string>(1);
  private activeSurvey$: Observable<Survey | null>;
  private activeSurvey: Survey | null = null;

  constructor(
    private dataStore: DataStoreService,
    private authService: AuthService
  ) {
    // Reload active survey each time authenticated user changes.
    this.activeSurvey$ = authService.getUser$().pipe(
      switchMap(() => this.createActiveSurvey$()),
      // Cache last loaded survey so that late subscribers don't cause survey to be
      // reloaded (i.e., make this stream "hot").
      shareReplay(1)
    );
  }

  /**
   * Returns a new stream containing the last loaded active survey, or if loading
   * hasn't started or is still in progress / incomplete.
   */
  createActiveSurvey$(): Observable<Survey | null> {
    return this.activateSurveyRequest$.pipe(
      distinctUntilChanged(),
      // Asynchronously load survey. `switchMap()` internally disposes
      // of previous subscription before subscribing to new stream.
      switchMap(id => this.getSurvey$(id)),
      // Use side-effects (`tap()`) rather than subscribe to avoid race condition between emission of
      // values from `activeSurvey$` and latest value in `activeSurvey`.
      tap(survey => (this.activeSurvey = survey))
    );
  }

  /**
   * Returns a new stream which emits the latest snapshot of the survey with the specified
   * id on subscribe, and on each successive change in the remote datastore.
   */
  getSurvey$(id: string): Observable<Survey | null> {
    if (id === NavigationService.SURVEY_ID_NEW) {
      return of(Survey.UNSAVED_NEW);
    }
    // Set currently active survey to `null` while loading is in progress.
    return of(null).pipe(switchMap(() => this.dataStore.getSurvey$(id)));
  }

  getActiveSurvey(): Survey | null {
    return this.activeSurvey;
  }

  requireActiveSurvey(): Survey {
    return this.activeSurvey!;
  }

  activateSurvey(id: string) {
    this.activateSurveyRequest$.next(id);
  }

  requireActiveSurvey$(): Observable<Survey> {
    return this.activeSurvey$.pipe(map(s => s!));
  }

  getActiveSurvey$(): Observable<Survey | null> {
    return this.activeSurvey$;
  }

  getAccessibleSurveys$(): Observable<List<Survey>> {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return new Observable<List<Survey>>();
    }
    const userEmail = user.email;
    return this.dataStore.loadAccessibleSurvey$(userEmail);
  }

  /**
   * Updates the survey with new title by calling the data-store service.
   *
   * @param surveyId the id of the survey.
   * @param newTitle the new title of the survey.
   */
  updateTitle(surveyId: string, newTitle: string): Promise<void> {
    return this.dataStore.updateSurveyTitle(surveyId, newTitle);
  }

  /**
   * Updates the survey with new title and new description by calling the data-store service.
   *
   * @param surveyId the id of the survey.
   * @param newTitle the new title of the survey.
   * @param newDescription the new description of the survey.
   */
  updateTitleAndDescription(
    surveyId: string,
    newTitle: string,
    newDescription: string
  ): Promise<void> {
    return this.dataStore.updateSurveyTitleAndDescription(
      surveyId,
      newTitle,
      newDescription
    );
  }

  updateAcl(surveyId: string, acl: Map<string, Role>): Promise<void> {
    return this.dataStore.updateAcl(surveyId, acl);
  }

  async createSurvey(title: string, description?: string): Promise<string> {
    const offlineBaseMapSources = environment.offlineBaseMapSources;
    const user = await firstValueFrom(this.authService.getUser$());
    const email = user?.email || 'Unknown email';
    const surveyId = await this.dataStore.createSurvey(
      email,
      title,
      description ?? '',
      offlineBaseMapSources
    );
    return Promise.resolve(surveyId);
  }

  /**
   * Returns the acl of the currently active survey, or an empty list if none is active.
   */
  getActiveSurveyAcl(): AclEntry[] {
    return (
      this.activeSurvey?.acl
        .entrySeq()
        .map(entry => new AclEntry(entry[0], entry[1]))
        .toList()
        .sortBy(entry => entry.email)
        .toArray() || []
    );
  }

  /**
   * Checks if a user has survey organizer or owner level permissions of the survey.
   */
  canManageSurvey(): boolean {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return false;
    }
    const userEmail = user.email;
    const acl = this.getActiveSurveyAcl();
    return !!acl.find(entry => entry.email === userEmail && entry.isManager());
  }
}
