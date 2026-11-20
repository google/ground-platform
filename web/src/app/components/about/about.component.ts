/**
 * Copyright 2024 The Ground Authors.
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

import { Location } from '@angular/common';
import { Component } from '@angular/core';

import { buildCommitId, buildVersion } from 'environments/build-info.generated';

@Component({
  selector: 'ground-about-page',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss'],
  standalone: false,
})
export class AboutComponent {
  readonly buildVersion = buildVersion;
  readonly buildCommitId = buildCommitId;

  constructor(private location: Location) {}

  onBackButtonClick() {
    this.location.back();
  }
}
