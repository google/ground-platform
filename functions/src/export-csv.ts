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

import Pb = GroundProtos.google.ground.v1beta1;
const sb = registry.getFieldIds(Pb.Submission);
const l = registry.getFieldIds(Pb.LocationOfInterest);

// TODO(#1277): Use a shared model with web
type Task = {
  readonly id: string;
  readonly type: string;
  readonly label: string;
  readonly required: boolean;
  readonly index: number;
  readonly multipleChoice?: any;
  readonly options?: any;
  readonly hasOtherOption?: boolean;
};

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
  const survey = await db.fetchSurvey(surveyId);
  if (!survey.exists) {
    res.status(HttpStatus.NOT_FOUND).send('Survey not found');
    return;
  }
  if (!canExport(user, survey)) {
    res.status(HttpStatus.FORBIDDEN).send('Permission denied');
    return;
  }
  console.log(`Exporting survey '${surveyId}', job '${jobId}'`);

  // TODO(#1779): Get job metadata from  `/surveys/{surveyId}/jobs` instead.
  const jobs = survey.get('jobs') || {};
  const job = jobs[jobId] || {};
  const jobName = job.name;
  const tasksObject = (job['tasks'] as {[id: string]: Task}) || {};
  const tasks = new Map(Object.entries(tasksObject));
  const loiDocs = await db.fetchLocationsOfInterestByJobId(survey.id, jobId);
  const loiProperties = getPropertyNames(loiDocs);
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

  const submissionsByLoi = await getSubmissionsByLoi(survey.id, jobId);

  loiDocs.forEach(loiDoc => {
    const loi = toMessage(loiDoc.data(), Pb.LocationOfInterest);
    if (loi instanceof Error) {
      throw loi;
    }
    const submissions = submissionsByLoi[loiDoc.id] || [{}];
    submissions.forEach(submissionDict => {
      try {
        const submission = toMessage(submissionDict, Pb.Submission);
        if (submission instanceof Error) {
          throw submission;
        }
        writeRow(csvStream, loiProperties, tasks, loi, submission);
      } catch (e) {
        console.debug('Skipping row', e);
      }
    });
  });
  res.status(HttpStatus.OK);
  csvStream.end();
}

function getHeaders(
  tasks: Map<string, Task>,
  loiProperties: Set<string>
): string[] {
  const headers = [];
  headers.push('system:index');
  headers.push('geometry');
  headers.push(...loiProperties);
  // TODO(#1936): Use `index` field to export columns in correct order.
  tasks.forEach(task => headers.push('data:' + task.label));
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
  tasks: Map<string, Task>,
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
  tasks.forEach((task, taskId) =>
    row.push(quote(getValue(taskId, task, data)))
  );
  // Header: contributor_username, contributor_email
  row.push(quote(submission.created?.displayName));
  row.push(quote(submission.created?.emailAddress));
  csvStream.write(row);
}

function quote(value: any): string {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'number') {
    return value.toString();
  }
  const escaped = value.toString().replace('"', '""');
  return '"' + escaped + '"';
}

function toWkt(geometry: Pb.IGeometry): string {
  return geojsonToWKT(toGeoJsonGeometry(geometry));
}

/**
 * Returns the string representation of a specific task element result.
 */
function getValue(taskId: string, task: Task, data: Pb.ITaskData[]): any {
  const result = data.find(d => d.taskId === taskId);
  if (result?.multipleChoiceResponses) {
    return result.multipleChoiceResponses?.selectedOptionIds
      ?.map(id => getMultipleChoiceValues(id, task))
      .join(', ');
  } else if (result?.captureLocationResult) {
    // TODO(): Include alitude and accuracy in separate columns.
    return toWkt(
      new Pb.Geometry({
        point: new Pb.Point({
          coordinates: result.captureLocationResult.coordinates,
        }),
      })
    );
  } else if (result?.drawGeometryResult?.geometry) {
    return toWkt(result.drawGeometryResult.geometry);
  } else {
    return result;
  }
}

/**
 * Returns the code associated with a specified multiple choice option, or if
 * the code is not defined, returns the label in English.
 */
function getMultipleChoiceValues(id: any, task: Task) {
  // "Other" options are encoded to be surrounded by square brakets, to let us
  // distinguish them from the other pre-defined options.
  if (isOtherOption(id)) {
    return extractOtherOption(id);
  }
  const options = task.options || {};
  const option = options[id] || {};
  const label = option.label || {};
  // TODO: i18n.
  return option.code || label || '';
}

function isOtherOption(submission: any): boolean {
  // "Other" options are encoded to be surrounded by square brakets, to let us
  // distinguish them from the other pre-defined options.
  return (
    typeof submission === 'string' &&
    submission.startsWith('[ ') &&
    submission.endsWith(' ]')
  );
}

function extractOtherOption(submission: string): string {
  const match = submission.match(/\[(.*?)\]/); // Match any text between []
  return match ? match[1].trim() : ''; // Extract the match and remove spaces
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
    lois.docs.map(loi => Object.keys(loi.get(l.properties) || {})).flat()
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
