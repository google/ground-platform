import {GeometryType} from 'app/models/geometry/geometry';
import {LocationOfInterest} from 'app/models/loi.model';

export const getLoiIcon = (loi: LocationOfInterest): string => {
  let icon = 'point';
  const type = loi.geometry?.geometryType;
  if (type === GeometryType.POLYGON || type === GeometryType.MULTI_POLYGON) {
    icon = 'polygon';
  }
  return icon;
};
