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
import {
  MessageDescriptor,
  registry,
} from "./message-registry";
import { DocumentData } from "@google-cloud/firestore";

export function toMessage<T>(
  data: DocumentData,
  constructor: Constructor<T>
): T | Error {
  const descriptor = registry.getDescriptor(constructor);
  if (!descriptor) return Error(`Message type ${constructor.name} not found in registry`);
  const properties: { [key: string]: any } = {};
  for (const key in data) {
    const firestoreValue = data[key];
    const fieldNo = parseInt(key);
    // Skip non-numeric keys  .
    if (Number.isNaN(fieldNo)) continue;
    const fieldName = registry.getFieldNameByNumber(descriptor, fieldNo);
    // Skip unrecognized field numbers.
    if (!fieldName) continue;
    properties[fieldName] = toMessageValue(descriptor, fieldName, firestoreValue);
  }
  return createInstance<T>(constructor, properties);
}

function toMessageValue(descriptor: MessageDescriptor, fieldName: String, firestoreValue: any): any {
  return firestoreValue;
}

function createInstance<T>(constructor: Constructor<T>, ...args: any[]): T {
  return new constructor(...args);
}
