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
import { initializeApp, provideFirebaseApp } from '@angular/fire/app';
import {
  Auth,
  User as FirebaseUser,
  getAuth,
  provideAuth,
} from '@angular/fire/auth';
import { Firestore } from '@angular/fire/firestore';
import { Functions } from '@angular/fire/functions';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from 'app/services/auth/auth.service';
import { DataStoreService } from 'app/services/data-store/data-store.service';
import { User } from 'app/models/user.model';
import { environment } from 'environments/environment';

import { HttpClientService } from '../http-client/http-client.service';

const SESSION_COOKIE_EXPIRES_AT_KEY = 'sessionCookieExpiresAt';
const FIVE_DAYS_MS = 60 * 60 * 24 * 5 * 1000;

/** Providers shared by all describe blocks that use a mock Auth. */
function mockAuthProviders(mockAuth: Partial<Auth>, postWithAuth: jasmine.Spy) {
  return [
    { provide: Auth, useValue: mockAuth },
    { provide: Firestore, useValue: {} },
    { provide: Functions, useValue: {} },
    {
      provide: DataStoreService,
      useValue: {
        user$: () =>
          of({
            id: 'user-1',
            email: 'test@test.com',
            displayName: 'Test User',
            isAuthenticated: true,
          } as User),
      },
    },
    { provide: Router, useValue: { events: of() } },
    { provide: HttpClientService, useValue: { postWithAuth } },
  ];
}

/** Returns a minimal Auth mock where onAuthStateChanged is a no-op. */
function createMockAuth(onIdTokenChanged: jasmine.Spy): Partial<Auth> {
  return {
    onIdTokenChanged,
    onAuthStateChanged: jasmine
      .createSpy('onAuthStateChanged')
      .and.returnValue(() => {}),
  };
}

describe('AuthService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        provideFirebaseApp(() =>
          initializeApp({ appId: '123', apiKey: '123' })
        ),
        provideAuth(() => getAuth()),
        { provide: Firestore, useValue: {} },
        { provide: Functions, useValue: {} },
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

describe('AuthService createSessionCookie()', () => {
  let service: AuthService;
  let postWithAuthSpy: jasmine.Spy;
  const futureExpiry = Date.now() + FIVE_DAYS_MS;

  beforeEach(() => {
    localStorage.clear();

    postWithAuthSpy = jasmine
      .createSpy('postWithAuth')
      .and.resolveTo({ expiresAt: futureExpiry });

    const mockAuth = createMockAuth(
      jasmine.createSpy('onIdTokenChanged').and.returnValue(() => {})
    );

    TestBed.configureTestingModule({
      providers: mockAuthProviders(mockAuth, postWithAuthSpy),
    });

    service = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('calls sessionLogin and persists expiresAt in localStorage on first call', async () => {
    await service.createSessionCookie();

    expect(postWithAuthSpy).toHaveBeenCalledOnceWith(
      `${environment.cloudFunctionsUrl}/sessionLogin`,
      {}
    );
    expect(localStorage.getItem(SESSION_COOKIE_EXPIRES_AT_KEY)).toBe(
      String(futureExpiry)
    );
  });

  it('skips sessionLogin when a valid cookie already exists in localStorage', async () => {
    localStorage.setItem(SESSION_COOKIE_EXPIRES_AT_KEY, String(futureExpiry));

    await service.createSessionCookie();

    expect(postWithAuthSpy).not.toHaveBeenCalled();
  });

  it('calls sessionLogin again when the stored cookie has expired', async () => {
    localStorage.setItem(SESSION_COOKIE_EXPIRES_AT_KEY, String(Date.now() - 1));

    await service.createSessionCookie();

    expect(postWithAuthSpy).toHaveBeenCalledTimes(1);
  });

  it('calls sessionLogin when the stored cookie expires within the refresh buffer', async () => {
    const expiresWithinBuffer = Date.now() + 60 * 1000; // 1 minute from now, within the 5-minute buffer
    localStorage.setItem(
      SESSION_COOKIE_EXPIRES_AT_KEY,
      String(expiresWithinBuffer)
    );

    await service.createSessionCookie();

    expect(postWithAuthSpy).toHaveBeenCalledTimes(1);
  });
});

describe('AuthService session cookie invalidation', () => {
  let service: AuthService;
  let capturedIdTokenCallback: (user: FirebaseUser | null) => void;

  beforeEach(() => {
    localStorage.setItem(
      SESSION_COOKIE_EXPIRES_AT_KEY,
      String(Date.now() + FIVE_DAYS_MS)
    );

    const onIdTokenChangedSpy = jasmine
      .createSpy('onIdTokenChanged')
      .and.callFake((cb: (user: FirebaseUser | null) => void) => {
        capturedIdTokenCallback = cb;
        return () => {};
      });

    const mockAuth = createMockAuth(onIdTokenChangedSpy);

    TestBed.configureTestingModule({
      providers: mockAuthProviders(mockAuth, jasmine.createSpy('postWithAuth')),
    });

    service = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('clears localStorage entry when user signs out', () => {
    expect(localStorage.getItem(SESSION_COOKIE_EXPIRES_AT_KEY)).not.toBeNull();

    capturedIdTokenCallback!(null);

    expect(localStorage.getItem(SESSION_COOKIE_EXPIRES_AT_KEY)).toBeNull();
  });

  it('preserves localStorage entry on token refresh for the same user', () => {
    // Spy on callProfileRefresh to prevent it from calling httpsCallable with
    // the mock Functions instance, which would fail with a missing _url error.
    spyOn(service, 'callProfileRefresh').and.resolveTo();

    capturedIdTokenCallback!({ uid: 'user-1' } as FirebaseUser);

    expect(localStorage.getItem(SESSION_COOKIE_EXPIRES_AT_KEY)).not.toBeNull();
  });
});
