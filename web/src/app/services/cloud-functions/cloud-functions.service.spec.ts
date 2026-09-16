/**
 * Copyright 2024 The Ground Authors.
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
import { Functions } from '@angular/fire/functions';
import { environment } from 'environments/environment';

import { CloudFunctionsService } from './cloud-functions.service';
import { HttpClientService } from '../http-client/http-client.service';
import { Injector } from '@angular/core';
import * as fireFunctions from '@angular/fire/functions';

describe('CloudFunctionsService', () => {
  let service: CloudFunctionsService;
  let postWithAuthSpy: jasmine.Spy;
  let httpsCallableSpy: jasmine.Spy;

  beforeEach(() => {
    postWithAuthSpy = jasmine.createSpy('postWithAuth').and.resolveTo({ expiresAt: 12345 });

    httpsCallableSpy = jasmine.createSpy('httpsCallable').and.returnValue(
      jasmine.createSpy('callableFn').and.resolveTo({ data: 'OK' })
    );

    spyOnProperty(fireFunctions, 'httpsCallable', 'get').and.returnValue(httpsCallableSpy);

    TestBed.configureTestingModule({
      providers: [
        CloudFunctionsService,
        { provide: Functions, useValue: {} },
        { provide: HttpClientService, useValue: { postWithAuth: postWithAuthSpy } },
        { provide: Injector, useValue: TestBed.inject(Injector) }
      ],
    });

    service = TestBed.inject(CloudFunctionsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('sessionLogin', () => {
    it('should call postWithAuth with the correct URL', async () => {
      const result = await service.sessionLogin();

      expect(postWithAuthSpy).toHaveBeenCalledOnceWith(
        `${environment.cloudFunctionsUrl}/sessionLogin`,
        {}
      );
      expect(result.expiresAt).toBe(12345);
    });
  });

  describe('profileRefresh', () => {
    it('should call httpsCallable with profile-refresh', async () => {
      await service.profileRefresh();

      expect(httpsCallableSpy).toHaveBeenCalled();
      const args = httpsCallableSpy.calls.mostRecent().args;
      expect(args[1]).toBe('profile-refresh');
    });

    it('should throw error if result is not OK', async () => {
      httpsCallableSpy.and.returnValue(
        jasmine.createSpy('callableFn').and.resolveTo({ data: 'ERROR' })
      );

      await expectAsync(service.profileRefresh()).toBeRejectedWithError('User profile could not be updated');
    });
  });
});
