/**
 * Copyright 2022 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {DocumentData} from '@angular/fire/firestore';
import {GeoPoint} from 'firebase/firestore';
import {Map} from 'immutable';

import {GEOMETRY_TYPES, toGeometry} from 'app/converters/geometry-converter';
import {Geometry} from 'app/models/geometry/geometry';
import {Point} from 'app/models/geometry/point';
import {
  AreaOfInterest,
  GenericLocationOfInterest,
  GeoJsonLocationOfInterest,
  LocationOfInterest,
} from 'app/models/loi.model';
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import Pb = GroundProtos.google.ground.v1beta1;
import { geometryPbToModel } from './geometry-data-converter';

/**
 * Helper to return either the keys of a dictionary, or if missing, returns an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export function loiDocToModel(
  id: string,
  data: DocumentData
): LocationOfInterest | Error {
  // Use old converter if document doesn't include `job_id` using the new
  // proto-based format.
  if (!data['2']) {
    return LegacyLoiDataConverter.toLocationOfInterest(id, data);
  }
  const pb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;
  if (!pb.jobId) return Error(`Missing job_id in loi ${id}`);
  if (!pb.geometry) return Error(`Missing geometry in loi ${id}`);
  const geometry = geometryPbToModel(pb.geometry);
  if (!geometry) return new Error(`Invalid geometry in loi ${id}`);
  const properties = propertiesPbToModel(pb.properties || {});
  return new GenericLocationOfInterest(
    id,
    pb.jobId,
    geometry,
    properties,
    pb.customTag,
    pb.source === Pb.LocationOfInterest.Source.IMPORTED
  );
}

function propertiesPbToModel(pb: {[k: string]: Pb.LocationOfInterest.IProperty}): Map<string, string | number> {
  const properties: {[k: string]: string | number} = {};
  for (const k in pb.keys) {
    const v = pb[k].stringValue || pb[k].numericValue;
    if (v !== null && v !== undefined) {
      properties[k] = v;
    }
  }
  return Map(properties);
}
export class LegacyLoiDataConverter {
  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable LocationOfInterest instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   */
  static toLocationOfInterest(
    id: string,
    data: DocumentData
  ): LocationOfInterest | Error {
    try {
      if (!data.jobId) {
        return new Error('Error converting to LOI: missing job id');
      }
      const properties = Map<string, string | number>(
        keys(data.properties).map((property: string) => [
          property,
          data.properties[property],
        ])
      );
      const result = toGeometry(data.geometry);
      if (result instanceof Error) {
        return result;
      }

      return new GenericLocationOfInterest(
        id,
        data.jobId,
        result as Geometry,
        properties,
        data.customId,
        data.predefined
      );
    } catch (err) {
      return new Error(
        `Error converting to LOI: invalid LOI in remote data store; data: ${data}, error message: ${err}`
      );
    }
  }

  // TODO(Nick): Add polygon / multipolygon conversion, remove AreaOfInterest
  // and GeoJsonLocationOfInterest. This may not be urgent, as only point
  // conversion seems to be used on the frontend.
  public static loiToJS(loi: LocationOfInterest): {} | Error {
    // TODO: Set audit info (created / last modified user and timestamp).
    if (loi.geometry instanceof Point) {
      // TODO: Add geometryToJS converters in geometry-converter.ts call it from here. Then GEOMETRY_TYPES can be local.
      const {jobId, geometry} = loi;
      return {
        jobId,
        geometry: {
          coordinates: new GeoPoint(geometry.coord.x, geometry.coord.y),
          type: GEOMETRY_TYPES.get(geometry.geometryType),
        },
      };
    } else if (loi instanceof GeoJsonLocationOfInterest) {
      const {jobId, geoJson} = loi;
      return {
        jobId,
        geoJson,
      };
    } else if (loi instanceof AreaOfInterest) {
      const {jobId, polygonVertices} = loi;
      return {
        jobId,
        polygonVertices,
      };
    } else {
      return new Error(
        `Cannot convert unexpected loi class ${loi.constructor.name} to json.`
      );
    }
  }
}
