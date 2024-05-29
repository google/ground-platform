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

import { Constructor } from "protobufjs";
import { MessageDescriptor, registry } from "./message-registry";
import { DocumentData } from "@google-cloud/firestore";

export function toMessage<T>(
  data: DocumentData,
  constructor: Constructor<T>
): T | Error {
  const descriptor = registry.getDescriptor(constructor);
  if (!descriptor)
    return Error(`Message type ${constructor.name} not found in registry`);
  return toMessageInternal(data, constructor, descriptor, []);
}

function toMessageInternal<T>(
  data: DocumentData,
  constructor: Constructor<T>,
  descriptor: MessageDescriptor,
  // Path of message type described by descriptor from root of registry.
  descriptorPath: string[]
): T | Error {
  const properties: DocumentData = {};
  for (const key in data) {
    const firestoreValue = data[key];
    const fieldNo = parseInt(key);
    // Skip non-numeric keys  .
    if (Number.isNaN(fieldNo)) continue;
    const fieldName = registry.getFieldNameByNumber(descriptor, fieldNo);
    // Skip unrecognized field numbers.
    if (!fieldName) continue;
    const messageValue = toMessageValue(
      descriptor,
      descriptorPath,
      fieldName,
      firestoreValue
    );
    if (!!messageValue) properties[fieldName] = messageValue;
  }
  return createInstance<T>(constructor, properties);
}

function toMessageValue(
  descriptor: MessageDescriptor,
  // Path of message type described by descriptor from root of registry.
  descriptorPath: string[],
  fieldName: string,
  firestoreValue: any
): any | null {
  if (!descriptor.fields) return null;
  const fieldType = descriptor.fields[fieldName]?.type;
  // TODO: Check value type corresponds to appropriate type.
  switch (fieldType) {
    case "string":
    case "int32":
    case "int64":
    case "bool":
      return firestoreValue;
    default:
      const nestedDescriptorPath = [...descriptorPath, fieldType];
      const constructor = registry.getConstructorByPath(nestedDescriptorPath);
      // Skip unknown types.
      if (!constructor) return null;
      const nestedDescriptor = registry.getDescriptor(constructor);
      // Skip unknown types.
      if (!nestedDescriptor) return null;
      // TODO: Check firestoreValue is a DocumentData.
      return toMessageInternal(
        firestoreValue as DocumentData,
        constructor,
        nestedDescriptor,
        nestedDescriptorPath
      );
  }
}

function createInstance<T>(constructor: Constructor<T>, ...args: any[]): T {
  return new constructor(...args);
}
