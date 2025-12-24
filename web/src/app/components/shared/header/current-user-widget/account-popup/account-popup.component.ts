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

import { Component, ElementRef, Inject, OnInit } from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialogConfig,
  MatDialogRef,
} from '@angular/material/dialog';
import { AuthService } from 'app/services/auth/auth.service';

@Component({
  selector: 'ground-account-popup',
  templateUrl: './account-popup.component.html',
  styleUrls: ['./account-popup.component.scss'],
  standalone: false,
})
export class AccountPopupComponent implements OnInit {
  private readonly matDialogRef: MatDialogRef<AccountPopupComponent>;
  private readonly triggerElementRef: ElementRef;

  constructor(
    matDialogRef: MatDialogRef<AccountPopupComponent>,
    public auth: AuthService,
    @Inject(MAT_DIALOG_DATA) data: { trigger: ElementRef }
  ) {
    this.matDialogRef = matDialogRef;
    this.triggerElementRef = data.trigger;
  }

  ngOnInit() {
    if (!this.triggerElementRef) {
      return;
    }
    const matDialogConfig: MatDialogConfig = new MatDialogConfig();
    matDialogConfig.position = {
      right: '12px',
      top: '60px',
    };
    this.matDialogRef.updateSize(matDialogConfig.width, matDialogConfig.height);
    this.matDialogRef.updatePosition(matDialogConfig.position);
  }

  onSignOut() {
    this.matDialogRef.close();
    this.auth.signOut();
  }
}
