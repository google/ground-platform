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

import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class DrawingToolsService {
  private editMode$: BehaviorSubject<EditMode> = new BehaviorSubject<EditMode>(
    EditMode.None
  );
  private disabled$: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(
    false
  );
  private selectedJobId: string | undefined;

  getDisabled$(): BehaviorSubject<boolean> {
    return this.disabled$;
  }

  setDisabled(disabled: boolean) {
    return this.disabled$.next(disabled);
  }

  getSelectedJobId(): string | undefined {
    return this.selectedJobId;
  }

  setSelectedJobId(selectedJobId: string | undefined) {
    this.selectedJobId = selectedJobId;
  }

  getEditMode$(): BehaviorSubject<EditMode> {
    return this.editMode$;
  }

  setEditMode(editMode: EditMode) {
    this.editMode$.next(editMode);
  }
}

export enum EditMode {
  None,
  AddPoint,
  AddPolygon,
}
