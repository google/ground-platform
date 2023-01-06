/**
 * @license
 * Copyright 2020 Google LLC
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
  const tasks = job['tasks'] || {};
  const task = (Object.values(tasks)[0] as any) || {};
  const elementMap = task['elements'] || {};
  const elements = Object.keys(elementMap)
    .map(elementId => ({id: elementId, ...elementMap[elementId]}))
    .sort((a, b) => a.index - b.index);

  const headers = [];
  headers.push('loi_id');
  headers.push('loi_name');
  headers.push('latitude');
  headers.push('longitude');
  headers.push('geometry');
  elements.forEach(element => {
    const labelMap = element['label'] || {};
    const label = Object.values(labelMap)[0] || 'Unnamed step';
    headers.push(label);
  });

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

  const lois = await db.fetchLocationsOfInterestByJobId(survey.id, jobId);
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
    const location = loi.get('location') || {};
    const submissions = submissionsByLocationOfInterest[loiId] || [{}];
    submissions.forEach(submission => {
      const row = [];
      row.push(getId(loi));
      row.push(getLabel(loi));
      row.push(location['_latitude'] || '');
      row.push(location['_longitude'] || '');
      row.push(toWkt(loi.get('geoJson')) || '');
      const results = submission['results'] || {};
      elements
        .map(element => getValue(element, results))
        .forEach(value => row.push(value));
      csvStream.write(row);
    });
  });
  csvStream.end();
}

function toWkt(geoJsonString: string) {
  const geoJsonObject = parseGeoJson(geoJsonString);
  const geometry = getGeometry(geoJsonObject);
  return geometry ? geojsonToWKT(geometry) : '';
}

function parseGeoJson(jsonString: string) {
  try {
    // Note: Returns null when jsonString is null.
    return JSON.parse(jsonString);
  } catch (e) {
    return null;
  }
}

function getGeometry(geoJsonObject: any) {
  if (!geoJsonObject || typeof geoJsonObject !== 'object') {
    return null;
  }
  return geoJsonObject.geometry;
}

function getId(
  loi: FirebaseFirestore.QueryDocumentSnapshot<FirebaseFirestore.DocumentData>
) {
  const properties = loi.get('properties') || {};
  return (
    loi.get('id') ||
    properties['ID'] ||
    properties['id'] ||
    properties['id_prod'] ||
    ''
  );
}

function getLabel(
  loi: FirebaseFirestore.QueryDocumentSnapshot<FirebaseFirestore.DocumentData>
) {
  const properties = loi.get('properties') || {};
  return (
    properties['caption'] || properties['label'] || properties['title'] || ''
  );
}
/**
 * Returns the string representation of a specific task element result.
 */
function getValue(element: any, results: any) {
  const result = results[element.id] || '';
  if (
    element.type === 'multiple_choice' &&
    Array.isArray(result) &&
    element.options
  ) {
    return result.map(id => getMultipleChoiceValues(id, element)).join(', ');
  } else {
    return result;
  }
}

/**
 * Returns the code associated with a specified multiple choice option, or if
 * the code is not defined, returns the label in English.
 */
function getMultipleChoiceValues(id: any, element: any) {
  const options = element.options || {};
  const option = options[id] || {};
  const label = option.label || {};
  // TODO: i18n.
  return option.code || label['en'] || '';
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.csv`;
}
