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

import { LocationFeature } from './../app/shared/models/feature.model';
import { Project } from '../app/shared/models/project.model';
import { Map } from 'immutable';
import { Layer } from './../app/shared/models/layer.model';
import { StringMap } from './../app/shared/models/string-map.model';
import { User } from './../app/shared/models/user.model';
import firebase from 'firebase/app';
import { Form } from '../app/shared/models/form/form.model';
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

  public static newProject({
    id = 'project001',
    title = { en: 'title' } as Record<string, string>,
    description = { en: 'description' } as Record<string, string>,
    layers = {} as Record<string, Layer>,
    acl = {} as Record<string, Role>,
  }): Project {
    return new Project(id, Map(title), Map(description), Map(layers), Map(acl));
  }

  public static newLayer({
    id = 'layer001',
    index = 0,
    color = '#ffff00',
    name = { en: 'layer' } as Record<string, string>,
    forms = {} as Record<string, Form>,
    contributorsCanAdd = [] as string[],
  }): Layer {
    return new Layer(
      id,
      index,
      color,
      StringMap(name),
      Map(forms),
      contributorsCanAdd
    );
  }

  public static newLocationFeature(): LocationFeature {
    return new LocationFeature(
      'feature001',
      'layer001',
      new firebase.firestore.GeoPoint(0, 0),
      Map()
    );
  }
}
