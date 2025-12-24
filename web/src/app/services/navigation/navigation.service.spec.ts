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

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { NavigationService } from 'app/services/navigation/navigation.service';

import { DataStoreService } from '../data-store/data-store.service';

describe('NavigationService', () => {
  let service: NavigationService;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl'], {
      events: of(),
      routerState: {
        root: {},
      },
    });

    TestBed.configureTestingModule({
      providers: [
        {
          provide: DataStoreService,
          useValue: { getAccessDeniedMessage: () => '' },
        },
        { provide: Router, useValue: routerSpy },
      ],
    });

    service = TestBed.inject(NavigationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('navigateToEditSurvey navigates to ', () => {
    const surveyId = 'survey123';
    service.navigateToEditSurvey(surveyId);

    expect(routerSpy.navigateByUrl).toHaveBeenCalledOnceWith(
      'survey/survey123/edit/survey'
    );
  });
});
