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

import { Component, Inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Map } from 'immutable';

import { AclEntry } from 'app/models/acl-entry.model';
import { Role } from 'app/models/role.model';
import { Survey } from 'app/models/survey.model';
import { ROLE_OPTIONS } from 'app/services/auth/auth.service';

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

  constructor(
    private dialogRef: MatDialogRef<ShareDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { survey: Survey }
  ) {
    if (data.survey) {
      this.onSurveyLoaded(data.survey);
    }
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
    this.dialogRef.close({ acl: this.getAclMap() });
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
