/**
 * Copyright 2019 Google LLC
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
import firebase from 'firebase/app';
import { DocumentData } from '@angular/fire/firestore';
import { Survey } from '../models/survey.model';
import { Job } from '../models/job.model';
import { Task } from '../models/task/task.model';
import { Step, StepType } from '../models/task/step.model';
import {
  MultipleChoice,
  Cardinality,
} from '../models/task/multiple-choice.model';
import {
  LocationOfInterest,
  PointOfInterest,
  GeoJsonLocationOfInterest,
  AreaOfInterest,
} from '../models/loi.model';
import { Observation } from '../models/observation/observation.model';
import { Option } from '../models/task/option.model';
import { List, Map } from 'immutable';
import { AuditInfo } from '../models/audit-info.model';
import { Response } from '../../shared/models/observation/response.model';
import { Role } from '../models/role.model';
import { User } from '../models/user.model';
import { OfflineBaseMapSource } from '../models/offline-base-map-source';

const STEP_TYPE_ENUMS_BY_STRING = Map([
  [StepType.TEXT, 'text_field'],
  [StepType.MULTIPLE_CHOICE, 'multiple_choice'],
  [StepType.PHOTO, 'photo'],
  [StepType.NUMBER, 'number'],
  [StepType.DATE, 'date'],
  [StepType.TIME, 'time'],
]);

const STEP_TYPE_STRINGS_BY_ENUM = Map(
  Array.from(
    STEP_TYPE_ENUMS_BY_STRING.toArray(),
    el => el.reverse() as [string, StepType]
  )
);

/**
 * Helper to return either the keys of a dictionary, or if missing, returns an
 * empty array.
 */
function keys(dict?: {}): string[] {
  return Object.keys(dict || {});
}

export class FirebaseDataConverter {
  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Survey instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   */
  static toSurvey(id: string, data: DocumentData): Survey {
    return new Survey(
      id,
      data.title,
      data.description,
      Map<string, Job>(
        keys(data.jobs).map((id: string) => [
          id as string,
          FirebaseDataConverter.toJob(id, data.jobs[id]),
        ])
      ),
      Map<string, Role>(
        keys(data.acl).map((id: string) => [
          id as string,
          FirebaseDataConverter.toRole(data.acl[id]),
        ])
      )
    );
  }

  private static toRole(roleString: string) {
    switch (roleString) {
      case 'owner':
        return Role.OWNER;
      case 'manager':
        return Role.MANAGER;
      case 'contributor':
        return Role.CONTRIBUTOR;
      case 'viewer':
        return Role.VIEWER;
      default:
        console.log('User has unsupported role: ', roleString);
        return Role.VIEWER;
    }
  }

  static newSurveyJS(
    ownerEmail: string,
    title: string,
    offlineBaseMapSources?: OfflineBaseMapSource[]
  ): {} {
    return {
      // TODO(i18n): Make title language dynamic.
      title: { en: title },
      acl: { [ownerEmail]: FirebaseDataConverter.toRoleId(Role.OWNER) },
      ...(offlineBaseMapSources?.length ? { offlineBaseMapSources } : {}),
    };
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Job instance.
   *
   * @param id the uuid of the job instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toJob(id: string, data: DocumentData): Job {
    return new Job(
      id,
      // Fall back to constant so old dev databases do not break.
      data.index || -1,
      data.defaultStyle?.color || data.color,
      data.name,
      Map<string, Task>(
        keys(data.tasks).map((id: string) => [
          id as string,
          FirebaseDataConverter.toTask(id, data.tasks[id]),
        ])
      ),
      data.contributorsCanAdd || []
    );
  }

  static jobToJS(job: Job): {} {
    const { name, tasks, color, contributorsCanAdd, ...jobDoc } = job;
    return {
      contributorsCanAdd,
      name,
      ...(tasks
        ? {
            tasks: tasks
              ?.valueSeq()
              .reduce(
                (map, task) => ({ ...map, [task.id]: this.taskToJS(task) }),
                {}
              ),
          }
        : {}),
      defaultStyle: { color },
      ...jobDoc,
    };
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Task instance.
   *
   * @param id the uuid of the task instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toTask(id: string, data: DocumentData): Task {
    return new Task(
      id,
      Map<string, Step>(
        keys(data.elements)
          .map(id => FirebaseDataConverter.toStep(id, data.elements[id]))
          .filter(step => step !== null)
          .map(step => [step!.id, step!])
      )
    );
  }

  private static taskToJS(task: Task): {} {
    const { steps, ...taskDoc } = task;
    return {
      elements:
        steps?.reduce(
          (map, step: Step) => ({
            ...map,
            [step.id]: this.stepToJS(step),
          }),
          {}
        ) || {},
      ...taskDoc,
    };
  }

  public static loiToJS(loi: LocationOfInterest): {} {
    // TODO: Set audit info (created / last modified user and timestamp).
    if (loi instanceof PointOfInterest) {
      const { jobId, location } = loi;
      return {
        jobId,
        location,
      };
    } else if (loi instanceof GeoJsonLocationOfInterest) {
      const { jobId, geoJson } = loi;
      return {
        jobId,
        geoJson,
      };
    } else if (loi instanceof AreaOfInterest) {
      const { jobId, polygonVertices } = loi;
      return {
        jobId,
        polygonVertices,
      };
    } else {
      throw new Error(
        `Cannot convert unexpected loi class ${loi.constructor.name} to json.`
      );
    }
  }
  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Step instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   index: 0,
   *   label: 'Question 1',
   *   required: true,
   *   type: 'text_field'
   * }
   * </code></pre>
   * or
   * <pre><code>
   * {
   *   index: 1,
   *   label: 'Question 2',
   *   required: false,
   *   type: 'multiple_choice',
   *   cardinality: 'select_one',
   *   options: {
   *     option001: {
   *       index: 0,
   *       code: 'A',
   *       label: 'Option A'
   *     },
   *     // ...
   *   }
   * </code></pre>
   */
  private static toStep(id: string, data: DocumentData): Step | null {
    try {
      return new Step(
        id,
        FirebaseDataConverter.stringToStepType(data.type),
        data.label,
        data.required,
        // Fall back to constant so old dev databases do not break.
        data.index || -1,
        data.options &&
          new MultipleChoice(
            FirebaseDataConverter.stringToCardinality(data.cardinality),
            List(
              keys(data.options).map((id: string) =>
                FirebaseDataConverter.toOption(id, data.options[id])
              )
            )
          )
      );
    } catch (e) {
      console.error(e);
      return null;
    }
  }

  private static stepToJS(step: Step): {} {
    const { type, label, multipleChoice, ...stepDoc } = step;
    if (multipleChoice === undefined) {
      return {
        type: FirebaseDataConverter.stepTypeToString(type),
        label,
        ...stepDoc,
      };
    } else {
      return {
        type: FirebaseDataConverter.stepTypeToString(type),
        label,
        cardinality: FirebaseDataConverter.cardinalityToString(
          multipleChoice.cardinality
        ),
        // convert list of options to map of optionId: option.
        options:
          multipleChoice?.options?.reduce(
            (map, option: Option) => ({
              ...map,
              [option.id]: FirebaseDataConverter.optionToJS(option),
            }),
            {}
          ) || {},
        ...stepDoc,
      };
    }
  }

  private static stringToCardinality(cardinality: string): Cardinality {
    switch (cardinality) {
      case 'select_one':
        return Cardinality.SELECT_ONE;
      case 'select_multiple':
        return Cardinality.SELECT_MULTIPLE;
      default:
        throw Error(`Unsupported cardinality ${cardinality}`);
    }
  }

  private static cardinalityToString(cardinality: Cardinality): string {
    switch (cardinality) {
      case Cardinality.SELECT_ONE:
        return 'select_one';
      case Cardinality.SELECT_MULTIPLE:
        return 'select_multiple';
      default:
        throw Error(`Unsupported cardinality ${cardinality}`);
    }
  }

  private static stringToStepType(stepType: string): StepType {
    const type = STEP_TYPE_STRINGS_BY_ENUM.get(stepType);
    if (!type) {
      throw new Error(`Ignoring unsupported step of type: ${stepType}`);
    }
    return type;
  }

  private static stepTypeToString(stepType: StepType): string {
    const str = STEP_TYPE_ENUMS_BY_STRING.get(stepType);
    if (!str) {
      throw Error(`Unsupported step type ${stepType}`);
    }
    return str;
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Step instance.
   *
   * @param id the uuid of the survey instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *    index: 0,
   *    code: 'A',
   *    label: 'Option A'
   *  }
   * </code></pre>
   */
  private static toOption(id: string, data: DocumentData): Option {
    return new Option(
      id,
      data.code,
      data.label,
      // Fall back to constant so old dev databases do not break.
      data.index || -1
    );
  }

  private static optionToJS(option: Option): {} {
    const { label, ...optionDoc } = option;
    return {
      label,
      ...optionDoc,
    };
  }

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
  ): LocationOfInterest | undefined {
    try {
      if (!data.jobId) {
        throw new Error('Missing job id');
      }
      const loiProperties = Map<string, string | number>(
        keys(data.properties).map((property: string) => [
          property,
          data.properties[property],
        ])
      );

      if (this.isPointOfInterest(data)) {
        return new PointOfInterest(
          id,
          data.jobId,
          data.location,
          loiProperties
        );
      }
      if (this.isGeoJsonLocationOfInterest(data)) {
        const geoJson = JSON.parse(data.geoJson);
        return new GeoJsonLocationOfInterest(
          id,
          data.jobId,
          geoJson,
          loiProperties
        );
      }
      if (this.isAreaOfInterest(data)) {
        return new AreaOfInterest(
          id,
          data.jobId,
          data.geometry.coordinates,
          loiProperties
        );
      }
      throw new Error('Missing location and geoJson');
    } catch (err) {
      console.error(`Invalid loi ${id}, ${err}`);
    }
    console.warn(`Invalid loi ${id} in remote data store ignored`);
    return;
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Observation instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   loiId: 'loi123'
   *   taskId: 'task001',
   *   responses: {
   *     'element001': 'Response text',  // For 'text_field  elements.
   *     'element002': ['A', 'B'],       // For 'multiple_choice' elements.
   *      // ...
   *   }
   *   created: <AUDIT_INFO>,
   *   lastModified: <AUDIT_INFO>
   * }
   * </code></pre>
   */
  static toObservation(
    task: Task,
    id: string,
    data: DocumentData
  ): Observation {
    if (data === undefined) {
      throw Error(`Observation ${id} does not have document data.`);
    }
    return new Observation(
      id,
      data.loiId,
      data.jobId,
      task,
      FirebaseDataConverter.toAuditInfo(data.created),
      FirebaseDataConverter.toAuditInfo(data.lastModified),
      Map<string, Response>(
        keys(data.responses).map((stepId: string) => [
          stepId as string,
          FirebaseDataConverter.toResponse(
            task,
            stepId,
            data.responses[stepId]
          ),
        ])
      )
    );
  }

  static observationToJS(observation: Observation): {} {
    return {
      loiId: observation.loiId,
      jobId: observation.jobId,
      taskId: observation.task?.id,
      created: FirebaseDataConverter.auditInfoToJs(observation.created),
      lastModified: FirebaseDataConverter.auditInfoToJs(
        observation.lastModified
      ),
      responses: FirebaseDataConverter.responsesToJS(observation.responses),
    };
  }

  static toUser(data: DocumentData): User | undefined {
    if (!data) {
      return;
    }
    return new User(
      data.id,
      data.email,
      data.isAuthenticated || true,
      data.displayName,
      data.photoURL
    );
  }

  private static responsesToJS(responses: Map<string, Response>): {} {
    return responses.entrySeq().reduce(
      (obj: {}, [stepId, response]) => ({
        ...obj,
        [stepId]: FirebaseDataConverter.responseToJS(response),
      }),
      {}
    );
  }

  private static toResponse(
    task: Task,
    stepID: string,
    responseValue: number | string | List<string>
  ): Response {
    if (typeof responseValue === 'string') {
      return new Response(responseValue as string);
    }
    if (typeof responseValue === 'number') {
      return new Response(responseValue as number);
    }
    if (responseValue instanceof Array) {
      return new Response(
        List(
          responseValue.map(optionId =>
            task.getMultipleChoiceStepOption(stepID, optionId)
          )
        )
      );
    }
    if (responseValue instanceof firebase.firestore.Timestamp) {
      return new Response(responseValue.toDate());
    }
    throw Error(`Unknown value type ${typeof responseValue}`);
  }

  private static responseToJS(response: Response): {} {
    if (typeof response.value === 'string') {
      return response.value;
    }
    if (typeof response.value === 'number') {
      return response.value;
    }
    if (response.value instanceof List) {
      return (response.value as List<Option>)
        .map(option => option.id)
        .toArray();
    }
    if (response.value instanceof Date) {
      return firebase.firestore.Timestamp.fromDate(response.value);
    }
    throw Error(`Unknown value type of ${response.value}`);
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

  private static auditInfoToJs(auditInfo: AuditInfo): {} {
    return {
      user: FirebaseDataConverter.userToJs(auditInfo.user),
      clientTimestamp: auditInfo.clientTime,
      serverTimestamp: auditInfo.serverTime,
    };
  }

  private static userToJs(user: User): {} {
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
    };
  }

  static aclToJs(acl: Map<string, Role>): {} {
    return acl.map(FirebaseDataConverter.toRoleId).toJS();
  }

  /**
   * Returns the string used to represent the specified role in the database.
   * @param role the Role to be converted.
   */
  static toRoleId(role: Role): string {
    return Role[role].toLowerCase();
  }

  private static isPointOfInterest(data: DocumentData): boolean {
    return data?.location?.latitude && data?.location?.longitude;
  }

  private static isGeoJsonLocationOfInterest(data: DocumentData): boolean {
    return data?.geoJson;
  }
  private static isAreaOfInterest(data: DocumentData): boolean {
    return data?.geometry;
  }
}
