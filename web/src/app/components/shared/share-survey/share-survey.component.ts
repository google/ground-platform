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

import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';

import { ShareDialogComponent } from 'app/components/shared/share-dialog/share-dialog.component';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

@Component({
  selector: 'share-survey',
  templateUrl: './share-survey.component.html',
  styleUrls: ['./share-survey.component.scss'],
  standalone: false,
})
export class ShareSurveyComponent {
  private draftSurveyService = inject(DraftSurveyService);
  private dialog = inject(MatDialog);

  survey = toSignal(this.draftSurveyService.getSurvey$());

  openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
      data: { survey: this.survey() },
    });
  }
}
