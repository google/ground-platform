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

import { GroundProtos } from '@ground/proto';

import Pb = GroundProtos.ground.v1beta1;

/**
 * Checks if a Location of Interest (LOI) is accessible to a given user.
 *
 * An LOI is accessible if:
 * - Its source is `IMPORTED`.
 * - Its `ownerId` passed to the function is `null`.
 * - Its `ownerId` matches the LOI's owner.
 *
 * @param loi The Location of Interest object to check.
 * @param ownerId The ID of the user requesting access. Pass `null` to bypass the ownership check.
 * @returns True if the LOI is accessible to the user, false otherwise.
 */
export function isAccessibleLoi(
  loi: Pb.ILocationOfInterest,
  ownerId: string | null
) {
  return (
    loi.source === Pb.LocationOfInterest.Source.IMPORTED ||
    ownerId === null ||
    loi.ownerId === ownerId
  );
}

export function stringFormat(s: string, ...args: any[]): string {
  return s.replace(/\{(\d+)\}/g, (_, index) => args[index] || `{${index}}`);
}
