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

import { Component, OnInit, ElementRef } from '@angular/core';
import { AuthService } from './../../services/auth/auth.service';
import { UserProfilePopupComponent } from '../../components/user-profile-popup/user-profile-popup.component';
import { MatDialog } from '@angular/material/dialog';
import { ShareDialogComponent } from '../share-dialog/share-dialog.component';

@Component({
  selector: 'app-project-header',
  templateUrl: './project-header.component.html',
  styleUrls: ['./project-header.component.scss'],
})
export class ProjectHeaderComponent implements OnInit {
  constructor(public auth: AuthService, private dialog: MatDialog) {}

  ngOnInit() {}

  openProfileDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog.open(UserProfilePopupComponent, {
      data: { trigger: target },
    });
  }

  private openShareDialog(): void {
    this.dialog.open(ShareDialogComponent, { autoFocus: false });
  }
}
