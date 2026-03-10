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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatListModule } from '@angular/material/list';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { Map } from 'immutable';
import { Subject } from 'rxjs';

import { Role } from 'app/models/role.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DraftSurveyService } from 'app/services/draft-survey/draft-survey.service';

import { ShareListComponent } from './share-list.component';

describe('ShareListComponent', () => {
  let component: ShareListComponent;
  let fixture: ComponentFixture<ShareListComponent>;

  let draftSurveyServiceSpy: jasmine.SpyObj<DraftSurveyService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let activeSurvey$: Subject<Survey>;

  const user = new User('user1', 'user1@gmail.com', true);

  beforeEach(async () => {
    draftSurveyServiceSpy = jasmine.createSpyObj<DraftSurveyService>(
      'DraftSurveyService',
      ['getSurvey$', 'updateAcl']
    );

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'getUser',
    ]);

    activeSurvey$ = new Subject<Survey>();

    draftSurveyServiceSpy.getSurvey$.and.returnValue(activeSurvey$);
    authServiceSpy.getUser.and.callFake(email => {
      if (email === 'owner-email') {
        return Promise.resolve(new User('owner', 'owner@gmail.com', true));
      }
      return Promise.resolve(user);
    });

    await TestBed.configureTestingModule({
      declarations: [ShareListComponent],
      imports: [MatListModule, MatSelectModule],
      providers: [
        { provide: DraftSurveyService, useValue: draftSurveyServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ShareListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('updates itself when acl changes', async () => {
    activeSurvey$.next(
      new Survey(
        'id',
        'title',
        'description',
        Map(),
        Map({ [user.email]: Role.VIEWER }),
        'owner-email',
        { type: DataSharingType.PRIVATE }
      )
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.acl.length).toBeGreaterThan(0);
    component.onRoleChange(
      { value: Role.SURVEY_ORGANIZER } as MatSelectChange,
      0
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.acl.length).toBe(1);
    expect(component.acl[0].email).toBe(user.email);
    expect(component.acl[0].role).toBe(Role.SURVEY_ORGANIZER);
    expect(authServiceSpy.getUser).toHaveBeenCalled();
  });
});
