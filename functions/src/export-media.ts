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

import { Request } from 'firebase-functions/v2/https';
import type { Response } from 'express';
import { DecodedIdToken } from 'firebase-admin/auth';
import { StatusCodes } from 'http-status-codes';
import { pipeline } from 'stream/promises';
import { canExport, hasOrganizerRole } from './common/auth';
import { getDatastore, getStorageBucket } from './common/context';
import { isMediaPathInSurvey } from './common/media';
import { toMessage } from '@ground/lib';
import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;

/** Fallback used when the stored object declares no content type. */
const DEFAULT_CONTENT_TYPE = 'application/octet-stream';

/**
 * Streams the photo submitted for a single task back to the caller.
 *
 * Exported CSVs link here rather than to storage directly, so that permission
 * is re-evaluated against the survey every time a link is followed: losing
 * access to a survey also loses access to its photos, and links in an already
 * distributed CSV never become public.
 */
export async function exportMediaHandler(
  req: Request,
  res: Response,
  user: DecodedIdToken
) {
  const db = getDatastore();
  const { uid: userId } = user;
  const surveyId = req.query.survey as string;
  const submissionId = req.query.submission as string;
  const taskId = req.query.task as string;

  if (!surveyId || !submissionId || !taskId) {
    res
      .status(StatusCodes.BAD_REQUEST)
      .send('Missing survey, submission, or task');
    return;
  }

  const surveyDoc = await db.fetchSurvey(surveyId);
  if (!surveyDoc.exists) {
    res.status(StatusCodes.NOT_FOUND).send('Survey not found');
    return;
  }
  if (!canExport(user, surveyDoc)) {
    res.status(StatusCodes.FORBIDDEN).send('Permission denied');
    return;
  }
  const survey = toMessage(surveyDoc.data()!, Pb.Survey);
  if (survey instanceof Error) {
    res
      .status(StatusCodes.INTERNAL_SERVER_ERROR)
      .send('Unsupported or corrupt survey');
    return;
  }

  const canViewAll =
    hasOrganizerRole(user, surveyDoc) ||
    survey.dataVisibility === Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS;

  const submissionDoc = await db.fetchSubmission(surveyId, submissionId);
  if (!submissionDoc.exists) {
    res.status(StatusCodes.NOT_FOUND).send('Submission not found');
    return;
  }
  const submission = toMessage(submissionDoc.data()!, Pb.Submission);
  if (submission instanceof Error) {
    res
      .status(StatusCodes.INTERNAL_SERVER_ERROR)
      .send('Unsupported or corrupt submission');
    return;
  }

  // Submissions owned by others are reported as missing rather than forbidden,
  // so that the response doesn't confirm a submission the user may not see.
  if (!canViewAll && submission.ownerId !== userId) {
    res.status(StatusCodes.NOT_FOUND).send('Submission not found');
    return;
  }

  // Resolved by task id, matching how `export-csv.ts` builds the link: each
  // photo task in a job is one CSV column, so a task id identifies a photo
  // uniquely within a submission. `TaskData` is a repeated field carrying its
  // own id, so the schema permits several entries per task; nothing writes
  // them today, and both sides take the first match so a link and the photo it
  // serves always agree. Should repeated tasks ever be supported, this lookup
  // and `writeRow()` in `export-csv.ts` must switch to `TaskData.id` together.
  const photoPath = submission.taskData?.find(d => d.taskId === taskId)
    ?.takePhotoResult?.photoPath;
  if (!photoPath) {
    res.status(StatusCodes.NOT_FOUND).send('Photo not found');
    return;
  }
  if (!isMediaPathInSurvey(photoPath, surveyId)) {
    console.warn(
      `Submission ${submissionId} task ${taskId} references ${photoPath}, ` +
        `outside survey ${surveyId}`
    );
    res.status(StatusCodes.NOT_FOUND).send('Photo not found');
    return;
  }

  const file = getStorageBucket().file(photoPath);
  let contentType: string | undefined;
  let size: string | number | undefined;
  try {
    [{ contentType, size }] = await file.getMetadata();
  } catch (e) {
    console.debug(`Photo ${photoPath} is not readable`, e);
    res.status(StatusCodes.NOT_FOUND).send('Photo not found');
    return;
  }

  res.setHeader('Content-Type', contentType || DEFAULT_CONTENT_TYPE);
  // Photos are survey data, so they must not be cached by shared caches.
  res.setHeader('Cache-Control', 'private, max-age=3600');
  if (size !== undefined) res.setHeader('Content-Length', String(size));

  try {
    await pipeline(file.createReadStream(), res);
  } catch (e) {
    // The response is already committed once bytes start flowing, so a failure
    // mid-stream can only be logged and the connection dropped.
    console.error(`Failed to stream photo ${photoPath}`, e);
    if (!res.headersSent) {
      res
        .status(StatusCodes.INTERNAL_SERVER_ERROR)
        .send('Failed to read photo');
    } else {
      res.end();
    }
  }
}
