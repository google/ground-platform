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
import {Datastore} from './common/datastore';
import {DecodedIdToken} from 'firebase-admin/auth';
import {List} from 'immutable';

// TODO(#1277): Use a shared model with web
type Task = {
  readonly id: string;
  readonly type: string;
  readonly label: string;
  readonly required: boolean;
  readonly index: number;
  readonly multipleChoice?: any;
  readonly options?: any;
};

// TODO: Refactor into meaningful pieces.
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

  const jobs = survey.get('jobs') || {};
  const job = jobs[jobId] || {};
  const jobName = job.name && (job.name['en'] as string);
  const tasksObject = (job['tasks'] as {[id: string]: Task}) || {};
  const tasks = new Map(Object.entries(tasksObject));
  const lois = await db.fetchLocationsOfInterestByJobId(survey.id, jobId);

  const headers = [];
  headers.push('system:index');
  headers.push('geometry');
  const allLoiProperties = getPropertyNames(lois);
  headers.push(...allLoiProperties);
  tasks.forEach(task => headers.push('data:' + task.label));
  headers.push('data:contributor_username');
  headers.push('data:contributor_email');

  res.type('text/csv');
  res.setHeader(
    'Content-Disposition',
    'attachment; filename=' + getFileName(jobName)
  );
  const csvStream = csv.format({
    delimiter: ',',
    headers,
    includeEndRowDelimiter: true,
    rowDelimiter: '\n',
    quoteColumns: true,
    quote: '"',
  });
  csvStream.pipe(res);

  const submissions = await db.fetchSubmissionsByJobId(survey.id, jobId);

  // Index submissions by LOI id in memory. This consumes more
  // memory than iterating over and streaming both LOI and submission`
  // collections simultaneously, but it's easier to read and maintain. This will
  // likely need to be optimized to scale to larger datasets.
  const submissionsByLocationOfInterest: {[name: string]: any[]} = {};
  submissions.forEach(submission => {
    const loiId = submission.get('loiId') as string;
    const arr: any[] = submissionsByLocationOfInterest[loiId] || [];
    arr.push(submission.data());
    submissionsByLocationOfInterest[loiId] = arr;
  });

  lois.forEach(loi => {
    const loiId = loi.id;
    const submissions = submissionsByLocationOfInterest[loiId] || [{}];
    submissions.forEach(submission => {
      const row = [];
      // Header: system:index
      row.push(loi.get('properties')?.id || '');
      // Header: geometry
      row.push(toWkt(loi.get('geometry')) || '');
      // Header: One column for each loi property (merged over all properties across all LOIs)
      row.push(...getPropertiesByName(loi, allLoiProperties));
      // TODO(#1288): Clean up remaining references to old responses field
      const data =
        submission['data'] ||
        submission['responses'] ||
        submission['results'] ||
        {};
      // Header: One column for each task
      tasks.forEach((task, taskId) => row.push(getValue(taskId, task, data)));
      // Header: contributor_username, contributor_email
      const contributor = submission['created']
        ? submission['created']['user']
        : [];
      row.push(contributor['displayName'] || '');
      row.push(contributor['email'] || '');
      csvStream.write(row);
    });
  });
  csvStream.end();
}

/**
 * Returns the WKT string converted from the given geometry object
 *
 * @param geometryObject - the GeoJSON geometry object extracted from the LOI. This should have format:
 *   {
 *      coordinates: any[],
 *      type: string
 *   }
 * @returns The WKT string version of the object
 * https://www.vertica.com/docs/9.3.x/HTML/Content/Authoring/AnalyzingData/Geospatial/Spatial_Definitions/WellknownTextWKT.htm
 *
 * @beta
 */
function toWkt(geometryObject: any): string {
  return geojsonToWKT(Datastore.fromFirestoreMap(geometryObject));
}

/**
 * Returns the string representation of a specific task element result.
 */
function getValue(taskId: string, task: Task, data: any) {
  const result = data[taskId] || '';
  if (
    task.type === 'multiple_choice' &&
    Array.isArray(result) &&
    task.options
  ) {
    return result.map(id => getMultipleChoiceValues(id, task)).join(', ');
  } else if (task.type === 'capture_location') {
    if (!result) {
      return '';
    }
    return toWkt(result.geometry || result);
  } else {
    return result;
  }
}

/**
 * Returns the code associated with a specified multiple choice option, or if
 * the code is not defined, returns the label in English.
 */
function getMultipleChoiceValues(id: any, task: Task) {
  const options = task.options || {};
  const option = options[id] || {};
  const label = option.label || {};
  // TODO: i18n.
  return option.code || label || '';
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.csv`;
}

function getPropertyNames(
  lois: FirebaseFirestore.QuerySnapshot<FirebaseFirestore.DocumentData>
): Set<string> {
  return new Set(
    lois.docs
      .map(loi =>
        Object.keys(loi.get('properties') || {})
          // Don't retrieve ID because we already store it in a separate column
          .filter(prop => prop !== 'id')
      )
      .flat()
  );
}

function getPropertiesByName(
  loi: FirebaseFirestore.QueryDocumentSnapshot<FirebaseFirestore.DocumentData>,
  allLoiProperties: Set<string>
): List<string> {
  // Fill the list with the value associated with a prop, if the LOI has it, otherwise leave empty.
  return List.of(...allLoiProperties).map(
    prop => (loi.get('properties') || {})[prop] || ''
  );
}
