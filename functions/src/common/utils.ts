/**
 * Copyright 2024 The Ground Authors.
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

import {GroundProtos} from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Checks if a Location of Interest (LOI) is accessible to a given user.
 */
export function isAccessibleLoi(
  loi: Pb.ILocationOfInterest,
  ownerId: string | null
) {
  if (loi.source === Pb.LocationOfInterest.Source.IMPORTED) return true;
  if (!ownerId) return true;
  return loi.ownerId === ownerId;
}

export function stringFormat(s: string, ...args: any[]): string {
  return s.replace(/\{(\d+)\}/g, (_, index) => args[index] || `{${index}}`);
}
