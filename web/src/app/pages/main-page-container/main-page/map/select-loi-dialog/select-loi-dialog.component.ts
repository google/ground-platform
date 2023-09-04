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

import {Component, Inject} from '@angular/core';
import {
  MatLegacyDialogRef as MatDialogRef,
  MAT_LEGACY_DIALOG_DATA as MAT_DIALOG_DATA,
} from '@angular/material/legacy-dialog';

interface DialogData {
  clickedLocationsOfInterest: LocationOfInterestData[];
}

export interface LocationOfInterestData {
  loiId: string;
  color: string;
  jobName: string;
}

@Component({
  selector: 'select-loi-dialog',
  templateUrl: 'select-loi-dialog.html',
  styleUrls: ['./select-loi-dialog.component.scss'],
})
export class SelectLocationOfInterestDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<
      SelectLocationOfInterestDialogComponent,
      string | undefined
    >,
    @Inject(MAT_DIALOG_DATA)
    public data: DialogData
  ) {}

  onCancelClick(): void {
    this.dialogRef.close();
  }
}
