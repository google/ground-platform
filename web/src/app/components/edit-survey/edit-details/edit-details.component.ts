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

import { Component, ViewChild, computed, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { SurveyDetailsComponent } from 'app/components/create-survey/survey-details/survey-details.component';
import { DATA_SHARING_TYPE_DESCRIPTION } from 'app/models/survey.model';
import { EditSurveySession } from 'app/services/edit-survey-session/edit-survey-session';
import { NavigationService } from 'app/services/navigation/navigation.service';
import { SurveyService } from 'app/services/survey/survey.service';

import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from '../job-dialog/job-dialog.component';

interface DataSharingTermsDetails {
  description: string;
  customText?: string;
}

@Component({
  selector: 'ground-edit-details',
  templateUrl: './edit-details.component.html',
  styleUrls: ['./edit-details.component.scss'],
  standalone: false,
})
export class EditDetailsComponent {
  public dialog = inject(MatDialog);
  private editSurveySession = inject(EditSurveySession);
  private surveyService = inject(SurveyService);
  private navigationService = inject(NavigationService);

  readonly survey = this.editSurveySession.survey;

  readonly dataSharingTermsDetails = computed<DataSharingTermsDetails | undefined>(
    () => {
      const dataSharingTerms = this.survey()?.dataSharingTerms;

      if (!dataSharingTerms) return undefined;

      const { type, customText } = dataSharingTerms;

      return {
        description: DATA_SHARING_TYPE_DESCRIPTION.get(type)!,
        customText,
      };
    }
  );

  @ViewChild('surveyDetails')
  surveyDetails?: SurveyDetailsComponent;

  onDetailsChange(valid: boolean): void {
    if (this.surveyDetails) {
      const [title, description] = this.surveyDetails.toTitleAndDescription();

      this.editSurveySession.updateTitleAndDescription(
        title,
        description,
        valid
      );
    }
  }

  openDeleteSurveyDialog() {
    this.dialog
      .open(JobDialogComponent, {
        data: { dialogType: DialogType.DeleteSurvey },
        panelClass: 'small-width-dialog',
      })
      .afterClosed()
      .subscribe(async (result: DialogData) => {
        if (result?.dialogType === DialogType.DeleteSurvey) {
          this.surveyService.deleteSurvey(this.survey()!);

          this.navigationService.navigateToSurveyList();
        }
      });
  }

  openCopySurveyDialog() {
    this.dialog
      .open(JobDialogComponent, {
        data: { dialogType: DialogType.CopySurvey },
        panelClass: 'small-width-dialog',
      })
      .afterClosed()
      .subscribe(async (result: DialogData) => {
        if (result?.dialogType === DialogType.CopySurvey) {
          const { id: surveyId } = this.survey()!;

          const newSurveyId = await this.surveyService.copySurvey(surveyId);

          this.navigationService.navigateToSurveyDashboard(newSurveyId);
        }
      });
  }
}
