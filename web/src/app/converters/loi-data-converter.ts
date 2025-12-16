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
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {Map} from 'immutable';

import {LocationOfInterest} from 'app/models/loi.model';

import {geometryPbToModel} from './geometry-data-converter';

import Pb = GroundProtos.ground.v1beta1;

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

export function loiDocToModel(
  id: string,
  data: DocumentData
): LocationOfInterest | Error {
  const pb = toMessage(data, Pb.LocationOfInterest) as Pb.LocationOfInterest;
  if (!pb.jobId) return Error(`Missing job_id in loi ${id}`);
  if (!pb.geometry) return Error(`Missing geometry in loi ${id}`);
  try {
    const geometry = geometryPbToModel(pb.geometry);
    if (!geometry) return Error(`Invalid geometry in loi ${id}`);
    const properties = propertiesPbToModel(pb.properties || {});
    return new LocationOfInterest(
      id,
      pb.jobId,
      geometry,
      properties,
      pb.customTag,
      pb.source === Pb.LocationOfInterest.Source.IMPORTED
    );
  } catch (e) {
    return Error(`Error converting LOI with ID ${id}`, {
      cause: e,
    });
  }
}
