/*
Copyright 2020 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AngularFireAuth } from '@angular/fire/auth';
import { AngularFirestore } from '@angular/fire/firestore';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { NEVER, of } from 'rxjs';
import { NavigationService } from '../../services/navigation/navigation.service';
import { FeaturePanelHeaderComponent } from './feature-panel-header.component';

describe('FeaturePanelHeaderComponent', () => {
  let component: FeaturePanelHeaderComponent;
  let fixture: ComponentFixture<FeaturePanelHeaderComponent>;

  beforeEach(async () => {
    const navigationService = {
      getProjectId$: () => of(''),
      getFeatureId$: () => of(''),
    };
    await TestBed.configureTestingModule({
      declarations: [FeaturePanelHeaderComponent],
      imports: [MatIconModule, MatListModule, MatMenuModule, MatDialogModule],
      providers: [
        { provide: Router, useValue: {} },
        { provide: AngularFirestore, useValue: {} },
        { provide: AngularFireAuth, useValue: { authState: NEVER } },
        { provide: NavigationService, useValue: navigationService },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FeaturePanelHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
