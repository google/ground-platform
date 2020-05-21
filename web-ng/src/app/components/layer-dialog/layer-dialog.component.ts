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

import {Component, Inject, OnDestroy} from '@angular/core';
import {Observable} from 'rxjs';
import {ProjectService} from '../../services/project/project.service';
import {
  MatDialogRef,
  MAT_DIALOG_DATA,
  MatDialog,
} from '@angular/material/dialog';
import {Project} from '../../shared/models/project.model';
import {Layer} from '../../shared/models/layer.model';
import {Form} from '../../shared/models/form/form.model';
import {Subscription} from 'rxjs';
import {DataStoreService} from '../../services/data-store/data-store.service';
import {Router} from '@angular/router';
import {FormBuilder} from '@angular/forms';
import {FieldType, Field} from '../../shared/models/form/field.model';
import {StringMap} from '../../shared/models/string-map.model';
import {Map, List} from 'immutable';
import {ConfirmationDialogComponent} from '../confirmation-dialog/confirmation-dialog.component';
import {CdkDragDrop} from '@angular/cdk/drag-drop';

// To make ESLint happy:
/*global alert*/

const DEFAULT_LAYER_COLOR = '#ff9131';

@Component({
  selector: 'app-layer-dialog',
  templateUrl: './layer-dialog.component.html',
  styleUrls: ['./layer-dialog.component.css'],
})
export class LayerDialogComponent implements OnDestroy {
  lang: string;
  layerId: string;
  layer?: Layer;
  layerName!: string;
  projectId?: string;
  activeProject$: Observable<Project>;
  subscription: Subscription = new Subscription();
  fieldTypes = FieldType;
  fields: List<Field>;

  constructor(
    // tslint:disable-next-line:no-any
    @Inject(MAT_DIALOG_DATA) data: any,
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private projectService: ProjectService,
    private dataStoreService: DataStoreService,
    private router: Router,
    private formBuilder: FormBuilder,
    private confirmationDialog: MatDialog
  ) {
    this.lang = 'en';
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.layerId = data.layerId;
    this.activeProject$ = this.projectService.getActiveProject$();
    this.subscription.add(
      this.activeProject$.subscribe(project => {
        this.onProjectLoaded(project);
      })
    );
    const fields = List<Field>();
    const fieldId = this.dataStoreService.generateId();
    this.fields = fields.push(
      new Field(
        fieldId,
        1,
        StringMap({
          en: '',
        }),
        false,
        0,
        undefined
      )
    );
  }

  // TODO: move options group to separate component
  createOptionGroup() {
    return this.formBuilder.group({
      label: [''],
      code: [''],
    });
  }

  addQuestion() {
    const fieldId = this.dataStoreService.generateId();
    this.fields = this.fields.push(
      new Field(
        fieldId,
        1,
        StringMap({
          en: '',
        }),
        false,
        this.fields.size,
        undefined
      )
    );
  }

  /**
   * Delete the field of a given index.
   *
   * @param index - The index of the field
   * @returns void
   *
   */

  onFieldDelete(index: number) {
    const dialogRef = this.confirmationDialog.open(
      ConfirmationDialogComponent,
      {
        maxWidth: '500px',
        data: {
          title: 'Warning',
          message:
            'Are you sure you wish to delete this field? Any associated data will be lost. This cannot be undone.',
        },
      }
    );

    dialogRef.afterClosed().subscribe(dialogResult => {
      if (dialogResult) {
        this.fields = this.fields.splice(index, 1);
      }
    });
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
    this.layerName = this.layer?.name?.get(this.lang) || '';
    const form = this.getForms();
    if (form) {
      this.fields =
        this.getForms()
          ?.fields.toList()
          .sortBy(field => field.index) || List<Field>();
    }
    if (!this.layer) {
      throw Error('No layer exists');
    }
    this.projectId = project.id;
  }

  private getForms(): Form | undefined {
    const forms = this.layer?.forms;
    return forms ? forms.valueSeq().first() : undefined;
  }

  onSave() {
    // TODO: Wait for project to load before showing dialog.
    if (!this.projectId) {
      throw Error('Project not yet loaded');
    }
    let fields = Map<string, Field>();
    this.fields.forEach((field: Field, index: number) => {
      const layerFieldId = this.fields && this.fields.get(index)?.id;
      const fieldId = layerFieldId
        ? layerFieldId
        : this.dataStoreService.generateId();
      fields = fields.set(fieldId, field);
    });
    const form = this.getForms();
    const formId = form ? form.id : this.dataStoreService.generateId();
    const layer = new Layer(
      this.layerId,
      this.layer?.color || DEFAULT_LAYER_COLOR,
      // TODO: Make layerName Map
      StringMap({[this.lang]: this.layerName}),
      this.fields && this.fields.size > 0
        ? Map({
            [formId]: new Form(formId, fields),
          })
        : Map<string, Form>()
    );

    // TODO: Inform user layer was saved
    this.dataStoreService
      .updateLayer(this.projectId, layer)
      .then(() => this.onClose())
      .catch(() => {
        alert('Layer update failed.');
      });
  }

  onClose() {
    this.dialogRef.close();
    // TODO: refactor this path into a custom router wrapper
    return this.router.navigate([`p/${this.projectId}`]);
  }

  setLayerName(value: string) {
    this.layerName = value;
  }

  /**
   * Updates the field at given index from event emitted from form-field-editor
   *
   * @param index - The index of the field
   * @param event - updated field emitted from form-field-editor
   * @returns void
   *
   */

  onFieldUpdate(event: Field, index: number) {
    const fieldId = this.fields.get(index)?.id;
    const field = new Field(
      fieldId || '',
      event.type,
      event.label,
      event.required,
      index,
      undefined
    );
    this.fields = this.fields.set(index, field);
  }

  trackByFn(index: number) {
    return index;
  }

  drop(event: CdkDragDrop<string[]>) {
    const fieldAtPrevIndex = this.fields.get(event.previousIndex);
    const fieldAtCurrentIndex = this.fields.get(event.currentIndex);
    if (fieldAtCurrentIndex && fieldAtPrevIndex) {
      this.fields = this.fields.set(event.previousIndex, fieldAtCurrentIndex);
      this.fields = this.fields.set(event.currentIndex, fieldAtPrevIndex);
    }
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }
}
