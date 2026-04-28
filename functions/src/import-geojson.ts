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

import { Request } from 'firebase-functions/v2/https';
import type { Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import { getDatastore } from './common/context';
import Busboy from 'busboy';
import JSONStream from 'jsonstream-ts';
import { canImport } from './common/auth';
import { DecodedIdToken } from 'firebase-admin/auth';
import { DocumentData } from 'firebase-admin/firestore';
import { GroundProtos } from '@ground/proto';
import { isGeometryValid, toDocumentData, toGeometryPb } from '@ground/lib';
import { Feature, GeoJsonProperties } from 'geojson';
import { HttpError } from './common/http-error';

import Pb = GroundProtos.ground.v1beta1;

class BadRequestError extends Error {
  statusCode = StatusCodes.BAD_REQUEST;
}

/**
 * Read the body of a multipart HTTP POSTed form containing a GeoJson 'file'
 * and required 'survey' id and 'job' id to the database.
 */
export function importGeoJsonHandler(
  req: Request,
  res: Response,
  user: DecodedIdToken
): Promise<void> {
  if (req.method !== 'POST') {
    throw new HttpError(
      StatusCodes.METHOD_NOT_ALLOWED,
      `Expected method POST, got ${req.method}`
    );
  }

  const busboy = Busboy({ headers: req.headers });

  // Dictionary used to accumulate task step values, keyed by step name.
  const params: { [name: string]: string } = {};

  // Accumulate LOI documents to be bulk-inserted once the file is fully parsed.
  const loiDocs: DocumentData[] = [];

  const db = getDatastore();

  const ownerId = user.uid;

  return new Promise<void>((resolve, reject) => {
    let settled = false;

    function fail(code: number, message: string) {
      if (settled) return;
      settled = true;
      reject(new HttpError(code, message));
    }

    function succeed() {
      if (settled) return;
      settled = true;
      resolve();
    }

    // This code will process each file uploaded.
    busboy.on('file', async (_fieldname, fileStream) => {
      try {
        const { survey: surveyId, job: jobId } = params;
        if (!surveyId || !jobId) {
          return fail(StatusCodes.BAD_REQUEST, 'Missing survey and/or job ID');
        }
        const survey = await db.fetchSurvey(surveyId);
        if (!survey.exists) {
          return fail(StatusCodes.NOT_FOUND, `Survey ${surveyId} not found`);
        }
        if (!canImport(user, survey)) {
          return fail(
            StatusCodes.FORBIDDEN,
            `User does not have permission to import into survey ${surveyId}`
          );
        }

        console.debug(
          `Importing GeoJSON into survey '${surveyId}', job '${jobId}'`
        );

        const parser = JSONStream.parse(['features', true], undefined);

        fileStream.pipe(
          parser
            .on('header', (data: any) => {
              try {
                onGeoJsonType(data.type);
                if (data.crs) onGeoJsonCrs(data.crs);
              } catch (error: any) {
                busboy.emit('error', error);
              }
            })
            .on('data', (data: any) => {
              if (!settled) onGeoJsonFeature(data, surveyId, jobId);
            })
        );
      } catch (err) {
        busboy.emit('error', err);
      }
    });

    // Handle non-file fields in the task. survey and job must appear
    // before the file for the file handler to work properly.
    busboy.on('field', (key, val) => {
      params[key] = val;
    });

    // Triggered once all uploaded files are processed by Busboy.
    busboy.on('finish', async () => {
      if (settled) return;
      try {
        await db.insertLocationsOfInterest(params.survey, loiDocs);
        const count = loiDocs.length;
        console.debug(`${count} LOIs imported`);
        res.send(JSON.stringify({ count }));
        succeed();
      } catch (err) {
        console.debug(err);
        fail(StatusCodes.BAD_REQUEST, (err as Error).message);
      }
    });

    busboy.on('error', (err: any) => {
      console.error('Busboy error', err);
      req.unpipe(busboy);
      fail(err.statusCode || StatusCodes.INTERNAL_SERVER_ERROR, err.message);
    });

    // Start processing the body data.
    // Use this for Cloud Functions rather than `req.pipe(busboy)`:
    // https://github.com/mscdex/busboy/issues/229#issuecomment-648303108
    busboy.end(req.rawBody);
  });

  /**
   * This function is called by Busboy during file parsing to ensure that the GeoJSON
   * data being processed is valid. It checks for the presence of the required 'type'
   * property and verifies that its value is 'FeatureCollection'.
   */
  function onGeoJsonType(geoJsonType: string | undefined) {
    if (!geoJsonType)
      throw new BadRequestError('Invalid GeoJSON: Missing "type" property');

    if (geoJsonType !== 'FeatureCollection') {
      throw new BadRequestError(
        `Unsupported GeoJSON Type: Expected 'FeatureCollection', got '${geoJsonType}'`
      );
    }
  }

  /**
   * This function is called by Busboy during file parsing to ensure that the GeoJSON
   * data uses the 'CRS84' coordinate reference system.
   */
  function onGeoJsonCrs(
    geoJsonCrs: { type: string; properties: { name?: string } } | undefined
  ) {
    let crs = 'CRS84';
    if (geoJsonCrs) {
      const { type, properties } = geoJsonCrs;
      switch (type) {
        case 'name':
          crs = properties?.name ?? 'CRS84';
          break;
      }
    }
    if (!crs.endsWith('CRS84'))
      throw new BadRequestError(
        `Unsupported GeoJSON CRS: Expected 'CRS84', got '${JSON.stringify(
          geoJsonCrs
        )}'`
      );
  }

  /**
   * This function is called by Busboy during file parsing to validate and process
   * GeoJSON Feature objects within the file. It checks the feature type, geometry
   * validity, and converts the feature to a document data format for insertion.
   */
  function onGeoJsonFeature(
    geoJsonFeature: any,
    surveyId: string,
    jobId: string
  ) {
    if (geoJsonFeature.type !== 'Feature') {
      console.debug(
        `Skipping Feature with invalid type ${geoJsonFeature.type}`
      );
      return;
    }
    if (!isGeometryValid(geoJsonFeature.geometry)) {
      console.debug(
        `Skipping Feature with invalid coordinates ${JSON.stringify(
          geoJsonFeature.geometry
        )}`
      );
      return;
    }
    try {
      loiDocs.push(
        toDocumentData(toLoiPb(geoJsonFeature as Feature, jobId, ownerId))
      );
    } catch (loiErr) {
      console.debug('Skipping LOI', loiErr);
    }
  }
}

/**
 * Convert the provided GeoJSON LocationOfInterest and jobId into a
 * LocationOfInterest for insertion into the data store.
 */
function toLoiPb(
  feature: Feature,
  jobId: string,
  ownerId: string
): Pb.LocationOfInterest {
  // TODO: Add created/modified metadata.
  const { id, geometry, properties } = feature;
  const geometryPb = toGeometryPb(geometry);
  return new Pb.LocationOfInterest({
    jobId,
    ownerId,
    customTag: id?.toString(),
    source: Pb.LocationOfInterest.Source.IMPORTED,
    geometry: geometryPb,
    properties: toLoiPbProperties(properties),
  });
}

export function toLoiPbProperties(properties: GeoJsonProperties): {
  [k: string]: Pb.LocationOfInterest.Property;
} {
  return Object.fromEntries(
    Object.entries(properties ?? {}).map(([k, v]) => [k, toLoiPbProperty(v)])
  );
}

function toLoiPbProperty(value: any): Pb.LocationOfInterest.Property {
  return new Pb.LocationOfInterest.Property(
    typeof value === 'number'
      ? { numericValue: value }
      : { stringValue: value?.toString() || '' }
  );
}
