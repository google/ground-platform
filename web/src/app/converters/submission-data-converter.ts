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
import {DocumentData, Timestamp} from '@angular/fire/firestore';
import {toMessage} from '@ground/lib';
import {GroundProtos} from '@ground/proto';
import {List, Map} from 'immutable';

import {toGeometry} from 'app/converters/geometry-converter';
import {AuditInfo} from 'app/models/audit-info.model';
import {MultiPolygon} from 'app/models/geometry/multi-polygon';
import {Point} from 'app/models/geometry/point';
import {Polygon} from 'app/models/geometry/polygon';
import {Job} from 'app/models/job.model';
import {Result} from 'app/models/submission/result.model';
import {
  Submission,
  SubmissionData,
} from 'app/models/submission/submission.model';
import {Option} from 'app/models/task/option.model';
import {Task} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';
import {DataStoreService} from 'app/services/data-store/data-store.service';

import {
  coordinatesPbToModel,
  geometryPbToModel,
} from './geometry-data-converter';

import Pb = GroundProtos.google.ground.v1beta1;

/**
 * Helper to return either the keys of a dictionary, or if missing, returns an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export function submissionDocToModel(
  job: Job,
  id: string,
  data: DocumentData
): Submission | Error {
  // Use old converter if document doesn't include `job_id` using the new
  // proto-based format.
  if (!data['4']) {
    return LegacySubmissionDataConverter.toSubmission(job, id, data);
  }
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

function authInfoPbToModel(pb: Pb.IAuditInfo): AuditInfo {
  return new AuditInfo(
    new User(pb.userId!, pb.displayName!, true),
    new Date(pb.clientTimestamp?.nanos || 0),
    new Date(pb.serverTimestamp?.nanos || 0)
  );
}

function taskDataPbToModel(pb: Pb.ITaskData[], job: Job): SubmissionData {
  const submissionData: {[k: string]: Result} = {};

  pb.forEach(taskData => {
    const task = job.tasks?.get(taskData.taskId!);

    if (!task) throw new Error(`Missing task`);

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
      value = new Date(dateTimeResponse.dateTime!.nanos!);
    else if (multipleChoiceResponses)
      value = List(
        task.multipleChoice?.options.filter(({id: optionId}) =>
          multipleChoiceResponses!.selectedOptionIds?.includes(optionId)
        )
      );
    else if (drawGeometryResult)
      value = geometryPbToModel(drawGeometryResult.geometry!) as Polygon;
    else if (captureLocationResult)
      value = new Point(
        coordinatesPbToModel(captureLocationResult.coordinates!)
      );
    else if (takePhotoResult) value = takePhotoResult.photoPath;
    else throw new Error('Error converting to Submission: invalid task data');

    submissionData[taskData.id!] = new Result(value!);
  });

  return Map(submissionData);
}

export class LegacySubmissionDataConverter {
  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Submission instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   loiId: 'loi123'
   *   taskId: 'task001',
   *   data: {
   *     'task001': 'Result text',    // For 'text_field' tasks.
   *     'task002': ['A', 'B'],       // For 'multiple_choice' tasks.
   *      // ...
   *   }
   *   created: <AUDIT_INFO>,
   *   lastModified: <AUDIT_INFO>
   * }
   * </code></pre>
   */
  static toSubmission(
    job: Job,
    id: string,
    data: DocumentData
  ): Submission | Error {
    if (!job.tasks) {
      return Error(
        'Error converting to submission: job must contain at least once task'
      );
    }
    if (!data) {
      return Error(
        `Error converting to submission: submission ${id} does not have document data.`
      );
    }
    return new Submission(
      id,
      data.loiId,
      job,
      LegacySubmissionDataConverter.toAuditInfo(data.created),
      LegacySubmissionDataConverter.toAuditInfo(data.lastModified),
      LegacySubmissionDataConverter.toResults(job, data)
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable AuditInfo instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   user: {
   *     id: ...,
   *     displayName: ...,
   *     email: ...
   *   },
   *   clientTimestamp: ...,
   *   serverTimestamp: ...
   * }
   * </code></pre>
   */
  private static toAuditInfo(data: DocumentData): AuditInfo {
    return new AuditInfo(
      data.user,
      data.clientTimestamp?.toDate(),
      data.serverTimestamp?.toDate()
    );
  }

  /**
   * Extracts and converts from the raw Firebase object to a map of {@link Result}s keyed by task id.
   * In case of error when converting from raw data to {@link Result}, logs the error and then ignores
   * that one {@link Result}.
   *
   * @param job the job related to this submission data, {@link job.tasks} must not be null or undefined.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toResults(job: Job, data: DocumentData): Map<string, Result> {
    // TODO(#1288): Clean up remaining references to old responses field
    // Support submissions that have results or responses fields instead of data
    // before model change.
    const submissionData = data.data ?? data.results ?? data.responses;
    return Map<string, Result>(
      keys(submissionData)
        .map((taskId: string) => {
          return [
            taskId as string,
            LegacySubmissionDataConverter.toResult(
              submissionData[taskId],
              job.tasks!.get(taskId)
            ),
          ];
        })
        .filter(([_, resultOrError]) =>
          DataStoreService.filterAndLogError<Result>(
            resultOrError as Result | Error
          )
        )
        .map(([k, v]) => [k, v] as [string, Result])
    );
  }

  private static toResult(
    resultValue: number | string | List<string>,
    task?: Task
  ): Result | Error {
    try {
      if (typeof resultValue === 'string') {
        return new Result(resultValue as string);
      } else if (typeof resultValue === 'number') {
        return new Result(resultValue as number);
      } else if (resultValue instanceof Array) {
        return new Result(
          List(
            resultValue
              .filter(optionId => !optionId.startsWith('['))
              .map(
                optionId =>
                  task?.getMultipleChoiceOption(optionId) ||
                  new Option(optionId, optionId, optionId, -1)
              )
          )
        );
      } else if (resultValue instanceof Timestamp) {
        return new Result(resultValue.toDate());
      } else {
        const geometry = toGeometry(resultValue);
        if (
          geometry instanceof Point ||
          geometry instanceof Polygon ||
          geometry instanceof MultiPolygon
        ) {
          return new Result(geometry);
        }
      }
      return Error(
        `Error converting to Result: unknown value type ${typeof resultValue}`
      );
    } catch (err: any) {
      return err instanceof Error ? err : new Error(err);
    }
  }
}
