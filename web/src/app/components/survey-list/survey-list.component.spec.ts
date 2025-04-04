/**
 * Copyright 2021 The Ground Authors.
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

import {Component} from '@angular/core';
import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';
import {AngularFireAuth} from '@angular/fire/compat/auth';
import {AngularFirestore} from '@angular/fire/compat/firestore';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatDialog} from '@angular/material/dialog';
import {MatGridListModule} from '@angular/material/grid-list';
import {MatIconModule} from '@angular/material/icon';
import {By} from '@angular/platform-browser';
import {
  TranslateModule,
  TranslateService,
  TranslateStore,
} from '@ngx-translate/core';
import {List, Map} from 'immutable';
import {of} from 'rxjs';

import {AclEntry} from 'app/models/acl-entry.model';
import {Job} from 'app/models/job.model';
import {Role} from 'app/models/role.model';
import {
  DataSharingType,
  Survey,
  SurveyGeneralAccess,
  SurveyState,
} from 'app/models/survey.model';
import {Task, TaskType} from 'app/models/task/task.model';
import {AuthService} from 'app/services/auth/auth.service';
import {NavigationService} from 'app/services/navigation/navigation.service';
import {SurveyService} from 'app/services/survey/survey.service';

import {SurveyListComponent} from './survey-list.component';

@Component({selector: 'ground-header', template: ''})
class HeaderComponent {}

describe('SurveyListComponent', () => {
  let component: SurveyListComponent;
  let fixture: ComponentFixture<SurveyListComponent>;
  let navigationServiceSpy: jasmine.SpyObj<NavigationService>;
  const dialog: Partial<MatDialog> = {};

  // A survey that hasn't gone through creation flow
  const incompleteSurvey = new Survey(
    'survey001',
    'title1',
    'description1',
    /* jobs= */ Map({
      job001: new Job(
        'job001',
        /* index */ -1,
        'red',
        'name',
        /* tasks= */ Map()
      ),
    }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE}
  );

  // A survey that has gone through creation flow
  const completeSurvey = new Survey(
    'survey002',
    'title2',
    'description2',
    /* jobs= */ Map({
      job002: new Job(
        'job002',
        /* index */ -1,
        'green',
        'name',
        /* tasks= */ Map({
          task001: new Task(
            'task001',
            TaskType.TEXT,
            'Text Field',
            /*required=*/ true,
            0
          ),
        })
      ),
    }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE},
    SurveyState.READY
  );

  // A survey that has gone through creation flow
  const publicSurvey = new Survey(
    'survey003',
    'title3',
    'description3',
    /* jobs= */ Map({
      job003: new Job(
        'job003',
        /* index */ -1,
        'green',
        'name',
        /* tasks= */ Map({
          task001: new Task(
            'task001',
            TaskType.TEXT,
            'Text Field',
            /*required=*/ true,
            0
          ),
        })
      ),
    }),
    /* acl= */ Map(),
    /* ownerId= */ '',
    {type: DataSharingType.PRIVATE},
    SurveyState.READY,
    SurveyGeneralAccess.PUBLIC
  );

  const surveyServiceSpy = jasmine.createSpyObj('SurveyService', [
    'getAccessibleSurveys$',
    'getSurveyAcl',
  ]);
  const authServiceSpy = jasmine.createSpyObj('AuthService', [
    'canManageSurvey',
  ]);

  beforeEach(waitForAsync(() => {
    navigationServiceSpy = jasmine.createSpyObj<NavigationService>(
      'NavigationService',
      ['navigateToCreateSurvey', 'selectSurvey', 'getSidePanelExpanded']
    );

    TestBed.configureTestingModule({
      imports: [
        MatButtonModule,
        MatCardModule,
        MatGridListModule,
        MatIconModule,
        TranslateModule.forRoot(),
      ],
      declarations: [SurveyListComponent, HeaderComponent],
      providers: [
        {provide: MatDialog, useValue: dialog},
        {provide: SurveyService, useValue: surveyServiceSpy},
        {provide: NavigationService, useValue: navigationServiceSpy},
        {provide: AngularFirestore, useValue: {}},
        {provide: AngularFireAuth, useValue: {}},
        {provide: AuthService, useValue: authServiceSpy},
        TranslateService,
        TranslateStore,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    surveyServiceSpy.getAccessibleSurveys$.and.returnValue(
      of<List<Survey>>(List([incompleteSurvey, completeSurvey, publicSurvey]))
    );
    surveyServiceSpy.getSurveyAcl.and.returnValue([
      new AclEntry('test@gmail.com', Role.SURVEY_ORGANIZER),
    ]);
    authServiceSpy.canManageSurvey.and.returnValue(true);
    fixture = TestBed.createComponent(SurveyListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display survey cards', () => {
    let surveyCards = fixture.debugElement.queryAll(By.css('.survey-card'));
    expect(surveyCards.length).toBe(4, 'Should display 4 survey cards');

    clickFilter(fixture, 2);
    fixture.detectChanges();

    surveyCards = fixture.debugElement.queryAll(By.css('.survey-card'));
    expect(surveyCards.length).toBe(2, 'Should display 2 survey cards');

    clickFilter(fixture, 1);
    fixture.detectChanges();

    surveyCards = fixture.debugElement.queryAll(By.css('.survey-card'));
    expect(surveyCards.length).toBe(4, 'Should display 2 survey cards');
  });

  it('should go to create survey page when add card is clicked', () => {
    clickCard(fixture, 'add-card');

    expect(
      navigationServiceSpy.navigateToCreateSurvey
    ).toHaveBeenCalledOnceWith(null);
  });

  it('should go to create survey page with id when a incomplete survey card is clicked', () => {
    clickCard(fixture, 'survey-card-0');

    expect(
      navigationServiceSpy.navigateToCreateSurvey
    ).toHaveBeenCalledOnceWith(incompleteSurvey.id);
  });

  it('should go to map page with id when a complete survey card is clicked', () => {
    clickCard(fixture, 'survey-card-1');

    expect(navigationServiceSpy.selectSurvey).toHaveBeenCalledOnceWith(
      completeSurvey.id
    );
  });
});

function clickCard(
  fixture: ComponentFixture<SurveyListComponent>,
  id: string
): void {
  const button = fixture.debugElement.query(By.css(`#${id}`))
    .nativeElement as HTMLElement;
  button.click();
}

function clickFilter(
  fixture: ComponentFixture<SurveyListComponent>,
  nth: number
): void {
  const filter = fixture.debugElement.query(
    By.css(`mat-chip:nth-child(${nth})`)
  ).nativeElement as HTMLElement;
  filter.click();
}
