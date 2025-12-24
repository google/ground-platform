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

import { DocumentData } from '@angular/fire/firestore';
import { timestampToInt, toMessage } from '@ground/lib';
import { GroundProtos } from '@ground/proto';
import { List, Map } from 'immutable';

import { AuditInfo } from 'app/models/audit-info.model';
import { Geometry } from 'app/models/geometry/geometry';
import { Point } from 'app/models/geometry/point';
import { Polygon } from 'app/models/geometry/polygon';
import { Job } from 'app/models/job.model';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { Result } from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import { User } from 'app/models/user.model';

import {
  coordinatesPbToModel,
  geometryPbToModel,
} from './geometry-data-converter';

import Pb = GroundProtos.ground.v1beta1;

function authInfoPbToModel(pb: Pb.IAuditInfo): AuditInfo {
  return new AuditInfo(
    new User(pb.userId!, pb.emailAddress!, true, pb.displayName!, pb.photoUrl!),
    new Date(timestampToInt(pb.clientTimestamp)),
    new Date(timestampToInt(pb.serverTimestamp))
  );
}

function taskDataPbArrayToModel(pb: Pb.ITaskData[]): SubmissionData {
  const submissionData: { [k: string]: Result } = {};

  pb.forEach(taskData => {
    const result = taskDataPbToModel(taskData);
    if (result !== null) {
      submissionData[taskData.taskId!] = result;
    }
  });

  return Map(submissionData);
}

function taskDataPbToModel(taskData: Pb.ITaskData): Result | null {
  const value = taskDataPbToModelValue(taskData);
  if (taskData.skipped) {
    if (value) {
      console.warn('Ignoring unexpected value on skipped task', value);
    }
    return new Result(null, true);
  } else if (value === null) {
    console.warn(
      'Skipping unexpected missing value on non-skipped task',
      taskData
    );
    return null;
  } else if (value === undefined) {
    console.warn(
      'Skipping unexpected undefined value on non-skipped task',
      taskData
    );
    return null;
  } else {
    return new Result(value, false);
  }
}

function taskDataPbToModelValue(
  taskData: Pb.ITaskData
): string | number | Date | MultipleSelection | Geometry | null | undefined {
  const {
    textResponse,
    numberResponse,
    dateTimeResponse,
    multipleChoiceResponses,
    drawGeometryResult,
    captureLocationResult,
    takePhotoResult,
  } = taskData;

  if (textResponse) return textResponse.text;
  else if (numberResponse) return numberResponse.number;
  else if (dateTimeResponse)
    return new Date(timestampToInt(dateTimeResponse.dateTime));
  else if (multipleChoiceResponses) {
    const { selectedOptionIds, otherText } = multipleChoiceResponses;

    return new MultipleSelection(List(selectedOptionIds || []), otherText);
  } else if (drawGeometryResult)
    return geometryPbToModel(drawGeometryResult.geometry!) as Polygon;
  else if (captureLocationResult)
    return new Point(
      coordinatesPbToModel(captureLocationResult.coordinates!),
      captureLocationResult.accuracy || undefined,
      captureLocationResult.altitude || undefined
    );
  else if (takePhotoResult) return takePhotoResult.photoPath;
  else return null;
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
    taskDataPbArrayToModel(pb.taskData)
  );
}
