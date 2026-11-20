import { FormArray } from '@angular/forms';

import { GeometryType } from 'app/models/geometry/geometry';
import { LocationOfInterest } from 'app/models/loi.model';

export function moveItemInFormArray(
  formArray: FormArray,
  fromIndex: number,
  toIndex: number
): void {
  const dir = toIndex > fromIndex ? 1 : -1;

  const item = formArray.at(fromIndex);
  for (let i = fromIndex; i * dir < toIndex * dir; i = i + dir) {
    const current = formArray.at(i + dir);
    formArray.setControl(i, current);
  }
  formArray.setControl(toIndex, item);
}

export const getLoiIcon = (loi: LocationOfInterest): string => {
  let icon = 'point';
  const type = loi.geometry?.geometryType;
  if (type === GeometryType.POLYGON || type === GeometryType.MULTI_POLYGON) {
    icon = 'polygon';
  }
  return icon;
};
