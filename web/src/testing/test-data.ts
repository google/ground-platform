/**
 * Copyright 2021 The Ground Authors.
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

import {GeoPoint} from 'firebase/firestore';
import {Map} from 'immutable';

import {DataCollectionStrategy, Job} from 'app/models/job.model';
import {PointOfInterest} from 'app/models/loi.model';
import {Role} from 'app/models/role.model';
import {Survey} from 'app/models/survey.model';
import {Task} from 'app/models/task/task.model';
import {User} from 'app/models/user.model';

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
    title = 'title',
    description = 'description',
    jobs = {} as Record<string, Job>,
    acl = {} as Record<string, Role>,
  }): Survey {
    return new Survey(id, title, description, Map(jobs), Map(acl));
  }

  public static newJob({
    id = 'job001',
    index = 0,
    color = '#ffff00',
    name = 'job',
    tasks = {} as Record<string, Task>,
    strategy = DataCollectionStrategy.PREDEFINED,
  }): Job {
    return new Job(id, index, color, name, Map(tasks), strategy);
  }

  public static newPointOfInterest(): PointOfInterest {
    return new PointOfInterest('loi001', 'job001', new GeoPoint(0, 0), Map());
  }
}
