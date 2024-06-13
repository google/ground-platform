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

import functions from 'firebase-functions';
import HttpStatus from 'http-status-codes';
import {db} from './common/context';
import Busboy from 'busboy';
import JSONStream from 'jsonstream-ts';
// import {canImport} from './common/auth';
import {DecodedIdToken} from 'firebase-admin/auth';
import {GroundProtos} from '@ground/proto';
import {Datastore} from './common/datastore';
import {DocumentData} from 'firebase-admin/firestore';
import {toDocumentData} from '@ground/lib';
import {Feature, Geometry, Position} from 'geojson';

import Pb = GroundProtos.google.ground.v1beta1;

/**
 * Read the body of a multipart HTTP POSTed form containing a GeoJson 'file'
 * and required 'survey' id and 'job' id to the database.
 */
export async function importGeoJsonHandler(
  req: functions.https.Request,
  res: functions.Response<any>,
  user: DecodedIdToken
) {
  try {
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
      // const survey = await db.fetchSurvey(surveyId);
      // if (!survey.exists) {
      //   res.status(HttpStatus.NOT_FOUND).send('Survey not found');
      //   return;
      // }
      // if (!canImport(user, survey)) {
      //   res.status(HttpStatus.FORBIDDEN).send('Permission denied');
      //   return;
      // }

      console.debug(
        `Importing GeoJSON into survey '${surveyId}', job '${jobId}'`
      );
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
            console.debug(`Skipping LOI with invalid type ${geoJsonLoi.type}`);
            return;
          }
          try {
            const loi = {
              ...toDocumentData(toLoiPb(geoJsonLoi as Feature, jobId) || {}),
              ...geoJsonToLoiLegacy(geoJsonLoi, jobId),
            };
            if (Object.keys(loi).length > 0) {
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
      console.debug(`${count} LOIs imported`);
      res.status(HttpStatus.OK).end(JSON.stringify({count}));
    });

    busboy.on('error', (err: any) => {
      console.error('Busboy error', err);
      req.unpipe(busboy);
      res.status(HttpStatus.INTERNAL_SERVER_ERROR).end(err.message);
    });

    // Start processing the body data.
    // Use this for Cloud Functions rather than `req.pipe(busboy)`:
    // https://github.com/mscdex/busboy/issues/229#issuecomment-648303108
    busboy.end(req.rawBody);
  } catch (e) {
    console.error('Unhandled error', e);
    res.status(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}

/**
 * Convert the provided GeoJSON LocationOfInterest and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function geoJsonToLoiLegacy(geoJsonLoi: Feature, jobId: string): DocumentData {
  // TODO: Add created/modified metadata.
  const {id, geometry, properties} = geoJsonLoi;
  return {
    jobId,
    customId: id,
    predefined: true,
    geometry: Datastore.toFirestoreMap(geometry),
    properties,
  };
}

/**
 * Convert the provided GeoJSON LocationOfInterest and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function toLoiPb(
  feature: Feature,
  jobId: string
): Pb.LocationOfInterest | null {
  // TODO: Add created/modified metadata.
  const {id, geometry, properties} = feature;
  const geometryPb = toGeometryPb(geometry);
  if (!geometryPb) return null;
  return new Pb.LocationOfInterest({
    jobId,
    customTag: id?.toString(),
    source: Pb.LocationOfInterest.Source.IMPORTED,
    geometry: geometryPb,
    properties,
  });
}

function toGeometryPb(geometry: Geometry): Pb.Geometry | null {
  switch (geometry.type) {
    case 'Point':
      const point = toPointPb(geometry.coordinates);
      return point ? new Pb.Geometry({point}) : null;
    case 'Polygon':
      const polygon = toPolygonPb(geometry.coordinates);
      return polygon ? new Pb.Geometry({polygon}) : null;
    case 'MultiPolygon':
      const multiPolygon = toMultiPolygon(geometry.coordinates);
      return multiPolygon ? new Pb.Geometry({multiPolygon}) : null;
  }
  // Unsupported GeoJSON type.
  return null;
}

function toPointPb(position: Position): Pb.Point | null {
  const coordinatesPb = toCoordinatesPb(position);
  return coordinatesPb ? new Pb.Point({coordinates: coordinatesPb}) : null;
}
function toCoordinatesPb(position: Position): Pb.Coordinates | null {
  const [longitude, latitude] = position;
  return longitude && latitude
    ? new Pb.Coordinates({
        longitude,
        latitude,
      })
    : null;
}

function toPolygonPb(positions: Position[][]): Pb.Polygon | null {
  const [shell, ...holes] = positions;
  // Ignore if shell is missing.
  const shellPb = shell ? toLinearRingPb(shell) : null;
  const holesPb =
    holes
      ?.map(h => toLinearRingPb(h))
      ?.filter(h => !!h)
      .map(h => h!!) || [];
  return shellPb ? new Pb.Polygon({shell: shellPb, holes: holesPb}) : null;
}

function toLinearRingPb(positions: Position[]): Pb.LinearRing | null {
  const coords = positions.map(p => toCoordinatesPb(p));
  // Don't convert rings with invalid coords.
  if (coords.includes(null)) return null;
  return new Pb.LinearRing({coordinates: coords.map(c => c!)});
}

function toMultiPolygon(positions: Position[][][]): Pb.MultiPolygon | null {
  // Skip invalid polygons.
  const polygons = positions
    .map(p => toPolygonPb(p))
    .filter(p => !!p)
    .map(p => p!);
  return polygons.length > 0 ? new Pb.MultiPolygon({polygons}) : null;
}
