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

import { Component, effect, inject, input, signal } from '@angular/core';
import { Storage, getDownloadURL, ref } from '@angular/fire/storage';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

import { CloudFunctionsService } from 'app/services/cloud-functions/cloud-functions.service';

@Component({
  selector: 'submission-photo-view',
  templateUrl: './submission-photo-view.component.html',
  styleUrls: ['./submission-photo-view.component.scss'],
  standalone: false,
})
export class SubmissionPhotoViewComponent {
  private storage = inject(Storage);
  private cloudFunctionsService = inject(CloudFunctionsService);
  private sanitizer = inject(DomSanitizer);

  path = input.required<string | null>();
  surveyId = input<string>();
  submissionId = input<string>();
  taskId = input<string>();

  readonly downloadUrl = signal<string | SafeUrl | undefined>(undefined);
  readonly unavailable = signal(false);

  constructor() {
    effect(onCleanup => {
      const path = this.path();
      const surveyId = this.surveyId();
      const submissionId = this.submissionId();
      const taskId = this.taskId();
      this.downloadUrl.set(undefined);
      this.unavailable.set(false);
      if (!path || path.trim() === '' || path === '/') {
        console.warn('Photo task has no valid image path.');
        this.unavailable.set(true);
        return;
      }
      let active = true;
      let objectUrl: string | undefined;
      onCleanup(() => {
        active = false;
        if (objectUrl) {
          URL.revokeObjectURL(objectUrl);
        }
      });
      this.fetchStorageDownloadUrl(path)
        .then(url => {
          if (active) this.downloadUrl.set(url);
        })
        .catch(async (error: { code?: string }) => {
          if (
            error?.code === 'storage/unauthorized' &&
            surveyId &&
            submissionId &&
            taskId
          ) {
            try {
              const blob =
                await this.cloudFunctionsService.getSubmissionMediaBlob(
                  surveyId,
                  submissionId,
                  taskId
                );
              if (!active) return;
              objectUrl = URL.createObjectURL(blob);
              this.downloadUrl.set(
                this.sanitizer.bypassSecurityTrustUrl(objectUrl)
              );
              return;
            } catch (fallbackError) {
              console.error(
                `Could not load image via exportMedia: ${path}`,
                fallbackError
              );
            }
          } else {
            console.error(`Could not load image: ${path}`, error);
          }
          if (active) this.unavailable.set(true);
        });
    });
  }

  // Thin wrapper so tests can stub `getDownloadURL` without spying on an ES
  // module namespace.
  protected fetchStorageDownloadUrl(path: string): Promise<string> {
    return getDownloadURL(ref(this.storage, path));
  }
}
