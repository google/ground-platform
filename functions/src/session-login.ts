/**
 * Copyright 2023 The Ground Authors.
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

import { Request } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import type { Response } from 'express';
import { setSessionCookie } from './common/auth';
import { StatusCodes } from 'http-status-codes';

/**
 * Generates and sets a session cookie for the current user.
 */
export async function sessionLoginHandler(req: Request, res: Response) {
  try {
    // Required for CDN:
    // https://stackoverflow.com/questions/44929653/firebase-cloud-function-wont-store-cookie-named-other-than-session/44935288#44935288
    res.setHeader('Cache-Control', 'private');
    await setSessionCookie(req, res);
    res.end('OK');
  } catch (err) {
    logger.error(err);
    res.status(StatusCodes.UNAUTHORIZED).send('Authorization error');
  }
}
