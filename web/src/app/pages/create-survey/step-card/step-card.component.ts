/**
 * Copyright 2023 The Ground Authors.
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

import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'ground-step-card',
  templateUrl: './step-card.component.html',
  styleUrls: ['./step-card.component.scss'],
})
export class StepCardComponent implements OnInit {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() description = '';

  constructor() {}

  ngOnInit(): void {}
}
