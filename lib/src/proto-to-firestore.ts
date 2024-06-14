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

import {FieldDescriptor, MessageDescriptor, registry} from './message-registry';
import {isEmpty} from './obj-util';
import {DocumentData, DocumentFieldValue} from '@google-cloud/firestore';

/**
 * Returns the map representation of the provided message ready for serialization in Firestore.
 * The map is keyed by message field numbers represented as strings, while field values are
 * converted to corresponding Firestore data types.
 */
export function toDocumentData(message: object): DocumentData | Error {
  const type = message.constructor.name;
  const descriptor = registry.getMessageDescriptor(message.constructor);
  // Fail if message not defined in registry.
  if (!descriptor) return Error(`Unknown message type ${type}`);
  // Messages must also have at least one field.
  if (!descriptor.fields)
    return Error(`Invalid message definition: ${descriptor}`);
  return messageToData(message, descriptor);
}

function messageToData(
  message: any,
  descriptor: MessageDescriptor
): DocumentData {
  const firestoreMap: DocumentData = {};
  for (const name in descriptor.fields) {
    const fieldDescriptor = descriptor.fields[name];
    const fieldNumber = fieldDescriptor?.id;
    if (!fieldNumber) {
      // Skipping unknown field.
      continue;
    }
    const value = toDocumentFieldValue(fieldDescriptor, message[name]);
    if (value) {
      firestoreMap[fieldNumber.toString()] = value;
    }
  }
  return firestoreMap;
}

function toDocumentFieldValue(
  field: FieldDescriptor,
  value: any
): DocumentFieldValue | null {
  if (value === null || isEmpty(value)) {
    return null;
  } else if (field.keyType) {
    return Object.fromEntries(
      Object.entries(value).map(([k, v]) => [
        k.toString(),
        toValueOrNull(field.type, v),
      ])
    );
  } else {
    return toValueOrNull(field.type, value);
  }
}

function toValueOrNull(
  fieldType: string,
  value: any
): DocumentFieldValue | null {
  const v = toValue(fieldType, value);
  if (v instanceof Error) {
    console.error(v);
    return null;
  } else return v;
}

function toValue(
  fieldType: string,
  value: any
): DocumentFieldValue | Error | null {
  // TODO(#1758): Coerce values to type specified in `fieldType`.
  switch (typeof value) {
    case 'string':
    case 'number': // This handles proto enums as well.
    case 'boolean':
      return value;
    case 'object':
      if (Array.isArray(value)) {
        return value.map(e => toValue(fieldType, e));
      } else {
        return toDocumentData(value);
      }
    default:
      return Error(`Unsupported field type ${typeof value}`);
  }
}
