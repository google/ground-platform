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

import { DecodedIdToken, getAuth } from 'firebase-admin/auth';
import { DocumentSnapshot } from 'firebase-admin/firestore';
import { Response, https } from 'firebase-functions/v1';
import { EmulatorIdToken } from '../handlers';
import { GroundProtos } from '@ground/proto';
import { registry } from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;
const s = registry.getFieldIds(Pb.Survey);

// This is the only cookie not stripped by Firebase CDN.
// https://firebase.google.com/docs/hosting/manage-cache#using_cookies
export const SESSION_COOKIE_NAME = '__session';
export const SURVEY_ORGANIZER_ROLE = Pb.Role.SURVEY_ORGANIZER;
export const DATA_COLLECTOR_ROLE = Pb.Role.DATA_COLLECTOR;

/**
 * Returns the encoded auth token from the "Authorization: Bearer" HTTP header
 * if present, or `undefined` if not.
 */
export function getAuthBearer(req: https.Request): string | undefined {
  const authHeader = req.headers.authorization;
  if (authHeader?.startsWith('Bearer ')) {
    return authHeader.split('Bearer ')[1];
  } else {
    return;
  }
}

/**
 * Verifies and returns the decoded user details from the `Authorization` header or session cookie.
 */
export async function getDecodedIdToken(
  req: https.Request
): Promise<DecodedIdToken | undefined> {
  const idToken = getAuthBearer(req);
  if (idToken) {
    return getAuth().verifyIdToken(idToken);
  } else if (req.cookies) {
    return await getAuth().verifySessionCookie(
      req.cookies[SESSION_COOKIE_NAME],
      true /** checkRevoked */
    );
  } else {
    return;
  }
}

/**
 * Generates and sets a session cookie for the current user into the provided response.
 */
export async function setSessionCookie(
  req: https.Request,
  res: Response
): Promise<void> {
  const token = getAuthBearer(req);
  const expiresIn = 60 * 60 * 24 * 5 * 1000; // 5 days
  const cookie = await getAuth().createSessionCookie(token!, { expiresIn });
  res.cookie(SESSION_COOKIE_NAME, cookie, {
    maxAge: expiresIn,
    httpOnly: true,
    secure: true,
  });
}

function isEmulatorIdToken(user: DecodedIdToken): boolean {
  return user instanceof EmulatorIdToken;
}

function getRole(
  user: DecodedIdToken,
  survey: DocumentSnapshot
): number | null {
  if (isEmulatorIdToken(user)) {
    return Pb.Role.SURVEY_ORGANIZER;
  }
  const acl = survey.get(s.acl);
  return acl && user.email ? acl[user.email] : null;
}

export function hasOrganizerRole(
  user: DecodedIdToken,
  survey: DocumentSnapshot
): boolean {
  const role = getRole(user, survey);
  return !!role && [Pb.Role.SURVEY_ORGANIZER].includes(role);
}

export function canExport(
  user: DecodedIdToken,
  survey: DocumentSnapshot
): boolean {
  const generalAccess = survey.get(s.generalAccess);
  if (
    [Pb.Survey.GeneralAccess.PUBLIC, Pb.Survey.GeneralAccess.UNLISTED].includes(
      generalAccess
    )
  )
    return true;
  return !!getRole(user, survey);
}

export function canImport(
  user: DecodedIdToken,
  survey: DocumentSnapshot
): boolean {
  return hasOrganizerRole(user, survey);
}
