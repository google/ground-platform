/**
 * @license
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
import {geojsonToWKT} from '@terraformer/wkt';
import {db} from '@/common/context';
import * as HttpStatus from 'http-status-codes';
import {Datastore} from './common/datastore';

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
  res: functions.Response<any>
) {
  const surveyId = req.query.survey as string;
  const jobId = req.query.job as string;
  const survey = await db.fetchSurvey(surveyId);
  if (!survey.exists) {
    res.status(HttpStatus.NOT_FOUND).send('Survey not found');
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
  // Feature ID column conforms to desktop GIS defaults:
  //   "FID" is default used by ArcGIS but is case-insensitive.
  //   "fid" is default used by QGIS and is case-sensitive.
  headers.push('fid');
  headers.push('geometry');
  const allLoiProperties = getAllPropertiesName(lois);
  headers.push(...allLoiProperties);
  tasks.forEach(task => headers.push(task.label));

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
      // Header: fid
      row.push(loi.get('properties').id || '');
      // Header: geometry
      row.push(toWkt(loi.get('geometry')) || '');
      // Header: One column for each loi property (merged over all properties across all LOIs)
      row.push(...extractLoiProperties(loi, allLoiProperties))
      // TODO(#1288): Clean up remaining references to old responses field
      const data =
        submission['data'] ||
        submission['responses'] ||
        submission['results'] ||
        {};
      // Header: One column for each task
      tasks.forEach((task, taskId) => row.push(getValue(taskId, task, data)));
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

function getAllPropertiesName(
  lois: FirebaseFirestore.QuerySnapshot<FirebaseFirestore.DocumentData>): string[] {
  let properties: string[] = [];
  lois.forEach((loi) => {
    Object.keys(loi.get('properties')).forEach((prop) => {
      // We don't push 'id' because it's added initially in the first column of the CSV file.
      if (!properties.includes(prop) && prop !== 'id') {
        properties.push(prop);
      }
    })
  })

  return properties
}

function extractLoiProperties(
  loi: FirebaseFirestore.QueryDocumentSnapshot<FirebaseFirestore.DocumentData>,
  allLoiProperties: string[]): string[] {
    let res: string[] = [];
    if (!loi.get('properties')) {
      throw Error(`${loi.data} does not have a 'properties' field`)
    }

    // Fill the list with the value associated with a prop, if the LOI has it, otherwise leave empty.
    allLoiProperties.forEach((prop)=> res.push(loi.get('properties')[prop] || ''))
    return res
}