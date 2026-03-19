import { Component, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ShareDialogComponent } from 'app/components/shared/share-dialog/share-dialog.component';
import { Survey } from 'app/models/survey.model';

@Component({
  selector: 'share-survey',
  templateUrl: './share-survey.component.html',
  styleUrls: ['./share-survey.component.scss'],
  standalone: false,
})
export class ShareSurveyComponent {
  survey = input<Survey>();

  constructor(private dialog: MatDialog) {}

  openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
      data: { survey: this.survey() },
    });
  }
}
