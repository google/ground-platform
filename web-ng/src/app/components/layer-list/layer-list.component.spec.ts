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

import { LayerListComponent } from './layer-list.component';
import { ProjectService } from '../../services/project/project.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Project } from '../../shared/models/project.model';
import { of } from 'rxjs';
import { Map } from 'immutable';
import { StringMap } from '../../shared/models/string-map.model';
import { Layer } from '../../shared/models/layer.model';
import { LayerListItemModule } from '../layer-list-item/layer-list-item.module';
import { MatListModule } from '@angular/material/list';

const mockProject = new Project(
  'project001',
  StringMap({ en: 'title' }),
  StringMap({ en: 'description' }),
  Map({
    layer001: new Layer(
      'layer001',
      /* index */ -1,
      'red',
      StringMap({ en: 'name' }),
      Map()
    ),
  }),
  Map()
);

class MockProjectService {
  getActiveProject$() {
    return of<Project>(mockProject);
  }
}

const projectService = new MockProjectService();

describe('LayerListComponent', () => {
  let component: LayerListComponent;
  let fixture: ComponentFixture<LayerListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [LayerListComponent],
      imports: [LayerListItemModule, MatListModule],
      providers: [{ provide: ProjectService, useValue: projectService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LayerListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
