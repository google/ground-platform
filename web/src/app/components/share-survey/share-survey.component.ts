import {Component} from '@angular/core';
import {MatLegacyDialog as MatDialog} from '@angular/material/legacy-dialog';
import {ShareDialogComponent} from 'app/components/share-dialog/share-dialog.component';

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
