/**
 * Copyright 2023 The Ground Authors.
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

import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
} from '@angular/core';
import { MatSelectChange } from '@angular/material/select';
import { Map } from 'immutable';

import { AclEntry } from 'app/models/acl-entry.model';
import { Role } from 'app/models/role.model';
import { Survey } from 'app/models/survey.model';
import { AuthService, ROLE_OPTIONS } from 'app/services/auth/auth.service';

@Component({
  selector: 'ground-share-list',
  templateUrl: './share-list.component.html',
  styleUrls: ['./share-list.component.scss'],
  standalone: false,
})
export class ShareListComponent implements OnChanges {
  @Input() survey?: Survey;
  @Output() onAclChange = new EventEmitter<Map<string, Role>>();

  acl: Array<AclEntry> = [];
  surveyOwnerEmail = '';

  readonly roleOptions = ROLE_OPTIONS;

  roles = Role;

  constructor(readonly authService: AuthService) {}

  async ngOnChanges(): Promise<void> {
    if (this.survey) {
      const owner = await this.authService.getUser(this.survey.ownerId);

      this.surveyOwnerEmail = owner?.email || '';

      this.acl = this.survey
        .getAclSorted()
        .entrySeq()
        .filter(([key]) => key !== this.surveyOwnerEmail)
        .map(([key, value]) => new AclEntry(key, value))
        .toArray();
    }
  }

  onRoleChange(event: MatSelectChange, index: number) {
    if (!this.acl) {
      return;
    }
    // Value holds the selected Role enum value, or -1 if "Remove" was selected.
    if (event.value < 0) {
      // Remove user.
      this.acl.splice(index, 1);
    } else {
      // Update user role.
      this.acl[index] = new AclEntry(this.acl[index].email, event.value);
    }
    // Add user owner.
    const aclUpdate = [...this.acl];
    if (this.surveyOwnerEmail) {
      aclUpdate.push(
        new AclEntry(this.surveyOwnerEmail, Role.SURVEY_ORGANIZER)
      );
    }

    this.onAclChange.emit(
      Map(aclUpdate.map(entry => [entry.email, entry.role]))
    );
  }
}
