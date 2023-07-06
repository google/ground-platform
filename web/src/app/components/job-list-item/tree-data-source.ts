import {
  CollectionViewer,
  SelectionChange,
  DataSource,
} from '@angular/cdk/collections';
import {FlatTreeControl} from '@angular/cdk/tree';
import {LocationOfInterest} from 'app/models/loi.model';
import {SubmissionService} from 'app/services/submission/submission.service';
import {SurveyService} from 'app/services/survey/survey.service';
import {BehaviorSubject, merge, Subscription, Observable} from 'rxjs';
import {map, switchMap} from 'rxjs/operators';
import {List} from 'immutable';
import {Submission} from 'app/models/submission/submission.model';

/** Flat node with with information on name, level, and if it is expandable.
 * Loi specific nodes have additional fields for loi info and number of
 * submissions.
 */
export class DynamicFlatNode {
  constructor(
    public name: string,
    public level = 1,
    public expandable = false,
    // Specific for nodes that represent lois.
    public loi?: LocationOfInterest,
    public submissionCount?: number,
    // Specific for nodes that represent submissions.
    public submission?: Submission
  ) {}
}

export class DynamicDataSource implements DataSource<DynamicFlatNode> {
  dataChange = new BehaviorSubject<DynamicFlatNode[]>([]);

  // Data represents just the rendered data, only when lois are expanded is when
  // submissions get added to this and to the DOM.
  get data(): DynamicFlatNode[] {
    return this.dataChange.value;
  }
  set data(value: DynamicFlatNode[]) {
    this._treeControl.dataNodes = value;
    this.dataChange.next(value);
  }

  constructor(
    private _treeControl: FlatTreeControl<DynamicFlatNode>,
    private nodeSubscriptions: Map<string, Subscription>,
    private submissionService: SubmissionService,
    readonly surveyService: SurveyService
  ) {}

  connect(collectionViewer: CollectionViewer): Observable<DynamicFlatNode[]> {
    this._treeControl.expansionModel.changed.subscribe(change => {
      if (
        (change as SelectionChange<DynamicFlatNode>).added ||
        (change as SelectionChange<DynamicFlatNode>).removed
      ) {
        this.handleTreeControl(change as SelectionChange<DynamicFlatNode>);
      }
    });

    return merge(collectionViewer.viewChange, this.dataChange).pipe(
      map(() => this.data)
    );
  }

  disconnect(_: CollectionViewer): void {}

  /** Handle expand/collapse behaviors */
  handleTreeControl(change: SelectionChange<DynamicFlatNode>) {
    if (change.added) {
      change.added.forEach(node => this.toggleNode(node, true));
    }
    if (change.removed) {
      change.removed
        .slice()
        .reverse()
        .forEach(node => this.toggleNode(node, false));
    }
  }

  /**
   * Toggle the node, remove from display list.
   */
  toggleNode(node: DynamicFlatNode, expand: boolean) {
    const index = this.data.indexOf(node);
    if (index < 0) {
      // If cannot find the node, no op.
      return;
    }

    if (expand) {
      this.nodeSubscriptions.set(
        node.loi!.id,
        this.getSubmissions$(node.loi!).subscribe(submissions => {
          if (!submissions || submissions.size === 0) {
            // If no submissions, no op.
            return;
          }

          // TODO: Submission ids should not be displayed, but rather be
          // enumerated as 'Submission 1, Submission 2, ...'. The ids are
          // currently being shown because that information will be needed;
          // for the separate side panel that expands with more details.
          const nodes = submissions.map(submission => {
            return new DynamicFlatNode(
              submission.id,
              1,
              false,
              undefined,
              undefined,
              submission
            );
          });

          let existingSubmissions = 0;
          if (node.submissionCount) {
            // Delete all previously loaded nodes since the new value comes
            // from a subscription change and we don't know the new value.
            existingSubmissions = node.submissionCount;
          }

          this.data.splice(index + 1, existingSubmissions, ...nodes);

          // Keep track of nodes adding to delete.
          node.submissionCount = nodes.size;

          // Notify the change.
          this.dataChange.next(this.data);
        })
      );
    } else {
      const nodeId = node.loi!.id;
      this.nodeSubscriptions.get(nodeId)?.unsubscribe();
      this.nodeSubscriptions.delete(nodeId);

      let count = 0;
      for (
        let i = index + 1;
        i < this.data.length && this.data[i].level > node.level;
        i++
      ) {
        count++;
      }
      this.data.splice(index + 1, count);

      // Notify the change.
      this.dataChange.next(this.data);
    }
  }

  getSubmissions$(loi: LocationOfInterest): Observable<List<Submission>> {
    return this.surveyService
      .getActiveSurvey$()
      .pipe(
        switchMap(survey => this.submissionService.submissions$(survey, loi))
      );
  }
}
