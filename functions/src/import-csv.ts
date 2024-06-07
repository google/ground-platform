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

import {https, Response} from 'firebase-functions';
import HttpStatus from 'http-status-codes';
import csvParser from 'csv-parser';
import Busboy from 'busboy';
import {db} from './common/context';
import {GeoPoint} from 'firebase-admin/firestore';
import {DecodedIdToken} from 'firebase-admin/auth';
import {canImport} from './common/auth';
import {GroundProtos} from '@ground/proto';
import {toDocumentData, deepMerge} from '@ground/lib';
import {Datastore} from './common/datastore';

import Pb = GroundProtos.google.ground.v1beta1;

/**
 * Streams a multipart HTTP POSTed form containing a CSV 'file' and required
 * 'survey' id and 'job' id to the database.
 */
export async function importCsvHandler(
  req: https.Request,
  res: Response<any>,
  user: DecodedIdToken
) {
  // Based on https://cloud.google.com/functions/docs/writing/http#multipart_data
  if (req.method !== 'POST') {
    res.status(HttpStatus.METHOD_NOT_ALLOWED).end();
    return;
  }
  const busboy = Busboy({headers: req.headers});

  // Dictionary used to accumulate form values, keyed by field name.
  const params: {[name: string]: string} = {};

  // Accumulate Promises for insert operations, so we don't finalize the res
  // stream before operations are complete.
  const inserts: any[] = [];

  // Handle non-file fields in the form. Survey and job must appear
  // before the file for the file handler to work properly.
  busboy.on('field', (key, val) => {
    params[key] = val;
  });

  // This code will process each file uploaded.
  busboy.on('file', async (_key, file, _) => {
    const {survey: surveyId, job: jobId} = params;
    if (!surveyId || !jobId) {
      res.status(HttpStatus.BAD_REQUEST).end();
      return;
    }

    const survey = await db.fetchSurvey(surveyId);
    if (!survey.exists) {
      res.status(HttpStatus.NOT_FOUND).send('Survey not found');
      return;
    }
    if (!canImport(user, survey)) {
      res.status(HttpStatus.FORBIDDEN).send('Permission denied');
      return;
    }

    console.log(`Importing CSV into survey '${surveyId}', job '${jobId}'`);

    // Pipe file through CSV parser lib, inserting each row in the db as it is
    // received.

    file.pipe(csvParser()).on('data', async row => {
      try {
        inserts.push(insertRow(surveyId, jobId, row));
      } catch (err: any) {
        console.error(err);
        req.unpipe(busboy);
        res
          .status(HttpStatus.BAD_REQUEST)
          .end(JSON.stringify({error: err.message}));
      }
    });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on('finish', async () => {
    await Promise.all(inserts);
    const count = inserts.length;
    console.log(`Inserted ${count} rows`);
    res.status(HttpStatus.OK).end(JSON.stringify({count}));
  });

  busboy.on('error', (err: any) => {
    console.error('Busboy error', err);
    req.unpipe(busboy);
    res.status(HttpStatus.INTERNAL_SERVER_ERROR).end(err.message);
  });

  // Use this for Cloud Functions rather than `req.pipe(busboy)`:
  // https://github.com/mscdex/busboy/issues/229#issuecomment-648303108
  busboy.end(req.rawBody);
}

/**
 * Transforms a dictionary of string in the form A->B[] into a dictionary of
 * strings in the form B->A. Values in B[] are assumed to appear at most once
 * in the array values in the provided dictionary.
 */
function invertAndFlatten(obj: any) {
  return Object.keys(obj)
    .flatMap(k => obj[k].map((v: any) => ({k, v})))
    .reduce((o, {v, k}) => {
      o[v] = k;
      return o;
    }, {});
}

/**
 * Dictionary of lowercase aliases for recognized LOI properties. Case
 * is ignored when mapping column aliases to LOI properties.
 */
const SPECIAL_COLUMN_NAMES = invertAndFlatten({
  customTag: ['system:index'],
  lat: ['lat', 'latitude', 'y'],
  lng: ['lng', 'lon', 'long', 'lng', 'x'],
});

async function insertRow(surveyId: string, jobId: string, row: any) {
  const loi = csvRowToLocationOfInterestLegacy(row, jobId) || {};
  const loiPb = csvRowToLocationOfInterestPb(row, jobId);
  if (loiPb) deepMerge(loi, toDocumentData(loiPb) || {});
  if (loi.length) {
    await db.insertLocationOfInterest(surveyId, loi);
  }
}

/**
 * Convert the provided row (key-value pairs) and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function csvRowToLocationOfInterestLegacy(row: any, jobId: string) {
  const data: any = {jobId};
  const properties: {[name: string]: any} = {};
  for (const columnName in row) {
    const loiKey = SPECIAL_COLUMN_NAMES[columnName.toLowerCase()];
    const value = row[columnName];
    if (loiKey) {
      // Handle column differently if column name is recognized as having special significance.
      data[loiKey] = value;
    } else {
      properties[columnName] = value;
    }
  }
  const {lat, lng, ...loi} = data;
  if (isNaN(lat) || isNaN(lng)) return null;
  loi['predefined'] = true;
  loi['geometry'] = Datastore.toFirestoreMap({
    type: 'Point',
    coordinates: new GeoPoint(Number(lat), Number(lng)),
  });
  if (Object.keys(properties).length > 0) {
    loi['properties'] = properties;
  }
  return loi;
}

function csvRowToLocationOfInterestPb(
  row: any,
  jobId: string
): Pb.LocationOfInterest | null {
  const loi: any = {};
  const properties: {[name: string]: any} = {};
  for (const columnName in row) {
    const loiKey = SPECIAL_COLUMN_NAMES[columnName.toLowerCase()];
    const value = row[columnName];
    if (!value) continue;
    if (loiKey) {
      // Handle column differently if column name is recognized as having special significance.
      loi[loiKey] = value;
    } else {
      const numValue = Number.parseFloat(value);
      if (isNaN(numValue)) {
        properties[columnName] = value;
      } else {
        properties[columnName] = numValue;
      }
    }
  }
  const {customTag, lat, lng} = loi;
  if (isNaN(lat) || isNaN(lng)) return null;
  const point = new Pb.Point({
    coordinates: new Pb.Coordinates({longitude: Number(lng), latitude: Number(lat)}),
  });
  return new Pb.LocationOfInterest({
    jobId,
    customTag,
    samplingFrameType: Pb.LocationOfInterest.SamplingFrameType.PREDEFINED,
    geometry: {point},
    properties,
  });
}
