/**
 * Copyright 2021 The Ground Authors.
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
import * as HttpStatus from 'http-status-codes';
import {getDatastore} from './common/context';
import * as Busboy from 'busboy';
import * as JSONStream from 'jsonstream-ts';
import {canImport} from './common/auth';
import {DecodedIdToken} from 'firebase-admin/auth';

/**
 * Read the body of a multipart HTTP POSTed form containing a GeoJson 'file'
 * and required 'survey' id and 'job' id to the database.
 */
export async function importGeoJsonHandler(
  req: functions.https.Request,
  res: functions.Response<any>,
  user: DecodedIdToken
) {
  if (req.method !== 'POST') {
    res.status(HttpStatus.METHOD_NOT_ALLOWED).end();
    return;
  }

  const busboy = Busboy({headers: req.headers});

  // Dictionary used to accumulate task step values, keyed by step name.
  const params: {[name: string]: string} = {};

  // Accumulate Promises for insert operations, so we don't finalize the res
  // stream before operations are complete.
  const inserts: any[] = [];

  const db = getDatastore();

  // Handle non-file fields in the task. survey and job must appear
  // before the file for the file handler to work properly.
  busboy.on('field', (key, val) => {
    params[key] = val;
  });

  // This code will process each file uploaded.
  busboy.on('file', async (_field, file, _filename) => {
    const {survey: surveyId, job: jobId} = params;
    if (!surveyId || !jobId) {
      res
        .status(HttpStatus.BAD_REQUEST)
        .end(JSON.stringify({error: 'Invalid request'}));
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
      
    console.log(`Importing GeoJSON into survey '${surveyId}', job '${jobId}'`);
    // Pipe file through JSON parser lib, inserting each row in the db as it is
    // received.
    let geoJsonType: any = null;
    file.pipe(JSONStream.parse('type', undefined)).on('data', (data: any) => {
      geoJsonType = data;
    });

    file
      .pipe(JSONStream.parse(['features', true], undefined))
      .on('data', (geoJsonLoi: any) => {
        if (geoJsonType !== 'FeatureCollection') {
          // TODO: report error to user
          console.debug(`Invalid ${geoJsonType}`);
          res.status(HttpStatus.BAD_REQUEST).end();
          return;
        }
        if (geoJsonLoi.type !== 'Feature') {
          console.debug(`Skipping loi with type ${geoJsonLoi.type}`);
          return;
        }
        try {
          const loi = geoJsonToLoi(geoJsonLoi, jobId);
          if (loi) {
            inserts.push(db.insertLocationOfInterest(surveyId, loi));
          }
        } catch (err) {
          console.error(err);
          req.unpipe(busboy);
          res
            .status(HttpStatus.BAD_REQUEST)
            .end(JSON.stringify({error: (err as Error).message}));
          // TODO(#525): Abort stream on error. How?
        }
      });
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on('finish', async () => {
    await Promise.all(inserts);
    const count = inserts.length;
    console.log(`${count} LOIs imported`);
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
 * Convert the provided GeoJSON LocationOfInterest and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function geoJsonToLoi(geoJsonLoi: any, jobId: string) {
  // TODO: Add created/modified metadata.
  const { id, geometry, properties } = geoJsonLoi;
  return {
    jobId,
    customId: id,
    predefined: true,
    geometry,
    properties
  };
}
