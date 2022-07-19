/**
 * Copyright 2019 Google LLC
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
import { Observable, ReplaySubject } from 'rxjs';
import { switchMap, shareReplay } from 'rxjs/operators';
import { Survey } from '../../shared/models/survey.model';
import { DataStoreService } from '../data-store/data-store.service';
import { AuthService } from '../auth/auth.service';
import { Role } from '../../shared/models/role.model';
import { List, Map } from 'immutable';
import { of } from 'rxjs';
import { take } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { NavigationService } from '../navigation/navigation.service';
import { AclEntry } from '../../shared/models/acl-entry.model';

@Injectable({
  providedIn: 'root',
})
export class SurveyService {
  private activeSurveyId$ = new ReplaySubject<string>(1);
  private activeSurvey$: Observable<Survey>;
  private currentSurvey!: Survey;

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
    this.activeSurvey$.subscribe(survey => (this.currentSurvey = survey));
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

  updateAcl(surveyId: string, acl: Map<string, Role>): Promise<void> {
    return this.dataStore.updateAcl(surveyId, acl);
  }

  async createSurvey(title: string): Promise<string> {
    const offlineBaseMapSources = environment.offlineBaseMapSources;
    const user = await this.authService.getUser$().pipe(take(1)).toPromise();
    const email = user?.email || 'Unknown email';
    const surveyId = await this.dataStore.createSurvey(
      email,
      title,
      offlineBaseMapSources
    );
    return Promise.resolve(surveyId);
  }

  /**
   * Returns the acl of the current survey.
   */
  getCurrentSurveyAcl(): AclEntry[] {
    return this.currentSurvey.acl
      .entrySeq()
      .map(entry => new AclEntry(entry[0], entry[1]))
      .toList()
      .sortBy(entry => entry.email)
      .toArray();
  }

  /**
   * Checks if a user has manager or owner level permissions of the survey.
   */
  canManageSurvey(): boolean {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return false;
    }
    const userEmail = user.email;
    const acl = this.getCurrentSurveyAcl();
    return !!acl.find(entry => entry.email === userEmail && entry.isManager());
  }
}
