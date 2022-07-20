/**
 * Copyright 2021 Google LLC
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
import { AngularFireAuth } from '@angular/fire/auth';
import { AngularFirestore } from '@angular/fire/firestore';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Component } from '@angular/core';
import { NEVER, of } from 'rxjs';
import { AuthService } from '../../services/auth/auth.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { SurveyService } from '../../services/survey/survey.service';
import { Map } from 'immutable';
import { SurveyListComponent } from './survey-list.component';
import { UserProfilePopupComponent } from '../user-profile-popup/user-profile-popup.component';
import { MatCardModule } from '@angular/material/card';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Survey } from '../../shared/models/survey.model';
import { StringMap } from '../../shared/models/string-map.model';
import { Job } from '../../shared/models/job.model';
import { AclEntry } from '../../shared/models/acl-entry.model';
import { Role } from '../../shared/models/role.model';

@Component({ selector: 'ground-header-layout', template: '' })
class HeaderLayoutComponent {}

describe('SurveyListComponent', () => {
  let component: SurveyListComponent;
  let fixture: ComponentFixture<SurveyListComponent>;
  const dialog: Partial<MatDialog> = {};
  const dialogRef: Partial<MatDialogRef<UserProfilePopupComponent>> = {};

  const mockSurvey1 = new Survey(
    'survey001',
    StringMap({ en: 'title1' }),
    StringMap({ en: 'description1' }),
    /* jobs= */ Map({
      job001: new Job(
        'job001',
        /* index */ -1,
        'red',
        StringMap({ en: 'name' }),
        /* tasks= */ Map()
      ),
    }),
    /* acl= */ Map()
  );

  const mockSurvey2 = new Survey(
    'survey002',
    StringMap({ en: 'title2' }),
    StringMap({ en: 'description2' }),
    /* jobs= */ Map({
      job002: new Job(
        'job002',
        /* index */ -1,
        'green',
        StringMap({ en: 'name' }),
        /* tasks= */ Map()
      ),
    }),
    /* acl= */ Map()
  );

  const surveyServiceSpy = jasmine.createSpyObj('SurveyService', [
    'getAccessibleSurveys$',
    'getSurveyAcl',
  ]);
  const authServiceSpy = jasmine.createSpyObj('AuthService', [
    'canManageSurvey',
  ]);

  beforeEach(
    waitForAsync(() => {
      const navigationService = {
        newSurvey: () => NEVER,
      };

      TestBed.configureTestingModule({
        imports: [
          MatCardModule,
          MatGridListModule,
          MatButtonModule,
          MatIconModule,
        ],
        declarations: [SurveyListComponent, HeaderLayoutComponent],
        providers: [
          { provide: MatDialog, useValue: dialog },
          { provide: MatDialogRef, useValue: dialogRef },
          { provide: SurveyService, useValue: surveyServiceSpy },
          { provide: NavigationService, useValue: navigationService },
          { provide: AngularFirestore, useValue: {} },
          { provide: AngularFireAuth, useValue: {} },
          { provide: AuthService, useValue: authServiceSpy },
        ],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    surveyServiceSpy.getAccessibleSurveys$.and.returnValue(
      of<Survey[]>([mockSurvey1, mockSurvey2])
    );
    surveyServiceSpy.getSurveyAcl.and.returnValue([
      new AclEntry('test@gmail.com', Role.MANAGER),
    ]);
    authServiceSpy.canManageSurvey.and.returnValue(true);
    fixture = TestBed.createComponent(SurveyListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
