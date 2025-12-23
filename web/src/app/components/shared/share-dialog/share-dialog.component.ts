/**
 * Copyright 2020 The Ground Authors.
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

import { Component } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSelectChange } from '@angular/material/select';
import { Map } from 'immutable';
import { Subscription } from 'rxjs';

import { AclEntry } from 'app/models/acl-entry.model';
import { Role } from 'app/models/role.model';
import { Survey } from 'app/models/survey.model';
import { ROLE_OPTIONS } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

@Component({
  selector: 'ground-share-dialog',
  templateUrl: './share-dialog.component.html',
  styleUrls: ['./share-dialog.component.scss'],
  standalone: false,
})
export class ShareDialogComponent {
  readonly roleOptions = ROLE_OPTIONS;

  addUserForm = new FormGroup({
    email: new FormControl('', [Validators.email, this.notInListValidator()]),
    role: new FormControl(Role.DATA_COLLECTOR),
  });

  get emailControl(): AbstractControl {
    return this.addUserForm.get('email')!;
  }

  roles = Role;

  /** List of ACL entries. Each entry consists of an email and a Role. */
  acl?: AclEntry[];

  /** ACL before any changes were made by the user. **/
  originalAcl?: Map<string, Role>;

  hasChanges = false;

  /** The active survey. */
  private survey?: Survey;

  private subscription = new Subscription();

  constructor(
    private dialogRef: MatDialogRef<ShareDialogComponent>,
    private draftSurveyService: DraftSurveyService
  ) {
    this.subscription.add(
      this.draftSurveyService
        .getSurvey$()
        .subscribe(survey => this.onSurveyLoaded(survey))
    );
  }

  /**
   * Add/update user role when email address is entered and add is clicked or
   * enter is pressed.
   */
  onAddUserSubmit(): void {
    // UI is hidden until survey is loaded, so this should never happen.
    if (!this.survey || !this.acl) {
      return;
    }
    const { email, role } = this.addUserForm.value;

    // Add new email/role and update change state. Validation rules prevent
    // the same email from being added twice.
    this.acl.push(new AclEntry(email!, role!));
    this.updateChangeState();

    // Clear "Add data collector" field.
    this.addUserForm.setValue({ email: '', role: Role.DATA_COLLECTOR });

    this.onSaveClicked();
  }

  onRoleChange(event: MatSelectChange, index: number) {
    if (!this.acl) {
      return;
    }
    // value holds the selected Role enum value, or -1 if "Remove" was selected.
    if (event.value < 0) {
      // Remove data collector.
      this.acl.splice(index, 1);
    } else {
      // Update data collector role.
      this.acl[index] = new AclEntry(this.acl[index].email, event.value);
    }
    this.updateChangeState();
  }

  /**
   * Close the dialog when "Cancel" is clicked.
   */
  onCancelClicked(): void {
    this.dialogRef.close();
  }

  /**
   * Store the ACL associated with the survey.
   */
  onSaveClicked(): void {
    // TODO: Show saving spinner.
    this.draftSurveyService.updateAcl(this.getAclMap());
    this.dialogRef.close();
  }

  /**
   * Clean up Rx subscription when cleaning up the component.
   */
  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /**
   * Update ACL and surveyId when survey is loaded.
   */
  private onSurveyLoaded(survey: Survey): void {
    this.survey = survey;
    this.originalAcl = survey.acl;
    // Sort users by email address.
    this.acl = survey.getAclEntriesSorted();
  }

  private updateChangeState() {
    if (!this.acl || !this.originalAcl) {
      this.hasChanges = false;
    } else {
      this.hasChanges = !this.getAclMap().equals(this.originalAcl);
    }
  }

  private getAclMap(): Map<string, Role> {
    if (!this.acl) {
      return Map();
    }
    return Map(this.acl.map(entry => [entry.email, entry.role]));
  }

  private notInListValidator(): ValidatorFn {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (control: AbstractControl): { [key: string]: any } | null => {
      const emailsInAcl = this.acl?.map(entry => entry.email) || [];
      const newEmail = control.value;
      return emailsInAcl.includes(newEmail)
        ? { forbiddenName: { value: control.value } }
        : null;
    };
  }
}
