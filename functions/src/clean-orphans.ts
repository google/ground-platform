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

import {
  DocumentReference,
  Firestore,
  getFirestore,
} from 'firebase-admin/firestore';
import { registry } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import { lois, submissions, surveys } from './common/datastore';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);
const sb = registry.getFieldIds(Pb.Submission);

// Firestore caps writeBatch at 500 operations; leave a small margin.
const BATCH_SIZE = 400;

/**
 * Deletes LOIs and submissions whose `jobId` no longer refers to a live job
 * under their parent survey. Such orphans can be left behind if the client's
 * chunked batch deletion (see DataStoreService.updateSurvey / deleteSurvey)
 * fails part-way through after the surrounding survey/job transaction has
 * already committed. See issue #1532.
 *
 * Scans only surveys that currently exist in `/surveys`. Orphans under a
 * surveyId whose parent survey doc has already been deleted are not cleaned
 * up here — that case is rare and handled by retrying `deleteSurvey`, which
 * is idempotent.
 */
export async function cleanOrphansHandler() {
  const db = getFirestore();
  const surveysSnapshot = await db.collection(surveys()).get();
  let totalDeleted = 0;
  let failedSurveys = 0;
  for (const surveyDoc of surveysSnapshot.docs) {
    try {
      totalDeleted += await cleanOrphansForSurvey(db, surveyDoc.id);
    } catch (err) {
      // Don't let one bad survey abort the whole sweep — log and continue.
      failedSurveys++;
      console.error(
        `cleanOrphans: survey ${surveyDoc.id} failed; continuing.`,
        err
      );
    }
  }
  console.log(
    `Deleted ${totalDeleted} orphan LOI/submission doc(s) across ${surveysSnapshot.size} survey(s) (${failedSurveys} failed).`
  );
}

async function cleanOrphansForSurvey(
  db: Firestore,
  surveyId: string
): Promise<number> {
  // Read candidates BEFORE the live-jobs set so that a job + LOI created
  // mid-run can't be misclassified as orphaned. Same pattern as
  // cleanOrphanMediaHandler.
  const [loiCandidates, submissionCandidates] = await Promise.all([
    fetchCandidates(db, lois(surveyId), l.jobId),
    fetchCandidates(db, submissions(surveyId), sb.jobId),
  ]);

  const jobsSnapshot = await db
    .collection(`${surveys()}/${surveyId}/jobs`)
    .get();
  const liveJobIds = new Set(jobsSnapshot.docs.map(d => d.id));

  const orphanRefs: DocumentReference[] = [
    ...loiCandidates.filter(c => !liveJobIds.has(c.jobId)).map(c => c.ref),
    ...submissionCandidates
      .filter(c => !liveJobIds.has(c.jobId))
      .map(c => c.ref),
  ];
  await chunkedDelete(db, orphanRefs);
  return orphanRefs.length;
}

interface Candidate {
  ref: DocumentReference;
  jobId: string;
}

async function fetchCandidates(
  db: Firestore,
  collectionPath: string,
  jobIdField: string
): Promise<Candidate[]> {
  const snapshot = await db.collection(collectionPath).get();
  const candidates: Candidate[] = [];
  for (const d of snapshot.docs) {
    const jobId = d.get(jobIdField);
    if (typeof jobId === 'string') candidates.push({ ref: d.ref, jobId });
  }
  return candidates;
}

async function chunkedDelete(
  db: Firestore,
  refs: DocumentReference[]
): Promise<void> {
  for (let i = 0; i < refs.length; i += BATCH_SIZE) {
    const chunk = refs.slice(i, i + BATCH_SIZE);
    const batch = db.batch();
    for (const ref of chunk) batch.delete(ref);
    await batch.commit();
  }
}
