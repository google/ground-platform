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

import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FeaturePanelComponent } from './feature-panel.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { Map, List } from 'immutable';
import { firestore } from 'firebase/app';
import { Feature } from '../../shared/models/feature.model';
import { Layer } from '../../shared/models/layer.model';
import { Observation } from '../../shared/models/observation/observation.model';
import { Project } from '../../shared/models/project.model';
import { StringMap } from '../../shared/models/string-map.model';
import { ProjectService } from '../../services/project/project.service';
import { FeatureService } from '../../services/feature/feature.service';
import { ObservationService } from '../../services/observation/observation.service';
import { Router } from '@angular/router';

const mockProject = new Project(
  'project001',
  StringMap({ 'en': 'title' }),
  StringMap({ 'en': 'description' }),
  Map({
    'layer001':
    new Layer(
      'layer001',
      'red',
      StringMap({ 'en': 'name'}),
      Map()
    )
  })
);

const mockFeature = new Feature(
  'feature001',
  'layer001',
  new firestore.GeoPoint(0.0, 0.0)
);

const mockObservations = List<Observation>([]);

class MockProjectService {
  getActiveProject$() {
    return of<Project>(mockProject);
  }
}

class MockFeatureService {
  getSelectedFeature$() {
    return of<Feature>(mockFeature);
  }
}

class MockObservationService {
  observations$() {
    return of<List<Observation>>(mockObservations);
  }
}

const projectService = new MockProjectService();
const featureService = new MockFeatureService();
const observationService = new MockObservationService();

describe('FeaturePanelComponent', () => {
  let component: FeaturePanelComponent;
  let fixture: ComponentFixture<FeaturePanelComponent>;

  beforeEach(async(() => {
    const routerSpy = createRouterSpy();
    TestBed.configureTestingModule({
      declarations: [FeaturePanelComponent],
      providers: [
        { provide: FeatureService, useValue: featureService },
        { provide: ProjectService, useValue: projectService },
        { provide: ObservationService, useValue: observationService },
        { provide: Router, useValue: routerSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FeaturePanelComponent);
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
