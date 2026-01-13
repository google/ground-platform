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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { Map, List } from 'immutable';
import { of } from 'rxjs';

import { Job } from 'app/models/job.model';
import { DataSharingType, Survey } from 'app/models/survey.model';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { LocationOfInterestService } from 'app/services/loi/loi.service';
import { NavigationService } from 'app/services/navigation/navigation.service';

import { JobListComponent } from './job-list.component';

const mockSurvey = new Survey(
  'survey001',
  'title',
  'description',
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
  { type: DataSharingType.PRIVATE }
);

const authState = {
  displayName: null,
  isAnonymous: true,
  uid: '',
};

const mockAuth = {
  currentUser: authState,
};

describe('JobListComponent', () => {
  let component: JobListComponent;
  let fixture: ComponentFixture<JobListComponent>;

  beforeEach(async () => {
    const navigationService = {
      getSurveyId$: () => of(''),
      getLocationOfInterestId$: () => of(''),
      getSidePanelExpanded: () => true,
    };
    const routerSpy = createRouterSpy();
    await TestBed.configureTestingModule({
      declarations: [JobListComponent],
      imports: [MatIconModule, MatListModule, MatMenuModule, MatButtonModule],
      providers: [
        {
          provide: Router,
          useValue: routerSpy,
        },
        { provide: NavigationService, useValue: navigationService },
        { provide: Firestore, useValue: {} },
        {
          provide: Auth,
          useValue: mockAuth,
        },
        { provide: DataStoreService, useValue: { user$: () => of() } },
        {
          provide: LocationOfInterestService,
          useValue: { getLocationsOfInterest$: () => of() },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListComponent);
    fixture.componentRef.setInput('activeSurvey', mockSurvey);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return sorted jobs', () => {
    const job1 = new Job('job1', 1, 'red', 'Job 1', Map());
    const job2 = new Job('job2', 0, 'blue', 'Job 2', Map());
    const survey = new Survey(
      's1',
      'title',
      'desc',
      Map({ job1, job2 }),
      Map(),
      '',
      { type: DataSharingType.PRIVATE }
    );

    fixture.componentRef.setInput('activeSurvey', survey);
    fixture.detectChanges();

    const jobs = component.jobs();
    expect(jobs.size).toBe(2);
    expect(jobs.get(0)).toEqual(job2);
    expect(jobs.get(1)).toEqual(job1);
  });

  it('should group LOIs by job', () => {
    const loi1 = { id: 'loi1', jobId: 'job1' } as any;
    const loi2 = { id: 'loi2', jobId: 'job1' } as any;
    const loi3 = { id: 'loi3', jobId: 'job2' } as any;
    const lois = List([loi1, loi2, loi3]);

    fixture.componentRef.setInput('lois', lois);
    fixture.detectChanges();

    const grouped = component.loisByJob();
    expect(grouped.get('job1')?.size).toBe(2);
    expect(grouped.get('job2')?.size).toBe(1);
    expect(grouped.get('job1')?.includes(loi1)).toBe(true);
    expect(grouped.get('job1')?.includes(loi2)).toBe(true);
    expect(grouped.get('job2')?.includes(loi3)).toBe(true);
  });

  it('should delegate isSidePanelExpanded to navigation service', () => {
    const navService = TestBed.inject(NavigationService);
    const spy = spyOn(navService, 'getSidePanelExpanded').and.returnValue(true);
    
    expect(component.isSidePanelExpanded()).toBe(true);
    expect(spy).toHaveBeenCalled();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
