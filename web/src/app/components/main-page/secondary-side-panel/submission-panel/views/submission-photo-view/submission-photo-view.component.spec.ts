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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Storage } from '@angular/fire/storage';

import { CloudFunctionsService } from 'app/services/cloud-functions/cloud-functions.service';

import { SubmissionPhotoViewComponent } from './submission-photo-view.component';

describe('SubmissionPhotoViewComponent', () => {
  let component: SubmissionPhotoViewComponent;
  let fixture: ComponentFixture<SubmissionPhotoViewComponent>;
  let cloudFunctionsService: jasmine.SpyObj<CloudFunctionsService>;
  let fetchStorageDownloadUrlSpy: jasmine.Spy;

  beforeEach(async () => {
    cloudFunctionsService = jasmine.createSpyObj('CloudFunctionsService', [
      'getSubmissionMediaBlob',
    ]);

    await TestBed.configureTestingModule({
      declarations: [SubmissionPhotoViewComponent],
      providers: [
        { provide: Storage, useValue: {} },
        { provide: CloudFunctionsService, useValue: cloudFunctionsService },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SubmissionPhotoViewComponent);
    component = fixture.componentInstance;
    fetchStorageDownloadUrlSpy = spyOn(
      component as unknown as {
        fetchStorageDownloadUrl(path: string): Promise<string>;
      },
      'fetchStorageDownloadUrl'
    );
  });

  it('renders the direct Storage download URL when accessible', async () => {
    fetchStorageDownloadUrlSpy.and.resolveTo(
      'https://storage.example.com/photo.jpg'
    );
    fixture.componentRef.setInput(
      'path',
      'user-media/surveys/s1/submissions/t1-uuid.jpg'
    );

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.downloadUrl()).toBe(
      'https://storage.example.com/photo.jpg'
    );
    expect(component.unavailable()).toBeFalse();
    expect(cloudFunctionsService.getSubmissionMediaBlob).not.toHaveBeenCalled();
  });

  it('falls back to exportMedia when direct Storage read returns storage/unauthorized', async () => {
    fetchStorageDownloadUrlSpy.and.rejectWith({ code: 'storage/unauthorized' });
    cloudFunctionsService.getSubmissionMediaBlob.and.resolveTo(
      new Blob(['jpeg-bytes'], { type: 'image/jpeg' })
    );
    const revokeSpy = spyOn(URL, 'revokeObjectURL').and.callThrough();

    fixture.componentRef.setInput(
      'path',
      'user-media/surveys/s1/submissions/t1-uuid.jpg'
    );
    fixture.componentRef.setInput('surveyId', 's1');
    fixture.componentRef.setInput('submissionId', 'sub1');
    fixture.componentRef.setInput('taskId', 't1');

    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(
      cloudFunctionsService.getSubmissionMediaBlob
    ).toHaveBeenCalledOnceWith('s1', 'sub1', 't1');
    expect(component.downloadUrl()).toBeDefined();
    expect(component.unavailable()).toBeFalse();

    fixture.destroy();
    expect(revokeSpy).toHaveBeenCalledTimes(1);
  });

  it('marks photo unavailable without calling exportMedia when object does not exist', async () => {
    spyOn(console, 'error');
    fetchStorageDownloadUrlSpy.and.rejectWith({
      code: 'storage/object-not-found',
    });

    fixture.componentRef.setInput(
      'path',
      'user-media/surveys/s1/submissions/missing.jpg'
    );
    fixture.componentRef.setInput('surveyId', 's1');
    fixture.componentRef.setInput('submissionId', 'sub1');
    fixture.componentRef.setInput('taskId', 't1');

    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(cloudFunctionsService.getSubmissionMediaBlob).not.toHaveBeenCalled();
    expect(component.downloadUrl()).toBeUndefined();
    expect(component.unavailable()).toBeTrue();
  });

  it('marks photo unavailable when exportMedia fallback also fails', async () => {
    spyOn(console, 'error');
    fetchStorageDownloadUrlSpy.and.rejectWith({ code: 'storage/unauthorized' });
    cloudFunctionsService.getSubmissionMediaBlob.and.rejectWith(
      new Error('404 Not Found')
    );

    fixture.componentRef.setInput(
      'path',
      'user-media/surveys/s1/submissions/t1-uuid.jpg'
    );
    fixture.componentRef.setInput('surveyId', 's1');
    fixture.componentRef.setInput('submissionId', 'sub1');
    fixture.componentRef.setInput('taskId', 't1');

    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(component.downloadUrl()).toBeUndefined();
    expect(component.unavailable()).toBeTrue();
  });
});
