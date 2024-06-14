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
import {getDatastore} from './common/context';
import Busboy from 'busboy';
import JSONStream from 'jsonstream-ts';
import {canImport} from './common/auth';
import {DecodedIdToken} from 'firebase-admin/auth';
import {GroundProtos} from '@ground/proto';
import {Datastore} from './common/datastore';
import {DocumentData} from 'firebase-admin/firestore';
import {toDocumentData, deleteEmpty} from '@ground/lib';
import {Feature, Geometry, Position} from 'geojson';

import Pb = GroundProtos.google.ground.v1beta1;
import {ErrorHandler} from './handlers';

/**
 * Read the body of a multipart HTTP POSTed form containing a GeoJson 'file'
 * and required 'survey' id and 'job' id to the database.
 */
export function importGeoJsonHandler(
  req: functions.https.Request,
  res: functions.Response<any>,
  user: DecodedIdToken,
  done: () => void,
  error: ErrorHandler
) {
  if (req.method !== 'POST') {
    return error(
      HttpStatus.METHOD_NOT_ALLOWED,
      `Expected method POST, got ${req.method}`
    );
  }

  const busboy = Busboy({headers: req.headers});

  // Dictionary used to accumulate task step values, keyed by step name.
  const params: {[name: string]: string} = {};

  // Accumulate Promises for insert operations, so we don't finalize the res
  // stream before operations are complete.
  const inserts: any[] = [];

  const db = getDatastore();

  // This code will process each file uploaded.
  busboy.on('file', async (_field, file, _filename) => {
    const {survey: surveyId, job: jobId} = params;
    if (!surveyId || !jobId) {
      return error(HttpStatus.BAD_REQUEST, `Missing survey and/or job ID`);
    }
    const survey = await db.fetchSurvey(surveyId);
    if (!survey.exists) {
      return error(HttpStatus.NOT_FOUND, `Survey ${surveyId} not found`);
    }
    if (!canImport(user, survey)) {
      return error(
        HttpStatus.FORBIDDEN,
        `Use does not have permission to import into survey ${surveyId}`
      );
    }

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
        try {
          if (geoJsonType !== 'FeatureCollection') {
            return error(
              HttpStatus.BAD_REQUEST,
              `Expected 'FeatureCollection', got '${geoJsonType}'`
            );
          }
          if (geoJsonLoi.type !== 'Feature') {
            console.debug(`Skipping LOI with invalid type ${geoJsonLoi.type}`);
            return;
          }
          const loi = {
            ...toDocumentData(toLoiPb(geoJsonLoi as Feature, jobId) || {}),
            ...geoJsonToLoiLegacy(geoJsonLoi, jobId),
          };
          if (Object.keys(loi).length > 0) {
            inserts.push(db.insertLocationOfInterest(surveyId, loi));
          }
        } catch (err) {
          req.unpipe(busboy);
          return error(HttpStatus.BAD_REQUEST, (err as Error).message);
        }
      });
  });

  // Handle non-file fields in the task. survey and job must appear
  // before the file for the file handler to work properly.
  busboy.on('field', (key, val) => {
    params[key] = val;
  });

  // Triggered once all uploaded files are processed by Busboy.
  busboy.on('finish', async () => {
    try {
      await inserts[0];
      const count = inserts.length;
      console.debug(`${count} LOIs imported`);
      res.send(JSON.stringify({count}));
      done();
    } catch (err: any) {
      console.debug(err);
      error(HttpStatus.BAD_REQUEST, err);
    }
  });

  busboy.on('error', (err: any) => {
    console.error('Busboy error', err);
    req.unpipe(busboy);
    error(HttpStatus.INTERNAL_SERVER_ERROR, err);
  });

  // Start processing the body data.
  // Use this for Cloud Functions rather than `req.pipe(busboy)`:
  // https://github.com/mscdex/busboy/issues/229#issuecomment-648303108
  busboy.end(req.rawBody);
}

/**
 * Convert the provided GeoJSON LocationOfInterest and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function geoJsonToLoiLegacy(geoJsonLoi: Feature, jobId: string): DocumentData {
  // TODO: Add created/modified metadata.
  const {id, geometry, properties} = geoJsonLoi;
  return deleteEmpty({
    jobId,
    customId: id,
    predefined: true,
    geometry: Datastore.toFirestoreMap(geometry),
    properties,
  });
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
      return toPointGeometry(geometry.coordinates);
    case 'Polygon':
      return toPolygonGeometry(geometry.coordinates);
    case 'MultiPolygon':
      return toMultiPolygonGeometry(geometry.coordinates);
  }
  // Unsupported GeoJSON type.
  return null;
}

function toPointGeometry(position: Position): Pb.Geometry | null {
  const coordinates = toCoordinatesPb(position);
  const point = coordinates ? new Pb.Point({coordinates}) : null;
  return point ? new Pb.Geometry({point}) : null;
}

function toCoordinatesPb(position: Position): Pb.Coordinates | null {
  const [longitude, latitude] = position;
  if (!longitude && !latitude) return null;
  return new Pb.Coordinates({longitude, latitude});
}

function toPolygonPb(positions: Position[][]): Pb.Polygon | null {
  const [shellCoords, ...holeCoords] = positions;
  // Ignore if shell is missing.
  if (!shellCoords) return null;
  const shell = toLinearRingPb(shellCoords);
  if (!shell) return null;
  const holes =
    holeCoords
      ?.map(h => toLinearRingPb(h))
      ?.filter(h => !!h)
      ?.map(h => h as Pb.LinearRing) || [];
  return new Pb.Polygon({shell, holes});
}

function toPolygonGeometry(positions: Position[][]): Pb.Geometry | null {
  const polygon = toPolygonPb(positions);
  if (!polygon) return null;
  return new Pb.Geometry({polygon});
}

function toLinearRingPb(positions: Position[]): Pb.LinearRing | null {
  const coords = positions.map(p => toCoordinatesPb(p));
  // Don't convert rings with invalid coords.
  if (coords.includes(null)) return null;
  return new Pb.LinearRing({coordinates: coords.map(c => c!)});
}

function toMultiPolygonGeometry(positions: Position[][][]): Pb.Geometry | null {
  // Skip invalid polygons.
  const polygons = positions
    .map(p => toPolygonPb(p))
    .filter(p => !!p)
    .map(p => p!);
  if (polygons.length) return null;
  const multiPolygon = new Pb.MultiPolygon({polygons});
  return new Pb.Geometry({multiPolygon});
}
