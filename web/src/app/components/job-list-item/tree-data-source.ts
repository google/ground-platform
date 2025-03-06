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

import {
  CollectionViewer,
  DataSource,
  SelectionChange,
} from '@angular/cdk/collections';
import {FlatTreeControl} from '@angular/cdk/tree';
import {List} from 'immutable';
import {BehaviorSubject, Observable, Subscription, merge} from 'rxjs';
import {map, shareReplay} from 'rxjs/operators';

import {LocationOfInterest} from 'app/models/loi.model';
import {LocationOfInterestService} from 'app/services/loi/loi.service';
import {getLoiIcon} from 'app/utils/utils';

/** Flat node with with information on name, level, and if it is expandable.
 * Loi specific nodes have additional fields for loi info and number of
 * submissions.
 */
export class DynamicFlatNode {
  constructor(
    public name: string,
    public level = 1,
    public expandable = false,
    public iconName = '',
    public iconColor = '',
    public jobId: string,
    public isJob: boolean,
    public childCount: number,
    // Specific for nodes that represent lois.
    public loi?: LocationOfInterest
  ) {}
}

export class DynamicDataSource implements DataSource<DynamicFlatNode> {
  dataChange = new BehaviorSubject<DynamicFlatNode[]>([]);
  private loiSubscription: Subscription | undefined;

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
    private loiService: LocationOfInterestService
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
      // Subscribe to the lois while expanded to dynamically update if needed.
      this.loiSubscription = this.getLois$().subscribe(
        (lois: List<LocationOfInterest>) => {
          // Delete all previously loaded nodes since the new value comes from
          // a subscription change and we don't know the new value.
          this.removeLoiNodes();

          // Get lois that for specific job
          const jobLois = lois.filter(loi => {
            return loi.jobId === node.jobId;
          });

          // Create loi nodes for job tree
          const loiNodes: DynamicFlatNode[] = [];

          for (const loi of jobLois) {
            const loiNode = new DynamicFlatNode(
              /* name= */ LocationOfInterestService.getDisplayName(loi),
              /* level= */ 1,
              /* expandable= */ false,
              /* iconName= */ getLoiIcon(loi),
              /* iconColor= */ node.iconColor,
              /* jobId= */ 'undefined',
              /* isJob= */ false,
              /* childCount= */ 0,
              /* loi= */ loi
            );
            loiNodes.push(loiNode);
          }
          // Add loi nodes to the tree, needs to be reassigned to force
          // mat-tree to rerender
          this.data = this.data.concat(loiNodes);
        }
      );
    } else {
      // Remove the loi nodes and unsubscribe when closing the dropdown.
      this.removeLoiNodes();
      this.loiSubscription?.unsubscribe();
    }
  }

  getLois$(): Observable<List<LocationOfInterest>> {
    return this.loiService.getLocationsOfInterest$().pipe(shareReplay());
  }

  removeLoiNodes() {
    // First node is always job node, the rest are the loi nodes.
    this.data = [this.data[0]];
  }
}
