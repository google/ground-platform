/**
 * Copyright 2021 Google LLC
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

import { PointOfInterest } from './../app/shared/models/loi.model';
import { Survey } from '../app/shared/models/survey.model';
import { Map } from 'immutable';
import { Job } from './../app/shared/models/job.model';
import { StringMap } from './../app/shared/models/string-map.model';
import { User } from './../app/shared/models/user.model';
import firebase from 'firebase/app';
import { Task } from '../app/shared/models/task/task.model';
import { Role } from './../app/shared/models/role.model';

/**
 * Shorthand builders with reasonable defaults for use by tests. Tests should
 * override values that are relevant even if they're the same as the default
 * values. Defaults values should only be used for states which are not under
 * test.
 */
export class TestData {
  public static newUser(): User {
    return new User(
      'id',
      'email@test.com',
      true,
      'Some user',
      'https://url-here'
    );
  }

  public static newSurvey({
    id = 'survey001',
    title = { en: 'title' } as Record<string, string>,
    description = { en: 'description' } as Record<string, string>,
    jobs = {} as Record<string, Job>,
    acl = {} as Record<string, Role>,
  }): Survey {
    return new Survey(id, Map(title), Map(description), Map(jobs), Map(acl));
  }

  public static newJob({
    id = 'job001',
    index = 0,
    color = '#ffff00',
    name = { en: 'job' } as Record<string, string>,
    tasks = {} as Record<string, Task>,
    contributorsCanAdd = [] as string[],
  }): Job {
    return new Job(
      id,
      index,
      color,
      StringMap(name),
      Map(tasks),
      contributorsCanAdd
    );
  }

  public static newPointOfInterest(): PointOfInterest {
    return new PointOfInterest(
      'loi001',
      'job001',
      new firebase.firestore.GeoPoint(0, 0),
      Map()
    );
  }
}
