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

import {Constructor} from 'protobufjs';
import {MessageDescriptor, MessageTypePath, registry} from './message-registry';
import {DocumentData} from '@google-cloud/firestore';

export function toMessage<T>(
  data: DocumentData,
  constructor: Constructor<T>
): T | Error {
  const typePath = registry.getTypePath(constructor);
  if (!typePath) return Error(`${constructor.name} is not a protojs message`);
  const descriptor = registry.getDescriptorByPath(typePath);
  if (!descriptor)
    return Error(`Message type ${constructor.name} not found in registry`);
  return toMessageInternal(data, constructor, descriptor, typePath);
}

function toMessageInternal<T>(
  data: DocumentData,
  constructor: Constructor<T>,
  descriptor: MessageDescriptor,
  messageTypePath: MessageTypePath
): T | Error {
  const properties: DocumentData = {};
  for (const key in data) {
    const firestoreValue = data[key];
    const fieldNo = parseInt(key);
    // Skip non-numeric keys.
    if (Number.isNaN(fieldNo)) continue;
    const fieldName = registry.getFieldNameByNumber(descriptor, fieldNo);
    // Skip unrecognized field numbers.
    if (!fieldName) continue;
    const messageValue = toMessageValue(
      descriptor,
      messageTypePath,
      fieldName,
      firestoreValue
    );
    if (messageValue instanceof Error) {
      console.debug(messageValue);
      continue;
    }
    if (messageValue) properties[fieldName] = messageValue;
  }
  return createInstance<T>(constructor, properties);
}

/**
 * Returns the equivalent protobuf field value for a give Firestore value.
 */
function toMessageValue(
  descriptor: MessageDescriptor,
  // Path of message type described by descriptor from root of registry.
  messageTypePath: MessageTypePath,
  fieldName: string,
  firestoreValue: any
): any | Error | null {
  if (!descriptor.fields) return null;
  const fields = descriptor.fields[fieldName];
  const fieldType = fields?.type;
  if (fields.keyType) {
    if (fields.keyType !== 'string')
      return Error(`${fields.keyType} map keys not supported`);
    // TODO: Check that firestoreValue is an object.
    return toMapValue(messageTypePath, fieldType, firestoreValue);
  } else {
    return toFieldValue(messageTypePath, fieldType, firestoreValue);
  }
}

function toMapValue(
  messageTypePath: MessageTypePath,
  valueType: string,
  nestedObject: [key: string]
): any | null {
  const messageMap: {[key: string]: any} = {};
  for (const key in nestedObject) {
    const firestoreValue = nestedObject[key];
    messageMap[key.toString()] = toFieldValue(
      messageTypePath,
      valueType,
      firestoreValue
    );
  }
  return messageMap;
}

function toFieldValue(
  // Path of the containing messsage type.
  messageTypePath: MessageTypePath,
  fieldType: string,
  firestoreValue: any
): any | Error | null {
  // TODO: Check value type corresponds to appropriate type.
  switch (fieldType) {
    case 'string':
    case 'int32':
    case 'int64':
    case 'bool':
      return firestoreValue;
    default:
      return toMessageOrEnumValue(messageTypePath, fieldType, firestoreValue);
  }
}

function toMessageOrEnumValue(
  // Path of the containing messsage type.
  messageTypePath: MessageTypePath,
  fieldType: string,
  firestoreValue: any
): any | Error | null {
  const fieldTypePath = registry.findType(messageTypePath, fieldType);
  if (!fieldTypePath) return Error(`Unknown field type ${fieldType}`);
  const constructor = registry.getConstructorByPath(fieldTypePath);
  const nestedDescriptor = constructor
    ? registry.getMessageDescriptor(constructor)
    : null;
  if (constructor && nestedDescriptor) {
    if (!nestedDescriptor)
      return Error(`Unknown message type ${nestedDescriptor}`);
    // TODO: Check firestoreValue is a DocumentData.
    return toMessageInternal(
      firestoreValue as DocumentData,
      constructor,
      nestedDescriptor,
      fieldTypePath
    );
  } else {
    // If constructor not found, assume value is an enum value.
    const enumValue = Number(firestoreValue);
    // Only include valid numeric values.
    return isNaN(enumValue) ? null : enumValue;
  }
}

function createInstance<T>(constructor: Constructor<T>, ...args: any[]): T {
  return new constructor(...args);
}
