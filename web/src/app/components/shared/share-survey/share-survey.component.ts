import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Map } from 'immutable';

import { EditSurveyComponent } from 'app/components/edit-survey/edit-survey.component';
import { ShareDialogComponent } from 'app/components/shared/share-dialog/share-dialog.component';
import { Role } from 'app/models/role.model';
import {
  SurveyDataVisibility,
  SurveyGeneralAccess,
} from 'app/models/survey.model';

@Component({
  selector: 'share-survey',
  templateUrl: './share-survey.component.html',
  styleUrls: ['./share-survey.component.scss'],
  standalone: false,
})
export class ShareSurveyComponent {
  constructor(
    private dialog: MatDialog,
    public editSurveyComponent: EditSurveyComponent
  ) {}

  openShareDialog(): void {
    const survey = this.editSurveyComponent.survey();
    if (!survey) return;

    this.dialog
      .open(ShareDialogComponent, {
        width: '580px',
        autoFocus: false,
        data: { survey },
      })
      .afterClosed()
      .subscribe((result: { acl: Map<string, Role> }) => {
        if (result) {
          this.editSurveyComponent.updateAcl(result.acl);
        }
      });
  }

  onAclChange(acl: Map<string, Role>): void {
    this.editSurveyComponent.updateAcl(acl);
  }

  onGeneralAccessChange(access: SurveyGeneralAccess): void {
    this.editSurveyComponent.updateGeneralAccess(access);
  }

  onDataVisibilityChange(visibility: SurveyDataVisibility): void {
    this.editSurveyComponent.updateDataVisibility(visibility);
  }
}
