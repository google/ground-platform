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

import registryJson from "./generated/ground-protos.json";

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
  fields?: { [fieldName: string]: FieldDescriptor };
  oneofs?: { [oneofName: string]: OneOfDescriptor };
  nested?: { [nestedMessageName: string]: MessageDescriptor };
  values?: { [enumValueName: string]: number }; // For enums
}

export interface OneOfDescriptor {
  oneof: string[]; // Names of fields in the oneof
}

export interface MessageRegistry {
  options?: ProtoOptions;
  nested: { [messageName: string]: MessageDescriptor };
}

export const registry = registryJson as MessageRegistry;
