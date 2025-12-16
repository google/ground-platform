/**
 * Copyright 2020 The Ground Authors.
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
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
} from '@angular/core';

@Component({
  selector: 'inline-editor',
  templateUrl: './inline-editor.component.html',
  styleUrls: ['./inline-editor.component.scss'],
})
export class InlineEditorComponent {
  @Input() data = '';
  @Input() placeholder = '';
  @Output() focusOut: EventEmitter<string> = new EventEmitter<string>();

  @ViewChild('input')
  inputElement!: ElementRef;

  constructor() {}

  onFocusOut(event: FocusEvent) {
    const {target} = event;
    if (!target || !(target instanceof HTMLInputElement)) return;
    this.focusOut.emit(target.value);
  }

  handleKeyPress(event: KeyboardEvent) {
    const {key, target} = event;
    if (!target || !(target instanceof HTMLInputElement)) return;
    switch (key) {
      case 'Enter':
        target.blur();
        break;
      case 'Escape':
        target.value = this.data;
        target.blur();
        break;
      default:
      // n/a.
    }
  }
}
