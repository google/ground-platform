/**
 * Copyright 2019 The Ground Authors.
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
import { Auth } from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { Functions } from '@angular/fire/functions';
import { Router } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';

import { HttpClientService } from '../http-client/http-client.service';

describe('AuthService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        { provide: Firestore, useValue: {} },
        { provide: Functions, useValue: {} },
        {
          provide: Auth,
          useValue: {
            // Mock properties as needed by authState if possible, or just {}
            onAuthStateChanged: () => {},
            onIdTokenChanged: () => {},
          },
        },
        { provide: DataStoreService, useValue: { user$: () => of() } },
        { provide: Router, useValue: { events: of() } },
        { provide: HttpClientService, useValue: {} },
      ],
    });
  });

  it('should be created', () => {
    const service: AuthService = TestBed.inject(AuthService);
    expect(service).toBeTruthy();
  });
});
