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

import { Component, effect, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { List } from 'immutable';

import { Job } from 'app/models/job.model';
import { Survey } from 'app/models/survey.model';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import { JobService } from 'app/services/job/job.service';
import {
  SURVEYS_SHARE,
  SURVEY_SEGMENT,
} from 'app/services/navigation/navigation.constants';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { environment } from 'environments/environment';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from './job-dialog/job-dialog.component';

@Component({
  selector: 'edit-survey',
  templateUrl: './edit-survey.component.html',
  styleUrls: ['./edit-survey.component.scss'],
  standalone: false,
})
export class EditSurveyComponent {
  private surveyService = inject(SurveyService);
  private jobService = inject(JobService);
  private draftSurveyService = inject(DraftSurveyService);
  private navigationService = inject(NavigationService);
  public dialog = inject(MatDialog);

  private editSurveyPageSignal =
    this.navigationService.getEditSurveyPageSignal();
  surveyId = input<string>();

  survey?: Survey;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  production = !!(environment as any)['production'];
  sectionTitle?: string = '';
  sortedJobs = List<Job>();

  constructor() {
    effect(async () => {
      const id = this.surveyId();

      if (id) {
        await this.draftSurveyService.init(id);
        this.draftSurveyService.getSurvey$().subscribe(survey => {
          this.survey = survey;
          this.sortedJobs = this.survey.getJobsSorted();
        });
      }
    });

    effect(() => {
      const section = this.editSurveyPageSignal();

      switch (section) {
        case SURVEY_SEGMENT:
          this.sectionTitle = $localize`:@@app.editSurvey.surveyDetails.title:Survey details`;
          break;
        case SURVEYS_SHARE:
          this.sectionTitle = $localize`:@@app.editSurvey.sharing.title:Sharing`;
          break;
        default:
          this.sectionTitle = '';
          break;
      }
    });
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
      data: { dialogType, jobName: job.name ?? '' },
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

          this.navigationService.navigateToEditJob(this.survey!.id, job.id);
          break;
        case DialogType.DeleteJob:
          {
            const previousJob = this.survey?.getPreviousJob(job);

            this.draftSurveyService.deleteJob(job);

            if (previousJob) {
              this.navigationService.navigateToEditJob(
                this.draftSurveyService.getSurvey().id,
                previousJob.id
              );
            } else {
              this.navigationService.navigateToEditSurvey(this.survey!.id);
            }
          }
          break;
        default:
          break;
      }
    });
  }
}
