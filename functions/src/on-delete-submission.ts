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

import {
  DocumentSnapshot,
  FirestoreEvent,
} from 'firebase-functions/v2/firestore';
import { getStorageBucket } from './common/context';
import { toMessage } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;

export async function onDeleteSubmissionHandler(
  event: FirestoreEvent<DocumentSnapshot | undefined>
) {
  const data = event.data?.data();
  if (!data) return;

  const submission = toMessage(data, Pb.Submission);
  if (submission instanceof Error) {
    console.warn(
      'Skipping photo cleanup for unreadable submission',
      submission
    );
    return;
  }

  const paths = photoPaths(submission);
  if (paths.length === 0) return;

  const bucket = getStorageBucket();
  const results = await Promise.allSettled(
    paths.map(path => bucket.file(path).delete({ ignoreNotFound: true }))
  );

  results.forEach((result, i) => {
    if (result.status === 'rejected') {
      console.error(`Failed to delete photo ${paths[i]}`, result.reason);
    }
  });
}

function photoPaths(submission: Pb.ISubmission): string[] {
  return (submission.taskData ?? [])
    .map(taskData => taskData.takePhotoResult?.photoPath)
    .filter((path): path is string => !!path);
}
