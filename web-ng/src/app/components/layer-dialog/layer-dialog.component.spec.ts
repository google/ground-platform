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

import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { LayerDialogComponent } from './layer-dialog.component';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { MatDialogModule } from '@angular/material/dialog';

@Component({ selector: 'mat-dialog-content', template: '' })
class MatDialogContent { }

@Component({ selector: 'mat-dialog-actions', template: '' })
class MatDialogActions { }


describe('LayerDialogComponent', () => {
  let component: LayerDialogComponent;
  let fixture: ComponentFixture<LayerDialogComponent>;
  const dialogRef: Partial<MatDialogRef<LayerDialogComponent>> = {};

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [LayerDialogComponent, MatDialogContent, MatDialogActions],
      imports: [CommonModule, MatDialogModule, MatIconModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LayerDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
