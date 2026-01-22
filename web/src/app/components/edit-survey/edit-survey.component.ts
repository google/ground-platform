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

import { Component, effect, inject, input, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { List, Map } from 'immutable';
import { firstValueFrom } from 'rxjs';

import { Job } from 'app/models/job.model';
import { Role } from 'app/models/role.model';
import {
  Survey,
  SurveyDataVisibility,
  SurveyGeneralAccess,
} from 'app/models/survey.model';
import { Task } from 'app/models/task/task.model';
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

  survey = signal<Survey | undefined>(undefined);
  originalSurvey = signal<Survey | undefined>(undefined);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  production = !!(environment as any)['production'];
  sectionTitle?: string = '';
  sortedJobs = List<Job>();

  // Map from job ID to validation status.
  valid = Map<string, boolean>();

  dirty = false;

  constructor() {
    effect(async () => {
      const id = this.surveyId();

      if (id) {
        const original = await firstValueFrom(
          this.surveyService.loadSurvey$(id)
        );
        this.originalSurvey.set(original);
        this.survey.set(original);
        this.sortedJobs = original.getJobsSorted();
        this.dirty = false;
        this.valid = Map<string, boolean>();
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
    const survey = this.survey();
    if (!survey) return;

    const newJob = this.jobService.duplicateJob(
      job,
      this.jobService.getNextColor(survey.jobs)
    );

    const newSurvey = this.draftSurveyService.addOrUpdateJob(survey, newJob);
    this.survey.set(newSurvey);
    this.sortedJobs = newSurvey.getJobsSorted();
    this.dirty = true;

    this.navigationService.navigateToEditJob(newSurvey.id, newJob.id);
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
      const survey = this.survey();
      if (!survey) return;

      switch (result.dialogType) {
        case DialogType.AddJob:
        case DialogType.RenameJob: {
          const newJob = job.copyWith({
            name: result.jobName,
            color: job.color || this.jobService.getNextColor(survey.jobs),
          });
          const newSurvey = this.draftSurveyService.addOrUpdateJob(
            survey,
            newJob
          );
          this.survey.set(newSurvey);
          this.sortedJobs = newSurvey.getJobsSorted();
          this.dirty = true;

          if (dialogType === DialogType.AddJob) {
            this.valid = this.valid.set(newJob.id, false);
          }

          this.navigationService.navigateToEditJob(newSurvey.id, newJob.id);
          break;
        }
        case DialogType.DeleteJob:
          {
            const previousJob = survey.getPreviousJob(job);

            const s = this.draftSurveyService.deleteJob(survey, job.id);
            this.survey.set(s);
            this.sortedJobs = s.getJobsSorted();
            this.dirty = true;
            this.valid = this.valid.remove(job.id);

            if (previousJob) {
              this.navigationService.navigateToEditJob(s.id, previousJob.id);
            } else {
              this.navigationService.navigateToEditSurvey(s.id);
            }
          }
          break;
        default:
          break;
      }
    });
  }

  addOrUpdateTasks(jobId: string, tasks: List<Task>, valid: boolean): void {
    const survey = this.survey();
    if (!survey) return;

    this.survey.set(
      this.draftSurveyService.addOrUpdateTasks(survey, jobId, tasks)
    );
    this.valid = this.valid.set(jobId, valid);
    this.dirty = true;
  }

  addOrUpdateJob(job: Job): void {
    const survey = this.survey();
    if (!survey) return;

    const newSurvey = this.draftSurveyService.addOrUpdateJob(survey, job);
    this.survey.set(newSurvey);
    this.sortedJobs = newSurvey.getJobsSorted();
    this.dirty = true;
  }

  updateAcl(acl: Map<string, Role>): void {
    const survey = this.survey();
    if (!survey) return;
    this.survey.set(this.draftSurveyService.updateAcl(survey, acl));
    this.dirty = true;
  }

  updateGeneralAccess(generalAccess: SurveyGeneralAccess): void {
    const survey = this.survey();
    if (!survey) return;
    this.survey.set(
      this.draftSurveyService.updateGeneralAccess(survey, generalAccess)
    );
    this.dirty = true;
  }

  updateDataVisibility(dataVisibility: SurveyDataVisibility): void {
    const survey = this.survey();
    if (!survey) return;
    this.survey.set(
      this.draftSurveyService.updateDataVisibility(survey, dataVisibility)
    );
    this.dirty = true;
  }

  updateSurvey(survey: Survey): void {
    this.survey.set(survey);
    this.sortedJobs = survey.getJobsSorted();
    this.dirty = true;
  }

  isValid(): boolean {
    return this.valid.valueSeq().every(v => v);
  }

  isDirty(): boolean {
    return this.dirty;
  }

  async publish(): Promise<void> {
    const survey = this.survey();
    const original = this.originalSurvey();
    if (!survey || !original) return;

    await this.draftSurveyService.updateSurvey(survey, original);
    this.originalSurvey.set(survey);
    this.dirty = false;
  }

  cancel(): void {
    const original = this.originalSurvey();
    if (original) {
      this.survey.set(original);
      this.sortedJobs = original.getJobsSorted();
      this.dirty = false;
      this.valid = Map<string, boolean>();
    }
  }
}
