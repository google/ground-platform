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

import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { Component } from '@angular/core';
import { MainPageComponent } from './main-page.component';
import { ActivatedRoute, Router } from '@angular/router';
import { ActivatedRouteStub } from '../../../testing/activated-route-stub';
import { ProjectService } from '../../services/project/project.service';
import { MatDialog } from '@angular/material/dialog';

@Component({ selector: 'ground-map', template: '' })
class MapComponent {}

describe('MainPageComponent', () => {
  let component: MainPageComponent;
  let fixture: ComponentFixture<MainPageComponent>;
  let route: ActivatedRouteStub;
  let dialog: Partial<MatDialog> = {};

  beforeEach(async(() => {
    const routerSpy = createRouterSpy();
    route = new ActivatedRouteStub();

    const projectService = jasmine.createSpyObj('ProjectService', [
      'getActiveProject$',
      'activateProject',
    ]);

    TestBed.configureTestingModule({
      declarations: [MainPageComponent, MapComponent],
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: MatDialog, useValue: dialog },
        { provide: ProjectService, useValue: projectService },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MainPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

function createRouterSpy() {
  return jasmine.createSpyObj('Router', ['navigate']);
}
