/**
 * Copyright 2026 The Ground Authors.
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
 * Returns a copy of `auditInfo` with `serverTimestamp` set from `eventTime`
 * (a Firestore trigger's commit time), leaving every other field unchanged.
 * Clients can only guess the server time from their own clock when they
 * write an AuditInfo, so this corrects it to the time the server actually
 * committed the write.
 */
export function withServerTimestamp(
  auditInfo: Pb.IAuditInfo,
  eventTime: string
): Pb.AuditInfo {
  return new Pb.AuditInfo({
    userId: auditInfo.userId,
    displayName: auditInfo.displayName,
    photoUrl: auditInfo.photoUrl,
    emailAddress: auditInfo.emailAddress,
    clientTimestamp: auditInfo.clientTimestamp,
    serverTimestamp: toTimestampPb(Date.parse(eventTime)),
  });
}

export function toTimestampPb(
  millis: number
): GroundProtos.google.protobuf.Timestamp {
  return new GroundProtos.google.protobuf.Timestamp({
    seconds: Math.floor(millis / 1000),
  });
}
