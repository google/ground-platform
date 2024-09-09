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

import {Injectable} from '@angular/core';
import {List, Map} from 'immutable';
import {Observable, ReplaySubject, of} from 'rxjs';
import {map, switchMap} from 'rxjs/operators';

import {AuditInfo} from 'app/models/audit-info.model';
import {LocationOfInterest} from 'app/models/loi.model';
import {Result} from 'app/models/submission/result.model';
import {Submission} from 'app/models/submission/submission.model';
import {Survey} from 'app/models/survey.model';
import {User} from 'app/models/user.model';
import {AuthService} from 'app/services/auth/auth.service';
import {DataStoreService} from 'app/services/data-store/data-store.service';
import {LoadingState} from 'app/services/loading-state.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {SurveyService} from 'app/services/survey/survey.service';

@Injectable({
  providedIn: 'root',
})
export class SubmissionService {
  private submissions$: Observable<List<Submission>>;
  private selectedSubmissionId$ = new ReplaySubject<string>(1);
  private selectedSubmission$ = new Observable<Submission>();

  constructor(
    private authService: AuthService,
    private dataStore: DataStoreService,
    private loiService: LocationOfInterestService,
    private surveyService: SurveyService
  ) {
    this.submissions$ = this.authService
      .getUser$()
      .pipe(
        switchMap(user =>
          surveyService
            .getActiveSurvey$()
            .pipe(
              switchMap(survey =>
                this.loiService
                  .getSelectedLocationOfInterest$()
                  .pipe(
                    switchMap(loi =>
                      !survey || !loi || !user
                        ? of(List<Submission>())
                        : this.dataStore.getAccessibleSubmissions$(
                            survey,
                            loi,
                            user.id,
                            this.surveyService.canManageSurvey()
                          )
                    )
                  )
              )
            )
        )
      );

    this.selectedSubmission$ = this.selectedSubmissionId$.pipe(
      switchMap(submissionId =>
        this.submissions$.pipe(
          map(submissions => submissions.find(({id}) => id === submissionId)!)
        )
      )
    );
  }

  getSubmissions$(): Observable<List<Submission>> {
    return this.submissions$;
  }

  selectSubmission(submissionId: string) {
    this.selectedSubmissionId$.next(submissionId);
  }

  getSelectedSubmission$(): Observable<Submission> {
    return this.selectedSubmission$;
  }

  createNewSubmission(
    user: User,
    survey: Survey,
    loi: LocationOfInterest
  ): Submission | LoadingState {
    if (!user) {
      throw Error('Login required to create new submission.');
    }
    const job = survey.getJob(loi.jobId);
    if (!job) {
      throw Error(`Missing job ${loi.jobId}`);
    }
    const newSubmissionId = this.dataStore.generateId();
    const auditInfo = new AuditInfo(
      user,
      new Date(),
      this.dataStore.getServerTimestamp()
    );
    return new Submission(
      newSubmissionId,
      loi.id,
      job,
      auditInfo,
      auditInfo,
      Map<string, Result>([])
    );
  }
}
