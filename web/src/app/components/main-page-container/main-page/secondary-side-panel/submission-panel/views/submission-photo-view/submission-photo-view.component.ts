/**
 * Copyright 2026 The Ground Authors.
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

import {
  Component,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Storage, getDownloadURL, ref } from '@angular/fire/storage';

import { Result } from 'app/models/submission/result.model';

@Component({
  selector: 'submission-photo-view',
  templateUrl: './submission-photo-view.component.html',
  styleUrls: ['./submission-photo-view.component.scss'],
  standalone: false,
})
export class SubmissionPhotoViewComponent {
  private storage = inject(Storage);

  result = input.required<Result>();

  readonly downloadUrl = signal<string | undefined>(undefined);

  constructor() {
    effect(() => {
      const path = this.result().value as string | undefined;
      this.downloadUrl.set(undefined);
      if (!path || path.trim() === '' || path === '/') {
        console.warn('Photo task has no valid image path.');
        return;
      }
      getDownloadURL(ref(this.storage, path))
        .then(url => this.downloadUrl.set(url))
        .catch(error =>
          console.error(`Could not load image: ${path}`, error)
        );
    });
  }
}
