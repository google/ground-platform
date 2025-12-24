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

import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { FlatTreeControl } from '@angular/cdk/tree';
import { Job } from 'app/models/job.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { getLoiIcon } from 'app/utils/utils';
import { List } from 'immutable';
import { BehaviorSubject, Observable, merge } from 'rxjs';
import { map } from 'rxjs/operators';

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
    public loi?: LocationOfInterest
  ) {}
}

export class DynamicDataSource implements DataSource<DynamicFlatNode> {
  /** Stream that emits the latest array of nodes to be rendered by the tree. */
  dataChange = new BehaviorSubject<DynamicFlatNode[]>([]);

  /** The current set of nodes displayed in the tree. */
  get data(): DynamicFlatNode[] {
    return this.dataChange.value;
  }
  set data(value: DynamicFlatNode[]) {
    this._treeControl.dataNodes = value;
    this.dataChange.next(value);
  }

  private job?: Job;
  private lois: List<LocationOfInterest> = List();

  constructor(private _treeControl: FlatTreeControl<DynamicFlatNode>) {}

  setJobAndLois(job: Job, lois: List<LocationOfInterest>) {
    this.job = job;
    this.lois = lois;

    let jobNode = this.data.find(n => n.isJob && n.jobId === job.id);

    if (!jobNode) {
      jobNode = this.createJobNode(job, lois);
    } else {
      jobNode.name = job.name!;
      jobNode.iconColor = job.color!;
      jobNode.childCount = lois.size;
    }

    if (this._treeControl.isExpanded(jobNode)) {
      const loiNodes = this.createLoiNodes(lois);

      this.data = [jobNode, ...loiNodes];
    } else {
      this.data = [jobNode];
    }
  }

  expandJob(node: DynamicFlatNode) {
    if (!node.isJob) return;

    const loiNodes = this.createLoiNodes(this.lois);

    this.data = [node, ...loiNodes];
  }

  collapseJob(node: DynamicFlatNode) {
    if (!node.isJob) return;

    this.data = [node];
  }

  /** Creates a top-level node representing the Job. */
  private createJobNode(
    job: Job,
    lois: List<LocationOfInterest>
  ): DynamicFlatNode {
    return new DynamicFlatNode(
      /* name= */ job!.name!,
      /* level= */ 0,
      /* expandable= */ true,
      /* iconName= */ 'label',
      /* iconColo= */ job!.color!,
      /* jobId= */ job!.id,
      /* isJob= */ true,
      /* childCount= */ lois.size
    );
  }

  /** Maps a list of LOIs into flat tree nodes. */
  private createLoiNodes(lois: List<LocationOfInterest>): DynamicFlatNode[] {
    return lois
      .map(
        loi =>
          new DynamicFlatNode(
            /* name= */ LocationOfInterestService.getDisplayName(loi),
            /* level= */ 1,
            /* expandable= */ false,
            /* iconName= */ getLoiIcon(loi),
            /* iconColo= */ this.job!.color!,
            /* jobId= */ this.job!.id,
            /* isJob= */ false,
            /* childCount= */ 0,
            /* loi */ loi
          )
      )
      .toArray();
  }

  /** Used by the CDK Tree to connect to the data source.
   * This is called once by the tree when it is initialized.
   */
  connect(collectionViewer: CollectionViewer): Observable<DynamicFlatNode[]> {
    return merge(collectionViewer.viewChange, this.dataChange).pipe(
      map(() => this.data)
    );
  }

  /** Used by the CDK Tree to disconnect from the data source.
   * This is called when the tree is destroyed.
   */
  disconnect(_: CollectionViewer): void {
    // Currently no cleanup needed as dataChange is handled by the tree subscription.
  }
}
