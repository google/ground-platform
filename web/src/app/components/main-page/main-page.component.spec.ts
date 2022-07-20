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
import { ActivatedRoute, Router } from '@angular/router';
import { ActivatedRouteStub } from '../../../testing/activated-route-stub';
import { SurveyService } from '../../services/survey/survey.service';
import { MatDialog } from '@angular/material/dialog';
import { FeatureService } from '../../services/feature/feature.service';
import { ObservationService } from '../../services/observation/observation.service';
import { NavigationService } from '../../services/navigation/navigation.service';
import { NEVER } from 'rxjs';
import { AngularFirestore } from '@angular/fire/firestore';
import { AngularFireAuth } from '@angular/fire/auth';
import { AuthService } from '../../services/auth/auth.service';

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

      const surveyService = jasmine.createSpyObj('SurveyService', [
        'getActiveSurvey$',
        'activateSurvey',
      ]);

      const featureService = jasmine.createSpyObj('FeatureService', [
        'selectFeature$',
      ]);

      const observationService = jasmine.createSpyObj('ObservationService', [
        'selectObservation$',
      ]);

      const navigationService = {
        getSurveyId$: () => NEVER,
        getJobId$: () => NEVER,
        getFeatureId$: () => NEVER,
        getObservationId$: () => NEVER,
      };

      TestBed.configureTestingModule({
        declarations: [MainPageComponent, MapComponent, MatSideNavComponent],
        providers: [
          { provide: ActivatedRoute, useValue: route },
          { provide: MatDialog, useValue: dialog },
          { provide: FeatureService, useValue: featureService },
          { provide: ObservationService, useValue: observationService },
          { provide: SurveyService, useValue: surveyService },
          { provide: NavigationService, useValue: navigationService },
          { provide: AngularFirestore, useValue: {} },
          { provide: AngularFireAuth, useValue: {} },
          { provide: Router, useValue: {} },
          {
            provide: AuthService,
            useValue: { getUser$: () => NEVER, isAuthenticated$: () => NEVER },
          },
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
