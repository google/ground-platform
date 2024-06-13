/**
 * Copyright 2023 The Ground Authors.
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

import {getDatastore}
 from './common/context';
import {CallableRequest, HttpsError} from 'firebase-functions/v2/https';
import {getAuth} from 'firebase-admin/auth';

type ProfileRefreshResponse = String | HttpsError;
type ProfileRefreshRequest = CallableRequest<ProfileRefreshResponse>;

/**
 * Store profile of current user in database so it can be displayed to other collaborators.
 */
export async function handleProfileRefresh(
  request: ProfileRefreshRequest
): Promise<ProfileRefreshResponse> {
  if (!request.auth) {
    return new HttpsError('unauthenticated', 'Missing user credentials');
  }
  const uid = request.auth.token.uid;
  const user = await getAuth().getUser(uid);
  await getDatastore().mergeUserProfile(user);
  return 'OK';
}
