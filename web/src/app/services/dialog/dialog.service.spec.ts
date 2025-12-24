/**
 * Copyright 2021 The Ground Authors.
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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import {
  ConfirmationDialogComponent,
  DialogService,
} from 'app/services/dialog/dialog.service';

describe('DialogService', () => {
  let service: DialogService;
  let matDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(() => {
    matDialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [MatDialogModule, NoopAnimationsModule],
      providers: [DialogService, { provide: MatDialog, useValue: matDialog }],
    });
    service = TestBed.inject(DialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('openConfirmationDialog', () => {
    const testTitle = 'testTitle';
    const testMessage = 'testMessage';

    it('should return correct dialog without discard actions', () => {
      service.openConfirmationDialog(testTitle, testMessage);

      expect(matDialog.open).toHaveBeenCalledWith(
        ConfirmationDialogComponent,
        jasmine.objectContaining({
          data: {
            title: testTitle,
            message: testMessage,
          },
        })
      );
    });

    it('should return correct dialog with discard actions', () => {
      service.openConfirmationDialog(testTitle, testMessage);

      expect(matDialog.open).toHaveBeenCalledWith(
        ConfirmationDialogComponent,
        jasmine.objectContaining({
          data: {
            title: testTitle,
            message: testMessage,
          },
        })
      );
    });
  });
});
