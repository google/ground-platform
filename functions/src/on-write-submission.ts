/**
 * @license
 * Copyright 2023 Google LLC
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
import { surveyPathTemplate } from './on-write-survey';
import {db} from '@/common/context';

// Template for submission write triggers capturing survey and submission ids.
export const submissionPathTemplate = `${surveyPathTemplate}/submissions/{submissionId}`;

export async function onWriteSubmissionHandler(
  change: Change<DocumentSnapshot>,
  context: EventContext
) {
  // Messages are sent without a payload so that they can collapsed. Collapsible
  // messages are more performant, and they may be replaced by newer messages if
  // necessary. This is important when importing LOIs, which may trigger
  // hundred of updates in a short period of time.
  // See also: https://firebase.google.com/docs/cloud-messaging/concept-options#collapsible_and_non-collapsible_messages
  // const topic = context.params.surveyId;
  const surveyId = context.params.surveyId;
  const loiId = change.after.get("loiId") || change.before.get("loiId");
  if (!loiId) return;
  const count = await db.countSubmissionsForLoi(surveyId, loiId);
  console.debug(`Updating submission count survey ${surveyId} loi ${loiId}: ${count}`);
  await db.updateSubmissionCount(surveyId, loiId, count);
}
