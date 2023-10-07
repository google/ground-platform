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

import {Component, EventEmitter, Input, Output} from '@angular/core'; // First, import Input
import {Submission} from 'app/models/submission/submission.model';

@Component({
  selector: 'submission-detail-panel',
  templateUrl: './submission-detail-panel.component.html',
  styleUrls: ['./submission-detail-panel.component.scss'],
})
export class SubmissionDetailPanelComponent {
  @Input() submission!: Submission;
  @Output() closePanel = new EventEmitter<boolean>();

  closeDetailPanel() {
    // Tell main component to hide this submission detail panel
    this.closePanel.emit(true);
  }

  getTaskLabel(taskId: string) {
    return this.submission.job?.tasks?.get(taskId)?.label;
  }
}
