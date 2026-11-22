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

import { List } from 'immutable';

import { Coordinate } from 'app/models/geometry/coordinate';
import { Point } from 'app/models/geometry/point';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { ResultValue } from 'app/models/submission/result.model';
import { TaskType } from 'app/models/task/task.model';

import { isResultCompatible, resultToString } from './result-display';

describe('result-display', () => {
  const point = new Point(new Coordinate(1, 2));
  const values: Record<string, ResultValue> = {
    text: 'text',
    number: 5,
    date: new Date('2023-01-01T12:00:00'),
    selection: new MultipleSelection(List(['a', 'b']), 'x'),
    geometry: point,
    nothing: null,
  };
  const accepted = new Map<TaskType, string[]>([
    [TaskType.TEXT, ['text', 'number']],
    [TaskType.NUMBER, ['text', 'number']],
    [TaskType.PHOTO, ['text']],
    [TaskType.DATE, ['date']],
    [TaskType.TIME, ['date']],
    [TaskType.DATE_TIME, ['date']],
    [TaskType.MULTIPLE_CHOICE, ['selection']],
    [TaskType.DROP_PIN, ['geometry']],
    [TaskType.DRAW_AREA, ['geometry']],
    [TaskType.CAPTURE_LOCATION, ['geometry']],
  ]);

  for (const [taskType, names] of accepted) {
    it(`isResultCompatible accepts only ${names} for ${TaskType[taskType]}`, () => {
      for (const [name, value] of Object.entries(values)) {
        expect(isResultCompatible(taskType, value))
          .withContext(`${TaskType[taskType]} with ${name}`)
          .toBe(names.includes(name));
      }
    });
  }

  it('resultToString shows every kind of result as text', () => {
    expect(resultToString('text')).toBe('text');
    expect(resultToString(5)).toBe('5');
    expect(resultToString(values['date'])).toBe(
      (values['date'] as Date).toLocaleString()
    );
    expect(resultToString(values['selection'])).toBe('a, b, Other: x');
    expect(resultToString(point)).toBe('POINT');
    expect(resultToString(null)).toBe('');
  });
});
