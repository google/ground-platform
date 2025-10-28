/**
 * Copyright 2023 The Ground Authors.
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

import '@angular/localize/init';

import {Component, OnInit} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {List} from 'immutable';
import {Subscription, distinctUntilChanged, filter, startWith} from 'rxjs';

import {Job} from 'app/models/job.model';
import {Survey} from 'app/models/survey.model';
import {DraftSurveyService} from 'app/services/draft-survey/draft-survey.service';
import {JobService} from 'app/services/job/job.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {environment} from 'environments/environment';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from './job-dialog/job-dialog.component';

@Component({
  selector: 'edit-survey',
  templateUrl: './edit-survey.component.html',
  styleUrls: ['./edit-survey.component.scss'],
})
export class EditSurveyComponent implements OnInit {
  private subscription = new Subscription();

  surveyId?: string;
  survey?: Survey;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  production = !!(environment as any)['production'];
  sectionTitle?: string = '';

  constructor(
    public dialog: MatDialog,
    private surveyService: SurveyService,
    private jobService: JobService,
    private draftSurveyService: DraftSurveyService,
    private navigationService: NavigationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.navigationService
        .getSurveyId$()
        .pipe(distinctUntilChanged())
        .subscribe(async surveyId => {
          if (surveyId) {
            this.surveyId = surveyId;
            this.surveyService.activateSurvey(surveyId);
            await this.draftSurveyService.init(surveyId);
            this.draftSurveyService
              .getSurvey$()
              .subscribe(survey => (this.survey = survey));
          }
        })
    );

    this.subscription.add(
      this.router.events
        .pipe(
          filter((e): e is NavigationEnd => e instanceof NavigationEnd),
          startWith(this.router)
        )
        .subscribe(event => {
          if (event.url.endsWith('survey'))
            this.sectionTitle = $localize`:@@app.editSurvey.surveyDetails.title:Survey details`;
          else if (event.url.endsWith('share'))
            this.sectionTitle = $localize`:@@app.editSurvey.sharing.title:Sharing`;
          else this.sectionTitle = '';
        })
    );
  }

  jobs(): List<Job> {
    return this.survey!.getJobsSorted();
  }

  addJob(): void {
    this.openDialog(DialogType.AddJob, this.jobService.createNewJob());
  }

  jobRouterLink(jobId: string): string[] {
    return [`./job/${jobId}`];
  }

  onMenu(e: Event): void {
    e.preventDefault();
    e.stopImmediatePropagation();
  }

  renameJob(job: Job): void {
    this.openDialog(DialogType.RenameJob, job);
  }

  async duplicateJob(job: Job): Promise<void> {
    const newJob = this.jobService.duplicateJob(
      job,
      this.jobService.getNextColor(this.survey?.jobs)
    );

    this.draftSurveyService.addOrUpdateJob(newJob, true);

    this.navigationService.navigateToEditJob(
      this.draftSurveyService.getSurvey().id,
      newJob.id
    );
  }

  deleteJob(job: Job): void {
    this.openDialog(DialogType.DeleteJob, job);
  }

  openDialog(dialogType: DialogType, job: Job): void {
    const dialogRef = this.dialog.open(JobDialogComponent, {
      autoFocus: [DialogType.AddJob, DialogType.RenameJob].includes(dialogType)
        ? `#${JobDialogComponent.JOB_NAME_FIELD_ID}`
        : 'first-tabbable',
      data: {dialogType, jobName: job.name ?? ''},
      panelClass: 'small-width-dialog',
    });

    dialogRef.afterClosed().subscribe(async (result: DialogData) => {
      if (!result) {
        return;
      }
      switch (result.dialogType) {
        case DialogType.AddJob:
        case DialogType.RenameJob:
          this.draftSurveyService.addOrUpdateJob(
            job.copyWith({
              name: result.jobName,
              color:
                job.color || this.jobService.getNextColor(this.survey?.jobs),
            })
          );

          this.navigationService.navigateToEditJob(this.surveyId!, job.id);
          break;
        case DialogType.DeleteJob:
          {
            const previousJob = this.survey?.getPreviousJob(job);

            this.draftSurveyService.deleteJob(job);
            previousJob
              ? this.navigationService.navigateToEditJob(
                  this.draftSurveyService.getSurvey().id,
                  previousJob.id
                )
              : this.navigationService.navigateToEditSurvey(this.surveyId!);
          }
          break;
        default:
          break;
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
