/**
 * Copyright 2024 The Ground Authors.
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

import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

@Component({
  selector: 'ground-loi-properties-dialog',
  templateUrl: './loi-properties-dialog.component.html',
  styleUrls: ['./loi-properties-dialog.component.scss'],
})
export class LoiPropertiesDialogComponent {
  iconColor: string;
  iconName: string;
  loiDisplayName: string;
  properties: {[key: string]: string | number};

  constructor(
    private dialogRef: MatDialogRef<LoiPropertiesDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LoiPropertiesDialogComponent
  ) {
    this.iconColor = data.iconColor;
    this.iconName = data.iconName;
    this.loiDisplayName = data.loiDisplayName;
    this.properties = data.properties;
  }

  closeDialog() {
    this.dialogRef.close();
  }
}
