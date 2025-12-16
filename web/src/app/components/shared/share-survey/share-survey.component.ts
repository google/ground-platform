import {Component} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';

import {ShareDialogComponent} from 'app/components/shared/share-dialog/share-dialog.component';

@Component({
  selector: 'share-survey',
  templateUrl: './share-survey.component.html',
  styleUrls: ['./share-survey.component.scss'],
})
export class ShareSurveyComponent {
  constructor(private dialog: MatDialog) {}

  openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, {
      width: '580px',
      autoFocus: false,
    });
  }
}
