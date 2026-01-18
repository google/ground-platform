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

import { Injectable } from '@angular/core';
import { List, Map } from 'immutable';
import { Observable, firstValueFrom, of } from 'rxjs';
import { shareReplay, switchMap } from 'rxjs/operators';

import { Role } from 'app/models/role.model';
import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SURVEY_ID_NEW } from 'app/services/navigation/navigation.constants';
import { NavigationService } from 'app/services/navigation/navigation.service';

@Injectable({
  providedIn: 'root',
})
export class SurveyService {
  private activeSurvey$: Observable<Survey>;
  private activeSurvey!: Survey;

  constructor(
    private dataStore: DataStoreService,
    private authService: AuthService,
    private navigationService: NavigationService
  ) {
    // Reload active survey each time authenticated user changes.
    this.activeSurvey$ = authService.getUser$().pipe(
      switchMap(() =>
        //  on each change to survey id.
        this.navigationService.getSurveyId$().pipe(
          // Asynchronously load survey. switchMap() internally disposes
          // of previous subscription if present.
          switchMap(id => {
            if (id === SURVEY_ID_NEW) {
              return of(Survey.UNSAVED_NEW);
            }
            if (id) {
              return this.dataStore.loadSurvey$(id);
            }
            return of(Survey.UNSAVED_NEW);
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

  getActiveSurvey$(): Observable<Survey> {
    return this.activeSurvey$;
  }

  loadSurvey$(id: string): Observable<Survey> {
    return this.dataStore.loadSurvey$(id);
  }

  getAccessibleSurveys$(): Observable<List<Survey>> {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return of(List<Survey>());
    }
    const { email: userEmail } = user;
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

  /**
   * Updates the survey with new state by calling the data-store service.
   *
   * @param survey the survey instance.
   * @param state the new status of the survey.
   */
  updateState(state: SurveyState): Promise<void> {
    return this.dataStore.updateSurvey({
      ...this.activeSurvey,
      state,
    } as Survey);
  }

  /**
   * Updates the survey with new acl by calling the data-store service.
   *
   * @param survey the survey instance.
   * @param acl the new access control list of the survey.
   */
  updateAcl(acl: Map<string, Role>): Promise<void> {
    return this.dataStore.updateSurvey({ ...this.activeSurvey, acl } as Survey);
  }

  /**
   * Adds or overwrites the dataSharingTerms in the survey of the specified id.
   * @param survey the survey instance.
   * @param type the type of the DataSharingTerms.
   * @param customText the text of the DataSharingTerms.
   */
  updateDataSharingTerms(
    type: DataSharingType,
    customText?: string
  ): Promise<void> {
    return this.dataStore.updateSurvey({
      ...this.activeSurvey,
      dataSharingTerms: { type, ...(customText && { customText }) },
    } as Survey);
  }

  async copySurvey(surveyId: string): Promise<string> {
    return this.dataStore.copySurvey(surveyId);
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
   * @param survey the survey instance.
   */
  async deleteSurvey(survey: Survey): Promise<void> {
    try {
      await this.dataStore.deleteSurvey(survey);
    } catch (error) {
      console.error(`Error deleting survey: ${survey.id}`, error);
      throw error;
    }
  }

  /**
   * Checks if a user has survey organizer or owner level permissions of the survey.
   */
  canManageSurvey(survey?: Survey): boolean {
    const user = this.authService.getCurrentUser();
    if (!user) {
      return false;
    }
    const targetSurvey = survey || this.activeSurvey;
    if (!targetSurvey) {
      return false;
    }
    const userEmail = user.email;
    const acl = targetSurvey.getAclEntriesSorted();
    return !!acl.find(entry => entry.email === userEmail && entry.isManager());
  }
}
