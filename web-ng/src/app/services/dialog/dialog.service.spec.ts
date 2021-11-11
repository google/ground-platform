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

import { TestBed } from '@angular/core/testing';

import { DialogService } from './dialog.service';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { of } from 'rxjs';
import { ConfirmationDialogComponent } from '../../components/confirmation-dialog/confirmation-dialog.component';

describe('DialogService', () => {
  let service: DialogService;
  let dialogSpy: jasmine.Spy;
  const dialogRefSpyObj = jasmine.createSpyObj({
    afterClosed: of({}),
    close: null,
  });
  dialogRefSpyObj.componentInstance = { body: '' }; // attach componentInstance to the spy object...

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MatDialogModule] });
    service = TestBed.inject(DialogService);
  });

  beforeEach(() => {
    dialogSpy = spyOn(TestBed.inject(MatDialog), 'open').and.returnValue(
      dialogRefSpyObj
    );
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return correct dialog without discard actions', () => {
    service.openConfirmationDialog('testTitle', 'testMessage');

    expect(dialogSpy).toHaveBeenCalled();

    expect(dialogSpy).toHaveBeenCalledWith(ConfirmationDialogComponent, {
      maxWidth: '500px',
      autoFocus: false,
      data: {
        title: 'testTitle',
        message: 'testMessage',
        showDiscardActions: false,
      },
    });
  });
});
