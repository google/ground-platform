/**
 * Copyright 2026 The Ground Authors.
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

import { getFirestore } from 'firebase-admin/firestore';

import { getStorageBucket } from './common/context';
import { surveys } from './common/datastore';

const USER_MEDIA_SURVEYS_PREFIX = 'user-media/surveys/';

/**
 * Deletes files under `user-media/surveys/{surveyId}/...` whose `surveyId`
 * no longer corresponds to a live document in the `surveys` collection.
 *
 * Files are listed before surveys so that a survey created mid-run cannot
 * have its just-uploaded media misclassified as orphaned.
 */
export async function cleanOrphanMediaHandler() {
  const bucket = getStorageBucket();
  const [files] = await bucket.getFiles({ prefix: USER_MEDIA_SURVEYS_PREFIX });

  const surveysSnapshot = await getFirestore().collection(surveys()).get();
  const liveSurveyIds = new Set(surveysSnapshot.docs.map(d => d.id));

  const orphans = files.filter(f => {
    const surveyId = surveyIdFromPath(f.name);
    return surveyId !== null && !liveSurveyIds.has(surveyId);
  });

  await Promise.all(orphans.map(f => f.delete()));
  console.log(
    `Deleted ${orphans.length} orphan media file(s) under ${USER_MEDIA_SURVEYS_PREFIX}.`
  );
}

function surveyIdFromPath(path: string): string | null {
  if (!path.startsWith(USER_MEDIA_SURVEYS_PREFIX)) return null;
  const rest = path.substring(USER_MEDIA_SURVEYS_PREFIX.length);
  const slashIdx = rest.indexOf('/');
  return slashIdx < 0 ? null : rest.substring(0, slashIdx);
}
