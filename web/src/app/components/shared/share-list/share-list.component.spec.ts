/**
 * Copyright 2023 The Ground Authors.
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

import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MatListModule } from '@angular/material/list';
import { MatListHarness } from '@angular/material/list/testing';
import { MatSelectModule } from '@angular/material/select';
import { Map } from 'immutable';
import { Subject, firstValueFrom, of } from 'rxjs';

import { Role } from 'app/models/role.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

import { ShareListComponent } from './share-list.component';

describe('ShareListComponent', () => {
  let component: ShareListComponent;
  let fixture: ComponentFixture<ShareListComponent>;
  let loader: HarnessLoader;

  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let activeSurvey$: Subject<Survey>;

  const [surveyId, surveyTitle, surveyDescription] = [
    'survey1',
    'title1',
    'description1',
  ];

  const survey = new Survey(
    surveyId,
    surveyTitle,
    surveyDescription,
    /* jobs= */ Map(),
    /* acl= */ Map(),
    /* ownerId= */ '',
    { type: DataSharingType.PRIVATE }
  );

  const user = new User('user1', 'user1@gmail.com', true);

  beforeEach(waitForAsync(() => {
    draftSurveyServiceSpy = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      ['getSurvey$', 'updateAcl']
    );

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'getUser',
    ]);

    activeSurvey$ = new Subject<Survey>();

    draftSurveyServiceSpy.getSurvey$.and.returnValue(activeSurvey$);
    authServiceSpy.getUser.and.returnValue(firstValueFrom(of(user)));

    TestBed.configureTestingModule({
      declarations: [ShareListComponent],
      imports: [MatListModule, MatSelectModule],
      providers: [
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ShareListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    loader = TestbedHarnessEnvironment.loader(fixture);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('updates itself when acl changes', async () => {
    activeSurvey$.next(survey);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.acl?.length).toBe(0);

    activeSurvey$.next(
      new Survey(
        surveyId,
        surveyTitle,
        surveyDescription,
        /* jobs= */ Map(),
        /* acl= */ Map({ a: Role.OWNER, b: Role.OWNER }),
        /* ownerId= */ 'user1',
        { type: DataSharingType.PRIVATE }
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.acl?.length).toBe(2);

    const aclList = await loader.getHarness(MatListHarness);
    const aclListItems = await aclList.getItems();

    expect(aclListItems.length).toBe(3);
  });
});
