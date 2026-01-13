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

import { TestBed } from '@angular/core/testing';
import { List, Map } from 'immutable';
import { ReplaySubject, of } from 'rxjs';

import { User } from 'app/models/user.model';
import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { SubmissionService } from 'app/services/submission/submission.service';
import { SurveyService } from 'app/services/survey/survey.service';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { LocationOfInterest } from 'app/models/loi.model';
import { Point } from 'app/models/geometry/point';
import { Coordinate } from 'app/models/geometry/coordinate';
import { Submission } from 'app/models/submission/submission.model';
import { AuditInfo } from 'app/models/audit-info.model';
import { Job } from 'app/models/job.model';

describe('SubmissionService', () => {
  let service: SubmissionService;
  let dataStoreServiceSpy: jasmine.SpyObj<DataStoreService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let surveyServiceSpy: jasmine.SpyObj<SurveyService>;

  let user$: ReplaySubject<User>;
  const mockUser = new User('user1', 'user@test.com', true);
  const mockJob = new Job('job1', 0);
  const mockSurvey = new Survey(
    'survey1',
    'Title',
    'Description',
    Map({ job1: mockJob }),
    Map(),
    'owner1',
    { type: DataSharingType.PRIVATE }
  );
  const mockLoi = new LocationOfInterest(
    'loi1',
    'job1',
    new Point(new Coordinate(0, 0)),
    Map(),
    '',
    true
  );
  const mockSubmission = new Submission(
    'sub1',
    'loi1',
    mockJob,
    new AuditInfo(mockUser, new Date(), new Date()),
    new AuditInfo(mockUser, new Date(), new Date()),
    Map()
  );

  beforeEach(() => {
    user$ = new ReplaySubject<User>(1);
    dataStoreServiceSpy = jasmine.createSpyObj('DataStoreService', [
      'getAccessibleSubmissions$',
      'generateId',
      'getServerTimestamp',
    ]);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getUser$']);
    surveyServiceSpy = jasmine.createSpyObj('SurveyService', [
      'canManageSurvey',
    ]);

    authServiceSpy.getUser$.and.returnValue(user$);
    dataStoreServiceSpy.getServerTimestamp.and.returnValue(new Date() as any);
    dataStoreServiceSpy.generateId.and.returnValue('newId');

    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: dataStoreServiceSpy },
        { provide: SurveyService, useValue: surveyServiceSpy },
        { provide: LocationOfInterestService, useValue: {} },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
    service = TestBed.inject(SubmissionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getSubmissions$', () => {
    it('should return empty list if no user', done => {
      user$.next(null as unknown as User);
      service.getSubmissions$(mockSurvey, mockLoi).subscribe(submissions => {
        expect(submissions.size).toBe(0);
        done();
      });
    });

    it('should return accessible submissions if user exists', done => {
      user$.next(mockUser);
      const submissions = List([mockSubmission]);
      dataStoreServiceSpy.getAccessibleSubmissions$.and.returnValue(
        of(submissions)
      );

      service.getSubmissions$(mockSurvey, mockLoi).subscribe(result => {
        expect(result).toBe(submissions);
        expect(
          dataStoreServiceSpy.getAccessibleSubmissions$
        ).toHaveBeenCalled();
        done();
      });
    });
  });

  describe('getSubmission$', () => {
    it('should return submission if found', done => {
      user$.next(mockUser);
      const submissions = List([mockSubmission]);
      dataStoreServiceSpy.getAccessibleSubmissions$.and.returnValue(
        of(submissions)
      );

      service.getSubmission$(mockSurvey, mockLoi, 'sub1').subscribe(result => {
        expect(result).toBe(mockSubmission);
        done();
      });
    });
  });

  describe('createNewSubmission', () => {
    it('should throw error if no user', () => {
      expect(() =>
        service.createNewSubmission(
          null as unknown as User,
          mockSurvey,
          mockLoi
        )
      ).toThrowError('Login required to create new submission.');
    });

    it('should throw error if job not found', () => {
      const invalidLoi = new LocationOfInterest(
        'loi2',
        'invalidJob',
        new Point(new Coordinate(0, 0)),
        Map(),
        '',
        true
      );
      expect(() =>
        service.createNewSubmission(mockUser, mockSurvey, invalidLoi)
      ).toThrowError('Missing job invalidJob');
    });

    it('should create new submission', () => {
      const result = service.createNewSubmission(mockUser, mockSurvey, mockLoi);
      expect(result instanceof Submission).toBe(true);
      expect((result as Submission).id).toBe('newId');
    });
  });
});
