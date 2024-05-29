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

import assert from "assert";
import registryJson from "./generated/ground-protos.json";
import * as GroundProtos from "./generated/ground-protos";
import { Constructor } from "protobufjs";

export interface ProtoOptions {
  java_package: string;
  java_multiple_files: boolean;
}

export interface FieldDescriptor {
  keyType?: string; // Used for proto map<>
  type: string;
  id: number;
}

export interface MessageDescriptor {
  // Normal proto fields.
  fields?: { [fieldName: string]: FieldDescriptor };
  // Possible oneof selector values.
  oneofs?: { [oneofName: string]: OneOfDescriptor };
  // Nested message type definition.
  nested?: { [nestedMessageName: string]: MessageDescriptor };
  // Enum definitions, including valid values.
  values?: { [enumValueName: string]: number };
}

export interface OneOfDescriptor {
  oneof: string[]; // Names of fields in the oneof
}

export interface MessageRegistryJson {
  options?: ProtoOptions;
  nested: { [messageName: string]: MessageDescriptor };
}

export class MessageRegistry { 
  constructor(private readonly json: MessageRegistryJson) {}

  getMessageDescriptor(constructor: any): MessageDescriptor | null {
    if (!constructor.getTypeUrl) return null;
    // Gets URL of nested type in the format "/ClassA.ClassB.ClassC".
    const typeUrl = constructor.getTypeUrl("");
    assert(typeUrl?.substr(0, 1) == "/")
    // Remove preceding "/" and split path along ".";
    const typePath = typeUrl.substr(1).split(".");
    return this.getDescriptorByPath(typePath);
  }

  private getDescriptorByPath(path: string[]): any | null {
    let node = this.json as any;
    for (const type of path) {
      node = node?.nested[type];
    }
    return node;  
  }

  getFieldNameByNumber(descriptor: MessageDescriptor, fieldNo: Number): string | null {
    if (!descriptor.fields) return null;
    for (const fieldName in descriptor.fields) {
      const field = descriptor.fields[fieldName];
      if (field.id === fieldNo) {
        return fieldName;
      }
    }
    return null;
  }

  getConstructorByPath<T>(path: string[]): Constructor<T> | null {
    let node = GroundProtos as any;
    for (const typeName of path) {
      node = node[typeName];
      if (!node) return null;
    }
    return node;
  }

  getEnumValues(messagePath: string[], enumName: string):{ [enumValueName: string]: number } | null {
    return this.getDescriptorByPath([...messagePath, enumName])?.values;
  } 
}

export const registry = new MessageRegistry(registryJson as MessageRegistryJson);
