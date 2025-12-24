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
import { MatSnackBar } from '@angular/material/snack-bar';

import { NotificationService } from 'app/services/notification/notification.service';

describe('NotificationService', () => {
  let matSnackBar: jasmine.SpyObj<MatSnackBar>;
  let service: NotificationService;

  beforeEach(() => {
    matSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    TestBed.configureTestingModule({
      providers: [{ provide: MatSnackBar, useValue: matSnackBar }],
    });
    service = TestBed.inject(NotificationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('success', () => {
    it('should open snack bar', () => {
      const testMessage = 'Success!';

      service.success(testMessage);

      expect(matSnackBar.open).toHaveBeenCalledOnceWith(
        testMessage,
        '',
        jasmine.objectContaining({
          panelClass: ['success', 'notification'],
        })
      );
    });
  });

  describe('error', () => {
    it('should open snack bar', () => {
      const testMessage = 'Error!';

      service.error(testMessage);

      expect(matSnackBar.open).toHaveBeenCalledOnceWith(
        testMessage,
        '',
        jasmine.objectContaining({
          panelClass: ['error', 'notification'],
        })
      );
    });
  });
});
