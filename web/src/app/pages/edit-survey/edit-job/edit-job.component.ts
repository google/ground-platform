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

import {Component, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {LoiSelectionComponent} from 'app/pages/create-survey/loi-selection/loi-selection.component';
import {Observable} from 'rxjs';
import {map} from 'rxjs/internal/operators/map';

@Component({
  selector: 'edit-job',
  templateUrl: './edit-job.component.html',
  styleUrls: ['./edit-job.component.scss'],
})
export class EditJobComponent {
  id$: Observable<string>;

  @ViewChild('loiSelection')
  loiSelection?: LoiSelectionComponent;

  constructor(route: ActivatedRoute) {
    this.id$ = route.params.pipe(map(params => params['id']));
  }
}
