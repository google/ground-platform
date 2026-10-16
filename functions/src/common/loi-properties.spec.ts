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
import { propertiesPbToObject } from './loi-properties';

import Pb = GroundProtos.ground.v1beta1;

describe('propertiesPbToObject()', () => {
  it('keeps empty string and zero values', () => {
    expect(
      propertiesPbToObject({
        empty: new Pb.LocationOfInterest.Property({ stringValue: '' }),
        zero: new Pb.LocationOfInterest.Property({ numericValue: 0 }),
        text: new Pb.LocationOfInterest.Property({ stringValue: 'a' }),
        num: new Pb.LocationOfInterest.Property({ numericValue: 5 }),
      })
    ).toEqual({ empty: '', zero: 0, text: 'a', num: 5 });
  });

  it('skips properties with no value', () => {
    expect(
      propertiesPbToObject({ unset: new Pb.LocationOfInterest.Property() })
    ).toEqual({});
  });
});
