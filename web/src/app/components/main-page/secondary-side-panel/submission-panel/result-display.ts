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

import { Geometry } from 'app/models/geometry/geometry';
import { Point } from 'app/models/geometry/point';
import { MultipleSelection } from 'app/models/submission/multiple-selection';
import { ResultValue } from 'app/models/submission/result.model';
import { TaskType } from 'app/models/task/task.model';

function isGeometry(value: unknown): value is Geometry {
  return typeof value === 'object' && value !== null && 'geometryType' in value;
}

/**
 * Returns whether a result can be shown the way the type of its task asks.
 *
 * The type of a task can be changed after submissions were made, so a result
 * holds whatever the type was back then (a selection for a task that is now a
 * date, say), and the views for the current type can't render it.
 */
export function isResultCompatible(
  taskType: TaskType,
  value: ResultValue | undefined
): boolean {
  switch (taskType) {
    case TaskType.MULTIPLE_CHOICE:
      return value instanceof MultipleSelection;
    case TaskType.PHOTO:
      return typeof value === 'string';
    case TaskType.DATE:
    case TaskType.TIME:
    case TaskType.DATE_TIME:
      return value instanceof Date;
    case TaskType.TEXT:
    case TaskType.NUMBER:
      return typeof value === 'string' || typeof value === 'number';
    case TaskType.CAPTURE_LOCATION:
      return value instanceof Point;
    case TaskType.DROP_PIN:
    case TaskType.DRAW_AREA:
      return isGeometry(value);
    default:
      return true;
  }
}

/** Returns a readable text for a result of any kind. */
export function resultToString(value: ResultValue | undefined): string {
  if (value === null || value === undefined) return '';
  if (value instanceof MultipleSelection) {
    const selected = value.values.toArray();
    if (value.otherValue) selected.push(`Other: ${value.otherValue}`);
    return selected.join(', ');
  }
  if (value instanceof Date) return value.toLocaleString();
  if (isGeometry(value)) return value.geometryType;
  return String(value);
}
