/**
 * Copyright 2019 Google LLC
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
import { Component, NO_ERRORS_SCHEMA } from '@angular/core';
import { MainPageComponent } from './main-page.component';
import { ActivatedRoute } from '@angular/router';
import { ActivatedRouteStub } from '../../../testing/activated-route-stub';
import { ProjectService } from '../../services/project/project.service';
import { MatDialog } from '@angular/material/dialog';
import { FeatureService } from '../../services/feature/feature.service';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ObservationService } from '../../services/observation/observation.service';
import { NavigationService } from './../../services/router/router.service';
import { NEVER } from 'rxjs';

@Component({ selector: 'ground-map', template: '' })
class MapComponent {}

@Component({ selector: 'mat-sidenav', template: '' })
class MatSideNavComponent {
  opened = false;
}

describe('MainPageComponent', () => {
  let component: MainPageComponent;
  let fixture: ComponentFixture<MainPageComponent>;
  let route: ActivatedRouteStub;
  const dialog: Partial<MatDialog> = {};

  beforeEach(
    waitForAsync(() => {
      route = new ActivatedRouteStub();

      const projectService = jasmine.createSpyObj('ProjectService', [
        'getActiveProject$',
        'activateProject',
      ]);

      const featureService = jasmine.createSpyObj('FeatureService', [
        'selectFeature$',
      ]);

      const observationService = jasmine.createSpyObj('ObservationService', [
        'selectObservation$',
      ]);

      const navigationService = {
        getLayerId$: () => NEVER,
        getFeatureId$: () => NEVER,
        getObservationId$: () => NEVER,
      };

      TestBed.configureTestingModule({
        declarations: [MainPageComponent, MapComponent, MatSideNavComponent],
        imports: [MatProgressSpinnerModule],
        providers: [
          { provide: ActivatedRoute, useValue: route },
          { provide: MatDialog, useValue: dialog },
          { provide: FeatureService, useValue: featureService },
          { provide: ObservationService, useValue: observationService },
          { provide: ProjectService, useValue: projectService },
          { provide: NavigationService, useValue: navigationService },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      }).compileComponents();

      fixture = TestBed.createComponent(MainPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    })
  );

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
