/**
 * Copyright 2019 The Ground Authors.
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

import { TestBed } from '@angular/core/testing';
import { List, Map } from 'immutable';
import { ReplaySubject, Subject, of, take } from 'rxjs';

import { DataSharingType, Survey, SurveyState } from 'app/models/survey.model';
import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { SURVEY_ID_NEW } from 'app/services/navigation/navigation.constants';
import { Role } from 'app/models/role.model';

describe('SurveyService', () => {
  let service: SurveyService;
  let dataStoreServiceSpy: jasmine.SpyObj<DataStoreService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  let user$: ReplaySubject<User>;
  const mockUser = new User('user1', 'user@test.com', true);
  const newSurveyId = 'newSurveyId';
  const mockSurvey = new Survey(
    'survey1',
    'Title',
    'Description',
    Map(),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );

  beforeEach(() => {
    dataStoreServiceSpy = jasmine.createSpyObj('DataStoreService', [
      'loadSurvey$',
      'loadAccessibleSurveys$',
      'updateSurveyTitle',
      'updateSurveyTitleAndDescription',
      'updateSurvey',
      'copySurvey',
      'createSurvey',
      'deleteSurvey',
    ]);

    user$ = new ReplaySubject<User>(1);
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'getUser$',
      'getCurrentUser',
    ]);

    authServiceSpy.getUser$.and.returnValue(user$);
    authServiceSpy.getCurrentUser.and.returnValue(mockUser);
    dataStoreServiceSpy.loadSurvey$.and.returnValue(of(mockSurvey));

    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    service = TestBed.inject(SurveyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('activeSurvey$', () => {
    it('should load survey when active survey ID changes', done => {
      service
        .getActiveSurvey$()
        .pipe(take(1))
        .subscribe(survey => {
          expect(survey).toEqual(mockSurvey);
          expect(dataStoreServiceSpy.loadSurvey$).toHaveBeenCalledWith(
            mockSurvey.id
          );
          done();
        });

      user$.next(mockUser);
      service.activateSurvey(mockSurvey.id);
    });

    it('should return UNSAVED_NEW when activating new survey ID', done => {
      service
        .getActiveSurvey$()
        .pipe(take(1))
        .subscribe(survey => {
          expect(survey).toEqual(Survey.UNSAVED_NEW);
          done();
        });

      user$.next(mockUser);
      service.activateSurvey(SURVEY_ID_NEW);
    });
  });

  describe('getAccessibleSurveys$', () => {
    it('should return empty list if no user', done => {
      authServiceSpy.getCurrentUser.and.returnValue(null as unknown as User);
      service.getAccessibleSurveys$().subscribe(surveys => {
        expect(surveys.size).toBe(0);
        done();
      });
    });

    it('should return accessible surveys if user exists', done => {
      const surveys = List([mockSurvey]);
      dataStoreServiceSpy.loadAccessibleSurveys$.and.returnValue(of(surveys));

      service.getAccessibleSurveys$().subscribe(result => {
        expect(result).toBe(surveys);
        expect(dataStoreServiceSpy.loadAccessibleSurveys$).toHaveBeenCalledWith(
          mockUser.email
        );
        done();
      });
    });
  });

  describe('updates', () => {
    beforeEach(() => {
      // Activate survey to set this.activeSurvey
      user$.next(mockUser);
      service.activateSurvey(mockSurvey.id);
    });

    it('should update title', async () => {
      dataStoreServiceSpy.updateSurveyTitle.and.resolveTo();
      await service.updateTitle('id1', 'New Title');
      expect(dataStoreServiceSpy.updateSurveyTitle).toHaveBeenCalledWith(
        'id1',
        'New Title'
      );
    });

    it('should update title and description', async () => {
      dataStoreServiceSpy.updateSurveyTitleAndDescription.and.resolveTo();
      await service.updateTitleAndDescription('id1', 'New Title', 'New Desc');
      expect(
        dataStoreServiceSpy.updateSurveyTitleAndDescription
      ).toHaveBeenCalledWith('id1', 'New Title', 'New Desc');
    });

    it('should update state', async () => {
      dataStoreServiceSpy.updateSurvey.and.resolveTo();
      await service.updateState(SurveyState.DRAFT);
      // Verify updateSurvey was called with mutated survey object (difficult to equality check exact object due to immutability/cloning)
      // Check if called.
      expect(dataStoreServiceSpy.updateSurvey).toHaveBeenCalled();
    });

    it('should update acl', async () => {
      dataStoreServiceSpy.updateSurvey.and.resolveTo();
      await service.updateAcl(Map({ 'user@test.com': Role.SURVEY_ORGANIZER }));
      expect(dataStoreServiceSpy.updateSurvey).toHaveBeenCalled();
    });

    it('should update data sharing terms', async () => {
      dataStoreServiceSpy.updateSurvey.and.resolveTo();
      await service.updateDataSharingTerms(DataSharingType.PUBLIC, 'Terms');
      expect(dataStoreServiceSpy.updateSurvey).toHaveBeenCalled();
    });
  });

  describe('copySurvey', () => {
    it('should call copySurvey and return the new ID', async () => {
      const originalSurveyId = 'original123';
      dataStoreServiceSpy.copySurvey.and.resolveTo(newSurveyId);

      const resultId = await service.copySurvey(originalSurveyId);

      expect(dataStoreServiceSpy.copySurvey).toHaveBeenCalledWith(
        originalSurveyId
      );
      expect(resultId).toBe(newSurveyId);
    });
  });

  describe('createSurvey', () => {
    it('should create survey', async () => {
      dataStoreServiceSpy.createSurvey.and.resolveTo(newSurveyId);
      user$.next(mockUser); // Emit user for firstValueFrom

      const id = await service.createSurvey('Name', 'Desc');

      expect(dataStoreServiceSpy.createSurvey).toHaveBeenCalledWith(
        'Name',
        'Desc',
        mockUser
      );
      expect(id).toBe(newSurveyId);
    });
  });

  describe('deleteSurvey', () => {
    it('should delete survey', async () => {
      dataStoreServiceSpy.deleteSurvey.and.resolveTo();
      await service.deleteSurvey(mockSurvey);
      expect(dataStoreServiceSpy.deleteSurvey).toHaveBeenCalledWith(mockSurvey);
    });

    it('should throw error if delete fails', async () => {
      dataStoreServiceSpy.deleteSurvey.and.rejectWith(new Error('Fail'));
      await expectAsync(service.deleteSurvey(mockSurvey)).toBeRejectedWithError(
        'Fail'
      );
    });
  });

  describe('canManageSurvey', () => {
    it('should return false if no user', () => {
      authServiceSpy.getCurrentUser.and.returnValue(null as unknown as User);
      expect(service.canManageSurvey(mockSurvey)).toBe(false);
    });

    it('should return false if no survey', () => {
      expect(service.canManageSurvey(undefined)).toBe(false);
    });

    it('should return true if user is manager', () => {
      const managerSurvey = new Survey(
        'id',
        'title',
        'desc',
        Map(),
        Map({ [mockUser.email]: Role.SURVEY_ORGANIZER }),
        'ownerId',
        { type: DataSharingType.PRIVATE }
      );
      expect(service.canManageSurvey(managerSurvey)).toBe(true);
    });

    it('should return true if user is owner', () => {
      const ownerSurvey = new Survey(
        'id',
        'title',
        'desc',
        Map(),
        Map({ [mockUser.email]: Role.OWNER }),
        'ownerId',
        { type: DataSharingType.PRIVATE }
      );
      expect(service.canManageSurvey(ownerSurvey)).toBe(true);
    });

    it('should return false if user is not manager or owner', () => {
      const viewerSurvey = new Survey(
        'id',
        'title',
        'desc',
        Map(),
        Map({ [mockUser.email]: Role.VIEWER }),
        'ownerId',
        { type: DataSharingType.PRIVATE }
      );
      expect(service.canManageSurvey(viewerSurvey)).toBe(false);
    });
  });
});
