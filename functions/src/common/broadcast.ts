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

import { getMessaging } from 'firebase-admin/messaging';

/**
 * A change to announce to clients watching a survey. `type` tells them what
 * changed, so they can fetch just that instead of the whole survey. Clients
 * which don't recognize a message fall back to syncing everything, so new
 * types can be added without breaking older ones.
 */
export type SurveyUpdate =
  | { type: 'survey'; surveyId: string }
  | { type: 'job'; surveyId: string; jobId: string }
  | { type: 'loi'; surveyId: string; loiId: string; deleted: boolean };

/**
 * Announces `update` to clients subscribed to its survey's topic, stamped with
 * the time the triggering write was committed.
 *
 * Messages share one collapse key per survey so that a burst of writes - an
 * import may trigger thousands - is delivered as a single wake-up. Clients must
 * therefore treat the payload as a hint about one of the changes, never as the
 * complete set: the ids in a collapsed message are whichever arrived last.
 * See also: https://firebase.google.com/docs/cloud-messaging/concept-options#collapsible_and_non-collapsible_messages
 */
export async function broadcastUpdate(
  update: SurveyUpdate,
  eventTime: string
): Promise<string> {
  const { surveyId } = update;

  if (process.env.FUNCTIONS_EMULATOR === 'true') {
    console.debug(`Skipping FCM message to ${surveyId} (emulator mode)`);
    return '';
  }

  console.debug(`Sending ${update.type} update to ${surveyId}`);

  return getMessaging().send({
    topic: surveyId,
    data: toFcmData(update, eventTime),
    android: { collapseKey: surveyId, priority: 'normal' },
  });
}

/** FCM data payloads carry strings only, so all values are stringified. */
function toFcmData(
  update: SurveyUpdate,
  eventTime: string
): { [k: string]: string } {
  return Object.fromEntries([
    ...Object.entries(update).map(([k, v]) => [k, String(v)]),
    ['eventTime', eventTime],
  ]);
}
