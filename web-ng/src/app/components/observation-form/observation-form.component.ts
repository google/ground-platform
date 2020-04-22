import { Component, OnInit } from '@angular/core';
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
import { List } from 'immutable';

@Component({
  selector: 'ground-observation-form',
  templateUrl: './observation-form.component.html',
  styleUrls: ['./observation-form.component.css'],
})
export class ObservationFormComponent {
  readonly lang: string;
  readonly fieldTypes = FieldType;
  readonly cardinality = Cardinality;
  observationForm?: FormGroup;
  observationFields?: List<Field>;
  payLoad = '';

  constructor(
    private observationService: ObservationService,
    private formBuilder: FormBuilder
  ) {
    // TODO: Make dynamic to support i18n.
    this.lang = 'en';
    observationService
      .getSelectedObservation$()
      .subscribe((observation?: Observation) => {
        this.initForm(observation);
        this.observationFields = observation!
          .form!.fields.toOrderedMap()
          .sortBy((k, v) => k.label)
          .toList();
      });
  }

  initForm(observation?: Observation) {
    if (observation === undefined) {
      throw Error('Observation is not selected.');
    }
    this.observationForm = this.getFormGroupForObservation(observation);
  }

  populateFormGroupWithControlsForSelectOneField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const selectedOptionId = ((response?.value as List<
      Option
    >)?.first() as Option)?.id;
    group[field.id] = field.required
      ? new FormControl(selectedOptionId, Validators.required)
      : new FormControl(selectedOptionId);
  }

  populateFormGroupWithControlsForSelectMultipleField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const selectedOptions = response?.value as List<Option>;
    for (const option of field.multipleChoice!.options) {
      group[option.id] = new FormControl(
        selectedOptions?.contains(option) || false
      );
    }
  }

  populateFormGroupWithControlsForMultipleChoiceField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    switch (field.multipleChoice?.cardinality) {
      case Cardinality.SELECT_ONE:
        this.populateFormGroupWithControlsForSelectOneField(
          group,
          field,
          response
        );
        return;
      case Cardinality.SELECT_MULTIPLE:
        this.populateFormGroupWithControlsForSelectMultipleField(
          group,
          field,
          response
        );
        return;
      default:
        throw Error(
          `Can not create FormControl(s) for given Cardinality:${field.multipleChoice?.cardinality}`
        );
    }
  }

  populateFormGroupWithControlsForTextField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const value = response?.value as string;
    group[field.id] = field.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  getFormGroupForObservation(observation?: Observation): FormGroup {
    const group: { [fieldId: string]: FormControl } = {};
    for (const [fieldId, field] of observation!.form!.fields) {
      const fieldResponse = observation!.responses?.get(fieldId);
      switch (field.type) {
        case FieldType.TEXT:
          this.populateFormGroupWithControlsForTextField(
            group,
            field,
            fieldResponse
          );
          break;
        case FieldType.MULTIPLE_CHOICE:
          this.populateFormGroupWithControlsForMultipleChoiceField(
            group,
            field,
            fieldResponse
          );
          break;
        default:
          throw Error(
            `Can not create FormControl(s) for given FieldType:${field.type}`
          );
      }
    }
    return this.formBuilder.group(group);
  }

  onSave() {
    this.payLoad = JSON.stringify(this.observationForm?.getRawValue());
  }
}
