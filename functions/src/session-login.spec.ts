/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as adminAuth from 'firebase-admin/auth';
import type { Auth } from 'firebase-admin/auth';
import { Request } from 'firebase-functions/v2/https';
import type { Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { sessionLoginHandler } from './session-login';

const FIVE_DAYS_MS = 60 * 60 * 24 * 5 * 1000;

describe('sessionLoginHandler', () => {
  let req: Request;
  let res: jasmine.SpyObj<Response>;
  let createSessionCookieSpy: jasmine.Spy;

  beforeEach(() => {
    req = {
      headers: { authorization: 'Bearer fake-id-token' },
      cookies: {},
    } as unknown as Request;

    res = jasmine.createSpyObj<Response>('response', [
      'setHeader',
      'cookie',
      'json',
      'status',
      'send',
    ]);
    res.status.and.returnValue(res);

    createSessionCookieSpy = jasmine
      .createSpy('createSessionCookie')
      .and.resolveTo('fake-session-cookie');
    spyOn(adminAuth, 'getAuth').and.returnValue({
      createSessionCookie: createSessionCookieSpy,
    } as unknown as Auth);
  });

  it('returns expiresAt approximately 5 days in the future', async () => {
    const before = Date.now();
    await sessionLoginHandler(req, res);
    const after = Date.now();

    expect(res.json).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ expiresAt: jasmine.any(Number) })
    );
    const { expiresAt } = (res.json as jasmine.Spy).calls.mostRecent().args[0];
    expect(expiresAt).toBeGreaterThanOrEqual(before + FIVE_DAYS_MS);
    expect(expiresAt).toBeLessThanOrEqual(after + FIVE_DAYS_MS);
  });

  it('sets httpOnly secure session cookie', async () => {
    await sessionLoginHandler(req, res);

    // Cast needed: Express's `cookie` overloads cause TS to resolve it as a
    // 2-param function, but `setSessionCookie` calls the 3-param overload.
    expect(res.cookie as jasmine.Spy).toHaveBeenCalledOnceWith(
      '__session',
      'fake-session-cookie',
      jasmine.objectContaining({ httpOnly: true, secure: true })
    );
  });

  it('returns 401 on auth error', async () => {
    createSessionCookieSpy.and.rejectWith(new Error('invalid token'));

    await sessionLoginHandler(req, res);

    expect(res.status).toHaveBeenCalledWith(StatusCodes.UNAUTHORIZED);
  });
});
