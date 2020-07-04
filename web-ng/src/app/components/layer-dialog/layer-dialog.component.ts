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
import {
  MatDialogRef,
  MAT_DIALOG_DATA,
  MatDialog,
} from '@angular/material/dialog';
import { Layer } from '../../shared/models/layer.model';
import { Form } from '../../shared/models/form/form.model';
import { Subscription } from 'rxjs';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { Router } from '@angular/router';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Map, List } from 'immutable';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { MarkerColorEvent } from '../edit-style-button/edit-style-button.component';
import { CdkDragDrop } from '@angular/cdk/drag-drop';

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
  layer?: Layer;
  layerName!: string;
  projectId?: string;
  subscription: Subscription = new Subscription();
  fieldTypes = FieldType;
  fields: List<Field>;
  color!: string;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    data: {
      projectId: string;
      layer?: Layer;
      createLayer: boolean;
    },
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private dataStoreService: DataStoreService,
    private router: Router,
    private confirmationDialog: MatDialog
  ) {
    this.lang = 'en';
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.fields = List<Field>();
    this.init(data.projectId, data.createLayer, data.layer);
  }

  addQuestion() {
    const newField = this.createNewField();
    this.fields = this.fields.push(newField);
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

  createNewLayer() {
    const layerId = this.dataStoreService.generateId();
    return new Layer(layerId, /* index */ -1);
  }

  createNewField() {
    const fieldId = this.dataStoreService.generateId();
    return new Field(
      fieldId,
      FieldType.TEXT,
      StringMap({
        en: '',
      }),
      /* required= */
      false,
      /* index= */
      this.fields.size
    );
  }

  init(projectId: string, createLayer: boolean, layer?: Layer) {
    if (!createLayer && !layer) {
      console.warn('User passed an invalid layer id');
    }
    this.projectId = projectId;
    if (!layer) {
      this.layer = this.createNewLayer();
      const newField = this.createNewField();
      this.fields = this.fields.push(newField);
      return;
    }
    this.layer = layer;
    this.layerName = this.layer?.name?.get(this.lang) || '';
    this.color = this.layer?.color || DEFAULT_LAYER_COLOR;
    const form = this.getForms();
    if (form) {
      this.fields =
        this.getForms()
          ?.fields.toList()
          .sortBy(field => field.index) || List<Field>();
    }
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
    // Check if there are empty fields, if empty return.
    const emptyFields = this.fields.filter(field => !field.label.get('en'));
    if (emptyFields.size) {
      return;
    }
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
      this.layer?.id || '',
      /* index */ -1,
      this.color,
      // TODO: Make layerName Map
      StringMap({ [this.lang]: this.layerName }),
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
      event.multipleChoice
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

  onMarkerColorChange(event: MarkerColorEvent) {
    this.color = event.color;
  }
}
