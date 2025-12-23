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

import { Component, Input } from '@angular/core';

import { Tasks } from '../task-form/task-form.component';
import { TaskGroup } from '../tasks-editor.component';

@Component({
  selector: 'add-task-button',
  templateUrl: './add-task-button.component.html',
  styleUrls: ['./add-task-button.component.scss'],
  standalone: false,
})
export class AddTaskButtonComponent {
  @Input() taskGroup?: TaskGroup;

  text = '';
  icon = 'question_mark';

  ngOnInit(): void {
    const { icon, label } = Tasks[this.taskGroup ?? TaskGroup.QUESTION];

    this.text = label;

    this.icon = icon;
  }
}
