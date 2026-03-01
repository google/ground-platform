/**
 * Copyright 2026 The Ground Authors.
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

import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';
import {
  DialogData,
  DialogType,
  JobDialogComponent,
} from 'app/components/edit-survey/job-dialog/job-dialog.component';

export const dirtyCheckGuard: CanDeactivateFn<any> = async () => {
  const draftSurveyService = inject(DraftSurveyService);
  const dialog = inject(MatDialog);

  if (!draftSurveyService.dirty) {
    return true;
  }

  const dialogRef = dialog.open(JobDialogComponent, {
    data: { dialogType: DialogType.UndoJobs },
    panelClass: 'small-width-dialog',
  });

  const result: DialogData = await firstValueFrom(dialogRef.afterClosed());

  return result?.dialogType === DialogType.UndoJobs;
};
