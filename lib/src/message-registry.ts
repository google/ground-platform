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

import { GroundProtos, registryJson } from '@ground/proto';
import { Constructor } from 'protobufjs';

/** Path of message or enum type declaration in definition files and registry. */
export type MessageTypePath = string[];

export interface ProtoOptions {
  java_package?: string;
  java_multiple_files?: boolean;
  syntax?: string;
}

export interface FieldDescriptor {
  keyType?: string; // Used for proto map<>
  type: string;
  id: number;
  rule?: 'repeated';
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

  getTypePath(constructor: any): string[] | null {
    if (!constructor.getTypeUrl) return null;
    // Gets URL of nested type in the format "/ClassA.ClassB.ClassC".
    const typeUrl = constructor.getTypeUrl('');
    if (typeUrl?.substr(0, 1) !== '/') {
      console.error('Invalid type URL returned by protojs class:', typeUrl);
      return null;
    }
    // Remove preceding "/" and split path along ".";
    return typeUrl.substr(1).split('.');
  }

  getMessageDescriptor(constructor: any): MessageDescriptor | null {
    const typePath = this.getTypePath(constructor);
    return typePath ? this.getDescriptorByPath(typePath) : null;
  }

  /**
   * Returns a dictionary containing the numbers of all fields in a
   * message definition, keyed by field name. Throws an error if not found.
   */
  getFieldIds(constructor: any): { [key: string]: string } {
    const desc = this.getMessageDescriptor(constructor);
    if (!desc) throw new Error(`Unknown constructor ${constructor.name}`);
    const map: { [key: string]: string } = {};
    if (desc.fields) {
      Object.keys(desc.fields).forEach(
        name => (map[name] = desc.fields![name].id?.toString())
      );
    }
    return map;
  }

  /**
   * Searches the descriptor hierarchy starting for the specified `typeName`,
   * starting from the specified containing `messageTypePath`. If the type
   * is not found there, the parent containing type is checked, recursively
   * down to the registry root.
   */
  findType(messageTypePath: string[], typeName: string): string[] | null {
    // Create copy to prevent destructive edits to `messageTypePath`.
    const parentPath = [...messageTypePath];
    do {
      const descriptorPath = [...parentPath, typeName];
      if (this.getDescriptorByPath(descriptorPath)) return descriptorPath;
    } while (parentPath.pop() !== undefined);
    return null;
  }

  getDescriptorByPath(path: string[]): any | null {
    let node = this.json as any;
    for (const type of path) {
      if (!node || !node.nested) return null;
      node = node.nested[type];
    }
    return node;
  }

  getFieldNameByNumber(
    descriptor: MessageDescriptor,
    fieldNo: number
  ): string | null {
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
}

export const registry = new MessageRegistry(
  registryJson as MessageRegistryJson
);
