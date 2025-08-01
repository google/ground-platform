/**
 * Copyright 2025 The Ground Authors.
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
import {Map} from 'immutable';
import JSONStream from 'jsonstream-ts';
import {canExport, canImport} from './common/auth';
import {getDatastore} from './common/context';
import * as HttpStatus from 'http-status-codes';
import {DecodedIdToken} from 'firebase-admin/auth';
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {toGeoJsonGeometry} from '@ground/lib';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Iterates over all LOIs in a job returning a valid GeoJSON file.
 */
export async function exportGeojsonHandler(
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
  const ownerId = canImport(user, surveyDoc) ? undefined : userId;

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

  const survey = toMessage(surveyDoc.data()!, Pb.Survey);

  res.type('application/json');
  res.setHeader(
    'Content-Disposition',
    'attachment; filename=' + getFileName(jobName)
  );

  /**
   * Creates a JSON stream representing a GeoJSON FeatureCollection.
   * The arguments follow a pattern of open, separator, close, and
   * indent, which helps to organize the JSON structure for clarity.
   */
  const jsonStream = JSONStream.stringify(
    '{\n  "type": "FeatureCollection",\n  "features": [\n    ',
    ',\n',
    '\n  ]\n}',
    '  '
  );
  jsonStream.pipe(res);

  const rows = await db.fetchLocationsOfInterest(surveyId, jobId);

  rows.forEach(row => {
    try {
      const loi = toMessage(row.data(), Pb.LocationOfInterest);
      if (loi instanceof Error) throw loi;
      if (isAccessibleLoi(survey, loi, ownerId)) {
        writeRow(jsonStream, loi);
      }
    } catch (e) {
      console.debug('Skipping row', e);
    }
  });

  res.status(HttpStatus.OK);
  jsonStream.end();
}

function writeRow(jsonStream: any, loi: Pb.LocationOfInterest) {
  if (!loi.geometry) {
    console.debug(`Skipping LOI ${loi.id} - missing geometry`);
    return;
  }
  const geoJsonGeometry = toGeoJsonGeometry(loi.geometry);
  const row = {
    type: 'Feature',
    properties: propertiesPbToModel(loi.properties).toObject(),
    geometry: geoJsonGeometry,
  };
  jsonStream.write(row);
}

/**
 * Checks if a Location of Interest (LOI) is accessible to a given user.
 */
function isAccessibleLoi(
  survey: Pb.ISurvey,
  loi: Pb.ILocationOfInterest,
  ownerId?: string
) {
  if (
    survey.dataVisibility === Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS
  )
    return true;
  const isFieldData = loi.source === Pb.LocationOfInterest.Source.FIELD_DATA;
  return ownerId ? isFieldData && loi.ownerId === ownerId : true;
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string | null) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.geojson`;
}

function propertiesPbToModel(pb: {
  [k: string]: Pb.LocationOfInterest.IProperty;
}): Map<string, string | number> {
  const properties: {[k: string]: string | number} = {};
  for (const k of Object.keys(pb)) {
    const v = pb[k].stringValue || pb[k].numericValue;
    if (v !== null && v !== undefined) {
      properties[k] = v;
    }
  }
  return Map(properties);
}
