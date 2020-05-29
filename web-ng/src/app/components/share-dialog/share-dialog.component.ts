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
import { Validators, FormControl, FormGroup } from '@angular/forms';
import { ProjectService } from '../../services/project/project.service';
import { Role } from '../../shared/models/role.model';
import { Subscription, ReplaySubject } from 'rxjs';
import { List } from 'immutable';
import { Project } from '../../shared/models/project.model';

@Component({
  selector: 'app-share-dialog',
  templateUrl: './share-dialog.component.html',
  styleUrls: ['./share-dialog.component.scss'],
})
export class ShareDialogComponent {
  addUserForm = new FormGroup({
    email: new FormControl('', [Validators.email]),
  });

  private projectId?: string;

  /** List of acl entries. Each entry consists of an email and a Role. */
  acl = new ReplaySubject<List<[string, Role]>>();

  private subscription = new Subscription();

  constructor(
    private dialogRef: MatDialogRef<ShareDialogComponent>,
    private projectService: ProjectService
  ) {
    this.subscription.add(
      this.projectService
        .getActiveProject$()
        .subscribe(p => this.onProjectLoaded(p))
    );
  }

  /**
   * Update ACL and projectId when project is loaded.
   */
  private onProjectLoaded(project: Project): void {
    this.projectId = project.id;
    this.acl.next(
      project.acl
        .entrySeq()
        .toList()
        .sortBy(pair => pair[0])
    );
  }

  /**
   * Add/update user role when email address is entered and add is clicked or
   * enter is pressed.
   */
  onAddUserSubmit(): void {
    if (!this.projectId) {
      return;
    }
    // TODO: Allow setting role.
    this.projectService.updateRole(
      this.projectId,
      this.addUserForm.value['email'],
      Role.MANAGER
    );
    // TODO: Show saving / saved status.
    this.addUserForm.reset();
  }

  /**
   * Close the dialog when "Done" is clicked.
   */
  onDoneClicked(): void {
    this.dialogRef.close();
  }

  /**
   * Clean up Rx subscription when cleaning up the component.
   */
  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
