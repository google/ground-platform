/**
 * Copyright 2020 The Ground Authors.
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
import type { Response } from 'express';
import * as csv from '@fast-csv/format';
import { canExport, hasOrganizerRole } from './common/auth';
import { isAccessibleLoi } from './common/utils';
import { geojsonToWKT } from '@terraformer/wkt';
import {
  getDatastore,
  getFirebaseDownloadUrl,
  getStorageBucket,
} from './common/context';
import { getTempFilePath } from './common/temp-storage';
import { getExportMediaUrl, getRequestBaseUrl } from './common/media';
import { DecodedIdToken } from 'firebase-admin/auth';
import { QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { StatusCodes } from 'http-status-codes';
import { List } from 'immutable';
import { registry, timestampToInt, toMessage } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { toGeoJsonGeometry } from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;

const l = registry.getFieldIds(Pb.LocationOfInterest);

/**
 * Iterates over all LOIs and submissions in a job, joining them
 * into a single table written to the response as a quote CSV file.
 */
export async function exportCsvHandler(
  req: Request,
  res: Response,
  user: DecodedIdToken
) {
  const db = getDatastore();
  const { uid: userId } = user;
  const surveyId = req.query.survey as string;
  const jobId = req.query.job as string;

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

  const jobDoc = await db.fetchJob(surveyId, jobId);
  if (!jobDoc.exists || !jobDoc.data()) {
    res.status(StatusCodes.NOT_FOUND).send('Job not found');
    return;
  }
  const job = toMessage(jobDoc.data()!, Pb.Job);
  if (job instanceof Error) {
    res
      .status(StatusCodes.INTERNAL_SERVER_ERROR)
      .send('Unsupported or corrupt job');
    return;
  }
  const { name: jobName } = job;

  const isOrganizer = hasOrganizerRole(user, surveyDoc);

  const canViewAll =
    isOrganizer ||
    survey.dataVisibility === Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS;

  const ownerIdFilter = canViewAll ? null : userId;

  const tasks = job.tasks.sort((a, b) => a.index! - b.index!);

  const baseUrl = getRequestBaseUrl(req);
  const photoUrl: PhotoUrlFn = (submissionId, taskId) =>
    getExportMediaUrl(baseUrl, surveyId, submissionId, taskId);

  const loiProperties = new Set<string>();
  let query = db.fetchPartialLocationsOfInterest(surveyId, jobId, 1000);
  let lastVisible = null;
  do {
    const snapshot = await query.get();
    if (snapshot.empty) break;
    snapshot.docs.forEach(doc =>
      collectLoiProperties(doc, ownerIdFilter, loiProperties)
    );
    lastVisible = snapshot.docs[snapshot.docs.length - 1];
    query = query.startAfter(lastVisible);
  } while (lastVisible);

  const headers = getHeaders(tasks, loiProperties);

  const fileName = getFileName(jobName);
  const bucket = getStorageBucket();
  const file = bucket.file(getTempFilePath(userId, `${Date.now()}.csv`));
  const writeStream = file.createWriteStream({
    metadata: {
      contentType: 'text/csv',
      contentDisposition: `attachment; filename=${fileName}`,
    },
  });

  const csvStream = csv.format({
    delimiter: ',',
    headers,
    rowDelimiter: '\n',
    includeEndRowDelimiter: true, // Add \n to last row in CSV
    quote: false,
  });
  csvStream.pipe(writeStream);

  const rows = await db.fetchLoisSubmissions(
    surveyId,
    jobId,
    ownerIdFilter,
    50
  );

  for await (const row of rows) {
    try {
      const [loiDoc, submissionDoc] = row;
      const loi = toMessage(loiDoc.data(), Pb.LocationOfInterest);
      if (loi instanceof Error) throw loi;
      if (isAccessibleLoi(loi, ownerIdFilter) && submissionDoc) {
        const submission = toMessage(submissionDoc.data(), Pb.Submission);
        if (submission instanceof Error) throw submission;
        writeRow(csvStream, loiProperties, tasks, loi, photoUrl, submission);
      } else {
        writeRow(csvStream, loiProperties, tasks, loi, photoUrl);
      }
    } catch (e) {
      console.debug('Skipping row', e);
    }
  }

  csvStream.end();

  await new Promise<void>((resolve, reject) => {
    writeStream.on('finish', resolve);
    writeStream.on('error', reject);
  });

  res.redirect(await getFirebaseDownloadUrl(file));
}

function getHeaders(tasks: Pb.ITask[], loiProperties: Set<string>): string[] {
  const headers = [
    'system:index',
    'geometry',
    ...loiProperties,
    ...tasks.flatMap(getTaskHeaders),
    'data:contributor_name',
    'data:contributor_email',
    'data:created_client_timestamp',
    'data:created_server_timestamp',
  ];
  return headers.map(quote);
}

/**
 * Returns the headers of the columns for a specific task. Tasks capturing the
 * device location have additional columns for accuracy and altitude.
 */
function getTaskHeaders(task: Pb.ITask): string[] {
  const header = `data:${task.prompt || ''}`;
  if (capturesDeviceLocation(task)) {
    return [header, `${header}:accuracy`, `${header}:altitude`];
  }
  return [header];
}

/**
 * Returns true for "Capture location" tasks, and for "Drop pin" tasks
 * requiring the pin to be placed at the device location.
 */
function capturesDeviceLocation(task: Pb.ITask): boolean {
  const { captureLocation, drawGeometry } = task;
  if (captureLocation) return true;
  return (
    !!drawGeometry?.requireDeviceLocation &&
    !!drawGeometry.allowedMethods?.includes(
      Pb.Task.DrawGeometry.Method.DROP_PIN
    )
  );
}

/**
 * Returns the URL at which the photo submitted for a task can be fetched. The
 * URL names the submission rather than the storage object, so it keeps working
 * across re-exports and stays subject to the survey's access rules.
 */
type PhotoUrlFn = (submissionId: string, taskId: string) => string;

function writeRow(
  csvStream: csv.CsvFormatterStream<csv.Row, csv.Row>,
  loiProperties: Set<string>,
  tasks: Pb.ITask[],
  loi: Pb.LocationOfInterest,
  photoUrl: PhotoUrlFn,
  submission?: Pb.Submission
) {
  if (!loi.geometry) {
    console.debug(`Skipping LOI ${loi.id} - missing geometry`);
    return;
  }
  const row = [];
  // Header: system:index
  row.push(quote(loi.customTag));
  // Header: geometry
  row.push(quote(toWkt(loi.geometry)));
  // Header: One column for each loi property (merged over all properties across all LOIs)
  getPropertiesByName(loi, loiProperties).forEach(v => row.push(quote(v)));
  if (submission) {
    const { taskData: data } = submission;
    // Header: One or more columns for each task
    const taskPhotoUrl = (taskId: string) => photoUrl(submission.id, taskId);
    tasks.forEach(task =>
      getTaskValues(task, data, taskPhotoUrl).forEach(v => row.push(quote(v)))
    );
    // Header: contributor_username, contributor_email, created_client_timestamp, created_server_timestamp
    const { created } = submission;
    row.push(quote(created?.displayName));
    row.push(quote(created?.emailAddress));
    row.push(
      quote(new Date(timestampToInt(created?.clientTimestamp)).toISOString())
    );
    row.push(
      quote(new Date(timestampToInt(created?.serverTimestamp)).toISOString())
    );
  }
  csvStream.write(row);
}

function quote(value: unknown): string {
  if (value == null) {
    return '';
  }
  if (typeof value === 'number') {
    return value.toString();
  }
  const escaped = String(value).replace(/"/g, '""');
  return `"${escaped}"`;
}

function toWkt(geometry: Pb.IGeometry): string {
  return geojsonToWKT(toGeoJsonGeometry(geometry));
}

/**
 * Returns the values of the columns for a specific task, in the same order as
 * the headers returned by `getTaskHeaders()`.
 */
function getTaskValues(
  task: Pb.ITask,
  data: Pb.ITaskData[],
  photoUrl: (taskId: string) => string
): (string | number | null)[] {
  const result = data.find(d => d.taskId === task.id);
  const value = getValue(task, result, photoUrl);
  if (!capturesDeviceLocation(task)) return [value];
  const location = result?.skipped
    ? null
    : (result?.captureLocationResult ?? result?.drawGeometryResult);
  // Unset accuracy and altitude are read as 0, so they are exported as empty.
  return [value, location?.accuracy || null, location?.altitude || null];
}

/**
 * Returns the string or number representation of a specific task element
 * result.
 */
function getValue(
  task: Pb.ITask,
  result: Pb.ITaskData | undefined,
  photoUrl: (taskId: string) => string
): string | number | null {
  if (!result || result.skipped) return null;
  const {
    textResponse,
    numberResponse,
    dateTimeResponse,
    multipleChoiceResponses,
    drawGeometryResult,
    captureLocationResult,
    takePhotoResult,
  } = result;
  if (textResponse) return textResponse.text ?? null;
  else if (numberResponse) return numberResponse.number ?? null;
  else if (dateTimeResponse) return getDateTimeValue(dateTimeResponse);
  else if (multipleChoiceResponses)
    return getMultipleChoiceValues(task, multipleChoiceResponses);
  else if (drawGeometryResult?.geometry) {
    // TODO(#1248): Test when implementing other plot annotations feature.
    return toWkt(drawGeometryResult.geometry);
  } else if (captureLocationResult) {
    return toWkt(
      new Pb.Geometry({
        point: new Pb.Point({
          coordinates: captureLocationResult.coordinates,
        }),
      })
    );
  } else if (takePhotoResult)
    return takePhotoResult.photoPath ? photoUrl(task.id!) : null;
  else return null;
}

function getDateTimeValue(
  response: Pb.TaskData.IDateTimeResponse
): string | null {
  const seconds = response.dateTime?.seconds;
  if (seconds == null) {
    return null;
  }
  return new Date(Number(seconds) * 1000).toISOString();
}

/**
 * Returns a comma-separated list of the labels of the
 * specified multiple choice option, or the raw text if "Other".
 */
function getMultipleChoiceValues(
  task: Pb.ITask,
  responses: Pb.TaskData.IMultipleChoiceResponses
) {
  const values =
    responses.selectedOptionIds?.map(
      id => getMultipleChoiceLabel(task, id) || '#ERR'
    ) || [];
  // Temporary workaround: Ensure at least one value is present: if no values are selected and 'otherText' is empty, add 'Other' as a fallback.
  // https://github.com/google/ground-android/issues/2846
  if (values.length === 0 && !responses.otherText) values.push('Other');
  if (responses.otherText)
    values.push(
      responses.otherText.trim() !== ''
        ? `Other: ${responses.otherText}`
        : 'Other'
    );
  return values.join(',');
}

function getMultipleChoiceLabel(task: Pb.ITask, id: string): string | null {
  return (
    task?.multipleChoiceQuestion?.options?.find(
      (o: Pb.Task.MultipleChoiceQuestion.IOption) => o.id === id
    )?.label ?? null
  );
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string | null) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.csv`;
}

/**
 * Adds the property keys of an accessible LOI document to the provided set.
 */
function collectLoiProperties(
  doc: QueryDocumentSnapshot,
  ownerIdFilter: string | null,
  loiProperties: Set<string>
): void {
  const loi = doc.data();
  if (
    loi[l.source] === Pb.LocationOfInterest.Source.IMPORTED ||
    ownerIdFilter === null ||
    loi[l.ownerId] === ownerIdFilter
  ) {
    Object.keys(loi[l.properties] || {}).forEach(key => loiProperties.add(key));
  }
}

/**
 * Retrieves the values of specified properties from a LocationOfInterest object.
 */
function getPropertiesByName(
  loi: Pb.LocationOfInterest,
  properties: Set<string | number>
): List<string | number | null> {
  // Fill the list with the value associated with a prop, if the LOI has it, otherwise leave empty.
  return List([...properties])
    .map(prop => loi.properties[prop])
    .map(value => value?.stringValue || value?.numericValue || null);
}
