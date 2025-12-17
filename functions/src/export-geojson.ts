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
import { canExport, hasOrganizerRole } from './common/auth';
import { getDatastore } from './common/context';
import { isAccessibleLoi } from './common/utils';
import * as HttpStatus from 'http-status-codes';
import { DecodedIdToken } from 'firebase-admin/auth';
import { toMessage } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { toGeoJsonGeometry } from '@ground/lib';

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
  const { uid: userId } = user;
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
  const survey = toMessage(surveyDoc.data()!, Pb.Survey);
  if (survey instanceof Error) {
    res
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .send('Unsupported or corrupt survey');
    return;
  }

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
  const { name: jobName } = job;

  const isOrganizer = hasOrganizerRole(user, surveyDoc);

  const canViewAll =
    isOrganizer ||
    survey.dataVisibility === Pb.Survey.DataVisibility.ALL_SURVEY_PARTICIPANTS;

  const ownerIdFilter = canViewAll ? null : userId;

  res.type('application/json');
  res.setHeader(
    'Content-Disposition',
    'attachment; filename=' + getFileName(jobName)
  );
  res.status(HttpStatus.OK);

  // Write opening of FeatureCollection manually
  res.write('{\n  "type": "FeatureCollection",\n  "features": [\n');

  // Fetch all locations of interest
  const rows = await db.fetchLocationsOfInterest(surveyId, jobId);

  let first = true;
  for (const row of rows.docs) {
    try {
      const loi = toMessage(row.data(), Pb.LocationOfInterest);
      if (loi instanceof Error) throw loi;
      if (isAccessibleLoi(loi, ownerIdFilter)) {
        const feature = buildFeature(loi);
        if (!feature) continue;

        // Manually write the separator comma before each feature except the first one.
        if (!first) {
          res.write(',\n');
        } else {
          first = false;
        }

        // Use JSON.stringify to convert the feature object to a string and write it.
        res.write(JSON.stringify(feature, null, 2));
      }
    } catch (e) {
      console.debug('Skipping row', e);
    }
  }

  // Close the FeatureCollection after the loop completes.
  res.write('\n  ]\n}');
  res.end();
}

function buildFeature(loi: Pb.LocationOfInterest) {
  if (!loi.geometry) {
    console.debug(`Skipping LOI ${loi.id} - missing geometry`);
    return null;
  }
  return {
    type: 'Feature',
    properties: propertiesPbToObject(loi.properties),
    geometry: toGeoJsonGeometry(loi.geometry),
  };
}

/**
 * Returns the file name in lowercase (replacing any special characters with '-') for csv export
 */
function getFileName(jobName: string | null) {
  jobName = jobName || 'ground-export';
  const fileBase = jobName.toLowerCase().replace(/[^a-z0-9]/gi, '-');
  return `${fileBase}.geojson`;
}

function propertiesPbToObject(pb: {
  [k: string]: Pb.LocationOfInterest.IProperty;
}): { [k: string]: string | number } {
  const properties: { [k: string]: string | number } = {};
  for (const k of Object.keys(pb).sort()) {
    const v = pb[k].stringValue || pb[k].numericValue;
    if (v !== null && v !== undefined) {
      properties[k] = v;
    }
  }
  return properties;
}
