/**
 * Copyright 2023 Google LLC
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

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {NavigationService} from 'app/services/navigation/navigation.service';

@Component({
  selector: 'error',
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.scss'],
})
export class ErrorComponent implements OnInit {
  constructor(private route: ActivatedRoute) {}

  error = '';

  ngOnInit(): void {
    this.error = this.route.snapshot.paramMap.get('error') ?? '';
  }
}
