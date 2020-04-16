import { Component, OnInit } from '@angular/core';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { Cardinality } from '../../shared/models/form/multiple-choice.model';
import { Option } from '../../shared/models/form/option.model';
import { Observation } from '../../shared/models/observation/observation.model';
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
    this.observationForm = this.toFormGroup(observation);
  }

  toFormGroup(observation?: Observation): FormGroup {
    const group: { [fieldId: string]: FormControl } = {};
    for (const [fieldId, field] of observation!.form!.fields) {
      const observationValue = observation!.responses?.get(fieldId)?.value;
      if (field.type === FieldType.TEXT) {
        const stringValue = observationValue as string;
        group[fieldId] = field.required
          ? new FormControl(stringValue, Validators.required)
          : new FormControl(stringValue);
      } else if (field.type === FieldType.MULTIPLE_CHOICE) {
        const selectedOptionId = ((observationValue as List<
          Option
        >)?.first() as Option)?.id;
        if (field.multipleChoice?.cardinality === Cardinality.SELECT_ONE) {
          group[fieldId] = field.required
            ? new FormControl(selectedOptionId, Validators.required)
            : new FormControl(selectedOptionId);
        } else if (
          field.multipleChoice?.cardinality === Cardinality.SELECT_MULTIPLE
        ) {
          const selectedOptions = observation!.responses?.get(fieldId)
            ?.value as List<Option>;
          for (const option of field.multipleChoice.options) {
            group[option.id] = new FormControl(
              selectedOptions?.contains(option) || false
            );
          }
        }
      }
    }
    return this.formBuilder.group(group);
  }

  onSave() {
    this.payLoad = JSON.stringify(this.observationForm?.getRawValue());
  }
}
