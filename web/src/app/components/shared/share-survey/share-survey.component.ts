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
