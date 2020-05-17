import {
  Component,
  OnInit,
  Input,
  Output,
  EventEmitter,
  OnChanges,
} from '@angular/core';
import { FormGroup, FormBuilder } from '@angular/forms';
import { FieldType } from '../../shared/models/form/field.model';

export interface FieldTypeOptionModel {
  icon: string;
  label: string;
  type: FieldType;
}

@Component({
  selector: 'app-form-field-editor',
  templateUrl: './form-field-editor.component.html',
  styleUrls: ['./form-field-editor.component.css'],
})
export class FormFieldEditorComponent implements OnInit {
  @Input() label?: string;
  @Input() required?: boolean;
  @Input() type?: string;
  @Output() update = new EventEmitter();
  @Output() delete = new EventEmitter();
  fieldTypeOptions: FieldTypeOptionModel[] = [
    {
      icon: 'short_text',
      label: 'Text',
      type: FieldType.TEXT,
    },
    {
      icon: 'library_add_check',
      label: 'Select multiple',
      type: FieldType.MULTIPLE_CHOICE,
    },
  ];

  textFieldGroup: FormGroup;

  constructor(private formBuilder: FormBuilder) {
    this.textFieldGroup = this.formBuilder.group({
      label: [''],
      required: [false],
      type: this.fieldTypeOptions[0],
    });
  }

  ngOnInit(): void {
    this.textFieldGroup.valueChanges.subscribe(value => {
      this.update.emit({
        label: value.label,
        required: value.required,
        type: value.type.type,
      });
    });
  }

  ngOnChanges() {
    const type = this.fieldTypeOptions.find(
      fieldTypeOption => fieldTypeOption.type === Number(this.type)
    );
    this.textFieldGroup.setValue({
      label: this.label,
      required: this.required,
      type,
    });
  }

  onFieldDelete() {
    this.delete.emit();
  }

  getFieldType() {
    return this.textFieldGroup.get('type')?.value;
  }

  onFieldTypeSelect(event: FieldTypeOptionModel) {
    this.textFieldGroup.patchValue({ type: event });
  }
}
