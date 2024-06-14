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
import {List, Map} from 'immutable';
import {Observable, ReplaySubject, firstValueFrom, of} from 'rxjs';
import {shareReplay, switchMap} from 'rxjs/operators';

import {AclEntry} from 'app/models/acl-entry.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {environment} from 'environments/environment';

@Injectable({
  providedIn: 'root',
})
export class SurveyService {
  private activeSurveyId$ = new ReplaySubject<string>(1);
  private activeSurvey$: Observable<Survey>;
  private activeSurvey!: Survey;

  constructor(
    private dataStore: DataStoreService,
    private authService: AuthService
  ) {
    // Reload active survey each time authenticated user changes.
    this.activeSurvey$ = authService.getUser$().pipe(
      switchMap(() =>
        //  on each change to survey id.
        this.activeSurveyId$.pipe(
          // Asynchronously load survey. switchMap() internally disposes
          // of previous subscription if present.
          switchMap(id => {
            if (id === NavigationService.SURVEY_ID_NEW) {
              return of(Survey.UNSAVED_NEW);
            }
            return this.dataStore.loadSurvey$(id);
          })
        )
      ),
      // Cache last loaded survey so that late subscribers don't cause
      // survey to be reloaded.
      shareReplay(1)
    );
    this.activeSurvey$.subscribe(survey => (this.activeSurvey = survey));
  }

  getActiveSurvey(): Survey {
    return this.activeSurvey;
  }

  activateSurvey(id: string) {
    this.activeSurveyId$.next(id);
  }

  getActiveSurvey$(): Observable<Survey> {
    return this.activeSurvey$;
  }

  getAccessibleSurveys$(): Observable<List<Survey>> {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return new Observable<List<Survey>>();
    }
    const userEmail = user.email;
    return this.dataStore.loadAccessibleSurveys$(userEmail);
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

  async createSurvey(name: string, description?: string): Promise<string> {
    const user = await firstValueFrom(this.authService.getUser$());
    const surveyId = await this.dataStore.createSurvey(
      name,
      description ?? '',
      user
    );
    return Promise.resolve(surveyId);
  }

  /**
   * Deletes the survey and its subcollections.
   *
   * @param surveyId the id of the survey.
   */
  deleteSurvey(survey: Survey): Promise<void> {
    return this.dataStore.deleteSurvey(survey);
  }

  /**
   * Returns the acl of the current survey.
   */
  getActiveSurveyAcl(): AclEntry[] {
    if (!this.activeSurvey) {
      throw Error('No active survey');
    }
    return this.activeSurvey.acl
      .entrySeq()
      .map(entry => new AclEntry(entry[0], entry[1]))
      .toList()
      .sortBy(entry => entry.email)
      .toArray();
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
