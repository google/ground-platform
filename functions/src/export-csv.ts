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
import {canExport} from './common/auth';
import {geojsonToWKT} from '@terraformer/wkt';
import {getDatastore} from './common/context';
import * as HttpStatus from 'http-status-codes';
import {DecodedIdToken} from 'firebase-admin/auth';
import {List} from 'immutable';
import {DocumentData, QuerySnapshot} from 'firebase-admin/firestore';
import {registry, toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {toGeoJsonGeometry} from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;
const sb = registry.getFieldIds(Pb.Submission);
const l = registry.getFieldIds(Pb.LocationOfInterest);

/** A dictionary of submissions values (array) keyed by loi ID. */
type SubmissionDict = {[key: string]: any[]};

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
  console.log(`Exporting survey '${surveyId}', job '${jobId}'`);

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
  const jobName = job.name;
  const loiDocs = await db.fetchLocationsOfInterestByJobId(surveyId, jobId);
  const loiProperties = getPropertyNames(loiDocs);
  const headers = getHeaders(job.tasks, loiProperties);

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

  const submissionsByLoi = await getSubmissionsByLoi(surveyId, jobId);

  loiDocs.forEach(loiDoc => {
    const loi = toMessage(loiDoc.data(), Pb.LocationOfInterest);
    if (loi instanceof Error) {
      throw loi;
    }
    // Submissions to be joined with the current LOI, resulting in one row
    // per submission. For LOIs with no submissions, a single empty submission
    // is added to ensure the LOI is represented in the output as a row with
    // LOI fields, but no submission data.
    const submissions = submissionsByLoi[loiDoc.id] || [{}];
    submissions.forEach(submissionDict =>
      writeSubmissions(csvStream, loiProperties, job.tasks, loi, submissionDict)
    );
  });
  res.status(HttpStatus.OK);
  csvStream.end();
}

function writeSubmissions(
  csvStream: csv.CsvFormatterStream<csv.Row, csv.Row>,
  loiProperties: Set<string>,
  tasks: Pb.ITask[],
  loi: Pb.LocationOfInterest,
  submissionDict: SubmissionDict
) {
  try {
    const submission = toMessage(submissionDict, Pb.Submission);
    if (submission instanceof Error) {
      throw submission;
    }
    writeRow(csvStream, loiProperties, tasks, loi, submission);
  } catch (e) {
    console.debug('Skipping row', e);
  }
}

function getHeaders(tasks: Pb.ITask[], loiProperties: Set<string>): string[] {
  const headers = [];
  headers.push('system:index');
  headers.push('geometry');
  headers.push(...loiProperties);
  // TODO(#1936): Use `index` field to export columns in correct order.
  tasks.forEach(task => headers.push('data:' + (task.prompt || '')));
  headers.push('data:contributor_name');
  headers.push('data:contributor_email');
  return headers.map(quote);
}

/**
 * Returns all submissions in the specified job, indexed by LOI ID.
 * Note: Indexes submissions by LOI id in memory. This consumes more
 * memory than iterating over and streaming both LOI and submission
 * collections simultaneously, but it's easier to read and maintain. This
 * function will need to be optimized to scale to larger datasets than
 * can fit in memory.
 */
async function getSubmissionsByLoi(
  surveyId: string,
  jobId: string
): Promise<SubmissionDict> {
  const db = getDatastore();
  const submissions = await db.fetchSubmissionsByJobId(surveyId, jobId);
  const submissionsByLoi: {[name: string]: any[]} = {};
  submissions.forEach(submission => {
    const loiId = submission.get(sb.loiId) as string;
    const arr: any[] = submissionsByLoi[loiId] || [];
    arr.push(submission.data());
    submissionsByLoi[loiId] = arr;
  });
  return submissionsByLoi;
}

function writeRow(
  csvStream: csv.CsvFormatterStream<csv.Row, csv.Row>,
  loiProperties: Set<string>,
  tasks: Pb.ITask[],
  loi: Pb.LocationOfInterest,
  submission: Pb.Submission
) {
  const row = [];
  // Header: system:index
  row.push(quote(loi.customTag));
  // Header: geometry
  if (!loi.geometry) {
    console.debug(`Skipping LOI ${loi.id} - missing geometry`);
    return;
  }
  row.push(quote(toWkt(loi.geometry)));
  // Header: One column for each loi property (merged over all properties across all LOIs)
  getPropertiesByName(loi, loiProperties).forEach(v => row.push(quote(v)));
  const data = submission.taskData;
  // Header: One column for each task
  tasks.forEach(task => row.push(quote(getValue(task, data))));
  // Header: contributor_username, contributor_email
  row.push(quote(submission.created?.displayName));
  row.push(quote(submission.created?.emailAddress));
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
 * Returns the string or number representation of a specific task element result.
 */
function getValue(
  task: Pb.ITask,
  data: Pb.ITaskData[]
): string | number | null {
  const result = data.find(d => d.taskId === task.id);
  if (!result) {
    return null;
  }
  if (result.textResponse) {
    return result.textResponse.text ?? null;
  } else if (result.numberResponse) {
    return getNumberValue(result.numberResponse);
  } else if (result.dateTimeResponse) {
    return getDateTimeValue(result.dateTimeResponse);
  } else if (result.multipleChoiceResponses) {
    return getMultipleChoiceValues(task, result.multipleChoiceResponses);
  } else if (result.captureLocationResult) {
    // TODO(#1916): Include altitude and accuracy in separate columns.
    return toWkt(
      new Pb.Geometry({
        point: new Pb.Point({
          coordinates: result.captureLocationResult.coordinates,
        }),
      })
    );
  } else if (result.drawGeometryResult?.geometry) {
    // TODO(#1248): Test when implementing other plot annotations feature.
    return toWkt(result.drawGeometryResult.geometry);
  } else if (result.takePhotoResult) {
    return getPhotoUrlValue(result.takePhotoResult);
  } else {
    return null;
  }
}

function getNumberValue(response: Pb.TaskData.INumberResponse): number | null {
  return response.number ?? null;
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
  if (responses.otherText && responses.otherText.trim() !== '')
    values.push(responses.otherText);
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

function getPropertyNames(lois: QuerySnapshot<DocumentData>): Set<string> {
  return new Set(
    lois.docs.flatMap(loi => Object.keys(loi.get(l.properties) || {}))
  );
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
