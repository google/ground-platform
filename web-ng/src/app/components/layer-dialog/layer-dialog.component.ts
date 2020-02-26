/**
 * Copyright 2019 Google LLC
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

import { Component, Inject, OnDestroy } from '@angular/core';
import { Observable } from 'rxjs';
import { ProjectService } from '../../services/project/project.service';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Project } from '../../shared/models/project.model';
import { Layer } from '../../shared/models/layer.model';
import { Subscription } from 'rxjs';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-layer-dialog',
  templateUrl: './layer-dialog.component.html',
  styleUrls: ['./layer-dialog.component.css'],
})
export class LayerDialogComponent implements OnDestroy {
  layerId: string;
  layer?: Layer;
  projectId?: string;
  activeProject$: Observable<Project>;
  subscription: Subscription = new Subscription();
  constructor(
    // tslint:disable-next-line:no-any
    @Inject(MAT_DIALOG_DATA) data: any,
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private projectService: ProjectService,
    private dataStoreService: DataStoreService,
    private router: Router
  ) {
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.layerId = data.layerId;
    this.activeProject$ = this.projectService.getActiveProject$();
    this.subscription.add(
      this.activeProject$.subscribe(project => {
        this.onProjectLoaded(project);
      })
    );
  }

  onProjectLoaded(project: Project) {
    if (this.layerId === ':new') {
      this.layerId = this.dataStoreService.generateId();
      this.layer = {
        id: this.layerId,
      };
    } else {
      this.layer = project.layers.get(this.layerId);
    }
    if (!this.layer) {
      throw Error('No layer exists');
    }
    this.projectId = project.id;
  }

  onSave() {
    // TODO  wait for project to load before showing dialog
    if (!this.projectId) {
      throw Error('Project not yet loaded');
    }
    const layer = {
      id: this.layerId,
    };
    this.dataStoreService.updateProjectLayer(this.projectId, layer);
    this.onClose();
  }

  onClose() {
    this.dialogRef.close();
    // TODO: refactor this path into a custom router wrapper
    return this.router.navigate([`p/${this.projectId}`]);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
