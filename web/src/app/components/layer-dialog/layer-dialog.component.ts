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

import {
  ChangeDetectorRef,
  Component,
  Inject,
  OnDestroy,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { DialogService } from '../../services/dialog/dialog.service';
import { Layer } from '../../shared/models/layer.model';
import { Form } from '../../shared/models/form/form.model';
import { Subscription } from 'rxjs';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { StringMap } from '../../shared/models/string-map.model';
import { List } from 'immutable';
import { MarkerColorEvent } from '../edit-style-button/edit-style-button.component';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { LayerService } from '../../services/layer/layer.service';
import { FormFieldEditorComponent } from '../form-field-editor/form-field-editor.component';
import { NavigationService } from '../../services/navigation/navigation.service';

// To make ESLint happy:
/*global alert*/

@Component({
  selector: 'app-layer-dialog',
  templateUrl: './layer-dialog.component.html',
  styleUrls: ['./layer-dialog.component.scss'],
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
  defaultLayerColor: string;
  form?: Form;
  @ViewChildren(FormFieldEditorComponent)
  formFieldEditors?: QueryList<FormFieldEditorComponent>;
  contributorsCanAddPoints = true;
  contributorsCanAddPolygons = true;

  constructor(
    @Inject(MAT_DIALOG_DATA)
    data: {
      projectId: string;
      layer?: Layer;
      createLayer: boolean;
    },
    private dialogRef: MatDialogRef<LayerDialogComponent>,
    private dialogService: DialogService,
    private layerService: LayerService,
    private navigationService: NavigationService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.lang = 'en';
    this.defaultLayerColor = '#ff9131';
    // Disable closing on clicks outside of dialog.
    dialogRef.disableClose = true;
    this.fields = List<Field>();
    this.init(data.projectId, data.createLayer, data.layer);
    this.dialogRef.keydownEvents().subscribe(event => {
      if (event.key === 'Escape') {
        this.close();
      }
    });
  }

  addQuestion() {
    const newField = this.layerService.createField(
      FieldType.TEXT,
      /* label= */
      '',
      /* required= */
      false,
      /* index= */
      this.fields.size
    );
    this.fields = this.fields.push(newField);
    this.markFormFieldsTouched();
    this.focusNewQuestion();
  }

  /**
   * Delete the field of a given index.
   *
   * @param index - The index of the field
   * @returns void
   *
   */
  onFieldDelete(index: number) {
    this.dialogService
      .openConfirmationDialog(
        'Warning',
        'Are you sure you wish to delete this question? Any associated data ' +
          'will be lost. This cannot be undone.'
      )
      .afterClosed()
      .subscribe(dialogResult => {
        if (dialogResult) {
          this.fields = this.fields.splice(index, 1);
        }
      });
  }

  init(projectId: string, createLayer: boolean, layer?: Layer) {
    if (!createLayer && !layer) {
      console.warn('User passed an invalid layer id');
    }
    this.projectId = projectId;
    this.layer = layer;
    this.layerName = this.layer?.name?.get(this.lang) || '';
    this.color = this.layer?.color || this.defaultLayerColor;
    if (!layer) {
      this.layer = this.layerService.createNewLayer();
      this.addQuestion();
      return;
    }
    this.contributorsCanAddPoints =
      this.layer?.contributorsCanAdd?.includes('points') || false;
    this.contributorsCanAddPolygons =
      this.layer?.contributorsCanAdd?.includes('polygons') || false;
    this.form = this.layerService.getForm(this.layer);
    if (this.form) {
      this.fields =
        this.form?.fields.toList().sortBy(field => field.index) ||
        List<Field>();
    } else {
      this.addQuestion();
    }
  }

  async onSave() {
    if (!this.projectId) {
      throw Error('Project not yet loaded');
    }

    if (!this.formFieldEditors) {
      return;
    }

    this.markFormFieldsTouched();

    for (const editor of this.formFieldEditors) {
      if (editor.formGroup.invalid || !this.isFieldOptionsValid(editor)) {
        return;
      }
    }
    const fields = this.layerService.convertFieldsListToMap(this.fields);
    const formId = this.form?.id;
    const forms = this.layerService.createForm(formId, fields);
    const allowedFeatureTypes: string[] = [];
    if (this.contributorsCanAddPoints) {
      allowedFeatureTypes.push('points');
    }
    if (this.contributorsCanAddPolygons) {
      allowedFeatureTypes.push('polygons');
    }
    const layer = new Layer(
      this.layer?.id || '',
      /* index */ this.layer?.index || -1,
      this.color,
      // TODO: Make layerName Map
      StringMap({ [this.lang]: this.layerName.trim() }),
      forms,
      allowedFeatureTypes
    );
    this.addOrUpdateLayer(this.projectId, layer);
  }

  private isFieldOptionsValid(
    formFieldEditor: FormFieldEditorComponent
  ): boolean {
    if (!formFieldEditor.optionEditors) {
      return true;
    }
    for (const editor of formFieldEditor.optionEditors) {
      if (editor.optionGroup.invalid) {
        return false;
      }
    }
    return true;
  }

  private addOrUpdateLayer(projectId: string, layer: Layer) {
    // TODO: Inform user layer was saved
    this.layerService
      .addOrUpdateLayer(projectId, layer)
      .then(() => this.close())
      .catch(err => {
        console.error(err);
        alert('Layer update failed.');
      });
  }

  onCancel(): void {
    if (!this.hasUnsavedChanges()) {
      this.close();
      return;
    }
    this.dialogService
      .openConfirmationDialog(
        'Discard changes',
        'Unsaved changes to this layer will be lost. Are you sure?',
        /* showDiscardActions= */ true
      )
      .afterClosed()
      .subscribe(async dialogResult => {
        if (dialogResult) {
          this.close();
        }
      });
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
    if (!fieldAtPrevIndex) {
      return;
    }
    this.fields = this.fields.delete(event.previousIndex);
    this.fields = this.fields.insert(event.currentIndex, fieldAtPrevIndex);
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onMarkerColorChange(event: MarkerColorEvent) {
    this.color = event.color;
  }

  private markFormFieldsTouched(): void {
    this.formFieldEditors?.forEach(editor => {
      this.markOptionsTouched(editor);
      editor.labelControl.markAsTouched();
    });
  }

  private markOptionsTouched(editor: FormFieldEditorComponent): void {
    editor.optionEditors?.forEach(editor => {
      editor.optionGroup.markAllAsTouched();
    });
  }

  private focusNewQuestion(): void {
    if (this.formFieldEditors?.length) {
      this.cdr.detectChanges();
      const question = this.formFieldEditors.last;
      question?.questionInput?.nativeElement.focus();
    }
  }

  private isFieldOptionsDirty(
    formFieldEditor: FormFieldEditorComponent
  ): boolean {
    if (!formFieldEditor.optionEditors) {
      return true;
    }
    for (const editor of formFieldEditor.optionEditors) {
      if (editor.optionGroup.dirty) {
        return false;
      }
    }
    return true;
  }

  private hasUnsavedChanges(): boolean {
    if (!this.formFieldEditors) {
      return false;
    }
    for (const editor of this.formFieldEditors) {
      if (editor.formGroup.dirty || !this.isFieldOptionsDirty(editor)) {
        return true;
      }
    }
    return false;
  }

  private close(): void {
    this.dialogRef.close();
    // TODO: Add closeLayerDialog() in NavigationService that removes the layer fragment.
    return this.navigationService.selectProject(this.projectId!);
  }
}
