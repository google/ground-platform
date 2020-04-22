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
  observation?: Observation;
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
        this.observation = observation;
        this.observationFields = observation!
          .form!.fields.toOrderedMap()
          .sortBy((k, v) => k.label)
          .toList();
        this.initForm();
      });
  }

  initForm() {
    if (this.observation === undefined) {
      throw Error('Observation is not selected.');
    }
    this.observationForm = this.convertObservationToFormGroup(
      this.observation!
    );
  }

  onSave() {
    this.payLoad = JSON.stringify(this.observationForm?.getRawValue());
    console.log(this.extractResponses());
  }

  convertObservationToFormGroup(observation: Observation): FormGroup {
    const group: { [fieldId: string]: FormControl } = {};
    for (const [fieldId, field] of observation.form!.fields) {
      const response = observation!.responses?.get(fieldId);
      switch (field.type) {
        case FieldType.TEXT:
          this.addControlsForTextField(group, field, response);
          break;
        case FieldType.MULTIPLE_CHOICE:
          this.addControlsForMultipleChoiceField(group, field, response);
          break;
        default:
          throw Error(
            `Unimplemented conversion to FormControl(s) for Field with
             Type:${field.type}`
          );
      }
    }
    return this.formBuilder.group(group);
  }

  extractResponses(): Map<string, Response> {
    return new Map<string, Response>(
      this.observationFields!.map(field => [
        field.id,
        this.extractResponseForField(field),
      ])
    );
  }

  extractResponseForField(field: Field) {
    switch (field.type) {
      case FieldType.TEXT:
        return this.extractResponseForTextField(field);
      case FieldType.MULTIPLE_CHOICE:
        return this.extractResponseForMultipleChoiceField(field);
      default:
        throw Error(
          `Unimplemented Response extraction for Field with
           Type:${field.type}`
        );
    }
  }

  addControlsForTextField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const value = response?.value as string;
    group[field.id] = field.required
      ? new FormControl(value, Validators.required)
      : new FormControl(value);
  }

  extractResponseForTextField(field: Field): Response {
    return new Response(this.observationForm?.value[field.id]);
  }

  addControlsForMultipleChoiceField(
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

  extractResponseForMultipleChoiceField(field: Field): Response {
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

  addControlsForSelectOneField(
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

  extractResponseForSelectOneField(field: Field): Response {
    const selectedOption: Option = field.getMultipleChoiceOption(
      this.observationForm?.value[field.id]
    );
    return new Response(List([selectedOption]));
  }

  addControlsForSelectMultipleField(
    group: { [fieldId: string]: FormControl },
    field: Field,
    response?: Response
  ): void {
    const selectedOptions = response?.value as List<Option>;
    for (const option of field.multipleChoice!.options) {
      group[option.id] = new FormControl(selectedOptions?.contains(option));
    }
  }

  extractResponseForSelectMultipleField(field: Field): Response {
    const selectedOptions: List<Option> = field.multipleChoice!.options!.filter(
      option => this.observationForm?.value[option.id]
    );
    return new Response(selectedOptions);
  }
}
