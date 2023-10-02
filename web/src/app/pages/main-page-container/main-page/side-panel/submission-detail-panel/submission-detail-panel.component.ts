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
