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

import { FieldDescriptor, MessageDescriptor, registry } from "./message-registry";
import { DocumentData, DocumentFieldValue } from "@google-cloud/firestore";

export function toDocumentData(message: any): DocumentData | Error {
  const type = message.constructor.name;
  const descriptor =  registry.getDescriptor(message);
  if (!descriptor) return Error(`Unknown message type ${type}`);

  if (!descriptor.fields) {
    // `values`, `nested`, and `oneofs` fields are handled implicitly in `messageToData()`.
    return Error(`Unexpected field type ${descriptor}`);
  }
  return messageToData(message, descriptor);
}

function messageToData(message: any, descriptor: MessageDescriptor): DocumentData {
  const firestoreMap: DocumentData = {};
  for (const name in descriptor.fields) {
    const fieldDescriptor = descriptor.fields[name];
    const fieldNumber = fieldDescriptor?.id;
    if (!fieldNumber) {
      console.debug(`Skipping unknown field $name in $type`);
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
  if (value == null || isEmpty(value)) {
    return null;
  } else if (field.keyType) {
    return Object.fromEntries(
      Object.entries(value).map(([k, v]) => [
        k.toString(),
        toValue(field.type, v),
      ])
    );
  } else {
    return toValue(field.type, value);
  }
}

function toValue(fieldType: string, value: any): DocumentFieldValue | null {
  switch (typeof value) {
   // TODO: Validate against fieldType.
   case "string":
    case "number": // This handles proto enums as well.
      return value;
    case "object":
      return toDocumentData(value);
    default:
      console.debug(`Unsupported proto field type ${typeof value}`);
      return null;
  }
}

function isEmpty(obj: any) {
  switch (typeof obj) {
    case "string":
      return obj === "";
    case "object":
      return Object.keys(obj).length === 0;
    default:
      return false;
  }
}
