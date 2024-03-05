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

import {List} from 'immutable';

import {Copiable} from './copiable';

export type LoiPropertyGenerator = {
  name: string;
  url: string;
  prefix: string;
};

export type Integrations = {
  loiPropertyGenerators: List<LoiPropertyGenerator>;
};

export class Config extends Copiable {
  constructor(readonly integrations: Integrations) {
    super();
  }

  getIntegrations(): Integrations {
    return this.integrations;
  }

  getLoiPropertyGenerators(): List<LoiPropertyGenerator> {
    return this.integrations.loiPropertyGenerators;
  }
}
