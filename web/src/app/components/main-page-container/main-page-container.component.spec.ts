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

import { ActivatedRouteStub } from './../../../testing/activated-route-stub';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MainPageContainerComponent } from './main-page-container.component';
import { MainPageComponent } from './../main-page/main-page.component';
import { NavigationService } from '../../services/navigation/navigation.service';
import { NEVER } from 'rxjs';
import { ProjectService } from './../../services/project/project.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';

const navigationService = {
  init: () => {},
  getProjectId$: () => NEVER,
};

const projectService = jasmine.createSpyObj('ProjectService', [
  'getActiveProject$',
  'activateProject',
]);

describe('MainPageContainerComponent', () => {
  let component: MainPageContainerComponent;
  let fixture: ComponentFixture<MainPageContainerComponent>;
  let route: ActivatedRouteStub;

  beforeEach(
    waitForAsync(() => {
      route = new ActivatedRouteStub();
      TestBed.configureTestingModule({
        declarations: [MainPageContainerComponent, MainPageComponent],
        providers: [
          { provide: ActivatedRoute, useValue: route },
          { provide: NavigationService, useValue: navigationService },
          { provide: ProjectService, useValue: projectService },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();

      fixture = TestBed.createComponent(MainPageContainerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    })
  );

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
