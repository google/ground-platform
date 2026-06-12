import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ShareDialogComponent } from 'app/components/shared/share-dialog/share-dialog.component';
import { EditSurveySession } from 'app/services/edit-survey-session/edit-survey-session';

@Component({
  selector: 'share-survey',
  templateUrl: './share-survey.component.html',
  styleUrls: ['./share-survey.component.scss'],
  standalone: false,
})
export class ShareSurveyComponent {
  private editSurveySession = inject(EditSurveySession);
  private dialog = inject(MatDialog);

  survey = this.editSurveySession.survey;

  openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
      data: { survey: this.survey() },
    });
  }
}
