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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { FormFieldEditorComponent } from './form-field-editor.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {
  AngularFirestore,
  AngularFirestoreModule,
} from '@angular/fire/firestore';
import { BehaviorSubject } from 'rxjs';
import { MatDialogModule } from '@angular/material/dialog';
import { AngularFireModule, FIREBASE_OPTIONS } from '@angular/fire';
import { AngularFireAuthModule } from '@angular/fire/auth';
import { Router } from '@angular/router';

const firestoreStub = {
  collection: () => ({
    doc: () => ({
      valueChanges: () => new BehaviorSubject({}),
      set: () => new Promise(resolve => resolve()),
    }),
  }),
};

describe('FormFieldEditorComponent', () => {
  let component: FormFieldEditorComponent;
  let fixture: ComponentFixture<FormFieldEditorComponent>;

  beforeEach(
    waitForAsync(() => {
      TestBed.configureTestingModule({
        declarations: [FormFieldEditorComponent],
        imports: [
          AngularFireModule,
          AngularFireAuthModule,
          AngularFirestoreModule,
          FormsModule,
          ReactiveFormsModule,
          BrowserModule,
          MatSelectModule,
          MatSlideToggleModule,
          MatFormFieldModule,
          MatInputModule,
          BrowserAnimationsModule,
          MatDialogModule,
        ],
        providers: [
          { provide: FIREBASE_OPTIONS, useValue: {} },
          { provide: AngularFirestore, useValue: firestoreStub },
          { provide: Router, useValue: {} },
        ],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(FormFieldEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
