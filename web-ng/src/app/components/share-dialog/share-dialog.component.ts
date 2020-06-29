/**
 * Copyright 2020 Google LLC
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
import { MatDialogRef } from '@angular/material/dialog';
import {
  Validators,
  FormControl,
  FormGroup,
  AbstractControl,
  ValidatorFn,
} from '@angular/forms';
import { ProjectService } from '../../services/project/project.service';
import { Role } from '../../shared/models/role.model';
import { Subscription } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { MatSelectChange } from '@angular/material/select';
import { take } from 'rxjs/operators';
import { Map } from 'immutable';

@Component({
  selector: 'app-share-dialog',
  templateUrl: './share-dialog.component.html',
  styleUrls: ['./share-dialog.component.scss'],
})
export class ShareDialogComponent {
  addUserForm = new FormGroup({
    email: new FormControl('', [Validators.email, this.notInListValidator()]),
    role: new FormControl(Role.CONTRIBUTOR),
  });

  /** The id of the currently active project. */
  private projectId?: string;

  roles = Role;

  /** Roles and labels for select drop-downs. */
  readonly ROLE_OPTIONS = [
    { label: 'Contributor', value: Role.CONTRIBUTOR },
    { label: 'Manager', value: Role.MANAGER },
    { label: 'Owner', value: Role.OWNER },
    { label: 'Viewer', value: Role.VIEWER },
  ];

  /** List of ACL entries. Each entry consists of an email and a Role. */
  acl?: [string, Role][];

  /** ACL before any changes were made by the user. **/
  originalAcl?: Map<string, Role>;

  hasChanges = false;

  private subscription = new Subscription();

  constructor(
    private dialogRef: MatDialogRef<ShareDialogComponent>,
    private projectService: ProjectService
  ) {
    this.subscription.add(
      this.projectService
        .getActiveProject$()
        .pipe(take(1))
        .subscribe(p => this.onProjectLoaded(p))
    );
  }

  /**
   * Update ACL and projectId when project is loaded.
   */
  private onProjectLoaded(project: Project): void {
    this.projectId = project.id;
    this.originalAcl = project.acl;
    // Sort users by email address.
    this.acl = project.acl
      .entrySeq()
      .toList()
      .sortBy(pair => pair[0])
      .toArray();
  }

  private updateChangeState() {
    if (!this.acl || !this.originalAcl) {
      this.hasChanges = false;
    } else {
      this.hasChanges = !Map(this.acl).equals(this.originalAcl);
    }
  }

  /**
   * Add/update user role when email address is entered and add is clicked or
   * enter is pressed.
   */
  onAddUserSubmit(): void {
    // UI is hidden until project is loaded, so this should never happen.
    if (!this.projectId || !this.acl) {
      return;
    }
    const { email, role } = this.addUserForm.value;

    // Add new email/role and update change state. Validation rules prevent
    // the same email from being added twice.
    this.acl.push([email, role]);
    this.updateChangeState();

    // Clear "Add contributor" field.
    this.addUserForm.setValue({ email: '', role: Role.CONTRIBUTOR });
  }

  onRoleChange(event: MatSelectChange, email: string) {
    // TODO: Implement in follow-up commit.
    console.log(event, email);
  }

  /**
   * Close the dialog when "Done" is clicked.
   */
  onDoneClicked(): void {
    this.dialogRef.close();
  }

  /**
   * Store the ACL associated with the project.
   */
  onSaveClicked(): void {
    // TODO: Show saving spinner.
    this.projectService
      .updateAcl(this.projectId!, Map(this.acl!))
      .then(() => this.dialogRef.close());
  }

  /**
   * Clean up Rx subscription when cleaning up the component.
   */
  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private notInListValidator(): ValidatorFn {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (control: AbstractControl): { [key: string]: any } | null => {
      const emailsInAcl = this.acl?.map(pair => pair[0]) || [];
      const newEmail = control.value;
      return emailsInAcl.includes(newEmail)
        ? { forbiddenName: { value: control.value } }
        : null;
    };
  }
}
