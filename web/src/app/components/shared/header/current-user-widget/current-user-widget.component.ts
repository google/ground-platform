/**
 * Copyright 2022 The Ground Authors.
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

import { Component, ElementRef } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { AccountPopupComponent } from 'app/components/shared/header/current-user-widget/account-popup/account-popup.component';
import { AuthService } from 'app/services/auth/auth.service';

@Component({
  selector: 'ground-current-user-widget',
  templateUrl: './current-user-widget.component.html',
  styleUrls: ['./current-user-widget.component.scss'],
})
export class CurrentUserWidgetComponent {
  constructor(public auth: AuthService, private dialog: MatDialog) {}

  openProfileDialog(evt: MouseEvent): void {
    const target = new ElementRef(evt.currentTarget);
    this.dialog.open(AccountPopupComponent, {
      data: { trigger: target },
    });
  }
}
