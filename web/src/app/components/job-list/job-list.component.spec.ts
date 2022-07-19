/**
 * Copyright 2020 Google LLC
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

import { AngularFireAuth } from '@angular/fire/auth';
import { AngularFirestore } from '@angular/fire/firestore';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { JobListComponent } from './job-list.component';
import { ProjectService } from '../../services/project/project.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Project } from '../../shared/models/project.model';
import { of } from 'rxjs';
import { Map } from 'immutable';
import { StringMap } from '../../shared/models/string-map.model';
import { Job } from '../../shared/models/job.model';
import { MatListModule } from '@angular/material/list';
import { Router } from '@angular/router';
import { NavigationService } from '../../services/navigation/navigation.service';
import { DataStoreService } from '../../services/data-store/data-store.service';

const mockProject = new Project(
  'project001',
  StringMap({ en: 'title' }),
  StringMap({ en: 'description' }),
  /* jobs= */ Map({
    job001: new Job(
      'job001',
      /* index */ -1,
      'red',
      StringMap({ en: 'name' }),
      /* forms= */ Map()
    ),
  }),
  /* acl= */ Map()
);

const authState = {
  displayName: null,
  isAnonymous: true,
  uid: '',
};

const mockAngularFireAuth = {
  authState: of(authState),
};

class MockProjectService {
  getActiveProject$() {
    return of<Project>(mockProject);
  }
  getCurrentProject() {}
  getCurrentProjectAcl() {}
  canManageProject() {}
}

const projectService = new MockProjectService();

describe('JobListComponent', () => {
  let component: JobListComponent;
  let fixture: ComponentFixture<JobListComponent>;

  beforeEach(
    waitForAsync(() => {
      const navigationService = {
        getProjectId$: () => of(''),
        getFeatureId$: () => of(''),
      };
      const routerSpy = createRouterSpy();
      TestBed.configureTestingModule({
        declarations: [JobListComponent],
        imports: [MatListModule],
        providers: [
          { provide: ProjectService, useValue: projectService },
          {
            provide: Router,
            useValue: routerSpy,
          },
          { provide: NavigationService, useValue: navigationService },
          { provide: AngularFirestore, useValue: {} },
          {
            provide: AngularFireAuth,
            useValue: mockAngularFireAuth,
          },
          { provide: DataStoreService, useValue: { user$: () => of() } },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();
    })
  );

  beforeEach(() => {
    fixture = TestBed.createComponent(JobListComponent);
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
