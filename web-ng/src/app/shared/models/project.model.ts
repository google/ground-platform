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

import { Layer } from './layer.model';
import { StringMap } from './string-map.model';
import { Map } from 'immutable';
import { Role } from './role.model';

export class Project {
  static readonly UNSAVED_NEW = new Project(
    /* id= */
    '',
    /* title= */
    StringMap({}),
    /* description= */
    StringMap({}),
    /* layers= */
    Map<string, Layer>(),
    /* acl= */
    Map<string, Role>()
  );

  constructor(
    readonly id: string,
    readonly title: StringMap,
    readonly description: StringMap,
    readonly layers: Map<string, Layer>,
    readonly acl: Map<string, Role>
  ) {}

  getLayer(layerId: string): Layer | undefined {
    return this.layers.get(layerId);
  }

  isUnsavedNew() {
    return (
      !this.id &&
      !this.title.size &&
      !this.description.size &&
      !this.layers.size &&
      !this.acl.size
    );
  }
}
