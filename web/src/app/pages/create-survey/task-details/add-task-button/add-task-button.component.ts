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

import {Component, Input} from '@angular/core';
import {TaskGroup} from '../task-details.component';

@Component({
  selector: 'add-task-button',
  templateUrl: './add-task-button.component.html',
  styleUrls: ['./add-task-button.component.scss'],
})
export class AddTaskButtonComponent {
  @Input() taskGroup?: TaskGroup;

  text = '';
  icon = '';

  ngOnInit(): void {
    switch (this.taskGroup) {
      case TaskGroup.QUESTION:
        this.text = 'Answer a question';
        this.icon = 'forum';
        break;
      case TaskGroup.PHOTO:
        this.text = 'Take a photo';
        this.icon = 'photo_camera';
        break;
      case TaskGroup.DROP_PIN:
        this.text = 'Drop a pin';
        this.icon = 'pin_drop';
        break;
      case TaskGroup.DRAW_AREA:
        this.text = 'Draw an area';
        this.icon = 'draw';
        break;
      case TaskGroup.SUGGEST_LOI:
        this.text = 'Capture location';
        this.icon = 'share_location';
        break;
      default:
        this.text = 'Undefined Task';
        this.icon = 'question_mark';
    }
  }
}
