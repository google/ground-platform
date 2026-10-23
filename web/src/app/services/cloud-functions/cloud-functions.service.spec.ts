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

describe('CloudFunctionsService', () => {
  let service: CloudFunctionsService;
  let postWithAuthSpy: jasmine.Spy;
  let callableSpy: jasmine.Spy;
  let callableFnSpy: jasmine.Spy;

  beforeEach(() => {
    postWithAuthSpy = jasmine
      .createSpy('postWithAuth')
      .and.resolveTo({ expiresAt: 12345 });

    TestBed.configureTestingModule({
      providers: [
        CloudFunctionsService,
        { provide: Functions, useValue: {} },
        {
          provide: HttpClientService,
          useValue: { postWithAuth: postWithAuthSpy },
        },
      ],
    });

    service = TestBed.inject(CloudFunctionsService);

    callableFnSpy = jasmine
      .createSpy('callableFn')
      .and.resolveTo({ data: 'OK' });
    callableSpy = spyOn<any>(service, 'callable').and.returnValue(
      callableFnSpy
    );
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
    it('should call the profile-refresh callable', async () => {
      await service.profileRefresh();

      expect(callableSpy).toHaveBeenCalledOnceWith('profile-refresh');
      expect(callableFnSpy).toHaveBeenCalledOnceWith({});
    });

    it('should throw error if result is not OK', async () => {
      callableFnSpy.and.resolveTo({ data: 'ERROR' });

      await expectAsync(service.profileRefresh()).toBeRejectedWithError(
        'User profile could not be updated'
      );
    });
  });
});
