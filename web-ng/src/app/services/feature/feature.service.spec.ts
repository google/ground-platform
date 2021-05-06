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

import { TestBed } from '@angular/core/testing';
import { FeatureService } from './feature.service';
import { DataStoreService } from '../data-store/data-store.service';
import { ProjectService } from '../project/project.service';
import { Subject, of } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { NavigationService } from '../../services/navigation/navigation.service';

describe('FeatureService', () => {
  const activeProject$ = new Subject<Project | null>();

  beforeEach(() => {
    const navigationService = {
      getProjectId$: () => of(''),
      getFeatureId$: () => of(''),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: {} },
        {
          provide: ProjectService,
          useValue: {
            getActiveProject$: () => activeProject$,
          },
        },
        { provide: NavigationService, useValue: navigationService },
      ],
    });
  });

  it('should be created', () => {
    const service: FeatureService = TestBed.inject(FeatureService);
    expect(service).toBeTruthy();
  });
});
