/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { NO_ERRORS_SCHEMA } from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';

import { NotificationService } from 'app/services/notification/notification.service';

import { QrCodeDialogComponent } from './qr-code-dialog.component';

describe('QrCodeDialogComponent', () => {
  let component: QrCodeDialogComponent;
  let fixture: ComponentFixture<QrCodeDialogComponent>;

  let dialogRef: jasmine.SpyObj<MatDialogRef<QrCodeDialogComponent>>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  const surveyAppLink = 'https://example.com/survey/abc';

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    notificationService = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
    ]);

    await TestBed.configureTestingModule({
      declarations: [QrCodeDialogComponent],
      imports: [MatDialogModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { surveyAppLink } },
        { provide: NotificationService, useValue: notificationService },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(QrCodeDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose the survey app link from injected data', () => {
    expect(component.surveyAppLink).toBe(surveyAppLink);
  });

  it('should close the dialog when closeDialog is called', () => {
    component.closeDialog();

    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('should log an error and skip the copy when no canvas is found', () => {
    component.qrCodeElement = {
      nativeElement: document.createElement('div'),
    };
    spyOn(console, 'error');
    spyOn(navigator.clipboard, 'write');

    component.copyQrCodeToClipboard();

    expect(console.error).toHaveBeenCalledWith('Canvas element not found');
    expect(navigator.clipboard.write).not.toHaveBeenCalled();
  });

  it('should copy the QR code to clipboard and show a success notification', fakeAsync(() => {
    const blob = new Blob(['fake'], { type: 'image/png' });
    const wrapper = document.createElement('div');
    const canvas = document.createElement('canvas');
    spyOn(canvas, 'toBlob').and.callFake((cb: BlobCallback) => cb(blob));
    wrapper.appendChild(canvas);
    component.qrCodeElement = { nativeElement: wrapper };
    spyOn(navigator.clipboard, 'write').and.returnValue(Promise.resolve());

    component.copyQrCodeToClipboard();
    tick();

    expect(navigator.clipboard.write).toHaveBeenCalled();
    expect(notificationService.success).toHaveBeenCalledWith(
      'Survey QR code copied to clipboard'
    );
    expect(notificationService.error).not.toHaveBeenCalled();
  }));

  it('should show an error notification when clipboard write fails', fakeAsync(() => {
    const blob = new Blob(['fake'], { type: 'image/png' });
    const wrapper = document.createElement('div');
    const canvas = document.createElement('canvas');
    spyOn(canvas, 'toBlob').and.callFake((cb: BlobCallback) => cb(blob));
    wrapper.appendChild(canvas);
    component.qrCodeElement = { nativeElement: wrapper };
    spyOn(navigator.clipboard, 'write').and.returnValue(
      Promise.reject('Copy failed')
    );

    component.copyQrCodeToClipboard();
    tick();

    expect(navigator.clipboard.write).toHaveBeenCalled();
    expect(notificationService.success).not.toHaveBeenCalled();
    expect(notificationService.error).toHaveBeenCalledWith(
      'Impossible to copy Survey QR code to clipboard'
    );
  }));
});
