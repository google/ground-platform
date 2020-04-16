import { Component, OnInit } from '@angular/core';
import { FieldType, Field } from '../../shared/models/form/field.model';
import { Observation } from '../../shared/models/observation/observation.model';
import { Observable } from 'rxjs';
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
        console.log('got observation');
        this.initForm(observation);
        this.observationFields = observation!.form!.fields.toOrderedMap().sortBy((k, v) => k.label).toList();
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
      const fieldValue = observation!.responses?.get(fieldId)?.value || '';
      group[fieldId] = field.required
        ? new FormControl(fieldValue, Validators.required)
        : new FormControl(fieldValue);
    }
    console.log(group);
    return this.formBuilder.group(group);
  }

  onSave() {

    this.payLoad = JSON.stringify(this.observationForm?.getRawValue());
  }
}
