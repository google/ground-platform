/**
 * Copyright 2020 Google LLC
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

import { Component } from '@angular/core';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { Cardinality } from '../../shared/models/form/multiple-choice.model';
import { Option } from '../../shared/models/form/option.model';
import { Observation } from '../../shared/models/observation/observation.model';
import { Response } from '../../shared/models/observation/response.model';
import { ObservationService } from '../../services/observation/observation.service';
import {
  FormGroup,
  FormBuilder,
  FormControl,
  Validators,
} from '@angular/forms';
import { List, Map } from 'immutable';
import { DataStoreService } from '../../services/data-store/data-store.service';
import { Project } from '../../shared/models/project.model';
import { ProjectService } from '../../services/project/project.service';
import { LoadingState } from '../../services/loading-state.model';
import { Observable } from 'rxjs';
import { first } from 'rxjs/operators';
import { Layer } from '../../shared/models/layer.model';
import { FeatureService } from '../../services/feature/feature.service';
import { switchMap, map } from 'rxjs/operators';
import { AuthService } from '../../services/auth/auth.service';
import { AuditInfo } from '../../shared/models/audit-info.model';
import { LayerListItemActionsType } from '../layer-list-item/layer-list-item.component';
import { NavigationService } from '../../services/navigation/navigation.service';

// To make ESLint happy:
/*global alert*/

@Component({
  selector: 'ground-observation-form',
  templateUrl: './observation-form.component.html',
  styleUrls: ['./observation-form.component.scss'],
})
export class ObservationFormComponent {
  readonly lang: string;
  readonly fieldTypes = FieldType;
  readonly cardinality = Cardinality;
  readonly layerListItemActionsType = LayerListItemActionsType;
  readonly layer$: Observable<Layer>;
  projectId?: string;
  observation?: Observation;
  observationForm?: FormGroup;
  observationFields?: List<Field>;

  constructor(
    private dataStoreService: DataStoreService,
    private authService: AuthService,
    private formBuilder: FormBuilder,
    private navigationService: NavigationService,
    observationService: ObservationService,
    projectService: ProjectService,
    featureService: FeatureService
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    projectService.getActiveProject$().subscribe((project?: Project) => {
      this.projectId = project?.id;
    });
    observationService
      .getSelectedObservation$()
      .subscribe((observation?: Observation | LoadingState) =>
        this.onSelectObservation(observation)
      );
    this.layer$ = projectService
      .getActiveProject$()
      .pipe(
        switchMap(project =>
          featureService
            .getSelectedFeature$()
            .pipe(map(feature => project.layers.get(feature.layerId)!))
        )
      );
  }

  onCancel() {
    this.navigateToFeature();
  }

  onSave() {
    this.authService
      .getUser$()
      .pipe(first())
      .subscribe(user => {
        if (!user) {
          throw Error('Login required to update observation.');
        }
        const lastModified = new AuditInfo(
          user,
          /*clientTime=*/ new Date(),
          /*serverTime=*/ this.dataStoreService.getServerTimestamp()
        );
        const updatedResponses: Map<string, Response> = this.extractResponses();
        const updatedObservation = this.observation!.withResponsesAndLastModified(
          updatedResponses,
          lastModified
        );
        this.dataStoreService
          .updateObservation(this.projectId!, updatedObservation)
          .then(() => {
            this.observationForm?.markAsPristine();
            return this.navigateToFeature();
          })
          .catch(() => {
            alert('Observation update failed.');
          });
      });
  }

  private onSelectObservation(observation?: Observation | LoadingState) {
    if (
      observation === LoadingState.NOT_LOADED &&
      this.observationForm?.dirty
    ) {
      if (
        confirm(
          'You have unsaved changes in observation form, do you want to save them?'
        )
      ) {
        this.onSave();
      } else {
        this.observationForm = undefined;
      }
    }
    if (observation instanceof Observation) {
      this.observation = observation;
      this.observationFields = observation!
        .form!.fields!.toOrderedMap()
        .sortBy(entry => entry.index)
        .toList();
      this.initForm();
    }
  }

  private initForm() {
    if (this.observation === undefined) {
      throw Error('Observation is not selected.');
    }
    this.observationForm = this.convertObservationToFormGroup(
      this.observation!
    );
  }

  private navigateToFeature() {
    this.navigationService.clearObservationId();
  }

  private convertObservationToFormGroup(observation: Observation): FormGroup {
    const group: { [fieldId: string]: FormControl } = {};
    for (const [fieldId, field] of observation.form!.fields) {
      const response = observation!.responses?.get(fieldId);
      switch (field.type) {
        case FieldType.TEXT:
          this.addControlsForTextField(group, field, response);
          break;
        case FieldType.NUMBER:
          this.addControlsForNumberField(group, field, response);
          break;
        case FieldType.MULTIPLE_CHOICE:
          this.addControlsForMultipleChoiceField(group, field, response);
          break;
        default:
          console.debug(`Skipping unsupported field type: ${field.type}`);
      }
    }
    return this.formBuilder.group(group);
  }

  private extractResponses(): Map<string, Response> {
    return Map<string, Response>(
      this.observationFields!.map(field => [
        field.id,
        this.extractResponseForField(field),
      ])
    );
  }

  private extractResponseForField(field: Field) {
    switch (field.type) {
      case FieldType.TEXT:
        return this.extractResponseForTextField(field);
      case FieldType.NUMBER:
        return this.extractResponseForNumberField(field);
      case FieldType.MULTIPLE_CHOICE:
        return this.extractResponseForMultipleChoiceField(field);
      default:
        throw Error(
          `Unimplemented Response extraction for Field with
           Type:${field.type}`
        );
    }
  }

  private addControlsForTextField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const value = response?.value as string;
    group[field.id] = field.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  private addControlsForNumberField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const value = response?.value as number;
    group[field.id] = field.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  private extractResponseForTextField(field: Field): Response {
    return new Response(this.observationForm?.value[field.id]);
  }

  private extractResponseForNumberField(field: Field): Response {
    return new Response(this.observationForm?.value[field.id]);
  }

  private addControlsForMultipleChoiceField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    switch (field.multipleChoice?.cardinality) {
      case Cardinality.SELECT_ONE:
        this.addControlsForSelectOneField(group, field, response);
        return;
      case Cardinality.SELECT_MULTIPLE:
        this.addControlsForSelectMultipleField(group, field, response);
        return;
      default:
        throw Error(
          `Unimplemented conversion to FormControl(s) for Field with
           Cardinality:${field.multipleChoice?.cardinality}`
        );
    }
  }

  private extractResponseForMultipleChoiceField(field: Field): Response {
    switch (field.multipleChoice?.cardinality) {
      case Cardinality.SELECT_ONE:
        return this.extractResponseForSelectOneField(field);
      case Cardinality.SELECT_MULTIPLE:
        return this.extractResponseForSelectMultipleField(field);
      default:
        throw Error(
          `Unimplemented Response extraction for Field with
           Cardinality:${field.multipleChoice?.cardinality}`
        );
    }
  }

  private addControlsForSelectOneField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const selectedOptionId = ((response?.value as List<Option>)?.first() as Option)
      ?.id;
    group[field.id] = field.required
      ? new FormControl(selectedOptionId, Validators.required)
      : new FormControl(selectedOptionId);
  }

  private extractResponseForSelectOneField(field: Field): Response {
    const selectedOption: Option = field.getMultipleChoiceOption(
      this.observationForm?.value[field.id]
    );
    return new Response(List([selectedOption]));
  }

  private addControlsForSelectMultipleField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const selectedOptions = response?.value as List<Option>;
    for (const option of field.multipleChoice!.options) {
      group[option.id] = new FormControl(selectedOptions?.contains(option));
    }
  }

  private extractResponseForSelectMultipleField(field: Field): Response {
    const selectedOptions: List<Option> = field.multipleChoice!.options!.filter(
      option => this.observationForm?.value[option.id]
    );
    return new Response(selectedOptions);
  }
}
