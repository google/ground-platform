/**
 * Copyright 2024 The Ground Authors.
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
import {List, Map} from 'immutable';
import Long from 'long';

import {AuditInfo} from 'app/models/audit-info.model';
import {Point} from 'app/models/geometry/point';
import {Polygon} from 'app/models/geometry/polygon';
import {Job} from 'app/models/job.model';
import {MultipleSelection} from 'app/models/submission/multiple-selection';
import {Result} from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import {User} from 'app/models/user.model';

import {
  coordinatesPbToModel,
  geometryPbToModel,
} from './geometry-data-converter';

import Pb = GroundProtos.ground.v1beta1;

function timestampToInt(
  timestamp: GroundProtos.google.protobuf.ITimestamp | null | undefined
): number {
  if (!timestamp) return 0;

  return (
    (Long.isLong(timestamp.seconds)
      ? timestamp.seconds.toInt()
      : timestamp.seconds || 0) * 1000
  );
}

function authInfoPbToModel(pb: Pb.IAuditInfo): AuditInfo {
  return new AuditInfo(
    new User(pb.userId!, pb.emailAddress!, true, pb.displayName!, pb.photoUrl!),
    new Date(timestampToInt(pb.clientTimestamp)),
    new Date(timestampToInt(pb.serverTimestamp))
  );
}

function taskDataPbToModel(pb: Pb.ITaskData[], job: Job): SubmissionData {
  const submissionData: {[k: string]: Result} = {};

  pb.forEach(taskData => {
    const task = job.tasks?.get(taskData.taskId!);

    if (!task) return;

    let value = null;

    const {
      textResponse,
      numberResponse,
      dateTimeResponse,
      multipleChoiceResponses,
      drawGeometryResult,
      captureLocationResult,
      takePhotoResult,
    } = taskData;

    if (textResponse) value = textResponse.text;
    else if (numberResponse) value = numberResponse.number;
    else if (dateTimeResponse)
      value = new Date(timestampToInt(dateTimeResponse.dateTime));
    else if (multipleChoiceResponses) {
      value = new MultipleSelection(
        task.multipleChoice?.options.filter(({id: optionId}) =>
          multipleChoiceResponses!.selectedOptionIds?.includes(optionId)
        ) || List([]),
        multipleChoiceResponses.otherText
      );
    } else if (drawGeometryResult)
      value = geometryPbToModel(drawGeometryResult.geometry!) as Polygon;
    else if (captureLocationResult)
      value = new Point(
        coordinatesPbToModel(captureLocationResult.coordinates!),
        captureLocationResult.accuracy === null ? undefined : captureLocationResult.accuracy,
        captureLocationResult.altitude === null ? undefined: captureLocationResult.altitude,
      );
    else if (takePhotoResult) value = takePhotoResult.photoPath;
    else throw new Error('Error converting to Submission: invalid task data');

    submissionData[task.id] = new Result(value!);
  });

  return Map(submissionData);
}

export function submissionDocToModel(
  job: Job,
  id: string,
  data: DocumentData
): Submission | Error {
  const pb = toMessage(data, Pb.Submission) as Pb.Submission;
  if (!pb.jobId) return Error(`Missing job_id in submission ${id}`);
  if (!pb.loiId) return Error(`Missing loi_id in loi ${id}`);
  return new Submission(
    id,
    pb.loiId,
    job,
    authInfoPbToModel(pb.created!),
    authInfoPbToModel(pb.lastModified!),
    taskDataPbToModel(pb.taskData, job)
  );
}
