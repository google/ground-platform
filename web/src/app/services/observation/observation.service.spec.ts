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
import { TestData } from '../../../testing/test-data';
import { ObservationService } from './observation.service';
import { DataStoreService } from '../data-store/data-store.service';
import { ProjectService } from '../project/project.service';
import { FeatureService } from '../feature/feature.service';
import { AuthService } from './../../services/auth/auth.service';
import { Subject } from 'rxjs';
import { User } from '../../shared/models/user.model';

const { newUser, newProject, newLayer, newLocationFeature } = TestData;

describe('ObservationService', () => {
  const user$ = new Subject<User | null>();
  let service: ObservationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: DataStoreService, useValue: {} },
        { provide: ProjectService, useValue: {} },
        { provide: FeatureService, useValue: {} },
        {
          provide: AuthService,
          useValue: {
            user$,
          },
        },
      ],
    });
    service = TestBed.inject(ObservationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('createNewObservation', () => {
    it('fail if form missing', () => {
      expect(() =>
        service.createNewObservation(
          newUser(),
          newProject({ layers: { layer001: newLayer({ forms: {} }) } }),
          newLocationFeature()
        )
      ).toThrow(new Error('No form in layer layer001'));
    });
  });
});
