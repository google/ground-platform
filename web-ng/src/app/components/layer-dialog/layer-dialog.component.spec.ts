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

import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { Component } from '@angular/core';
import { LayerDialogComponent } from './layer-dialog.component';
import {
  MAT_DIALOG_DATA,
  MatDialogRef,
  MatDialogModule,
} from '@angular/material/dialog';
import { AngularFireModule } from '@angular/fire';
import { AngularFireAuthModule } from '@angular/fire/auth';
import { AngularFirestoreModule } from '@angular/fire/firestore';
import { environment } from '../../../environments/environment';
import { Router } from '@angular/router';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FlexLayoutModule } from '@angular/flex-layout';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { InlineEditorModule } from '../inline-editor/inline-editor.module';
import { MatIconModule } from '@angular/material/icon';

@Component({ selector: 'mat-dialog-content', template: '' })
class MatDialogContent {}

@Component({ selector: 'mat-dialog-actions', template: '' })
class MatDialogActions {}

describe('LayerDialogComponent', () => {
  let component: LayerDialogComponent;
  let fixture: ComponentFixture<LayerDialogComponent>;
  const dialogRef: Partial<MatDialogRef<LayerDialogComponent>> = {};

  beforeEach(
    waitForAsync(() => {
      const routerSpy = createRouterSpy();
      TestBed.configureTestingModule({
        declarations: [
          LayerDialogComponent,
          MatDialogContent,
          MatDialogActions,
        ],
        imports: [
          AngularFireModule.initializeApp(environment.firebaseConfig),
          AngularFireAuthModule,
          AngularFirestoreModule,
          FormsModule,
          InlineEditorModule,
          ReactiveFormsModule,
          BrowserAnimationsModule,
          FlexLayoutModule,
          MatFormFieldModule,
          MatInputModule,
          MatDialogModule,
          MatIconModule,
        ],
        providers: [
          { provide: MAT_DIALOG_DATA, useValue: { createLayer: true } },
          { provide: MatDialogRef, useValue: dialogRef },
          { provide: Router, useValue: routerSpy },
        ],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(LayerDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
