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

import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { TaskEditorModule } from 'app/components/shared/task-editor/task-editor.module';

import { TaskDetailsComponent } from './task-details.component';

@NgModule({
  declarations: [TaskDetailsComponent],
  imports: [CommonModule, TaskEditorModule],
  exports: [TaskDetailsComponent],
})
export class TaskDetailsModule {}
