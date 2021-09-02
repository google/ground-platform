/**
 * Copyright 2021 Google LLC
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
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

interface DialogData {
  clickedFeatures: FeatureData[];
}

export interface FeatureData {
  featureId: string;
  color: string;
  layerName: string;
}

@Component({
  selector: 'select-feature-dialog',
  templateUrl: 'select-feature-dialog.html',
  styleUrls: ['./select-feature-dialog.component.scss'],
})
export class SelectFeatureDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<
      SelectFeatureDialogComponent,
      string | undefined
    >,
    @Inject(MAT_DIALOG_DATA)
    public data: DialogData
  ) {}

  onCancelClick(): void {
    this.dialogRef.close();
  }
}
