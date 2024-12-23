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

import * as functions from 'firebase-functions';
import * as csv from '@fast-csv/format';
import {canExport, canImport} from './common/auth';
import {geojsonToWKT} from '@terraformer/wkt';
import {getDatastore} from './common/context';
import * as HttpStatus from 'http-status-codes';
import {DecodedIdToken} from 'firebase-admin/auth';
import {List} from 'immutable';
import {QuerySnapshot} from 'firebase-admin/firestore';
import {timestampToInt, toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {toGeoJsonGeometry} from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Iterates over all LOIs and submissions in a job, joining them
 * into a single table written to the response as a quote CSV file.
 */
export async function exportCsvHandler(
  req: functions.Request,
  res: functions.Response<any>,
  user: DecodedIdToken
) {
  const db = getDatastore();
  const {uid: userId} = user;
  const surveyId = req.query.survey as string;
  const jobId = req.query.job as string;
  const surveyDoc = await db.fetchSurvey(surveyId);
  if (!surveyDoc.exists) {
    res.status(HttpStatus.NOT_FOUND).send('Survey not found');
    return;
  }
  if (!canExport(user, surveyDoc)) {
    res.status(HttpStatus.FORBIDDEN).send('Permission denied');
    return;
  }
  const canManageSurvey = canImport(user, surveyDoc);
  const ownerId = !canManageSurvey ? userId : undefined;

  console.log(
    `Exporting survey '${surveyId}', job '${jobId}', owner '${
      ownerId || 'survey organizer'
    }'`
  );

  const jobDoc = await db.fetchJob(surveyId, jobId);
  if (!jobDoc.exists || !jobDoc.data()) {
    res.status(HttpStatus.NOT_FOUND).send('Job not found');
    return;
  }
  const job = toMessage(jobDoc.data()!, Pb.Job);
  if (job instanceof Error) {
    res
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .send('Unsupported or corrupt job');
    return;
  }
  const {name: jobName} = job;
  const tasks = job.tasks.sort((a, b) => a.index! - b.index!);
  const snapshot = await db.fetchLocationsOfInterest(surveyId, jobId);
  const loiProperties = createProperySetFromSnapshot(snapshot);
  const headers = getHeaders(tasks, loiProperties);

  res.type('text/csv');
  res.setHeader(
    'Content-Disposition',
    'attachment; filename=' + getFileName(jobName)
  );
  const csvStream = csv.format({
    delimiter: ',',
    headers,
    rowDelimiter: '\n',
    includeEndRowDelimiter: true, // Add \n to last row in CSV
    quote: false,
  });
  csvStream.pipe(res);

  const rows = await db.fetchLoisSubmissions(surveyId, jobId, ownerId, 50);

  for await (const row of rows) {
    try {
      const [loiDoc, submissionDoc] = row;
      const loi = toMessage(loiDoc.data(), Pb.LocationOfInterest);
      if (loi instanceof Error) throw loi;
      if (!isAccessibleLoi(loi, ownerId)) return;
      if (submissionDoc) {
        const submission = toMessage(submissionDoc.data(), Pb.Submission);
        if (submission instanceof Error) throw submission;
        writeRow(csvStream, loiProperties, tasks, loi, submission);
      } else {
        writeRow(csvStream, loiProperties, tasks, loi);
      }
    } catch (e) {
      console.debug('Skipping row', e);
    }
  }

  res.status(HttpStatus.OK);
  csvStream.end();
}

function getHeaders(tasks: Pb.ITask[], loiProperties: Set<string>): string[] {
  const headers = [
    'system:index',
    'geometry',
    ...loiProperties,
    ...tasks.map(task => `data:${task.prompt || ''}`),
    'data:contributor_name',
    'data:contributor_email',
    'data:created_client_timestamp',
    'data:created_server_timestamp',
  ];
  return headers.map(quote);
}

function writeRow(
  csvStream: csv.CsvFormatterStream<csv.Row, csv.Row>,
  loiProperties: Set<string>,
  tasks: Pb.ITask[],
  loi: Pb.LocationOfInterest,
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
    const {taskData: data} = submission;
    // Header: One column for each task
    tasks.forEach(task => row.push(quote(getValue(task, data))));
    // Header: contributor_username, contributor_email, created_client_timestamp, created_server_timestamp
    const {created} = submission;
    row.push(quote(created?.displayName));
    row.push(quote(created?.emailAddress));
    row.push(
      quote(new Date(timestampToInt(created?.clientTimestamp)).toISOString())
    );
    row.push(
      quote(new Date(timestampToInt(created?.serverTimestamp)).toISOString())
    );
  } else {
    row.concat(new Array(tasks.length + 4).fill(''));
  }
  csvStream.write(row);
}

function quote(value: any): string {
  if (value == null) {
    return '';
  }
  if (typeof value === 'number') {
    return value.toString();
  }
  const escaped = value.toString().replaceAll('"', '""');
  return `"${escaped}"`;
}

function toWkt(geometry: Pb.IGeometry): string {
  return geojsonToWKT(toGeoJsonGeometry(geometry));
}

/**
 * Checks if a Location of Interest (LOI) is accessible to a given user.
 */
function isAccessibleLoi(loi: Pb.ILocationOfInterest, ownerId?: string) {
  const isFieldData = loi.source === Pb.LocationOfInterest.Source.FIELD_DATA;
  return ownerId ? isFieldData && loi.ownerId === ownerId : true;
}

/**
 * Returns the string or number representation of a specific task element result.
 */
function getValue(
  task: Pb.ITask,
  data: Pb.ITaskData[]
): string | number | null {
  const result = data.find(d => d.taskId === task.id);
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
    // TODO(#1916): Include altitude and accuracy in separate columns.
    return toWkt(
      new Pb.Geometry({
        point: new Pb.Point({
          coordinates: captureLocationResult.coordinates,
        }),
      })
    );
  } else if (takePhotoResult) return getPhotoUrlValue(takePhotoResult);
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
    task?.multipleChoiceQuestion?.options?.find((o: any) => o.id === id)
      ?.label ?? null
  );
}

function getPhotoUrlValue(result: Pb.TaskData.ITakePhotoResult): string | null {
  return result?.photoPath || null;
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string | null) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.csv`;
}

function createProperySetFromSnapshot(
  snapshot: QuerySnapshot,
  ownerId?: string
): Set<string> {
  const allKeys = new Set<string>();
  snapshot.forEach(doc => {
    const loi = toMessage(doc.data(), Pb.LocationOfInterest);
    if (loi instanceof Error) return;
    if (!isAccessibleLoi(loi, ownerId)) return;
    const properties = loi.properties;
    for (const key of Object.keys(properties || {})) {
      allKeys.add(key);
    }
  });
  return allKeys;
}

function getPropertiesByName(
  loi: Pb.LocationOfInterest,
  properties: Set<string | number>
): List<string | number | null> {
  // Fill the list with the value associated with a prop, if the LOI has it, otherwise leave empty.
  return List.of(...properties)
    .map(prop => loi.properties[prop])
    .map(value => value?.stringValue || value?.numericValue || null);
}
