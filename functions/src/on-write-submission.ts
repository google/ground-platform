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

// import * as admin from 'firebase-admin';
import {Change, EventContext} from 'firebase-functions';
import {DocumentSnapshot} from 'firebase-functions/v1/firestore';
import {getDatastore} from './common/context';

export async function onWriteSubmissionHandler(
  change: Change<DocumentSnapshot>,
  context: EventContext
) {
  const surveyId = context.params.surveyId;
  const loiId = change.after?.get('loiId') || change.before?.get('loiId');
  if (!loiId) return;
  // Note: Counting submissions requires scanning the index, which has O(N) cost,
  // where N=submission count. This could be done in constant time by
  // incrementing/decrementing the count on create/delete, however that could lead to
  // skew or invalid states should the event not be handled for any reason.
  // An example of how this might be done for is shared here for future reference:
  //   https://gist.github.com/gino-m/6097f38c950921b7f98d8de87bbde4dd
  const db = getDatastore();
  const count = await db.countSubmissionsForLoi(surveyId, loiId);
  await db.updateSubmissionCount(surveyId, loiId, count);
}
